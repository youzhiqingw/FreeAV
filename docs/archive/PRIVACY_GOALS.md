# javbrowser — 无 Root 广告屏蔽与隐私保护集成方案

> **Claude Code 目标文档**  
> 基于安卓近 5 年（2021–2026）主流方案的可行性分析，聚焦**无 Root、内置到 App、不依赖系统 VPN**的实现路径。  
> 本文档补充 `AD_BLOCK_GOALS.md` 和 `CLAUDE_CODE_GOALS.md`，专注于尚未覆盖的隐私保护能力。

---

## 一、方案可行性矩阵

| 方案 | 原理 | 无 Root 可行 | 内置到 App | 适合本项目 | 备注 |
|------|------|:---:|:---:|:---:|------|
| **Private DNS (DoT/DoH)** | 系统级 DNS 加密 | ✅ | ⚠️ 部分 | ✅ | OkHttp 层可用；WebView DNS 无法覆盖 |
| **本地 VPN 过滤** | VPN API 拦截全部流量 | ✅ | ❌ | ❌ | 需要 VPN Service，与其他 VPN 冲突，不适合嵌入浏览器 App |
| **WebView shouldInterceptRequest** | 请求发出前拦截 | ✅ | ✅ | ✅ | **核心方案，已在 AD_BLOCK_GOALS.md 覆盖** |
| **JavaScript DOM 注入** | 页面加载后清理广告元素 | ✅ | ✅ | ✅ | **已在 AD_BLOCK_GOALS.md 覆盖** |
| **WebRTC 泄露防护** | JS 覆盖 RTCPeerConnection | ✅ | ✅ | ✅ | **本文新增，优先级极高** |
| **反指纹（Canvas/WebGL/Screen）** | JS 注入噪声或标准化 | ✅ | ✅ | ✅ | **本文新增** |
| **第三方 Cookie 控制** | WebSettings + CookieManager | ✅ | ✅ | ✅ | **本文新增** |
| **HTTPS 强制升级** | shouldOverrideUrlLoading | ✅ | ✅ | ✅ | **本文新增** |
| **DoH（OkHttp 层）** | 自定义 OkHttp DNS 解析器 | ✅ | ✅ | ✅ | **本文新增，适用于视频代理/规则下载** |
| **WebView 权限守卫** | WebChromeClient 拦截 | ✅ | ✅ | ✅ | **本文新增，阻止摄像头/麦克风/定位** |
| **无痕会话模式** | 清除 Cookie/Cache/History | ✅ | ✅ | ✅ | **本文新增** |
| **追踪 JS API 拦截** | 覆盖 sendBeacon/fetch 等 | ✅ | ✅ | ✅ | **本文新增** |
| **Tracker Control（per-app DNS）** | 独立 App，VPN 层过滤 | ✅ | ❌ | ❌ | 需要独立 VPN App，无法嵌入 |
| **系统 hosts 修改** | Root 写 /system/etc/hosts | ❌ | ❌ | ❌ | 需要 Root |
| **Xposed/LSPosed 模块** | 框架级钩子 | ❌ | ❌ | ❌ | 需要 Root |
| **GrapheneOS/CalyxOS 特性** | 系统级隐私加固 | ❌ | ❌ | ❌ | 只对自定义 ROM 有效 |
| **AdAway Root 模式** | Root hosts 文件覆写 | ❌ | ❌ | ❌ | 需要 Root |
| **Manifest V3 扩展限制绕过** | 浏览器扩展 | ❌ | ❌ | ❌ | Android WebView 不支持浏览器扩展 |

---

## 二、现有项目已具备能力（无需重复实现）

| 功能 | 来源文档 | 状态 |
|------|---------|------|
| 网络层 URL 拦截（shouldInterceptRequest） | AD_BLOCK_GOALS.md | 待实现 |
| DOM 元素隐藏（CSS cosmetic 注入） | AD_BLOCK_GOALS.md | 待实现 |
| 追踪参数净化（utm_*、fbclid 等） | AD_BLOCK_GOALS.md | 待实现 |
| 生物识别应用锁 | 原项目 | ✅ 已实现 |
| 任务管理器隐藏（FLAG_SECURE） | 原项目 | ✅ 已实现 |
| 防截图/录屏 | 原项目 | ✅ 已实现 |
| 图标伪装 | 原项目 | ✅ 已实现 |
| 代理设置（HTTP/SOCKS5） | CLAUDE_CODE_GOALS.md F-02 | 待实现 |
| 自定义 Hosts | CLAUDE_CODE_GOALS.md F-03 | 待实现 |

---

## 三、新增隐私保护模块

### 新增文件结构

```
app/src/main/java/com/example/javbrowser/
└── privacy/
    ├── PrivacyConfig.kt           ← 隐私功能统一配置入口
    ├── DohResolver.kt             ← P-01: DNS over HTTPS
    ├── WebRtcBlocker.kt           ← P-02: WebRTC IP 泄露防护
    ├── AntiFingerprint.kt         ← P-03: 反指纹 JS 注入
    ├── CookieController.kt        ← P-04: Cookie 隔离与控制
    ├── HttpsEnforcer.kt           ← P-05: HTTPS 强制升级
    ├── PermissionGuard.kt         ← P-06: WebView 权限守卫
    ├── IncognitoSession.kt        ← P-07: 无痕会话
    └── TrackingApiBlocker.kt      ← P-08: 追踪 JS API 拦截
```

---

## P-01 DNS over HTTPS (DoH)

### 适用范围

| 流量类型 | DoH 是否生效 | 说明 |
|----------|:---:|------|
| OkHttp 请求（视频代理、规则下载） | ✅ | 直接注入自定义 DNS |
| NanoHTTPD 出站连接 | ✅ | 通过 OkHttpClientProvider 共享 |
| WebView 页面请求 | ❌ | WebView 使用系统 DNS，应用层无法覆盖 |
| WebView 拦截层域名黑名单 | ✅ | 通过 shouldInterceptRequest 弥补 |

> **结论**：DoH 主要保护 OkHttp 出站请求（规则更新、视频流下载）不被 ISP DNS 监听。WebView 流量的 DNS 隐私依靠用户在系统设置中启用 Private DNS（提示用户）。

### 验收标准

- [ ] `OkHttpClientProvider` 中可选注入 DoH 解析器
- [ ] 支持：Cloudflare (1.1.1.1)、Google、阿里 DNS（国内推荐）、NextDNS（用户自定义）
- [ ] SettingsActivity 中「DNS 加密」分组，含提供商单选 + 自定义 URL 输入框
- [ ] 「测试 DoH 连接」按钮，显示解析延迟（ms）或失败原因
- [ ] DoH 失败时自动降级到系统 DNS（不中断功能）

### 新增 Gradle 依赖

```kotlin
// app/build.gradle.kts
implementation("com.squareup.okhttp3:okhttp-doh:4.12.0")
```

### `DohResolver.kt`

```kotlin
package com.example.freeavbrowser.privacy

import android.content.Context
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.InetAddress

enum class DoHProvider(val displayName: String, val url: String) {
    NONE      ("不使用（系统 DNS）",      ""),
    CLOUDFLARE("Cloudflare 1.1.1.1",    "https://cloudflare-dns.com/dns-query"),
    GOOGLE    ("Google 8.8.8.8",         "https://dns.google/dns-query"),
    ALIDNS    ("阿里 DNS（国内推荐）",     "https://dns.alidns.com/dns-query"),
    NEXTDNS   ("NextDNS（自定义 URL）",   ""),
    CUSTOM    ("自定义 URL",              "")
}

object DohResolver {

    private const val PREF = "doh_settings"

    fun build(context: Context, bootstrapClient: OkHttpClient): Dns {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val provider = runCatching {
            DoHProvider.valueOf(prefs.getString("provider", DoHProvider.NONE.name)!!)
        }.getOrElse { DoHProvider.NONE }

        if (provider == DoHProvider.NONE) return Dns.SYSTEM

        val url = when (provider) {
            DoHProvider.CUSTOM, DoHProvider.NEXTDNS ->
                prefs.getString("custom_url", "") ?: ""
            else -> provider.url
        }
        if (url.isBlank()) return Dns.SYSTEM

        return runCatching {
            DnsOverHttps.Builder()
                .client(bootstrapClient)
                .url(url.toHttpUrl())
                .bootstrapDnsHosts(
                    // 硬编码引导 IP，避免 DoH 服务器域名本身需要 DNS 解析（引导问题）
                    InetAddress.getByName("1.1.1.1"),
                    InetAddress.getByName("8.8.8.8"),
                    InetAddress.getByName("223.5.5.5")   // 阿里
                )
                .includeIPv6(false)
                .build()
        }.getOrElse { Dns.SYSTEM }
    }

    fun save(context: Context, provider: DoHProvider, customUrl: String = "") {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().apply {
            putString("provider", provider.name)
            putString("custom_url", customUrl)
            apply()
        }
    }

    fun current(context: Context): DoHProvider {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        return runCatching {
            DoHProvider.valueOf(prefs.getString("provider", DoHProvider.NONE.name)!!)
        }.getOrElse { DoHProvider.NONE }
    }
}
```

**串联 DNS 解析器（`CompositeDns.kt`）**，保证 Hosts 优先、DoH 次之、系统兜底：

```kotlin
package com.example.freeavbrowser.privacy

import okhttp3.Dns
import java.net.InetAddress

/** 串联多个 DNS 解析器，按顺序尝试，第一个成功即返回 */
class CompositeDns(private vararg val resolvers: Dns) : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        for (resolver in resolvers) {
            val result = runCatching { resolver.lookup(hostname) }.getOrNull()
            if (!result.isNullOrEmpty()) return result
        }
        return Dns.SYSTEM.lookup(hostname)
    }
}
```

**集成到 `OkHttpClientProvider.build()`**：

```kotlin
// 1. 先构建无 DoH 的 bootstrap client（用于 DoH 服务器本身的连接）
val bootstrapClient = OkHttpClient.Builder().proxy(proxy)
    .connectTimeout(10, TimeUnit.SECONDS).build()

// 2. 构建 DoH 解析器
val doh = DohResolver.build(context, bootstrapClient)

// 3. 最终 client：Hosts > DoH > System
OkHttpClient.Builder()
    .proxy(proxy)
    .dns(CompositeDns(HostsManager.okHttpDns, doh))
    ...
```

---

## P-02 WebRTC IP 泄露防护

### 背景

WebRTC 是**最严重的浏览器隐私漏洞**之一：即使用户配置了代理，WebRTC 的 STUN/TURN 请求也会绕过代理，直接暴露设备的**本机 IP 和局域网 IP**。对于隐私敏感的 App，这是必须修复的安全漏洞。

### 验收标准

- [ ] 默认开启，在 `onPageStarted` **第一行**注入（比任何页面 JS 都早）
- [ ] 覆盖 `RTCPeerConnection`，清空 ICE 服务器列表，阻止 STUN 请求
- [ ] 覆盖 `navigator.getUserMedia` / `navigator.mediaDevices.getUserMedia` 返回拒绝
- [ ] SettingsActivity 有「WebRTC 防护」开关（默认开启）
- [ ] 可用 `https://browserleaks.com/webrtc` 验证无 IP 泄露

### `WebRtcBlocker.kt`

```kotlin
package com.example.freeavbrowser.privacy

import android.content.Context
import android.webkit.WebView

object WebRtcBlocker {

    fun inject(webView: WebView, context: Context) {
        if (!isEnabled(context)) return

        val js = """
            (function() {
                'use strict';

                // ── 覆盖 RTCPeerConnection，清空 ICE 服务器 ─────────────
                var OrigRTCPC = window.RTCPeerConnection
                             || window.webkitRTCPeerConnection
                             || window.mozRTCPeerConnection;

                if (OrigRTCPC) {
                    function FakeRTCPC(config, constraints) {
                        if (config && config.iceServers) {
                            config = Object.assign({}, config, { iceServers: [] });
                        }
                        return new OrigRTCPC(config || { iceServers: [] }, constraints);
                    }
                    FakeRTCPC.prototype               = OrigRTCPC.prototype;
                    window.RTCPeerConnection           = FakeRTCPC;
                    window.webkitRTCPeerConnection     = FakeRTCPC;
                    window.mozRTCPeerConnection        = FakeRTCPC;
                }

                // ── 覆盖 getUserMedia，阻止摄像头/麦克风获取 ─────────────
                if (navigator.mediaDevices && navigator.mediaDevices.getUserMedia) {
                    navigator.mediaDevices.getUserMedia = function() {
                        return Promise.reject(
                            new DOMException('NotAllowedError', 'NotAllowedError'));
                    };
                }
                if (navigator.getUserMedia) {
                    navigator.getUserMedia = function(c, s, error) {
                        if (error) error(new DOMException('NotAllowedError'));
                    };
                }

                // ── enumerateDevices 返回空列表（防止设备枚举指纹）────────
                if (navigator.mediaDevices && navigator.mediaDevices.enumerateDevices) {
                    navigator.mediaDevices.enumerateDevices = function() {
                        return Promise.resolve([]);
                    };
                }
            })();
        """.trimIndent()

        webView.evaluateJavascript(js, null)
    }

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences("privacy_settings", Context.MODE_PRIVATE)
            .getBoolean("webrtc_block", true)
}
```

**集成点**：`WebViewClient.onPageStarted()` 的第一行：

```kotlin
override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
    super.onPageStarted(view, url, favicon)
    view?.let {
        WebRtcBlocker.inject(it, this@MainActivity)     // 最先执行
        CosmeticInjector.injectPopupBlocker(it)
    }
}
```

---

## P-03 增强反指纹保护

### 指纹维度覆盖

| 指纹维度 | 防护方式 | 实现 |
|---------|---------|------|
| Canvas 2D | 单像素 ±1 RGB 噪声注入 | JS 覆盖 `toDataURL` |
| WebGL RENDERER/VENDOR | 返回通用值 | JS 覆盖 `getParameter` |
| Screen 尺寸 | 标准化为最常见机型（1080×2340） | JS 覆盖 `screen.*` |
| navigator.languages | 固定为 `["zh-CN", "zh"]` | JS 覆盖 |
| 时区（Intl） | 固定返回 `Asia/Shanghai` | JS 覆盖 `resolvedOptions` |
| AudioContext | 极微噪声（1e-7）注入 | JS 覆盖 `getChannelData` |
| navigator.plugins | 返回空列表 | JS 覆盖 |
| performance.now 精度 | 降至 10ms 精度 | JS 覆盖 |

### 验收标准

- [ ] `AntiFingerprint.inject()` 在 `onPageStarted` 中调用
- [ ] 每次无痕会话切换时调用 `AntiFingerprint.newSession()` 更新噪声种子
- [ ] Canvas 噪声不影响视觉（±1 RGB，人眼不可见）
- [ ] 不影响页面验证码（注入逻辑有 size > 0 守卫）
- [ ] SettingsActivity 有「反指纹保护」开关（默认开启）

### `AntiFingerprint.kt`

```kotlin
package com.example.freeavbrowser.privacy

import android.content.Context
import android.webkit.WebView
import kotlin.random.Random

object AntiFingerprint {

    // 每次会话的噪声种子（重启 App 或切换无痕模式时更新）
    private var sessionSeed = Random.nextInt(256)

    fun newSession() { sessionSeed = Random.nextInt(256) }

    fun inject(webView: WebView, context: Context) {
        if (!isEnabled(context)) return

        val seed = sessionSeed
        val nr = (seed % 3) - 1     // -1、0 或 1
        val ng = ((seed + 1) % 3) - 1
        val nb = ((seed + 2) % 3) - 1
        val pixelIdx = seed % 97    // 噪声像素位置（0–96，prime spread）

        val js = """
            (function() {
                'use strict';
                var NR = $nr, NG = $ng, NB = $nb, PIDX = $pixelIdx;

                // ── Canvas 2D ──────────────────────────────────────────
                var origToDataURL = HTMLCanvasElement.prototype.toDataURL;
                HTMLCanvasElement.prototype.toDataURL = function(type, quality) {
                    var ctx = this.getContext('2d');
                    if (ctx && this.width > 0 && this.height > 0) {
                        try {
                            var imgData = ctx.getImageData(0, 0, this.width, this.height);
                            var d = imgData.data;
                            var idx = (PIDX % Math.floor(d.length / 4)) * 4;
                            if (idx + 3 < d.length) {
                                d[idx]   = Math.max(0, Math.min(255, d[idx]   + NR));
                                d[idx+1] = Math.max(0, Math.min(255, d[idx+1] + NG));
                                d[idx+2] = Math.max(0, Math.min(255, d[idx+2] + NB));
                            }
                            ctx.putImageData(imgData, 0, 0);
                        } catch(e) {}
                    }
                    return origToDataURL.apply(this, arguments);
                };

                // ── WebGL ──────────────────────────────────────────────
                function patchWebGL(ctx) {
                    if (!ctx) return;
                    var orig = ctx.prototype.getParameter;
                    ctx.prototype.getParameter = function(p) {
                        if (p === 37446) return 'ANGLE (Generic GPU)';  // RENDERER
                        if (p === 37445) return 'Google Inc.';           // VENDOR
                        return orig.apply(this, arguments);
                    };
                }
                patchWebGL(window.WebGLRenderingContext);
                patchWebGL(window.WebGL2RenderingContext);

                // ── Screen ────────────────────────────────────────────
                try {
                    ['width','availWidth'].forEach(function(k) {
                        Object.defineProperty(screen, k, { get: function(){ return 1080; } });
                    });
                    ['height','availHeight'].forEach(function(k) {
                        Object.defineProperty(screen, k, { get: function(){ return 2340; } });
                    });
                    Object.defineProperty(screen, 'colorDepth', { get: function(){ return 24; } });
                    Object.defineProperty(screen, 'pixelDepth',  { get: function(){ return 24; } });
                } catch(e) {}

                // ── navigator.languages ───────────────────────────────
                try {
                    Object.defineProperty(navigator, 'languages', {
                        get: function() { return ['zh-CN', 'zh']; }, configurable: true
                    });
                } catch(e) {}

                // ── navigator.plugins ─────────────────────────────────
                try {
                    Object.defineProperty(navigator, 'plugins', {
                        get: function() { return []; }, configurable: true
                    });
                } catch(e) {}

                // ── Intl 时区标准化 ───────────────────────────────────
                var origRI = Intl.DateTimeFormat.prototype.resolvedOptions;
                Intl.DateTimeFormat.prototype.resolvedOptions = function() {
                    return Object.assign({}, origRI.apply(this, arguments),
                        { timeZone: 'Asia/Shanghai' });
                };

                // ── AudioContext 噪声 ─────────────────────────────────
                if (typeof AudioBuffer !== 'undefined') {
                    var origGCD = AudioBuffer.prototype.getChannelData;
                    AudioBuffer.prototype.getChannelData = function(ch) {
                        var data = origGCD.apply(this, arguments);
                        if (data && data.length > 0) data[0] += 1e-7 * NR;
                        return data;
                    };
                }

                // ── performance.now 精度降级 ──────────────────────────
                if (window.performance && window.performance.now) {
                    var origPN = window.performance.now.bind(window.performance);
                    window.performance.now = function() {
                        return Math.floor(origPN() / 10) * 10;
                    };
                }
            })();
        """.trimIndent()

        webView.evaluateJavascript(js, null)
    }

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences("privacy_settings", Context.MODE_PRIVATE)
            .getBoolean("antifingerprint", true)
}
```

---

## P-04 Cookie 隔离与第三方 Cookie 控制

### 验收标准

- [ ] WebView 初始化时调用 `CookieController.applySettings()`
- [ ] 默认阻止第三方 Cookie（`setAcceptThirdPartyCookies(webView, false)`）
- [ ] 无痕模式下：全部 Cookie / DOM Storage / Cache 禁用
- [ ] 提供「清除浏览数据」功能（Settings 或工具栏按钮）
- [ ] 提供「退出时自动清除 Cookie」选项（默认关闭）
- [ ] `WebSettings.mixedContentMode = MIXED_CONTENT_COMPATIBILITY_MODE`（JAV 站混合内容兼容）

### `CookieController.kt`

```kotlin
package com.example.freeavbrowser.privacy

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView

object CookieController {

    fun applySettings(webView: WebView, context: Context) {
        val prefs = context.getSharedPreferences("privacy_settings", Context.MODE_PRIVATE)
        val blockThirdParty = prefs.getBoolean("block_third_party_cookies", true)
        val incognito = IncognitoSession.isActive

        val cm = CookieManager.getInstance()
        cm.setAcceptCookie(!incognito)
        cm.setAcceptThirdPartyCookies(webView, if (incognito) false else !blockThirdParty)

        webView.settings.apply {
            domStorageEnabled = !incognito
            databaseEnabled   = !incognito
            savePassword      = false
            saveFormData      = false
            mixedContentMode  = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            cacheMode         = if (incognito) WebSettings.LOAD_NO_CACHE
                                else WebSettings.LOAD_DEFAULT
        }
    }

    /** 清除所有会话数据（无痕模式退出 / 用户手动触发） */
    fun clearAllData(webView: WebView) {
        webView.clearHistory()
        webView.clearFormData()
        webView.clearCache(true)
        CookieManager.getInstance().apply {
            removeAllCookies(null)
            flush()
        }
        WebStorage.getInstance().deleteAllData()
        android.util.Log.d("CookieController", "All session data cleared")
    }

    /** 仅清除 Session Cookie（保留 Remember Me 等持久 Cookie） */
    fun clearSessionCookies() {
        CookieManager.getInstance().removeSessionCookies(null)
    }
}
```

---

## P-05 HTTPS 强制升级

### 验收标准

- [ ] `shouldOverrideUrlLoading` 中检测 `http://`，自动重写为 `https://`
- [ ] 豁免：`.ts`、`.m3u8`、`.key` 后缀的媒体资源（视频 CDN 可能走 HTTP）
- [ ] 豁免：`localhost`、`127.0.0.1`
- [ ] SettingsActivity 有「HTTPS 强制升级」开关（默认开启）
- [ ] 升级失败时（服务器返回 HTTP 错误）不阻塞用户继续操作

### `HttpsEnforcer.kt`

```kotlin
package com.example.freeavbrowser.privacy

import android.content.Context
import android.net.Uri

object HttpsEnforcer {

    private val EXEMPT_HOSTS = setOf("localhost", "127.0.0.1", "10.0.2.2")
    private val EXEMPT_EXTENSIONS = setOf(".ts", ".m3u8", ".key", ".mp4", ".mp3")

    /**
     * @return 升级后的 HTTPS URL，或 null（不需要升级）
     */
    fun tryUpgrade(url: String, context: Context): String? {
        if (!isEnabled(context)) return null
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return null
        if (uri.scheme != "http") return null
        val host = uri.host ?: return null
        if (EXEMPT_HOSTS.contains(host)) return null
        val path = (uri.path ?: "").lowercase()
        if (EXEMPT_EXTENSIONS.any { path.endsWith(it) }) return null

        return uri.buildUpon().scheme("https").build().toString()
    }

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences("privacy_settings", Context.MODE_PRIVATE)
            .getBoolean("https_upgrade", true)
}
```

**集成到 `shouldOverrideUrlLoading`**：

```kotlin
override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
    val url = request?.url?.toString() ?: return false

    // P-05: HTTPS 升级（最先检查）
    HttpsEnforcer.tryUpgrade(url, this@MainActivity)?.let { httpsUrl ->
        view?.loadUrl(httpsUrl)
        return true
    }

    // 原有弹窗/重定向拦截逻辑（保留不变）...
    return super.shouldOverrideUrlLoading(view, request)
}
```

---

## P-06 WebView 权限守卫

### 背景

广告脚本或恶意页面会请求摄像头、麦克风、地理位置等敏感权限。应用本身不需要这些功能，一律拒绝。仅保留 `RESOURCE_PROTECTED_MEDIA_ID`（HLS DRM 解密）。

### 验收标准

- [ ] `WebChromeClient.onPermissionRequest` 仅允许 Protected Media，其余全部拒绝
- [ ] `WebChromeClient.onGeolocationPermissionsShowPrompt` 永远拒绝
- [ ] 拒绝时 Toast 提示用户「已阻止权限：摄像头/麦克风/定位」
- [ ] **Safe Browsing 明确禁用**（会将 URL 发往 Google，侵犯隐私）

### `PermissionGuard.kt`

```kotlin
package com.example.freeavbrowser.privacy

import android.content.Context
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.widget.Toast

object PermissionGuard {

    private val ALLOWED = setOf(PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID)

    private val NAMES = mapOf(
        PermissionRequest.RESOURCE_AUDIO_CAPTURE      to "麦克风",
        PermissionRequest.RESOURCE_VIDEO_CAPTURE      to "摄像头",
        PermissionRequest.RESOURCE_MIDI_SYSEX         to "MIDI",
        PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID to "DRM 媒体"
    )

    fun onPermissionRequest(request: PermissionRequest, context: Context) {
        val toGrant = request.resources.filter { it in ALLOWED }.toTypedArray()
        val toDeny  = request.resources.filter { it !in ALLOWED }

        if (toDeny.isNotEmpty()) {
            val names = toDeny.mapNotNull { NAMES[it] }.joinToString("、")
            if (names.isNotBlank())
                Toast.makeText(context, "已阻止权限请求：$names", Toast.LENGTH_SHORT).show()
        }

        if (toGrant.isNotEmpty()) request.grant(toGrant) else request.deny()
    }

    fun onGeolocationPermissionsShowPrompt(
        origin: String,
        callback: GeolocationPermissions.Callback
    ) {
        Toast.makeText(/* context needed — pass via parameter */ null,
            "已阻止地理位置请求", Toast.LENGTH_SHORT)
        callback.invoke(origin, false, false)
    }
}
```

> 注意：`onGeolocationPermissionsShowPrompt` 需要将 `context` 作为参数传入（略作调整）。

**集成到 WebChromeClient（MainActivity 中）**：

```kotlin
webView.webChromeClient = object : WebChromeClient() {
    override fun onPermissionRequest(request: PermissionRequest) {
        PermissionGuard.onPermissionRequest(request, this@MainActivity)
    }
    override fun onGeolocationPermissionsShowPrompt(
        origin: String, callback: GeolocationPermissions.Callback) {
        Toast.makeText(this@MainActivity, "已阻止地理位置请求", Toast.LENGTH_SHORT).show()
        callback.invoke(origin, false, false)
    }
    // ... 原有方法保留
}

// Safe Browsing 明确禁用
webView.settings.setSafeBrowsingEnabled(false)
```

---

## P-07 无痕会话（Incognito Mode）

### 验收标准

- [ ] 工具栏「无痕模式」切换按钮（面具图标），激活时图标高亮（M3 Primary 色）
- [ ] 开启时：禁用 Cookie / DOM Storage / Cache / 历史记录写入
- [ ] 关闭时：自动清除本次会话所有浏览数据（Cookie / Cache / History）
- [ ] `onStop`（App 进后台）时，如处于无痕模式，立即清除数据
- [ ] 无痕状态不写入 SharedPreferences（内存状态，重启后归零）
- [ ] 切换无痕模式时调用 `AntiFingerprint.newSession()`（重置噪声种子）

### `IncognitoSession.kt`

```kotlin
package com.example.freeavbrowser.privacy

import android.content.Context
import android.webkit.WebView

object IncognitoSession {

    @Volatile private var _active = false
    val isActive get() = _active

    fun enter(webView: WebView, context: Context) {
        _active = true
        AntiFingerprint.newSession()
        CookieController.applySettings(webView, context)
    }

    fun exit(webView: WebView, context: Context) {
        _active = false
        CookieController.clearAllData(webView)
        CookieController.applySettings(webView, context)
        webView.loadUrl("about:blank")
    }

    /** App 进入后台时调用（onStop） */
    fun onBackground(webView: WebView, context: Context) {
        if (_active) CookieController.clearAllData(webView)
    }
}
```

**集成到 `MainActivity`**：

```kotlin
// onStop
override fun onStop() {
    super.onStop()
    IncognitoSession.onBackground(webView, this)
}

// 工具栏按钮
binding.btnIncognito.setOnClickListener {
    if (IncognitoSession.isActive) {
        IncognitoSession.exit(webView, this)
        binding.btnIncognito.setIconTintResource(R.color.md_neutral_60)
        Snackbar.make(binding.root, "已退出无痕模式，会话数据已清除", Snackbar.LENGTH_SHORT).show()
    } else {
        IncognitoSession.enter(webView, this)
        binding.btnIncognito.setIconTintResource(R.color.md_primary_40)
        Snackbar.make(binding.root, "无痕模式已开启", Snackbar.LENGTH_SHORT).show()
    }
}
```

---

## P-08 追踪 JS API 拦截

### 覆盖的追踪 API

| API | 追踪用途 | 拦截方式 |
|-----|---------|---------|
| `navigator.sendBeacon()` | 页面离开时静默上报 | 覆盖，匹配追踪 URL 时静默丢弃 |
| `fetch()` | 异步追踪事件 | 覆盖，匹配追踪 URL 时返回 204 |
| `XMLHttpRequest` | 同步/异步追踪 | 覆盖 `open`+`send`，匹配时模拟 204 |
| `performance.now` | 定时器指纹 | 降至 10ms 精度（已在 P-03 覆盖） |

> **注**：`fetch`/`XHR` 的域名层面拦截已由 `shouldInterceptRequest` 覆盖；此模块作为补充，拦截**路径关键字层面**的追踪请求（如 `/track`、`/beacon`）。

### 验收标准

- [ ] `onPageFinished` 中调用（页面 JS 加载后再覆盖，确保覆盖生效）
- [ ] 关键字匹配列表可扩展（配置文件或常量）
- [ ] 静默处理（不抛出 JS 异常，不破坏页面功能）
- [ ] SettingsActivity 有「追踪 API 拦截」开关（默认开启）

### `TrackingApiBlocker.kt`

```kotlin
package com.example.freeavbrowser.privacy

import android.content.Context
import android.webkit.WebView

object TrackingApiBlocker {

    fun inject(webView: WebView, context: Context) {
        if (!isEnabled(context)) return

        val js = """
            (function() {
                'use strict';

                var TRACKER_KW = [
                    'analytics', '/pixel/', '/beacon', '/track', '/collect',
                    'telemetry', '/statistic', '/log?', 'fingerprint',
                    'googlesyndication', 'doubleclick', '/tr?', 'facebook.com/tr',
                    'hotjar.com', 'mixpanel.com', 'segment.io', 'amplitude.com'
                ];

                function isTracker(url) {
                    if (!url || typeof url !== 'string') return false;
                    var l = url.toLowerCase();
                    return TRACKER_KW.some(function(kw) { return l.indexOf(kw) !== -1; });
                }

                // sendBeacon ─────────────────────────────────────────────
                var origBeacon = navigator.sendBeacon && navigator.sendBeacon.bind(navigator);
                if (origBeacon) {
                    navigator.sendBeacon = function(url, data) {
                        if (isTracker(url)) return true;   // 假装成功，静默丢弃
                        return origBeacon(url, data);
                    };
                }

                // fetch ──────────────────────────────────────────────────
                var origFetch = window.fetch;
                if (origFetch) {
                    window.fetch = function(input, init) {
                        var url = typeof input === 'string' ? input
                                : (input && input.url ? input.url : '');
                        if (isTracker(url))
                            return Promise.resolve(new Response('', { status: 204 }));
                        return origFetch.apply(this, arguments);
                    };
                }

                // XMLHttpRequest ─────────────────────────────────────────
                var origOpen = XMLHttpRequest.prototype.open;
                XMLHttpRequest.prototype.open = function(method, url) {
                    this._javbUrl = url;
                    return origOpen.apply(this, arguments);
                };
                var origSend = XMLHttpRequest.prototype.send;
                XMLHttpRequest.prototype.send = function(body) {
                    if (this._javbUrl && isTracker(this._javbUrl)) {
                        var self = this;
                        setTimeout(function() {
                            try {
                                Object.defineProperty(self, 'readyState', { value: 4, writable: true });
                                Object.defineProperty(self, 'status', { value: 204, writable: true });
                                if (self.onreadystatechange) self.onreadystatechange();
                                if (self.onload) self.onload();
                            } catch(e) {}
                        }, 0);
                        return;
                    }
                    return origSend.apply(this, arguments);
                };
            })();
        """.trimIndent()

        webView.evaluateJavascript(js, null)
    }

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences("privacy_settings", Context.MODE_PRIVATE)
            .getBoolean("tracking_api_block", true)
}
```

---

## 四、统一配置 — `PrivacyConfig.kt`

```kotlin
package com.example.freeavbrowser.privacy

import android.content.Context

object PrivacyConfig {

    private const val PREF = "privacy_settings"

    data class State(
        val webRtcBlock: Boolean          = true,
        val antiFingerprint: Boolean      = true,
        val blockThirdPartyCookies: Boolean = true,
        val httpsUpgrade: Boolean         = true,
        val trackingApiBlock: Boolean     = true,
        val cosmeticEnabled: Boolean      = true,
        val clearCookiesOnExit: Boolean   = false,
        val dohProvider: DoHProvider      = DoHProvider.NONE,
        val dohCustomUrl: String          = ""
    )

    fun load(context: Context): State {
        val p = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        return State(
            webRtcBlock             = p.getBoolean("webrtc_block", true),
            antiFingerprint         = p.getBoolean("antifingerprint", true),
            blockThirdPartyCookies  = p.getBoolean("block_third_party_cookies", true),
            httpsUpgrade            = p.getBoolean("https_upgrade", true),
            trackingApiBlock        = p.getBoolean("tracking_api_block", true),
            cosmeticEnabled         = p.getBoolean("cosmetic_enabled", true),
            clearCookiesOnExit      = p.getBoolean("clear_cookies_on_exit", false),
            dohProvider             = runCatching {
                DoHProvider.valueOf(p.getString("doh_provider", DoHProvider.NONE.name)!!)
            }.getOrElse { DoHProvider.NONE },
            dohCustomUrl            = p.getString("doh_custom_url", "") ?: ""
        )
    }

    fun save(context: Context, state: State) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().apply {
            putBoolean("webrtc_block",              state.webRtcBlock)
            putBoolean("antifingerprint",           state.antiFingerprint)
            putBoolean("block_third_party_cookies", state.blockThirdPartyCookies)
            putBoolean("https_upgrade",             state.httpsUpgrade)
            putBoolean("tracking_api_block",        state.trackingApiBlock)
            putBoolean("cosmetic_enabled",          state.cosmeticEnabled)
            putBoolean("clear_cookies_on_exit",     state.clearCookiesOnExit)
            putString("doh_provider",               state.dohProvider.name)
            putString("doh_custom_url",             state.dohCustomUrl)
            apply()
        }
    }
}
```

---

## 五、集成总调用顺序（MainActivity）

```
onCreate()
├── WebView 初始化
│   ├── webView.settings.setSafeBrowsingEnabled(false)   ← 禁用 Safe Browsing
│   └── CookieController.applySettings(webView, this)    ← P-04 初始化
│
onPageStarted()                        ← 越早注入越安全
├── WebRtcBlocker.inject()             ← P-02 最高优先级
├── AntiFingerprint.inject()           ← P-03
└── CosmeticInjector.injectPopupBlocker()
│
onPageFinished()
├── CosmeticInjector.inject()          ← 元素隐藏
└── TrackingApiBlocker.inject()        ← P-08（覆盖页面 JS 的追踪 API）
│
shouldInterceptRequest()
├── 内容类型守卫（.m3u8/.ts 放行）
├── 同源守卫
├── 硬编码白名单
├── HostsManager
└── AdBlockEngine.shouldBlock()
│
shouldOverrideUrlLoading()
├── HttpsEnforcer.tryUpgrade()         ← P-05
└── 原有弹窗拦截逻辑
│
WebChromeClient.onPermissionRequest()
└── PermissionGuard.onPermissionRequest()   ← P-06
│
onStop()
└── IncognitoSession.onBackground()    ← P-07 后台清理
```

---

## 六、Settings — 隐私保护分组 UI 规格

```
┌─ 隐私保护 ──────────────────────────────────────────────┐
│                                                          │
│  WebRTC 防护（阻止 IP 泄露）           [■ 开启]          │
│  反指纹保护（Canvas / WebGL 噪声）     [■ 开启]          │
│  阻止第三方 Cookie                     [■ 开启]          │
│  HTTPS 强制升级                        [■ 开启]          │
│  追踪 API 拦截（sendBeacon / fetch）   [■ 开启]          │
│  退出时清除 Cookie                     [□ 关闭]          │
│  ──────────────────────────────────────────────────── │
│  DNS 加密（DoH）          当前：系统  [切换 ▸]           │
│    提供商：○ 系统  ● Cloudflare  ○ 阿里 DNS  ○ 自定义  │
│    [测试连接]  上次测试：延迟 38 ms                      │
│  ──────────────────────────────────────────────────── │
│  [清除所有浏览数据]                                      │
└──────────────────────────────────────────────────────────┘
```

---

## 七、不适合集成的方案（详细原因）

| 方案 | 不集成原因 |
|------|-----------|
| **本地 VPN（RethinkDNS / NetGuard 能力）** | 需要 `BIND_VPN_SERVICE` 权限 + 持续前台服务；与用户代理 App（F-02）互斥；`shouldInterceptRequest` 已覆盖所有 WebView 流量，VPN 无额外价值 |
| **System Private DNS 编程设置** | Android 不提供应用层 API 修改系统 DNS；可在 Settings 中引导用户手动设置，但不能代劳 |
| **Google Safe Browsing** | 将访问的每个 URL 发送到 Google 进行检测，严重侵犯用户隐私；**应明确禁用** |
| **Xposed/LSPosed** | 需要 Root；破坏 Play Integrity |
| **AdAway Root 模式** | 需要 Root + system 分区写权限；F-03 自定义 Hosts 已覆盖同等能力 |
| **浏览器扩展（uBlock Origin MV3）** | Android WebView 不支持浏览器扩展机制；等效能力通过 shouldInterceptRequest + JS 注入实现 |
| **GrapheneOS 隐私沙箱** | 系统 ROM 特性，普通 App 无法调用 |

---

## 八、实施优先级

```
P1 — 立即实现（高价值，零风险）
├── P-02 WebRTC 防护     ← JAV 浏览器最严重的 IP 泄露漏洞
├── P-06 权限守卫        ← 拒绝摄像头/麦克风/定位，1 小时内完成
└── P-04 Cookie 隔离     ← 阻止第三方 Cookie，1 行 API 调用

P2 — 高优先级
├── P-03 反指纹          ← 防止跨会话追踪
├── P-07 无痕会话        ← 用户明确需要的隐私功能
└── P-05 HTTPS 升级      ← 防止中间人嗅探

P3 — 增强功能
├── P-08 追踪 JS API     ← sendBeacon/fetch 补充拦截
└── P-01 DoH             ← 保护规则下载/视频代理的 DNS 隐私

P4 — 用户可选
└── DoH 提供商切换 UI    ← 国内用户推荐阿里 DNS
```
