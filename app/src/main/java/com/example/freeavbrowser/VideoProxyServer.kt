package com.example.freeavbrowser

import android.content.Context
import fi.iki.elonen.NanoHTTPD
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * 本地 HTTP Proxy Server
 * 用途：為受 CDN 防盜鏈保護的影片（如 avjoy.me）加上正確的 Request Headers，
 *       讓外部播放器（MX Player / VLC）不需要自行帶 headers 就能正常播放。
 *
 * 使用方式：
 *   val proxy = VideoProxyServer(context)
 *   proxy.start()
 *   val localUrl = proxy.buildProxyUrl(realVideoUrl, referer, cookies)
 *   // 將 localUrl 傳給外部播放器
 */
class VideoProxyServer(private val context: Context) : NanoHTTPD("127.0.0.1", 0) {

    companion object {
        private const val CONNECT_TIMEOUT = 15000
        private const val READ_TIMEOUT = 30000
        private val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }

    private val rateLimiter = HostRateLimiter(minIntervalMs = 200L)
    private val proxyToken = java.util.UUID.randomUUID().toString()
    private val videoCdnCache = mutableSetOf<String>()

    fun buildProxyUrl(realUrl: String, referer: String, cookies: String?): String {
        // Extract and cache CDN domain
        val cdnHost = android.net.Uri.parse(realUrl).host
        if (cdnHost != null) {
            videoCdnCache.add(cdnHost)
        }

        val encodedUrl = java.net.URLEncoder.encode(realUrl, "UTF-8")
        val encodedReferer = java.net.URLEncoder.encode(referer, "UTF-8")
        val encodedCookies = java.net.URLEncoder.encode(cookies ?: "", "UTF-8")
        return "http://127.0.0.1:$listeningPort/proxy?url=$encodedUrl&referer=$encodedReferer&cookies=$encodedCookies&token=$proxyToken"
    }

    override fun serve(session: IHTTPSession): Response {
        return try {
            if (session.uri != "/proxy") {
                return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
            }

            // 1. Token validation
            val token = session.parameters["token"]?.firstOrNull()
            if (token != proxyToken) {
                return newFixedLengthResponse(Response.Status.FORBIDDEN, MIME_PLAINTEXT, "Invalid token")
            }

            val params = session.parameters
            val realUrl = params["url"]?.firstOrNull()
                ?.let { java.net.URLDecoder.decode(it, "UTF-8") }
                ?: return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "Missing url")

            // 2. Get current page host
            val referer = params["referer"]?.firstOrNull()
                ?.let { java.net.URLDecoder.decode(it, "UTF-8") } ?: ""
            val currentPageHost = if (referer.isNotEmpty()) {
                android.net.Uri.parse(referer).host
            } else null

            // 3. SSRF protection (3-tier)
            when (val result = com.example.freeavbrowser.security.UrlSecurityValidator.validateProxyTarget(realUrl, currentPageHost, videoCdnCache)) {
                is com.example.freeavbrowser.security.UrlSecurityValidator.ValidationResult.Valid -> {
                    // Pass validation
                }
                is com.example.freeavbrowser.security.UrlSecurityValidator.ValidationResult.Invalid -> {
                    return newFixedLengthResponse(Response.Status.FORBIDDEN, MIME_PLAINTEXT, result.reason)
                }
                is com.example.freeavbrowser.security.UrlSecurityValidator.ValidationResult.RequireWhitelist -> {
                    android.util.Log.w("VideoProxyServer", "Blocked host not in whitelist: ${result.host}")
                    return newFixedLengthResponse(Response.Status.FORBIDDEN, MIME_PLAINTEXT, "Host not allowed")
                }
            }

            val cookies = params["cookies"]?.firstOrNull()
                ?.let { java.net.URLDecoder.decode(it, "UTF-8") } ?: ""
            val rangeHeader = session.headers["range"]

            proxyRequest(realUrl, referer, cookies, rangeHeader)
        } catch (e: Exception) {
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Proxy error: ${e.message}")
        }
    }

    private fun proxyRequest(
        realUrl: String,
        referer: String,
        cookies: String,
        rangeHeader: String?
    ): Response {
        // 速率限制：按 host 强制最小间隔
        val host = URL(realUrl).host
        rateLimiter.acquire(host)

        val connection = URL(realUrl).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = CONNECT_TIMEOUT
            connection.readTimeout = READ_TIMEOUT
            connection.instanceFollowRedirects = true

            // 加上所有必要的 headers，模擬瀏覽器行為
            connection.setRequestProperty("User-Agent", USER_AGENT)
            if (referer.isNotEmpty()) {
                connection.setRequestProperty("Referer", referer)
            }

            // 设置 Cookie（优先使用传入的 cookies，然后补充 cf_clearance）
            var finalCookies = cookies
            val privacySettings = PrivacySettings(context)
            if (privacySettings.isCloudflareBypassEnabled) {
                val host = URL(realUrl).host
                val cfCookie = privacySettings.getCloudflareCookie(host)
                if (cfCookie != null && !finalCookies.contains("cf_clearance")) {
                    finalCookies = if (finalCookies.isEmpty()) "cf_clearance=$cfCookie"
                                   else "$finalCookies; cf_clearance=$cfCookie"
                }
            }
            if (finalCookies.isNotEmpty()) {
                connection.setRequestProperty("Cookie", finalCookies)
            }

            connection.setRequestProperty("Accept", "*/*")
            connection.setRequestProperty("Accept-Encoding", "identity;q=1, *;q=0")
            connection.setRequestProperty("Sec-Fetch-Dest", "video")
            connection.setRequestProperty("Sec-Fetch-Mode", "no-cors")
            connection.setRequestProperty("Sec-Fetch-Site", "same-site")

            // 將播放器的 Range header 透傳（讓 seek 正常運作）
            if (!rangeHeader.isNullOrEmpty()) {
                connection.setRequestProperty("Range", rangeHeader)
            }

            val responseCode = connection.responseCode
            val contentType = connection.contentType ?: "video/mp4"
            val contentLength = connection.contentLengthLong
            val contentRange = connection.getHeaderField("Content-Range")
            val acceptRanges = connection.getHeaderField("Accept-Ranges")

            val inputStream: InputStream = if (responseCode >= 400) {
                connection.errorStream ?: return newFixedLengthResponse(
                    Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "CDN error $responseCode"
                )
            } else {
                connection.inputStream
            }

            val status = when (responseCode) {
                206 -> Response.Status.PARTIAL_CONTENT
                200 -> Response.Status.OK
                else -> Response.Status.lookup(responseCode) ?: Response.Status.INTERNAL_ERROR
            }

            val response = if (contentLength > 0) {
                newFixedLengthResponse(status, contentType, inputStream, contentLength)
            } else {
                newChunkedResponse(status, contentType, inputStream)
            }

            if (!contentRange.isNullOrEmpty()) {
                response.addHeader("Content-Range", contentRange)
            }
            if (!acceptRanges.isNullOrEmpty()) {
                response.addHeader("Accept-Ranges", acceptRanges)
            }

            return response
        } catch (e: Exception) {
            connection.disconnect()
            throw e
        }
    }
}
