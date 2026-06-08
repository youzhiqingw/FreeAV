# Changelog - JAV Browser

---

## v1.1.5 — 多广告规则文件支持 + 云端规则恢复

**发布日期**: 2026-06-08  
**版本**: 1.1.5 (Build 17)

### 📋 更新日志

- 恢复云端规则 URL：`https://raw.githubusercontent.com/fekilooo/javbrowser/refs/heads/main/ad-filter-rules.json`
- 支持多广告规则文件（云端+本地多规则源）
- 合并多个规则的屏蔽列表（取并集）
- 规则优先级处理：本地 > 云端 > 默认

---

## v1.1.4 — 新增 13 个 Hentai 站点 + 代码现代化

**发布日期**: 2026-06-08  
**版本**: 1.1.4 (Build 16)

### 🎬 新增站点

- 新增 13 个 Hentai 动画站点支持
- 首页分类导航（JAV / Hentai）
- 视频提取逻辑实现

### 🔧 代码改进

- 替换已弃用 API（Activity Result API）
- 使用 MaterialAlertDialogBuilder
- 优化日志和异常处理
- 修复编译警告

---

## v1.1.3 — Material Design 3 全面重构 + 简体中文汉化

**发布日期**: 2026-06-08  
**版本**: 1.1.3 (Build 15)

### 🎨 Material Design 3 主题系统

- 品牌色色调板：主色 `#5DAC81`（绿色）、辅助色 `#FF753F`（橙色）、第三色 `#4A6FA5`（蓝绿色）
- 完整实现 MD3 色调体系：primary / secondary / tertiary / error 及其 container 变体
- 新增 surfaceContainer 五档色调层级（surfaceContainerLowest → surfaceContainerHighest）
- 新增 inverse 色调令牌（inverseSurface / inverseOnSurface / inversePrimary）
- 亮/暗主题分离，暗色模式圆角与亮色一致

### 🧭 底部导航重构

- 自建 `LinearLayout + ImageButton` 替换为 `BottomNavigationView`
- 新增 `navigation_menu.xml` 统一管理菜单项
- `OnItemSelectedListener` 替代手动高亮管理

### 🎯 组件升级

- **Toolbar**：背景升级 `colorSurfaceContainer`，遵循 MD3 色调分层
- **收藏列表**：`Elevated` 卡片 → `Filled` 变体 + `colorSurfaceContainerLow`，减少阴影视觉噪音
- **PIN 键盘**：全部数字按钮统一采用 `PinButtonStyle`
- **空状态**：收藏页空状态增加插图、排版样式和引导按钮
- **ListAdapter** + `DiffUtil.ItemCallback` 替代内联 RecyclerView.Adapter

### 📐 排版体系

- 新建 `typography.xml`，集中管理 Display / Headline / Title / Body / Label 各三档
- `TitleMedium` 和 `TitleSmall` 增加 bold 覆盖
- 专用 `SectionHeader` 样式用于设置页分区标题
- 全部布局从 `TextAppearance.Material3.*` 迁移至 `TextAppearance.JAVBrowser.*`

### 🔐 锁屏改进

- `OnBackPressedCallback` 替换已弃用 `onBackPressed()`
- 输入错误 PIN 码新增抖动动画
- 按钮增加按压缩放动画（OvershootInterpolator 弹簧回弹）

### 🎬 交互动画

- `overridePendingTransition` fade_in/fade_out 转场动画（FavoritesActivity、SettingsActivity、LockActivity）
- `View.setScaleAnimation()` 扩展函数，应用到 Play 按钮、PIN 键、空状态按钮等

### 🌓 WebView 主题适配

- 首页着陆页和错误页 HTML 集成 `@media (prefers-color-scheme: dark)`
- 内联 CSS 使用自定义属性实现亮暗自动切换

### 🌐 简体中文汉化

- `strings.xml` 全部 62 条字符串统一为简体中文
- 首页着陆页搜索提示、站点链接文字全部汉化
- 全部对话框、Toast 提示、硬编码字符串（PIN 码设置、图标更换、规则管理、错误页面、帮助弹窗等）汉化
- 繁体中文统一转为简体中文

### ⚙️ 构建 & 维护

- compileSdk / targetSdk 34，minSdk 24
- Material Components 1.12.0，Kotlin 1.9.20 + Java 17
- 清理冗余资源和旧色值引用

---

## 版本历史

| 版本 | 日期 | 主要变更 |
|------|------|----------|
| v1.1.5 | 2026-06-08 | 多广告规则文件支持 + 云端规则恢复 |
| v1.1.4 | 2026-06-08 | 新增 13 个 Hentai 站点，代码现代化 |
| v1.1.3 | 2026-06-08 | Material Design 3 全面重构 + 简体中文汉化 |
