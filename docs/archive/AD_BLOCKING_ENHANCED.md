# Android WebView 广告拦截与隐私保护增强方案

**适用项目**: JAV Browser (com.example.freeavbrowser)  
**文档版本**: v2.0  
**更新日期**: 2026-06-09  
**基于版本**: v1.1.5 双引擎架构

---

## 目录

1. [现有架构回顾与评估](#一现有架构回顾与评估)
2. [DOM结构适配广告屏蔽可行性分析](#二dom结构适配广告屏蔽可行性分析)
3. [URL-DOM规则映射系统设计](#三url-dom规则映射系统设计)
4. [站点专项DOM适配策略](#四站点专项dom适配策略)
5. [动态广告检测（MutationObserver）](#五动态广告检测mutationobserver)
6. [隐私保护增强方案](#六隐私保护增强方案)
7. [性能优化建议](#七性能优化建议)
8. [规则维护与更新策略](#八规则维护与更新策略)
9. [已知局限与风险](#九已知局限与风险)
10. [优先级路线图](#十优先级路线图)

---

## 一、现有架构回顾与评估

### 1.1 双引擎能力矩阵

| 能力 | JSON规则引擎 | Adblock Plus引擎 | 缺口 |
|------|:-----------:|:----------------:|:----:|
| 网络请求拦截 | ✅ ~100条 | ✅ ~120,000条 | 无 |
| 静态元素隐藏 | ❌ | ✅ `##.selector` | 规则量不足 |
| 动态注入广告 | ❌ | ❌ | **缺口** |
| URL粒度DOM规则 | ❌ | ❌ | **缺口** |
| 弹窗自动关闭 | ✅（JS注入） | ❌ | 覆盖不全 |
| 视频播放前贴片 | ❌ | ❌ | **缺口** |
| 反指纹追踪 | ❌ | ❌ | **缺口** |

### 1.2 核心瓶颈

现有双引擎主要工作在**网络层**（拦截请求）和**静态CSS层**（隐藏已知选择器），对以下场景力不从心：

- 广告元素与正常内容共用相同 CSS 类名（动态混淆）
- 广告由 JavaScript 在页面加载后动态注入 DOM
- 视频播放器内嵌的贴片广告（在 `<video>` 或 iframe 层）
- 基于用户行为触发的弹窗（延迟弹出、滚动触发）

---

## 二、DOM结构适配广告屏蔽可行性分析

### 2.1 结论：完全可行，且是当前最值得投入的方向

Android WebView 提供了完整的 JavaScript 执行环境，可以通过以下三种方式操纵 DOM，三者配合使用效果最佳：

```
网络层（已有）  →  阻止广告资源加载
CSS注入层（已有，需扩展）  →  隐藏已知广告容器
DOM操纵层（待实现）  →  处理动态注入、弹窗、播放器广告
```

### 2.2 技术可行性依据

**Android WebView 支持的核心 API：**

```kotlin
// 页面加载完成后执行 JS
webView.evaluateJavascript(script, null)

// 注入持久性 JS（在每个页面顶部执行）
webView.addJavascriptInterface(bridge, "AndroidBridge")

// 在 shouldInterceptRequest 中注入 CSS
webView.settings.apply {
    javaScriptEnabled = true
    domStorageEnabled = true
}
```

**MutationObserver（浏览器原生 API）：**  
可监听 DOM 变化，当广告元素被动态插入时立即隐藏，无需轮询。

### 2.3 三种DOM级拦截方案对比

| 方案 | 原理 | 精准度 | 维护成本 | 适用场景 |
|------|------|:------:|:--------:|---------|
| **静态CSS注入** | 注入已知选择器的 `display:none` | 中 | 低 | 稳定的广告容器类名 |
| **URL-DOM规则映射** | 按 URL 匹配注入站点专属 JS | 高 | 中 | 目标站点深度适配 |
| **MutationObserver** | 监听 DOM 变化实时清除 | 高 | 低 | 动态注入广告 |

**推荐组合**：静态CSS + URL-DOM映射 + MutationObserver，三层叠加。

---

## 三、URL-DOM规则映射系统设计

### 3.1 扩展后的 ad-filter-rules.json 格式

在现有 JSON 规则结构基础上，新增 `domRules` 节点：

```json
{
  "version": "1.2.0",
  "lastUpdate": "2026-06-09",
  "domains": { },
  "rules": { },
  "domRules": {
    "missav": {
      "urlPatterns": ["missav.ai", "missav.ws", "missav.com"],
      "hideSelectors": [
        ".ad-slot",
        "[id^='div-gpt-ad']",
        "[data-ad-unit]",
        ".popup-overlay",
        "#interstitial-wrapper",
        ".video-ads-container",
        "div[class*='banner']",
        "div[class*='sponsor']"
      ],
      "removeSelectors": [
        "#floating-banner",
        ".redirect-overlay"
      ],
      "clickDismiss": [
        ".close-ad-btn",
        "[aria-label='关闭广告']",
        ".modal-close",
        "#dismiss-button"
      ],
      "attrBlock": [
        { "selector": "a[href*='tracker']", "attr": "href" },
        { "selector": "a[onclick*='popup']", "attr": "onclick" }
      ],
      "injectScript": null
    },
    "jable": {
      "urlPatterns": ["jable.tv"],
      "hideSelectors": [
        ".sidebar-ad",
        "#top-banner",
        ".ad-notice",
        "div[id^='google_ads_iframe']"
      ],
      "removeSelectors": [".age-verify-overlay"],
      "clickDismiss": [".age-confirm-btn"],
      "attrBlock": [],
      "injectScript": null
    },
    "generic": {
      "urlPatterns": ["*"],
      "hideSelectors": [
        "[id*='google_ads']",
        "[class*='adsense']",
        "[id*='adsbygoogle']",
        "iframe[src*='doubleclick']",
        "iframe[src*='googlesyndication']",
        "div[data-ad]"
      ],
      "removeSelectors": [],
      "clickDismiss": [],
      "attrBlock": [],
      "injectScript": null
    }
  }
}
```

### 3.2 规则类型说明

| 字段 | 类型 | 说明 |
|------|------|------|
| `urlPatterns` | `List<String>` | URL 包含任一字符串时命中此规则组 |
| `hideSelectors` | `List<String>` | CSS 选择器，匹配元素设为 `display:none` |
| `removeSelectors` | `List<String>` | CSS 选择器，匹配元素调用 `remove()` 彻底删除 |
| `clickDismiss` | `List<String>` | 自动点击关闭按钮的选择器列表 |
| `attrBlock` | `List<AttrRule>` | 清除特定元素的危险属性（href/onclick） |
| `injectScript` | `String?` | 站点专属 JS 片段（可为 null） |

### 3.3 Kotlin 实现：DomRuleEngine.kt

```kotlin
/**
 * DOM 規則引擎
 * 根據當前 URL 匹配規則，生成注入 JS
 */
class DomRuleEngine(private val adFilterRules: AdFilterRules) {

    /**
     * 根據 URL 生成完整的 DOM 清理腳本
     */
    fun buildInjectionScript(currentUrl: String): String {
        val matchedRules = adFilterRules.getDomRulesForUrl(currentUrl)
        if (matchedRules.isEmpty()) return ""

        val sb = StringBuilder()
        sb.append("(function() {\n")
        sb.append("  'use strict';\n\n")

        // 1. 合并所有命中规则的选择器
        val allHide = matchedRules.flatMap { it.hideSelectors }.distinct()
        val allRemove = matchedRules.flatMap { it.removeSelectors }.distinct()
        val allDismiss = matchedRules.flatMap { it.clickDismiss }.distinct()
        val allAttrBlock = matchedRules.flatMap { it.attrBlock }.distinct()

        // 2. CSS 隐藏注入
        if (allHide.isNotEmpty()) {
            val selectors = allHide.joinToString(",\n  ") { "\"$it\"" }
            sb.append("""
  // --- 隱藏廣告元素 ---
  var hideSelectors = [$selectors];
  var hideStyle = document.createElement('style');
  hideStyle.id = 'jb-ad-hide';
  hideStyle.textContent = hideSelectors.join(',') + ' { display:none!important; visibility:hidden!important; }';
  document.documentElement.appendChild(hideStyle);

""")
        }

        // 3. 彻底删除元素
        if (allRemove.isNotEmpty()) {
            val selectors = allRemove.joinToString(",\n  ") { "\"$it\"" }
            sb.append("""
  // --- 刪除廣告元素 ---
  var removeSelectors = [$selectors];
  function removeElements(selectors) {
    selectors.forEach(function(sel) {
      document.querySelectorAll(sel).forEach(function(el) { el.remove(); });
    });
  }
  removeElements(removeSelectors);

""")
        }

        // 4. 自动点击关闭按钮
        if (allDismiss.isNotEmpty()) {
            val selectors = allDismiss.joinToString(",\n  ") { "\"$it\"" }
            sb.append("""
  // --- 自動關閉彈窗 ---
  var dismissSelectors = [$selectors];
  function clickDismiss(selectors) {
    selectors.forEach(function(sel) {
      var btn = document.querySelector(sel);
      if (btn) { btn.click(); }
    });
  }
  setTimeout(function() { clickDismiss(dismissSelectors); }, 500);
  setTimeout(function() { clickDismiss(dismissSelectors); }, 1500);

""")
        }

        // 5. 属性清除
        if (allAttrBlock.isNotEmpty()) {
            allAttrBlock.forEach { rule ->
                sb.append("""
  // --- 屬性清除: ${rule.selector} ---
  document.querySelectorAll("${rule.selector}").forEach(function(el) {
    el.removeAttribute("${rule.attr}");
  });

""")
            }
        }

        sb.append("})();\n")
        return sb.toString()
    }

    /**
     * 生成 MutationObserver 持久监听脚本
     * 在 onPageStarted 时注入，持续监听整个页面生命周期
     */
    fun buildMutationObserverScript(currentUrl: String): String {
        val matchedRules = adFilterRules.getDomRulesForUrl(currentUrl)
        if (matchedRules.isEmpty()) return ""

        val allHide = matchedRules.flatMap { it.hideSelectors }.distinct()
        val allRemove = matchedRules.flatMap { it.removeSelectors }.distinct()
        if (allHide.isEmpty() && allRemove.isEmpty()) return ""

        val hideJson = allHide.joinToString(",") { "\"$it\"" }
        val removeJson = allRemove.joinToString(",") { "\"$it\"" }

        return """
(function() {
  var hideSelectors = [$hideJson];
  var removeSelectors = [$removeJson];
  
  function cleanNode(node) {
    if (node.nodeType !== 1) return;
    var el = node;
    hideSelectors.forEach(function(sel) {
      try {
        if (el.matches(sel)) { el.style.setProperty('display','none','important'); }
        el.querySelectorAll(sel).forEach(function(c) {
          c.style.setProperty('display','none','important');
        });
      } catch(e) {}
    });
    removeSelectors.forEach(function(sel) {
      try {
        if (el.matches(sel)) { el.remove(); return; }
        el.querySelectorAll(sel).forEach(function(c) { c.remove(); });
      } catch(e) {}
    });
  }

  var observer = new MutationObserver(function(mutations) {
    mutations.forEach(function(m) {
      m.addedNodes.forEach(function(node) { cleanNode(node); });
    });
  });

  observer.observe(document.documentElement, {
    childList: true,
    subtree: true
  });
  
  // 窗口卸载时断开，防内存泄漏
  window.addEventListener('unload', function() { observer.disconnect(); });
})();
""".trimIndent()
    }
}
```

### 3.4 在 MainActivity 中的接入点

```kotlin
// WebViewClient 中两处注入

override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
    super.onPageStarted(view, url, favicon)
    // 注入 MutationObserver（尽早注入，监听整个页面生命周期）
    val observerScript = domRuleEngine.buildMutationObserverScript(url)
    if (observerScript.isNotEmpty()) {
        view.evaluateJavascript(observerScript, null)
    }
}

override fun onPageFinished(view: WebView, url: String) {
    super.onPageFinished(view, url)

    // 已有：Adblock Plus 元素隐藏 CSS
    injectElementHideCSS(view)

    // 新增：URL 专属 DOM 清理脚本
    val domScript = domRuleEngine.buildInjectionScript(url)
    if (domScript.isNotEmpty()) {
        view.evaluateJavascript(domScript, null)
    }

    // 已有：通用 JS 弹窗清理（保留）
    injectPopupKiller(view)
}
```

---

## 四、站点专项DOM适配策略

### 4.1 如何获取目标站点的 DOM 规则

**步骤：**

1. 在 `AndroidManifest.xml` 的开发构建中临时移除 `FLAG_SECURE`
2. 在 `MainActivity` 中启用 WebView 调试：
   ```kotlin
   // 仅 Debug 构建开启
   if (BuildConfig.DEBUG) {
       WebView.setWebContentsDebuggingEnabled(true)
   }
   ```
3. Chrome DevTools 连接（`chrome://inspect`），在 Elements 面板分析广告结构
4. 记录广告元素的 `id`、`class`、`data-*` 属性特征
5. 写入对应站点的 `domRules` 规则

### 4.2 常见广告 DOM 模式速查

```
Google AdSense:     [id^="div-gpt-ad"], .adsbygoogle, [data-ad-client]
Outbrain/Taboola:   div[id^="OUTBRAIN"], div[id^="taboola-"]
弹窗遮罩:           .modal-backdrop, .overlay, [role="dialog"][class*="ad"]
视频贴片:           .preroll-container, .video-ad, .ima-ad-container
悬浮广告:           div[style*="position:fixed"][style*="z-index"]
延迟弹窗:           body > div[style*="display:block"][style*="z-index:99"]
```

### 4.3 MISSAV 专项分析

MISSAV 使用 Dean Edwards packer 混淆视频 URL（已在 `VideoExtractor` 处理），其广告特征：

- 播放器外包裹的横幅 `.outstream-wrapper`
- 右下角悬浮 `#floating-widget`
- 页面跳转劫持：`window.onbeforeunload` 触发弹窗

```kotlin
// 针对 MISSAV 的额外保护：禁用页面跳转劫持
val missavScript = """
(function() {
  if (location.hostname.includes('missav')) {
    window.onbeforeunload = null;
    window.onunload = null;
    // 禁用新窗口打开
    var origOpen = window.open;
    window.open = function(url, name, features) {
      if (url && (url.includes('track') || url.includes('click'))) return null;
      return origOpen.apply(this, arguments);
    };
  }
})();
""".trimIndent()
```

### 4.4 通用防弹窗保护脚本

```kotlin
val universalScript = """
(function() {
  // 禁用 alert/confirm/prompt 弹窗
  window.alert = function() {};
  window.confirm = function() { return false; };
  window.prompt = function() { return null; };
  
  // 清除定时弹窗（在页面加载后 3 秒内的定时器可能是广告弹窗）
  var maxId = setTimeout(function(){}, 0);
  for (var i = 1; i <= maxId; i++) { clearTimeout(i); }
})();
""".trimIndent()
```

---

## 五、动态广告检测（MutationObserver）

### 5.1 为什么需要 MutationObserver

部分广告采用以下策略绕过静态规则：

- 页面加载 2-5 秒后才注入广告元素
- 用户滚动到特定位置后触发
- 视频播放开始时插入 overlay
- AJAX 路由切换后重新注入（SPA 网站）

MutationObserver 是处理上述场景的标准解法。

### 5.2 增强版 MutationObserver（含 SPA 支持）

```javascript
(function() {
  var adSelectors = [/* 从 domRules 注入 */];

  function purge(root) {
    adSelectors.forEach(function(sel) {
      try {
        root.querySelectorAll(sel).forEach(function(el) {
          el.style.cssText += 'display:none!important;';
        });
      } catch(e) {}
    });
  }

  // 首次清理
  purge(document.documentElement);

  // 监听 DOM 变动
  new MutationObserver(function(mutations) {
    var needsPurge = false;
    mutations.forEach(function(m) {
      if (m.addedNodes.length > 0) needsPurge = true;
    });
    if (needsPurge) purge(document.documentElement);
  }).observe(document.documentElement, { childList: true, subtree: true });

  // SPA 路由变化检测（History API hook）
  var pushState = history.pushState;
  history.pushState = function() {
    pushState.apply(this, arguments);
    setTimeout(function() { purge(document.documentElement); }, 300);
  };
  window.addEventListener('popstate', function() {
    setTimeout(function() { purge(document.documentElement); }, 300);
  });
})();
```

---

## 六、隐私保护增强方案

### 6.1 请求头指纹清理

在 `shouldInterceptRequest` 中修改请求头，减少追踪特征：

```kotlin
override fun shouldInterceptRequest(
    view: WebView,
    request: WebResourceRequest
): WebResourceResponse? {

    val url = request.url.toString()

    // 拦截已知追踪器
    if (TRACKER_DOMAINS.any { url.contains(it) }) {
        return emptyResponse()
    }

    // 清理暴露隐私的请求头（通过 WebView 设置实现）
    return null
}

// WebView 设置中添加
webView.settings.apply {
    // 禁用位置共享
    setGeolocationEnabled(false)
    // 禁用 Save Password 提示（防止密码被第三方读取）
    savePassword = false
}
```

### 6.2 JavaScript 反指纹注入

```kotlin
/**
 * 在每个页面注入，混淆常用指纹采集 API
 * 注意：过激的混淆会导致正常功能失效，以下为保守方案
 */
val fingerprintScript = """
(function() {
  // Canvas 指纹噪声（轻微扰动，不影响正常渲染）
  var origGetContext = HTMLCanvasElement.prototype.getContext;
  HTMLCanvasElement.prototype.getContext = function(type, attrs) {
    var ctx = origGetContext.apply(this, arguments);
    if (type === '2d' && ctx) {
      var origGetImageData = ctx.getImageData.bind(ctx);
      ctx.getImageData = function(x, y, w, h) {
        var data = origGetImageData(x, y, w, h);
        // 在最后几个像素加噪声（不可见但破坏指纹哈希）
        for (var i = data.data.length - 8; i < data.data.length; i++) {
          data.data[i] ^= 1;
        }
        return data;
      };
    }
    return ctx;
  };

  // 隐藏 WebView 特有的 userAgent 标识
  // （在 WebView settings 中统一设置，此处不重复）
  
  // 禁用 Battery Status API（常用于指纹）
  if (navigator.getBattery) {
    navigator.getBattery = function() { return Promise.reject(); };
  }
  
  // 隐藏插件列表
  Object.defineProperty(navigator, 'plugins', {
    get: function() { return []; }
  });
})();
""".trimIndent()
```

### 6.3 User-Agent 标准化

```kotlin
// 在 MainActivity.onCreate() 中设置
// 使用标准 Chrome 桌面 UA，而非暴露 Android WebView 标识
webView.settings.userAgentString = 
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
    "AppleWebKit/537.36 (KHTML, like Gecko) " +
    "Chrome/124.0.0.0 Safari/537.36"
```

### 6.4 Cookie 隔离策略

```kotlin
// 导入 androidx.webkit.WebViewCompat
// 为每个站点组设置独立的 Cookie 存储（需 Android 9+）

// 方案1：退出时清除所有 Cookies（当前方案，简单可靠）
private fun clearPrivateData() {
    CookieManager.getInstance().removeAllCookies(null)
    WebStorage.getInstance().deleteAllData()
    webView.clearCache(true)
    webView.clearHistory()
    webView.clearFormData()
}

// 方案2（增强）：应用进入后台时自动清除
override fun onStop() {
    super.onStop()
    if (privacySettings.clearOnBackground) {
        clearPrivateData()
    }
}
```

### 6.5 隐私保护能力总览

| 保护层 | 现状 | 建议增强 |
|--------|:----:|---------|
| FLAG_SECURE（截图/录屏防护） | ✅ 已实现 | 无需改动 |
| 生物识别锁 | ✅ 已实现 | 可增加失败次数限制 |
| 图标伪装 | ✅ 4个别名 | 无需改动 |
| 网络追踪器拦截 | ✅ 双引擎 | 增加 domRules 层 |
| Canvas 指纹防护 | ❌ 缺失 | **建议实现** |
| User-Agent 标准化 | ❌ 暴露 WebView | **建议实现** |
| Cookie 自动清除 | 部分 | 增加后台清除触发 |
| DNS-over-HTTPS | ❌ 缺失 | 可选，系统级配置 |

---

## 七、性能优化建议

### 7.1 Adblock Plus 规则匹配优化

现有 `~120,000` 条规则用 `Set.contains()` 匹配，对高频请求存在性能隐患。

**方案A：域名前缀 HashMap 分区**

```kotlin
// 按域名首字符分桶，减少每次匹配的遍历量
private val domainBuckets: Map<Char, Set<String>> by lazy {
    blockRules.groupBy { it.firstOrNull() ?: '_' }
              .mapValues { it.value.toHashSet() }
}

fun shouldBlockFast(url: String): Boolean {
    val domain = extractDomain(url) ?: return false
    val bucket = domainBuckets[domain.firstOrNull()] ?: return false
    return bucket.contains(domain)
}
```

**方案B：Bloom Filter 预筛（推荐）**

```kotlin
// 使用 Guava BloomFilter（需添加依赖）
// implementation 'com.google.guava:guava:31.1-android'
private val bloomFilter: BloomFilter<String> by lazy {
    BloomFilter.create(Funnels.stringFunnel(Charset.forName("UTF-8")), 150_000, 0.01)
        .also { bf -> blockRules.forEach { bf.put(it) } }
}

fun shouldBlockWithBloom(url: String): Boolean {
    val domain = extractDomain(url) ?: return false
    if (!bloomFilter.mightContain(domain)) return false  // 快速排除（99%命中此路径）
    return blockRules.contains(domain)  // 精确确认
}
```

### 7.2 DOM 注入时机优化

```kotlin
// 优化后的注入顺序（按影响用户体验的优先级排序）

// T=0 (onPageStarted)：注入 MutationObserver，监听动态广告
// T=page_finish (onPageFinished)：注入 CSS 隐藏 + DOM 清理
// T=+500ms (延迟)：自动点击关闭按钮（等待按钮渲染完成）
// T=+2000ms (延迟)：再次清理（应对慢速注入的广告）

handler.postDelayed({
    webView.evaluateJavascript(domRuleEngine.buildCleanupScript(currentUrl), null)
}, 2000)
```

### 7.3 规则持久化方案（Adblock Plus 规则）

当前 Adblock Plus 规则仅存内存，每次启动需重新下载。建议改为本地缓存：

```kotlin
// 将解析后的规则序列化存储
private val ABPLUS_CACHE_KEY = "abplus_rules_v2"
private val ABPLUS_CACHE_DATE_KEY = "abplus_rules_date"
private val CACHE_EXPIRE_DAYS = 7

fun saveParsedRules(context: Context) {
    val prefs = context.getSharedPreferences("ad_rules", Context.MODE_PRIVATE)
    val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    prefs.edit()
        .putStringSet(ABPLUS_CACHE_KEY, blockRules)
        .putString(ABPLUS_CACHE_DATE_KEY, today)
        .apply()
}

fun loadCachedRulesIfValid(context: Context): Boolean {
    val prefs = context.getSharedPreferences("ad_rules", Context.MODE_PRIVATE)
    val cacheDate = prefs.getString(ABPLUS_CACHE_DATE_KEY, null) ?: return false
    val daysDiff = /* 计算日期差 */
    if (daysDiff > CACHE_EXPIRE_DAYS) return false
    blockRules.addAll(prefs.getStringSet(ABPLUS_CACHE_KEY, emptySet()) ?: return false)
    return true
}
```

---

## 八、规则维护与更新策略

### 8.1 扩展后的 ad-filter-rules.json 管理流程

```
1. 发现新广告  →  Chrome DevTools 分析 DOM
2. 编写 domRules 条目
3. 本地测试（WebView + ADB logcat）
4. 更新 JSON 文件 version + lastUpdate
5. 同步更新 AdFilterRules.kt 中的 DEFAULT_RULES 常量
6. Push 到 GitHub
7. 用户通过 Settings → 更新规则 拉取
```

### 8.2 规则版本验证

```kotlin
data class DomRule(
    val urlPatterns: List<String>,
    val hideSelectors: List<String>,
    val removeSelectors: List<String>,
    val clickDismiss: List<String>,
    val attrBlock: List<AttrRule>,
    val injectScript: String?
)

// 解析时做基本校验
private fun parseDomRule(json: JSONObject): DomRule? {
    return try {
        DomRule(
            urlPatterns = json.getJSONArray("urlPatterns").toStringList(),
            hideSelectors = json.optJSONArray("hideSelectors")?.toStringList() ?: emptyList(),
            removeSelectors = json.optJSONArray("removeSelectors")?.toStringList() ?: emptyList(),
            clickDismiss = json.optJSONArray("clickDismiss")?.toStringList() ?: emptyList(),
            attrBlock = emptyList(), // 简化
            injectScript = json.optString("injectScript").takeIf { it.isNotBlank() }
        )
    } catch (e: JSONException) {
        Log.w("DomRule", "规则解析失败: ${e.message}")
        null
    }
}
```

### 8.3 更新频率建议

| 规则类型 | 建议频率 | 触发方式 |
|---------|:-------:|---------|
| JSON domRules（站点专属） | 每次启动 | 自动后台拉取 |
| Adblock Plus（通用） | 每7天 | 用户手动或定时任务 |
| 域名配置 DomainConfig | 按需 | 发现域名变更时 |

---

## 九、已知局限与风险

### 9.1 技术局限

| 场景 | 原因 | 应对 |
|------|------|------|
| 视频播放器内嵌广告 | `<video>` 内部无法通过 DOM 访问 | 在 VideoExtractor 层处理（已有思路） |
| HTTPS 混合内容警告 | 注入脚本修改了 DOM 可能触发 | 开启 `setMixedContentMode(MIXED_CONTENT_ALWAYS_ALLOW)`，仅用于内部站点 |
| 服务端渲染广告 | HTML 直接包含广告内容 | CSS 隐藏可处理，但可能影响布局 |
| WebAssembly 广告 | 无法通过 DOM/CSS 拦截 | 只能在网络层拦截 CDN 域名 |
| 规则被站点反制 | 站点可能动态混淆 CSS 类名 | 定期更新规则；使用结构特征而非类名 |

### 9.2 DOM 注入的安全边界

> ⚠️ **重要**：注入的 JS 脚本在目标页面的 Origin 下执行，具备完整的 DOM 访问权限。务必遵守以下原则：

- 注入脚本只执行 **删除/隐藏/点击** 操作，不读取页面内容
- 不注入任何向外部发送数据的代码
- `injectScript` 字段仅支持预定义的白名单脚本，不支持任意代码注入
- 所有规则来源（GitHub raw）必须通过 HTTPS 且域名验证

### 9.3 合规说明

本方案中的广告拦截技术原理与 uBlock Origin、AdGuard 等主流工具相同，属于用户自主控制本地浏览体验的范畴。

---

## 十、优先级路线图

### Phase 1（1-2周，高价值低成本）

- [ ] 在 `ad-filter-rules.json` 中添加 `domRules` 节点
- [ ] 实现 `DomRuleEngine.buildInjectionScript()`
- [ ] 在 `onPageFinished` 中接入 DOM 注入
- [ ] 标准化 User-Agent（3行代码）
- [ ] Adblock Plus 规则本地缓存（减少启动时间）

### Phase 2（3-4周，提升稳定性）

- [ ] 实现 `buildMutationObserverScript()`（动态广告防御）
- [ ] MISSAV / JABLE 专项 DOM 规则调研与录入
- [ ] Bloom Filter 优化匹配性能
- [ ] Canvas 指纹混淆注入

### Phase 3（可选，进阶功能）

- [ ] `SettingsActivity` 中增加自定义规则 UI
- [ ] 规则命中统计（本地计数，不上传）
- [ ] Trie 树优化 Adblock Plus 规则匹配

---

## 附录：DOM 调试速查

```bash
# 启用 WebView 远程调试后，在 Chrome 访问
chrome://inspect/#devices

# 常用 DevTools Console 命令（分析广告结构用）
# 查找所有固定定位元素（疑似悬浮广告）
document.querySelectorAll('*').filter(el => 
  getComputedStyle(el).position === 'fixed' && 
  parseInt(getComputedStyle(el).zIndex) > 100
)

# 查找所有 iframe
document.querySelectorAll('iframe').forEach(f => console.log(f.src, f.className))

# 查找高 z-index 的遮罩层
document.querySelectorAll('[style*="z-index"]').forEach(el => 
  console.log(el.tagName, el.className, el.style.zIndex)
)
```

---

*文档维护：在发现新的广告模式时，优先更新 `ad-filter-rules.json` 中的 `domRules` 节点，无需发布新版本 APK。*
