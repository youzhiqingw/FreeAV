package com.example.freeavbrowser

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.navigation.NavigationBarView
import com.google.android.material.progressindicator.LinearProgressIndicator
import java.io.ByteArrayInputStream

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var adFilterRules: AdFilterRules
    private lateinit var domainConfig: DomainConfig
    private lateinit var btnPlay: MaterialButton
    private lateinit var progressBar: LinearProgressIndicator
    private lateinit var bottomNavigation: NavigationBarView
    private lateinit var favoritesManager: FavoritesManager
    private lateinit var privacySettings: PrivacySettings
    private var currentVideoUrl: String? = null
    private var currentVideoReferer: String? = null
    private var videoProxyServer: VideoProxyServer? = null
    private var isUnlocked = false
    private var isFreshStart = true
    private var backPressedTime: Long = 0

    // Loading Timeout & Progress
    private var loadStartTime: Long = 0
    private var timeoutHandler: android.os.Handler? = null
    private var timeoutRunnable: Runnable? = null
    private val TIMEOUT_DURATION = 30000L // 30 seconds
    // 儲存每個 URL 對應的滾動位置，格式為 url -> Pair(scrollX, scrollY)
    private val scrollPositionMap = HashMap<String, Pair<Int, Int>>()
    // 標記下一次 onPageFinished 是否需要恢復滾動位置（因為是 goBack 觸發的）
    private var pendingScrollRestoreUrl: String? = null

    // Activity Result Launchers (替代已弃用的 startActivityForResult)
    private val favoritesLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val url = result.data?.getStringExtra("url")
            if (url != null) {
                val updatedUrl = domainConfig.updateUrlIfNeeded(url)
                webView.loadUrl(updatedUrl)
            }
        }
    }

    private val lockLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            isUnlocked = true
            privacySettings.updateUnlockTime()
        } else {
            // Lock failed or cancelled, finish app
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply saved dark mode preference before super.onCreate
        val prefs = getSharedPreferences("privacy_prefs", android.content.Context.MODE_PRIVATE)
        val darkMode = prefs.getInt("dark_mode", androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(darkMode)

        super.onCreate(savedInstanceState)
        // 根据设置条件控制防截屏（FLAG_SECURE）
        if (prefs.getBoolean("screenshot_block_enabled", true)) {
            window.setFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE, android.view.WindowManager.LayoutParams.FLAG_SECURE)
        }
        setContentView(R.layout.activity_main)

        favoritesManager = FavoritesManager(this)

        // Start local proxy for CDN-protected video (e.g. avjoy.me)
        try {
            videoProxyServer = VideoProxyServer(this)
            videoProxyServer?.start()
        } catch (e: Exception) {
            android.util.Log.e("VideoProxy", "Failed to start proxy: ${e.message}")
        }

        adFilterRules = AdFilterRules(this)
        privacySettings = PrivacySettings(this)
        domainConfig = DomainConfig()

        // biometricHelper = BiometricHelper(this) // Moved to LockActivity
        
        webView = findViewById(R.id.webView)
        btnPlay = findViewById(R.id.btn_play)
        bottomNavigation = findViewById(R.id.bottom_navigation)
        progressBar = findViewById(R.id.progressBar)

        initializeApp()
    }
    
    private fun initializeApp() {
        setupWebView()
        setupPlayButton()
        setupBottomNavigation()
        setupBackPressedHandler()

        loadLandingPage()

        // Handle URL if opened via external app intent
        handleIncomingIntent(intent)
    }

    private fun setupBackPressedHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.evaluateJavascript(
                        """
                        (function() {
                            var sy = window.scrollY || window.pageYOffset || document.documentElement.scrollTop || 0;
                            var key = 'scrollPos__' + window.location.href;
                            sessionStorage.setItem(key, sy);
                            return sy;
                        })();
                        """.trimIndent()
                    ) { _ ->
                        webView.goBack()
                    }
                } else {
                    if (backPressedTime + 2000 > System.currentTimeMillis()) {
                        showExitConfirmationDialog()
                    } else {
                        Toast.makeText(this@MainActivity, "再按一次退出", Toast.LENGTH_SHORT).show()
                        backPressedTime = System.currentTimeMillis()
                    }
                }
            }
        })
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return

        var urlToLoad: String? = null

        when (intent.action) {
            Intent.ACTION_VIEW -> {
                urlToLoad = intent.data?.toString()
            }
            Intent.ACTION_SEND -> {
                if (intent.type == "text/plain") {
                    val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
                    urlToLoad = sharedText?.let { extractUrl(it) }
                    if (urlToLoad == null && sharedText != null) {
                        Toast.makeText(this, "无法从分享内容中找到网址", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        if (urlToLoad != null) {
            // Use UrlSecurityValidator to prevent javascript:/file: injection
            when (val result = com.example.freeavbrowser.security.UrlSecurityValidator.validateExternalUrl(urlToLoad)) {
                is com.example.freeavbrowser.security.UrlSecurityValidator.ValidationResult.Valid -> {
                    val updatedUrl = domainConfig.updateUrlIfNeeded(result.url)
                    webView.post {
                        webView.loadUrl(updatedUrl)
                    }
                }
                is com.example.freeavbrowser.security.UrlSecurityValidator.ValidationResult.Invalid -> {
                    Toast.makeText(this, "不支持的链接: ${result.reason}", Toast.LENGTH_SHORT).show()
                }
                else -> {}
            }
        }
    }

    private fun extractUrl(text: String): String? {
        val urlRegex = "(https?://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|])".toRegex()
        val matchResult = urlRegex.find(text)
        return matchResult?.value
    }

    // ... (rest of the file)

    override fun onResume() {
        super.onResume()
        // Check if lock is needed when returning from background or fresh start
        if (privacySettings.isLockEnabled && !isUnlocked) {
            if (isFreshStart || privacySettings.shouldLock()) {
                val intent = Intent(this, LockActivity::class.java)
                lockLauncher.launch(intent)
            }
        }
        isFreshStart = false
    }

    override fun onStop() {
        super.onStop()
        // App is going to background, reset unlock state if lock is enabled
        if (privacySettings.isLockEnabled) {
            isUnlocked = false
        }

        // 隐私模式：离开应用时清除浏览记录
        if (privacySettings.isPrivacyModeEnabled) {
            try {
                webView.clearHistory()
                webView.clearCache(true)
                // 清除所有 Cookie（广告 Cookie 欺骗规则会在下次页面加载时重新注入）
                android.webkit.CookieManager.getInstance().removeAllCookies(null)
                android.webkit.CookieManager.getInstance().flush()
                // 清除表单数据
                webView.clearFormData()
            } catch (e: Exception) {
                // 忽略清理失败
            }
        }
    }

    // ... (rest of the file)



    private fun setupWebView() {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

        // Security configurations
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.setSavePassword(false)

        // Disable WebView debugging in release builds
        android.webkit.WebView.setWebContentsDebuggingEnabled(false)

        // Add JS interface globally
        webView.addJavascriptInterface(object {
            @android.webkit.JavascriptInterface
            fun onVideoFound(videoUrl: String) {
                runOnUiThread {
                    currentVideoUrl = videoUrl
                    btnPlay.visibility = View.VISIBLE
                }
            }
            
            @android.webkit.JavascriptInterface
            fun navigateToUrl(url: String) {
                // Validate URL to prevent javascript: injection
                if (!com.example.freeavbrowser.security.UrlSecurityValidator.validateJavaScriptUrl(url)) {
                    android.util.Log.w("MainActivity", "JS interface blocked invalid URL: $url")
                    return
                }

                runOnUiThread {
                    Toast.makeText(this@MainActivity, "正在连接...", Toast.LENGTH_SHORT).show()
                    progressBar.visibility = View.VISIBLE
                    progressBar.progress = 10
                    webView.loadUrl(url)
                    startLoadTimeout()
                }
            }
            
            @android.webkit.JavascriptInterface
            fun showHelpDialog() {
                runOnUiThread {
                    this@MainActivity.showHelpDialog()
                }
            }
            
            @android.webkit.JavascriptInterface
            fun loadLandingPage() {
                runOnUiThread {
                    this@MainActivity.loadLandingPage()
                }
            }
        }, "Android")

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                val lowerUrl = url.lowercase()

                // Block Shopee and Lazada redirects
                if (lowerUrl.contains("shopee") || lowerUrl.contains("shp.ee") || lowerUrl.contains("lazada")) {
                    return true
                }
                
                // Handle APK download
                if (url.contains(".apk") || url.contains("down_ra")) {
                    downloadAndInstallApk(url)
                    return true
                }
                
                // 離開當前頁面前，先把目前的滾動位置存入 sessionStorage（以當前頁 URL 為 key）
                // 這樣按返回鍵回來時，onPageFinished 才能正確恢復位置
                view?.evaluateJavascript("""
                    (function() {
                        var sy = window.scrollY || window.pageYOffset || document.documentElement.scrollTop || 0;
                        if (sy > 0) {
                            var key = 'scrollPos__' + window.location.href;
                            sessionStorage.setItem(key, sy);
                        }
                    })();
                """.trimIndent(), null)
                
                // Allow navigation to target URLs
                return false
            }

            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                val url = request?.url.toString()

                // 检查是否应该拦截
                if (!adFilterRules.shouldBlock(url)) {
                    // 放行：继续视频嗅探
                    if (url.contains(".m3u8") && !url.contains("minisite")) {
                        view?.post {
                            if (currentVideoUrl != url) {
                                currentVideoUrl = url
                                btnPlay.visibility = View.VISIBLE
                            }
                        }
                    }
                    return super.shouldInterceptRequest(view, request)
                }

                // 拦截：根据资源类型返回不同响应
                val resourceType = inferResourceType(url)

                // $redirect=noopjs → 返回空脚本而非空响应
                val host = android.net.Uri.parse(url).host?.lowercase() ?: ""
                if (adFilterRules.isRedirectRule(host)) {
                    return WebResourceResponse(
                        "application/javascript", "utf-8",
                        java.io.ByteArrayInputStream("/* noop */".toByteArray())
                    )
                }

                // 第三方 iframe 广告 → 返回空 HTML（阻止 iframe 加载广告页）
                if (isThirdPartyIframeAd(url)) {
                    return WebResourceResponse(
                        "text/html", "utf-8",
                        java.io.ByteArrayInputStream("<html><body></body></html>".toByteArray())
                    )
                }

                return when (resourceType) {
                    "script" -> WebResourceResponse(
                        "application/javascript", "utf-8",
                        java.io.ByteArrayInputStream("/* blocked */".toByteArray())
                    )
                    "stylesheet" -> WebResourceResponse(
                        "text/css", "utf-8",
                        java.io.ByteArrayInputStream("/* blocked */".toByteArray())
                    )
                    else -> WebResourceResponse(
                        "text/plain", "utf-8",
                        java.io.ByteArrayInputStream("".toByteArray())
                    )
                }
            }

            /**
             * 检测是否为第三方 iframe 广告请求。
             * 特征：域名匹配已知广告 iframe 源（smartpop, bluetraffic 等）。
             */
            private fun isThirdPartyIframeAd(url: String): Boolean {
                val host = android.net.Uri.parse(url).host?.lowercase() ?: return false
                val iframeAdDomains = listOf(
                    "bluetrafficstream.com", "smartpop", "mnaspm.com",
                    "havenclick.com", "wpadmngr.com", "clickadu",
                    "popunder", "tabunder", "popup"
                )
                return iframeAdDomains.any { host.contains(it) || host.endsWith(".$it") }
            }

            /**
             * 推断资源类型 — 类似 uBO 的 $script/$image/$media 过滤器。
             */
            private fun inferResourceType(url: String): String {
                val lowerUrl = url.lowercase()
                val path = android.net.Uri.parse(url).path?.lowercase() ?: ""

                // 根据扩展名推断
                if (path.endsWith(".js") || lowerUrl.contains(".js?") ||
                    lowerUrl.contains("/js/") || lowerUrl.contains("javascript"))
                    return "script"
                if (path.endsWith(".css") || lowerUrl.contains(".css?") || lowerUrl.contains("/css/"))
                    return "stylesheet"
                if (path.endsWith(".png") || path.endsWith(".jpg") || path.endsWith(".jpeg") ||
                    path.endsWith(".gif") || path.endsWith(".webp") || path.endsWith(".svg") ||
                    path.endsWith(".ico"))
                    return "image"
                if (path.endsWith(".mp4") || path.endsWith(".webm") || path.endsWith(".m3u8") ||
                    path.endsWith(".mp3") || path.endsWith(".ogg"))
                    return "media"
                if (path.endsWith(".woff") || path.endsWith(".woff2") || path.endsWith(".ttf"))
                    return "font"

                // 默认根据 URL 特征推断
                if (lowerUrl.contains("/ad") || lowerUrl.contains("admanager") ||
                    lowerUrl.contains("clickadu") || lowerUrl.contains("smartpop"))
                    return "script"

                return "other"
            }

            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                errorResponse: WebResourceResponse?
            ) {
                super.onReceivedHttpError(view, request, errorResponse)

                if (!privacySettings.isCloudflareBypassEnabled) return

                if (errorResponse?.statusCode == 403) {
                    val cfHeader = errorResponse.responseHeaders?.get("cf-mitigated")
                    if (cfHeader == "challenge") {
                        android.util.Log.d("CloudflareBypass", "Challenge detected: ${request?.url}")
                    }
                }
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                btnPlay.visibility = View.GONE
                currentVideoUrl = null
                currentVideoReferer = null

                // 注入全局脚本（弹窗拦截、fetch拦截、反调试、隐私沙箱禁用）
                view?.evaluateJavascript("javascript:" + ScriptletEngine.getPageStartScript()
                    .replace("\n", "\\n").replace("'", "\\'"), null)

                // Show progress bar and start timeout
                if (!isOnLandingPage()) {
                    progressBar.visibility = View.VISIBLE
                    progressBar.progress = 0
                    startLoadTimeout()
                }
            }

            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                super.doUpdateVisitedHistory(view, url, isReload)
                // 頁面歷史更新時（包含 goBack），重設恢復目標 URL
                // pendingScrollRestoreUrl 在 onBackPressed 中已設定好，這裡不需要額外處理
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)

                // Hide progress bar and cancel timeout
                progressBar.visibility = View.GONE
                cancelLoadTimeout()

                // 注入 JS 过滤器（反广告检测 + Cookie 欺骗）
                url?.let { currentUrl ->
                    val domain = Uri.parse(currentUrl).host ?: ""

                    // 1. 通用反广告检测绕过（所有站点生效）
                    view?.evaluateJavascript(ScriptletEngine.getAntiAdblockScript(), null)

                    // 2. 域名特定覆盖（最小化，仅已知站点）
                    val domainScript = ScriptletEngine.getDomainScriptlets(domain)
                    if (domainScript.isNotBlank()) {
                        view?.evaluateJavascript(domainScript, null)
                    }

                    // 3. 注入解析后的 scriptlet 规则（来自 uBO 规则解析）
                    val parsedScriptlets = ScriptletEngine.generateScriptlets(
                        adFilterRules.getScriptletRules(), domain
                    )
                    if (parsedScriptlets.isNotBlank()) {
                        view?.evaluateJavascript(parsedScriptlets, null)
                    }

                    // 4. 注入 AdFilterRules 中注册的 JS 过滤器
                    val jsFilters = adFilterRules.getJsFilters(domain)
                    jsFilters.forEach { filter ->
                        val jsCode = when (filter.type) {
                            "set-constant" -> {
                                if (filter.value == "noopFunc") {
                                    "(${filter.target}=function(){});"
                                } else {
                                    "Object.defineProperty(window,'${filter.target}',{value:${filter.value},writable:false,configurable:false});"
                                }
                            }
                            "trusted-set-cookie" ->
                                "document.cookie='${filter.target}=${filter.value};path=/;max-age=31536000';"
                            else -> null
                        }
                        jsCode?.let { view?.evaluateJavascript(it, null) }
                    }

                    // 4. 注入 Cosmetic Filter CSS
                    val cosmeticScript = CosmeticFilter.getCssInjectionScript(domain)
                    if (cosmeticScript.isNotBlank()) {
                        view?.evaluateJavascript(cosmeticScript, null)
                    }

                    // 4.5 注入 EasyList Element Hiding CSS（通用规则 + 域名特定规则）
                    val elementHideCss = adFilterRules.getElementHideCssScript(domain)
                    if (elementHideCss.isNotBlank()) {
                        view?.evaluateJavascript(elementHideCss, null)
                    }

                    // 5. CSS 广告元素隐藏（display:none，不用 remove）
                    view?.evaluateJavascript(ScriptletEngine.getAdElementHider(), null)

                    // 6. fetch/XHR 广告响应过滤
                    view?.evaluateJavascript(ScriptletEngine.getFetchXhrFilter(), null)
                }

                // 利用網頁自身的 sessionStorage，以「自己的 URL」作為 key 恢復滾動位置
                // shouldOverrideUrlLoading 離開時已儲存；onBackPressed 返回時同樣儲存
                val restoreScript = """
                    (function() {
                        var key = 'scrollPos__' + window.location.href;
                        var savedY = sessionStorage.getItem(key);
                        if (savedY) {
                            var sy = parseInt(savedY, 10);
                            if (sy > 0) {
                                setTimeout(function() {
                                    window.scrollTo(0, sy);
                                    document.documentElement.scrollTop = sy;
                                    document.body.scrollTop = sy;
                                    sessionStorage.removeItem(key);
                                }, 300);
                            } else {
                                sessionStorage.removeItem(key);
                            }
                        }
                    })();
                """.trimIndent()
                view?.evaluateJavascript(restoreScript, null)
                
                // Do NOT reset btnPlay or currentVideoUrl here, as video might have been found during load
                
                // Inject JS to remove specific ad elements
                @Suppress("UNUSED_VARIABLE")
                val removeAdsJs = """
                    (function() {
                        function removeAds() {
                            // Remove iframes with ID starting with 'container-'
                            var iframes = document.querySelectorAll('iframe[id^="container-"]');
                            iframes.forEach(function(iframe) {
                                iframe.remove();
                            });
                            
                            // Remove elements with high z-index and fixed position (common for overlays)
                            var allElements = document.getElementsByTagName('*');
                            for (var i = 0; i < allElements.length; i++) {
                                var el = allElements[i];
                                var style = window.getComputedStyle(el);
                                if (style.position === 'fixed' && style.zIndex > 2000000000) {
                                    el.style.display = 'none';
                                    el.remove();
                                }
                            }

                            // Rou.Video specific ad removal - Enhanced
                            var rmpAds = document.querySelectorAll('.rmp-ad-container, .rootContent--OjJEv');
                            rmpAds.forEach(function(ad) { ad.remove(); });
                            
                            // PRIORITY: Remove ALL tscprts.com related elements (all sites)
                            var tscprtsElements = document.querySelectorAll('a[href*="tscprts.com"], a[href*="go.tscprts.com"]');
                            tscprtsElements.forEach(function(link) {
                                // Remove up to 3 levels of parent divs to ensure complete removal
                                var parent = link.parentElement;
                                for (var i = 0; i < 3 && parent; i++) {
                                    var nextParent = parent.parentElement;
                                    parent.remove();
                                    parent = nextParent;
                                }
                            });
                            
                            // Remove bottom-right floating ads by class patterns
                            var bottomRightAds = document.querySelectorAll('[class*="bottomRight"], [class*="slideAnimation"], [class*="root--"]');
                            bottomRightAds.forEach(function(ad) {
                                // Additional check: if it contains tscprts or doppiocdn links
                                if (ad.innerHTML && (ad.innerHTML.includes('tscprts.com') || ad.innerHTML.includes('doppiocdn.com'))) {
                                    ad.remove();
                                }
                            });
                            
                            // ENHANCED: Remove close-button ads and their parent containers (up to 2 levels)
                            // BUT exclude video player controls (vjs-*)
                            var closeButtons = document.querySelectorAll('[class*="close-button"]');
                            closeButtons.forEach(function(btn) {
                                // Skip if it's a video player control button
                                if (btn.className.includes('vjs-') || btn.className.includes('video-js')) {
                                    return; // Skip video player buttons
                                }
                                
                                // First, try to auto-click the button
                                try { btn.click(); } catch(e) {}
                                
                                // Remove up to 2 levels of parent to get the entire ad container
                                var parent = btn.parentElement;
                                if (parent) {
                                    var grandParent = parent.parentElement;
                                    if (grandParent) {
                                        grandParent.remove();
                                    } else {
                                        parent.remove();
                                    }
                                } else {
                                    btn.remove();
                                }
                            });
                            
                            // Remove dialog overlays by ID pattern or role
                            var dialogs = document.querySelectorAll('div[role="dialog"], div[id^="radix-"]');
                            dialogs.forEach(function(dialog) { dialog.remove(); });
                            
                            // Remove specific ad links/images - Enhanced with Safeguard
                            function isSafeToRemove(element) {
                                if (!element) return false;
                                if (element.id === 'player') return false;
                                if (element.classList.contains('video-js')) return false;
                                if (element.classList.contains('vjs-tech')) return false;
                                if (element.querySelector && (element.querySelector('#player') || element.querySelector('.video-js'))) return false;
                                return true;
                            }

                            var adLinks = document.querySelectorAll('a[href*="ra12.xyz"], a[href*="tscprts.com"], a[href*="doppiocdn.com"], img[src*="doppiocdn.com"]');
                            adLinks.forEach(function(link) { 
                                var parent = link.closest('div');
                                if (parent && isSafeToRemove(parent)) {
                                    parent.remove();
                                } else {
                                    link.remove(); 
                                }
                            });

                            // Generic removal for bottom floating ads (all sites now, not just rou.video)
                            var allDivs = document.getElementsByTagName('div');
                            for (var i = 0; i < allDivs.length; i++) {
                                var el = allDivs[i];
                                var style = window.getComputedStyle(el);
                                // Check for fixed position at bottom or bottom-right
                                if (style.position === 'fixed' && (style.bottom === '0px' || parseInt(style.bottom) < 100)) {
                                    // Check if contains ad indicators
                                    if (el.innerText.includes('Close') || el.innerHTML.includes('ra12.xyz') || 
                                        el.innerHTML.includes('tscprts') || el.innerHTML.includes('go.tscprts') ||
                                        el.innerHTML.includes('blob:') || style.zIndex > 100) {
                                        el.style.display = 'none';
                                        el.remove();
                                    }
                                }
                            }


                            // Auto-click "Close ad" buttons (but skip video player controls)
                            var buttons = document.querySelectorAll('button, div[role="button"], a');
                            buttons.forEach(function(btn) {
                                // Skip video player controls
                                if (btn.className.includes('vjs-') || btn.className.includes('video-js')) {
                                    return;
                                }
                                
                                var text = btn.innerText || "";
                                if (text.toLowerCase().includes("close ad") || (text.toLowerCase() === "close" && !btn.closest('.video-js')) || text.includes("×")) {
                                    // Check if it looks like an ad close button (heuristic)
                                    if (btn.className.includes("close") || btn.className.includes("dismiss") || 
                                        (btn.style.position === 'absolute' && btn.style.top)) {
                                        try { btn.click(); } catch(e) {}
                                        btn.remove(); // Remove it after clicking just in case
                                    }
                                }
                            });
                        }
                        
                        // Run immediately and periodically
                        removeAds();
                        setInterval(removeAds, 1000);
                    })();
                """.trimIndent()
                // view?.evaluateJavascript(removeAdsJs, null) // DISABLED FOR TESTING

                // New MISSAV Ad Blocking Logic
                if (url?.contains("missav") == true || url?.contains("jable") == true || url?.contains("rou.video") == true || url?.contains("rouva") == true || url?.contains("avjoy.me") == true) {
                    val missavAdBlockJs = """
                        (function() {
                            'use strict';

                            // 1. 攔截彈窗與惡意跳轉邏輯
                            var websites = ["missav.com/pop", "tsyndicate.com/api", "${domainConfig.getMissAvDomain()}/pop"];
                            var url = window.location.href;
                            for (var i = 0; i < websites.length; i++) {
                                // 簡單的正則匹配
                                if (url.indexOf(websites[i]) !== -1) {
                                    // 在WebView中，window.close() 可能無效，通常需要透過 about:blank 停止加載
                                    window.location.href = "about:blank";
                                    return; // 停止後續執行
                                }
                            }

                            // 2. 移除廣告 DOM 元素的函數
                            function cleanAds() {
                                // 移除特定的廣告區塊 (class 僅為 mx-auto 的元素)
                                try {
                                    const mxauto = document.querySelectorAll('.mx-auto:not([class*=" "])');
                                    mxauto.forEach(node => node.remove());
                                } catch (e) {}

                                // 移除特定的 root + bottomRight 廣告區塊 (動態識別)
                                try {
                                    // 1. 注入 CSS 強制隱藏
                                    var style = document.createElement('style');
                                    style.innerHTML = `
                                        div[class*="root"][class*="bottomRight"],
                                        div[role="dialog"]:not([data-slot="sheet-content"]),
                                        div[id^="radix-"]:not([data-slot="sheet-content"]),
                                        div[id^="__clb-spot_"],
                                        div[id^="ts_ad_"],
                                        div[id^="exo-native-widget"],
                                        .exo-native-widget,
                                        div[data-banner-id],
                                        .rmp-ad-container,
                                        script[src*="magsrv.com"],
                                        ins[data-zoneid],
                                        ins {
                                            display: none !important;
                                        }
                                    `;
                                    document.head.appendChild(style);

                                    // 2. 使用 MutationObserver 監聽並移除
                                    var observer = new MutationObserver(function(mutations) {
                                        // Safeguard function
                                        function isSafeToRemove(element) {
                                            if (!element || !element.tagName) return false;
                                            var tag = element.tagName.toLowerCase();
                                            if (tag === 'html' || tag === 'body' || tag === 'video' || tag === 'iframe') return false;
                                            if (element.id === 'player') return false;
                                            if (element.classList && (element.classList.contains('video-js') || element.classList.contains('vjs-tech'))) return false;
                                            if (element.querySelector && (element.querySelector('video') || element.querySelector('iframe') || element.querySelector('#player') || element.querySelector('.video-js'))) return false;
                                            return true;
                                        }

                                        mutations.forEach(function(mutation) {
                                            mutation.addedNodes.forEach(function(node) {
                                                if (node.nodeType === 1) { // Element
                                                    // Check if node matches generic ad selectors
                                                    if (node.matches && (
                                                        node.matches('div[class*="root"][class*="bottomRight"]') ||
                                                        (node.matches('div[role="dialog"]') && node.getAttribute('data-slot') !== 'sheet-content') ||
                                                        (node.matches('div[id^="radix-"]') && node.getAttribute('data-slot') !== 'sheet-content') ||
                                                        node.matches('div[id^="__clb-spot_"]') ||
                                                        node.matches('div[id^="ts_ad_"]') ||
                                                        node.matches('div[data-banner-id]') ||
                                                        node.matches('.rmp-ad-container') ||
                                                        node.matches('ins')
                                                    )) {
                                                        if (isSafeToRemove(node)) {
                                                            node.remove();
                                                        }
                                                    }
                                                    
                                                    // Check for Rou.Video specific cards (已停用以避免誤刪播放器)
                                                    // if (node.matches && node.matches('div[data-slot="card"]')) {
                                                    //     if (isSafeToRemove(node) && (node.innerText.includes('通告') || node.innerHTML.includes('ra12.xyz'))) {
                                                    //         node.remove();
                                                    //     }
                                                    // }

                                                    // Check for dynamic ad links
                                                    if (node.matches && (node.matches('a[href*="ra12.xyz"]') || node.matches('a[href*="rdz1.xyz"]'))) {
                                                        // 安全容器查找：最多往上 3 層，且確保容器不含影片內容連結
                                                        var safeContainer = null;
                                                        var cur = node.parentElement;
                                                        for (var _i = 0; _i < 3 && cur; _i++) {
                                                            // 若容器含有影片內容連結，停止往上找（避免誤刪影片列表）
                                                            if (cur.querySelector && cur.querySelector('a[href^="/v/"]')) break;
                                                            safeContainer = cur;
                                                            cur = cur.parentElement;
                                                        }
                                                        if (safeContainer && isSafeToRemove(safeContainer)) {
                                                            safeContainer.remove();
                                                        } else {
                                                            node.remove();
                                                        }
                                                    }

                                                    // Check children of added node for ad links
                                                    var dynamicAdLinks = node.querySelectorAll('a[href*="ra12.xyz"], a[href*="rdz1.xyz"]');
                                                    dynamicAdLinks.forEach(link => {
                                                        // 安全容器查找：最多往上 3 層，且確保容器不含影片內容連結
                                                        var safeContainer = null;
                                                        var cur = link.parentElement;
                                                        for (var _j = 0; _j < 3 && cur; _j++) {
                                                            if (cur.querySelector && cur.querySelector('a[href^="/v/"]')) break;
                                                            safeContainer = cur;
                                                            cur = cur.parentElement;
                                                        }
                                                        if (safeContainer && isSafeToRemove(safeContainer)) {
                                                            safeContainer.remove();
                                                        } else {
                                                            link.remove();
                                                        }
                                                    });
                                                    
                                                    // Also check children
                                                    var ads = node.querySelectorAll('div[class*="root"][class*="bottomRight"], div[role="dialog"]:not([data-slot="sheet-content"]), div[id^="radix-"]:not([data-slot="sheet-content"]), div[id^="__clb-spot_"], div[id^="ts_ad_"], div[data-banner-id], .rmp-ad-container, ins');
                                                    ads.forEach(ad => {
                                                        if (isSafeToRemove(ad)) {
                                                            ad.remove();
                                                        }
                                                    });
                                                }
                                            });
                                        });
                                    });
                                    observer.observe(document.body, { childList: true, subtree: true });

                                    // 3. 初始移除 (播放器可能還沒載入，避免誤刪)
                                    function cleanAdsInitial() {
                                        // Safeguard
                                        function isSafeToRemove(element) {
                                            if (!element) return false;
                                            if (element.id === 'player') return false;
                                            if (element.classList.contains('video-js')) return false;
                                            if (element.classList.contains('vjs-tech')) return false;
                                            if (element.querySelector && (element.querySelector('#player') || element.querySelector('.video-js'))) return false;
                                            return true;
                                        }

                                        // Generic selectors (不包括 card，因為播放器可能還沒載入)
                                        const selectors = [
                                            'div[class*="root"][class*="bottomRight"]',
                                            'div[role="dialog"]:not([data-slot="sheet-content"])',
                                            'div[id^="radix-"]:not([data-slot="sheet-content"])',
                                            'div[id^="__clb-spot_"]',
                                            'div[id^="ts_ad_"]',
                                            'div[id^="exo-native-widget"]',
                                            '.exo-native-widget',
                                            'div[data-banner-id]',
                                            '.rmp-ad-container',
                                            'ins[data-zoneid]',
                                            'ins'
                                        ];
                                        
                                        selectors.forEach(selector => {
                                            document.querySelectorAll(selector).forEach(node => {
                                                if (isSafeToRemove(node)) {
                                                    node.style.display = 'none';
                                                    node.remove();
                                                }
                                            });
                                        });
                                        
                                        // Remove magsrv.com ad scripts (jable.tv)
                                        document.querySelectorAll('script[src*="magsrv.com"]').forEach(script => {
                                            script.remove();
                                        });
                                        
                                        // Generic ad links and their containers
                                        // 注意：不可使用 closest('.grid')，會誤刪整個影片列表！
                                        // 改為最多往上 3 層找容器，且確保容器不含影片內容連結
                                        document.querySelectorAll('a[href*="ra12.xyz"], a[href*="rdz1.xyz"]').forEach(link => {
                                            var safeContainer = null;
                                            var cur = link.parentElement;
                                            for (var _k = 0; _k < 3 && cur; _k++) {
                                                if (cur.querySelector && cur.querySelector('a[href^="/v/"]')) break;
                                                safeContainer = cur;
                                                cur = cur.parentElement;
                                            }
                                            if (safeContainer && isSafeToRemove(safeContainer)) {
                                                safeContainer.remove();
                                            } else {
                                                link.remove();
                                            }
                                        });
                                    }
                                    
                                    
                                    cleanAdsInitial();
                                } catch (e) {}

                                // 嘗試點擊各種類型的關閉按鈕
                                // 註：這些 Class 名稱可能是混淆過的，網站更新後可能失效
                                const closeSelectors = [
                                    ".close-button--wsOv0",
                                    ".absolute.top-1.right-1.p-0.5.bg-black.rounded-lg.opacity-70"
                                ];

                                closeSelectors.forEach(selector => {
                                    const btns = document.querySelectorAll(selector);
                                    btns.forEach(btn => btn.click());
                                });
                            }

                            // 執行邏輯
                            cleanAds();

                            // 延遲執行 (應對動態加載的廣告)
                            setTimeout(cleanAds, 1000);
                            setTimeout(cleanAds, 2500);
                            setTimeout(cleanAds, 5000);

                        })();
                    """.trimIndent()
                    view?.evaluateJavascript(missavAdBlockJs, null)
                }

                // Cloudflare Cookie 提取（仅当功能开启）
                if (privacySettings.isCloudflareBypassEnabled) {
                    url?.let {
                        val cookies = android.webkit.CookieManager.getInstance().getCookie(it) ?: ""
                        if (cookies.contains("cf_clearance")) {
                            val host = android.net.Uri.parse(it).host ?: return@let
                            val cfValue = extractCfClearance(cookies)
                            if (cfValue.isNotEmpty()) {
                                privacySettings.saveCloudflareCookie(host, cfValue)
                                android.util.Log.d("CloudflareBypass", "Cookie saved for $host")
                            }
                        }
                    }
                }

                checkForVideo()
            }
        }
    }

    private fun extractCfClearance(cookies: String): String {
        return cookies.split(";")
            .map { it.trim() }
            .find { it.startsWith("cf_clearance=") }
            ?.substringAfter("=") ?: ""
    }

    private fun isAd(url: String): Boolean {
        val adKeywords = listOf(
            // General ad networks
            "googleads", "doubleclick", "adservice", "googlesyndication",
            "adnxs", "advertising", "adsystem", "adtech", "adform",
            
            // Adult ad networks (common on these sites)
            "popunder", "juicyads", "exoclick", "trafficjunky", 
            "plugrush", "adsterra", "popcash", "propeller", "popads",
            "tsyndicate", "realsrv", "hilltopads", "adcash",
            
            // Tracking & Analytics that might show popups
            "pubmatic", "outbrain", "taboola", "smartadserver",
            "criteo", "bidvertiser", "vibrantmedia",
            
            // Keywords in URL paths
            "/ads/", "/ad/", "/banner/", "/popup/", "/popunder/",
            "banner", "sponsor", "tracking", "clicktrack",
            
            // Specific domains requested by user
            "myavlive.com", "snaptrckr.fun", "stripchat.com", 
            "adxadserv.com", "fluxtrck.site", "ra12.xyz"
        )
        
        // Check if URL contains any ad keyword
        return adKeywords.any { url.contains(it) }
    }

    private fun checkForVideo() {
        val url = webView.url ?: return
        
        // Only check on likely video pages to save resources
        // Jable: /videos/
        // MissAV: usually has UUID or just check all pages on missav domain
        
        // Inject JS to monitor video element for src changes and intercept network requests for rou.video
        if (url.contains("rou.video") || url.contains("rouva")) {
            val monitorJs = """
                (function() {
                    if (window.rouVideoMonitor) return; // Already monitoring
                    window.rouVideoMonitor = true;
                    
                    // 1. Monitor <video> tag (for older implementation)
                    var checkInterval = setInterval(function() {
                        var video = document.querySelector('video');
                        if (video && video.src && video.src.startsWith('http') && video.src.indexOf('.m3u8') !== -1) {
                            Android.onVideoFound(video.src);
                            clearInterval(checkInterval);
                        }
                    }, 1000); // Check every second
                    
                    // Stop checking after 30 seconds
                    setTimeout(function() { clearInterval(checkInterval); }, 30000);

                    // 2. Intercept Fetch API to sniff .m3u8 or index.jpg (new)
                    var originalFetch = window.fetch;
                    window.fetch = async function() {
                        var fetchUrl = arguments[0];
                        var urlStr = typeof fetchUrl === 'string' ? fetchUrl : (fetchUrl && fetchUrl.url ? fetchUrl.url : '');
                        if (urlStr.indexOf('.m3u8') !== -1) {
                            Android.onVideoFound(urlStr);
                        } else if (urlStr.indexOf('index.jpg') !== -1 && urlStr.indexOf('exp=') !== -1 && urlStr.indexOf('auth=') !== -1) {
                            Android.onVideoFound(urlStr.replace('index.jpg', 'index.m3u8'));
                        }
                        return originalFetch.apply(this, arguments);
                    };

                    // 3. Intercept XHR to sniff
                    var originalXhrOpen = XMLHttpRequest.prototype.open;
                    XMLHttpRequest.prototype.open = function(method, url) {
                        if (typeof url === 'string') {
                            if (url.indexOf('.m3u8') !== -1) {
                                Android.onVideoFound(url);
                            } else if (url.indexOf('index.jpg') !== -1 && url.indexOf('exp=') !== -1 && url.indexOf('auth=') !== -1) {
                                Android.onVideoFound(url.replace('index.jpg', 'index.m3u8'));
                            }
                        }
                        return originalXhrOpen.apply(this, arguments);
                    };
                })();
            """.trimIndent()
            webView.evaluateJavascript(monitorJs, null)
        }
        
        // Also parse HTML to extract URL instantly for all supported sites
        webView.evaluateJavascript("(function() { return document.documentElement.outerHTML; })();") { html ->
            // html is a JSON string, e.g. "\u003Chtml>..."
            // We need to unescape it.
            val rawHtml = unescapeJsString(html)
            
            var extractedUrl: String? = null
            
            if (url.contains("jable.tv")) {
                extractedUrl = VideoExtractor.extractJable(rawHtml)
            } else if (url.contains("missav")) {
                extractedUrl = VideoExtractor.extractMissAV(rawHtml)
            } else if (url.contains("rou.video") || url.contains("rouva")) {
                extractedUrl = VideoExtractor.extractRouVideo(rawHtml)
            } else if (url.contains("avjoy.me")) {
                extractedUrl = VideoExtractor.extractAvJoy(rawHtml)
                if (extractedUrl != null) {
                    currentVideoReferer = "https://avjoy.me/"
                }
            } else if (url.contains("hentaihaven")) {
                extractedUrl = VideoExtractor.extractHentaiHaven(rawHtml)
            } else if (url.contains("hanime.tv")) {
                extractedUrl = VideoExtractor.extractHanime(rawHtml)
            } else if (url.contains("watchhentai")) {
                extractedUrl = VideoExtractor.extractWatchHentai(rawHtml)
            } else if (url.contains("oppai.stream")) {
                extractedUrl = VideoExtractor.extractOppai(rawHtml)
            } else if (url.contains("muchohentai")) {
                extractedUrl = VideoExtractor.extractMuchoHentai(rawHtml)
            } else if (url.contains("hentaimama")) {
                extractedUrl = VideoExtractor.extractHentaiMama(rawHtml)
            } else if (url.contains("hentaifreak")) {
                extractedUrl = VideoExtractor.extractHentaiFreak(rawHtml)
            } else if (url.contains("xanimeporn")) {
                extractedUrl = VideoExtractor.extractXanimeporn(rawHtml)
            } else if (url.contains("kisshentai")) {
                extractedUrl = VideoExtractor.extractKissHentai(rawHtml)
            } else if (url.contains("hentaicity")) {
                extractedUrl = VideoExtractor.extractHentaiCity(rawHtml)
            } else if (url.contains("hentaiuniverse")) {
                extractedUrl = VideoExtractor.extractHentaiUniverse(rawHtml)
            } else if (url.contains("animeidhentai")) {
                extractedUrl = VideoExtractor.extractAnimeIDHentai(rawHtml)
            } else if (url.contains("ohentai")) {
                extractedUrl = VideoExtractor.extractOhentai(rawHtml)
            }

            if (extractedUrl != null) {
                currentVideoUrl = extractedUrl
                btnPlay.visibility = View.VISIBLE
                // Toast.makeText(this, R.string.video_found, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun unescapeJsString(jsString: String): String {
        // Remove surrounding quotes
        var s = jsString
        if (s.startsWith("\"") && s.endsWith("\"")) {
            s = s.substring(1, s.length - 1)
        }
        try {
            return org.json.JSONTokener(jsString).nextValue().toString()
        } catch (e: Exception) {
            return s.replace("\\u003C", "<").replace("\\\"", "\"").replace("\\\\", "\\")
        }
    }

    private fun setupPlayButton() {
        btnPlay.setScaleAnimation()
        btnPlay.setOnClickListener {
            currentVideoUrl?.let { url ->
                playVideo(url)
            }
        }

        btnPlay.setOnLongClickListener {
            currentVideoUrl?.let { url ->
                showVideoMenu(url)
            }
            true
        }
    }

    private fun showVideoMenu(url: String) {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("视频操作")
            .setItems(arrayOf("播放", "复制地址")) { _, which ->
                when (which) {
                    0 -> playVideo(url)
                    1 -> {
                        val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("视频地址", url))
                        Toast.makeText(this, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .show()
    }

    private fun playVideo(url: String) {
        try {
            val referer = currentVideoReferer
            val playUrl: String

            if (referer != null && videoProxyServer != null) {
                // Use local proxy for CDN-protected sites (e.g. avjoy.me)
                // Proxy will attach Referer, Cookie, Sec-Fetch-* headers automatically
                val cookieManager = android.webkit.CookieManager.getInstance()
                val cookies = cookieManager.getCookie(referer) ?: ""
                playUrl = videoProxyServer!!.buildProxyUrl(url, referer, cookies)
            } else {
                playUrl = url
            }

            val mimeType = if (url.contains(".mp4")) "video/mp4" else "video/*"
            val intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(Uri.parse(playUrl), mimeType)

            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
            } else {
                intent.setDataAndType(Uri.parse(playUrl), "application/x-mpegURL")
                if (intent.resolveActivity(packageManager) != null) {
                    startActivity(intent)
                } else {
                    Toast.makeText(this, R.string.error_no_player, Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "错误：${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    
    private fun showExitConfirmationDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("退出应用")
            .setMessage("确定要退出吗？")
            .setPositiveButton("确定") { _, _ ->
                finish()
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    
    private fun isOnLandingPage(): Boolean {
        val url = webView.url
        return url == null || url == "about:blank" || url.startsWith("data:")
    }
    
    private fun loadLandingPage() {
        val landingHtml = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <meta name="color-scheme" content="light dark">
                <style>
                    :root {
                        --bg: #FBFDF8;
                        --text: #191C1A;
                        --text-secondary: #414941;
                        --surface: #FFFFFF;
                        --surface-variant: #DDE5DB;
                        --primary: #5DAC81;
                        --on-primary: #FFFFFF;
                        --primary-hover: #4A8B6A;
                        --outline: #C1C9BF;
                        --input-bg: #FFFFFF;
                        --input-border: #5DAC81;
                        --btn-bg: #E8F0EB;
                        --btn-text: #191C1A;
                        --btn-hover-bg: #5DAC81;
                        --btn-hover-text: #FFFFFF;
                        --chip-bg: #DDE5DB;
                        --card-shadow: 0 1px 3px rgba(0,0,0,0.08);
                        --card-radius: 16px;
                        --btn-radius: 10px;
                        --section-gap: 24px;
                        --grid-gap: 10px;
                    }
                    @media (prefers-color-scheme: dark) {
                        :root {
                            --bg: #191C1A;
                            --text: #E1E3DE;
                            --text-secondary: #C1C9BF;
                            --surface: #1C1F1C;
                            --surface-variant: #414941;
                            --primary: #7EC99D;
                            --on-primary: #003822;
                            --primary-hover: #5DAC81;
                            --outline: #414941;
                            --input-bg: #1C1F1C;
                            --input-border: #7EC99D;
                            --btn-bg: #2A332D;
                            --btn-text: #E1E3DE;
                            --btn-hover-bg: #7EC99D;
                            --btn-hover-text: #003822;
                            --chip-bg: #414941;
                            --card-shadow: 0 1px 3px rgba(0,0,0,0.24);
                        }
                    }
                    * { box-sizing: border-box; }
                    body {
                        background-color: var(--bg);
                        color: var(--text);
                        font-family: 'Roboto', system-ui, sans-serif;
                        margin: 0;
                        padding: 0;
                        min-height: 100vh;
                    }
                    .app-container {
                        max-width: 560px;
                        margin: 0 auto;
                        padding: 20px 16px 100px 16px;
                    }
                    .app-title {
                        text-align: center;
                        font-size: 24px;
                        font-weight: 700;
                        margin: 12px 0 20px 0;
                        color: var(--text);
                        letter-spacing: 0.02em;
                    }
                    .search-container {
                        margin-bottom: 24px;
                        position: relative;
                    }
                    .search-box {
                        width: 100%;
                        padding: 14px 16px;
                        font-size: 15px;
                        border: 2px solid var(--input-border);
                        border-radius: 14px;
                        background-color: var(--input-bg);
                        color: var(--text);
                        transition: border-color 0.2s;
                    }
                    .search-box:focus {
                        outline: none;
                        border-color: var(--primary);
                    }
                    .search-box::placeholder { color: var(--text-secondary); }
                    .search-results {
                        display: none;
                        margin-top: 6px;
                        background: var(--surface);
                        border-radius: 12px;
                        box-shadow: var(--card-shadow);
                        overflow: hidden;
                    }
                    .search-results.show { display: block; }
                    .search-results a {
                        display: block;
                        padding: 12px 16px;
                        color: var(--text);
                        text-decoration: none;
                        font-size: 14px;
                        border-bottom: 1px solid var(--outline);
                    }
                    .search-results a:last-child { border-bottom: none; }
                    .search-results a:active { background: var(--surface-variant); }

                    .section-card {
                        background: var(--surface);
                        border-radius: var(--card-radius);
                        box-shadow: var(--card-shadow);
                        padding: 16px;
                        margin-bottom: var(--section-gap);
                    }
                    .section-title {
                        display: flex;
                        align-items: center;
                        gap: 8px;
                        font-size: 13px;
                        font-weight: 600;
                        color: var(--primary);
                        text-transform: uppercase;
                        letter-spacing: 0.06em;
                        margin: 0 0 14px 4px;
                    }
                    .section-title::before {
                        content: '';
                        width: 4px;
                        height: 16px;
                        background: var(--primary);
                        border-radius: 2px;
                    }
                    .site-grid { display: grid; gap: var(--grid-gap); }
                    .grid-2 { grid-template-columns: 1fr 1fr; }
                    .grid-3 { grid-template-columns: 1fr 1fr 1fr; }

                    .site-btn {
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        gap: 6px;
                        width: 100%;
                        min-height: 48px;
                        padding: 10px 8px;
                        background: var(--btn-bg);
                        color: var(--btn-text);
                        text-decoration: none;
                        border-radius: var(--btn-radius);
                        font-size: 14px;
                        font-weight: 500;
                        text-align: center;
                        transition: all 0.15s;
                        border: 1px solid transparent;
                        word-break: break-word;
                        hyphens: auto;
                    }
                    .site-btn:active {
                        background: var(--btn-hover-bg);
                        color: var(--btn-hover-text);
                        border-color: var(--primary);
                    }
                    .site-btn .dot {
                        width: 6px;
                        height: 6px;
                        border-radius: 50%;
                        background: var(--primary);
                        flex-shrink: 0;
                    }

                    .help-wrap {
                        display: flex;
                        justify-content: center;
                        margin-top: 8px;
                    }
                    .help-button {
                        width: 28px;
                        height: 28px;
                        border-radius: 50%;
                        background: var(--chip-bg);
                        color: var(--text-secondary);
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        font-weight: 600;
                        font-size: 12px;
                        border: 2px solid var(--outline);
                        cursor: pointer;
                    }
                </style>
            </head>
            <body>
                <div class="app-container">
                    <div class="app-title">JAV Browser</div>

                    <div class="search-container">
                        <input type="text" id="searchInput" class="search-box" placeholder="搜索番号..." />
                        <div id="searchResults" class="search-results">
                            <a href="#" id="searchMissAV"></a>
                            <a href="#" id="searchJable"></a>
                            <a href="#" id="searchAvJoy"></a>
                        </div>
                    </div>

                    <div class="section-card">
                        <div class="section-title">JAV 视频站点</div>
                        <div class="site-grid grid-2">
                            <a class="site-btn" href="javascript:Android.navigateToUrl('${domainConfig.getMissAvBaseUrl()}')"><span class="dot"></span>MissAV</a>
                            <a class="site-btn" href="javascript:Android.navigateToUrl('https://7mmtv.sx/')"><span class="dot"></span>7mmtv</a>
                            <a class="site-btn" href="javascript:Android.navigateToUrl('https://hohoj.tv/')"><span class="dot"></span>HoHoJ.tv</a>
                            <a class="site-btn" href="javascript:Android.navigateToUrl('https://javtsunami.com/')"><span class="dot"></span>JAVTsunami</a>
                            <a class="site-btn" href="javascript:Android.navigateToUrl('https://${domainConfig.getJableDomain()}/')"><span class="dot"></span>Jable</a>
                            <a class="site-btn" href="javascript:Android.navigateToUrl('https://${domainConfig.getRouVideoDomain()}/home')"><span class="dot"></span>Rou.Video</a>
                            <a class="site-btn" href="javascript:Android.navigateToUrl('https://supjav.com/')"><span class="dot"></span>SupJAV</a>
                            <a class="site-btn" href="javascript:Android.navigateToUrl('https://javgg.net/')"><span class="dot"></span>JavGG</a>
                            <a class="site-btn" href="javascript:Android.navigateToUrl('https://njav.tv/')"><span class="dot"></span>Njav.tv</a>
                        </div>
                    </div>

                    <div class="section-card">
                        <div class="section-title">Hentai 动画站点</div>
                        <div class="site-grid grid-3">
                            <a class="site-btn" href="javascript:Android.navigateToUrl('https://hanime.tv')">Hanime.tv</a>
                            <a class="site-btn" href="javascript:Android.navigateToUrl('https://hentaihaven.xxx')">HentaiHaven</a>
                            <a class="site-btn" href="javascript:Android.navigateToUrl('https://hentaifreak.org')">HentaiFreak</a>
                            <a class="site-btn" href="javascript:Android.navigateToUrl('https://oppai.stream')">Oppai.stream</a>
                            <a class="site-btn" href="javascript:Android.navigateToUrl('https://watchhentai.net')">WatchHentai</a>
                            <a class="site-btn" href="javascript:Android.navigateToUrl('https://hentaimama.io')">HentaiMama</a>
                            <a class="site-btn" href="javascript:Android.navigateToUrl('https://hentaicity.com')">HentaiCity</a>
                            <a class="site-btn" href="javascript:Android.navigateToUrl('https://hentaiuniverse.net')">HentaiUniverse</a>
                            <a class="site-btn" href="javascript:Android.navigateToUrl('https://animeidhentai.com')">AnimeIDHentai</a>
                            <a class="site-btn" href="javascript:Android.navigateToUrl('https://ohentai.org')">Ohentai</a>
                            <a class="site-btn" href="javascript:Android.navigateToUrl('https://muchohentai.com')">MuchoHentai</a>
                        </div>
                    </div>

                    <div class="help-wrap">
                        <div class="help-button" onclick="showHelp()">?</div>
                    </div>
                </div>

                <script>
                    const searchInput = document.getElementById('searchInput');
                    const searchResults = document.getElementById('searchResults');
                    const searchMissAV = document.getElementById('searchMissAV');
                    const searchJable = document.getElementById('searchJable');
                    const searchAvJoy = document.getElementById('searchAvJoy');

                    searchInput.addEventListener('input', function() {
                        const keyword = this.value.trim();
                        if (keyword.length > 0) {
                            searchResults.classList.add('show');
                            searchMissAV.textContent = '在 MissAV 搜索: ' + keyword;
                            searchJable.textContent = '在 Jable.TV 搜索: ' + keyword;
                            searchAvJoy.textContent = '在 AvJoy 搜索: ' + keyword;
                            searchMissAV.href = 'https://${domainConfig.getMissAvDomain()}/search/' + encodeURIComponent(keyword);
                            searchJable.href = 'https://jable.tv/search/' + encodeURIComponent(keyword) + '/';
                            searchAvJoy.href = 'https://${domainConfig.getAvJoyDomain()}/search/videos/' + encodeURIComponent(keyword);
                        } else {
                            searchResults.classList.remove('show');
                        }
                    });

                    searchInput.addEventListener('keypress', function(e) {
                        if (e.key === 'Enter' && this.value.trim().length > 0) {
                            Android.navigateToUrl(searchMissAV.href);
                        }
                    });

                    function showHelp() {
                        Android.showHelpDialog();
                    }
                </script>
            </body>
            </html>
        """.trimIndent()
        webView.loadDataWithBaseURL("https://javbrowser.app/", landingHtml, "text/html", "utf-8", null)
    }
    
    private fun downloadAndInstallApk(url: String) {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.setData(android.net.Uri.parse(url))
        startActivity(intent)
    }
    

    private fun setupBottomNavigation() {
        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    loadLandingPage()
                    true
                }
                R.id.nav_favorite -> {
                    val intent = Intent(this, FavoritesActivity::class.java)
                    favoritesLauncher.launch(intent)
                    true
                }
                R.id.nav_settings -> {
                    val intent = Intent(this, SettingsActivity::class.java)
                    startActivity(intent)
                    true
                }
                else -> false
            }
        }
    }


    private fun startLoadTimeout() {
        // Cancel any existing timeout
        cancelLoadTimeout()
        
        // Initialize handler if needed
        if (timeoutHandler == null) {
            timeoutHandler = android.os.Handler(android.os.Looper.getMainLooper())
        }
        
        // Create and schedule timeout runnable
        timeoutRunnable = Runnable {
            android.util.Log.w("JAVBrowser", "[TIMEOUT] Page load timeout after ${TIMEOUT_DURATION}ms")
            
            runOnUiThread {
                progressBar.visibility = View.GONE
                
                MaterialAlertDialogBuilder(this)
                    .setTitle("连接超时")
                    .setMessage("页面加载时间过长。\n\n建议：\n• 检查网络连接\n• 切换 WiFi / 移动数据\n• 点击重试")
                    .setPositiveButton("重试") { _, _ ->
                        webView.reload()
                    }
                    .setNegativeButton("返回首页") { _, _ ->
                        loadLandingPage()
                    }
                    .setNeutralButton("取消", null)
                    .show()
            }
        }
        
        timeoutHandler?.postDelayed(timeoutRunnable!!, TIMEOUT_DURATION)
    }
    
    private fun cancelLoadTimeout() {
        timeoutRunnable?.let {
            timeoutHandler?.removeCallbacks(it)
            timeoutRunnable = null
        }
    }
    
    private fun getErrorPageHtml(errorDescription: String, failingUrl: String): String {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <meta name="color-scheme" content="light dark">
                <style>
                    :root {
                        --bg: #FBFDF8;
                        --text: #191C1A;
                        --text-secondary: #414941;
                        --surface: #FFFFFF;
                        --surface-variant: #DDE5DB;
                        --primary: #5DAC81;
                        --on-primary: #FFFFFF;
                        --primary-hover: #4A8B6A;
                        --error: #B3261E;
                        --outline: #C1C9BF;
                    }
                    @media (prefers-color-scheme: dark) {
                        :root {
                            --bg: #191C1A;
                            --text: #E1E3DE;
                            --text-secondary: #C1C9BF;
                            --surface: #1C1F1C;
                            --surface-variant: #414941;
                            --primary: #7EC99D;
                            --on-primary: #003822;
                            --primary-hover: #5DAC81;
                            --error: #F2B8B5;
                            --outline: #414941;
                        }
                    }
                    body {
                        background-color: var(--bg);
                        color: var(--text);
                        font-family: Arial, sans-serif;
                        text-align: center;
                        padding: 40px 20px;
                        margin: 0;
                    }
                    .error-icon {
                        font-size: 80px;
                        margin-bottom: 20px;
                    }
                    h1 {
                        color: var(--error);
                        margin-bottom: 15px;
                    }
                    p {
                        color: var(--text-secondary);
                        line-height: 1.6;
                        margin-bottom: 10px;
                    }
                    .url {
                        background-color: var(--surface);
                        border: 1px solid var(--outline);
                        padding: 10px;
                        border-radius: 8px;
                        word-break: break-all;
                        margin: 20px 0;
                        font-size: 14px;
                        color: var(--text-secondary);
                    }
                    .button {
                        display: inline-block;
                        background-color: var(--primary);
                        color: var(--on-primary);
                        padding: 12px 30px;
                        margin: 10px 5px;
                        border-radius: 12px;
                        text-decoration: none;
                        font-weight: bold;
                        transition: background-color 0.2s;
                    }
                    .button:active {
                        background-color: var(--primary-hover);
                    }
                    .button-secondary {
                        background-color: var(--surface);
                        color: var(--text);
                        border: 1px solid var(--outline);
                    }
                    .suggestions {
                        text-align: left;
                        max-width: 400px;
                        margin: 30px auto;
                        background-color: var(--surface);
                        border: 1px solid var(--outline);
                        padding: 20px;
                        border-radius: 16px;
                    }
                    .suggestions h3 {
                        color: var(--primary);
                        margin-top: 0;
                    }
                    .suggestions li {
                        margin: 10px 0;
                        color: var(--text-secondary);
                    }
                </style>
            </head>
            <body>
                <div class="error-icon">!</div>
                <h1>无法加载页面</h1>
                <p>$errorDescription</p>
                <div class="url">$failingUrl</div>
                
                <div class="suggestions">
                    <h3>建议解决方式：</h3>
                    <ul>
                        <li>检查网络连接是否正常</li>
                        <li>尝试切换 WiFi 和移动数据</li>
                        <li>刷新页面</li>
                        <li>稍后再试</li>
                    </ul>
                </div>

                <a href="javascript:location.reload();" class="button">重新加载</a>
                <br><br>
                <a href="javascript:Android.loadLandingPage();" class="button button-secondary">返回首页</a>
                
                <script>
                    if (typeof Android === 'undefined') {
                        Android = {
                            loadLandingPage: function() { window.history.back(); }
                        };
                    }
                </script>
            </body>
            </html>
        """.trimIndent()
    }
    
    private fun showHelpDialog() {
        val message = """
        
            JAV 浏览器 - 视频播放与下载
            
            🎬 功能：
            • 自动检测 m3u8 视频流
            • 外部播放器播放（VLC、MX Player）
            • 支持下载
            • 广告过滤
            • 收藏管理
            
            📱 推荐播放器：
            • VLC Media Player
            • MX Player
            • KM Player
            
            💾 推荐下载器：
            Lj Video Downloader（支持 m3u8、mp4、mpd）
            
            💡 提示：
            Lj Downloader 的无广告修改版可在线获取。
        """.trimIndent()
        
        MaterialAlertDialogBuilder(this)
            .setTitle("❓ 帮助")
            .setMessage(message)
            .setPositiveButton("关闭", null)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        cancelLoadTimeout()
        videoProxyServer?.stop()
        videoProxyServer = null
    }
}
