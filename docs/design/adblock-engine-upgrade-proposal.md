# 广告拦截引擎升级方案

> **版本**: v2.1
> **日期**: 2026-06-09
> **状态**: ✅ 已修复
> **基于**: 实际代码分析

---

## 一、当前实现问题分析

### 1.1 代码实际调用链

**MainActivity.kt:306-338** 中的拦截逻辑：

```kotlin
override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
    val url = request?.url.toString()
    val lowerUrl = url.lowercase()

    // ❌ 实际使用：简单的字符串包含匹配
    if (cachedBlockList.any { lowerUrl.contains(it) }) {
        return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream("".toByteArray()))
    }

    return super.shouldInterceptRequest(view, request)
}
```

**cachedBlockList 来源**（MainActivity.kt:94, 99）：

```kotlin
// 从 AdFilterRules.getCommonBlockList() 获取
cachedBlockList = adFilterRules.getCommonBlockList().toSet()
```

**getCommonBlockList() 实现**（AdFilterRules.kt:148-152）：

```kotlin
fun getCommonBlockList(): List<String> {
    return getRulesList(RuleType.NETWORK_BLOCK) + getRulesList(RuleType.LINK_BLOCK) +
           getRulesList(RuleType.IFRAME_BLOCK) + getRulesList(RuleType.REDIRECT_BLOCK) +
           blockRules.toList() + blockPatterns.toList()
}
```

### 1.2 发现的关键问题

| 问题 | 位置 | 影响 |
|------|------|------|
| **`shouldBlock()` 未被调用** | AdFilterRules.kt:155-175 | EasyList 规则（`blockRules`, `blockPatterns`）形同虚设 |
| **O(n) 线性匹配** | MainActivity.kt:318 | 每个请求遍历整个 cachedBlockList |
| **规则合并重复** | AdFilterRules.kt:149-151 | `getCommonBlockList()` 合并多个列表但可能有重复 |
| **EasyList 规则未生效** | MainActivity.kt:318 | 只用了 JSON 规则，EasyList 解析后的 `blockRules`/`blockPatterns` 没被使用 |

### 1.3 验证：shouldBlock 方法调用情况

```bash
# 搜索 shouldBlock 在 MainActivity 中的调用
grep -n "shouldBlock" MainActivity.kt
# 结果：No matches found
```

**结论**：`AdFilterRules.shouldBlock()` 方法实现了完整的拦截逻辑（白名单 + 域名精确匹配 + 模式匹配），但 MainActivity 根本没有调用它！

---

## 二、方案选择

基于实际代码问题，有两种修复路径：

### 方案 A：修复现有代码（推荐，最小改动）

**改动范围**：仅修改 `MainActivity.kt` 一处

**改动内容**：将 `cachedBlockList.any { lowerUrl.contains(it) }` 替换为 `adFilterRules.shouldBlock(url, isThirdParty = true)`

```kotlin
// 修改前（MainActivity.kt:317-320）
if (cachedBlockList.any { lowerUrl.contains(it) }) {
    return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream("".toByteArray()))
}

// 修改后
if (adFilterRules.shouldBlock(url, isThirdParty = true)) {
    return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream("".toByteArray()))
}
```

**效果**：
- ✅ EasyList 规则生效（`blockRules` + `blockPatterns`）
- ✅ 白名单生效
- ✅ 域名精确匹配（O(1) HashSet）
- ❌ 模式匹配仍是 O(n)

### 方案 B：集成 AdblockAndroid 引擎（大改动）

**改动范围**：
1. 添加 Gradle 依赖
2. 创建 Application 类
3. 重构 `MainActivity.kt` 拦截逻辑
4. 重构 `AdFilterRules.kt` 为适配层

**风险**：
- 引入第三方库维护风险
- APK 体积增加约 2-3MB
- 需要充分测试兼容性

---

## 三、推荐方案：先修复再优化

### 3.1 Phase 1：修复现有逻辑（1小时）

**目标**：让已有的 EasyList 规则生效

**修改文件**：`MainActivity.kt`

```kotlin
// 第 317-320 行
// 修改前
if (cachedBlockList.any { lowerUrl.contains(it) }) {
    return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream("".toByteArray()))
}

// 修改后
if (adFilterRules.shouldBlock(url, isThirdParty = true)) {
    return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream("".toByteArray()))
}
```

**可选优化**：移除不再需要的 `cachedBlockList` 变量（第 38、94、99、100 行）

### 3.2 Phase 2：性能优化（可选，后续迭代）

如果 Phase 1 后性能仍不满意，再考虑：

#### 选项 2.1：Bloom Filter 加速

在 `AdFilterRules.kt` 中添加：

```kotlin
// 添加依赖：implementation("com.google.guava:guava:31.1-android")
import com.google.common.hash.BloomFilter
import com.google.common.hash.Funnels

class AdFilterRules(private val context: Context) {
    // ... 现有代码 ...

    private val bloomFilter: BloomFilter<String> by lazy {
        BloomFilter.create(
            Funnels.stringFunnel(Charset.forName("UTF-8")),
            150_000,  // 规则容量
            0.01      // 误判率 1%
        ).also { bf -> blockRules.forEach { bf.put(it) } }
    }

    fun shouldBlock(url: String, isThirdParty: Boolean = false): Boolean {
        val uri = Uri.parse(url)
        val host = uri.host?.lowercase() ?: return false

        // 1. 白名单检查
        if (whiteList.contains(host)) return false

        // 2. Bloom Filter 快速排除
        if (!bloomFilter.mightContain(host)) {
            // Bloom Filter 说不在，那就一定不在（无假阴性）
            // 继续检查模式匹配
        } else {
            // Bloom Filter 说可能在，需要精确确认
            if (blockRules.contains(host)) return true
        }

        // 3. URL 模式匹配
        for (pattern in blockPatterns) {
            if (url.contains(pattern)) return true
        }

        return false
    }
}
```

#### 选项 2.2：集成 AdblockAndroid

如果自研优化仍不满足需求，再考虑集成第三方库。

---

## 四、验证测试

### 4.1 功能测试

**测试站点**：
- https://adblock-tester.com/ - 广告拦截测试
- https://canyoublockit.com/ - 拦截能力测试
- https://ads-blocker.com/testing/ - 广告检测

**测试步骤**：
1. 修改代码后重新编译
2. 访问测试站点
3. 对比修改前后的拦截效果

### 4.2 性能测试

**测试方法**：
```kotlin
// 在 shouldInterceptRequest 中添加耗时日志
override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
    val start = System.nanoTime()
    val url = request?.url.toString()

    val shouldBlock = adFilterRules.shouldBlock(url, isThirdParty = true)

    val elapsed = System.nanoTime() - start
    if (elapsed > 1_000_000) { // 超过 1ms 记录
        Log.d("AdBlock", "shouldBlock took ${elapsed / 1_000_000}ms for $url")
    }

    return if (shouldBlock) {
        WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream("".toByteArray()))
    } else {
        super.shouldInterceptRequest(view, request)
    }
}
```

---

## 五、结论

**当前最紧迫的问题**：`shouldBlock()` 方法未被调用，导致 EasyList 规则失效。

**✅ 已修复**：
1. 修改 `MainActivity.kt` 调用 `adFilterRules.shouldBlock()`
2. 移除不再需要的 `cachedBlockList` 变量

**修复效果**：
- ✅ EasyList 规则生效，拦截能力提升
- ✅ 白名单生效，减少误伤
- ✅ 域名精确匹配（HashSet O(1)）替代字符串包含匹配

**编译验证**：`gradlew.bat assembleDebug` 成功通过

---

## 附录：代码变更清单

### 必须修改

| 文件 | 行号 | 改动 |
|------|------|------|
| `MainActivity.kt` | 317-320 | 替换 `cachedBlockList.any` 为 `adFilterRules.shouldBlock` |

### 可选清理

| 文件 | 行号 | 改动 |
|------|------|------|
| `MainActivity.kt` | 38 | 移除 `cachedBlockList` 变量声明 |
| `MainActivity.kt` | 94, 99, 100 | 移除 `cachedBlockList` 赋值 |

---

**更新记录**：
- 2026-06-09: 基于实际代码分析，发现 `shouldBlock()` 未被调用的关键问题，提出最小改动修复方案
