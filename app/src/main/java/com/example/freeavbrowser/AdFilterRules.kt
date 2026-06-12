package com.example.freeavbrowser

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

class AdFilterRules(private val context: Context) {

     private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

     companion object {
         private const val TAG = "AdFilterRules"
        private const val PREFS_NAME = "ad_filter_rules"
        private const val KEY_RULES_JSON = "rules_json"
        private const val KEY_LAST_UPDATE = "last_update"
        private const val KEY_CLOUD_URL = "cloud_url"
        private const val KEY_CLOUD_URLS = "cloud_urls" // 多个云端 URL（逗号分隔）
        private const val KEY_LOCAL_RULES = "local_rules" // 本地规则文件列表（JSON 数组）
        private const val KEY_ELEMENT_HIDE_RULES = "element_hide_rules" // 元素隐藏规则
        private const val KEY_EASYLIST_CACHE = "easylist_rules_cache" // EasyList 规则缓存
        private const val KEY_EASYLIST_CACHE_TIME = "easylist_cache_time" // 缓存时间戳

        // EasyList规则源
        private val EASYLIST_SOURCES = listOf(
            "https://easylist.to/easylist/easylist.txt",
            "https://easylist.to/easylist/easyprivacy.txt",
            "https://easylist-downloads.adblockplus.org/easylistchina+easylist.txt",
            "https://raw.githubusercontent.com/217heidai/adblockfilters/main/rules/adblock_dns.txt",
            "https://raw.githubusercontent.com/GOODBYEADS/GOODBYEADS/master/blocklist.txt"
        )

        // 預設規則（首次安裝時使用）
        // 注意：此規則應與 ad-filter-rules.json 保持同步
        private val DEFAULT_RULES = """
        {
          "version": "2.3.4",
          "lastUpdate": "2026-06-10T00:00:00Z",
          "domains": {
            "missav": "missav.ai",
            "jable": "jable.tv",
            "rou_video": "rouva3.xyz",
            "avjoy": "avjoy.me"
          },
          "rules": {
            "commonBlock": [
              "jads.co",
              "juicyads.com",
              "creative.myavlive.com",
              "silent-basis.pro",
              "ptelastaxo.com",
              "magsrv.com",
              "afcdn.net",
              "siscprts.com",
              "exoclick.com",
              "go.mnaspm.com",
              "onclckbn.net",
              "smartpop",
              "tsyndicate.com",
              "ad-provider.js",
              "ra12.xyz",
              "rdz1.xyz",
              "uug27.com",
              "fluxtrck.site",
              "fuu78.com",
              "zlinkr.com",
              "mnaspm.com",
              "shukriya90.com",
              "shopee",
              "shp.ee",
              "lazada",
              "e608df03d6",
              "capndr.com",
              "onclckbnr.com",
              "tscprts.com",
              "clammyendearedkeg",
              "cdn.storageimagedisplay.com",
              "creative-sb1.com",
              "sunnycloudstone.com",
              "xxxvjmp.com",
              "myavlive.com",
              "snaptrckr.fun",
              "rmhfrtnd.com",
              "preferencenail.com",
              "content-sync.xyz",
              "nightdestruct.com",
              "tapioni.com",
              "googletagmanager.com",
              "sandwichconscientiousroadside.com",
              "adtng.com",
              "doppiocdn.com",
              "xlirdr.com",
              "javhdporn.live",
              "growcdnssedge.com",
              "skinnycrawlinglax.com",
              "havenclick.com",
              "ads-twitter.com",
              "wpadmngr.com",
              "ra13.xyz",
              "98d4403b02.com",
              "cloudflareinsights.com",
              "bluetrafficstream.com",
              "creativemyavlive.com",
              "xlviirdr.com",
              "quizzicalrun.com",
              "onesignal.com",
              "amung.us"
            ],
            "networkBlock": [
              "sunnycloudstone.com"
            ],
            "linkBlock": [],
            "iframeBlock": [
              "sunnycloudstone.com"
            ],
            "redirectBlock": []
          }
        }
        """.trimIndent()
    }
    
    // 新增规则数据结构
    private val blockRules = HashSet<String>()       // 域名精确匹配
    private val blockPatterns = ArrayList<String>()  // URL模式匹配
    private val whiteList = HashSet<String>()        // 白名单
    private val elementHideRules = LinkedHashSet<String>() // 通用元素隐藏规则（去重）
    private val domainElementHideRules = HashMap<String, MutableSet<String>>() // 域名特定元素隐藏 { domain -> selectors }
    private val urlPathRules = ArrayList<String>()   // URL 路径规则（包含字符串即拦截）
    private val trackerDomains = HashSet<String>()   // 跟踪器域名
    private val redirectDomains = HashSet<String>()  // $redirect=noopjs 域名
    private val generichideDomains = HashSet<String>() // $generichide 域名
    private var parsedScriptletRules = listOf<AdblockRuleParser.ParsedRule>()

    // JS 过滤器支持
    data class JsFilter(
        val domain: String,
        val type: String,    // "set-constant", "trusted-set-cookie"
        val target: String,  // "ABLK", "in_d4"
        val value: String    // "false", "1"
    )

    private val jsFilters = ArrayList<JsFilter>()
    
    init {
        try {
            // 如果是首次使用，載入預設規則
            if (!prefs.contains(KEY_RULES_JSON)) {
                updateRulesFromJson(DEFAULT_RULES)
            }

            // 初始化规则
            initializeRules()

            // 加载 JS 过滤器
            loadJsFilters()

            // 加载 URL 路径规则
            loadUrlPathRules()

            // 加载跟踪器域名
            loadTrackerDomains()

            // 加载 redirect 规则（$redirect=noopjs）
            loadRedirectRules()

            // 加载 generichide 白名单
            loadGenerichideRules()
        } catch (e: Exception) {
            Log.e(TAG, "AdFilterRules 初始化失败: ${e.message}", e)
            // 使用空规则继续，避免崩溃
        }
    }

    private fun loadUrlPathRules() {
        // 脚本管理器拦截
        urlPathRules.add("/wp-content/plugins/script-manager/assets/js/script-manager.js")
        // 广告下发路径模式
        urlPathRules.add("/z/")           // /z/*/code.js, /z/*/json 等
        urlPathRules.add("smartpop")      // 智能弹窗
        urlPathRules.add("clickadu")      // Clickadu 弹窗 SDK
        urlPathRules.add("adManager")     // 广告调度器
        urlPathRules.add("popunder")      // Pop-under
        urlPathRules.add("tabunder")      // Tab-under
        // 精准广告页面路径（低误杀）
        urlPathRules.add("ads_pages.php")
        urlPathRules.add("ads_pages2.php")
        urlPathRules.add("/popup/")
        urlPathRules.add("/pop/")
        urlPathRules.add("adserver")
        urlPathRules.add("adservice")
        urlPathRules.add("adsystem")
    }

    private fun loadTrackerDomains() {
        trackerDomains.addAll(listOf(
            "googletagmanager.com",
            "google-analytics.com",
            "googlesyndication.com",
            "doubleclick.net",
            "googleadservices.com",
            "cloudflareinsights.com"
        ))
    }

    // 获取跟踪器域名列表
    fun getTrackerDomains(): Set<String> = trackerDomains

    private fun loadRedirectRules() {
        // $redirect=noopjs 域名
        redirectDomains.add("googletagmanager.com")
    }

    private fun loadGenerichideRules() {
        // @@||domain^$generichide 白名单
        generichideDomains.add("hanime.tv")
        CosmeticFilter.addGenerichideDomains(generichideDomains)
    }

    // 检测域名是否为 redirect 规则
    fun isRedirectRule(host: String): Boolean {
        return isDomainInSet(host.lowercase(), redirectDomains)
    }

    // 获取 generichide 域名列表
    fun getGenerichideDomains(): Set<String> = generichideDomains

    // 获取解析后的 scriptlet 规则
    fun getScriptletRules(): List<AdblockRuleParser.ParsedRule> = parsedScriptletRules

    // 加载批量 uBO 规则（用于 EasyList 等外部规则源）
    fun loadExternalRules(rules: List<String>) {
        val parsed = AdblockRuleParser.parseAll(rules)
        for (rule in parsed) {
            when (rule.type) {
                AdblockRuleParser.RuleType.BLOCK -> {
                    rule.domain?.let { blockRules.add(it) }
                    if (rule.isRedirect) {
                        rule.domain?.let { redirectDomains.add(it) }
                    }
                }
                AdblockRuleParser.RuleType.GENERICHIDE -> {
                    rule.domain?.let { generichideDomains.add(it) }
                }
                AdblockRuleParser.RuleType.ELEMENT_HIDE -> {
                    rule.selector?.let { selector ->
                        if (rule.domain.isNullOrBlank()) {
                            elementHideRules.add(selector)
                        } else {
                            domainElementHideRules.getOrPut(rule.domain.lowercase()) { mutableSetOf() }.add(selector)
                        }
                    }
                }
                AdblockRuleParser.RuleType.SCRIPTLET -> {
                    // 累积到 scriptlet 规则列表
                    parsedScriptletRules = parsedScriptletRules + rule
                }
                AdblockRuleParser.RuleType.WHITELIST -> {
                    rule.domain?.let { whiteList.add(it) }
                }
            }
        }
        saveElementHideRules()
    }
    
    private fun initializeRules() {
        // 加载JSON规则
        val json = prefs.getString(KEY_RULES_JSON, DEFAULT_RULES) ?: DEFAULT_RULES
        parseJsonRules(json)
        
        // 加载元素隐藏规则
        loadElementHideRules()
    }
    
    private fun loadElementHideRules() {
        val json = prefs.getString(KEY_ELEMENT_HIDE_RULES, "[]") ?: "[]"
        try {
            val jsonArray = JSONArray(json)
            for (i in 0 until jsonArray.length()) {
                elementHideRules.add(jsonArray.getString(i))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load element hide rules", e)
        }
    }

    private fun loadJsFilters() {
        // Hanime: 反广告检测
        jsFilters.add(JsFilter(
            domain = "hanime.tv",
            type = "set-constant",
            target = "ABLK",
            value = "false"
        ))

        // Hanime: Cookie 欺骗
        jsFilters.add(JsFilter(
            domain = "hanime.tv",
            type = "trusted-set-cookie",
            target = "in_d4",
            value = "1"
        ))

        // HentaiHaven: 禁用广告拦截检测
        jsFilters.add(JsFilter(
            domain = "hentaihaven.xxx",
            type = "set-constant",
            target = "PlayerLogic.prototype.detectADB",
            value = "noopFunc"
        ))

        // HentaiHaven: 常量欺骗（让网站认为广告已展示）
        jsFilters.add(JsFilter(
            domain = "hentaihaven.xxx",
            type = "set-constant",
            target = "clickPopUpcf",
            value = "1"
        ))
    }

    // 获取指定域名的 JS 过滤器
    fun getJsFilters(domain: String): List<JsFilter> {
        return jsFilters.filter { it.domain == domain || domain.endsWith(".${it.domain}") }
    }

    // 獲取網路層攔截列表
    fun getNetworkBlockList(): List<String> {
        return getRulesList(RuleType.NETWORK_BLOCK)
    }

    // 獲取超連結遮蔽列表
    fun getLinkBlockList(): List<String> {
        return getRulesList(RuleType.LINK_BLOCK)
    }

    // 獲取 iframe 遮蔽列表
    fun getIframeBlockList(): List<String> {
        return getRulesList(RuleType.IFRAME_BLOCK)
    }

    // 獲取重定向阻擋列表
    fun getRedirectBlockList(): List<String> {
        return getRulesList(RuleType.REDIRECT_BLOCK)
    }

    // 获取元素隐藏规则
    fun getElementHideRules(): List<String> {
        return elementHideRules.toList()
    }

    // 生成 Element Hiding CSS 注入脚本（供 WebView evaluateJavascript 使用）
    // 合并通用规则 + 当前域名特定规则
    fun getElementHideCssScript(domain: String): String {
        val allSelectors = LinkedHashSet<String>()

        // 1. 通用规则（所有站点生效）
        allSelectors.addAll(elementHideRules)

        // 2. 域名特定规则（精确匹配 + 父域名匹配）
        val lowerDomain = domain.lowercase()
        domainElementHideRules[lowerDomain]?.let { allSelectors.addAll(it) }
        // 检查子域名：如 sub.ads.example.com 命中 ads.example.com 的规则
        var dotIndex = lowerDomain.indexOf('.')
        while (dotIndex != -1 && dotIndex < lowerDomain.length - 1) {
            val parent = lowerDomain.substring(dotIndex + 1)
            domainElementHideRules[parent]?.let { allSelectors.addAll(it) }
            dotIndex = lowerDomain.indexOf('.', dotIndex + 1)
        }

        if (allSelectors.isEmpty()) return ""

        val selectorStr = allSelectors.joinToString(",") { it.replace("'", "\\'") }
        return "(function(){var s=document.createElement('style');" +
               "s.textContent='$selectorStr{display:none!important}';" +
               "(document.head||document.documentElement).appendChild(s);})();"
    }

    // 獲取通用遮蔽列表（相容舊介面）
    fun getCommonBlockList(): List<String> {
        return getRulesList(RuleType.NETWORK_BLOCK) + getRulesList(RuleType.LINK_BLOCK) +
               getRulesList(RuleType.IFRAME_BLOCK) + getRulesList(RuleType.REDIRECT_BLOCK) +
               blockRules.toList() + blockPatterns.toList()
    }
    
    // 检查是否应该拦截请求
    fun shouldBlock(url: String): Boolean {
        try {
            val uri = Uri.parse(url)
            val host = uri.host?.lowercase() ?: return false

            // 1. 检查白名单（精确 + 父域名）
            if (isDomainInSet(host, whiteList)) return false

            // 2. 检查域名匹配（精确 + 子域名，O(levels) 复杂度）
            if (isDomainInSet(host, blockRules)) return true

            // 3. 检查跟踪器域名
            if (isDomainInSet(host, trackerDomains)) return true

            // 4. 检查 redirect 域名（$redirect=noopjs）
            if (isDomainInSet(host, redirectDomains)) return true

            // 5. 通用广告脚本检测（AdScriptDetector 行为模式识别）
            if (AdScriptDetector.isAdScript(url)) return true
            if (AdScriptDetector.isAdPlatform(host)) return true

            // 6. 检查 URL 路径规则
            val path = uri.path?.lowercase() ?: ""
            val lowerUrl = url.lowercase()
            for (pathRule in urlPathRules) {
                if (path.contains(pathRule) || lowerUrl.contains(pathRule)) return true
            }

            // 7. 检查URL模式匹配
            for (pattern in blockPatterns) {
                if (lowerUrl.contains(pattern)) return true
            }

            return false
        } catch (e: Exception) {
            return false
        }
    }

    // 将域名分解为各级父域名，检查是否命中集合
    // 例如 sub.ads.example.com → [sub.ads.example.com, ads.example.com, example.com]
    // 复杂度 O(levels) 而非 O(n)
    private fun isDomainInSet(host: String, domainSet: Set<String>): Boolean {
        if (domainSet.contains(host)) return true
        var dotIndex = host.indexOf('.')
        while (dotIndex != -1 && dotIndex < host.length - 1) {
            val parent = host.substring(dotIndex + 1)
            if (domainSet.contains(parent)) return true
            dotIndex = host.indexOf('.', dotIndex + 1)
        }
        return false
    }
    
    // 解析JSON规则
    private fun parseJsonRules(json: String) {
        try {
            val jsonObject = JSONObject(json)
            val rulesObject = jsonObject.getJSONObject("rules")
            
            // 解析commonBlock
            if (rulesObject.has("commonBlock")) {
                val array = rulesObject.getJSONArray("commonBlock")
                for (i in 0 until array.length()) {
                    parseRule(array.getString(i))?.let { rule ->
                        when (rule.type) {
                            RuleType.BLOCK -> {
                                if (rule.domain != null) {
                                    blockRules.add(rule.domain)
                                } else if (rule.pattern != null) {
                                    blockPatterns.add(rule.pattern)
                                }
                            }
                            RuleType.WHITELIST -> {
                                if (rule.domain != null) {
                                    whiteList.add(rule.domain)
                                }
                            }
                            RuleType.ELEMENT_HIDE -> {
                                if (rule.pattern != null) {
                                    elementHideRules.add(rule.pattern)
                                }
                            }
                            RuleType.REDIRECT_BLOCK -> {
                                if (rule.domain != null) {
                                    redirectDomains.add(rule.domain)
                                } else if (rule.pattern != null) {
                                    blockPatterns.add(rule.pattern)
                                }
                            }
                            else -> {}
                        }
                        Unit
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse JSON rules", e)
        }
    }

    // 解析单条规则（支持更多 Adblock Plus 格式）
    private fun parseRule(rule: String): AdRule? {
        try {
            if (rule.isBlank() || rule.startsWith("!") || rule.startsWith("[")) {
                return null
            }

            // 分离规则体和选项（$后面的参数），排除 ## 和 #@# 规则
            val dollarIndex = rule.indexOf('$')
            val ruleBody: String
            val options: String
            if (dollarIndex > 0 && !rule.startsWith("##") && !rule.startsWith("#@#")) {
                ruleBody = rule.substring(0, dollarIndex)
                options = rule.substring(dollarIndex + 1)
            } else {
                ruleBody = rule
                options = ""
            }

            // 解析 redirect 规则: *$redirect=noopjs
            if (options.contains("redirect=")) {
                return AdRule(RuleType.REDIRECT_BLOCK, null, ruleBody)
            }

            // 解析 @@ 白名单规则
            if (ruleBody.startsWith("@@")) {
                val inner = ruleBody.substring(2)
                return parseNetworkRule(inner, RuleType.WHITELIST)
            }

            // 解析 #@# 元素隐藏例外
            if (ruleBody.startsWith("#@#")) {
                val selector = ruleBody.substring(3)
                return AdRule(RuleType.ELEMENT_HIDE, null, selector)
            }

            // 解析 ## 元素隐藏
            if (ruleBody.startsWith("##")) {
                val selector = ruleBody.substring(2)
                return AdRule(RuleType.ELEMENT_HIDE, null, selector)
            }

            // 解析 /regex/ 规则
            if (ruleBody.startsWith("/") && ruleBody.endsWith("/") && ruleBody.length > 2) {
                val pattern = ruleBody.substring(1, ruleBody.length - 1)
                return AdRule(RuleType.BLOCK, null, pattern)
            }

            // 解析网络拦截规则
            return parseNetworkRule(ruleBody, RuleType.BLOCK)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse rule: $rule", e)
            return null
        }
    }

    // 解析网络规则（域名/URL模式）
    private fun parseNetworkRule(ruleBody: String, type: RuleType): AdRule? {
        // ||domain.com^ → 域名拦截
        if (ruleBody.startsWith("||") && ruleBody.endsWith("^")) {
            val domain = ruleBody.substring(2, ruleBody.length - 1).lowercase()
            return AdRule(type, domain, null)
        }

        // ||domain.com/path^ → URL 模式拦截
        if (ruleBody.startsWith("||")) {
            val pattern = ruleBody.substring(2).lowercase()
                .replace("*", ".*")
                .replace("^", ".*")
            return AdRule(type, null, pattern)
        }

        // 简单域名
        if (ruleBody.matches(Regex("^[a-zA-Z0-9.-]+$"))) {
            return AdRule(type, ruleBody.lowercase(), null)
        }

        // 包含通配符的URL模式
        if (ruleBody.contains("*") || ruleBody.contains("^")) {
            val pattern = ruleBody.replace("*", ".*").replace("^", ".*")
            return AdRule(type, null, pattern)
        }

        // 其他 URL 模式
        if (ruleBody.isNotBlank()) {
            return AdRule(type, null, ruleBody.lowercase())
        }

        return null
    }

     // 从外部规则源更新规则（并行下载 + 缓存）
    fun updateRulesFromExternalSources(callback: (success: Boolean, message: String) -> Unit) {
        thread {
            try {
                // 检查缓存是否有效
                if (isEasyListCacheValid()) {
                    val cachedRules = loadEasyListCache()
                    if (cachedRules != null && cachedRules.isNotEmpty()) {
                        processAndApplyRules(cachedRules)
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            callback(true, "从缓存加载 ${cachedRules.size} 条规则")
                        }
                        return@thread
                    }
                }

                // 并行下载所有规则源
                val allRules = java.util.Collections.synchronizedList(mutableListOf<String>())
                val successCount = AtomicInteger(0)
                val failCount = AtomicInteger(0)
                val executor = Executors.newFixedThreadPool(EASYLIST_SOURCES.size)
                val latch = CountDownLatch(EASYLIST_SOURCES.size)

                EASYLIST_SOURCES.forEach { url ->
                    executor.execute {
                        try {
                            val connection = URL(url).openConnection() as HttpURLConnection
                            connection.connectTimeout = 10000
                            connection.readTimeout = 10000
                            try {
                                if (connection.responseCode == 200) {
                                    val content = connection.inputStream.bufferedReader().readText()
                                    val rules = content.lines()
                                        .filter { it.isNotBlank() && !it.startsWith("!") && !it.startsWith("[") }
                                    allRules.addAll(rules)
                                    successCount.incrementAndGet()
                                } else {
                                    failCount.incrementAndGet()
                                }
                            } finally {
                                connection.disconnect()
                            }
                        } catch (e: Exception) {
                            failCount.incrementAndGet()
                        } finally {
                            latch.countDown()
                        }
                    }
                }

                latch.await(30, TimeUnit.SECONDS)
                executor.shutdown()

                // 处理规则
                if (allRules.isNotEmpty()) {
                    processAndApplyRules(allRules)
                    saveEasyListCache(allRules)

                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        callback(true, "成功加载 $successCount 个规则源，失败 $failCount 个")
                    }
                } else {
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        callback(false, "所有规则源加载失败，保留现有规则")
                    }
                }
            } catch (e: Exception) {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    callback(false, "更新失败: ${e.message}")
                }
            }
        }
    }

    // 解析并应用规则（使用 AdblockRuleParser 完整解析 EasyList 语法）
    private fun processAndApplyRules(rules: List<String>) {
        val parsed = AdblockRuleParser.parseAll(rules)

        val newBlockRules = HashSet<String>()
        val newBlockPatterns = ArrayList<String>()
        val newWhiteList = HashSet<String>()
        val newGenericHideRules = LinkedHashSet<String>()
        val newDomainHideRules = HashMap<String, MutableSet<String>>()
        val newScriptletRules = mutableListOf<AdblockRuleParser.ParsedRule>()

        for (rule in parsed) {
            when (rule.type) {
                AdblockRuleParser.RuleType.BLOCK -> {
                    rule.domain?.let { newBlockRules.add(it) }
                    if (rule.isRedirect) {
                        rule.domain?.let { redirectDomains.add(it) }
                    }
                }
                AdblockRuleParser.RuleType.WHITELIST -> {
                    rule.domain?.let { newWhiteList.add(it) }
                }
                AdblockRuleParser.RuleType.ELEMENT_HIDE -> {
                    rule.selector?.let { selector ->
                        if (rule.domain.isNullOrBlank()) {
                            // 通用规则（##selector）→ 所有站点生效
                            newGenericHideRules.add(selector)
                        } else {
                            // 域名特定规则（domain##selector）→ 仅目标站点生效
                            newDomainHideRules.getOrPut(rule.domain.lowercase()) { mutableSetOf() }.add(selector)
                        }
                    }
                }
                AdblockRuleParser.RuleType.SCRIPTLET -> {
                    newScriptletRules.add(rule)
                }
                AdblockRuleParser.RuleType.GENERICHIDE -> {
                    rule.domain?.let {
                        generichideDomains.add(it)
                        CosmeticFilter.addGenerichideDomain(it)
                    }
                }
            }
        }

        // 保留 DEFAULT_RULES，追加 EasyList 规则（LinkedHashSet 自动去重）
        if (newBlockRules.isNotEmpty() || newBlockPatterns.isNotEmpty() ||
            newGenericHideRules.isNotEmpty() || newDomainHideRules.isNotEmpty()) {
            blockRules.addAll(newBlockRules)
            blockPatterns.addAll(newBlockPatterns)
            whiteList.addAll(newWhiteList)
            elementHideRules.addAll(newGenericHideRules)
            for ((domain, selectors) in newDomainHideRules) {
                domainElementHideRules.getOrPut(domain) { mutableSetOf() }.addAll(selectors)
            }
            parsedScriptletRules = parsedScriptletRules + newScriptletRules
            saveElementHideRules()
        }
    }
    
    // 保存元素隐藏规则
    private fun saveElementHideRules() {
        try {
            val jsonArray = JSONArray()
            elementHideRules.forEach { rule ->
                jsonArray.put(rule)
            }
             prefs.edit().putString(KEY_ELEMENT_HIDE_RULES, jsonArray.toString()).apply()
         } catch (e: Exception) {
             Log.e(TAG, "Failed to save element hide rules", e)
         }
      }
    
    // 保存 EasyList 规则缓存
    private fun saveEasyListCache(rules: List<String>) {
        try {
            prefs.edit()
                .putString(KEY_EASYLIST_CACHE, rules.joinToString("\n"))
                .putLong(KEY_EASYLIST_CACHE_TIME, System.currentTimeMillis())
                .apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save EasyList cache", e)
        }
    }

    // 加载 EasyList 规则缓存
    private fun loadEasyListCache(): List<String>? {
        val cached = prefs.getString(KEY_EASYLIST_CACHE, null) ?: return null
        return cached.lines().filter { it.isNotBlank() }
    }

    // 检查缓存是否有效（24小时内）
    private fun isEasyListCacheValid(): Boolean {
        val cacheTime = prefs.getLong(KEY_EASYLIST_CACHE_TIME, 0L)
        return (System.currentTimeMillis() - cacheTime) < 24 * 60 * 60 * 1000L
    }
    
    // 添加默认元素隐藏规则
    fun addDefaultElementHideRules() {
        val defaultRules = listOf(
            "##.ad",
            "##.ads",
            "##.adsbox",
            "##.advertisement",
            "##.ad-banner",
            "##.ad-container",
            "##.banner-ad",
            "##.popup-ad",
            "##.google-ad",
            "##.adsbygoogle",
            "##iframe[src*=\"ads\"]",
            "##iframe[src*=\"doubleclick\"]",
            "##iframe[src*=\"googleads\"]",
            "##[class*=\"ad-\"]",
            "##[id*=\"ad-\"]",
            "##[class*=\"banner\"]",
            "##[id*=\"banner\"]",
            "##[class*=\"popup\"]",
            "##[id*=\"popup\"]",
            "##[class*=\"sponsor\"]",
            "##[id*=\"sponsor\"]",
            "##.ytp-ad-module",
            "##.ytp-ad-overlay-container"
        )
        
        // 清空现有规则
        elementHideRules.clear()
        
        // 添加默认规则
        defaultRules.forEach { rule ->
            if (!elementHideRules.contains(rule)) {
                elementHideRules.add(rule)
            }
        }
        
        // 保存规则
        saveElementHideRules()
    }
    
    // 內部方法：根據類型獲取規則列表（會合併 commonBlock）
    private fun getRulesList(type: RuleType): List<String> {
        return try {
            val json = prefs.getString(KEY_RULES_JSON, DEFAULT_RULES) ?: DEFAULT_RULES
            val jsonObject = JSONObject(json)
            val rulesObject = jsonObject.getJSONObject("rules")
            
            val result = mutableListOf<String>()
            
            // 1. 先添加 commonBlock 中的域名（適用於所有類型）
            try {
                val commonArray = rulesObject.getJSONArray("commonBlock")
                for (i in 0 until commonArray.length()) {
                    result.add(commonArray.getString(i))
                }
            } catch (e: Exception) {
                // commonBlock 不存在也沒關係，繼續
            }
            
            // 2. 再添加特定類型的域名
            val key = when (type) {
                RuleType.NETWORK_BLOCK -> "networkBlock"
                RuleType.LINK_BLOCK -> "linkBlock"
                RuleType.IFRAME_BLOCK -> "iframeBlock"
                RuleType.REDIRECT_BLOCK -> "redirectBlock"
                else -> return emptyList() // 不支持的类型，返回空列表
            }
            
            try {
                val array = rulesObject.getJSONArray(key)
                for (i in 0 until array.length()) {
                    val domain = array.getString(i)
                    if (!result.contains(domain)) {  // 避免重複
                        result.add(domain)
                    }
                }
            } catch (e: Exception) {
                // 特定列表不存在也沒關係
            }
            
            result
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    // 從 JSON 更新規則
    fun updateRulesFromJson(json: String): Boolean {
        return try {
            // 驗證 JSON 格式
            val jsonObject = JSONObject(json)
            jsonObject.getString("version")
            val rulesObject = jsonObject.getJSONObject("rules")
            
            // 至少要有 commonBlock 或其中一個特定列表
            val hasCommonBlock = rulesObject.has("commonBlock")
            val hasNetworkBlock = rulesObject.has("networkBlock")
            val hasLinkBlock = rulesObject.has("linkBlock")
            val hasIframeBlock = rulesObject.has("iframeBlock")
            val hasRedirectBlock = rulesObject.has("redirectBlock")
            
            if (!hasCommonBlock && !hasNetworkBlock && !hasLinkBlock && !hasIframeBlock && !hasRedirectBlock) {
                return false  // 完全沒有規則
            }
            
            // 保存
            prefs.edit().apply {
                putString(KEY_RULES_JSON, json)
                putLong(KEY_LAST_UPDATE, System.currentTimeMillis())
                apply()
            }
            true
        } catch (e: Exception) {
            false
        }
    }
    
    // 從雲端更新規則
    fun updateRulesFromCloud(url: String, callback: (success: Boolean, message: String) -> Unit) {
        thread {
            try {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                
                if (connection.responseCode == 200) {
                    val json = connection.inputStream.bufferedReader().readText()
                    val success = updateRulesFromJson(json)
                    
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        if (success) {
                            callback(true, "规则更新成功")
                        } else {
                            callback(false, "JSON 格式错误")
                        }
                    }
                } else {
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        callback(false, "网络错误：${connection.responseCode}")
                    }
                }
            } catch (e: Exception) {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    callback(false, "更新失败：${e.message}")
                }
            }
        }
    }
    
    // 新增規則
    fun addRule(type: RuleType, domain: String): Boolean {
        return try {
            val json = prefs.getString(KEY_RULES_JSON, DEFAULT_RULES) ?: DEFAULT_RULES
            val jsonObject = JSONObject(json)
            val rulesObject = jsonObject.getJSONObject("rules")
            
            val key = when (type) {
                RuleType.NETWORK_BLOCK -> "networkBlock"
                RuleType.LINK_BLOCK -> "linkBlock"
                RuleType.IFRAME_BLOCK -> "iframeBlock"
                RuleType.REDIRECT_BLOCK -> "redirectBlock"
                else -> return false // 不支持的类型
            }
            
            val array = rulesObject.getJSONArray(key)
            
            // 檢查是否已存在
            for (i in 0 until array.length()) {
                if (array.getString(i) == domain) {
                    return false // 已存在
                }
            }
            
            // 新增
            array.put(domain)
            rulesObject.put(key, array)
            jsonObject.put("rules", rulesObject)
            
            updateRulesFromJson(jsonObject.toString())
        } catch (e: Exception) {
            false
        }
    }
    
    // 移除規則
    fun removeRule(type: RuleType, domain: String): Boolean {
        return try {
            val json = prefs.getString(KEY_RULES_JSON, DEFAULT_RULES) ?: DEFAULT_RULES
            val jsonObject = JSONObject(json)
            val rulesObject = jsonObject.getJSONObject("rules")
            
            val key = when (type) {
                RuleType.NETWORK_BLOCK -> "networkBlock"
                RuleType.LINK_BLOCK -> "linkBlock"
                RuleType.IFRAME_BLOCK -> "iframeBlock"
                RuleType.REDIRECT_BLOCK -> "redirectBlock"
                else -> return false // 不支持的类型
            }
            
            val array = rulesObject.getJSONArray(key)
            val newArray = JSONArray()
            
            // 複製除了要刪除的項目
            for (i in 0 until array.length()) {
                val item = array.getString(i)
                if (item != domain) {
                    newArray.put(item)
                }
            }
            
            rulesObject.put(key, newArray)
            jsonObject.put("rules", rulesObject)
            
            updateRulesFromJson(jsonObject.toString())
        } catch (e: Exception) {
            false
        }
    }
    
    // 導出規則為 JSON
    fun exportToJson(): String {
        return prefs.getString(KEY_RULES_JSON, DEFAULT_RULES) ?: DEFAULT_RULES
    }
    
    // 導入規則
    fun importFromJson(json: String): Boolean {
        return updateRulesFromJson(json)
    }
    
    // 获取规则统计
    fun getRulesStats(): Map<String, Int> {
        return mapOf(
            "blockRules" to blockRules.size,
            "blockPatterns" to blockPatterns.size,
            "whiteList" to whiteList.size,
            "elementHideRules" to elementHideRules.size,
            "total" to (blockRules.size + blockPatterns.size + whiteList.size + elementHideRules.size)
        )
    }
    
    // 僅獲取特定類型規則（不包含 commonBlock）
    private fun getRulesListOnly(type: RuleType): List<String> {
        return try {
            val json = prefs.getString(KEY_RULES_JSON, DEFAULT_RULES) ?: DEFAULT_RULES
            val jsonObject = JSONObject(json)
            val rulesObject = jsonObject.getJSONObject("rules")

            val key = when (type) {
                RuleType.NETWORK_BLOCK -> "networkBlock"
                RuleType.LINK_BLOCK -> "linkBlock"
                RuleType.IFRAME_BLOCK -> "iframeBlock"
                RuleType.REDIRECT_BLOCK -> "redirectBlock"
                else -> ""
            }
            if (key.isEmpty()) return emptyList()
            
            val array = rulesObject.getJSONArray(key)
            val list = mutableListOf<String>()
            for (i in 0 until array.length()) {
                list.add(array.getString(i))
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    // 獲取版本和更新時間
    fun getVersion(): String {
        return try {
            val json = prefs.getString(KEY_RULES_JSON, DEFAULT_RULES) ?: DEFAULT_RULES
            JSONObject(json).getString("version")
        } catch (e: Exception) {
            "Unknown"
        }
    }
    
    fun getLastUpdateTime(): Long {
        return prefs.getLong(KEY_LAST_UPDATE, 0L)
    }
    
    // 雲端 URL 管理
    var cloudUrl: String
        get() = prefs.getString(KEY_CLOUD_URL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_CLOUD_URL, value).apply()

    // ========== 多规则文件支持 ==========

    /**
     * 获取所有云端规则 URL 列表
     */
    fun getCloudUrls(): List<String> {
        val urlsString = prefs.getString(KEY_CLOUD_URLS, "") ?: ""
        return urlsString.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    /**
     * 设置多个云端规则 URL
     */
    fun setCloudUrls(urls: List<String>) {
        val urlsString = urls.joinToString(",")
        prefs.edit().putString(KEY_CLOUD_URLS, urlsString).apply()
    }

    /**
     * 添加云端规则 URL
     */
    fun addCloudUrl(url: String) {
        val currentUrls = getCloudUrls().toMutableList()
        if (!currentUrls.contains(url)) {
            currentUrls.add(url)
            setCloudUrls(currentUrls)
        }
    }

    /**
     * 从多个云端 URL 更新规则（并集合并）
     */
    fun updateRulesFromMultipleUrls(callback: (success: Boolean, message: String) -> Unit) {
        val urls = getCloudUrls()
        if (urls.isEmpty()) {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                callback(false, "没有配置云端规则 URL")
            }
            return
        }

        thread {
            val allRules = mutableListOf<String>()
            var successCount = 0
            var failCount = 0

            urls.forEach { url ->
                try {
                    val connection = URL(url).openConnection() as HttpURLConnection
                    connection.connectTimeout = 10000
                    connection.readTimeout = 10000

                    if (connection.responseCode == 200) {
                        val json = connection.inputStream.bufferedReader().readText()
                        allRules.add(json)
                        successCount++
                    } else {
                        failCount++
                    }
                } catch (e: Exception) {
                    failCount++
                }
            }

            // 合并所有规则
            if (allRules.isNotEmpty()) {
                val mergedRules = mergeRules(allRules)
                val success = updateRulesFromJson(mergedRules)

                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    if (success) {
                        callback(true, "成功加载 $successCount 个规则，失败 $failCount 个")
                    } else {
                        callback(false, "规则合并失败")
                    }
                }
            } else {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    callback(false, "所有规则加载失败")
                }
            }
        }
    }

    /**
     * 添加本地规则文件
     */
    fun addLocalRule(name: String, jsonContent: String): Boolean {
        return try {
            val localRules = getLocalRules().toMutableMap()
            localRules[name] = jsonContent
            saveLocalRules(localRules)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 获取所有本地规则
     */
    fun getLocalRules(): Map<String, String> {
        val json = prefs.getString(KEY_LOCAL_RULES, "{}") ?: "{}"
        return try {
            val jsonObject = JSONObject(json)
            val map = mutableMapOf<String, String>()
            jsonObject.keys().forEach { key ->
                map[key] = jsonObject.getString(key)
            }
            map
        } catch (e: Exception) {
            emptyMap()
        }
    }

    /**
     * 保存本地规则
     */
    private fun saveLocalRules(rules: Map<String, String>) {
        val jsonObject = JSONObject()
        rules.forEach { (key, value) ->
            jsonObject.put(key, value)
        }
        prefs.edit().putString(KEY_LOCAL_RULES, jsonObject.toString()).apply()
    }

    /**
     * 删除本地规则
     */
    fun removeLocalRule(name: String): Boolean {
        return try {
            val localRules = getLocalRules().toMutableMap()
            localRules.remove(name)
            saveLocalRules(localRules)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 合并多个规则 JSON（取并集）
     * 优先级：本地 > 云端 > 默认
     */
    private fun mergeRules(jsonList: List<String>): String {
        val commonBlockSet = mutableSetOf<String>()
        val networkBlockSet = mutableSetOf<String>()
        val linkBlockSet = mutableSetOf<String>()
        val iframeBlockSet = mutableSetOf<String>()
        val redirectBlockSet = mutableSetOf<String>()
        val domainsMap = mutableMapOf<String, String>()
        var latestVersion = "1.0.0"

        jsonList.forEach { json ->
            try {
                val jsonObject = JSONObject(json)

                // 版本号取最新的
                if (jsonObject.has("version")) {
                    val version = jsonObject.getString("version")
                    if (compareVersion(version, latestVersion) > 0) {
                        latestVersion = version
                    }
                }

                // 合并 domains
                if (jsonObject.has("domains")) {
                    val domains = jsonObject.getJSONObject("domains")
                    domains.keys().forEach { key ->
                        domainsMap[key] = domains.getString(key)
                    }
                }

                // 合并 rules
                if (jsonObject.has("rules")) {
                    val rules = jsonObject.getJSONObject("rules")

                    if (rules.has("commonBlock")) {
                        val array = rules.getJSONArray("commonBlock")
                        for (i in 0 until array.length()) {
                            commonBlockSet.add(array.getString(i))
                        }
                    }

                    if (rules.has("networkBlock")) {
                        val array = rules.getJSONArray("networkBlock")
                        for (i in 0 until array.length()) {
                            networkBlockSet.add(array.getString(i))
                        }
                    }

                    if (rules.has("linkBlock")) {
                        val array = rules.getJSONArray("linkBlock")
                        for (i in 0 until array.length()) {
                            linkBlockSet.add(array.getString(i))
                        }
                    }

                    if (rules.has("iframeBlock")) {
                        val array = rules.getJSONArray("iframeBlock")
                        for (i in 0 until array.length()) {
                            iframeBlockSet.add(array.getString(i))
                        }
                    }

                    if (rules.has("redirectBlock")) {
                        val array = rules.getJSONArray("redirectBlock")
                        for (i in 0 until array.length()) {
                            redirectBlockSet.add(array.getString(i))
                        }
                    }
                }
            } catch (e: Exception) {
                // 跳过无效的 JSON
            }
        }

        // 构建合并后的 JSON
        val mergedJson = JSONObject()
        mergedJson.put("version", latestVersion)
        mergedJson.put("lastUpdate", java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).format(java.util.Date()))

        // domains
        val domainsJson = JSONObject()
        domainsMap.forEach { (key, value) ->
            domainsJson.put(key, value)
        }
        mergedJson.put("domains", domainsJson)

        // rules
        val rulesJson = JSONObject()
        rulesJson.put("commonBlock", JSONArray(commonBlockSet.toList()))
        rulesJson.put("networkBlock", JSONArray(networkBlockSet.toList()))
        rulesJson.put("linkBlock", JSONArray(linkBlockSet.toList()))
        rulesJson.put("iframeBlock", JSONArray(iframeBlockSet.toList()))
        rulesJson.put("redirectBlock", JSONArray(redirectBlockSet.toList()))
        mergedJson.put("rules", rulesJson)

        return mergedJson.toString(2)
    }

    /**
     * 比较版本号
     */
    private fun compareVersion(v1: String, v2: String): Int {
        val parts1 = v1.split(".").mapNotNull { it.toIntOrNull() }
        val parts2 = v2.split(".").mapNotNull { it.toIntOrNull() }

        val maxLen = maxOf(parts1.size, parts2.size)
        for (i in 0 until maxLen) {
            val p1 = parts1.getOrElse(i) { 0 }
            val p2 = parts2.getOrElse(i) { 0 }
            if (p1 != p2) return p1 - p2
        }
        return 0
    }

    /**
     * 从云端和本地规则合并更新（完整的多规则源支持）
     */
    fun updateFromAllSources(callback: (success: Boolean, message: String) -> Unit) {
        thread {
            val allRulesJson = mutableListOf<String>()

            // 1. 加载云端规则
            val cloudUrls = getCloudUrls()
            cloudUrls.forEach { url ->
                try {
                    val connection = URL(url).openConnection() as HttpURLConnection
                    connection.connectTimeout = 10000
                    connection.readTimeout = 10000

                    if (connection.responseCode == 200) {
                        val json = connection.inputStream.bufferedReader().readText()
                        allRulesJson.add(json)
                    }
                } catch (e: Exception) {
                    // 跳过失败的 URL
                }
            }

            // 2. 加载本地规则
            val localRules = getLocalRules()
            allRulesJson.addAll(localRules.values)

            // 3. 添加默认规则（优先级最低）
            allRulesJson.add(DEFAULT_RULES)

            // 4. 合并所有规则
            if (allRulesJson.isNotEmpty()) {
                val mergedRules = mergeRules(allRulesJson)
                val success = updateRulesFromJson(mergedRules)

                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    if (success) {
                        callback(true, "规则更新成功（云端:${cloudUrls.size} + 本地:${localRules.size}）")
                    } else {
                        callback(false, "规则合并失败")
                    }
                }
            } else {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    callback(false, "没有可用的规则")
                }
            }
        }
    }
}
