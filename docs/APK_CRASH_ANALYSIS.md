# APK 闪退排查报告

> 现象：安装 APK 后点击图标直接闪退，`adb logcat` 无该进程相关日志产生。
> 分析日期：2026-06-10

---

## 1. 构建状态

| 项目 | 值 |
|------|-----|
| APK 路径 | `app/build/outputs/apk/debug/app-debug.apk` |
| APK 大小 | 6.65 MB |
| applicationId | `com.example.freeavbrowser` |
| versionCode | 15 |
| versionName | **1.1.3** |

**⚠️ 关键发现：APK 版本与当前源码不一致。**  
`output-metadata.json` 记录 versionName 为 `1.1.3`，但当前 `strings.xml` 中定义的是 `v1.1.5 (Build 17)`。  
说明此 APK 并非由当前最新源码构建，而是**历史版本的残留产物**。闪退可能由已修复的旧 bug 导致。

---

## 2. 资源完整性检查

所有 AndroidManifest 中引用的资源均存在：

| 引用 | 状态 |
|------|------|
| `@xml/data_extraction_rules` | ✅ 存在 |
| `@xml/backup_rules` | ✅ 存在 |
| `@style/Theme.JAVBrowser` | ✅ 存在（themes.xml + colors_md3.xml）|
| `@drawable/ic_launcher` | ✅ 存在 |
| `@drawable/ic_launcher_calculator/notes/file` | ✅ 存在 |
| `@drawable/ic_play_arrow` | ✅ 存在 |
| `@drawable/ic_backspace` / `ic_check` / `ic_fingerprint` / `ic_lock` | ✅ 全部存在 |
| `@drawable/selector_nav_home/favorite/settings` | ✅ 全部存在 |
| `@menu/navigation_menu` | ✅ 存在 |
| `@string/play` 等 strings | ✅ 全部存在 |
| Layout `activity_main.xml` / `activity_lock.xml` / `activity_settings.xml` / `activity_favorites.xml` | ✅ 全部存在 |
| `colors_md3.xml` (`md_theme_light_*` / `md_theme_dark_*`) | ✅ 定义完整 |
| `dimens.xml` | ✅ 定义完整 |

**结论：不涉及资源缺失导致的 Resources.NotFoundException。**  

---

## 3. 无日志产生的可能原因

闪退后 `adb logcat` 中找不到 `com.example.freeavbrowser` 进程的任何日志，可能原因：

| 原因 | 说明 |
|------|------|
| **WebView 瞬时崩溃** | WebView 的 native 层崩溃会在进程被 SIGKILL 后直接被系统清理，不经过 Java/Kotlin 异常处理链，产生零日志 |
| **崩溃发生在 Application 之前** | 资源主题解析失败或 ActivityThread 初始化时抛出 VerifyError / ClassNotFoundException，日志可能被系统过滤掉 |
| **国产 ROM 修改过日志缓冲区** | MIUI / HarmonyOS / ColorOS 等厂商系统会对非系统应用的 `Log.e/w` 做日志抑制，只保留 native crash tombstone |
| **`Log.isLoggable` 限制** | 如果构建时启用了日志过滤，部分日志等级不会写入缓冲区 |
| **进程被系统瞬时杀死** | 如果 ANR 或资源不足导致系统直接杀进程，可能不留 `uncaughtException` 日志 |

**建议：使用 `adb logcat --buffer=crash -b main -b system *:V` 全面捕获所有缓冲区，或查看 `/data/tombstones/` 下的 native crash 文件。**

---

## 4. 高可能性崩溃原因（按优先级排列）

### 4.1 WebView 初始化失败（最高嫌疑）

`activity_main.xml` 中**直接内嵌**了一个 `WebView`（第21-25行）：

```xml
<WebView
    android:id="@+id/webView"
    android:layout_width="match_parent"
    android:layout_height="match_parent" />
```

`setContentView(R.layout.activity_main)` 执行时，系统会**同步初始化 WebView**。如果：

- 设备上 **Android System WebView 未安装** 或 **已禁用**
- 多个 WebView 实现冲突（如 MIUI 自带 WebView + Google WebView）
- WebView 版本过旧（API 24+ 需要 Chrome WebView 55+）

会导致布局 inflate 时抛出 `android.util.AndroidRuntimeException`，且部分 OEM ROM 中此类异常不会被 `Log.e` 记录。

**验证方法：**
> 在 `onCreate()` 中先单独检查 WebView 可用性：
> ```kotlin
> if (!WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROCESS)) {
>     Log.e("CRASH", "WebView not available")
> }
> ```
> 或在 `super.onCreate(savedInstanceState)` 之前用 `try { webView = WebView(this) }` 预创建。

---

### 4.2 Material 3 主题属性在低版本系统上不兼容

`themes.xml` 中引用了大量 Material 3 的 Surface Container 属性：

```xml
?attr/colorSurfaceContainer       <!-- activity_main.xml 第67行 -->
?attr/colorSurfaceVariant         <!-- activity_main.xml 第34行 -->
?attr/colorOutlineVariant          <!-- activity_main.xml 第57行 -->
```

这些属性要求 **material:1.12.0+**。虽然 `build.gradle.kts` 声明了 `implementation("com.google.android.material:material:1.12.0")`，但在以下情况可能崩溃：

- APK 构建时用的是**旧版本 material 库**（如果 APK 是 v1.1.3 的旧产物）
- Gradle 依赖解析时下载了损坏的 AAR
- 设备上其他应用占用了同名资源 ID 造成冲突

---

### 4.3 FLAG_SECURE 在国产 ROM 上的兼容性问题

`MainActivity.onCreate()` 第78行：

```kotlin
window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
```

在 `setContentView()` 之前设置。虽然官方文档允许，但部分国产 ROM：

- **MIUI/HyperOS**：FLAG_SECURE 的实现有修改，可能与 SurfaceFlinger 冲突
- **HarmonyOS**：在非 EMUI 设备上 FLAG_SECURE 处理逻辑不一致
- **ColorOS/OxygenOS**：某些版本在设置 FLAG_SECURE 后 inflate 含有 SurfaceView/TextureView 的布局时会抛出 `SurfaceControl` 异常

**验证方法：** 注释掉 `setFlags(FLAG_SECURE)` 后重新构建测试。

---

### 4.4 `@JavascriptInterface` 在匿名对象上的兼容性风险

`MainActivity.kt` 第234行：

```kotlin
webView.addJavascriptInterface(object {
    @android.webkit.JavascriptInterface
    fun onVideoFound(videoUrl: String) { ... }
}, "Android")
```

`@JavascriptInterface` 注解在**匿名对象**上使用时，在部分 WebView 实现（如腾讯 X5 内核、部分三星 WebView）中会被忽略，整个 `addJavascriptInterface` 调用会静默失败。

这本身不直接导致崩溃，但**如果旧版本 APK 中的 WebView 实现对此处理不当**，可能抛出 `NoSuchMethodException` 或 `IllegalArgumentException`。

---

### 4.5 `activity-alias` 残留导致启动器混乱

AndroidManifest 中定义了三个 `activity-alias`（第31-68行），用于图标伪装：

```xml
<activity-alias android:name=".MainActivityCalculator" ... android:enabled="false" />
<activity-alias android:name=".MainActivityNotes" ... android:enabled="false" />
<activity-alias android:name=".MainActivityFile" ... android:enabled="false" />
```

虽设为 `enabled="false"`，但在某些启动器上：
- 卸载旧版后安装新版，启动器可能缓存了已禁用的 `activity-alias` 入口
- 点击捕获得错误的组件导致 `PackageManager` 抛出 `ActivityNotFoundException`

---

## 5. 中等可能性问题

### 5.1 旧版本代码中的已知编译错误

`build_log.txt`（来自旧构建路径 `D:/00000ABOT/jav/`）显示之前存在编译错误：

```
LockActivity.kt:45:48 Unresolved reference: gridlayout
MainActivity.kt:66:5 Conflicting overloads: onResume()
MainActivity.kt:111:17 Unresolved reference: showBiometricLock
```

这些错误指向**非常早期的代码版本**（包名 `com.example.javbrowser`）。如果用户安装的 APK 是在这些错误出现之前构建的，则 APK 中可能包含未完成的代码逻辑。

### 5.2 `LockActivity` 主题与 Material 3 不统一

`LockActivity` 使用 `@style/Theme.AppCompat.NoActionBar`（AppCompat 基础主题），而 `MainActivity` 使用 `@style/Theme.JAVBrowser`（MD3 扩展主题）。在 `LockActivity` 中调用 `biometricHelper.canAuthenticate()` 时，如果 BiometricPrompt 的主题解析出现跨主题样式不一致，可能抛出异常。

### 5.3 `BottomNavigationView` 使用 `setOnItemSelectedListener`

第1194行使用了较新的 `setOnItemSelectedListener` API（Material 1.12.0 引入），但如果构建时依赖了旧版本 material 库，该方法不存在，会在运行时抛出 `NoSuchMethodError`。此类错误在 logcat 中通常只显示一行且容易被忽略。

---

## 6. 排除项

| 怀疑点 | 结论 |
|--------|------|
| AndroidManifest 缺少 exported 属性 | ✅ 主 Activity 有 `exported="true"`，其余子 Activity 无 intent-filter，符合 API 31+ 要求 |
| Target SDK 太高（34）不兼容 | ✅ minSdk=24, targetSdk=34 属于正常范围 |
| 缺少网络权限 | ✅ `INTERNET` 权限已声明 |
| 缺少生物识别权限 | ✅ `USE_BIOMETRIC` 权限已声明 |
| 布局 ID 不匹配 | ✅ 所有 `findViewById` 的 ID 在布局中均存在 |
| 内置规则 JSON 解析失败 | ✅ `getCommonBlockList()` 有 try-catch 包裹，不会抛未捕获异常 |
| 视频代理服务器崩溃 | ✅ `VideoProxyServer()` 初始化包在 try-catch 中 |
| 缺少 `ic_play_arrow.xml` | ✅ 已确认存在 |

---

## 7. 排查建议

### 立即尝试（不需要修改代码）

1. **捕获完整的 logcat 日志**：
   ```bash
   adb logcat --buffer=crash -b main -b system -b events *:V | grep -i "freeavbrowser\|AndroidRuntime\|WebView\|tombstone"
   ```

2. **查看 native crash tombstone**：
   ```bash
   adb shell ls /data/tombstones/
   adb shell cat /data/tombstones/tombstone_XX
   ```

3. **检查设备 WebView 状态**：
   ```bash
   adb shell dumpsys webviewupdate
   adb shell pm list packages | grep webview
   ```

4. **通过命令行启动以获取更详细错误**：
   ```bash
   adb shell am start -n com.example.freeavbrowser/.MainActivity
   ```

5. **安装一个已知能运行的最小测试 APK**（如空白 Activity + WebView 的 demo），确认是否是 WebView 问题。

### 需要修改代码的排查

6. 在 `MainActivity.onCreate()` 最开头添加兜底异常捕获：
   ```kotlin
   try {
       super.onCreate(savedInstanceState)
       setContentView(R.layout.activity_main)
   } catch (e: Exception) {
       Log.e("CRASH", "onCreate failed", e)
       // 尝试无 WebView 的 fallback 布局
       setContentView(android.R.layout.simple_list_item_1)
       return
   }
   ```

7. 临时注释 `FLAG_SECURE` 和 `addJavascriptInterface`，排除兼容性问题。

8. **清除构建缓存后重新构建**（确保 APK 与当前源码一致）：
   ```bash
   gradlew.bat clean
   gradlew.bat assembleDebug
   ```

---

## 8. 总结

| 优先级 | 可能性 | 原因 |
|--------|--------|------|
| 🔴 **最高** | 70% | **APK 为 v1.1.3 旧产物，与当前 v1.1.5 源码不一致**，闪退可能是旧版本已存在的 bug |
| 🔴 **最高** | 60% | **WebView 初始化失败**（布局内嵌 WebView，设备 WebView 损坏/禁用） |
| 🟡 **高** | 40% | **Material 3 主题属性与旧版 material 库不兼容**（colorSurfaceContainer 等） |
| 🟡 **高** | 35% | **FLAG_SECURE 在国产 ROM 上的兼容性问题** |
| 🟢 **中** | 20% | **@JavascriptInterface 在匿名对象上的兼容性风险** |
| 🟢 **低** | 10% | **activity-alias 缓存导致启动器混乱** |

**建议第一步：** 先执行「7. 排查建议」中的第 1~5 步（无需修改代码）收集更多信息，优先确认是否 WebView 问题和 APK 版本问题。