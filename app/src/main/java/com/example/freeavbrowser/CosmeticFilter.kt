package com.example.freeavbrowser

/**
 * CosmeticFilter - CSS injection engine for element hiding rules.
 * Supports class, id, and attribute selectors (uBlock ## syntax).
 */
object CosmeticFilter {

    // CSS rules keyed by domain
    private val rules = mapOf(
        "hentaihaven.xxx" to listOf(
            ".vrav_a_pc { display:none !important; }",
            ".vrav_a_mb { display:none !important; }",
            "a[href*=\"havenclick.com\"] { display:none !important; }",
            "a[href*=\"ads-twitter.com\"] { display:none !important; }",
            "iframe[src*=\"havenclick.com\"] { display:none !important; }",
            "[class*=\"banner-ad\"] { display:none !important; }",
            "[class*=\"popup\"] { display:none !important; }"
        ),
        "hanime.tv" to listOf(
            ".desktop.htvad { display:none !important; }",
            "a[href*=\"adtng.com\"] { display:none !important; }"
        ),
        "jable.tv" to listOf(
            "iframe[id^=\"container-\"] { display:none !important; }"
        )
    )

    private val genericRules = listOf(
        "[class*=\"banner-ad\"] { display:none !important; }",
        "[id*=\"popup-ad\"] { display:none !important; }",
        "[class*=\"sponsor\"] { display:none !important; }"
    )

    /**
     * generichide 白名单 — 这些域名不使用通用隐藏规则，避免误杀。
     * 格式: "example.com"
     */
    private val generichideWhitelist = mutableSetOf(
        "hanime.tv"       // @@||hanime.tv^$generichide
    )

    /**
     * 添加运行时 generichide 域名（从 uBO 规则解析）。
     */
    fun addGenerichideDomain(domain: String) {
        generichideWhitelist.add(domain.lowercase())
    }

    fun addGenerichideDomains(domains: Collection<String>) {
        domains.forEach { generichideWhitelist.add(it.lowercase()) }
    }

    /**
     * Get CSS rules for a specific domain.
     */
    fun getCssRules(domain: String): String {
        val sb = StringBuilder()

        // Domain-specific rules
        for ((ruleDomain, ruleList) in rules) {
            if (domain.endsWith(ruleDomain) || domain == ruleDomain) {
                ruleList.forEach { sb.appendLine(it) }
            }
        }

        // Generic rules — skip if domain is in generichide whitelist
        val isGenerichide = generichideWhitelist.any { domain.endsWith(it) || domain == it }
        if (!isGenerichide) {
            genericRules.forEach { sb.appendLine(it) }
        }

        return sb.toString()
    }

    /**
     * Inject CSS rules into the page via a <style> element.
     * Returns JavaScript code to inject.
     */
    fun getCssInjectionScript(domain: String): String {
        val css = getCssRules(domain)
        if (css.isBlank()) return ""

        // Escape CSS for JavaScript string
        val escapedCss = css
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", "\\n")

        return """
            (function() {
                var style = document.createElement('style');
                style.id = 'adblock-cosmetic-filter';
                style.textContent = '$escapedCss';
                document.head.appendChild(style);
                console.log('[AdBlock] Cosmetic filter injected');
            })();
        """.trimIndent()
    }
}
