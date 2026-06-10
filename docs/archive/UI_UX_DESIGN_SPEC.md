# JavBrowser · UI/UX 设计规范文档
**版本**: v2.0 · **日期**: 2026-06-08 · **状态**: ✅ 正式版

> 本文档综合官方 Material Design 3 规范与项目实际重构记录，提供可直接落地的设计与开发指导。
> 适用对象：UI 设计师 · Android 开发者 · QA 测试人员

---

## 目录

1. [设计概述](#1-设计概述)
2. [信息架构](#2-信息架构)
3. [色彩系统](#3-色彩系统)
4. [字体规范](#4-字体规范)
5. [间距与圆角](#5-间距与圆角)
6. [组件规范](#6-组件规范)
7. [页面设计规范](#7-页面设计规范)
8. [交互规范](#8-交互规范)
9. [动画规范](#9-动画规范)
10. [无障碍规范](#10-无障碍规范)
11. [开发实施指南](#11-开发实施指南)
12. [附录：文件清单](#12-附录文件清单)

---

## 1. 设计概述

### 1.1 设计目标

将原有"功能堆砌式"界面升级为**以用户任务为核心**的现代 Android 应用，达到 Google 官方应用的品质标准。

| 维度 | 升级前 | 升级后 |
|------|--------|--------|
| 设计语言 | Material 2 混杂旧控件 | Material Design 3（完整实现） |
| 颜色管理 | 页面各自硬编码 | 统一 Token 体系，主题自动切换 |
| 导航模式 | Activity 跳转碎片化 | 底部导航栏 + Fragment 连续体验 |
| 信息架构 | 开发者视角（功能分组） | 用户视角（任务分组） |
| 黑暗模式 | 简单反色 | 完整适配，颜色层级清晰 |

### 1.2 核心设计原则

**P1 · 任务优先**  
所有布局决策以用户完成任务的流畅度为最高准则。导航路径 ≤ 3 步到达任意功能。

**P2 · 克制统一**  
全应用使用同一套 Material 3 设计语言，禁止页面自行发明样式。圆角、间距、颜色均来自规范 Token。

**P3 · 渐进披露**  
高频功能在第一层级可见；低频功能（高级设置、调试选项）收纳在第二、三层级。

**P4 · 即时反馈**  
所有可点击元素必须有视觉反馈（波纹/状态变化）。操作结果须在 500ms 内给出响应提示。

**P5 · 安全私密**  
涉及隐私的操作（锁屏、图标伪装）入口明显但不突兀，操作前须二次确认。

### 1.3 技术平台

| 项目 | 规格 |
|------|------|
| 最低 Android 版本 | API 26（Android 8.0）|
| 目标 SDK | API 35（Android 15）|
| UI 框架 | Material Components for Android 1.12+ |
| 布局引擎 | ConstraintLayout 2.1+ |
| 导航组件 | Navigation Component（Fragment 管理）|
| 图片加载 | Glide 4.x |
| 主题 | Material3 DayNight（支持动态取色 Android 12+）|

---

## 2. 信息架构

### 2.1 用户核心任务流

```
启动应用
    │
    ├─ 首次启动 ──→ 引导页（可选）
    │
    └─ 已有数据 ──→ 浏览页（默认标签）
                        │
          ┌─────────────┼─────────────┐
          ↓             ↓             ↓
       浏览内容       搜索内容       管理收藏
          │             │
          └──→ 进入浏览器模式 ──→ 观看内容 ──→ 收藏/分享
```

### 2.2 信息层级总览

```
一级（底部导航，始终可见）
├─ 浏览       内容发现中心，常用站点入口
├─ 搜索       主动查找内容
├─ 收藏       已保存的内容管理
└─ 我的       账户、隐私、应用配置

二级（从一级跳转）
├─ 浏览器模式    沉浸式 WebView 空间
├─ 历史记录      按时间排列的访问记录
├─ 广告规则      拦截规则管理
├─ 隐私与安全    应用锁 · 图标伪装 · 清除数据
└─ 外部播放器    播放器选择与配置

三级（从二级跳转，低频操作）
├─ 应用锁设置   PIN 码修改 · 生物识别
├─ 图标伪装配置  伪装图标选择
├─ 规则详情     单条规则编辑
└─ 关于应用     版本 · 开源许可 · 反馈
```

### 2.3 导航结构对比

| 位置 | 旧版 | 新版 | 变化说明 |
|------|------|------|----------|
| Tab 1 | 首页（模糊） | **浏览** | 明确职责：内容发现 |
| Tab 2 | 搜索（未实现）| **搜索** | 实现核心功能 |
| Tab 3 | 收藏 | **收藏** | 职责不变，入口统一 |
| Tab 4 | 设置 | **我的** | 覆盖隐私/账户/配置 |

> **重要**：设置不再作为对等导航项。用户认知中"设置"是低频工具，放在"我的"页面的第二屏比独立 Tab 更符合直觉。

---

## 3. 色彩系统

### 3.1 基础色板（Seed Color）

本项目使用薄荷绿 `#5DAC81` 作为 Seed Color 生成完整 Material 3 调色板。

```
Seed Color:  #5DAC81（薄荷绿）
Accent Color: #FF753F（橙红色，警示/强调）
```

在 Android 12+ 设备上**优先使用动态取色**（Dynamic Color），以下为静态回退方案。

### 3.2 浅色主题 Token

```xml
<!-- res/values/colors_md3.xml -->

<!-- Primary 系列 -->
<color name="md3_primary">#2D6A4F</color>              <!-- 主要按钮、选中态 -->
<color name="md3_on_primary">#FFFFFF</color>
<color name="md3_primary_container">#B7E4C7</color>    <!-- 轻量背景 -->
<color name="md3_on_primary_container">#1B4332</color>

<!-- Secondary 系列 -->
<color name="md3_secondary">#52796F</color>
<color name="md3_on_secondary">#FFFFFF</color>
<color name="md3_secondary_container">#CAE8D5</color>
<color name="md3_on_secondary_container">#1B4332</color>

<!-- Tertiary 系列（橙红强调） -->
<color name="md3_tertiary">#C0392B</color>
<color name="md3_on_tertiary">#FFFFFF</color>
<color name="md3_tertiary_container">#FFDAD6</color>
<color name="md3_on_tertiary_container">#410002</color>

<!-- Error 系列 -->
<color name="md3_error">#BA1A1A</color>
<color name="md3_on_error">#FFFFFF</color>
<color name="md3_error_container">#FFDAD6</color>
<color name="md3_on_error_container">#410002</color>

<!-- Surface 系列 -->
<color name="md3_surface">#F6FEF9</color>              <!-- 页面背景 -->
<color name="md3_on_surface">#191C1A</color>
<color name="md3_surface_variant">#D8E8DF</color>      <!-- 卡片/输入框背景 -->
<color name="md3_on_surface_variant">#3F4945</color>

<!-- 轮廓 -->
<color name="md3_outline">#6F7974</color>
<color name="md3_outline_variant">#BCC8C0</color>

<!-- 其他 -->
<color name="md3_inverse_surface">#2E312E</color>
<color name="md3_inverse_on_surface">#EFF1ED</color>
<color name="md3_inverse_primary">#5DAC81</color>      <!-- Snackbar 按钮 -->
<color name="md3_scrim">#000000</color>
<color name="md3_shadow">#000000</color>
```

### 3.3 深色主题 Token

```xml
<!-- res/values-night/colors_md3.xml -->

<color name="md3_primary">#5DAC81</color>              <!-- 深色模式主色保持薄荷绿 -->
<color name="md3_on_primary">#003920</color>
<color name="md3_primary_container">#1B5235</color>
<color name="md3_on_primary_container">#B7E4C7</color>

<color name="md3_secondary">#8ABBA8</color>
<color name="md3_on_secondary">#1D352B</color>
<color name="md3_secondary_container">#344B41</color>
<color name="md3_on_secondary_container">#CAE8D5</color>

<color name="md3_surface">#111411</color>              <!-- 深色背景，非纯黑 -->
<color name="md3_on_surface">#E1E3DF</color>
<color name="md3_surface_variant">#3F4945</color>
<color name="md3_on_surface_variant">#BCC8C0</color>
```

### 3.4 颜色使用规则

| 场景 | 使用 Token | 禁止 |
|------|-----------|------|
| 主操作按钮 | `?attr/colorPrimary` | 硬编码 `#2D6A4F` |
| 页面背景 | `?attr/colorSurface` | 硬编码 `#FFFFFF` / `#121212` |
| 卡片背景 | `?attr/colorSurfaceVariant` | 硬编码 `#F5F5F5` |
| 主文本 | `?attr/colorOnSurface` | 硬编码 `#000000` |
| 辅助文本 | `?attr/colorOnSurfaceVariant` | 硬编码 `#666666` |
| 危险操作 | `?attr/colorError` | 硬编码 `#FF0000` |
| 警示标签 | `md3_tertiary_container` | 橙色直接写入布局 |

> **⚠️ 强制规定**：任何布局文件和 Kotlin/Java 代码中不得出现硬编码颜色值（`#RRGGBB`），全部通过 `?attr/` 或 `@color/` 引用。

---

## 4. 字体规范

### 4.1 类型缩放（Type Scale）

遵循 Material 3 官方类型缩放，全部使用 Android 系统字体（Roboto / 设备默认）。

| 角色 | 样式 Token | 大小 / 行高 | 字重 | 使用场景 |
|------|-----------|------------|------|----------|
| Display Large | `TextAppearance.Material3.DisplayLarge` | 57sp / 64sp | Regular | 极少用，锁屏大标题 |
| Headline Large | `TextAppearance.Material3.HeadlineLarge` | 32sp / 40sp | Regular | 页面主标题 |
| Headline Medium | `TextAppearance.Material3.HeadlineMedium` | 28sp / 36sp | Regular | 对话框标题 |
| Title Large | `TextAppearance.Material3.TitleLarge` | 22sp / 28sp | Regular | Toolbar 标题 |
| Title Medium | `TextAppearance.Material3.TitleMedium` | 16sp / 24sp | Medium | 卡片标题、列表项标题 |
| Title Small | `TextAppearance.Material3.TitleSmall` | 14sp / 20sp | Medium | 标签、导航项 |
| Body Large | `TextAppearance.Material3.BodyLarge` | 16sp / 24sp | Regular | 正文内容 |
| Body Medium | `TextAppearance.Material3.BodyMedium` | 14sp / 20sp | Regular | 描述文字 |
| Body Small | `TextAppearance.Material3.BodySmall` | 12sp / 16sp | Regular | 辅助说明 |
| Label Large | `TextAppearance.Material3.LabelLarge` | 14sp / 20sp | Medium | 按钮文字 |
| Label Medium | `TextAppearance.Material3.LabelMedium` | 12sp / 16sp | Medium | 徽章、时间戳 |
| Label Small | `TextAppearance.Material3.LabelSmall` | 11sp / 16sp | Medium | 极小标签 |

### 4.2 在代码中使用

**XML 布局：**
```xml
<TextView
    android:textAppearance="?attr/textAppearanceTitleMedium"
    .../>
```

**Kotlin 代码：**
```kotlin
textView.setTextAppearance(
    com.google.android.material.R.style.TextAppearance_Material3_TitleMedium
)
```

---

## 5. 间距与圆角

### 5.1 间距系统

```xml
<!-- res/values/dimens.xml -->

<!-- 统一间距（8dp 基准网格）-->
<dimen name="spacing_xs">4dp</dimen>
<dimen name="spacing_sm">8dp</dimen>
<dimen name="spacing_md">16dp</dimen>
<dimen name="spacing_lg">24dp</dimen>
<dimen name="spacing_xl">32dp</dimen>
<dimen name="spacing_xxl">48dp</dimen>

<!-- 页面级边距 -->
<dimen name="page_margin_h">16dp</dimen>    <!-- 页面水平边距 -->
<dimen name="page_margin_v">12dp</dimen>    <!-- 页面垂直边距 -->

<!-- 组件级尺寸 -->
<dimen name="min_touch_target">48dp</dimen>    <!-- 最小触控区域 -->
<dimen name="icon_size_sm">20dp</dimen>
<dimen name="icon_size_md">24dp</dimen>
<dimen name="icon_size_lg">48dp</dimen>
<dimen name="thumbnail_size">72dp</dimen>
<dimen name="pin_button_size">80dp</dimen>
<dimen name="bottom_nav_height">80dp</dimen>   <!-- 含底部安全区 -->
<dimen name="toolbar_height">56dp</dimen>

<!-- 高度（elevation）-->
<dimen name="elevation_none">0dp</dimen>
<dimen name="elevation_sm">1dp</dimen>
<dimen name="elevation_md">2dp</dimen>
<dimen name="elevation_lg">4dp</dimen>
<dimen name="elevation_xl">8dp</dimen>
```

### 5.2 圆角系统

```xml
<!-- res/values/dimens.xml -->

<!-- 圆角（对应 Material3 Shape Scale）-->
<dimen name="corner_none">0dp</dimen>
<dimen name="corner_xs">4dp</dimen>     <!-- ExtraSmall -->
<dimen name="corner_sm">8dp</dimen>     <!-- Small -->
<dimen name="corner_md">12dp</dimen>    <!-- Medium（小组件） -->
<dimen name="corner_lg">16dp</dimen>    <!-- Large（普通卡片） -->
<dimen name="corner_xl">24dp</dimen>    <!-- ExtraLarge（大面板、BottomSheet）-->
<dimen name="corner_full">50dp</dimen>  <!-- Full（按钮、芯片、FAB）-->
```

| 圆角 | 适用场景 |
|------|----------|
| `corner_xs` (4dp) | 输入框、小徽章 |
| `corner_sm` (8dp) | 下拉菜单、Tooltip |
| `corner_md` (12dp) | 图标按钮、小卡片 |
| `corner_lg` (16dp) | 普通卡片、对话框 |
| `corner_xl` (24dp) | BottomSheet、大面板 |
| `corner_full` (50dp) | FAB、按钮、芯片 |

> **禁止使用**：3dp、5dp、7dp、11dp、15dp 等非规范圆角。

---

## 6. 组件规范

### 6.1 顶部应用栏（TopAppBar）

浏览器模式使用**紧凑型**，其余页面使用**标准型**。

```xml
<!-- 标准型 Toolbar（非浏览器页面）-->
<com.google.android.material.appbar.MaterialToolbar
    android:id="@+id/toolbar"
    android:layout_width="match_parent"
    android:layout_height="?attr/actionBarSize"
    android:background="?attr/colorSurface"
    app:titleTextAppearance="?attr/textAppearanceTitleLarge"
    app:titleCentered="false"
    app:navigationIcon="@drawable/ic_arrow_back"
    app:navigationContentDescription="@string/back" />
```

**行为规则：**
- 滚动时 Toolbar 收缩（CollapsingToolbarLayout），标题移入
- 不设置 elevation，用背景色与内容区区分
- 返回按钮始终出现在二级及以下页面

---

### 6.2 底部导航栏（BottomNavigationView）

```xml
<com.google.android.material.bottomnavigation.BottomNavigationView
    android:id="@+id/bottom_nav"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_gravity="bottom"
    android:background="?attr/colorSurface"
    app:labelVisibilityMode="labeled"
    app:itemIconSize="@dimen/icon_size_md"
    app:menu="@menu/bottom_nav_menu"
    app:itemActiveIndicatorStyle="@style/Widget.App.BottomNavIndicator" />
```

**菜单文件 `res/menu/bottom_nav_menu.xml`：**
```xml
<menu xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:id="@+id/nav_browse"
          android:icon="@drawable/ic_home"
          android:title="@string/nav_browse" />
    <item android:id="@+id/nav_search"
          android:icon="@drawable/ic_search"
          android:title="@string/nav_search" />
    <item android:id="@+id/nav_favorites"
          android:icon="@drawable/ic_favorite_border"
          android:title="@string/nav_favorites" />
    <item android:id="@+id/nav_profile"
          android:icon="@drawable/ic_person_outline"
          android:title="@string/nav_profile" />
</menu>
```

**选中态指示器样式：**
```xml
<!-- res/values/styles.xml -->
<style name="Widget.App.BottomNavIndicator"
       parent="Widget.Material3.BottomNavigationView.ActiveIndicator">
    <item name="android:color">@color/selector_nav_indicator</item>
    <item name="shapeAppearance">@style/ShapeAppearance.App.Pill</item>
</style>
```

**行为规则：**
- 进入沉浸式浏览器模式时，底部导航**滑出隐藏**（200ms 动画）
- 退出浏览器模式时，底部导航**滑入显示**
- 当前选中项再次点击时，平滑滚动回列表顶部
- Badge 用于显示"新规则可用"等通知，不超过 99+

---

### 6.3 按钮

| 类型 | 样式 | 使用场景 |
|------|------|----------|
| 填充按钮 | `Widget.Material3.Button` | 主要操作（确认、保存） |
| 色调按钮 | `Widget.Material3.Button.TonalButton` | 次要主要操作 |
| 轮廓按钮 | `Widget.Material3.Button.OutlinedButton` | 取消、次要操作 |
| 文字按钮 | `Widget.Material3.Button.TextButton` | 低强调操作、对话框取消 |
| 图标按钮 | `Widget.Material3.Button.IconButton` | 工具栏操作、列表项操作 |
| FAB | `Widget.Material3.FloatingActionButton` | 页面核心操作 |

```xml
<!-- 主操作按钮示例 -->
<com.google.android.material.button.MaterialButton
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:text="@string/confirm"
    android:minHeight="@dimen/min_touch_target"
    style="@style/Widget.Material3.Button" />

<!-- 图标按钮示例（工具栏）-->
<com.google.android.material.button.MaterialButton
    android:layout_width="@dimen/min_touch_target"
    android:layout_height="@dimen/min_touch_target"
    android:contentDescription="@string/add_favorite"
    app:icon="@drawable/ic_favorite_border"
    style="@style/Widget.Material3.Button.IconButton" />
```

---

### 6.4 卡片（Card）

```xml
<!-- 标准卡片（站点入口、收藏项）-->
<com.google.android.material.card.MaterialCardView
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_margin="@dimen/spacing_sm"
    app:cardCornerRadius="@dimen/corner_lg"
    app:cardElevation="@dimen/elevation_sm"
    app:cardBackgroundColor="?attr/colorSurfaceVariant"
    app:strokeWidth="0dp" />
```

**三种卡片变体：**

| 变体 | `cardElevation` | `strokeWidth` | 使用场景 |
|------|----------------|---------------|----------|
| Elevated | 1dp | 0dp | 浏览页站点卡片 |
| Filled | 0dp | 0dp | 收藏列表项 |
| Outlined | 0dp | 1dp | 设置选项组 |

---

### 6.5 输入框（TextInput）

```xml
<com.google.android.material.textfield.TextInputLayout
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:hint="@string/search_hint"
    style="@style/Widget.Material3.TextInputLayout.OutlinedBox">

    <com.google.android.material.textfield.TextInputEditText
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:imeOptions="actionSearch"
        android:inputType="text" />

</com.google.android.material.textfield.TextInputLayout>
```

**规则：**
- 所有文本输入必须使用 `TextInputLayout` 包裹
- 必填项使用 `app:helperText` 或错误状态 `app:error`
- 搜索输入框使用 `SearchBar` + `SearchView` 组合

---

### 6.6 列表与分隔线

```xml
<!-- 设置页列表项（含分隔线）-->
<LinearLayout
    android:orientation="vertical">

    <!-- 列表项 -->
    <com.google.android.material.card.MaterialCardView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        app:cardCornerRadius="@dimen/corner_none"
        app:cardElevation="@dimen/elevation_none">
        ...
    </com.google.android.material.card.MaterialCardView>

    <!-- 分隔线 -->
    <com.google.android.material.divider.MaterialDivider
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginStart="@dimen/spacing_md"
        android:layout_marginEnd="@dimen/spacing_md" />

</LinearLayout>
```

---

### 6.7 开关与复选框

```xml
<!-- 开关（Switch）-->
<com.google.android.material.switchmaterial.SwitchMaterial
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:minHeight="@dimen/min_touch_target" />

<!-- 或使用 MD3 新组件 -->
<com.google.android.material.materialswitch.MaterialSwitch
    android:layout_width="wrap_content"
    android:layout_height="wrap_content" />
```

---

### 6.8 对话框

```kotlin
// ✅ 正确：使用 MaterialAlertDialogBuilder
MaterialAlertDialogBuilder(context)
    .setTitle(R.string.dialog_title)
    .setMessage(R.string.dialog_message)
    .setNegativeButton(R.string.cancel) { dialog, _ -> dialog.dismiss() }
    .setPositiveButton(R.string.confirm) { _, _ -> doAction() }
    .show()

// ❌ 禁止：使用 AlertDialog.Builder
AlertDialog.Builder(context)  // 不符合 MD3 规范
```

---

### 6.9 Snackbar（操作反馈）

```kotlin
// 成功反馈（自动消失）
Snackbar.make(view, R.string.added_to_favorites, Snackbar.LENGTH_SHORT).show()

// 可撤销操作
Snackbar.make(view, R.string.deleted, Snackbar.LENGTH_LONG)
    .setAction(R.string.undo) { undoDelete() }
    .setAnchorView(R.id.bottom_nav)  // 避免遮挡底部导航
    .show()
```

**规则：**
- Toast 仅用于系统级提示，用户操作结果一律使用 Snackbar
- Snackbar 通过 `setAnchorView` 锚定到底部导航上方
- 包含可撤销操作的 Snackbar 显示 `LENGTH_LONG`

---

### 6.10 进度指示器

```xml
<!-- 线性进度条（页面加载）-->
<com.google.android.material.progressindicator.LinearProgressIndicator
    android:id="@+id/progress_bar"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:visibility="gone"
    app:indicatorColor="?attr/colorPrimary"
    app:trackColor="?attr/colorSurfaceVariant"
    app:trackThickness="3dp" />

<!-- 圆形进度条（内容加载）-->
<com.google.android.material.progressindicator.CircularProgressIndicator
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    app:indicatorSize="40dp" />
```

---

## 7. 页面设计规范

### 7.1 浏览页（首页 Tab）

**功能定位**：内容发现中心，用户的应用起点。

**页面结构：**
```
┌────────────────────────────────────┐
│ [Logo] JavBrowser    [🔍] [⋮]      │  ← MaterialToolbar (56dp)
├────────────────────────────────────┤
│ ████████████████████████           │  ← LinearProgressIndicator (3dp，加载时可见)
├────────────────────────────────────┤
│                                    │
│  最近访问                  [全部]   │  ← Section Header (TitleMedium)
│  ┌──────┐ ┌──────┐ ┌──────┐       │
│  │ 图标  │ │ 图标  │ │ 图标  │      │  ← 水平滚动 RecyclerView（圆角 16dp 卡片）
│  │ 站名  │ │ 站名  │ │ 站名  │      │
│  └──────┘ └──────┘ └──────┘       │
│                                    │
│  常用站点                           │  ← Section Header
│  ┌──────────────────────────────┐  │
│  │ 🌐 站点名称       ↗ 打开    │  │  ← 列表卡片（Elevated Card）
│  └──────────────────────────────┘  │
│  ┌──────────────────────────────┐  │
│  │ 🌐 站点名称       ↗ 打开    │  │
│  └──────────────────────────────┘  │
│  ...                               │
│                                    │
└────────────────────────────────────┘
│  [浏览]  [搜索]  [收藏]  [我的]      │  ← BottomNavigationView (80dp)
└────────────────────────────────────┘
```

**交互细节：**
- 点击站点卡片 → 进入**浏览器模式**（底部导航滑出，全屏沉浸）
- 长按站点卡片 → 弹出 BottomSheet：打开 / 复制链接 / 从列表移除
- 顶部 `⋮` 菜单：刷新站点列表 / 管理站点 / 分享

**站点卡片最小规格：**
- 高度：72dp
- 左侧图标：40×40dp（圆角 8dp，Glide 加载 favicon）
- 标题：TitleMedium（单行，截断）
- 副标题：BodySmall，灰色（域名）

---

### 7.2 搜索页（Search Tab）

**功能定位**：用户主动查找内容的核心入口。

**页面结构：**
```
┌────────────────────────────────────┐
│ ┌──────────────────────────────┐   │
│ │ 🔍  搜索网站或输入网址...  ✕ │   │  ← SearchBar（全宽，56dp，圆角 28dp）
│ └──────────────────────────────┘   │
├────────────────────────────────────┤
│                                    │
│  最近搜索                   [清除]  │  ← 展开时显示
│  • 搜索词1                          │
│  • 搜索词2                          │
│                                    │
│  热门站点                           │  ← 未搜索时显示
│  ┌────────┐ ┌────────┐             │
│  │  图标   │ │  图标   │            │  ← 2列网格
│  │  名称   │ │  名称   │            │
│  └────────┘ └────────┘             │
│                                    │
│  ─── 搜索结果（有输入时显示）─────   │
│  [卡片] 结果1                       │
│  [卡片] 结果2                       │
│  ...                               │
└────────────────────────────────────┘
│  [浏览]  [搜索]  [收藏]  [我的]      │
└────────────────────────────────────┘
```

**SearchBar 实现：**
```xml
<com.google.android.material.search.SearchBar
    android:id="@+id/search_bar"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:hint="@string/search_hint"
    app:navigationIcon="@drawable/ic_search" />

<com.google.android.material.search.SearchView
    android:id="@+id/search_view"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:hint="@string/search_hint"
    app:setupWithSearchBar="@id/search_bar">

    <!-- 搜索建议 RecyclerView -->
    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/search_suggestions"
        android:layout_width="match_parent"
        android:layout_height="match_parent" />

</com.google.android.material.search.SearchView>
```

**交互细节：**
- 点击 SearchBar → SearchView 展开动画（200ms）
- 输入时实时显示联想建议（防抖 300ms）
- 按回车/点击建议 → 直接进入浏览器模式打开 URL
- 清除历史需要二次确认对话框

---

### 7.3 收藏页（Favorites Tab）

**功能定位**：已保存内容的统一管理中心。

**页面结构：**
```
┌────────────────────────────────────┐
│  收藏                  [🗂] [⋮]     │  ← Toolbar（过滤/排序菜单）
├────────────────────────────────────┤
│ [ 全部 ] [ 视频 ] [ 网页 ]          │  ← ChipGroup 过滤（水平滚动）
├────────────────────────────────────┤
│                                    │
│  ┌────────────────────────────────┐│
│  │ [缩略图] 标题文字               ││  ← MaterialCardView（Filled）
│  │ [72×72]  来自: example.com     ││     高度：88dp
│  │          2026-06-08 · 视频     ││
│  └──────────────────────────────🗑││
│                                   ││  ← 右滑删除手势 OR 右侧删除按钮
│  ┌────────────────────────────────┐│
│  │ [缩略图] 标题文字2              ││
│  │ ...                            ││
│  └────────────────────────────────┘│
│                                    │
│  ─── 空状态（无收藏时显示）──────   │
│                                    │
│         [💚 大图标]                 │
│       还没有收藏内容                │  ← Headline Small
│   浏览网页时点击♡即可收藏           │  ← Body Medium，灰色
│      [开始浏览]                     │  ← Tonal Button
│                                    │
└────────────────────────────────────┘
│  [浏览]  [搜索]  [收藏]  [我的]      │
└────────────────────────────────────┘
```

**收藏项规格：**
- 卡片高度：88dp（含内边距）
- 缩略图：72×72dp，圆角 8dp，`scaleType="centerCrop"`
- 标题：TitleMedium，最多 2 行
- 来源：BodySmall，`?attr/colorOnSurfaceVariant`
- 删除按钮：48×48dp 触控区，图标 24dp

**批量操作：**
- 长按任意项 → 进入多选模式
- Toolbar 变为"已选 N 项 · [删除] [取消]"
- 选中项显示复选框动画

---

### 7.4 我的页面（Profile/Settings Tab）

**功能定位**：替代旧版设置页，覆盖隐私、外观、账户、工具。

**页面结构：**
```
┌────────────────────────────────────┐
│  我的                               │  ← Toolbar（无操作按钮）
├────────────────────────────────────┤
│                                    │
│  ╔══════════════════════════════╗  │
│  ║  [🔒] 隐私与安全              ║  │  ← Outlined Card（分组容器）
│  ╠══════════════════════════════╣  │
│  ║  应用锁        [开关]         ║  │
│  ║  图标伪装      [已启用 →]     ║  │
│  ║  清除浏览数据  [→]            ║  │
│  ╚══════════════════════════════╝  │
│                                    │
│  ╔══════════════════════════════╗  │
│  ║  [🌐] 浏览体验               ║  │
│  ╠══════════════════════════════╣  │
│  ║  广告拦截规则  [→]           ║  │
│  ║  外部播放器    [系统默认 →]   ║  │
│  ║  JavaScript    [开关]         ║  │
│  ╚══════════════════════════════╝  │
│                                    │
│  ╔══════════════════════════════╗  │
│  ║  [ℹ] 关于                    ║  │
│  ╠══════════════════════════════╣  │
│  ║  版本          v1.2.0         ║  │
│  ║  开源许可证    [→]           ║  │
│  ║  意见反馈      [→]           ║  │
│  ╚══════════════════════════════╝  │
│                                    │
└────────────────────────────────────┘
│  [浏览]  [搜索]  [收藏]  [我的]      │
└────────────────────────────────────┘
```

**设置项规格：**
- 每行高度：56dp
- 左侧图标（分组头）：24dp
- 标题：TitleMedium
- 副标题/当前值：BodySmall，`?attr/colorOnSurfaceVariant`
- 右侧：开关 OR 箭头图标（`ic_chevron_right`，24dp）
- 分隔线：从左边距 16dp 开始（内缩分隔线）

---

### 7.5 浏览器模式（沉浸式 WebView）

**进入条件**：从浏览页/搜索页/收藏页点击任意站点。

**页面结构：**
```
┌────────────────────────────────────┐
│                                    │  ← StatusBar（透明，图标深/浅自适应）
│ ← [🔒] https://example.com  [⋮]   │  ← 地址栏（56dp，可收起）
│ ████░░░░░░░░░░░░░░░░░░░░░░░░░░     │  ← 加载进度条（3dp，加载完成隐藏）
├────────────────────────────────────┤
│                                    │
│                                    │
│            WebView                 │  ← 占满剩余空间
│          （广告已过滤）              │
│                                    │
│                                    │
├────────────────────────────────────┤
│ [←] [→] [🔄] [⭐]        [📺 视频]│  ← 底部工具栏（56dp）
└────────────────────────────────────┘
```

**地址栏行为：**
- 页面向下滚动 → 地址栏收起（仅显示域名，高度压缩到 40dp，动画 200ms）
- 页面向上滚动 → 地址栏展开还原
- 点击地址栏 → 进入编辑状态，键盘弹出，全选当前 URL

**底部工具栏按钮：**

| 按钮 | 图标 | 功能 | 状态 |
|------|------|------|------|
| 后退 | `ic_arrow_back` | WebView 后退 | 无历史时置灰 |
| 前进 | `ic_arrow_forward` | WebView 前进 | 无前进时置灰 |
| 刷新 | `ic_refresh` / `ic_close` | 刷新/停止加载 | 加载中显示停止 |
| 收藏 | `ic_favorite_border` / `ic_favorite` | 添加/移除收藏 | 已收藏填充显示 |
| 视频 | `ic_play_circle` | 检测到视频时显示 | 无视频时隐藏 |

**视频检测 FAB：**
```
检测到视频时，底部工具栏右侧出现"📺 视频"扩展按钮
→ 点击弹出 BottomSheet 显示检测到的视频列表
→ 选择视频 → 调用外部播放器
```

---

### 7.6 锁屏页（LockActivity）

**触发条件**：应用锁开启 + 应用从后台回到前台。

**页面结构：**
```
┌────────────────────────────────────┐
│                                    │
│                                    │
│           [🔒  大图标]              │  ← 64dp，Primary 色
│                                    │
│         应用已锁定                  │  ← HeadlineMedium
│        请输入 PIN 码               │  ← BodyLarge，灰色
│                                    │
│   ● ● ● ●                         │  ← PIN 点位指示（4位）
│                                    │
│  ┌────┐  ┌────┐  ┌────┐           │
│  │ 1  │  │ 2  │  │ 3  │           │  ← 数字键盘
│  │    │  │ ABC│  │ DEF│           │
│  └────┘  └────┘  └────┘           │
│  ┌────┐  ┌────┐  ┌────┐           │
│  │ 4  │  │ 5  │  │ 6  │           │
│  │ GHI│  │ JKL│  │ MNO│           │
│  └────┘  └────┘  └────┘           │
│  ┌────┐  ┌────┐  ┌────┐           │
│  │ 7  │  │ 8  │  │ 9  │           │
│  │PQRS│  │ TUV│  │WXYZ│           │
│  └────┘  └────┘  └────┘           │
│          ┌────┐  ┌────┐           │
│          │ 0  │  │ ⌫  │           │
│          └────┘  └────┘           │
│                                    │
│         [指纹/面容解锁]             │  ← 生物识别按钮（如可用）
│                                    │
└────────────────────────────────────┘
```

**PIN 按钮规格：**
- 尺寸：80×80dp（`@dimen/pin_button_size`）
- 样式：`Widget.Material3.Button.TonalButton`
- 圆角：`corner_full`（完全圆形）
- 文字：HeadlineMedium（数字）+ LabelSmall（字母，灰色）

**错误状态：**
- 输入错误 → 点位指示器震动动画 + `#FF0000` 变红 300ms → 恢复
- 错误 3 次 → 锁定 30 秒 + 倒计时提示
- Snackbar 不在此页显示，错误直接在点位区反馈

---

### 7.7 广告规则页（AdFilter Settings）

**层级**：我的 → 浏览体验 → 广告拦截规则

**页面结构：**
```
┌────────────────────────────────────┐
│ ← 广告拦截规则          [刷新] [+] │  ← Toolbar
├────────────────────────────────────┤
│                                    │
│  规则状态                           │
│  ╔══════════════════════════════╗  │
│  ║ 已拦截域名        1,247 条   ║  │  ← 统计信息卡片
│  ║ URL 模式          834 条     ║  │
│  ║ 元素隐藏          62 条      ║  │
│  ║ 最后更新    2026-06-08 09:00 ║  │
│  ╚══════════════════════════════╝  │
│                                    │
│  规则源                            │
│  ┌──────────────────────────────┐  │
│  │ ✓ EasyList 主列表             │  │  ← CheckBox + 来源 URL
│  │   easylist.to/easylist/...   │  │
│  ├──────────────────────────────┤  │
│  │ ✓ EasyList 隐私保护          │  │
│  ├──────────────────────────────┤  │
│  │ ✓ 中国区域补充               │  │
│  ├──────────────────────────────┤  │
│  │ + 添加自定义规则源            │  │  ← TextButton
│  └──────────────────────────────┘  │
│                                    │
│  元素隐藏                          │
│  自定义 CSS 规则…                  │  ← 高级折叠项
│                                    │
└────────────────────────────────────┘
```

---

## 8. 交互规范

### 8.1 手势规范

| 手势 | 场景 | 效果 |
|------|------|------|
| 点击 | 所有可点击元素 | 波纹反馈（Ripple） |
| 长按 | 收藏列表项 | 进入多选模式 |
| 右滑 | 收藏列表项 | 显示"删除"操作 |
| 下拉 | 收藏页/浏览页列表顶部 | 刷新内容 |
| 边缘右滑 | 二级页面 | 返回上一级（Predictive Back） |
| 双击 | 浏览器地址栏 | 全选 URL |

### 8.2 状态管理

所有列表页必须处理以下四种状态：

```
┌──────────┬─────────────────────────────────────────┐
│  状态     │  呈现方式                               │
├──────────┼─────────────────────────────────────────┤
│ Loading  │ LinearProgressIndicator（顶部）          │
│          │ OR 骨架屏（Skeleton Loading）             │
├──────────┼─────────────────────────────────────────┤
│ Success  │ 正常内容列表                             │
├──────────┼─────────────────────────────────────────┤
│ Empty    │ 插图 + 说明文字 + 引导操作按钮            │
├──────────┼─────────────────────────────────────────┤
│ Error    │ 错误提示 + 重试按钮                      │
│          │ Snackbar（网络错误等可恢复错误）          │
└──────────┴─────────────────────────────────────────┘
```

### 8.3 表单验证时机

| 场景 | 验证时机 |
|------|----------|
| PIN 码输入 | 输入第 4 位后立即验证 |
| URL 输入 | 点击确认/回车时验证 |
| 自定义规则源 | 失焦（onFocusLoss）时验证格式 |

### 8.4 危险操作保护

以下操作必须有二次确认：

| 操作 | 确认方式 |
|------|----------|
| 删除全部收藏 | `MaterialAlertDialog`（明确说明后果） |
| 清除浏览历史 | `MaterialAlertDialog` |
| 删除单条收藏 | Snackbar + 撤销（5秒内可撤销） |
| 重置 PIN 码 | 需先验证旧 PIN |
| 更换图标伪装 | 提示"需要重启应用" |

---

## 9. 动画规范

### 9.1 页面转场

| 场景 | 动画类型 | 时长 |
|------|----------|------|
| Tab 切换 | 淡入淡出（Fade） | 200ms |
| 进入二级页面 | 向左滑入（Slide Left） | 300ms |
| 返回上级页面 | 向右滑出（Slide Right） | 250ms |
| 进入浏览器模式 | 从下向上展开（Slide Up） | 350ms |
| 退出浏览器模式 | 向下收回（Slide Down） | 300ms |
| 对话框出现 | 淡入 + 轻微上移 | 250ms |

### 9.2 组件动画

| 场景 | 动画 | 时长 |
|------|------|------|
| 底部导航隐藏（进浏览器）| 向下滑出 | 200ms |
| 底部导航显示（退浏览器）| 向上滑入 | 200ms |
| 收藏心形按钮切换 | Scale + Color 同步 | 300ms |
| 地址栏收起 | 高度压缩 + 内容淡出 | 200ms |
| 多选模式进入 | Toolbar 内容交叉淡入 | 200ms |
| PIN 错误震动 | 水平抖动（TranslateAnim）| 400ms |
| 加载进度条 | 从 0% 到当前进度（平滑）| 实时 |

### 9.3 插值器选择

```kotlin
// 页面进入
val enterInterpolator = FastOutSlowInInterpolator()     // 加速后减速
// 页面退出
val exitInterpolator = FastOutLinearInInterpolator()    // 快速退出
// 对话框/BottomSheet
val dialogInterpolator = DecelerateInterpolator()       // 减速落地
// 错误/强调
val emphasisInterpolator = BounceInterpolator()         // 弹性（仅 PIN 错误等）
```

### 9.4 共享元素转场

| 场景 | 共享元素 | 注意 |
|------|---------|------|
| 收藏缩略图 → 浏览器 | `@transition/thumbnail_hero` | 需设置相同 `transitionName` |
| 列表项 → 详情 | `@transition/card_hero` | ChangeImageTransform + ChangeBounds |

---

## 10. 无障碍规范

### 10.1 触控目标

**全部可点击元素的最小触控区域为 48×48dp**，视觉大小可更小，但触控热区必须通过 Padding 补足。

```xml
<!-- 24dp 图标按钮的正确写法 -->
<ImageButton
    android:layout_width="48dp"
    android:layout_height="48dp"
    android:padding="12dp"
    android:src="@drawable/ic_close_24"
    android:contentDescription="@string/close" />
```

### 10.2 内容描述（contentDescription）

所有**无文字**的可点击控件必须设置 `contentDescription`：

```xml
<!-- ✅ 正确 -->
<ImageButton
    android:contentDescription="@string/add_to_favorites" />

<!-- ❌ 错误：缺少描述 -->
<ImageButton />
```

特殊情况：
- 纯装饰性图片：`android:importantForAccessibility="no"`
- 按钮文字本身足够清晰：可省略 `contentDescription`

### 10.3 颜色对比度

| 文字类型 | 最低对比度 | 推荐对比度 |
|---------|-----------|-----------|
| 正文（≥18sp）| 3:1 | 4.5:1+ |
| 小字（＜18sp）| 4.5:1 | 7:1+ |
| 图标/UI元素 | 3:1 | - |

MD3 Token 体系在标准使用场景下已满足 WCAG 2.1 AA 级。

### 10.4 大字体适配

- 所有文字大小使用 `sp` 单位（禁止 `dp`）
- 布局不得写死高度导致大字体截断
- 卡片/列表项高度建议使用 `wrap_content` + `minHeight`

---

## 11. 开发实施指南

### 11.1 项目依赖（build.gradle）

```groovy
dependencies {
    // Material Design 3（必须）
    implementation 'com.google.android.material:material:1.12.0'

    // Navigation Component（Fragment 管理）
    implementation 'androidx.navigation:navigation-fragment-ktx:2.7.7'
    implementation 'androidx.navigation:navigation-ui-ktx:2.7.7'

    // 图片加载
    implementation 'com.github.bumptech.glide:glide:4.16.0'

    // ConstraintLayout
    implementation 'androidx.constraintlayout:constraintlayout:2.1.4'

    // 生物识别
    implementation 'androidx.biometric:biometric:1.1.0'
}
```

### 11.2 主题配置

**`res/values/themes.xml`（浅色）：**
```xml
<style name="Theme.JavBrowser" parent="Theme.Material3.DayNight.NoActionBar">
    <!-- 主色 -->
    <item name="colorPrimary">@color/md3_primary</item>
    <item name="colorOnPrimary">@color/md3_on_primary</item>
    <item name="colorPrimaryContainer">@color/md3_primary_container</item>
    <item name="colorOnPrimaryContainer">@color/md3_on_primary_container</item>

    <!-- 次级色 -->
    <item name="colorSecondary">@color/md3_secondary</item>
    <item name="colorOnSecondary">@color/md3_on_secondary</item>
    <item name="colorSecondaryContainer">@color/md3_secondary_container</item>
    <item name="colorOnSecondaryContainer">@color/md3_on_secondary_container</item>

    <!-- Surface 系列 -->
    <item name="colorSurface">@color/md3_surface</item>
    <item name="colorOnSurface">@color/md3_on_surface</item>
    <item name="colorSurfaceVariant">@color/md3_surface_variant</item>
    <item name="colorOnSurfaceVariant">@color/md3_on_surface_variant</item>

    <!-- 错误 -->
    <item name="colorError">@color/md3_error</item>
    <item name="colorOnError">@color/md3_on_error</item>

    <!-- 轮廓 -->
    <item name="colorOutline">@color/md3_outline</item>
    <item name="colorOutlineVariant">@color/md3_outline_variant</item>

    <!-- 状态栏 -->
    <item name="android:statusBarColor">?attr/colorSurface</item>
    <item name="android:navigationBarColor">?attr/colorSurface</item>
    <item name="android:windowLightStatusBar">true</item>

    <!-- 圆角 -->
    <item name="shapeAppearanceSmallComponent">@style/ShapeAppearance.App.Small</item>
    <item name="shapeAppearanceMediumComponent">@style/ShapeAppearance.App.Medium</item>
    <item name="shapeAppearanceLargeComponent">@style/ShapeAppearance.App.Large</item>
</style>

<style name="ShapeAppearance.App.Small" parent="ShapeAppearance.Material3.SmallComponent">
    <item name="cornerSize">@dimen/corner_sm</item>
</style>
<style name="ShapeAppearance.App.Medium" parent="ShapeAppearance.Material3.MediumComponent">
    <item name="cornerSize">@dimen/corner_md</item>
</style>
<style name="ShapeAppearance.App.Large" parent="ShapeAppearance.Material3.LargeComponent">
    <item name="cornerSize">@dimen/corner_lg</item>
</style>
```

### 11.3 MainActivity 导航结构

```kotlin
// MainActivity.kt（仅负责容器 + 底部导航）
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 沉浸式状态栏
        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        // 底部导航与 NavController 绑定
        binding.bottomNav.setupWithNavController(navController)

        // 浏览器模式时隐藏底部导航
        navController.addOnDestinationChangedListener { _, dest, _ ->
            val showNav = dest.id != R.id.browserFragment
            animateBottomNav(showNav)
        }
    }

    private fun animateBottomNav(show: Boolean) {
        val targetTranslation = if (show) 0f else binding.bottomNav.height.toFloat()
        binding.bottomNav.animate()
            .translationY(targetTranslation)
            .setDuration(200)
            .setInterpolator(FastOutSlowInInterpolator())
            .start()
    }
}
```

**导航图 `res/navigation/nav_graph.xml` 结构：**
```xml
<navigation ...>
    <!-- 一级 Tab -->
    <fragment android:id="@+id/browseFragment" ... />
    <fragment android:id="@+id/searchFragment" ... />
    <fragment android:id="@+id/favoritesFragment" ... />
    <fragment android:id="@+id/profileFragment" ... />

    <!-- 沉浸式浏览器（全屏）-->
    <activity android:id="@+id/browserActivity" ... />

    <!-- 二级页面 -->
    <fragment android:id="@+id/adFilterFragment" ... />
    <fragment android:id="@+id/privacyFragment" ... />
    <fragment android:id="@+id/aboutFragment" ... />

    <!-- 独立 Activity（锁屏）-->
    <activity android:id="@+id/lockActivity" ... />
</navigation>
```

### 11.4 颜色规范校验清单

在提交代码前，使用以下正则检查硬编码问题：

```bash
# 检查硬编码颜色（XML）
grep -rn '#[0-9A-Fa-f]\{3,8\}' app/src/main/res/layout/

# 检查硬编码颜色（Kotlin）
grep -rn 'Color.parseColor\|0xFF\|0x[0-9A-Fa-f]\{6,8\}' app/src/main/java/

# 检查硬编码尺寸（XML，排除 0dp/match_parent）
grep -rn '[0-9]\+dp"' app/src/main/res/layout/ | grep -v '"0dp"\|"1dp"\|"2dp"'

# 检查硬编码字符串（XML）
grep -rn 'android:text="[^@]' app/src/main/res/layout/
```

### 11.5 常见错误与正确写法

**错误 1：直接使用系统旧版组件**
```xml
<!-- ❌ 旧版 -->
<Button ... />
<EditText ... />
<Switch ... />
<ProgressBar ... />

<!-- ✅ MD3 -->
<com.google.android.material.button.MaterialButton ... />
<com.google.android.material.textfield.TextInputEditText ... />
<com.google.android.material.materialswitch.MaterialSwitch ... />
<com.google.android.material.progressindicator.LinearProgressIndicator ... />
```

**错误 2：硬编码间距**
```xml
<!-- ❌ -->
<TextView android:layout_margin="15dp" />

<!-- ✅ -->
<TextView android:layout_margin="@dimen/spacing_md" />
```

**错误 3：直接使用 AlertDialog**
```kotlin
// ❌
AlertDialog.Builder(this).setTitle("确认").show()

// ✅
MaterialAlertDialogBuilder(this).setTitle(R.string.confirm).show()
```

**错误 4：Toast 用于操作反馈**
```kotlin
// ❌ 用户操作结果用 Toast
Toast.makeText(this, "已添加收藏", Toast.LENGTH_SHORT).show()

// ✅ 用 Snackbar（可撤销）
Snackbar.make(binding.root, R.string.added_to_favorites, Snackbar.LENGTH_SHORT)
    .setAnchorView(binding.bottomNav)
    .show()
```

**错误 5：e.printStackTrace() 编译警告**
```kotlin
// ❌
} catch (e: Exception) {
    e.printStackTrace()
}

// ✅
} catch (e: Exception) {
    Log.e(TAG, "操作失败", e)
}
```

---

## 12. 附录：文件清单

### 12.1 资源文件

```
res/
├── values/
│   ├── colors_md3.xml          ← MD3 色彩 Token（48个）
│   ├── dimens.xml              ← 间距、圆角、尺寸规范
│   ├── strings.xml             ← 所有字符串资源
│   ├── themes.xml              ← MD3 浅色主题
│   └── styles.xml              ← 组件样式扩展
├── values-night/
│   ├── colors_md3.xml          ← MD3 深色 Token
│   └── themes.xml              ← MD3 深色主题
├── layout/
│   ├── activity_main.xml       ← 容器 + 底部导航
│   ├── fragment_browse.xml     ← 浏览页
│   ├── fragment_search.xml     ← 搜索页
│   ├── fragment_favorites.xml  ← 收藏页
│   ├── fragment_profile.xml    ← 我的页面
│   ├── activity_browser.xml    ← 浏览器模式
│   ├── activity_lock.xml       ← 锁屏页
│   ├── item_site.xml           ← 站点列表项
│   └── item_favorite.xml       ← 收藏列表项
├── drawable/
│   ├── ic_home.xml
│   ├── ic_search.xml
│   ├── ic_favorite_border.xml
│   ├── ic_favorite.xml
│   ├── ic_person_outline.xml
│   ├── ic_chevron_right.xml
│   ├── ic_delete.xml
│   ├── ic_play_circle.xml
│   ├── ic_arrow_back.xml
│   ├── ic_arrow_forward.xml
│   ├── ic_refresh.xml
│   └── ic_close.xml
├── menu/
│   ├── bottom_nav_menu.xml
│   ├── menu_browser.xml        ← 浏览器顶部菜单
│   └── menu_favorites.xml      ← 收藏页菜单
└── navigation/
    └── nav_graph.xml
```

### 12.2 验收清单（QA 测试用）

| 检查项 | 浅色 | 深色 | 备注 |
|--------|------|------|------|
| 底部导航 4 个 Tab 均可点击 | ☐ | ☐ | |
| Tab 切换无白屏/黑屏闪烁 | ☐ | ☐ | |
| 进入浏览器模式底部导航隐藏 | ☐ | ☐ | 动画 200ms |
| 退出浏览器模式底部导航显示 | ☐ | ☐ | |
| 所有按钮有波纹反馈 | ☐ | ☐ | |
| 搜索框可输入并触发搜索 | ☐ | ☐ | |
| 收藏页空状态正常显示 | ☐ | ☐ | |
| 收藏项可右滑删除 | ☐ | ☐ | 有撤销 Snackbar |
| 应用锁开启后再次进入触发锁屏 | ☐ | ☐ | |
| PIN 错误 3 次后锁定 30 秒 | ☐ | ☐ | |
| 广告规则更新成功/失败反馈 | ☐ | ☐ | |
| 所有对话框使用 MaterialAlertDialog | ☐ | ☐ | |
| 所有 Snackbar 锚定在底部导航上方 | ☐ | ☐ | |
| 大字体（200%）布局无截断 | ☐ | ☐ | |
| 所有图标按钮有 contentDescription | ☐ | ☐ | TalkBack 测试 |
| 触控区域均 ≥ 48dp | ☐ | ☐ | |
| WebView 背景与主题同步 | ☐ | ☐ | 避免白闪 |
| 状态栏图标颜色自适应主题 | ☐ | ☐ | |

---

> **文档维护**：每次 UI 改动后同步更新本文档对应章节，保持规范与实现一致。
>
> **参考资料**：
> - [Material Design 3 官方文档](https://m3.material.io/)
> - [Android 无障碍开发指南](https://developer.android.com/guide/topics/ui/accessibility)
> - [Navigation Component 指南](https://developer.android.com/guide/navigation)
