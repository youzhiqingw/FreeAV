package com.example.freeavbrowser

/**
 * AdblockRuleParser — 解析 uBlock Origin / AdGuard 格式的过滤规则。
 *
 * 支持格式：
 *   ||domain.com^              → 网络拦截
 *   ||domain.com^$script       → 仅拦截脚本
 *   ||domain.com^$redirect=noopjs → 重定向到空脚本
 *   @@||domain.com^$generichide → 通用隐藏白名单
 *   ##.selector                → 元素隐藏
 *   ##+js(name, arg1, arg2)    → Scriptlet 注入
 */
object AdblockRuleParser {

    data class ParsedRule(
        val type: RuleType,
        val domain: String? = null,
        val pattern: String? = null,
        val selector: String? = null,      // CSS selector (## rules)
        val scriptletName: String? = null,  // +js(name)
        val scriptletArgs: List<String> = emptyList(),
        val isRedirect: Boolean = false,    // $redirect=noopjs
        val isGenerichide: Boolean = false, // @@ generichide
        val resourceTypes: Set<String> = emptySet() // $script, $image, etc.
    )

    enum class RuleType {
        BLOCK,         // ||domain.com^
        WHITELIST,     // @@||domain.com^
        ELEMENT_HIDE,  // ##.selector
        SCRIPTLET,     // ##+js(...)
        GENERICHIDE    // @@||domain.com^$generichide
    }

    /**
     * 解析单条规则。
     */
    fun parse(rule: String): ParsedRule? {
        val trimmed = rule.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("!") || trimmed.startsWith("[")) return null

        return when {
            // Generichide: @@||domain.com^$generichide
            trimmed.startsWith("@@||") && trimmed.contains("\$generichide") ->
                parseGenerichide(trimmed)

            // Redirect noopjs: ||domain.com^$redirect=noopjs
            trimmed.contains("\$redirect=noopjs") ->
                parseRedirectNoopjs(trimmed)

            // Scriptlet: domain.com##+js(name, arg1, arg2)
            trimmed.contains("##+js(") ->
                parseScriptlet(trimmed)

            // Element hide: domain.com##.selector
            trimmed.contains("##") && !trimmed.contains("##+js(") ->
                parseElementHide(trimmed)

            // Whitelist: @@||domain.com^
            trimmed.startsWith("@@||") ->
                parseWhitelist(trimmed)

            // Network block: ||domain.com^
            trimmed.startsWith("||") ->
                parseNetworkBlock(trimmed)

            else -> null
        }
    }

    private fun parseGenerichide(rule: String): ParsedRule {
        // @@||hanime.tv^$generichide → extract "hanime.tv"
        val domain = rule
            .removePrefix("@@||")
            .substringBefore("^")
            .substringBefore("\$")
        return ParsedRule(type = RuleType.GENERICHIDE, domain = domain, isGenerichide = true)
    }

    private fun parseRedirectNoopjs(rule: String): ParsedRule {
        // ||googletagmanager.com^$redirect=noopjs
        val domain = rule
            .removePrefix("||")
            .substringBefore("^")
            .substringBefore("\$")
        val resourceTypes = parseResourceTypes(rule)
        return ParsedRule(
            type = RuleType.BLOCK,
            domain = domain,
            isRedirect = true,
            resourceTypes = resourceTypes
        )
    }

    private fun parseScriptlet(rule: String): ParsedRule? {
        // hanime.tv##+js(set-constant, ABLK, false)
        // ##+js(prevent-window-open)
        val domain = if (rule.startsWith("##")) null else rule.substringBefore("##")

        val jsPart = rule.substringAfter("##+js(").substringBefore(")")
        val parts = jsPart.split(",").map { it.trim() }
        val name = parts.getOrNull(0) ?: return null
        val args = if (parts.size > 1) parts.drop(1) else emptyList()

        return ParsedRule(
            type = RuleType.SCRIPTLET,
            domain = domain?.ifBlank { null },
            scriptletName = name,
            scriptletArgs = args
        )
    }

    private fun parseElementHide(rule: String): ParsedRule {
        // hanime.tv##.desktop.htvad
        // ##.banner-ad
        val domain = if (rule.startsWith("##")) null else rule.substringBefore("##")
        val selector = rule.substringAfter("##")

        return ParsedRule(
            type = RuleType.ELEMENT_HIDE,
            domain = domain?.ifBlank { null },
            selector = selector
        )
    }

    private fun parseWhitelist(rule: String): ParsedRule {
        // @@||missav.ws^
        val domain = rule.removePrefix("@@||").substringBefore("^")
        return ParsedRule(type = RuleType.WHITELIST, domain = domain)
    }

    private fun parseNetworkBlock(rule: String): ParsedRule {
        // ||adtng.com^
        // ||adtng.com^$script
        val domain = rule.removePrefix("||").substringBefore("^").substringBefore("\$")
        val resourceTypes = parseResourceTypes(rule)
        return ParsedRule(
            type = RuleType.BLOCK,
            domain = domain,
            resourceTypes = resourceTypes
        )
    }

    /**
     * 解析资源类型修饰符: $script, $image, $media, $subdocument
     */
    private fun parseResourceTypes(rule: String): Set<String> {
        val types = mutableSetOf<String>()
        val modifierPart = rule.substringAfter("\$", "")

        val typeMap = mapOf(
            "script" to "script",
            "image" to "image",
            "media" to "media",
            "subdocument" to "frame",
            "xmlhttprequest" to "xhr",
            "stylesheet" to "stylesheet",
            "font" to "font"
        )

        for ((key, value) in typeMap) {
            if (modifierPart.contains(key) && !modifierPart.contains("generichide") && !modifierPart.contains("redirect")) {
                types.add(value)
            }
        }

        return types
    }

    /**
     * 批量解析规则列表。
     */
    fun parseAll(rules: List<String>): List<ParsedRule> {
        return rules.mapNotNull { parse(it) }
    }
}
