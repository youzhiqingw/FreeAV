package com.example.freeavbrowser

/**
 * 网域设定 — 硬编码，不做云端映射。
 * 只保留 missav.ws 后缀。
 */
class DomainConfig {

    companion object {
        const val MISSAV_DOMAIN = "missav.ws"
        const val JABLE_DOMAIN = "jable.tv"
        const val ROU_VIDEO_DOMAIN = "rouva3.xyz"
        const val AVJOY_DOMAIN = "avjoy.me"
    }

    fun getMissAvDomain(): String = MISSAV_DOMAIN
    fun getMissAvBaseUrl(): String = "https://$MISSAV_DOMAIN/"
    fun getMissAvSearchUrl(query: String): String = "https://$MISSAV_DOMAIN/search/$query"
    fun getJableDomain(): String = JABLE_DOMAIN
    fun getRouVideoDomain(): String = ROU_VIDEO_DOMAIN
    fun getAvJoyDomain(): String = AVJOY_DOMAIN

    fun updateUrlIfNeeded(url: String): String {
        try {
            val uri = android.net.Uri.parse(url)
            val host = uri.host ?: return url

            if (host.contains("missav.", ignoreCase = true)) {
                return uri.buildUpon().authority(MISSAV_DOMAIN).build().toString()
            } else if (host.contains("jable.", ignoreCase = true)) {
                return uri.buildUpon().authority(JABLE_DOMAIN).build().toString()
            } else if (host.contains("rou.video", ignoreCase = true) || host.contains("rouva", ignoreCase = true)) {
                return uri.buildUpon().authority(ROU_VIDEO_DOMAIN).build().toString()
            } else if (host.contains("avjoy.", ignoreCase = true)) {
                return uri.buildUpon().authority(AVJOY_DOMAIN).build().toString()
            }
        } catch (e: Exception) { }
        return url
    }
}
