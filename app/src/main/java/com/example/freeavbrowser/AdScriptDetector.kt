package com.example.freeavbrowser

/**
 * AdScriptDetector - 通用广告脚本识别引擎
 *
 * 通过行为模式识别广告脚本，而非域名黑名单。
 * 即使广告域名变化，行为模式不变，仍能识别和拦截。
 *
 * 核心识别特征：
 * 1. 广告调度器: adManager.js / adManager
 * 2. 弹窗脚本: clickadu / popunder / popup / tabunder
 * 3. 广告下发路径: /z/STAR/code.js, /z/STAR/json
 * 4. 智能弹窗: smartpop
 * 5. .xyz 随机子域名 + 动态路径（广告联盟常见模式）
 */
object AdScriptDetector {

    /**
     * 广告脚本文件名特征
     * 匹配方式：URL 路径中包含关键词
     */
    private val adScriptFilenamePatterns = listOf(
        "admanager",      // 广告调度器（如 adManager.js）
        "clickadu",       // Clickadu 弹窗广告 SDK
        "smartpop",       // 智能弹窗
        "popunder",       // Pop-under 广告
        "popup",          // 弹窗广告
        "tabunder",       // Tab-under 广告
        "popads",         // 弹窗广告网络
        "popcash",        // PopCash 广告网络
        "propeller",      // PropellerAds
        "adsterra",       // Adsterra 广告网络
        "plugrush",       // PlugRush 广告
        "trafficjunky",   // TrafficJunky 广告
        "exoclick",       // ExoClick 成人广告
        "juicyads",       // JuicyAds 成人广告
        "adxadserv",      // 广告投放服务
        "ad-provider",    // 通用广告供应商
        "banner-ads",     // 横幅广告
        "ad-script",      // 广告脚本
        "popunder-js",    // Pop-under JS
        "ads-loader",     // 广告加载器
        "ad-loader"       // 广告加载器
    )

    /**
     * 广告联盟下发路径模式
     * 格式：/单字符/[随机]/资源
     */
    private val adDeliveryPathPatterns = listOf(
        // /z/STAR/code.js - 广告代码下发
        Regex("""/[a-z]/\w+/code\.js""", RegexOption.IGNORE_CASE),
        // /z/STAR/json - 广告配置下发
        Regex("""/[a-z]/\w+/json""", RegexOption.IGNORE_CASE),
        // /z/STAR/ads - 广告数据
        Regex("""/[a-z]/\w+/ads?""", RegexOption.IGNORE_CASE)
    )

    /**
     * .xyz 广告域名模式
     * 特征：短随机子域名 + 广告下发路径
     * 例：ra13.xyz/z/abc123/code.js
     */
    private val xyzAdDomainPattern = Regex(
        """^[a-z]{2,6}\d{1,4}\.xyz$""",
        RegexOption.IGNORE_CASE
    )

    /**
     * 已知广告平台域名后缀
     */
    private val adPlatformSuffixes = listOf(
        "wpadmngr.com",         // WP Ad Manager 广告调度
        "mnaspm.com",           // SmartPop 弹窗
        "clammyendearedkeg.com", // 广告重定向
        "cloudflareinsights.com" // 统计追踪
    )

    /**
     * 已知合法站点域名 — 即使匹配广告模式也不拦截。
     * 避免把网站自己的主页面拦掉导致白屏。
     */
    private val knownSiteDomains = setOf(
        "rouva3.xyz", "rouva5.xyz",
        "hentaihaven.xxx",
        "missav.ws", "missav.ai",
        "jable.tv",
        "hanime.tv",
        "njav.tv",
        "supjav.com",
        "javgg.net",
        "7mmtv.sx",
        "hohoj.tv",
        "javtsunami.com"
    )

    /**
     * 检测 URL 是否为广告脚本。
     * 返回: true 应该拦截
     */
    fun isAdScript(url: String): Boolean {
        // 0. 已知合法站点直接放行
        val host = extractHost(url)
        if (host != null && isKnownSite(host)) return false

        val lowerUrl = url.lowercase()

        // 1. 文件名模式检测
        for (pattern in adScriptFilenamePatterns) {
            if (lowerUrl.contains(pattern)) {
                // 排除正常页面内容
                if (!isFalsePositive(lowerUrl)) {
                    return true
                }
            }
        }

        // 2. 广告下发路径检测
        for (regex in adDeliveryPathPatterns) {
            if (regex.containsMatchIn(lowerUrl)) {
                return true
            }
        }

        // 3. .xyz 广告域名检测
        try {
            val urlHost = extractHost(url)
            if (urlHost != null && xyzAdDomainPattern.matches(urlHost)) {
                // .xyz 域名 + 动态路径 → 很可能是广告
                if (lowerUrl.contains("/code.js") ||
                    lowerUrl.contains("/json") ||
                    lowerUrl.contains("/ads")
                ) {
                    return true
                }
            }
        } catch (_: Exception) {}

        return false
    }

    /**
     * 检测域名是否为已知广告平台。
     * 返回: true 应该拦截
     */
    fun isAdPlatform(host: String): Boolean {
        // 已知合法站点不拦截
        if (isKnownSite(host)) return false

        val lowerHost = host.lowercase()

        for (suffix in adPlatformSuffixes) {
            if (lowerHost.endsWith(".$suffix") || lowerHost == suffix) {
                return true
            }
        }

        // 检查 .xyz 广告域名
        if (xyzAdDomainPattern.matches(lowerHost)) {
            return true
        }

        return false
    }

    /**
     * 检测域名是否为已知合法站点。
     * 这些域名即使匹配广告模式特征也不拦截。
     */
    private fun isKnownSite(host: String): Boolean {
        val lowerHost = host.lowercase()
        return knownSiteDomains.any { lowerHost == it || lowerHost.endsWith(".$it") }
    }

    /**
     * 检测 URL 是否包含广告脚本路径特征
     */
    fun isAdPath(url: String): Boolean {
        val lowerUrl = url.lowercase()

        for (regex in adDeliveryPathPatterns) {
            if (regex.containsMatchIn(lowerUrl)) {
                return true
            }
        }

        return false
    }

    /**
     * 排除正常页面内容的误报
     */
    private fun isFalsePositive(url: String): Boolean {
        // 排除视频相关内容
        val safePatterns = listOf(
            "/video/", "/videos/", "/watch/", "/play/",
            ".mp4", ".m3u8", ".webm", ".mkv",
            "player", "thumbnail", "poster"
        )
        return safePatterns.any { url.contains(it) }
    }

    /**
     * 从 URL 中提取 host
     */
    private fun extractHost(url: String): String? {
        return try {
            val uri = android.net.Uri.parse(url)
            uri.host
        } catch (_: Exception) {
            null
        }
    }
}
