# JAV Browser

> MISSAV、JABLE.TV、ROU.VIDEO、AVJOY 及 15+ Hentai 站点的终极隐私播放器

[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/platform-Android-green.svg)](https://www.android.com/)
[![Min SDK](https://img.shields.io/badge/min%20sdk-API%2024%20(Android%207.0)-orange.svg)]()
[![Version](https://img.shields.io/badge/version-1.1.3-brightgreen.svg)]()
[![Kotlin](https://img.shields.io/badge/kotlin-1.9.20-blue.svg)](https://kotlinlang.org/)

---

## 🌟 概述

**JAV Browser** 是一款专注于隐私保护的 Android 浏览器，支持 [MISSAV](https://missav.ws)、[JABLE.TV](https://jable.tv)、[ROU.VIDEO](https://rou.video)、[AVJOY](https://avjoy.me) 等 JAV 站点及 15+ Hentai 动画站点。

主要特性包括广告拦截、视频检测与外部播放器调用、可视化书签管理、生物识别锁和图标伪装等隐私功能。

---

## ✨ 核心功能

### 🛡️ 双引擎广告拦截系统

**JSON 规则引擎**（~100 条规则）

- 网络层拦截 - 阻止匹配的广告请求
- DOM 清理 - JavaScript 注入移除广告元素
- 自动关闭弹窗
- 支持云端更新（GitHub）和本地规则

**Adblock Plus 规则引擎**（~120,000 条规则）

- 集成 EasyList、EasyPrivacy、EasyList China
- 支持 217heidai AdBlock Filters 和 GOODBYEADS
- 支持域名阻断、URL 模式匹配和元素隐藏
- 规则格式：`||domain.com^`（阻断）、`@@||domain.com^`（白名单）、`##.selector`（元素隐藏）

### ▶️ 视频检测与外部播放器集成

- 自动检测页面中的 HLS 流（.m3u8）和 MP4 视频
- 支持 4 个 JAV 站点 + 15 个 Hentai 站点的视频提取
- 本地代理服务器（NanoHTTPD）注入 Referer/Cookie 绕过 CDN 限制
- 调用外部播放器（MX Player、VLC 等）
- 自动复制视频 URL 到剪贴板

### ♥ 可视化书签系统

- 点击 ❤️ 按钮收藏
- 自动截取缩略图（Base64 编码存储）
- 卡片视图显示标题和缩略图
- 搜索与批量删除
- 导入/导出 JSON 备份

### 🔒 隐私功能

| 功能         | JAV Browser              | 普通浏览器    |
| ------------ | ------------------------ | ------------- |
| 广告过滤     | ✅ 引擎拦截              | ❌ 需要插件   |
| 视频检测     | ✅ 自动检测 + 外部播放器 | ❌ 不支持     |
| 视频书签     | ✅ 缩略图 + 搜索         | ❌ 仅基础书签 |
| 应用锁       | ✅ 生物识别              | ❌ 不可用     |
| 图标伪装     | ✅ 4 种可选图标          | ❌ 不可能     |
| 最近应用隐私 | ✅ 空白界面              | ❌ 显示内容   |
| 截图拦截     | ✅ FLAG_SECURE           | ❌ 无此功能   |

**生物识别锁**

- 支持指纹/面部识别（BiometricPrompt API）
- 冷启动时强制身份验证

**图标伪装**

- 通过 `<activity-alias>` 切换启动器图标
- 可选：JAV Browser（默认）、计算器、备忘录、文件管理器

**后台隐私**

- `FLAG_SECURE` 阻止最近任务截图和屏幕录制
- WebView 缓存/Cookie 可选清理

### 🔍 导航功能

- Material 3 底部导航栏（4 个标签页）
- 首页快捷访问支持的站点
- 搜索标签页（待实现）
- 收藏夹标签页
- 设置标签页

---

## 🏗️ 技术架构

- **语言**: Kotlin 1.9.20
- **平台**: Android 7.0+（API 24）
- **目标 SDK**: API 34（Android 14）
- **构建系统**: Gradle 8.2 + AGP 8.2.0
- **当前版本**: 1.1.3 (Build 15)

### 核心组件

- **WebView** - 页面渲染，JavaScript 注入，请求拦截
- **NanoHTTPD** - 本地 HTTP 代理服务器（动态端口分配）
- **SharedPreferences** - 书签、设置、规则存储
- **BiometricPrompt** - 生物识别认证（androidx.biometric:1.1.0）
- **Glide 4.16.0** - 异步图片加载
- **Material Components 1.12.0** - Material Design 3 UI

### 广告拦截流程

1. `shouldInterceptRequest()` 检查 JSON 规则和 Adblock Plus 规则
2. 匹配的请求返回空响应
3. 页面加载后注入元素隐藏 CSS
4. 注入 DOM 清理 JavaScript（每秒执行）
5. 检测并关闭弹窗

### 视频检测流程

1. 页面加载完成后注入 JavaScript 读取 HTML 源码
2. 将 HTML 传递给 `VideoExtractor` 对应的站点解析器
3. 提取 .m3u8 或 .mp4 URL
4. 显示播放按钮
5. `VideoProxyServer.buildProxyUrl()` 包装 URL 并添加请求头
6. 调用外部播放器 Intent，同时复制 URL 到剪贴板

---

## 🚀 快速开始

### 系统要求

- Android 7.0+（API 24）
- 2GB+ RAM（推荐）
- 生物识别硬件（可选，用于应用锁）

### 安装步骤

1. 从 [Releases]() 下载最新 APK
2. 进入 `设置` → `安全` → 启用 `安装未知应用`
3. 安装 APK 并打开应用
4. 首次使用：`设置` → 滑动到底部 → 点击 `更新规则` 获取最新广告拦截规则

---

## 🎬 使用场景

| 场景       | 操作方法                                             |
| ---------- | ---------------------------------------------------- |
| 浏览视频   | 打开应用 → 访问站点 → 自动拦截广告                 |
| 收藏视频   | 浏览页面 → 点击 ❤️ → 自动截取缩略图并保存        |
| 外部播放器 | 进入视频页 → 等待绿色播放按钮 → 点击 → 选择播放器 |
| 隐私模式   | 设置 → 启用应用锁 → 切换图标为"计算器"             |
| 备份书签   | 设置 → 书签管理 → 导出 JSON                        |
| 更新规则   | 设置 → 更新规则 → 选择云端/Adblock Plus/全部源     |

---

## 🌐 支持的站点

### JAV 视频站点

以下站点中，**MissAV、Jable、Rou.Video、AvJoy** 已实现视频提取功能：

| 站点                | 链接                        | 说明                                |
| ------------------- | --------------------------- | ----------------------------------- |
| **MissAV**    | https://missav.ws/          | 大型 JAV 视频库（已支持视频提取）   |
| **Jable**     | https://jable.tv/           | 优质 JAV 流媒体（已支持视频提取）   |
| **Rou.Video** | https://rouva3.xyz/home     | JAV 资源平台（已支持视频提取）      |
| **AvJoy**     | https://avjoy.me/           | 日系 AV 观看站点（已支持视频提取）  |
| SupJAV              | https://supjav.com/         | 大型免费 JAV 流媒体，更新迅速       |
| JavGuru             | https://jav.guru/           | 高质量 JAV 免费观看站，支持番号搜索 |
| JavGG               | https://javgg.net/          | 免费 JAV 流媒体，库量大，界面简洁   |
| Njav                | https://njav.tv/            | 免费日本 AV 资源站，更新快          |
| WatchJAVOnline      | https://watchjavonline.com/ | 免费 JAV 流媒体，无需注册           |

### Hentai 动画站点

以下所有站点均已实现视频提取功能：

| 站点           | 链接                       | 说明                                       |
| -------------- | -------------------------- | ------------------------------------------ |
| HentaiHaven    | https://hentaihaven.xxx    | 经典 Hentai 流媒体，高清无码，支持英文字幕 |
| Hanime         | https://hanime.tv          | 顶级 Hentai 流媒体，资源全，支持多语言字幕 |
| WatchHentai    | https://watchhentai.net    | Hentai 视频免费观看站，支持字幕在线播放    |
| Oppai          | https://oppai.stream       | 专注高清 Hentai，无码版本多                |
| MuchoHentai    | https://muchohentai.com    | Hentai 全集免费流媒体，支持字幕/配音       |
| HentaiMama     | https://hentaimama.io      | 中英字幕 Hentai 站，内容更新活跃           |
| HentaiFreak    | https://hentaifreak.org    | HD/无码日本动画 Hentai，片源丰富           |
| Xanimeporn     | https://xanimeporn.com     | 免费动漫 Hentai 视频资源站                 |
| KissHentai     | https://kisshentai.net     | Hentai 动画在线观看站，支持字幕            |
| HentaiCity     | https://hentaicity.com     | 免费高清日本 Hentai 视频                   |
| HentaiUniverse | https://hentaiuniverse.net | HD 日本 Hentai 动画，免费浏览观看          |
| AnimeIDHentai  | https://animeidhentai.com  | 动漫风格 Hentai 免费流媒体                 |
| Ohentai        | https://ohentai.org        | 免费 Hentai 动画站，经典与新作并存         |
| HentaiDude     | https://hentaidude.com     | 分类丰富的 Hentai 动画，移动端友好         |
| Rule34Video    | https://rule34video.com    | Rule34/Hentai 视频站，社区驱动内容         |

---

## 🔐 隐私承诺

- 无追踪、无分析、无数据收集
- 所有数据本地存储（SharedPreferences）
- 不保存浏览历史
- 不上传任何数据至服务器
- Adblock Plus 规则仅内存缓存，不持久化

---

## ⚠️ 免责声明

- 仅供 18+ 成年人使用
- 请遵守所在地区的法律法规
- 请在私密环境中使用
- 开发者不对用户行为负责---

## 📄 许可证

本项目基于 MIT 许可证发布 – 详情请参阅 [LICENSE](LICENSE) 文件。
