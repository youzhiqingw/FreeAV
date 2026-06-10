# JAV Browser - 技术栈文档

> **项目定位**: 隐私优先的成人视频流媒体浏览器  
> **技术特色**: 多层广告拦截 + 智能视频提取 + 军事级隐私保护  
> **更新日期**: 2026-06-07

---

## 1. 开发环境

| 类别 | 工具/版本 | 说明 |
|------|----------|------|
| **编程语言** | Kotlin 1.9.20 | 主开发语言，100% Kotlin |
| **JVM目标** | Java 8 (1.8) | `jvmTarget = "1.8"` |
| **构建系统** | Gradle 8.2 | Kotlin DSL (.kts) |
| **Android Gradle Plugin** | 8.2.0 | 构建工具链 |
| **IDE** | Android Studio Hedgehog+ | 推荐 2023.1.1 或更高版本 |

---

## 2. Android 平台规格

| 配置项 | 版本/值 | 备注 |
|--------|---------|------|
| **Min SDK** | API 24 (Android 7.0 Nougat) | 支持 95%+ 设备 |
| **Target SDK** | API 34 (Android 14) | 最新稳定版 |
| **Compile SDK** | 34 | 编译目标 |
| **Package Name** | `com.example.freeavbrowser` | 应用唯一标识 |
| **Version Code** | 1 | 构建版本号 |
| **Version Name** | 1.0 | 用户可见版本 |

---

## 3. 核心依赖库

### 3.1 AndroidX 基础库

| 库名 | 版本 | 用途 |
|------|------|------|
| `androidx.core:core-ktx` | 1.12.0 | Kotlin 扩展函数 |
| `androidx.appcompat:appcompat` | 1.6.1 | 向下兼容支持 |
| `androidx.constraintlayout:constraintlayout` | 2.1.4 | 响应式布局 |
| `androidx.recyclerview:recyclerview` | 1.3.2 | 收藏夹列表渲染 |
| `androidx.cardview:cardview` | 1.0.0 | 卡片式 UI 组件 |

### 3.2 UI 与设计

| 库名 | 版本 | 用途 |
|------|------|------|
| `com.google.android.material:material` | 1.11.0 | Material Design 3 组件库 |
| `com.github.bumptech.glide:glide` | 4.16.0 | 图片异步加载与缓存（收藏夹缩略图） |

### 3.3 隐私与安全

| 库名 | 版本 | 用途 |
|------|------|------|
| `androidx.biometric:biometric` | 1.1.0 | 指纹/面部识别认证 |
| `WindowManager.FLAG_SECURE` | 系统API | 防截图/录屏/任务管理器预览 |

### 3.4 网络与视频

| 库名 | 版本 | 用途 |
|------|------|------|
| `org.nanohttpd:nanohttpd` | 2.3.1 | 嵌入式 HTTP 服务器（视频代理） |
| `Android WebView` | 系统组件 | 页面渲染引擎 |

### 3.5 测试工具

| 库名 | 版本 | 用途 |
|------|------|------|
| `junit:junit` | 4.13.2 | 单元测试框架 |
| `androidx.test.ext:junit` | 1.1.5 | Android JUnit Runner |
| `androidx.test.espresso:espresso-core` | 3.5.1 | UI 自动化测试 |

---

## 4. 架构组件

### 4.1 数据持久化

| 组件 | 实现方式 | 数据类型 |
|------|----------|----------|
| **本地存储** | `SharedPreferences` | 收藏夹、设置、广告规则缓存 |
| **缓存策略** | `WebView` 内置缓存 | HTML/CSS/JS 文件 |
| **图片存储** | Base64 编码 → JSON | 收藏夹缩略图（内嵌存储） |

**存储路径**:  
- 主配置: `/data/data/com.example.freeavbrowser/shared_prefs/favorites.xml`
- WebView 缓存: `/data/data/com.example.freeavbrowser/cache/`

### 4.2 网络层

| 功能 | 实现 | 关键技术 |
|------|------|----------|
| **广告拦截** | `WebViewClient.shouldInterceptRequest()` | URL 模式匹配 + 空响应 |
| **规则更新** | `HttpURLConnection` | GitHub raw 文件拉取 |
| **视频代理** | `NanoHTTPD` 本地服务器 | 端口 0（自动分配） |
| **CDN 突破** | HTTP Header 注入 | Referer/Cookie/User-Agent |

### 4.3 视频提取引擎

| 站点 | 提取方法 | 技术细节 |
|------|----------|----------|
| **JABLE.TV** | 正则表达式 | 匹配 `var hlsUrl = '...'` |
| **MISSAV** | Dean Edwards Unpacker | 反混淆 `eval(function(p,a,c,k,e,d){...})` |
| **ROU.VIDEO** | HTML 解析 | 待实现 |
| **AVJOY** | 动态脚本分析 | 待实现 |

**输出格式**: HLS (.m3u8) 流媒体 URL

### 4.4 UI 架构

```
MainActivity (WebView Container)
├── AdFilterRules (Network Interceptor)
├── VideoExtractor (HTML Parser)
├── VideoProxyServer (Local Proxy)
└── FavoritesManager (Bookmark Handler)

FavoritesActivity (RecyclerView)
├── FavoriteAdapter (Data Binding)
└── SearchView (Filter Logic)

SettingsActivity (Preferences UI)
├── PrivacySettings (Biometric Setup)
├── AppIconManager (Launcher Icon Switcher)
└── DomainConfig (Domain Rotator)

LockActivity (Biometric Gate)
└── BiometricHelper (Auth Handler)
```

---

## 5. 关键技术实现

### 5.1 多层广告拦截系统

```kotlin
// 三层拦截架构
Layer 1: Network Interception (shouldInterceptRequest)
         ↓ Block before data transfer
Layer 2: DOM Cleanup (JavaScript Injection)
         ↓ Remove ad elements from rendered page
Layer 3: Auto-Close Detection (Window Monitor)
         ↓ Close pop-ups automatically
```

**规则来源**: `ad-filter-rules.json` (Cloud-updateable)  
**当前版本**: v2.3.3 (2026-03-31)

### 5.2 视频代理服务器

```kotlin
VideoProxyServer (NanoHTTPD)
├── Port: 0 (Auto-assign, e.g., 35781)
├── Request Flow:
│   External Player → http://localhost:35781/proxy?url=xxx
│   ↓
│   Server adds headers (Referer/Cookie)
│   ↓
│   Forward to CDN → Return video stream
└── Supports: .m3u8 (HLS), .mp4 (Progressive)
```

**解决问题**: 外部播放器无法携带自定义 HTTP 头导致的 403 错误

### 5.3 隐私保护机制

| 功能 | 实现代码 | 效果 |
|------|----------|------|
| **应用锁** | `BiometricPrompt.authenticate()` | 冷启动时强制生物识别 |
| **图标伪装** | `<activity-alias>` × 4 | 伪装成计算器/记事本/文件管理器 |
| **任务管理器隐藏** | `FLAG_SECURE` | Recent Apps 中显示空白 |
| **防截图** | `FLAG_SECURE` | 系统级阻止截图/录屏 |

### 5.4 域名动态切换

```json
// ad-filter-rules.json
{
  "domains": {
    "missav": "missav.ai",   // 自动更新域名
    "jable": "jable.tv",
    "rou_video": "rouva3.xyz",
    "avjoy": "avjoy.me"
  }
}
```

**优势**: 无需发布新版本即可应对域名封锁

---

## 6. 构建配置

### 6.1 代码混淆

| Build Type | 混淆状态 | 配置文件 |
|------------|---------|----------|
| Debug | ❌ 未启用 | - |
| Release | ❌ 未启用 | `proguard-rules.pro` (待配置) |

**建议**: 生产环境启用 ProGuard/R8 混淆以保护代码逻辑

### 6.2 签名配置

| 配置项 | 状态 | 说明 |
|--------|------|------|
| **Debug Keystore** | ✅ 自动生成 | Android Studio 默认 |
| **Release Keystore** | ⚠️ 未配置 | 需手动创建 `.jks` 文件 |

---

## 7. 第三方服务

| 服务 | 用途 | 接入方式 |
|------|------|----------|
| **GitHub Raw** | 广告规则更新 | HTTP GET (无 API Key) |
| **JavDB** (计划中) | 元数据获取 | 待接入 API |
| **无后端服务** | - | 100% 本地化应用 |

---

## 8. 开发工具链

### 8.1 命令行工具

```bash
# Windows (CMD / PowerShell)
gradlew.bat assembleDebug      # 编译 Debug APK
gradlew.bat assembleRelease    # 编译 Release APK
gradlew.bat installDebug       # 安装到设备
gradlew.bat lint               # 代码静态检查
gradlew.bat test               # 运行单元测试
```

### 8.2 输出路径

| 产物类型 | 路径 |
|---------|------|
| Debug APK | `app/build/outputs/apk/debug/app-debug.apk` |
| Release APK | `app/build/outputs/apk/release/app-release.apk` |
| AAB (Play Store) | `app/build/outputs/bundle/release/app-release.aab` |

---

## 9. 技术亮点

### 9.1 创新点

1. **零后端架构**: 所有数据本地存储，无服务器成本
2. **Dean Edwards Unpacker**: 破解 MISSAV 的 JavaScript 混淆
3. **动态端口代理**: NanoHTTPD 端口 0 机制避免冲突
4. **多 Launcher Icon**: 4 个伪装图标共存（Android 清单 `<activity-alias>` 特性）

### 9.2 性能优化

| 优化项 | 实现方式 | 效果 |
|--------|----------|------|
| **图片懒加载** | Glide 默认行为 | 减少内存占用 |
| **WebView 缓存** | 系统默认 | 加速重复访问 |
| **规则缓存** | SharedPreferences | 离线可用 |

---

## 10. 依赖关系图

```
Project Dependencies Graph:

com.example.freeavbrowser
├── Android SDK 34
│   ├── androidx.* (UI/Core Libraries)
│   ├── com.google.android.material (Material Design)
│   └── WebView (System Component)
├── NanoHTTPD 2.3.1 (Video Proxy)
├── Glide 4.16.0 (Image Loading)
└── Biometric API 1.1.0 (Security)

Build Tools Chain:
└── Gradle 8.2 + AGP 8.2.0 + Kotlin 1.9.20
```

---

## 11. 系统要求

### 11.1 开发环境

- **操作系统**: Windows 10/11, macOS 12+, Linux (Ubuntu 20.04+)
- **Android Studio**: Hedgehog (2023.1.1) 或更高版本
- **JDK**: JDK 11 或 JDK 17 (推荐)
- **Gradle**: 8.2 (通过 Wrapper 自动下载)
- **RAM**: 8GB+ (推荐 16GB)

### 11.2 运行时要求

| 设备要求 | 规格 |
|---------|------|
| **Android 版本** | 7.0+ (API 24+) |
| **存储空间** | 50MB (安装包) + 200MB (缓存) |
| **RAM** | 2GB+ |
| **生物识别** | 指纹传感器或面部识别（可选） |

---

## 12. 技术债务

| 问题 | 影响 | 优先级 |
|------|------|--------|
| **未启用代码混淆** | 易于反编译 | 🔴 高 |
| **硬编码域名** | 域名变更需更新代码 | 🟢 已解决 (v2.3.3) |
| **无单元测试** | 代码质量难保证 | 🟡 中 |
| **SharedPreferences 存储** | 数据量大时性能差 | 🟡 中 |
| **缺少错误上报** | 线上问题难追踪 | 🟡 中 |

---

## 13. 安全合规

| 项目 | 状态 | 说明 |
|------|------|------|
| **HTTPS 通信** | ✅ 已实施 | 规则更新使用 GitHub HTTPS |
| **数据加密** | ⚠️ 部分实施 | SharedPreferences 未加密 |
| **权限最小化** | ✅ 已实施 | 仅请求 INTERNET 权限 |
| **隐私政策** | ❌ 缺失 | 需补充用户协议 |

---

## 附录: 版本兼容性矩阵

| Android 版本 | API Level | 支持状态 | 市场占有率 (2026) |
|-------------|-----------|---------|------------------|
| 14 | 34 | ✅ 完全支持 | 15% |
| 13 | 33 | ✅ 完全支持 | 18% |
| 12 | 31-32 | ✅ 完全支持 | 25% |
| 11 | 30 | ✅ 完全支持 | 15% |
| 10 | 29 | ✅ 完全支持 | 12% |
| 9 | 28 | ✅ 完全支持 | 8% |
| 8 | 26-27 | ✅ 完全支持 | 5% |
| 7 | 24-25 | ✅ 最低支持 | 2% |
| ≤6 | ≤23 | ❌ 不支持 | <1% |

---

**文档维护**: 请在升级依赖库或修改架构时同步更新本文档  
**联系方式**: GitHub Issues - [fekilooo/javbrowser](https://github.com/fekilooo/javbrowser)
