# JAV Browser - 功能实施详细说明

> **文档版本**: v1.0  
> **更新日期**: 2026-06-07  
> **技术栈**: Android (Kotlin) + WebView + NanoHTTPD

---

## F-01: 主题切换（深色模式）

### 功能描述
实现应用级别和 WebView 级别的深色模式，支持即时切换（无需重启）。

### 技术方案

#### 1.1 应用层主题切换
```kotlin
// ThemeManager.kt
object ThemeManager {
    enum class Theme {
        LIGHT,      // 浅色模式
        DARK,       // 深色模式
        SYSTEM      // 跟随系统
    }
    
    fun applyTheme(theme: Theme) {
        val mode = when (theme) {
            Theme.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            Theme.DARK -> AppCompatDelegate.MODE_NIGHT_YES
            Theme.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(mode)
        // 无需 recreate()，立即生效
    }
}
```

#### 1.2 WebView 深色模式同步
```kotlin
// 需要 androidx.webkit:webkit:1.8.0+
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
    WebSettingsCompat.setForceDark(
        webView.settings,
        WebSettingsCompat.FORCE_DARK_ON
    )
}
```

### 依赖项
```kotlin
// app/build.gradle.kts
dependencies {
    implementation("androidx.webkit:webkit:1.8.0")
}
```

### 相关文件
- 新增: `app/src/main/java/com/example/javbrowser/ThemeManager.kt`
- 修改: `MainActivity.kt` - 在 onCreate 中应用主题
- 修改: `SettingsActivity.kt` - 添加主题切换选项

---

## F-02: 代理设置

### 功能描述
支持 HTTP/HTTPS/SOCKS5 代理，覆盖 WebView 和网络请求。

### 技术方案

#### 2.1 WebView 代理（HTTP/HTTPS）
```kotlin
// ProxyManager.kt
class ProxyManager(private val context: Context) {
    
    fun setWebViewProxy(host: String, port: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ProxyController.getInstance()
                .setProxyOverride(
                    ProxyConfig.Builder()
                        .addProxyRule("$host:$port")
                        .build(),
                    { /* success */ },
                    { /* failure */ }
                )
        }
    }
}
```

#### 2.2 SOCKS5 代理（通过 NanoHTTPD 中转）
```kotlin
// VideoProxyServer.kt 扩展
class VideoProxyServer : NanoHTTPD("localhost", 0) {
    
    private var socksProxy: Proxy? = null
    
    fun setSocksProxy(host: String, port: Int) {
        socksProxy = Proxy(
            Proxy.Type.SOCKS,
            InetSocketAddress(host, port)
        )
    }
    
    override fun serve(session: IHTTPSession): Response {
        val targetUrl = session.parameters["url"]?.first() ?: return error()
        
        // 使用 SOCKS5 代理请求
        val connection = URL(targetUrl).openConnection(socksProxy ?: Proxy.NO_PROXY)
        // ... 转发响应
    }
}
```

#### 2.3 OkHttp 代理（非 WebView 请求）
```kotlin
val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress("proxy.example.com", 8080))
val client = OkHttpClient.Builder()
    .proxy(proxy)
    .build()
```

### 依赖项
```kotlin
dependencies {
    implementation("androidx.webkit:webkit:1.8.0")
}
```

### 相关文件
- 新增: `app/src/main/java/com/example/javbrowser/ProxyManager.kt`
- 修改: `VideoProxyServer.kt` - 添加 SOCKS5 支持
- 修改: `SettingsActivity.kt` - 代理配置界面

---

## F-03: 内置 Hosts 文件

### 功能描述
支持自定义 Hosts 映射，绕过 DNS 解析，适用于域名封锁场景。

### 技术方案

#### 3.1 Hosts 文件格式
```
# /data/data/com.example.javbrowser/files/hosts
127.0.0.1 localhost
185.199.108.133 raw.githubusercontent.com
104.21.12.34 missav.ai
104.21.12.34 missav.ws
```

#### 3.2 WebView 请求拦截
```kotlin
// HostsManager.kt
class HostsManager(context: Context) {
    
    private val hostsMap = ConcurrentHashMap<String, String>()
    
    init {
        loadHostsFile(context)
    }
    
    private fun loadHostsFile(context: Context) {
        val hostsFile = File(context.filesDir, "hosts")
        if (hostsFile.exists()) {
            hostsFile.readLines().forEach { line ->
                val parts = line.trim().split(Regex("\\s+"))
                if (parts.size >= 2 && !line.startsWith("#")) {
                    hostsMap[parts[1]] = parts[0]
                }
            }
        }
    }
    
    fun resolveHost(hostname: String): String? {
        return hostsMap[hostname]
    }
}
```

#### 3.3 双层拦截
```kotlin
// MainActivity.kt WebViewClient
override fun shouldInterceptRequest(
    view: WebView,
    request: WebResourceRequest
): WebResourceResponse? {
    val url = request.url.toString()
    val hostname = request.url.host ?: return super.shouldInterceptRequest(view, request)
    
    // 检查 Hosts 映射
    val customIp = hostsManager.resolveHost(hostname)
    if (customIp != null) {
        val newUrl = url.replace(hostname, customIp)
        // 使用自定义 IP 请求
        return fetchWithCustomIp(newUrl, hostname)
    }
    
    return super.shouldInterceptRequest(view, request)
}
```

#### 3.4 OkHttp DNS 覆盖
```kotlin
class CustomDns(private val hostsManager: HostsManager) : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        val customIp = hostsManager.resolveHost(hostname)
        if (customIp != null) {
            return listOf(InetAddress.getByName(customIp))
        }
        return Dns.SYSTEM.lookup(hostname)
    }
}

val client = OkHttpClient.Builder()
    .dns(CustomDns(hostsManager))
    .build()
```

### 相关文件
- 新增: `app/src/main/java/com/example/javbrowser/HostsManager.kt`
- 修改: `MainActivity.kt` - 集成 Hosts 拦截
- 修改: `VideoProxyServer.kt` - 使用自定义 DNS

---

## F-04: 智能域名切换

### 功能描述
自动探测多个镜像域名的可用性，选择延迟最低的域名访问。

### 技术方案

#### 4.1 扩展 ad-filter-rules.json
```json
{
  "version": "2.4.0",
  "domains": {
    "missav": "missav.ai"
  },
  "domain_candidates": {
    "missav": [
      "missav.ai",
      "missav.ws",
      "missav123.com",
      "missav.live"
    ],
    "jable": [
      "jable.tv",
      "fs1.app"
    ]
  }
}
```

#### 4.2 并发探活
```kotlin
// DomainProber.kt
class DomainProber {
    
    data class ProbeResult(
        val domain: String,
        val latencyMs: Long,
        val isAvailable: Boolean
    )
    
    suspend fun probeDomains(candidates: List<String>): List<ProbeResult> {
        return coroutineScope {
            candidates.map { domain ->
                async(Dispatchers.IO) {
                    val startTime = System.currentTimeMillis()
                    try {
                        val url = URL("https://$domain")
                        val connection = url.openConnection() as HttpURLConnection
                        connection.requestMethod = "HEAD"
                        connection.connectTimeout = 5000
                        connection.readTimeout = 5000
                        
                        val responseCode = connection.responseCode
                        val latency = System.currentTimeMillis() - startTime
                        
                        ProbeResult(
                            domain = domain,
                            latencyMs = latency,
                            isAvailable = responseCode in 200..399
                        )
                    } catch (e: Exception) {
                        ProbeResult(domain, Long.MAX_VALUE, false)
                    }
                }
            }.awaitAll()
                .filter { it.isAvailable }
                .sortedBy { it.latencyMs }
        }
    }
}
```

#### 4.3 自动 URL 重写
```kotlin
// DomainConfig.kt 扩展
suspend fun rewriteUrlToFastestDomain(url: String, site: String): String {
    val candidates = getCandidates(site)
    val results = domainProber.probeDomains(candidates)
    
    val fastestDomain = results.firstOrNull()?.domain ?: return url
    val currentDomain = getCurrentDomain(site)
    
    return url.replace(currentDomain, fastestDomain)
}
```

### 相关文件
- 新增: `app/src/main/java/com/example/javbrowser/DomainProber.kt`
- 修改: `DomainConfig.kt` - 添加探活逻辑
- 修改: `ad-filter-rules.json` - 添加 domain_candidates 字段

---
## F-05: 增强广告屏蔽系统

### 功能描述
三层联动的广告屏蔽：网络层 + DOM 层 + 隐私层。

### 技术方案

#### 5.1 网络层拦截（已实现，需增强）
```kotlin
// AdFilterEngine.kt
class AdFilterEngine {
    
    private val blockList = ConcurrentHashMap<String, Boolean>()
    private val cosmeticFilters = ConcurrentHashMap<String, List<String>>()
    
    fun shouldBlock(url: String): Boolean {
        return blockList.keys.any { pattern ->
            url.contains(pattern, ignoreCase = true)
        }
    }
}
```

#### 5.2 DOM 层拦截（CSS Cosmetic Filters）
```kotlin
// 注入 JavaScript 实现 CSS 选择器隐藏
val cosmeticScript = """
    (function() {
        const filters = ${gson.toJson(cosmeticFilters)};
        
        function applyFilters() {
            filters.forEach(selector => {
                document.querySelectorAll(selector).forEach(el => {
                    el.style.display = 'none';
                    el.remove();
                });
            });
        }
        
        // 初始执行
        applyFilters();
        
        // MutationObserver 监听动态广告
        const observer = new MutationObserver(applyFilters);
        observer.observe(document.body, {
            childList: true,
            subtree: true
        });
    })();
""".trimIndent()

webView.evaluateJavascript(cosmeticScript, null)
```

### 相关文件
- 修改: `AdFilterRules.kt` - 重构为 AdFilterEngine
- 新增: `app/src/main/java/com/example/javbrowser/FilterUpdateWorker.kt`
- 修改: `MainActivity.kt` - 注入隐私保护脚本


## F-06: M3U8 视频下载（无 FFmpeg）

### 功能描述
完整的 M3U8 下载功能，支持 AES-128 解密、断点续传、后台下载。

### 技术方案

#### 6.1 核心下载类
```kotlin
// VideoDownloadTask.kt
class VideoDownloadTask(private val m3u8Url: String, private val outputFile: File) {
    
    private val semaphore = Semaphore(4) // 最多 4 个并发
    
    suspend fun download(onProgress: (Int) -> Unit): Result<File> = coroutineScope {
        // 使用 javax.crypto.Cipher 解密 AES-128
        // 直接字节流拼接 TS 分片（无需 FFmpeg）
        // Room 持久化任务状态
    }
}
```

### 依赖项
```kotlin
dependencies {
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")
    implementation("androidx.work:work-runtime-ktx:2.9.0")
}
```

### 相关文件
- 新增: `app/src/main/java/com/example/javbrowser/download/` 目录
- 新增: `VideoDownloadTask.kt`, `M3U8Parser.kt`, `AESDecryptor.kt`
- 新增: `database/DownloadTask.kt` (Room Entity)

---

## CI-01: GitHub Actions Release 签名

### 功能描述
使用 GitHub Actions Secrets 存储签名密钥，自动构建和签名 Release APK。

### 技术方案

#### CI-01.1 GitHub Actions 配置
```yaml
# .github/workflows/release.yml
- name: Build Release APK
  run: |
    ./gradlew assembleRelease \
      -Pandroid.injected.signing.store.file=./release.keystore \
      -Pandroid.injected.signing.store.password="${{ secrets.KEYSTORE_PASSWORD }}" \
      -Pandroid.injected.signing.key.alias="${{ secrets.KEY_ALIAS }}" \
      -Pandroid.injected.signing.key.password="${{ secrets.KEY_PASSWORD }}"
```

### GitHub Secrets 设置
- `KEYSTORE_BASE64`: release.keystore 的 Base64 编码
- `KEYSTORE_PASSWORD`: keystore 密码
- `KEY_ALIAS`: 密钥别名
- `KEY_PASSWORD`: 密钥密码

### 相关文件
- 新增: `.github/workflows/release.yml`
- 修改: `app/build.gradle.kts` - 添加签名配置
- 新增: `.gitignore` - 排除 `*.keystore` 文件

---

## 总结

所有功能已详细说明实施方案，涵盖：
- ✅ 技术实现代码
- ✅ 依赖库清单
- ✅ 相关文件路径
- ✅ 完全基于 Android/Kotlin 技术栈
