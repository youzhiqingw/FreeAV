# javbrowser — 增强广告屏蔽实现方案

> **Claude Code 目标文档**  
> 基于 217heidai/adblockfilters · Cats-Team/AdRules · xinggsf/Adblock-Plus-Rule · tickmao/ADrules 四个开源规则库  
> 核心约束：**不破坏正常页面内容，不影响 M3U8/HLS 视频流加载**

---

## 目录

- [0 — 方案总览与设计原则](#0--方案总览与设计原则)
- [1 — 规则订阅源配置](#1--规则订阅源配置)
- [2 — 新增文件结构](#2--新增文件结构)
- [3 — FilterListConfig.kt](#3--filterlistconfigkt)
- [4 — FilterListParser.kt](#4--filterlistparserkt)
- [5 — FilterListStorage.kt](#5--filterliststoragekt)
- [6 — AdBlockEngine.kt（替换 AdFilterRules.kt）](#6--adblockengine-kt)
- [7 — CosmeticInjector.kt](#7--cosmeticinjectorkt)
- [8 — FilterListSyncWorker.kt](#8--filterlistsyncworkerkt)
- [9 — MainActivity.kt 集成修改](#9--mainactivitykt-集成修改)
- [10 — SettingsActivity 新增 UI](#10--settingsactivity-新增-ui)
- [11 — "不破坏内容"安全守卫清单](#11--不破坏内容安全守卫清单)
- [12 — 内置精简规则（Fallback）](#12--内置精简规则fallback)

---

## 0 — 方案总览与设计原则

### 架构分层

```
请求进入 shouldInterceptRequest()
        │
        ▼
① 内容类型守卫 ──── 视频流/字体/媒体 → 放行（最高优先级）
        │
        ▼
② 同源守卫 ────────── 请求域 == 页面域 → 放行
        │
        ▼
③ 硬编码白名单 ────── JAV 站点 CDN 域名 → 放行
        │
        ▼
④ 用户自定义白名单 ── 用户手动添加 → 放行
        │
        ▼
⑤ 域名黑名单匹配 ──── HashSet O(1) 查找 → 命中则拦截
        │
        ▼
⑥ 路径规则匹配 ────── 少量 pattern 规则 → 命中则拦截
        │
        ▼
⑦ 放行
```

### 规则来源选型理由

| 来源 | 格式 | 用途 | 规则数 | 更新频率 |
|------|------|------|--------|----------|
| **217heidai adblockdns.txt** | `\|\|domain^` | 主域名黑名单 | ~150k | 每 8 小时 |
| **217heidai adblockdnslite.txt** | `\|\|domain^` | 精简域名黑名单（首选） | ~30k | 每 8 小时 |
| **Cats-Team AdRules adblock_lite.txt** | ABP | 含 CSS cosmetic 规则 | ~30k | 每日 |
| **xinggsf mv.txt** | ABP | **视频广告专项规则** | ~200 | 按需 |
| **tickmao ADrules EasyPrivacy.txt** | ABP path 规则 | 追踪路径拦截 | ~5k | 按需 |

> **为何使用 DNS 格式而非完整 ABP 格式：**  
> 完整 ABP 规则文件 3–8 MB，Android 运行时解析耗时且内存占用高。  
> DNS 格式只含域名，解析时间 < 200ms，内存 HashSet 约 15–25 MB，完全可接受。

### 关键约束（不可妥协）

1. **永不拦截** `.m3u8`, `.ts`, `.mp4`, `.key` 后缀的请求
2. **永不拦截** 与当前页面同域名的请求（第一方资源）
3. **永不拦截** 硬编码白名单中的 JAV 站点及视频 CDN 域名
4. **永不拦截** `Content-Type: video/*` 类型响应

---

## 1 — 规则订阅源配置

### 所有可用 URL（含 CDN 加速，国内友好）

#### 主力源 — 217heidai/adblockfilters

```
# ABP 完整格式（浏览器插件 / uBlock Origin 兼容）
主线路: https://raw.githubusercontent.com/217heidai/adblockfilters/main/rules/adblockfilters.txt
CDN1:   https://gcore.jsdelivr.net/gh/217heidai/adblockfilters@main/rules/adblockfilters.txt
CDN2:   https://github.boki.moe/https://raw.githubusercontent.com/217heidai/adblockfilters/main/rules/adblockfilters.txt

# ABP 精简版（推荐移动端使用）
主线路: https://raw.githubusercontent.com/217heidai/adblockfilters/main/rules/adblockfilterslite.txt
CDN1:   https://gcore.jsdelivr.net/gh/217heidai/adblockfilters@main/rules/adblockfilterslite.txt
CDN2:   https://github.boki.moe/https://raw.githubusercontent.com/217heidai/adblockfilters/main/rules/adblockfilterslite.txt

# DNS 完整版（||domain^ 格式，最高效）
主线路: https://raw.githubusercontent.com/217heidai/adblockfilters/main/rules/adblockdns.txt
CDN1:   https://gcore.jsdelivr.net/gh/217heidai/adblockfilters@main/rules/adblockdns.txt

# DNS 精简版（国内域名，推荐作为轻量级选项）
主线路: https://raw.githubusercontent.com/217heidai/adblockfilters/main/rules/adblockdnslite.txt
CDN1:   https://gcore.jsdelivr.net/gh/217heidai/adblockfilters@main/rules/adblockdnslite.txt
```

#### 中国区 Ad 规则 — Cats-Team/AdRules

```
# ABP 完整版（含 CSS 元素隐藏规则）
主线路: https://raw.githubusercontent.com/Cats-Team/AdRules/main/adblock.txt
GitLab: https://gitlab.com/cats-team/adrules/-/raw/main/adblock.txt

# ABP 精简版（约 3 万条，推荐移动端）
Bitbucket: https://bitbucket.org/hacamer/adrules/raw/main/adblock_lite.txt
```

#### 视频广告专项 — xinggsf/Adblock-Plus-Rule

```
# 视频网站广告规则（JAV 站同适用）
CDN1: https://gcore.jsdelivr.net/gh/xinggsf/Adblock-Plus-Rule@master/mv.txt
CDN2: https://cdn.jsdelivr.net/gh/xinggsf/Adblock-Plus-Rule@master/mv.txt

# 通用规则
CDN1: https://gcore.jsdelivr.net/gh/xinggsf/Adblock-Plus-Rule@master/rule.txt
```

#### 追踪器路径规则 — tickmao/ADrules

```
# EasyPrivacy（追踪 URL 路径规则）
主线路: https://raw.githubusercontent.com/tickmao/ADrules/master/EasyPrivacy.txt
```

#### 混合规则自动更新 — lingeringsound/adblock_auto

```
# 混合规则（含国内外广告）
Pages: https://lingeringsound.github.io/adblock_auto/adblock_auto.txt
```

---

## 2 — 新增文件结构

```
app/src/main/java/com/example/javbrowser/
└── adblocker/
    ├── FilterListConfig.kt       ← 规则源 URL + 默认订阅配置
    ├── FilterListParser.kt       ← 解析 DNS/ABP 格式 → 内部数据结构
    ├── FilterListStorage.kt      ← 文件级存储（非 SharedPreferences）
    ├── AdBlockEngine.kt          ← 运行时匹配引擎（替换 AdFilterRules.kt）
    ├── CosmeticInjector.kt       ← CSS 元素隐藏 + JS 注入
    └── FilterListSyncWorker.kt   ← WorkManager 后台同步

app/src/main/res/raw/
├── builtin_domains.txt           ← 内置精简域名黑名单（~1000 条，无需联网）
└── builtin_cosmetic.txt          ← 内置 CSS 元素隐藏规则（~200 条）

app/src/main/java/com/example/javbrowser/
└── (修改) MainActivity.kt        ← 集成 AdBlockEngine
└── (修改) SettingsActivity.kt    ← 广告屏蔽设置 UI
```

**文件存储路径**（运行时，`filesDir` 目录，App 私有，无需权限）：

```
/data/data/com.example.freeavbrowser/files/
└── filters/
    ├── domains_primary.txt       ← 已解析的域名列表（换行分隔）
    ├── domains_video.txt         ← 视频广告域名列表
    ├── pattern_rules.json        ← 路径匹配规则（少量）
    ├── cosmetic_rules.json       ← CSS 元素隐藏规则
    ├── user_whitelist.txt        ← 用户自定义白名单
    └── meta.json                 ← 各订阅源 ETag + 最后更新时间
```

---

## 3 — FilterListConfig.kt

```kotlin
package com.example.freeavbrowser.adblocker

/**
 * 规则订阅源定义
 * 每个 FilterSource 描述一个独立的规则文件及其获取方式
 */
data class FilterSource(
    val id: String,
    val displayName: String,
    val description: String,
    val urls: List<String>,          // 按优先级排列，第一个失败尝试下一个
    val format: FilterFormat,
    val category: FilterCategory,
    val enabledByDefault: Boolean = true,
    val targetFile: String           // filesDir/filters/ 下的存储文件名
)

enum class FilterFormat {
    DNS_ADGUARD,   // ||domain^ 每行一条，注释以 ! 或 # 开头
    ABP,           // 完整 Adblock Plus 格式（含 cosmetic rules）
    HOSTS          // 0.0.0.0 domain.com 或 127.0.0.1 domain.com
}

enum class FilterCategory {
    PRIMARY_DOMAINS,    // 主域名黑名单
    VIDEO_ADS,          // 视频广告专项
    TRACKING,           // 追踪器
    COSMETIC,           // CSS 元素隐藏
    MIXED               // 混合
}

object FilterListConfig {

    val BUILTIN_SOURCES = listOf(

        // ── 主力域名规则（精简版，首选）────────────────────────────
        FilterSource(
            id = "heidai_dns_lite",
            displayName = "AdBlock DNS Lite（精简版）",
            description = "217heidai 合并精简规则，每 8 小时更新，约 3 万条国内域名",
            urls = listOf(
                "https://gcore.jsdelivr.net/gh/217heidai/adblockfilters@main/rules/adblockdnslite.txt",
                "https://raw.githubusercontent.com/217heidai/adblockfilters/main/rules/adblockdnslite.txt",
                "https://github.boki.moe/https://raw.githubusercontent.com/217heidai/adblockfilters/main/rules/adblockdnslite.txt"
            ),
            format = FilterFormat.DNS_ADGUARD,
            category = FilterCategory.PRIMARY_DOMAINS,
            enabledByDefault = true,
            targetFile = "domains_lite.txt"
        ),

        // ── 主力域名规则（完整版，可选）────────────────────────────
        FilterSource(
            id = "heidai_dns_full",
            displayName = "AdBlock DNS Full（完整版）",
            description = "217heidai 完整合并规则，约 15 万条，覆盖更全但体积较大",
            urls = listOf(
                "https://gcore.jsdelivr.net/gh/217heidai/adblockfilters@main/rules/adblockdns.txt",
                "https://raw.githubusercontent.com/217heidai/adblockfilters/main/rules/adblockdns.txt"
            ),
            format = FilterFormat.DNS_ADGUARD,
            category = FilterCategory.PRIMARY_DOMAINS,
            enabledByDefault = false,   // 默认关闭，由用户手动开启
            targetFile = "domains_full.txt"
        ),

        // ── 视频广告专项规则（最重要！）────────────────────────────
        FilterSource(
            id = "xinggsf_video",
            displayName = "乘风视频规则",
            description = "专针对视频网站广告，约 200 条精准规则，误杀率极低",
            urls = listOf(
                "https://gcore.jsdelivr.net/gh/xinggsf/Adblock-Plus-Rule@master/mv.txt",
                "https://cdn.jsdelivr.net/gh/xinggsf/Adblock-Plus-Rule@master/mv.txt"
            ),
            format = FilterFormat.ABP,
            category = FilterCategory.VIDEO_ADS,
            enabledByDefault = true,
            targetFile = "domains_video.txt"
        ),

        // ── 中国区规则 + CSS 元素隐藏 ────────────────────────────
        FilterSource(
            id = "cats_adrules_lite",
            displayName = "AdRules 精简版（含元素隐藏）",
            description = "Cats-Team 中国区广告规则，含 CSS 元素隐藏，约 3 万条",
            urls = listOf(
                "https://bitbucket.org/hacamer/adrules/raw/main/adblock_lite.txt",
                "https://raw.githubusercontent.com/Cats-Team/AdRules/main/adblock.txt",
                "https://gitlab.com/cats-team/adrules/-/raw/main/adblock.txt"
            ),
            format = FilterFormat.ABP,
            category = FilterCategory.COSMETIC,
            enabledByDefault = true,
            targetFile = "cosmetic_rules.json"   // 仅提取 cosmetic 部分存为 JSON
        ),

        // ── 追踪器路径规则 ────────────────────────────────────────
        FilterSource(
            id = "tickmao_easyprivacy",
            displayName = "EasyPrivacy（追踪器）",
            description = "tickmao/ADrules 托管的 EasyPrivacy，追踪 URL 路径拦截",
            urls = listOf(
                "https://raw.githubusercontent.com/tickmao/ADrules/master/EasyPrivacy.txt"
            ),
            format = FilterFormat.ABP,
            category = FilterCategory.TRACKING,
            enabledByDefault = false,   // 路径规则较激进，默认关闭
            targetFile = "pattern_rules.json"
        ),

        // ── 混合规则（lingeringsound 自动更新）────────────────────
        FilterSource(
            id = "lingeringsound_auto",
            displayName = "混合规则（自动更新）",
            description = "lingeringsound 每日自动合并的混合规则",
            urls = listOf(
                "https://lingeringsound.github.io/adblock_auto/adblock_auto.txt"
            ),
            format = FilterFormat.ABP,
            category = FilterCategory.MIXED,
            enabledByDefault = false,
            targetFile = "domains_mixed.txt"
        )
    )

    // ── 不可修改的硬编码白名单（保护正常内容加载）──────────────
    val HARDCODED_WHITELIST = setOf(
        // JAV 站点本身
        "jable.tv", "missav.ai", "missav.ws", "missav.com",
        "javdb.com", "javbus.com", "rou.video", "avjoy.me",
        "javlibrary.com", "dmm.co.jp", "fanza.com", "mgstage.com",
        "onejav.com", "supjav.com",

        // 视频 CDN（不可拦截，否则视频播放失败）
        "cloudfront.net", "akamaized.net", "fastly.net",
        "hwcdn.net", "edgesuite.net", "akamai.net",
        "llnwd.net", "limelight.com", "cdnetworks.com",
        "azureedge.net", "cloudflare.com", "cdn77.com",

        // 通用 CDN / 字体 / 分析（选择性保留）
        "googleapis.com", "gstatic.com", "jquery.com",
        "bootstrapcdn.com", "jsdelivr.net", "cdnjs.cloudflare.com",
        "unpkg.com",

        // 其他常用服务
        "google.com", "googlevideo.com", "youtube.com"
    )

    // ── 媒体文件后缀（绝不拦截这些类型）──────────────────────────
    val NEVER_BLOCK_EXTENSIONS = setOf(
        ".m3u8", ".ts", ".mp4", ".mp3", ".m4a", ".m4v",
        ".webm", ".ogg", ".flv", ".key",    // HLS key file
        ".woff", ".woff2", ".ttf", ".eot",  // 字体
        ".jpg", ".jpeg", ".png", ".webp", ".gif", ".ico",  // 图片（视情况可放行）
        ".css",                              // 样式表
        ".js"                               // JS（由 pattern 规则精确控制，此处可选）
    )

    // ── 永不触发 cosmetic 注入的域名（登录页等）──────────────────
    val COSMETIC_BLACKLIST = setOf(
        "accounts.google.com", "login.live.com", "apple.com"
    )
}
```

---

## 4 — FilterListParser.kt

```kotlin
package com.example.freeavbrowser.adblocker

/**
 * 解析三种格式的规则文件
 * 输出统一的 ParsedRules 数据结构
 */
data class ParsedRules(
    val blockedDomains: Set<String>,       // 精确域名（含子域名匹配）
    val patternRules: List<PatternRule>,   // 路径级别规则（少量）
    val cosmeticGlobal: List<String>,      // 全局 CSS 选择器
    val cosmeticPerDomain: Map<String, List<String>>  // 站点专属 CSS 选择器
)

data class PatternRule(
    val keyword: String,         // URL 中需出现的字符串
    val isWhitelist: Boolean = false,
    val thirdPartyOnly: Boolean = false
)

object FilterListParser {

    /**
     * 主解析入口：根据格式分发到对应解析器
     */
    fun parse(content: String, format: FilterFormat): ParsedRules {
        return when (format) {
            FilterFormat.DNS_ADGUARD -> parseDnsAdGuard(content)
            FilterFormat.ABP         -> parseAbp(content)
            FilterFormat.HOSTS       -> parseHosts(content)
        }
    }

    // ── DNS / AdGuard Home 格式解析 ────────────────────────────────
    // 格式: ||exoclick.com^ 或 ! 注释 或 # 注释
    private fun parseDnsAdGuard(content: String): ParsedRules {
        val domains = mutableSetOf<String>()
        content.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.isBlank() || line.startsWith("!") || line.startsWith("#")) return@forEach
            when {
                line.startsWith("||") -> {
                    // ||domain^ 或 ||domain^$important 等
                    val domain = line.removePrefix("||")
                        .split("^", "$", "/").first()
                        .trim()
                        .lowercase()
                    if (domain.isValidDomain()) domains.add(domain)
                }
                // 纯域名行（无前缀）
                !line.contains(" ") && !line.contains("/") && line.isValidDomain() -> {
                    domains.add(line.lowercase())
                }
            }
        }
        return ParsedRules(domains, emptyList(), emptyList(), emptyMap())
    }

    // ── ABP 格式解析 ───────────────────────────────────────────────
    // 处理子集：||domain^, @@||domain^, domain##selector, ##selector
    // 跳过：scriptlets(##+js), extended CSS(#?#), complex options
    fun parseAbp(content: String): ParsedRules {
        val domains = mutableSetOf<String>()
        val patterns = mutableListOf<PatternRule>()
        val cosmeticGlobal = mutableListOf<String>()
        val cosmeticPerDomain = mutableMapOf<String, MutableList<String>>()

        content.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.isBlank() || line.startsWith("!") || line.startsWith("[")) return@forEach

            when {
                // ── Whitelist（@@） — 本解析器不处理，引擎层用硬编码白名单
                line.startsWith("@@") -> { /* skip */ }

                // ── Scriptlets（不支持） ─────────────────────────────────
                line.contains("##+js(") || line.contains("#\$#") -> { /* skip */ }

                // ── Extended CSS（不支持） ────────────────────────────────
                line.contains("#?#") -> { /* skip */ }

                // ── CSS 元素隐藏规则 ─────────────────────────────────────
                // 格式1: ##selector（全局）
                line.startsWith("##") -> {
                    val selector = line.removePrefix("##").trim()
                    if (selector.isValidCssSelector()) cosmeticGlobal.add(selector)
                }
                // 格式2: domain1,domain2##selector（域专属）
                "##" in line && !line.startsWith("||") -> {
                    val delimIdx = line.indexOf("##")
                    val domainPart = line.substring(0, delimIdx)
                    val selector = line.substring(delimIdx + 2).trim()
                    if (!selector.isValidCssSelector()) return@forEach
                    domainPart.split(",").forEach { d ->
                        val domain = d.trim().removePrefix("~").removePrefix("www.")
                        if (domain.isNotBlank()) {
                            cosmeticPerDomain.getOrPut(domain) { mutableListOf() }.add(selector)
                        }
                    }
                }

                // ── 域名拦截规则 ─────────────────────────────────────────
                line.startsWith("||") -> {
                    val body = line.removePrefix("||")
                    val options = if ("$" in body) body.substringAfter("$") else ""
                    val urlPart = body.substringBefore("$")

                    // 跳过含复杂 options 的规则（避免误杀）
                    if (options.contains("domain=") && options.length > 10) return@forEach

                    // 纯域名规则: ||domain^ 或 ||domain/
                    val domain = urlPart.split("^", "/").first().trim().lowercase()
                    if (domain.isValidDomain()) {
                        domains.add(domain)
                    } else if (domain.contains("/")) {
                        // 路径规则转为关键字
                        val keyword = urlPart.trimEnd('^')
                        if (keyword.length > 5) {
                            val thirdPartyOnly = "third-party" in options
                            patterns.add(PatternRule(keyword, thirdPartyOnly = thirdPartyOnly))
                        }
                    }
                }

                // ── 关键字规则（/path 或 keyword）───────────────────────
                line.startsWith("/") && line.endsWith("/") -> { /* 正则规则，跳过 */ }
                line.startsWith("/") && !line.startsWith("/**") -> {
                    val keyword = line.trimEnd('^').trimEnd('*')
                    if (keyword.length > 5 && !keyword.contains("##")) {
                        patterns.add(PatternRule(keyword))
                    }
                }
            }
        }
        return ParsedRules(domains, patterns, cosmeticGlobal, cosmeticPerDomain)
    }

    // ── Hosts 格式解析 ─────────────────────────────────────────────
    // 格式: 0.0.0.0 domain.com 或 127.0.0.1 domain.com
    private fun parseHosts(content: String): ParsedRules {
        val domains = mutableSetOf<String>()
        content.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.isBlank() || line.startsWith("#")) return@forEach
            val parts = line.split(Regex("\\s+"))
            if (parts.size >= 2) {
                val ip = parts[0]
                val domain = parts[1].trim().lowercase()
                if ((ip == "0.0.0.0" || ip == "127.0.0.1") && domain.isValidDomain()) {
                    domains.add(domain)
                }
            }
        }
        return ParsedRules(domains, emptyList(), emptyList(), emptyMap())
    }

    // ── 工具函数 ────────────────────────────────────────────────────
    private fun String.isValidDomain(): Boolean {
        if (isBlank() || length > 253) return false
        if (contains("*") || contains("/") || contains(" ")) return false
        if (startsWith("-") || endsWith("-")) return false
        return matches(Regex("""^[a-z0-9]([a-z0-9\-]{0,61}[a-z0-9])?(\.[a-z0-9]([a-z0-9\-]{0,61}[a-z0-9])?)*\.[a-z]{2,}$"""))
    }

    private fun String.isValidCssSelector(): Boolean {
        if (isBlank() || length > 500) return false
        if (contains("<") || contains("javascript:")) return false
        return true
    }
}
```

---

## 5 — FilterListStorage.kt

```kotlin
package com.example.freeavbrowser.adblocker

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 规则文件的持久化读写
 * 全部使用 App 私有 filesDir，无需外部存储权限
 */
object FilterListStorage {

    private fun filtersDir(context: Context): File =
        File(context.filesDir, "filters").also { it.mkdirs() }

    // ── 域名列表（换行分隔文本，读写高效）─────────────────────────

    fun saveDomains(context: Context, filename: String, domains: Set<String>) {
        File(filtersDir(context), filename).bufferedWriter().use { w ->
            domains.forEach { w.write(it); w.newLine() }
        }
    }

    fun loadDomains(context: Context, filename: String): HashSet<String> {
        val file = File(filtersDir(context), filename)
        if (!file.exists()) return hashSetOf()
        return file.bufferedReader().lineSequence()
            .map { it.trim() }.filter { it.isNotBlank() }
            .toHashSet()
    }

    // ── Pattern 规则（JSON）──────────────────────────────────────────

    fun savePatterns(context: Context, patterns: List<PatternRule>) {
        val arr = JSONArray()
        patterns.forEach { p ->
            arr.put(JSONObject().apply {
                put("kw", p.keyword)
                put("wl", p.isWhitelist)
                put("tp", p.thirdPartyOnly)
            })
        }
        File(filtersDir(context), "pattern_rules.json").writeText(arr.toString())
    }

    fun loadPatterns(context: Context): List<PatternRule> {
        val file = File(filtersDir(context), "pattern_rules.json")
        if (!file.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                PatternRule(obj.getString("kw"), obj.optBoolean("wl"), obj.optBoolean("tp"))
            }
        }.getOrElse { emptyList() }
    }

    // ── Cosmetic 规则（JSON）─────────────────────────────────────────

    data class CosmeticData(
        val global: List<String>,
        val perDomain: Map<String, List<String>>
    )

    fun saveCosmetic(context: Context, global: List<String>, perDomain: Map<String, List<String>>) {
        val obj = JSONObject()
        val gArr = JSONArray().also { arr -> global.forEach { arr.put(it) } }
        obj.put("global", gArr)
        val dObj = JSONObject()
        perDomain.forEach { (domain, selectors) ->
            dObj.put(domain, JSONArray().also { arr -> selectors.forEach { arr.put(it) } })
        }
        obj.put("per_domain", dObj)
        File(filtersDir(context), "cosmetic_rules.json").writeText(obj.toString())
    }

    fun loadCosmetic(context: Context): CosmeticData {
        val file = File(filtersDir(context), "cosmetic_rules.json")
        if (!file.exists()) return loadBuiltinCosmetic(context)
        return runCatching {
            val obj = JSONObject(file.readText())
            val gArr = obj.optJSONArray("global") ?: JSONArray()
            val global = (0 until gArr.length()).map { gArr.getString(it) }
            val dObj = obj.optJSONObject("per_domain") ?: JSONObject()
            val perDomain = dObj.keys().asSequence().associate { domain ->
                val sArr = dObj.getJSONArray(domain)
                domain to (0 until sArr.length()).map { sArr.getString(it) }
            }
            CosmeticData(global, perDomain)
        }.getOrElse { loadBuiltinCosmetic(context) }
    }

    // ── 用户自定义白名单 ─────────────────────────────────────────────

    fun loadUserWhitelist(context: Context): HashSet<String> =
        loadDomains(context, "user_whitelist.txt")

    fun addToUserWhitelist(context: Context, domain: String) {
        val wl = loadUserWhitelist(context)
        wl.add(domain.lowercase().trim())
        saveDomains(context, "user_whitelist.txt", wl)
    }

    fun removeFromUserWhitelist(context: Context, domain: String) {
        val wl = loadUserWhitelist(context)
        wl.remove(domain.lowercase().trim())
        saveDomains(context, "user_whitelist.txt", wl)
    }

    // ── ETag / 更新元数据 ────────────────────────────────────────────

    data class SourceMeta(val etag: String = "", val lastUpdated: Long = 0L, val ruleCount: Int = 0)

    fun saveMeta(context: Context, sourceId: String, meta: SourceMeta) {
        val metaFile = File(filtersDir(context), "meta.json")
        val obj = runCatching { JSONObject(metaFile.readText()) }.getOrElse { JSONObject() }
        obj.put(sourceId, JSONObject().apply {
            put("etag", meta.etag)
            put("lastUpdated", meta.lastUpdated)
            put("ruleCount", meta.ruleCount)
        })
        metaFile.writeText(obj.toString())
    }

    fun loadMeta(context: Context, sourceId: String): SourceMeta {
        val metaFile = File(filtersDir(context), "meta.json")
        if (!metaFile.exists()) return SourceMeta()
        return runCatching {
            val obj = JSONObject(metaFile.readText()).getJSONObject(sourceId)
            SourceMeta(obj.getString("etag"), obj.getLong("lastUpdated"), obj.getInt("ruleCount"))
        }.getOrElse { SourceMeta() }
    }

    // ── 内置规则（从 raw 资源加载，作为 Fallback）────────────────────

    fun loadBuiltinDomains(context: Context): HashSet<String> {
        return runCatching {
            context.resources.openRawResource(R.raw.builtin_domains).bufferedReader()
                .lineSequence().filter { it.isNotBlank() && !it.startsWith("#") }
                .toHashSet()
        }.getOrElse { hashSetOf() }
    }

    private fun loadBuiltinCosmetic(context: Context): CosmeticData {
        val selectors = runCatching {
            context.resources.openRawResource(R.raw.builtin_cosmetic).bufferedReader()
                .lineSequence().filter { it.isNotBlank() && !it.startsWith("#") }
                .toList()
        }.getOrElse { emptyList() }
        return CosmeticData(selectors, emptyMap())
    }
}
```

---

## 6 — AdBlockEngine.kt

```kotlin
package com.example.freeavbrowser.adblocker

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

/**
 * 运行时广告拦截引擎
 * 线程安全，所有查询操作在任意线程可调用
 */
class AdBlockEngine(private val context: Context) {

    // ── 内存数据结构（启动时从文件加载）──────────────────────────────
    @Volatile private var domainBlacklist = HashSet<String>()
    @Volatile private var patternRules = emptyList<PatternRule>()
    @Volatile private var userWhitelist = HashSet<String>()
    @Volatile private var isLoaded = false

    // ── 统计计数器 ───────────────────────────────────────────────────
    val blockedCount = AtomicLong(0)
    val allowedCount = AtomicLong(0)

    /**
     * 应用启动时调用（Application.onCreate），异步加载，不阻塞主线程
     */
    fun loadAsync() {
        CoroutineScope(Dispatchers.IO).launch {
            reload()
        }
    }

    /**
     * 重新从文件加载规则（规则更新后调用）
     */
    suspend fun reload() {
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            val enabledSources = getEnabledSources()

            // 合并所有域名来源
            val merged = HashSet<String>()
            merged.addAll(FilterListStorage.loadBuiltinDomains(context))  // 内置兜底

            enabledSources.forEach { source ->
                when (source.format) {
                    FilterFormat.DNS_ADGUARD, FilterFormat.HOSTS -> {
                        merged.addAll(FilterListStorage.loadDomains(context, source.targetFile))
                    }
                    FilterFormat.ABP -> {
                        // ABP 源的域名部分也加载进来
                        merged.addAll(FilterListStorage.loadDomains(context, source.targetFile))
                    }
                }
            }

            domainBlacklist = merged
            patternRules = FilterListStorage.loadPatterns(context)
            userWhitelist = FilterListStorage.loadUserWhitelist(context)
            isLoaded = true

            android.util.Log.d("AdBlockEngine",
                "Loaded ${domainBlacklist.size} domains, ${patternRules.size} patterns")
        }
    }

    /**
     * 核心拦截判断
     * 
     * @param requestUrl  被拦截的请求 URL
     * @param pageUrl     当前页面 URL（用于同源检测和第三方检测）
     * @return true = 拦截此请求；false = 放行
     */
    fun shouldBlock(requestUrl: String, pageUrl: String?): Boolean {
        if (!isEnabled()) return false
        if (!isLoaded) return false  // 规则未加载完成，全部放行

        val uri = runCatching { Uri.parse(requestUrl) }.getOrNull() ?: return false
        val requestHost = uri.host?.lowercase() ?: return false

        // ════════════════════════════════════════════════════
        // 守卫层：这些情况永远不拦截
        // ════════════════════════════════════════════════════

        // G1：内容类型守卫（URL 后缀检测，在响应前拦截阶段无法获取 Content-Type）
        val urlPath = uri.path?.lowercase() ?: ""
        if (FilterListConfig.NEVER_BLOCK_EXTENSIONS.any { urlPath.endsWith(it) }) {
            allowedCount.incrementAndGet()
            return false
        }
        // 额外：含 .m3u8 或 .ts 的查询参数也放行
        if (requestUrl.contains(".m3u8") || requestUrl.contains(".ts?") || requestUrl.contains(".key")) {
            allowedCount.incrementAndGet()
            return false
        }

        // G2：同源守卫
        val pageHost = pageUrl?.let { runCatching { Uri.parse(it).host?.lowercase() }.getOrNull() }
        if (pageHost != null && requestHost == pageHost) {
            allowedCount.incrementAndGet()
            return false
        }

        // G3：硬编码白名单（JAV 站点 + CDN）
        if (isInHardcodedWhitelist(requestHost)) {
            allowedCount.incrementAndGet()
            return false
        }

        // G4：用户自定义白名单
        if (isInUserWhitelist(requestHost)) {
            allowedCount.incrementAndGet()
            return false
        }

        // ════════════════════════════════════════════════════
        // 拦截层：按效率从高到低
        // ════════════════════════════════════════════════════

        // B1：域名精确匹配（O(1)）
        if (isDomainBlocked(requestHost)) {
            blockedCount.incrementAndGet()
            return true
        }

        // B2：路径 / 关键字规则（线性扫描，规则数量少时可接受）
        val isThirdParty = pageHost != null && !requestHost.endsWith(".$pageHost") && requestHost != pageHost
        val blocked = patternRules.any { rule ->
            if (rule.thirdPartyOnly && !isThirdParty) return@any false
            if (rule.isWhitelist) return@any false
            requestUrl.contains(rule.keyword, ignoreCase = true)
        }
        if (blocked) {
            blockedCount.incrementAndGet()
            return true
        }

        allowedCount.incrementAndGet()
        return false
    }

    // ── 辅助方法 ─────────────────────────────────────────────────────

    private fun isDomainBlocked(host: String): Boolean {
        // 精确匹配
        if (domainBlacklist.contains(host)) return true
        // 子域名匹配：检查父域是否在黑名单
        var dot = host.indexOf('.')
        while (dot != -1) {
            val parent = host.substring(dot + 1)
            if (domainBlacklist.contains(parent)) return true
            dot = host.indexOf('.', dot + 1)
        }
        return false
    }

    private fun isInHardcodedWhitelist(host: String): Boolean {
        return FilterListConfig.HARDCODED_WHITELIST.any { wl ->
            host == wl || host.endsWith(".$wl")
        }
    }

    private fun isInUserWhitelist(host: String): Boolean {
        return userWhitelist.any { wl ->
            host == wl || host.endsWith(".$wl")
        }
    }

    private fun isEnabled(): Boolean =
        context.getSharedPreferences("adblocker_settings", Context.MODE_PRIVATE)
            .getBoolean("enabled", true)

    private fun getEnabledSources(): List<FilterSource> {
        val prefs = context.getSharedPreferences("adblocker_settings", Context.MODE_PRIVATE)
        return FilterListConfig.BUILTIN_SOURCES.filter { source ->
            prefs.getBoolean("source_${source.id}", source.enabledByDefault)
        }
    }

    // ── 用户白名单操作（供 UI 调用）──────────────────────────────────

    fun addToWhitelist(domain: String) {
        FilterListStorage.addToUserWhitelist(context, domain)
        userWhitelist = FilterListStorage.loadUserWhitelist(context)
    }

    fun removeFromWhitelist(domain: String) {
        FilterListStorage.removeFromUserWhitelist(context, domain)
        userWhitelist = FilterListStorage.loadUserWhitelist(context)
    }

    fun getUserWhitelist(): Set<String> = userWhitelist.toSet()

    // ── 统计重置 ─────────────────────────────────────────────────────

    fun resetStats() { blockedCount.set(0); allowedCount.set(0) }
}
```

---

## 7 — CosmeticInjector.kt

```kotlin
package com.example.freeavbrowser.adblocker

import android.content.Context
import android.webkit.WebView

/**
 * 在 WebView 页面加载完成后注入 CSS 和 JS
 * 用于隐藏广告 DOM 元素
 */
object CosmeticInjector {

    // ── 固定注入的 CSS 选择器（基于现有项目 + 常见广告容器）──────────
    private val STATIC_SELECTORS = listOf(
        // 通用广告容器
        ".ad", ".ads", ".ad-container", ".ad-wrapper", ".ad-block",
        ".advertisement", ".advert", ".banner-ad", ".ad-banner",
        "[class*='advertisement']", "[class*='adsense']", "[id*='google_ads']",
        // 弹窗
        ".popup-overlay", ".modal-overlay", "[class*='popup']",
        // iframe 广告
        "iframe[src*='exoclick']", "iframe[src*='magsrv']", "iframe[src*='tsyndicate']",
        // 常见追踪器按钮
        ".share-widget", "[class*='tracking-pixel']",
        // JAV 站点特有广告元素（需根据实际情况验证）
        ".col-ad-1", ".bg-ad", "[class*='banner-float']",
        // 固定位置广告
        "div[style*='position:fixed'][style*='z-index:9999']",
        "div[style*='position: fixed'][style*='z-index: 9999']"
    )

    /**
     * 在 onPageFinished 中调用
     * 注入 CSS 隐藏规则 + MutationObserver 监控动态插入
     */
    fun inject(webView: WebView, pageUrl: String?, context: Context) {
        val prefs = context.getSharedPreferences("adblocker_settings", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("cosmetic_enabled", true)) return

        val host = pageUrl?.let { android.net.Uri.parse(it).host?.lowercase() } ?: ""
        if (FilterListConfig.COSMETIC_BLACKLIST.contains(host)) return

        // 合并静态规则 + 动态规则（从 FilterListStorage 加载）
        val cosmeticData = FilterListStorage.loadCosmetic(context)
        val allSelectors = buildList {
            addAll(STATIC_SELECTORS)
            addAll(cosmeticData.global)
            // 站点专属规则：检查 host 及其父域
            cosmeticData.perDomain.forEach { (domain, selectors) ->
                if (host == domain || host.endsWith(".$domain")) addAll(selectors)
            }
        }.filter { it.isNotBlank() }.distinct()

        if (allSelectors.isEmpty()) return

        // 安全转义 CSS 选择器（避免 JS 注入）
        val safeSelectors = allSelectors
            .filter { !it.contains("'") && !it.contains("\\") }  // 跳过含危险字符的选择器
            .joinToString(", ") { it }

        if (safeSelectors.isBlank()) return

        // 转义成单引号安全的 JS 字符串
        val cssEscaped = safeSelectors.replace("\"", "\\\"")

        val js = """
            (function() {
                'use strict';
                var SELECTORS = "$cssEscaped";
                
                // 注入 <style> 标签
                function injectStyle() {
                    if (document.getElementById('jb-adblock-style')) return;
                    var style = document.createElement('style');
                    style.id = 'jb-adblock-style';
                    style.textContent = SELECTORS + ' { display: none !important; visibility: hidden !important; }';
                    if (document.head) {
                        document.head.appendChild(style);
                    } else if (document.documentElement) {
                        document.documentElement.appendChild(style);
                    }
                }
                
                // 隐藏单个元素
                function hideElement(el) {
                    try { el.style.setProperty('display', 'none', 'important'); } catch(e) {}
                }
                
                // 初次清理
                injectStyle();
                try {
                    document.querySelectorAll(SELECTORS).forEach(hideElement);
                } catch(e) {}
                
                // MutationObserver：监控动态插入的广告元素
                if (typeof MutationObserver !== 'undefined') {
                    var observer = new MutationObserver(function(mutations) {
                        mutations.forEach(function(mutation) {
                            mutation.addedNodes.forEach(function(node) {
                                if (node.nodeType !== 1) return;
                                try {
                                    if (node.matches && node.matches(SELECTORS)) hideElement(node);
                                    if (node.querySelectorAll) {
                                        node.querySelectorAll(SELECTORS).forEach(hideElement);
                                    }
                                } catch(e) {}
                            });
                        });
                    });
                    observer.observe(document.documentElement || document.body, {
                        childList: true,
                        subtree: true
                    });
                }
            })();
        """.trimIndent()

        webView.evaluateJavascript(js, null)
    }

    /**
     * 注入弹窗拦截器（在 onPageStarted 调用，比 onPageFinished 更早）
     * 阻止 window.open 弹窗
     */
    fun injectPopupBlocker(webView: WebView) {
        webView.evaluateJavascript("""
            (function() {
                // 阻止 window.open 弹窗
                window.open = function(url, target, features) {
                    console.log('[AdBlock] Blocked popup: ' + url);
                    return null;
                };
                // 阻止 alert/confirm 广告弹窗（可选，谨慎使用）
                // window.alert = function() {};
            })();
        """.trimIndent(), null)
    }
}
```

---

## 8 — FilterListSyncWorker.kt

```kotlin
package com.example.freeavbrowser.adblocker

import android.content.Context
import android.util.Log
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.util.concurrent.TimeUnit
import com.example.freeavbrowser.network.OkHttpClientProvider

class FilterListSyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    companion object {
        const val WORK_TAG = "filter_list_sync"
        private const val TAG = "FilterListSync"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val req = PeriodicWorkRequestBuilder<FilterListSyncWorker>(24, TimeUnit.HOURS)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                .addTag(WORK_TAG)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_TAG,
                ExistingPeriodicWorkPolicy.KEEP,
                req
            )
        }

        /** 强制立即同步（用户手动触发） */
        fun syncNow(context: Context) {
            val req = OneTimeWorkRequestBuilder<FilterListSyncWorker>()
                .setConstraints(Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED).build())
                .addTag("${WORK_TAG}_manual")
                .build()
            WorkManager.getInstance(context).enqueue(req)
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting filter list sync")
        var anyFailed = false

        val prefs = applicationContext.getSharedPreferences("adblocker_settings", Context.MODE_PRIVATE)
        val enabledSources = FilterListConfig.BUILTIN_SOURCES.filter { source ->
            prefs.getBoolean("source_${source.id}", source.enabledByDefault)
        }

        for (source in enabledSources) {
            val success = syncSource(source)
            if (!success) anyFailed = true
        }

        // 同步完成后重新加载引擎
        if (!anyFailed) {
            // 发送广播通知 MainActivity 重新加载规则
            val intent = android.content.Intent("com.example.freeavbrowser.FILTER_UPDATED")
            applicationContext.sendBroadcast(intent)
        }

        Log.d(TAG, "Sync complete. Failed sources: $anyFailed")
        if (anyFailed) Result.retry() else Result.success()
    }

    private suspend fun syncSource(source: FilterSource): Boolean {
        val client = OkHttpClientProvider.get(applicationContext)
        val meta = FilterListStorage.loadMeta(applicationContext, source.id)

        for (url in source.urls) {
            val success = runCatching {
                val reqBuilder = Request.Builder().url(url)
                // ETag 缓存：如果服务器没变化则跳过（节省流量）
                if (meta.etag.isNotBlank()) reqBuilder.header("If-None-Match", meta.etag)

                val response = client.newCall(reqBuilder.build()).execute()

                when (response.code) {
                    304 -> {
                        Log.d(TAG, "${source.id}: Not modified (ETag match)")
                        return@runCatching true
                    }
                    200 -> {
                        val body = response.body?.string() ?: return@runCatching false
                        val newEtag = response.header("ETag") ?: ""

                        // 解析并保存
                        processAndSave(source, body)

                        // 更新 meta
                        FilterListStorage.saveMeta(applicationContext, source.id,
                            FilterListStorage.SourceMeta(
                                etag = newEtag,
                                lastUpdated = System.currentTimeMillis(),
                                ruleCount = body.lines().count { it.isNotBlank() }
                            ))
                        Log.d(TAG, "${source.id}: Updated successfully")
                        true
                    }
                    else -> {
                        Log.w(TAG, "${source.id}: HTTP ${response.code} from $url")
                        false
                    }
                }
            }.getOrElse { e ->
                Log.w(TAG, "${source.id}: Failed from $url: ${e.message}")
                false
            }

            if (success) return true  // 当前 URL 成功，不尝试备用
        }
        return false  // 所有 URL 都失败
    }

    private fun processAndSave(source: FilterSource, content: String) {
        val parsed = FilterListParser.parse(content, source.format)

        when (source.category) {
            FilterCategory.PRIMARY_DOMAINS, FilterCategory.VIDEO_ADS, FilterCategory.MIXED -> {
                // 保存域名列表到文本文件
                FilterListStorage.saveDomains(applicationContext, source.targetFile, parsed.blockedDomains)
                // 如果有路径规则也保存（合并到全局 pattern 文件）
                if (parsed.patternRules.isNotEmpty()) {
                    val existing = FilterListStorage.loadPatterns(applicationContext).toMutableList()
                    existing.addAll(parsed.patternRules)
                    FilterListStorage.savePatterns(applicationContext, existing.distinctBy { it.keyword })
                }
            }
            FilterCategory.TRACKING -> {
                // 追踪规则以 pattern 方式存储
                FilterListStorage.savePatterns(applicationContext, parsed.patternRules)
            }
            FilterCategory.COSMETIC -> {
                // 保存 cosmetic 规则 + 域名规则
                FilterListStorage.saveDomains(applicationContext, source.targetFile, parsed.blockedDomains)
                // 合并 cosmetic 数据
                val existing = FilterListStorage.loadCosmetic(applicationContext)
                val mergedGlobal = (existing.global + parsed.cosmeticGlobal).distinct()
                val mergedPerDomain = (existing.perDomain.keys + parsed.cosmeticPerDomain.keys)
                    .associateWith { domain ->
                        ((existing.perDomain[domain] ?: emptyList()) +
                         (parsed.cosmeticPerDomain[domain] ?: emptyList())).distinct()
                    }
                FilterListStorage.saveCosmetic(applicationContext, mergedGlobal, mergedPerDomain)
            }
        }
    }
}
```

---

## 9 — MainActivity.kt 集成修改

在 `MainActivity.kt` 中进行以下修改（**不删除现有广告拦截逻辑，在其基础上扩展**）：

### 9.1 添加成员变量

```kotlin
// 在 MainActivity 类顶部添加
private lateinit var adBlockEngine: AdBlockEngine

// 接收规则更新广播
private val filterUpdateReceiver = object : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        lifecycleScope.launch(Dispatchers.IO) { adBlockEngine.reload() }
    }
}
```

### 9.2 修改 onCreate

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // 初始化广告拦截引擎（异步加载，不阻塞启动）
    adBlockEngine = AdBlockEngine(this)
    adBlockEngine.loadAsync()

    // 启动定期同步（每 24 小时）
    FilterListSyncWorker.schedule(this)

    // 注册规则更新广播
    registerReceiver(filterUpdateReceiver,
        android.content.IntentFilter("com.example.freeavbrowser.FILTER_UPDATED"))

    // ... 其余现有 onCreate 逻辑
}
```

### 9.3 修改 shouldInterceptRequest

用以下代码**完全替换**现有的 `shouldInterceptRequest` 实现：

```kotlin
override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
    val url = request?.url?.toString() ?: return super.shouldInterceptRequest(view, request)
    val currentPageUrl = view?.url

    // ── 新的引擎拦截（替换现有 shouldBlockUrl 调用）──────────────
    if (adBlockEngine.shouldBlock(url, currentPageUrl)) {
        return WebResourceResponse(
            "text/plain", "utf-8",
            java.io.ByteArrayInputStream(byteArrayOf())
        )
    }

    // ── 原有 CDN 突破 Header 注入逻辑（保留不变）────────────────
    // ... 原有 shouldInterceptRequest 中的 header 注入代码
    return super.shouldInterceptRequest(view, request)
}
```

### 9.4 修改 onPageFinished

在现有 `onPageFinished` 的**末尾**追加：

```kotlin
override fun onPageFinished(view: WebView?, url: String?) {
    super.onPageFinished(view, url)

    // ... 现有 DOM 清理 JS（保留）

    // ── 追加：Cosmetic 注入 ─────────────────────────────────────
    view?.let { CosmeticInjector.inject(it, url, this@MainActivity) }
}
```

### 9.5 修改 onPageStarted

追加弹窗拦截器（比 onPageFinished 更早注入）：

```kotlin
override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
    super.onPageStarted(view, url, favicon)
    view?.let { CosmeticInjector.injectPopupBlocker(it) }
}
```

### 9.6 长按菜单添加"加入白名单"

```kotlin
// 在 WebView 长按监听中
webView.setOnLongClickListener {
    val result = webView.hitTestResult
    if (result.type == WebView.HitTestResult.IMAGE_TYPE ||
        result.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) {
        // 提示用户是否将当前页面域名加入白名单
        val pageHost = Uri.parse(webView.url ?: "").host ?: return@setOnLongClickListener false
        MaterialAlertDialogBuilder(this)
            .setTitle("广告拦截")
            .setMessage("将 $pageHost 添加到白名单（不再拦截该站点）？")
            .setPositiveButton("添加") { _, _ ->
                adBlockEngine.addToWhitelist(pageHost)
                Toast.makeText(this, "已添加到白名单", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
        true
    } else false
}
```

---

## 10 — SettingsActivity 新增 UI

在 `SettingsActivity` 中新增「广告屏蔽」设置分组：

```xml
<!-- res/layout/settings_adblocker_card.xml -->
<com.google.android.material.card.MaterialCardView
    style="@style/Widget.Material3.CardView.Outlined"
    android:layout_margin="16dp">

    <LinearLayout android:orientation="vertical" android:padding="16dp">

        <!-- 总开关 -->
        <LinearLayout android:orientation="horizontal" android:gravity="center_vertical">
            <TextView style="@style/TextAppearance.Material3.TitleMedium"
                android:text="@string/adblock_title" android:layout_weight="1"/>
            <com.google.android.material.materialswitch.MaterialSwitch
                android:id="@+id/switch_adblock"/>
        </LinearLayout>

        <TextView style="@style/TextAppearance.Material3.BodySmall"
            android:text="@string/adblock_subtitle"
            android:paddingTop="4dp"/>

        <com.google.android.material.divider.MaterialDivider android:layout_marginVertical="12dp"/>

        <!-- 拦截统计 -->
        <TextView android:id="@+id/tv_block_stats"
            style="@style/TextAppearance.Material3.BodyMedium"
            android:text="本次会话：已拦截 0 次"/>

        <!-- Cosmetic 元素隐藏开关 -->
        <LinearLayout android:orientation="horizontal" android:gravity="center_vertical" android:layout_marginTop="8dp">
            <TextView style="@style/TextAppearance.Material3.BodyMedium"
                android:text="元素隐藏（CSS 过滤）" android:layout_weight="1"/>
            <com.google.android.material.materialswitch.MaterialSwitch
                android:id="@+id/switch_cosmetic"/>
        </LinearLayout>

        <com.google.android.material.divider.MaterialDivider android:layout_marginVertical="12dp"/>

        <!-- 规则订阅管理 -->
        <TextView style="@style/TextAppearance.Material3.TitleSmall" android:text="规则订阅源"/>
        <!-- 动态生成：为每个 FilterSource 生成一行 Switch -->
        <LinearLayout android:id="@+id/ll_filter_sources" android:orientation="vertical"/>

        <com.google.android.material.divider.MaterialDivider android:layout_marginVertical="12dp"/>

        <!-- 手动同步按钮 -->
        <com.google.android.material.button.MaterialButton
            android:id="@+id/btn_sync_now"
            style="@style/Widget.Material3.Button.OutlinedButton"
            android:text="立即同步规则"
            android:layout_marginTop="4dp"/>

        <!-- 上次更新时间 -->
        <TextView android:id="@+id/tv_last_updated"
            style="@style/TextAppearance.Material3.BodySmall"
            android:text="上次更新：未知"
            android:paddingTop="4dp"/>

        <!-- 用户白名单入口 -->
        <com.google.android.material.button.MaterialButton
            android:id="@+id/btn_whitelist"
            style="@style/Widget.Material3.Button.TextButton"
            android:text="管理白名单"
            android:layout_marginTop="4dp"/>

    </LinearLayout>
</com.google.android.material.card.MaterialCardView>
```

**SettingsActivity 中的动态 UI 逻辑：**

```kotlin
// 为每个规则源动态生成 MaterialSwitch
private fun buildFilterSourceToggles() {
    val container = binding.llFilterSources
    val prefs = getSharedPreferences("adblocker_settings", Context.MODE_PRIVATE)

    FilterListConfig.BUILTIN_SOURCES.forEach { source ->
        val rowView = layoutInflater.inflate(R.layout.item_filter_source, container, false)
        rowView.findViewById<TextView>(R.id.tv_source_name).text = source.displayName
        rowView.findViewById<TextView>(R.id.tv_source_desc).text = source.description
        val toggle = rowView.findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.switch_source)
        toggle.isChecked = prefs.getBoolean("source_${source.id}", source.enabledByDefault)
        toggle.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("source_${source.id}", checked).apply()
        }
        container.addView(rowView)
    }
}
```

---

## 11 — "不破坏内容"安全守卫清单

以下是确保拦截功能不影响正常内容的完整约束，Claude Code **必须在实现时验证每一条**：

### 硬性规则（绝不允许发生）

| 编号 | 规则 | 实现位置 |
|------|------|----------|
| S1 | `.m3u8`、`.ts`、`.key` 后缀 URL **永不拦截** | `AdBlockEngine.shouldBlock()` G1 守卫 |
| S2 | 与当前页面同域的请求**永不拦截** | `AdBlockEngine.shouldBlock()` G2 守卫 |
| S3 | `HARDCODED_WHITELIST` 中的域名**永不拦截** | `AdBlockEngine.shouldBlock()` G3 守卫 |
| S4 | 视频流 CDN（cloudfront/akamai/fastly）**永不拦截** | `HARDCODED_WHITELIST` 配置 |
| S5 | CSS 注入只在 `document.head` 可访问时执行 | `CosmeticInjector.inject()` |
| S6 | Cosmetic 选择器不包含 `<` 或 `javascript:` | `FilterListParser.isValidCssSelector()` |
| S7 | 规则未加载完成时**全部放行** | `AdBlockEngine.shouldBlock()` `isLoaded` 检查 |

### 软性规则（建议实现）

| 编号 | 规则 | 建议 |
|------|------|------|
| S8 | 用户可以一键将当前域名加入白名单 | 长按菜单实现 |
| S9 | 统计被拦截的请求数量，供用户参考 | `blockedCount` AtomicLong |
| S10 | EasyPrivacy（路径规则）默认关闭，避免误杀 | `enabledByDefault = false` |
| S11 | 完整版 DNS 规则（15 万条）默认关闭 | `enabledByDefault = false` |
| S12 | ABP 中 `$domain=` 复杂 option 规则跳过 | `FilterListParser.parseAbp()` 中过滤 |

---

## 12 — 内置精简规则（Fallback）

`app/src/main/res/raw/builtin_domains.txt` — 约 1000 条高频广告域名，无需联网即可生效：

```
# javbrowser builtin ad domains (fallback)
# 来源：exoclick、magsrv 等高频 JAV 站广告服务商
# 格式：每行一个域名

exoclick.com
magsrv.com
tsyndicate.com
trafficjunky.net
justfor.fans
imgspice.com
adspyglass.com
adnium.com
juicyads.com
trafficholder.com
ero-advertising.com
ads.trafficjunky.net
syndication.exoclick.com
cdn3.exoclick.com
fp3.exoclick.com
doubleclick.net
googlesyndication.com
pagead2.googlesyndication.com
adservice.google.com
amazon-adsystem.com
static.ads-twitter.com
ads.linkedin.com
facebook.com
pixel.facebook.com
connect.facebook.net
analytics.facebook.com
hotjar.com
mouseflow.com
```

`app/src/main/res/raw/builtin_cosmetic.txt` — 内置 CSS 元素隐藏规则：

```
# javbrowser builtin cosmetic rules (fallback)
# 格式：每行一条 CSS 选择器

.ad-container
.advertisement
.ad-banner
[class*="advert"]
[id*="ad-banner"]
[id*="google_ads"]
.popup-ad
.floating-ad
div[id^="exo_"]
div[id^="tjs_"]
```
