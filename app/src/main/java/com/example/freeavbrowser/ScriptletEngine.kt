package com.example.freeavbrowser

/**
 * ScriptletEngine - uBlock Origin compatible JS scriptlet injection engine.
 * Generates JavaScript code to be injected before/during page load.
 */
object ScriptletEngine {

    /**
     * Generate JS code to inject at page start (before any page scripts run).
     * These modify the global environment to prevent ad behaviors.
     */
    fun getPageStartScript(): String {
        return buildString {
            // === Popup Prevention ===
            appendLine("""
                // Prevent window.open popups
                (function() {
                    var realOpen = window.open;
                    window.open = function(url, name, features) {
                        console.log('[AdBlock] Blocked window.open: ' + url);
                        return null;
                    };
                })();

                // === Navigation Hijack Protection ===
                (function() {
                    var adRedirectDomains = [
                        'wpadmngr.com', 'mnaspm.com', 'clickadu', 'smartpop',
                        'ra13.xyz', '98d4403b02.com', 'clammyendearedkeg.com',
                        'adtng.com', 'havenclick.com', 'ads-twitter.com',
                        'exoclick.com', 'juicyads.com', 'trafficjunky.com',
                        'bluetrafficstream.com', 'creativemyavlive.com', 'tsyndicate.com'
                    ];

                    var origAssign = window.location.assign;
                    var origReplace = window.location.replace;

                    var isAdRedirect = function(url) {
                        if (!url) return false;
                        var urlStr = typeof url === 'string' ? url : url.toString();
                        for (var i = 0; i < adRedirectDomains.length; i++) {
                            if (urlStr.indexOf(adRedirectDomains[i]) !== -1) {
                                return true;
                            }
                        }
                        return false;
                    };

                    window.location.assign = function(url) {
                        if (isAdRedirect(url)) {
                            console.log('[AdBlock] Blocked location.assign: ' + url);
                            return;
                        }
                        return origAssign.apply(this, arguments);
                    };

                    window.location.replace = function(url) {
                        if (isAdRedirect(url)) {
                            console.log('[AdBlock] Blocked location.replace: ' + url);
                            return;
                        }
                        return origReplace.apply(this, arguments);
                    };
                })();

                // Prevent fetch-based ad requests
                (function() {
                    var realFetch = window.fetch;
                    var blockedFetchTerms = ['havenclick', 'ads-twitter.com', 'pagead', 'googleads',
                        'doubleclick', 'googlesyndication', 'adtng', 'wpadmngr', 'mnaspm',
                        'clickadu', 'smartpop', 'ra13.xyz', '98d4403b02', 'cloudflareinsights',
                        'bluetrafficstream.com', 'creativemyavlive.com', 'tsyndicate.com'];
                    window.fetch = function(url, options) {
                        var urlStr = typeof url === 'string' ? url : (url && url.url ? url.url : '');
                        for (var i = 0; i < blockedFetchTerms.length; i++) {
                            if (urlStr.indexOf(blockedFetchTerms[i]) !== -1) {
                                console.log('[AdBlock] Blocked fetch: ' + urlStr);
                                return Promise.reject(new Error('Blocked by AdBlock'));
                            }
                        }
                        return realFetch.apply(this, arguments);
                    };
                })();

                // === Privacy Sandbox Disable ===
                (function() {
                    // Disable Protected Audience API
                    if (navigator.joinAdInterestGroup) {
                        navigator.joinAdInterestGroup = function() { return Promise.reject(); };
                    }
                    if (navigator.runAdAuction) {
                        navigator.runAdAuction = function() { return Promise.reject(); };
                    }
                    // Disable Topics API
                    if (document.browsingTopics) {
                        document.browsingTopics = function() { return Promise.resolve([]); };
                    }
                    // Disable Private State Token
                    if (document.hasPrivateToken) {
                        document.hasPrivateToken = function() { return Promise.resolve(false); };
                    }
                })();
            """.trimIndent())
        }
    }

    /**
     * Generate simplified anti-adblock injection (universal, not per-domain).
     *
     * This covers 80%+ of small-site anti-adblock without the complexity
     * of a full +js() scriptlet engine.
     */
    fun getAntiAdblockScript(): String {
        return """
            (function() {
                // Core anti-adblock variables
                window.ADBLOCK = false;
                window.adblock = false;
                window.adsBlocked = false;
                window.canRunAds = true;
                window.adblockDetected = false;
                window.isAdblockActive = false;

                // Common detection functions → noop
                window.detectADB = function() { return false; };
                window.blockAdBlock = { onDetected: function(){}, onNotDetected: function(){} };
                window.FuckAdBlock = { onDetected: function(){}, onNotDetected: function(){} };

                // Player-specific detection (HentaiHaven pattern)
                if (typeof PlayerLogic !== 'undefined' && PlayerLogic.prototype) {
                    PlayerLogic.prototype.detectADB = function() { return false; };
                }

                console.log('[AdBlock] Anti-adblock bypass injected');
            })();
        """.trimIndent()
    }

    /**
     * Generate domain-specific overrides (minimal, only for well-known sites).
     */
    fun getDomainScriptlets(domain: String): String {
        val sb = StringBuilder()

        // HentaiHaven: clickPopUpcf constant
        if (domain.contains("hentaihaven")) {
            sb.appendLine("window.clickPopUpcf = 1;")
        }

        // Hanime: cookie spoofing
        if (domain.contains("hanime.tv")) {
            sb.appendLine("""
                Object.defineProperty(window, 'ABLK', { value: false, writable: false, configurable: false });
                document.cookie = 'in_d4=1; path=/; max-age=31536000';
            """.trimIndent())
        }

        return sb.toString()
    }

    /**
     * Ad element hider — uses CSS display:none instead of DOM remove().
     * This avoids the MutationObserver → remove → recreate → remove CPU loop.
     */
    fun getAdElementHider(): String {
        return """
            (function() {
                var adKeywords = ['popup', 'Script_Manager', 'clickPopUp', 'havenclick', 'ads-twitter',
                    'wpadmngr', 'clickadu', 'smartpop', 'popunder', 'tabunder'];

                // Phase 1: CSS hide all matching elements NOW
                var allElements = document.querySelectorAll('*');
                allElements.forEach(function(el) {
                    // Check className and id
                    var className = (el.className && el.className.toString) ? el.className.toString().toLowerCase() : '';
                    var elId = (el.id || '').toLowerCase();

                    for (var i = 0; i < adKeywords.length; i++) {
                        if (className.indexOf(adKeywords[i]) !== -1 || elId.indexOf(adKeywords[i]) !== -1) {
                            el.style.display = 'none';
                            el.style.visibility = 'hidden';
                            el.style.setProperty('display', 'none', 'important');
                            el.style.setProperty('visibility', 'hidden', 'important');
                            break;
                        }
                    }

                    // Check src/href attributes (for scripts, iframes, links)
                    var src = (el.getAttribute && el.getAttribute('src')) || '';
                    var href = (el.getAttribute && el.getAttribute('href')) || '';
                    var combined = (src + href).toLowerCase();

                    for (var i = 0; i < adKeywords.length; i++) {
                        if (combined.indexOf(adKeywords[i]) !== -1) {
                            if (el.tagName === 'SCRIPT') {
                                el.type = 'application/blocked';
                                el.removeAttribute('src');
                            }
                            el.style.display = 'none';
                            el.style.visibility = 'hidden';
                            el.style.setProperty('display', 'none', 'important');
                            break;
                        }
                    }
                });

                // Phase 2: Watch for new ad elements (but use CSS, not remove)
                if (window.MutationObserver) {
                    var observer = new MutationObserver(function(mutations) {
                        mutations.forEach(function(mutation) {
                            mutation.addedNodes.forEach(function(node) {
                                if (node.nodeType !== 1) return;
                                var cn = (node.className && node.className.toString) ? node.className.toString().toLowerCase() : '';
                                var nid = (node.id || '').toLowerCase();
                                for (var i = 0; i < adKeywords.length; i++) {
                                    if (cn.indexOf(adKeywords[i]) !== -1 || nid.indexOf(adKeywords[i]) !== -1) {
                                        node.style.display = 'none';
                                        node.style.visibility = 'hidden';
                                        node.style.setProperty('display', 'none', 'important');
                                        break;
                                    }
                                }
                            });
                        });
                    });
                    observer.observe(document.documentElement, { childList: true, subtree: true });
                }

                console.log('[AdBlock] Ad element hider installed (CSS-based)');
            })();
        """.trimIndent()
    }

    /**
     * fetch/XHR ad response filter.
     * Intercept ad API responses and return empty/cleaned data.
     */
    fun getFetchXhrFilter(): String {
        return """
            (function() {
                var adPathPatterns = ['/ads', '/ad/', '/sponsor', '/popup', '/banner',
                    '/tracking', '/promo', '/advert', '/z/', 'havenclick', 'wpadmngr',
                    'clickadu', 'smartpop', 'ra13.xyz', 'mnaspm'];

                var isAdUrl = function(url) {
                    if (!url) return false;
                    var urlStr = typeof url === 'string' ? url : url.toString();
                    for (var i = 0; i < adPathPatterns.length; i++) {
                        if (urlStr.indexOf(adPathPatterns[i]) !== -1) return true;
                    }
                    return false;
                };

                // Hook fetch
                var realFetch = window.fetch;
                window.fetch = function(url, options) {
                    if (isAdUrl(url)) {
                        console.log('[AdBlock] fetch blocked: ' + url);
                        return Promise.resolve(new Response('{}', { status: 200, headers: { 'Content-Type': 'application/json' } }));
                    }
                    return realFetch.apply(this, arguments);
                };

                // Hook XMLHttpRequest
                var realXhrOpen = XMLHttpRequest.prototype.open;
                XMLHttpRequest.prototype.open = function(method, url) {
                    this._adblockUrl = url;
                    this._adblockBlocked = isAdUrl(url);
                    if (this._adblockBlocked) {
                        console.log('[AdBlock] XHR blocked: ' + url);
                    }
                    return realXhrOpen.apply(this, arguments);
                };

                var realXhrSend = XMLHttpRequest.prototype.send;
                XMLHttpRequest.prototype.send = function() {
                    if (this._adblockBlocked) {
                        var xhr = this;
                        setTimeout(function() {
                            Object.defineProperty(xhr, 'status', { value: 200 });
                            Object.defineProperty(xhr, 'readyState', { value: 4 });
                            Object.defineProperty(xhr, 'responseText', { value: '{}' });
                            Object.defineProperty(xhr, 'response', { value: '{}' });
                            if (xhr.onreadystatechange) xhr.onreadystatechange();
                            if (xhr.onload) xhr.onload();
                        }, 0);
                        return;
                    }
                    return realXhrSend.apply(this, arguments);
                };
            })();
        """.trimIndent()
    }

    /**
     * 根据 uBO scriptlet 名称生成对应的 JavaScript 代码。
     * 支持 set-constant, prevent-window-open, abort-current-script,
     * trusted-set-cookie, noopFunc 五种 scriptlet。
     */
    fun executeScriptlet(name: String, args: List<String>): String {
        return when (name) {
            "set-constant" -> {
                // set-constant(prop, value) → Object.defineProperty
                val prop = args.getOrNull(0) ?: return ""
                val value = args.getOrNull(1) ?: "false"
                if (value == "noopFunc" || value == "noopfn") {
                    "($prop=function(){});"
                } else {
                    "(function(){var v=$value;try{Object.defineProperty(window,'$prop',{value:v,writable:false,configurable:false});}catch(e){window['$prop']=v;}})();"
                }
            }

            "abort-current-script" -> {
                // abort-current-script(method, pattern)
                // 拦截 document.createElement 调用，匹配指定 tag + 内容模式
                val method = args.getOrNull(0) ?: "document.createElement"
                val pattern = args.getOrNull(1) ?: ""
                if (method == "document.createElement" && pattern.isNotEmpty()) {
                    """
                    (function(){
                        var orig=document.createElement.bind(document);
                        document.createElement=new Proxy(document.createElement,{
                            apply:function(t,self,args){
                                var tag=(args[0]||'').toString().toLowerCase();
                                if(tag==='$pattern'){
                                    console.log('[AdBlock] abort-current-script: blocked createElement($pattern)');
                                    var d=orig('div');
                                    d.style.display='none';
                                    d.style.setProperty('display','none','important');
                                    Object.defineProperty(d,'src',{set:function(v){console.log('[AdBlock] blocked src:',v);},get:function(){return'';}});
                                    return d;
                                }
                                return Reflect.apply(t,self,args);
                            }
                        });
                    })();
                    """.trimIndent()
                } else ""
            }

            "noopFunc" -> {
                val prop = args.getOrNull(0) ?: return ""
                "($prop=function(){});"
            }

            "trusted-set-cookie" -> {
                val cookieName = args.getOrNull(0) ?: return ""
                val cookieValue = args.getOrNull(1) ?: "1"
                "document.cookie='$cookieName=$cookieValue;path=/;max-age=31536000';"
            }

            "prevent-addEventListener" -> {
                val typeFilter = args.getOrNull(0) ?: ""
                val listenerFilter = args.getOrNull(1) ?: ""
                """
                (function(){
                    var needle1='$typeFilter',needle2='$listenerFilter';
                    var orig=EventTarget.prototype.addEventListener;
                    EventTarget.prototype.addEventListener=function(type,listener){
                        if(needle1&&type.toString().indexOf(needle1)===-1)return orig.apply(this,arguments);
                        if(needle2){
                            var s=listener.toString();
                            if(s.indexOf(needle2)===-1)return orig.apply(this,arguments);
                        }
                        console.log('[AdBlock] prevent-addEventListener:',type,needle2||'');
                    };
                })();
                """.trimIndent()
            }

            "noeval-if" -> {
                val pattern = args.getOrNull(0) ?: return ""
                """
                (function(){
                    var needle='$pattern';
                    var origEval=window.eval;
                    window.eval=function(s){
                        var str=s.toString();
                        if(str.indexOf(needle)!==-1){
                            console.log('[AdBlock] noeval-if blocked:',needle);
                            return;
                        }
                        return origEval(s);
                    };
                    var origFunc=window.Function;
                    window.Function=function(){
                        var args=Array.prototype.slice.call(arguments);
                        var body=args[args.length-1]||'';
                        if(body.toString().indexOf(needle)!==-1){
                            console.log('[AdBlock] noeval-if blocked Function:',needle);
                            return function(){};
                        }
                        return origFunc.apply(this,arguments);
                    };
                })();
                """.trimIndent()
            }

            else -> {
                // 未知 scriptlet → 忽略
                ""
            }
        }
    }

    /**
     * 从解析后的规则列表生成批量 scriptlet 注入代码。
     */
    fun generateScriptlets(rules: List<AdblockRuleParser.ParsedRule>, domain: String): String {
        val sb = StringBuilder()
        for (rule in rules) {
            if (rule.type != AdblockRuleParser.RuleType.SCRIPTLET) continue
            if (rule.scriptletName == null) continue

            // 域名过滤：无域名=全局，否则匹配当前域名
            if (rule.domain != null && rule.domain.isNotBlank()) {
                if (!domain.endsWith(rule.domain) && domain != rule.domain) continue
            }

            val js = executeScriptlet(rule.scriptletName, rule.scriptletArgs)
            if (js.isNotBlank()) {
                sb.appendLine(js)
            }
        }
        return sb.toString()
    }
}
