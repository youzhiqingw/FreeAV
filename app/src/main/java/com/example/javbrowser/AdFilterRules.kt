package com.example.javbrowser

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import android.util.Log
import java.net.HttpURLConnection
import java.net.URL
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

        // Cloud update URL - can be configured in Settings
        const val DEFAULT_CLOUD_URL = "https://raw.githubusercontent.com/fekilooo/javbrowser/refs/heads/main/ad-filter-rules.json"
        
        // EasyList规则源
        private val EASYLIST_SOURCES = listOf(
            "https://easylist.to/easylist/easylist.txt",
            "https://easylist.to/easylist/easyprivacy.txt",
            "https://easylist-downloads.adblockplus.org/easylistchina+easylist.txt",
            "https://raw.githubusercontent.com/217heidai/adblockfilters/main/rules/adblock_dns.txt",
            "https://raw.githubusercontent.com/GOODBYEADS/GOODBYEADS/master/blocklist.txt"
        )

        // 預設規則（首次安裝時使用）
        private val DEFAULT_RULES = """
        {
          "version": "2.1.0",
          "lastUpdate": "2025-11-28T13:30:00Z",
          "domains": {
            "missav": "missav.ws",
            "jable": "jable.tv",
            "rou_video": "rouva3.xyz",
            "avjoy": "avjoy.me"
          },
          "rules": {
            "commonBlock": [
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
              "lazada"
            ],
            "networkBlock": [],
            "linkBlock": [],
            "iframeBlock": [],
            "redirectBlock": []
          }
        }
        """.trimIndent()
    }
    
    // 新增规则数据结构
    private val blockRules = HashSet<String>()       // 域名精确匹配
    private val blockPatterns = ArrayList<String>()  // URL模式匹配
    private val whiteList = HashSet<String>()        // 白名单
    private val elementHideRules = ArrayList<String>() // 元素隐藏规则
    
    init {
        // 如果是首次使用，載入預設規則
        if (!prefs.contains(KEY_RULES_JSON)) {
            updateRulesFromJson(DEFAULT_RULES)
        }
        
        // 初始化规则
        initializeRules()
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
        return elementHideRules
    }

    // 獲取通用遮蔽列表（相容舊介面）
    fun getCommonBlockList(): List<String> {
        return getRulesList(RuleType.NETWORK_BLOCK) + getRulesList(RuleType.LINK_BLOCK) +
               getRulesList(RuleType.IFRAME_BLOCK) + getRulesList(RuleType.REDIRECT_BLOCK) +
               blockRules.toList() + blockPatterns.toList()
    }
    
    // 检查是否应该拦截请求
    fun shouldBlock(url: String, isThirdParty: Boolean = false): Boolean {
        try {
            val uri = Uri.parse(url)
            val host = uri.host?.lowercase() ?: return false

            // 1. 检查白名单
            if (whiteList.contains(host)) return false

            // 2. 检查域名精确匹配
            if (blockRules.contains(host)) return true

            // 3. 检查URL模式匹配
            for (pattern in blockPatterns) {
                if (url.contains(pattern)) return true
            }

            // 4. 第三方请求额外检查（预留扩展点）
            if (isThirdParty) {
                for (pattern in blockPatterns) {
                    if (host.contains(pattern)) return true
                }
            }

            return false
        } catch (e: Exception) {
            return false
        }
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
                                if (rule.domain != null) blockRules.add(rule.domain)
                                else if (rule.pattern != null) blockPatterns.add(rule.pattern)
                            }
                            RuleType.WHITELIST -> {
                                if (rule.domain != null) whiteList.add(rule.domain)
                            }
                            RuleType.ELEMENT_HIDE -> {
                                if (rule.pattern != null) elementHideRules.add(rule.pattern)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse JSON rules", e)
        }
    }

    // 解析单条规则
    private fun parseRule(rule: String): AdRule? {
        try {
            // 跳过注释和无效规则
            if (rule.isBlank() || rule.startsWith("!") || rule.startsWith("[")) {
                return null
            }
            
            // 解析 ||domain.com^
            if (rule.startsWith("||") && rule.endsWith("^")) {
                val domain = rule.substring(2, rule.length - 1).lowercase()
                return AdRule(RuleType.BLOCK, domain, null)
            }
            
            // 解析 @@||domain.com^
            if (rule.startsWith("@@||") && rule.endsWith("^")) {
                val domain = rule.substring(4, rule.length - 1).lowercase()
                return AdRule(RuleType.WHITELIST, domain, null)
            }
            
            // 解析 ##.ad-class
            if (rule.startsWith("##")) {
                val selector = rule.substring(2)
                return AdRule(RuleType.ELEMENT_HIDE, null, selector)
            }
            
            // 解析简单域名
            if (rule.matches(Regex("^[a-zA-Z0-9.-]+$"))) {
                return AdRule(RuleType.BLOCK, rule.lowercase(), null)
            }
            
            // 解析包含通配符的URL
            if (rule.contains("*") || rule.contains("^")) {
                val pattern = rule.replace("*", ".*").replace("^", ".*")
                return AdRule(RuleType.BLOCK, null, pattern)
            }
            
             return null
         } catch (e: Exception) {
             Log.e(TAG, "Failed to parse rule", e)
             return null
         }
     }

     // 从外部规则源更新规则
    fun updateRulesFromExternalSources(callback: (success: Boolean, message: String) -> Unit) {
        thread {
            try {
                val allRules = mutableListOf<String>()
                var successCount = 0
                var failCount = 0
                
                EASYLIST_SOURCES.forEach { url ->
                    try {
                        val connection = URL(url).openConnection() as HttpURLConnection
                        connection.connectTimeout = 10000
                        connection.readTimeout = 10000
                        
                        if (connection.responseCode == 200) {
                            val content = connection.inputStream.bufferedReader().readText()
                            val rules = content.lines()
                                .filter { it.isNotBlank() && !it.startsWith("!") && !it.startsWith("[") }
                            allRules.addAll(rules)
                            successCount++
                        } else {
                            failCount++
                        }
                    } catch (e: Exception) {
                        failCount++
                    }
                }
                
                // 处理规则
                if (allRules.isNotEmpty()) {
                    val newBlockRules = HashSet<String>()
                    val newBlockPatterns = ArrayList<String>()
                    val newWhiteList = HashSet<String>()
                    val newElementHideRules = ArrayList<String>()

                    allRules.forEach { rule ->
                        parseRule(rule)?.let { adRule ->
                            when (adRule.type) {
                                RuleType.BLOCK -> {
                                    if (adRule.domain != null) newBlockRules.add(adRule.domain)
                                    else if (adRule.pattern != null) newBlockPatterns.add(adRule.pattern)
                                }
                                RuleType.WHITELIST -> {
                                    if (adRule.domain != null) newWhiteList.add(adRule.domain)
                                }
                                RuleType.ELEMENT_HIDE -> {
                                    if (adRule.pattern != null) newElementHideRules.add(adRule.pattern)
                                }
                            }
                        }
                    }

                    // 仅在有有效规则时才替换，避免清空后失败
                    if (newBlockRules.isNotEmpty() || newBlockPatterns.isNotEmpty()) {
                        blockRules.clear()
                        blockPatterns.clear()
                        whiteList.clear()
                        elementHideRules.clear()
                        blockRules.addAll(newBlockRules)
                        blockPatterns.addAll(newBlockPatterns)
                        whiteList.addAll(newWhiteList)
                        elementHideRules.addAll(newElementHideRules)
                        saveElementHideRules()
                    }

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
    
    /**
     * 讀取 domains 設定區塊（用於動態網域替換）
     * 回傳格式：Map<"missav" -> "missav.ws", ...>
     */
    fun getDomains(): Map<String, String> {
        return try {
            val json = prefs.getString(KEY_RULES_JSON, DEFAULT_RULES) ?: DEFAULT_RULES
            val jsonObject = JSONObject(json)
            if (!jsonObject.has("domains")) return emptyMap()
            val domainsObject = jsonObject.getJSONObject("domains")
            val map = mutableMapOf<String, String>()
            domainsObject.keys().forEach { key ->
                map[key] = domainsObject.getString(key)
            }
            map
        } catch (e: Exception) {
            emptyMap()
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
    
    // 规则类型枚举
    private enum class RuleType {
        BLOCK,       // 拦截规则
        WHITELIST,   // 白名单规则
        ELEMENT_HIDE // 元素隐藏规则
    }
    
    // 规则数据结构
    private data class AdRule(
        val type: RuleType,
        val domain: String?,    // 匹配的域名
        val pattern: String?    // 匹配的模式或选择器
    )
    
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
            }
            
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
        get() = prefs.getString(KEY_CLOUD_URL, DEFAULT_CLOUD_URL) ?: DEFAULT_CLOUD_URL
        set(value) = prefs.edit().putString(KEY_CLOUD_URL, value).apply()

    // ========== 多规则文件支持 ==========

    /**
     * 获取所有云端规则 URL 列表
     */
    fun getCloudUrls(): List<String> {
        val urlsString = prefs.getString(KEY_CLOUD_URLS, DEFAULT_CLOUD_URL) ?: DEFAULT_CLOUD_URL
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
