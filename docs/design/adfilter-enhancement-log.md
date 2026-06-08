# AdFilterRules 广告拦截增强记录

> **日期**: 2026-06-08
> **分支**: `feature/md3-ui-update`
> **提交**: `bef095f`

---

## 一、改动概述

在**不改动现有架构**的前提下，最大化广告拦截能力：

- 新增 5 个 EasyList 优质规则源
- 新增多云端 URL 支持
- 新增本地规则文件管理
- 新增规则合并引擎（并集策略）
- 新增元素隐藏规则（CSS 选择器）
- 新增内存缓存层
- 新增 `shouldBlock()` 拦截检查入口
- 新增 EasyList 规则解析（`||domain^`, `@@||domain^`, `##.class`, 通配符）
- 全面中文化（strings.xml + 所有 Activity）
- 修复 5 个架构问题

---

## 二、新增功能

### 2.1 EasyList 规则源

```kotlin
companion object {
    private val EASYLIST_SOURCES = listOf(
        "https://easylist.to/easylist/easylist.txt",
        "https://easylist.to/easylist/easyprivacy.txt",
        "https://easylist-downloads.adblockplus.org/easylistchina+easylist.txt",
        "https://raw.githubusercontent.com/217heidai/adblockfilters/main/rules/adblock_dns.txt",
        "https://raw.githubusercontent.com/GOODBYEADS/GOODBYEADS/master/blocklist.txt"
    )
}
```

| 规则源 | 说明 |
|--------|------|
| easylist.txt | EasyList 主列表（英文） |
| easyprivacy.txt | EasyList 隐私保护 |
| easylistchina+easylist.txt | 中国区域补充 |
| adblock_dns.txt | DNS 级别拦截 |
| GOODBYEADS | 综合广告拦截 |

### 2.2 多云端 URL 支持

新增方法：
- `getCloudUrls(): List<String>` — 获取所有云端规则 URL
- `setCloudUrls(urls: List<String>)` — 设置多个 URL
- `addCloudUrl(url: String)` — 添加单个 URL
- `updateRulesFromMultipleUrls(callback)` — 从多个 URL 合并更新

### 2.3 本地规则文件管理

新增方法：
- `addLocalRule(name, jsonContent)` — 添加本地规则
- `getLocalRules(): Map<String, String>` — 获取所有本地规则
- `removeLocalRule(name)` — 删除本地规则

### 2.4 规则合并引擎

```kotlin
private fun mergeRules(jsonList: List<String>): String
```

- 输入：多个 JSON 规则
- 策略：取并集（commonBlock + networkBlock + linkBlock + iframeBlock + redirectBlock）
- 优先级：本地 > 云端 > 默认
- 版本号：取最新版本

### 2.5 元素隐藏规则

```kotlin
fun addDefaultElementHideRules()
fun getElementHideRules(): List<String>
```

默认规则覆盖：
- `.ad`, `.ads`, `.adsbox`, `.advertisement`, `.ad-banner`, `.ad-container`
- `.banner-ad`, `.popup-ad`, `.google-ad`, `.adsbygoogle`
- `iframe[src*="ads"]`, `iframe[src*="doubleclick"]`
- `[class*="ad-"]`, `[id*="ad-"]`, `[class*="banner"]`, `[class*="popup"]`
- `.ytp-ad-module`, `.ytp-ad-overlay-container`

### 2.6 内存缓存层

```kotlin
private val blockRules = HashSet<String>()       // 域名精确匹配
private val blockPatterns = ArrayList<String>()  // URL 模式匹配
private val whiteList = HashSet<String>()        // 白名单
private val elementHideRules = ArrayList<String>() // 元素隐藏规则
```

### 2.7 拦截检查

```kotlin
fun shouldBlock(url: String, isThirdParty: Boolean = false): Boolean
```

检查顺序：
1. 白名单 → 放行
2. 域名精确匹配 → 拦截
3. URL 模式匹配 → 拦截
4. 第三方请求额外检查 → 拦截

### 2.8 EasyList 规则解析

支持的格式：
- `||domain.com^` → 域名精确匹配
- `@@||domain.com^` → 白名单
- `##.ad-class` → CSS 元素隐藏
- `simple-domain.com` → 简单域名
- `*wildcard*^` → 通配符 URL

---

## 三、修复的问题

### 问题 1: `##.ad-class` 规则被丢弃

**现象**: `parseRule()` 正确识别 `ELEMENT_HIDE` 类型，但 `parseJsonRules()` 中 `when` 分支没有处理该类型。

**修复**: 在 `parseJsonRules()` 中增加 `RuleType.ELEMENT_HIDE` 分支，将选择器存入 `elementHideRules`。

### 问题 2: `BLOCK` 类型只存 pattern 不存 domain

**现象**: `parseJsonRules()` 中 `RuleType.BLOCK` 只将 `rule.pattern` 存入 `blockPatterns`，忽略 `rule.domain`。导致 `shouldBlock()` 的域名精确匹配永远命中不了。

**修复**: 同时检查 `rule.domain` 和 `rule.pattern`，分别存入 `blockRules` 和 `blockPatterns`。

### 问题 3: `isThirdParty` 参数未使用

**现象**: `shouldBlock(url, isThirdParty)` 声明了参数但方法体内未使用。

**修复**: 增加第三方请求额外检查逻辑（域名包含模式匹配），预留扩展点。

### 问题 4: `getCommonBlockList()` 被移除

**现象**: `MainActivity.kt` 第 94 行和第 99 行调用 `getCommonBlockList()`，但新版本中移除了该方法。

**修复**: 恢复 `getCommonBlockList()` 作为相容介面，合并所有规则类型返回。

### 问题 5: `updateRulesFromExternalSources` 失败时清空规则

**现象**: 加载 EasyList 源时先清空现有规则，如果全部失败则规则被清空且无法恢复。

**修复**: 先构建新规则集（`newBlockRules`, `newBlockPatterns` 等），仅在有有效规则时才替换现有规则。

### 问题 6: `e.printStackTrace()` 编译警告

**现象**: 多处使用 `e.printStackTrace()`，产生编译警告。

**修复**: 全部替换为 `Log.e(TAG, message, e)`。

---

## 四、中文化改动

### strings.xml

所有字符串从英文改为中文：

| 类别 | 示例 |
|------|------|
| 主页 | `Home` → `首页`, `Add to favorites` → `添加收藏` |
| 设置 | `Privacy Settings` → `隐私设置`, `App Lock` → `应用锁` |
| 锁屏 | `App Locked` → `应用已锁定`, `Enter PIN` → `请输入 PIN 码` |
| 导航 | `Home` → `首页`, `Search` → `搜索`, `Favorites` → `收藏` |

### Activity 代码

- `MainActivity.kt`: Toast 消息、对话框文本、HTML 页面全部中文化
- `SettingsActivity.kt`: Toast 消息、对话框文本全部中文化
- `BiometricHelper.kt`: 生物识别提示文本中文化
- `FavoritesManager.kt`: 导入/导出提示文本中文化

### SettingsActivity 对话框

从 `AlertDialog.Builder` 改为 `MaterialAlertDialogBuilder`，符合 MD3 规范。

---

## 五、文件变更汇总

| 文件 | 操作 | 行数变化 |
|------|------|----------|
| `AdFilterRules.kt` | 增强 | +770, -175 |
| `MainActivity.kt` | 中文化 | ±50 |
| `SettingsActivity.kt` | 中文化 + MD3 对话框 | ±40 |
| `BiometricHelper.kt` | 中文化 | ±4 |
| `FavoritesManager.kt` | 中文化 | ±6 |
| `strings.xml` | 全面中文化 | ±50 |

---

## 六、架构保持不变的验证

| 原有接口 | 状态 |
|----------|------|
| `getNetworkBlockList()` | ✅ 保留 |
| `getLinkBlockList()` | ✅ 保留 |
| `getIframeBlockList()` | ✅ 保留 |
| `getRedirectBlockList()` | ✅ 保留 |
| `getCommonBlockList()` | ✅ 恢复 |
| `getDomains()` | ✅ 保留 |
| `updateRulesFromCloud()` | ✅ 保留 |
| `addRule()` / `removeRule()` | ✅ 保留 |
| `exportToJson()` / `importFromJson()` | ✅ 保留 |
| `getRulesStats()` | ✅ 保留 |
| `getVersion()` / `getLastUpdateTime()` | ✅ 保留 |
| `cloudUrl` getter/setter | ✅ 保留 |

---

*文档生成时间: 2026-06-08*
*操作人: opencode (AI assistant)*
