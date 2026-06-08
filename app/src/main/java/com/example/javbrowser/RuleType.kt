package com.example.javbrowser

/**
 * 广告拦截规则类型
 */
enum class RuleType {
    BLOCK,           // 通用拦截规则（Adblock Plus）
    WHITELIST,       // 白名单规则（Adblock Plus）
    ELEMENT_HIDE,    // 元素隐藏规则（Adblock Plus）
    NETWORK_BLOCK,   // 网络层拦截（JSON）
    LINK_BLOCK,      // 链接拦截（JSON）
    IFRAME_BLOCK,    // iframe拦截（JSON）
    REDIRECT_BLOCK   // 重定向拦截（JSON）
}

/**
 * 广告规则数据类
 * @param type 规则类型
 * @param domain 匹配的域名（用于网络层拦截），与 pattern 互斥
 * @param pattern 匹配的模式或选择器（用于URL匹配或元素隐藏），与 domain 互斥
 *
 * 注意：对于 BLOCK 类型，domain 和 pattern 不会同时非空：
 * - 如果是域名拦截：domain != null, pattern == null
 * - 如果是 URL 模式拦截：domain == null, pattern != null
 */
data class AdRule(
    val type: RuleType,
    val domain: String?,    // 匹配的域名
    val pattern: String?    // 匹配的模式或选择器
)
