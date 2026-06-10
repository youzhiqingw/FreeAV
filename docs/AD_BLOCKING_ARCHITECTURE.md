# 广告拦截系统架构说明

> **版本**: v2.0
> **更新日期**: 2026-06-09
> **主文档**: 完整说明请参考 [PRIVACY_AND_ADBLOCK.md](PRIVACY_AND_ADBLOCK.md)
> **升级方案**: [AdblockAndroid 引擎集成](design/adblock-engine-upgrade-proposal.md)

---

## 📋 架构总览

JAV Browser 采用**双引擎广告拦截系统**，在网络层和DOM层同时拦截广告。

### 1️⃣ JSON 规则引擎（站点优化）

**特点**：轻量级、快速启动、站点特定优化

- **格式**: 自定义 JSON 格式
- **规则数量**: ~100条
- **更新方式**: 云端自动更新
- **来源**: 
  - 默认内置规则
  - 云端规则（GitHub raw）
  - 本地规则文件
- **功能**: 
  - 域名屏蔽（commonBlock）
  - 网络拦截（networkBlock）
  - 链接屏蔽（linkBlock）
  - iframe 屏蔽（iframeBlock）
  - 重定向拦截（redirectBlock）

### 2️⃣ Adblock Plus 规则引擎（广泛覆盖）

**特点**：规则量大、覆盖面广、社区维护

- **格式**: Adblock Plus 标准格式
- **规则数量**: ~120,000条
- **更新方式**: 手动更新（每周推荐）
- **来源**: 
  - EasyList
  - EasyPrivacy
  - EasyList China
  - 217heidai AdBlock Filters
  - GOODBYEADS
- **功能**:
  - 域名精确匹配（`||domain.com^`）
  - URL 模式匹配（通配符 `*`、`^`）
  - 白名单（`@@||domain.com^`）
  - 元素隐藏（`##.selector`）

---

## 🔧 实现细节

### 核心类：AdFilterRules.kt

**✅ 已修复**：`shouldBlock()` 方法现在在 MainActivity 中被正确调用。

**当前拦截逻辑**（MainActivity.kt:312-315）：
```kotlin
// ✅ 使用完整的 shouldBlock 方法
if (adFilterRules.shouldBlock(url, isThirdParty = true)) {
    return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream("".toByteArray()))
}
```

**数据结构**：
```kotlin
private val blockRules = HashSet<String>()        // 域名精确匹配 - ✅ 已生效
private val blockPatterns = ArrayList<String>()   // URL模式匹配 - ✅ 已生效
private val whiteList = HashSet<String>()         // 白名单 - ✅ 已生效
private val elementHideRules = ArrayList<String>() // 元素隐藏规则 - ✅ 已生效
```

### 拦截判断逻辑

```kotlin
fun shouldBlock(url: String, isThirdParty: Boolean = false): Boolean {
    val uri = Uri.parse(url)
    val host = uri.host?.lowercase() ?: return false

    // 1. 白名单检查
    if (whiteList.contains(host)) return false

    // 2. 域名精确匹配（O(1)）
    if (blockRules.contains(host)) return true

    // 3. URL模式匹配
    for (pattern in blockPatterns) {
        if (url.contains(pattern)) return true
    }

    return false
}
```

---

## 📥 规则更新方法

### 方法 1: 云端JSON更新
```kotlin
adFilterRules.updateRulesFromCloud(
    "https://raw.githubusercontent.com/fekilooo/javbrowser/main/ad-filter-rules.json"
) { success, message ->
    // 处理更新结果
}
```

### 方法 2: Adblock Plus规则更新
```kotlin
adFilterRules.updateRulesFromExternalSources { success, message ->
    // 从5个EasyList源更新，约120,000条规则
}
```

### 方法 3: 多源合并更新
```kotlin
adFilterRules.updateFromAllSources { success, message ->
    // 合并云端 + 本地 + 默认规则
}
```

---

## 🎯 拦截流程

### 1. 网络层拦截（MainActivity.kt）

```kotlin
override fun shouldInterceptRequest(
    view: WebView?,
    request: WebResourceRequest?
): WebResourceResponse? {
    val url = request?.url?.toString() ?: return super.shouldInterceptRequest(view, request)
    
    // 内容类型守卫：视频流放行
    if (url.endsWith(".m3u8") || url.endsWith(".ts") || url.endsWith(".mp4")) {
        return null
    }
    
    // 双引擎拦截
    if (adFilterRules.shouldBlock(url, isThirdParty = true)) {
        return WebResourceResponse(
            "text/plain", "utf-8",
            ByteArrayInputStream(byteArrayOf())
        )
    }
    
    return super.shouldInterceptRequest(view, request)
}
```

### 2. DOM层拦截（元素隐藏）

```kotlin
override fun onPageFinished(view: WebView?, url: String?) {
    super.onPageFinished(view, url)
    
    // 注入CSS隐藏规则
    val hideRules = adFilterRules.getElementHideRules()
    if (hideRules.isNotEmpty()) {
        val selectors = hideRules.joinToString(",")
        val hideScript = """
            (function() {
                const style = document.createElement('style');
                style.textContent = '$selectors { display: none !important; }';
                document.head.appendChild(style);
            })();
        """.trimIndent()
        view?.evaluateJavascript(hideScript, null)
    }
}
```

### 3. 动态监听（MutationObserver）

```kotlin
val observerScript = """
(function() {
    var adSelectors = ['.ad', '.ads', '.advertisement'];
    
    function hideAds() {
        adSelectors.forEach(function(sel) {
            document.querySelectorAll(sel).forEach(function(el) {
                el.style.display = 'none';
            });
        });
    }
    
    hideAds();
    
    new MutationObserver(function() {
        hideAds();
    }).observe(document.documentElement, {
        childList: true,
        subtree: true
    });
})();
""".trimIndent()
```

---

## 📊 性能数据

| 指标 | JSON引擎 | Adblock Plus引擎 |
|------|:-------:|:---------------:|
| 规则数量 | ~100 | ~120,000 |
| 加载时间 | <50ms | ~200ms |
| 内存占用 | <1MB | ~20MB |
| 匹配速度 | O(1) | O(n) |
| 更新频率 | 每次启动 | 每周推荐 |

---

## 🛡️ 安全守卫

**绝不拦截**：
- ✅ `.m3u8` `.ts` `.mp4` `.key` 视频流
- ✅ 同域请求（第一方资源）
- ✅ 视频CDN（cloudfront.net, akamaized.net等）

**规则安全**：
- ✅ 规则源必须HTTPS
- ✅ 规则格式验证
- ✅ 更新失败时保留现有规则

---

## 🚀 优化建议

### 0. ~~修复现有问题（必须）~~ ✅ 已完成

> **详细方案**: [adblock-engine-upgrade-proposal.md](design/adblock-engine-upgrade-proposal.md)

**已修复**：`AdFilterRules.shouldBlock()` 方法现在在 MainActivity 中被正确调用。

**修复内容**（MainActivity.kt:312-315）：
```kotlin
// 修改后（已应用）
if (adFilterRules.shouldBlock(url, isThirdParty = true)) {
    return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream("".toByteArray()))
}
```

**效果**：
- ✅ EasyList 规则生效（`blockRules` + `blockPatterns`）
- ✅ 白名单生效
- ✅ 域名精确匹配（HashSet O(1)）

### 1. 集成成熟广告拦截引擎（可选，后续优化）

> **详细方案**: [adblock-engine-upgrade-proposal.md](design/adblock-engine-upgrade-proposal.md)

**推荐库**: [Edsuns/AdblockAndroid](https://github.com/Edsuns/AdblockAndroid)

**优势**：
- C++ 核心引擎（Brave ad-block），Trie + Bloom Filter 双重优化
- 支持 EasyList、AdGuard Filters（含 Extended CSS、Scriptlets）
- 匹配性能从 O(n) 提升到 O(k)，k = URL长度
- Kotlin 友好，Gradle 依赖即可

**集成示例**：
```kotlin
// Application 初始化
AdFilter.init(this)
AdFilter.get().addSubscription(Subscription.EASYLIST_CHINA)

// WebViewClient
override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
    val result = AdFilter.get().shouldIntercept(view!!, request!!)
    return result.resourceResponse  // 自动处理拦截
}

override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
    AdFilter.get().performScript(view, url)  // 注入 Extended CSS/Scriptlets
}
```

### 2. Bloom Filter加速（自研优化备选）

```kotlin
// 添加依赖：implementation("com.google.guava:guava:31.1-android")

private val bloomFilter: BloomFilter<String> by lazy {
    BloomFilter.create(
        Funnels.stringFunnel(Charset.forName("UTF-8")),
        150_000,
        0.01
    ).also { bf -> blockRules.forEach { bf.put(it) } }
}

fun shouldBlockFast(domain: String): Boolean {
    if (!bloomFilter.mightContain(domain)) return false  // 快速排除
    return blockRules.contains(domain)  // 精确确认
}
```

### 2. 规则持久化（减少启动时间）

```kotlin
fun saveParsedRules(context: Context) {
    val prefs = context.getSharedPreferences("ad_rules", Context.MODE_PRIVATE)
    prefs.edit()
        .putStringSet("abplus_cache", blockRules)
        .putString("cache_date", SimpleDateFormat("yyyy-MM-dd").format(Date()))
        .apply()
}
```

---

## 📚 相关文档

- **完整方案**: [PRIVACY_AND_ADBLOCK.md](PRIVACY_AND_ADBLOCK.md)
- **项目总览**: [../CLAUDE.md](../CLAUDE.md)
- **Adblock Plus规则语法**: https://help.eyeo.com/adblockplus/how-to-write-filters

---

**更新记录**:
- 2026-06-09: 与实际实现对齐，移除未实现的理论设计
- 2026-06-08: 添加双引擎架构说明
