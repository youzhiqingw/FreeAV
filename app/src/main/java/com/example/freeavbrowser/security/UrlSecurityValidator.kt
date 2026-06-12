package com.example.freeavbrowser.security

import android.net.Uri
import java.net.InetAddress

object UrlSecurityValidator {

    fun validateExternalUrl(url: String): ValidationResult {
        val uri = Uri.parse(url.trim()) ?: return ValidationResult.Invalid("URL解析失败")

        if (uri.scheme !in listOf("http", "https")) {
            return ValidationResult.Invalid("不支持的协议: ${uri.scheme}")
        }

        val host = uri.host ?: return ValidationResult.Invalid("无效的主机名")

        if (isPrivateHost(host)) {
            return ValidationResult.Invalid("禁止访问内网地址")
        }

        return ValidationResult.Valid(url)
    }

    fun validateWebViewNavigation(url: String): ValidationResult {
        val uri = Uri.parse(url.trim()) ?: return ValidationResult.Invalid("URL解析失败")

        val allowedSchemes = listOf("http", "https", "blob", "data")
        val blockedSchemes = listOf("javascript", "file", "content", "intent")

        when {
            uri.scheme in blockedSchemes -> {
                return ValidationResult.Invalid("禁止的协议: ${uri.scheme}")
            }
            uri.scheme in allowedSchemes -> {
                return ValidationResult.Valid(url)
            }
            else -> {
                return ValidationResult.Invalid("不支持的协议: ${uri.scheme}")
            }
        }
    }

    fun validateJavaScriptUrl(url: String): Boolean {
        val uri = Uri.parse(url.trim())
        return uri.scheme in listOf("http", "https")
    }

    fun validateProxyTarget(
        url: String,
        currentPageHost: String?,
        videoCdnCache: Set<String> = emptySet()
    ): ValidationResult {
        val uri = Uri.parse(url.trim()) ?: return ValidationResult.Invalid("URL解析失败")

        if (uri.scheme !in listOf("http", "https")) {
            return ValidationResult.Invalid("不支持的协议")
        }

        val host = uri.host ?: return ValidationResult.Invalid("无效的主机名")

        if (isPrivateHost(host)) {
            return ValidationResult.Invalid("禁止访问内网地址")
        }

        if (currentPageHost != null) {
            if (host == currentPageHost || host.endsWith(".$currentPageHost")) {
                return ValidationResult.Valid(url)
            }
        }

        if (videoCdnCache.any { host.endsWith(it) }) {
            return ValidationResult.Valid(url)
        }

        val commonCdnSuffixes = listOf(
            ".cloudflare.com", ".cloudflarestream.com",
            ".cloudfront.net", ".amazonaws.com",
            ".akamai.net", ".akamaihd.net",
            ".fastly.net", ".cdn77.com"
        )
        if (commonCdnSuffixes.any { host.endsWith(it) }) {
            return ValidationResult.Valid(url)
        }

        return ValidationResult.RequireWhitelist(url, host)
    }

    fun isPrivateHost(host: String): Boolean {
        if (host in setOf("localhost", "127.0.0.1", "0.0.0.0", "::1")) {
            return true
        }

        val addr = try {
            InetAddress.getByName(host)
        } catch (e: Exception) {
            return false
        }

        return addr.isLoopbackAddress ||
               addr.isSiteLocalAddress ||
               addr.isLinkLocalAddress ||
               addr.hostAddress?.startsWith("169.254.") == true
    }

    sealed class ValidationResult {
        data class Valid(val url: String) : ValidationResult()
        data class Invalid(val reason: String) : ValidationResult()
        data class RequireWhitelist(val url: String, val host: String) : ValidationResult()
    }
}
