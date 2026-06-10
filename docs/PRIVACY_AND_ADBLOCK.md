# JAV Browser — 隐私保护与广告屏蔽统一方案

> **版本**: v2.1
> **更新日期**: 2026-06-09
> **基于**: v1.1.5 双引擎架构 + 实际代码实现
> **升级方案**: [AdblockAndroid 引擎集成](design/adblock-engine-upgrade-proposal.md)

---

## 目录

- [一、架构总览](#一架构总览)
- [二、广告屏蔽系统](#二广告屏蔽系统)
- [三、隐私保护系统](#三隐私保护系统)
- [四、开发指南](#四开发指南)
- [五、配置与维护](#五配置与维护)

---

## 一、架构总览

### 1.1 系统分层

```
┌─────────────────────────────────────────────────────────────┐
│                     用户界面层                               │
│  MainActivity + SettingsActivity + BiometricHelper          │
└─────────────────────────────────────────────────────────────┘
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                   隐私保护层（P-01 ~ P-08）                  │
│  WebRTC防护 | 反指纹 | Cookie控制 | HTTPS升级 | 权限守卫   │
└─────────────────────────────────────────────────────────────┘
                            ▼
┌─────────────────────────────────────────────────────────────┐
│              广告屏蔽层（双引擎架构）                        │
│  ┌──────────────────┐    ┌──────────────────┐              │
│  │ JSON规则引擎     │    │ Adblock Plus引擎 │              │
│  │ ~100条站点规则   │    │ ~120,000条规则   │              │
│  └──────────────────┘    └──────────────────┘              │
└─────────────────────────────────────────────────────────────┘
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                    网络层                                    │
│  shouldInterceptRequest + VideoProxyServer                  │
└─────────────────────────────────────────────────────────────┘
```

### 1.2 核心能力矩阵

| 能力 | 状态 | 实现位置 | 优先级 |
|------|:---:|---------|:-----:|
| **广告屏蔽** | | | |
| 网络层URL拦截 | ✅ 已实现 | AdFilterRules.kt | P0 |
| 双引擎规则匹配 | ✅ 已实现 | AdFilterRules.kt | P0 |
| 元素隐藏（CSS） | ✅ 已实现 | MainActivity.kt | P0 |
| 动态广告监听 | ✅ 已实现 | JS注入（MutationObserver） | P1 |
| 云端规则更新 | ✅ 已实现 | AdFilterRules.kt | P0 |
| **引擎升级** | 📋 提案中 | AdblockAndroid | P1 |
| Extended CSS | 📋 提案中 | AdblockAndroid | P1 |
| Scriptlets | 📋 提案中 | AdblockAndroid | P1 |
| **隐私保护** | | | |
| 生物识别锁 | ✅ 已实现 | BiometricHelper.kt | P0 |
| FLAG_SECURE | ✅ 已实现 | MainActivity.kt | P0 |
| 图标伪装 | ✅ 已实现 | AndroidManifest.xml | P0 |
| WebRTC防护 | ⚠️ 待实现 | 需新增 | P1 |
| 反指纹保护 | ⚠️ 待实现 | 需新增 | P1 |
| Cookie控制 | 部分实现 | WebView设置 | P1 |
| HTTPS升级 | ⚠️ 待实现 | 需新增 | P2 |
| 权限守卫 | ⚠️ 待实现 | 需新增 | P2 |
| 无痕会话 | ⚠️ 待实现 | 需新增 | P2 |
| DNS over HTTPS | ⚠️ 待实现 | 需新增 | P3 |

---

## 二、广告屏蔽系统

### 2.1 双引擎架构（已实现）

#### JSON规则引擎

**特点**：
- 站点特定优化，约100条规则
- 支持云端更新，无需发布新版本
- 轻量级，启动加载快

**规则格式**：
```json
{
  "version": "2.1.0",
  "lastUpdate": "2025-11-28T13:30:00Z",
  "domains": {
    "missav": "missav.ws",
    "jable": "jable.tv"
  },
  "rules": {
    "commonBlock": [
      "exoclick.com",
      "magsrv.com",
      "tsyndicate.com"
    ]
  }
}
```

#### Adblock Plus引擎

**特点**：
- 广泛覆盖，约120,000条规则
- 支持多种格式：域名精确匹配、URL模式、元素隐藏
- 内存缓存，不持久化

**规则来源**：
```kotlin
private val EASYLIST_SOURCES = listOf(
    "https://easylist.to/easylist/easylist.txt",
    "https://easylist.to/easylist/easyprivacy.txt",
    "https://easylist-downloads.adblockplus.org/easylistchina+easylist.txt",
    "https://raw.githubusercontent.com/217heidai/adblockfilters/main/rules/adblock_dns.txt",
    "https://raw.githubusercontent.com/GOODBYEADS/GOODBYEADS/master/blocklist.txt"
)
```

### 2.2 拦截流程（已修复）

**✅ 已修复**：`AdFilterRules.shouldBlock()` 方法现在在 MainActivity 中被正确调用。

当前实际使用的拦截逻辑：

```kotlin
// MainActivity.WebViewClient - 第 312-315 行
// ✅ 当前实现：完整的 shouldBlock 方法
if (adFilterRules.shouldBlock(url, isThirdParty = true)) {
    return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream("".toByteArray()))
}
```

**修复效果**：
- ✅ EasyList 规则（`blockRules`, `blockPatterns`）生效
- ✅ 白名单生效
- ✅ 域名精确匹配（HashSet O(1)）生效

> **修复详情**: [adblock-engine-upgrade-proposal.md](design/adblock-engine-upgrade-proposal.md)

### 2.3 元素隐藏（已实现）

```kotlin
override fun onPageFinished(view: WebView?, url: String?) {
    super.onPageFinished(view, url)
    
    // 注入元素隐藏CSS
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

### 2.4 动态广告监听（已实现）

```kotlin
// 注入MutationObserver监听动态插入的广告
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
    
    // 初次清理
    hideAds();
    
    // 监听DOM变化
    new MutationObserver(function(mutations) {
        hideAds();
    }).observe(document.documentElement, {
        childList: true,
        subtree: true
    });
})();
""".trimIndent()
```

### 2.5 规则更新方式

#### 单一云端源更新
```kotlin
adFilterRules.updateRulesFromCloud(
    "https://raw.githubusercontent.com/fekilooo/javbrowser/main/ad-filter-rules.json"
) { success, message ->
    // 处理更新结果
}
```

#### Adblock Plus规则更新
```kotlin
adFilterRules.updateRulesFromExternalSources { success, message ->
    // 处理更新结果
}
```

#### 多源合并更新
```kotlin
adFilterRules.updateFromAllSources { success, message ->
    // 从云端 + 本地 + 默认规则合并更新
}
```

---

## 三、隐私保护系统

### 3.1 已实现的保护层

#### ✅ 生物识别应用锁

**实现**：BiometricHelper.kt + LockActivity.kt

```kotlin
// 特性
- 指纹/面容识别
- 冷启动强制验证
- 可配置超时时间
- 失败重试限制
```

#### ✅ 截图/录屏防护

**实现**：FLAG_SECURE

```kotlin
// MainActivity.onCreate()
window.setFlags(
    WindowManager.LayoutParams.FLAG_SECURE,
    WindowManager.LayoutParams.FLAG_SECURE
)
```

#### ✅ 图标伪装

**实现**：AndroidManifest.xml

```xml
<!-- 4个启动器别名 -->
<activity-alias android:name=".CalculatorAlias" />
<activity-alias android:name=".NotesAlias" />
<activity-alias android:name=".FileManagerAlias" />
<activity-alias android:name=".DefaultAlias" />
```

#### 部分实现：Cookie控制

**当前实现**：
```kotlin
webView.settings.apply {
    domStorageEnabled = true
    databaseEnabled = true
    savePassword = false
    saveFormData = false
}

// 退出时清除
CookieManager.getInstance().removeAllCookies(null)
```

### 3.2 待实现的保护层（优先级排序）

#### P1 - WebRTC IP泄露防护（高优先级）

**威胁**：WebRTC绕过代理，直接暴露设备IP

**实现方案**：
```kotlin
// 在onPageStarted最先注入
val webrtcBlockScript = """
(function() {
    var OrigRTCPC = window.RTCPeerConnection || window.webkitRTCPeerConnection;
    if (OrigRTCPC) {
        function FakeRTCPC(config, constraints) {
            if (config && config.iceServers) {
                config = Object.assign({}, config, { iceServers: [] });
            }
            return new OrigRTCPC(config || { iceServers: [] }, constraints);
        }
        FakeRTCPC.prototype = OrigRTCPC.prototype;
        window.RTCPeerConnection = FakeRTCPC;
        window.webkitRTCPeerConnection = FakeRTCPC;
    }
    
    // 阻止getUserMedia
    if (navigator.mediaDevices && navigator.mediaDevices.getUserMedia) {
        navigator.mediaDevices.getUserMedia = function() {
            return Promise.reject(new DOMException('NotAllowedError'));
        };
    }
})();
""".trimIndent()

webView.evaluateJavascript(webrtcBlockScript, null)
```

**验证**：访问 https://browserleaks.com/webrtc 检查无IP泄露

#### P1 - 反指纹保护

**威胁**：Canvas、WebGL、Screen等API用于跨站追踪

**实现方案**：
```kotlin
val antiFingerprintScript = """
(function() {
    // Canvas噪声注入（轻微扰动，不影响视觉）
    var origToDataURL = HTMLCanvasElement.prototype.toDataURL;
    HTMLCanvasElement.prototype.toDataURL = function(type, quality) {
        var ctx = this.getContext('2d');
        if (ctx && this.width > 0 && this.height > 0) {
            try {
                var imgData = ctx.getImageData(0, 0, this.width, this.height);
                var d = imgData.data;
                // 最后一个像素加噪声
                if (d.length >= 4) {
                    d[d.length - 4] ^= 1;
                }
                ctx.putImageData(imgData, 0, 0);
            } catch(e) {}
        }
        return origToDataURL.apply(this, arguments);
    };
    
    // WebGL标准化
    function patchWebGL(ctx) {
        if (!ctx) return;
        var orig = ctx.prototype.getParameter;
        ctx.prototype.getParameter = function(p) {
            if (p === 37446) return 'Generic GPU';  // RENDERER
            if (p === 37445) return 'Generic Vendor';  // VENDOR
            return orig.apply(this, arguments);
        };
    }
    patchWebGL(window.WebGLRenderingContext);
    patchWebGL(window.WebGL2RenderingContext);
    
    // Screen标准化（最常见分辨率）
    Object.defineProperty(screen, 'width', { get: () => 1920 });
    Object.defineProperty(screen, 'height', { get: () => 1080 });
})();
""".trimIndent()
```

#### P2 - 第三方Cookie控制

**实现方案**：
```kotlin
// 初始化WebView时
val cm = CookieManager.getInstance()
cm.setAcceptCookie(true)
cm.setAcceptThirdPartyCookies(webView, false) // 阻止第三方Cookie

// 无痕模式下全部禁用
webView.settings.apply {
    domStorageEnabled = false
    databaseEnabled = false
    cacheMode = WebSettings.LOAD_NO_CACHE
}
```

#### P2 - HTTPS强制升级

**实现方案**：
```kotlin
override fun shouldOverrideUrlLoading(
    view: WebView?,
    request: WebResourceRequest?
): Boolean {
    val url = request?.url?.toString() ?: return false
    
    // HTTP升级为HTTPS
    if (url.startsWith("http://")) {
        // 豁免视频流和本地地址
        if (!url.contains(".m3u8") && 
            !url.contains(".ts") && 
            !url.contains("localhost")) {
            val httpsUrl = url.replaceFirst("http://", "https://")
            view?.loadUrl(httpsUrl)
            return true
        }
    }
    
    return super.shouldOverrideUrlLoading(view, request)
}
```

#### P2 - WebView权限守卫

**实现方案**：
```kotlin
webView.webChromeClient = object : WebChromeClient() {
    override fun onPermissionRequest(request: PermissionRequest) {
        // 仅允许DRM媒体，拒绝其他
        val allowed = setOf(PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID)
        val toGrant = request.resources.filter { it in allowed }.toTypedArray()
        
        if (toGrant.isNotEmpty()) {
            request.grant(toGrant)
        } else {
            request.deny()
            Toast.makeText(this@MainActivity, 
                "已阻止权限请求", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onGeolocationPermissionsShowPrompt(
        origin: String,
        callback: GeolocationPermissions.Callback
    ) {
        callback.invoke(origin, false, false)
        Toast.makeText(this@MainActivity, 
            "已阻止地理位置请求", Toast.LENGTH_SHORT).show()
    }
}

// 禁用Safe Browsing（会将URL发送给Google）
webView.settings.setSafeBrowsingEnabled(false)
```

#### P2 - 无痕会话模式

**实现方案**：
```kotlin
object IncognitoSession {
    @Volatile private var active = false
    
    fun enter(webView: WebView) {
        active = true
        webView.settings.apply {
            domStorageEnabled = false
            databaseEnabled = false
            cacheMode = WebSettings.LOAD_NO_CACHE
        }
        CookieManager.getInstance().setAcceptCookie(false)
    }
    
    fun exit(webView: WebView) {
        active = false
        clearAllData(webView)
        webView.loadUrl("about:blank")
    }
    
    private fun clearAllData(webView: WebView) {
        webView.clearHistory()
        webView.clearCache(true)
        CookieManager.getInstance().removeAllCookies(null)
        WebStorage.getInstance().deleteAllData()
    }
}

// 在onStop时自动清除
override fun onStop() {
    super.onStop()
    if (IncognitoSession.isActive) {
        IncognitoSession.clearAllData(webView)
    }
}
```

#### P3 - DNS over HTTPS（可选）

**说明**：
- WebView使用系统DNS，应用层无法直接覆盖
- DoH主要保护OkHttp请求（规则下载、视频代理）
- 建议在Settings中引导用户启用系统Private DNS

**实现方案**（仅用于OkHttp）：
```kotlin
// 添加依赖：implementation("com.squareup.okhttp3:okhttp-doh:4.12.0")

val dohDns = DnsOverHttps.Builder()
    .client(OkHttpClient())
    .url("https://cloudflare-dns.com/dns-query".toHttpUrl())
    .bootstrapDnsHosts(InetAddress.getByName("1.1.1.1"))
    .build()

val client = OkHttpClient.Builder()
    .dns(dohDns)
    .build()
```

---

## 四、开发指南

### 4.1 添加新广告规则

#### JSON规则（站点特定）

**步骤**：
1. 编辑 `ad-filter-rules.json`
2. 增加版本号
3. 提交到GitHub
4. 用户通过Settings更新

```json
{
  "version": "2.2.0",
  "rules": {
    "commonBlock": [
      "newad.com"  // 新增广告域名
    ]
  }
}
```

#### Adblock Plus规则（通用）

**说明**：自动从EasyList等源更新，无需手动维护

### 4.2 添加新隐私保护模块

**模板**：
```kotlin
// 1. 创建独立模块类
object NewPrivacyFeature {
    fun inject(webView: WebView, context: Context) {
        if (!isEnabled(context)) return
        
        val script = """
            // 保护逻辑
        """.trimIndent()
        
        webView.evaluateJavascript(script, null)
    }
    
    private fun isEnabled(context: Context): Boolean {
        return context.getSharedPreferences("privacy_settings", Context.MODE_PRIVATE)
            .getBoolean("feature_name", true)
    }
}

// 2. 在MainActivity集成
override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
    super.onPageStarted(view, url, favicon)
    view?.let { NewPrivacyFeature.inject(it, this) }
}

// 3. 在SettingsActivity添加开关
```

### 4.3 调试WebView

**启用远程调试**：
```kotlin
// MainActivity.onCreate()
if (BuildConfig.DEBUG) {
    WebView.setWebContentsDebuggingEnabled(true)
}
```

**Chrome DevTools**：
1. 连接设备
2. 访问 `chrome://inspect/#devices`
3. 选择目标WebView
4. 使用Elements面板分析广告DOM结构

### 4.4 性能优化建议

#### 方案 A：集成成熟引擎（推荐）

> **完整方案**: [adblock-engine-upgrade-proposal.md](design/adblock-engine-upgrade-proposal.md)

使用 **AdblockAndroid** 库替代自研引擎：

```kotlin
// build.gradle.kts
implementation("com.github.Edsuns:AdblockAndroid:1.2.0")

// Application 初始化
AdFilter.init(this)
AdFilter.get().addSubscription(Subscription.EASYLIST_CHINA)

// WebViewClient 使用
val result = AdFilter.get().shouldIntercept(view, request)
return result.resourceResponse
```

**性能对比**：

| 指标 | 当前自研 | AdblockAndroid |
|------|----------|----------------|
| 匹配算法 | O(n) 遍历 | O(k) Trie |
| 120k规则内存 | ~20MB | ~15MB |
| 单次匹配 | ~5-10ms | ~0.1-0.5ms |
| Extended CSS | ❌ | ✅ |
| Scriptlets | ❌ | ✅ |

#### 方案 B：Adblock Plus规则优化（自研改进）

**当前**：HashSet.contains() 线性查找

**优化方案B - Bloom Filter**：
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

**优化方案C - 规则持久化**：

**当前**：Adblock Plus规则每次启动重新下载

**优化**：本地缓存7天
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

## 五、配置与维护

### 5.1 配置文件

#### ad-filter-rules.json（云端）

**位置**：GitHub仓库根目录  
**更新频率**：按需（发现新广告时）  
**格式验证**：必须包含 `version` 和 `rules.commonBlock`

#### SharedPreferences（本地）

```
ad_filter_rules:
  - rules_json: 当前规则JSON
  - last_update: 最后更新时间
  - cloud_url: 云端规则URL
  - element_hide_rules: 元素隐藏规则列表
  
privacy_settings:
  - webrtc_block: WebRTC防护开关
  - antifingerprint: 反指纹开关
  - block_third_party_cookies: 第三方Cookie阻止开关
```

### 5.2 更新策略

| 规则类型 | 频率 | 触发方式 |
|---------|:---:|---------|
| JSON规则 | 每次启动 | 自动后台 |
| Adblock Plus | 每7天 | 用户手动 |
| 域名配置 | 按需 | 发现域名变更时 |

### 5.3 安全守卫清单

**绝不拦截**：
- ✅ `.m3u8` `.ts` `.mp4` `.key` 视频流
- ✅ 同域请求（第一方资源）
- ✅ 硬编码白名单（JAV站点 + CDN）

**注入安全**：
- ✅ 所有JS脚本仅执行删除/隐藏操作
- ✅ 不读取页面内容，不向外发送数据
- ✅ 规则源必须HTTPS

### 5.4 故障排查

#### 广告拦截失效

**检查**：
1. Settings → 广告拦截是否开启
2. 规则版本是否最新
3. 特定域名是否在白名单

**调试**：
```kotlin
// 添加日志
Log.d("AdBlock", "Checking URL: $url")
Log.d("AdBlock", "Should block: ${adFilterRules.shouldBlock(url)}")
```

#### 视频播放失败

**可能原因**：误拦截视频流

**解决**：
1. 检查URL是否包含 `.m3u8` 或 `.ts`
2. 将视频CDN加入白名单
3. 临时关闭广告拦截测试

#### 隐私功能冲突

**WebRTC防护**：可能导致视频会议功能失效（本项目无影响）  
**反指纹**：可能影响验证码显示（需测试）  
**第三方Cookie阻止**：可能影响第三方登录（本项目无影响）

---

## 附录A：规则格式速查

### JSON规则格式

```json
{
  "version": "版本号",
  "lastUpdate": "ISO时间",
  "domains": {
    "站点标识": "当前域名"
  },
  "rules": {
    "commonBlock": ["域名列表"]
  }
}
```

### Adblock Plus规则格式

```
||domain.com^               # 域名精确匹配
@@||domain.com^             # 白名单
##.ad-class                 # 元素隐藏（全局）
domain.com##.ad-class       # 元素隐藏（站点专属）
/path/to/ad.js              # URL路径匹配
```

---

## 附录B：优先级路线图

### Phase 1（1-2周，高价值）

- [x] 双引擎广告屏蔽
- [x] 元素隐藏CSS注入
- [x] 动态广告监听
- [ ] WebRTC防护（P1）
- [ ] 第三方Cookie控制（P1）

### Phase 2（3-4周，提升稳定性）

- [ ] 集成 AdblockAndroid 引擎（替代自研）
- [ ] 反指纹保护（P1）
- [ ] HTTPS强制升级（P2）
- [ ] WebView权限守卫（P2）
- [ ] 无痕会话模式（P2）

### Phase 3（可选，进阶功能）

- [ ] DNS over HTTPS（P3）
- [ ] 规则统计UI
- [ ] 自定义规则编辑器

---

**文档维护**：当发现新的广告模式或隐私威胁时，优先更新本文档及 `ad-filter-rules.json`
