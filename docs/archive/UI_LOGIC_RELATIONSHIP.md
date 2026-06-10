# JAV Browser 前端界面逻辑关系文档

## 目录

1. [界面布局总览](#1-界面布局总览)
2. [顶部工具栏（Toolbar）按钮详解](#2-顶部工具栏toolbar按钮详解)
3. [底部导航栏（BottomNavigationView）详解](#3-底部导航栏bottomnavigationview详解)
4. [图标伪装系统详解](#4-图标伪装系统详解)
5. [四大循环逻辑结构](#5-四大循环逻辑结构)
6. [Activity跳转关系矩阵](#6-activity跳转关系矩阵)
7. [按钮与功能对照表](#7-按钮与功能对照表)

---

## 1. 界面布局总览

### 1.1 布局层次结构

```
┌─────────────────────────────────────────┐
│  [状态栏] 系统状态显示                     │
├─────────────────────────────────────────┤
│  ┌─────┐  ♡  [Space]  [播放] [设置]      │  ← Toolbar (顶部)
│  └─────┘                                 │
├─────────────────────────────────────────┤
│                                         │
│              WebView                     │  ← 主内容区
│         (网页渲染区域)                    │
│                                         │
├─────────────────────────────────────────┤
│  [进度条] 页面加载进度指示 (LinearProgress) │
├─────────────────────────────────────────┤
│  🏠    🔍    ❤️    ⚙️                    │  ← BottomNavigationView (底部)
│  首页   搜索   收藏   设置                │
└─────────────────────────────────────────┘
```

### 1.2 布局文件对应关系

| 布局文件 | 位置 | 用途 |
|---------|------|------|
| `activity_main.xml` | `app/src/main/res/layout/` | 主浏览器界面（含Toolbar + WebView + BottomNav） |
| `activity_settings.xml` | `app/src/main/res/layout/` | 设置页面（隐私、图标、广告规则） |
| `activity_favorites.xml` | `app/src/main/res/layout/` | 收藏夹页面 |
| `activity_lock.xml` | `app/src/main/res/layout/` | 应用锁验证页面 |
| `navigation_menu.xml` | `app/src/main/res/menu/` | 底部导航菜单定义 |

---

## 2. 顶部工具栏（Toolbar）按钮详解

### 2.1 按钮位置与ID

```
Toolbar 内部水平布局（从左到右）：
┌─────────────────────────────────────────────────────────────┐
│ [Home按钮] [收藏按钮] [弹性空间] [播放按钮] [设置按钮(隐藏)] │
│  btn_home  btn_add_favorite   btn_play   btn_settings      │
└─────────────────────────────────────────────────────────────┘
```

| 按钮ID | 图标 | 位置 | 可见性 | 功能说明 |
|--------|------|------|--------|----------|
| `btn_home` | `ic_home` | Toolbar最左侧 | 始终可见 | 点击返回首页（加载LandingPage） |
| `btn_add_favorite` | `ic_favorite_border` | Home右侧 | 始终可见 | 点击收藏当前页面/取消收藏 |
| `btn_play` | `ic_play_arrow` | Toolbar右侧 | **动态显示** | 检测到视频时显示，点击启动外部播放器 |
| `btn_settings` | `ic_settings` | Toolbar最右 | `gone`（隐藏） | 当前未使用，功能已移至底部导航 |
| `btn_view_favorites` | `ic_collections` | - | `gone`（隐藏） | 预留，当前未使用 |

### 2.2 各按钮详细行为

#### 2.2.1 Home按钮（btn_home）

- **位置**: Toolbar最左侧
- **图标**: `ic_home`（房子图标）
- **代码位置**: `MainActivity.kt:1055-1058`
- **点击行为**: 调用 `loadLandingPage()` 重新加载首页HTML
- **首页内容**: 显示17个站点入口（4个JAV + 13个Hentai）+ 搜索框

**首页站点列表**:
```
JAV 视频站点:
├── MissAV      → navigateToUrl('${domainConfig.getMissAvBaseUrl()}')
├── Jable       → navigateToUrl('https://${domainConfig.getJableDomain()}/')
├── Rou.Video   → navigateToUrl('https://${domainConfig.getRouVideoDomain()}/home')
└── AvJoy       → navigateToUrl('https://${domainConfig.getAvJoyDomain()}/')

Hentai 动画站点:
├── Hanime.tv      ├── HentaiMama.io
├── HentaiHaven    ├── Xanimeporn.com
├── HentaiFreak    ├── KissHentai.net
├── Oppai.stream   ├── HentaiCity.com
├── WatchHentai    ├── HentaiUniverse.net
├── MuchoHentai    ├── AnimeIDHentai.com
└── Ohentai.org
```

#### 2.2.2 收藏按钮（btn_add_favorite）

- **位置**: Home按钮右侧
- **图标**: `ic_favorite_border`（空心爱心）
- **代码位置**: `MainActivity.kt:927-987`
- **状态切换**:
  - 未收藏: 显示 `♡`（空心）
  - 已收藏: 显示 `♥`（实心）
- **点击行为**:
  1. 获取当前WebView URL和标题
  2. 检查是否已在收藏列表中
  3. **如果已收藏**: 从收藏移除，显示"已从收藏移除"
  4. **如果未收藏**: 提取缩略图 → 保存到SharedPreferences → 显示"已加入收藏"

**缩略图提取优先级**:
```
1. video[poster] 属性
2. meta[property="og:image"]
3. meta[name="twitter:image"]
4. 页面第一个 img 标签
```

#### 2.2.3 播放按钮（btn_play）

- **位置**: Toolbar右侧（收藏按钮右侧）
- **图标**: `ic_play_arrow` + 文字"播放"
- **代码位置**: `MainActivity.kt:918-925`
- **可见性**: `View.GONE`（默认隐藏）→ `View.VISIBLE`（检测到视频时显示）
- **触发显示条件**:
  - WebView拦截到 `.m3u8` 请求（`shouldInterceptRequest`）
  - JS接口 `onVideoFound()` 被调用
- **点击行为**: 调用 `playVideo(url)` → 构建代理URL → 启动外部播放器Intent

---

## 3. 底部导航栏（BottomNavigationView）详解

### 3.1 导航项定义

**菜单文件**: `navigation_menu.xml`

```xml
<menu>
    <item android:id="@+id/nav_home"      android:icon="@drawable/ic_home"               android:title="@string/nav_home" />      <!-- 首页 -->
    <item android:id="@+id/nav_search"    android:icon="@drawable/ic_nav_search_unselected" android:title="@string/nav_search" />    <!-- 搜索 -->
    <item android:id="@+id/nav_favorite"  android:icon="@drawable/ic_favorite_border"     android:title="@string/nav_favorite" />  <!-- 收藏 -->
    <item android:id="@+id/nav_settings" android:icon="@drawable/ic_settings"            android:title="@string/nav_settings" />  <!-- 设置 -->
</menu>
```

### 3.2 导航项位置与行为

```
底部导航栏布局（从左到右，等宽分布）：
┌─────────────────────────────────────────────────────────┐
│   🏠首页      🔍搜索      ❤️收藏      ⚙️设置           │
│  nav_home   nav_search  nav_favorite  nav_settings    │
└─────────────────────────────────────────────────────────┘
```

| 导航项ID | 图标 | 标题 | 位置 | 点击行为 | 目标 |
|---------|------|------|------|----------|------|
| `nav_home` | `ic_home` | "首页" | 第1项（最左） | `loadLandingPage()` | 当前Activity内刷新 |
| `nav_search` | `ic_nav_search_unselected` | "搜索" | 第2项 | `Toast.makeText(this, "搜索功能开发中", Toast.LENGTH_SHORT).show()` | 仅提示，无跳转 |
| `nav_favorite` | `ic_favorite_border` | "收藏" | 第3项 | `favoritesLauncher.launch(Intent(this, FavoritesActivity::class.java))` | **启动FavoritesActivity** |
| `nav_settings` | `ic_settings` | "设置" | 第4项（最右） | `startActivity(Intent(this, SettingsActivity::class.java))` | **启动SettingsActivity** |

**代码位置**: `MainActivity.kt:1285-1310`

---

## 4. 图标伪装系统详解

### 4.1 伪装选项与对应关系

**设置页面**: `SettingsActivity` → 图标伪装区域

```
┌─────────────────────────────────────────┐
│  图标伪装                                │
│  ○ 默认 (JAV Browser)                   │  ← radio_default    → ICON_DEFAULT
│  ○ 计算器 (Calculator)                   │  ← radio_calculator → ICON_CALCULATOR
│  ○ 备忘录 (Notes)                        │  ← radio_notes      → ICON_NOTES
│  ○ 文件管理器 (File Manager)             │  ← radio_file       → ICON_FILE
└─────────────────────────────────────────┘
```

### 4.2 伪装与ActivityAlias对应关系

**AndroidManifest.xml 定义**:

| 伪装选项 | RadioButton ID | 常量值 | ActivityAlias名称 | 显示名称 | 图标资源 | 启用状态 |
|---------|---------------|--------|-------------------|----------|----------|----------|
| 默认 | `radio_default` | `"default"` | 无（使用主Activity） | "JAV Browser" | `ic_launcher` | 始终启用 |
| 计算器 | `radio_calculator` | `"calculator"` | `.MainActivityCalculator` | "Calculator" | `ic_launcher_calculator` | 动态切换 |
| 备忘录 | `radio_notes` | `"notes"` | `.MainActivityNotes` | "Notes" | `ic_launcher_notes` | 动态切换 |
| 文件管理器 | `radio_file` | `"file"` | `.MainActivityFile` | "File Manager" | `ic_launcher_file` | 动态切换 |

### 4.3 图标切换流程

```
用户选择新图标
    ↓
显示确认对话框: "确定要将图标更换为「计算器」吗？"
    ↓
用户点击"确定"
    ↓
AppIconManager.switchIcon(newIcon)
    ↓
禁用当前启用的ActivityAlias (PackageManager.COMPONENT_ENABLED_STATE_DISABLED)
    ↓
启用新选择的ActivityAlias (PackageManager.COMPONENT_ENABLED_STATE_ENABLED)
    ↓
桌面显示新图标
    ↓
用户点击新图标 → 仍然进入 MainActivity（通过targetActivity关联）
    ↓
如果应用锁开启 → 先进入 LockActivity 验证
```

**代码位置**: `SettingsActivity.kt:106-118`, `AppIconManager.kt`

### 4.4 LockActivity中的伪装效果

当应用锁开启且图标伪装设置为非默认时:

| 伪装 | LockActivity标题 | LockActivity图标 | TaskDescription |
|------|-----------------|-----------------|-----------------|
| 默认 | "JAV Browser" | `ic_launcher` | "JAV Browser" + 默认图标 |
| 计算器 | "Calculator" | `ic_launcher_calculator` | "Calculator" + 计算器图标 |
| 备忘录 | "Notes" | `ic_launcher_notes` | "Notes" + 备忘录图标 |
| 文件管理器 | "File Manager" | `ic_launcher_file` | "File Manager" + 文件图标 |

**代码位置**: `LockActivity.kt:30-47`

---

## 5. 四大循环逻辑结构

### 5.1 循环1：首页浏览与视频播放循环

```
┌─────────────────────────────────────────────────────────────────┐
│  循环1: 首页浏览与视频播放循环                                     │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  [冷启动App] ──→ [LockActivity验证] ──→ 验证失败? ──→ [退出App]   │
│                      │                          │               │
│                      │                          否               │
│                      ↓                          ↓               │
│              [MainActivity] ←────────────────────┘               │
│                      │                                           │
│                      │ 点击底部导航 nav_home                      │
│                      ↓                                           │
│              [loadLandingPage]                                   │
│                      │                                           │
│                      │ 加载首页HTML                               │
│                      ↓                                           │
│              [首页站点列表]                                        │
│                      │                                           │
│                      │ 点击站点卡片                                 │
│                      ↓                                           │
│              [WebView.loadUrl]                                   │
│                      │                                           │
│                      │ 页面加载 + 广告拦截                        │
│                      ↓                                           │
│              [shouldInterceptRequest]                            │
│                      │                                           │
│                      │ 检测到.m3u8?                               │
│                      ↓                                           │
│              [btn_play 显示]                                     │
│                      │                                           │
│                      │ 点击播放按钮                                │
│                      ↓                                           │
│              [playVideo(url)]                                    │
│                      │                                           │
│                      │ 构建代理URL                                │
│                      ↓                                           │
│              [外部播放器 Intent.ACTION_VIEW]                      │
│                      │                                           │
│                      │ 播放完成/返回                              │
│                      ↓                                           │
│              [返回MainActivity] ─────────────────────────────────→│
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

**Toolbar按钮在此循环中的作用**:
- `btn_home`: 中断当前浏览，返回首页站点列表
- `btn_add_favorite`: 将当前浏览的页面加入收藏
- `btn_play`: 触发视频播放（仅检测到视频时可见）

### 5.2 循环2：收藏夹循环

```
┌─────────────────────────────────────────────────────────────────┐
│  循环2: 收藏夹循环                                                │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  [MainActivity浏览中]                                             │
│          │                                                      │
│          │ 点击 Toolbar btn_add_favorite (♡ → ♥)                 │
│          ↓                                                      │
│  [favoritesManager.addFavorite]                                   │
│          │                                                      │
│          │ WebView.capturePicture → Base64编码                    │
│          ↓                                                      │
│  [SharedPreferences JSON存储]                                   │
│          │                                                      │
│          │ 点击底部导航 nav_favorite                               │
│          ↓                                                      │
│  [favoritesLauncher.launch] → [FavoritesActivity]               │
│          │                                                      │
│          │ 显示收藏列表 (RecyclerView + Glide加载缩略图)           │
│          ↓                                                      │
│  [点击收藏项]                                                     │
│          │                                                      │
│          │ setResult(RESULT_OK, Intent.putExtra("url", url))     │
│          ↓                                                      │
│  [返回MainActivity]                                               │
│          │                                                      │
│          │ favoritesLauncher回调: webView.loadUrl(url)            │
│          ↓                                                      │
│  [MainActivity加载收藏URL] ─────────────────────────────────────→│
│                                                                 │
│  [FavoritesActivity子功能]:                                        │
│  ├── 搜索过滤 (Toolbar搜索框)                                    │
│  ├── 多选删除                                                    │
│  ├── 导入JSON (从剪贴板)                                         │
│  └── 导出JSON (复制到剪贴板)                                     │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 5.3 循环3：隐私设置循环

```
┌─────────────────────────────────────────────────────────────────┐
│  循环3: 隐私设置循环                                               │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  [MainActivity]                                                   │
│          │                                                      │
│          │ 点击底部导航 nav_settings                             │
│          ↓                                                      │
│  [startActivity → SettingsActivity]                               │
│          │                                                      │
│          ├──→ [应用锁开关] switch_lock                             │
│          │       │                                              │
│          │       │ 开启 → BiometricHelper.authenticate           │
│          │       │       │                                      │
│          │       │       成功 → PrivacySettings.isLockEnabled=true│
│          │       │       失败 → switch_lock.isChecked=false       │
│          │       │                                              │
│          │       ↓                                              │
│          │  [设置PIN码] btn_set_pin                               │
│          │       │                                              │
│          │       │ 显示对话框 → 输入4-6位数字                     │
│          │       │       │                                      │
│          │       │       有效 → PrivacySettings.pinCode=pin       │
│          │       │       无效 → Toast "PIN码需为4-6位"            │
│          │       │                                              │
│          │       ↓                                              │
│          │  [PrivacySettings SharedPreferences存储]              │
│          │                                                      │
│          ├──→ [图标伪装] radio_group_icon                          │
│          │       │                                              │
│          │       │ 选择新图标 → showIconChangeDialog              │
│          │       │       │                                      │
│          │       │       确认 → AppIconManager.switchIcon         │
│          │       │       │       │                              │
│          │       │       │       禁用旧别名 + 启用新别名           │
│          │       │       │       PrivacySettings.selectedIcon=新  │
│          │       │       │                                      │
│          │       │       取消 → loadSettings() 恢复选择          │
│          │       │                                              │
│          │       ↓                                              │
│          │  [桌面显示新图标]                                      │
│          │                                                      │
│          └──→ [广告规则管理]                                       │
│                  │                                              │
│                  ├── 云端URL输入框 (et_cloud_url)                 │
│                  ├── 从云端更新 (btn_update_from_cloud)            │
│                  ├── 导出规则 (btn_export_rules) → 剪贴板         │
│                  └── 导入规则 (btn_import_rules) → 从剪贴板       │
│                                                                 │
│  [隐私设置影响全局]:                                               │
│  ├── MainActivity.onResume(): 检查isLockEnabled → 启动LockActivity │
│  ├── MainActivity.onStop(): 重置isUnlocked=false                │
│  └── LockActivity: 读取currentAppLabel + currentIconResourceId    │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 5.4 循环4：广告规则更新循环

```
┌─────────────────────────────────────────────────────────────────┐
│  循环4: 广告规则更新循环                                           │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  [SettingsActivity]                                               │
│          │                                                      │
│          │ 点击"从云端更新"                                        │
│          ↓                                                      │
│  [AdFilterRules.updateRulesFromCloud(url)]                       │
│          │                                                      │
│          │ HTTP GET 请求 GitHub raw URL                          │
│          ↓                                                      │
│  [JSON解析]                                                       │
│          │                                                      │
│          ├── domains: 站点域名配置                                │
│          ├── rules.commonBlock: ~30个通用拦截域名                   │
│          ├── rules.networkBlock: 网络级拦截                       │
│          ├── rules.iframeBlock: iframe拦截                        │
│          ├── rules.linkBlock: 链接拦截                            │
│          └── rules.redirectBlock: 重定向拦截                        │
│          │                                                      │
│          ↓                                                      │
│  [保存到SharedPreferences]                                        │
│          │                                                      │
│          │ 同时更新cachedBlockList (内存缓存)                     │
│          ↓                                                      │
│  [MainActivity.shouldInterceptRequest]                           │
│          │                                                      │
│          │ cachedBlockList.any { url.contains(it) }              │
│          │       │                                              │
│          │       匹配 → 返回空WebResourceResponse (拦截)           │
│          │       不匹配 → super.shouldInterceptRequest            │
│          │                                                      │
│          ↓                                                      │
│  [页面加载完成 → 注入JS清理DOM广告]                                │
│          │                                                      │
│          │ 定时器每1秒执行一次清理                                  │
│          │                                                      │
│          └──→ 移除: iframe[id^="container-"]                     │
│              └──→ 移除: fixed + zIndex > 2000000000               │
│              └──→ 移除: .rmp-ad-container                         │
│              └──→ 移除: tscprts.com相关链接                       │
│              └──→ 移除: close-button广告                          │
│              └──→ 移除: dialog overlays                           │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## 6. Activity跳转关系矩阵

### 6.1 跳转关系总表

| 从 | 到 | 触发方式 | 传参 | 返回处理 |
|----|----|---------|------|---------|
| **桌面图标** | MainActivity | 点击图标启动 | 无 | 无 |
| **桌面图标** | LockActivity | 冷启动 + 应用锁开启 | 无 | RESULT_OK → MainActivity / 失败 → 退出 |
| **MainActivity** | LockActivity | `lockLauncher.launch()` | 无 | RESULT_OK → 设置isUnlocked=true |
| **MainActivity** | FavoritesActivity | `favoritesLauncher.launch()` | 无 | RESULT_OK → 获取"url" → webView.loadUrl(url) |
| **MainActivity** | SettingsActivity | `startActivity()` | 无 | 无返回处理（单向） |
| **MainActivity** | 外部播放器 | `Intent.ACTION_VIEW` | 视频URL | 用户手动返回 |
| **FavoritesActivity** | MainActivity | `setResult(RESULT_OK)` | Intent.putExtra("url", url) | 自动返回 |
| **SettingsActivity** | 无 | `finish()` | 无 | 返回MainActivity |
| **LockActivity** | MainActivity | `setResult(RESULT_OK)` + `finish()` | 无 | 自动返回 |

### 6.2 跳转流程图

```
┌─────────────────────────────────────────────────────────────────┐
│                      Activity 跳转关系图                          │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│   [桌面]                                                         │
│      │                                                          │
│      ├──→ 点击默认图标 ──→ [MainActivity]                         │
│      │                              │                           │
│      ├──→ 点击计算器图标 ──→ [MainActivity]（通过Alias）            │
│      │                              │                           │
│      ├──→ 点击备忘录图标 ──→ [MainActivity]（通过Alias）            │
│      │                              │                           │
│      └──→ 点击文件管理器 ──→ [MainActivity]（通过Alias）            │
│                                     │                           │
│                    ┌────────────────┼────────────────┐          │
│                    │                │                │          │
│                    ↓                ↓                ↓          │
│            [LockActivity]   [FavoritesActivity]  [SettingsActivity]│
│                    │                │                │          │
│                    │                │                │          │
│                    ↓ RESULT_OK       ↓ RESULT_OK     ↓ finish()  │
│                    │  (传isUnlocked) │  (传url)      │          │
│                    └──────→ [MainActivity] ←───────┘          │
│                                     │                           │
│                                     ↓                           │
│                            [外部播放器]                           │
│                                     │                           │
│                                     └──────→ 用户返回 ──→ [MainActivity]│
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## 7. 按钮与功能对照表

### 7.1 MainActivity 所有按钮

| 按钮ID | 所在位置 | 图标/文字 | 默认可见性 | 点击功能 | 目标 |
|--------|---------|----------|-----------|---------|------|
| `btn_home` | Toolbar左侧 | 🏠 Home | 可见 | `loadLandingPage()` | 内部 |
| `btn_add_favorite` | Toolbar左中 | ♡/♥ | 可见 | 添加/移除收藏 | FavoritesManager |
| `btn_play` | Toolbar右侧 | ▶ 播放 | **GONE** | `playVideo()` | 外部播放器 |
| `btn_settings` | Toolbar最右 | ⚙️ | **GONE** | 无（已废弃） | - |
| `btn_view_favorites` | Toolbar | 📑 | **GONE** | 无（已废弃） | - |
| `nav_home` | BottomNav第1项 | 🏠 首页 | 可见 | `loadLandingPage()` | 内部 |
| `nav_search` | BottomNav第2项 | 🔍 搜索 | 可见 | Toast "开发中" | 无 |
| `nav_favorite` | BottomNav第3项 | ❤️ 收藏 | 可见 | 启动FavoritesActivity | FavoritesActivity |
| `nav_settings` | BottomNav第4项 | ⚙️ 设置 | 可见 | 启动SettingsActivity | SettingsActivity |

### 7.2 SettingsActivity 所有按钮/控件

| 控件ID | 类型 | 位置 | 功能 | 影响 |
|--------|------|------|------|------|
| `switch_lock` | SwitchMaterial | 应用锁卡片 | 开启/关闭生物识别锁 | PrivacySettings.isLockEnabled |
| `btn_set_pin` | MaterialButton | 应用锁卡片 | 设置4-6位PIN码 | PrivacySettings.pinCode |
| `radio_default` | MaterialRadioButton | 图标伪装卡片 | 选择默认图标 | 无Alias变更 |
| `radio_calculator` | MaterialRadioButton | 图标伪装卡片 | 选择计算器图标 | 启用MainActivityCalculator Alias |
| `radio_notes` | MaterialRadioButton | 图标伪装卡片 | 选择备忘录图标 | 启用MainActivityNotes Alias |
| `radio_file` | MaterialRadioButton | 图标伪装卡片 | 选择文件管理器图标 | 启用MainActivityFile Alias |
| `et_cloud_url` | TextInputEditText | 广告规则卡片 | 输入云端规则URL | AdFilterRules.cloudUrl |
| `btn_update_from_cloud` | MaterialButton | 广告规则卡片 | 从云端更新规则 | 更新AdFilterRules |
| `btn_export_rules` | MaterialButton | 广告规则卡片 | 导出规则到剪贴板 | 剪贴板 |
| `btn_import_rules` | MaterialButton | 广告规则卡片 | 从剪贴板导入规则 | AdFilterRules |
| `btn_back` | MaterialButton | 页面底部 | 返回MainActivity | finish() |

### 7.3 LockActivity 所有按钮

| 按钮ID | 类型 | 位置 | 功能 |
|--------|------|------|------|
| Grid数字按钮 (0-9) | Button | PIN键盘网格 | 输入PIN数字 |
| `btn_delete` | MaterialButton | PIN键盘 | 删除最后一位 |
| `btn_enter` | MaterialButton | PIN键盘 | 验证PIN |
| `btn_use_biometric` | Button | PIN键盘下方 | 启动生物识别验证 |

---

## 附录：关键代码位置索引

| 功能 | 文件 | 行号范围 |
|------|------|---------|
| 底部导航设置 | `MainActivity.kt` | 1285-1310 |
| Home按钮 | `MainActivity.kt` | 1055-1058 |
| 收藏按钮 | `MainActivity.kt` | 927-987 |
| 播放按钮 | `MainActivity.kt` | 918-925 |
| 设置按钮 | `MainActivity.kt` | 1278-1283 |
| 视频播放 | `MainActivity.kt` | 1003-1020 |
| 广告拦截 | `MainActivity.kt` | 306-339 |
| 应用锁开关 | `SettingsActivity.kt` | 79-103 |
| 图标选择 | `SettingsActivity.kt` | 106-118 |
| PIN设置对话框 | `SettingsActivity.kt` | 132-164 |
| 图标切换对话框 | `SettingsActivity.kt` | 166-187 |
| 规则更新 | `SettingsActivity.kt` | 218-242 |
| LockActivity标题/图标 | `LockActivity.kt` | 30-47 |
| PIN验证 | `LockActivity.kt` | 137-153 |
| ActivityAlias | `AndroidManifest.xml` | 31-68 |

---

*文档版本: v1.0*  
*对应代码版本: JAV Browser v1.1.5*  
*生成日期: 2026-06-08*
