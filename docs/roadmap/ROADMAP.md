# JAV Browser - 开发路线图

> **文档版本**: v1.0  
> **更新日期**: 2026-06-07  
> **项目阶段**: 核心功能完成，进入增强迭代期

---

## 📊 项目现状分析

### 当前架构健康度评估

| 维度 | 评分 | 说明 |
|------|------|------|
| **核心功能完整性** | 🟢 85/100 | 主要功能已实现，部分站点支持待完善 |
| **代码质量** | 🟡 65/100 | 功能实现良好，缺少测试和文档 |
| **性能表现** | 🟢 80/100 | WebView 性能优秀，内存管理良好 |
| **安全性** | 🟡 70/100 | 隐私功能完善，但缺少加密和混淆 |
| **可维护性** | 🟡 60/100 | 代码结构清晰，但耦合度较高 |
| **扩展性** | 🟡 55/100 | 硬编码较多，新增站点需修改核心代码 |

### 技术债务清单

#### 🔴 高优先级（影响安全或稳定性）

1. **代码混淆缺失**  
   - **风险**: APK 可被轻易反编译，暴露广告拦截逻辑和视频提取算法  
   - **影响**: 站点方可能针对性封锁  
   - **工作量**: 2 天（配置 R8 + 测试）

2. **SharedPreferences 未加密**  
   - **风险**: Root 用户可读取收藏夹数据（包含浏览历史）  
   - **影响**: 隐私泄露风险  
   - **工作量**: 3 天（集成 EncryptedSharedPreferences）

3. **无错误上报机制**  
   - **风险**: 线上崩溃无法追踪，用户流失  
   - **影响**: 难以定位和修复问题  
   - **工作量**: 2 天（集成 Firebase Crashlytics 或 Sentry）

#### 🟡 中优先级（影响开发效率）

4. **单元测试覆盖率 0%**  
   - **影响**: 重构风险高，难以保证代码质量  
   - **工作量**: 持续投入（目标 60% 覆盖率，约 2 周）

5. **视频提取器耦合在 MainActivity**  
   - **影响**: 新增站点需修改主逻辑，难以复用  
   - **工作量**: 3 天（抽象 `VideoExtractorFactory`）

6. **硬编码字符串未提取**  
   - **影响**: 国际化困难，维护成本高  
   - **工作量**: 1 天（迁移到 `strings.xml`）

#### 🟢 低优先级（不影响功能）

7. **缺少 CI/CD 流程**  
   - **影响**: 手动构建易出错，发布效率低  （GitHub Actions 配置）
8. **UI 设计不统一**  
   - **影响**: 用户体验不够精致  （重构为 Material Design 3）

---

## 🎯 短期规划 (v1.1 - v1.3)

### 里程碑 1: v1.1 - 安全加固版 (预计 2 周)

**发布目标**: 2026-06-21  
**主题**: 生产环境安全性提升

#### 必做任务

- [ ] **启用 R8 代码混淆** (2 天)
  - 配置 `proguard-rules.pro`
  - 保留必要的反射类（Glide, NanoHTTPD）
  - 测试混淆后的 APK 功能完整性
  - 符号表上传到 Google Play Console

- [ ] **数据加密** (3 天)
  - 集成 `androidx.security:security-crypto:1.1.0-alpha06`
  - 迁移 SharedPreferences → EncryptedSharedPreferences
  - 加密收藏夹缩略图（AES-256-GCM）
  - 提供从旧版本的数据迁移逻辑

- [ ] **Crashlytics 集成** (2 天)
  - 接入 Firebase Crashlytics
  - 添加自定义日志上报点（视频提取失败、广告规则更新失败）
  - 隐私合规：用户可选退出（Settings 开关）

- [ ] **发布签名配置** (1 天)
  - 生成 Release Keystore
  - 配置 `signingConfigs` in Gradle
  - 文档化签名流程

#### 可选任务

- [ ] 添加应用内更新检测（GitHub Releases API）
- [ ] 实现密码锁备选方案（无生物识别设备）

---

### 里程碑 2: v1.2 - 视频完整支持版 (预计 3 周)

**发布目标**: 2026-07-12  
**主题**: 完成四大站点视频提取

#### 必做任务

- [ ] **ROU.VIDEO 视频提取器** (4 天)
  - 分析页面 HTML 结构
  - 实现提取逻辑（参考 JABLE 方案）
  - 单元测试覆盖（至少 3 个真实 URL）

- [ ] **AVJOY 视频提取器** (5 天)
  - 分析动态加载脚本
  - 实现反混淆（可能需要新的 Unpacker）
  - 测试不同视频分辨率

- [ ] **视频提取器架构重构** (3 天)
  ```kotlin
  // 插件化架构
  interface VideoExtractor {
      fun canHandle(url: String): Boolean
      fun extract(html: String): VideoInfo?
  }
  
  object VideoExtractorFactory {
      fun getExtractor(url: String): VideoExtractor? {
          return extractors.firstOrNull { it.canHandle(url) }
      }
  }
  ```

- [ ] **播放历史记录** (2 天)
  - 新增 `PlayHistory` 数据模型
  - 保存播放进度（视频 URL + 时间戳）
  - 历史记录管理界面

#### 可选任务

- [ ] 支持 4K 视频检测（提示用户选择画质）
- [ ] 视频加载进度条（HLS 分片下载监控）

---

### 里程碑 3: v1.3 - 收藏夹增强版 (预计 2 周)

**发布目标**: 2026-07-26  
**主题**: 书签管理系统升级

#### 必做任务

- [ ] **文件夹分类** (3 天)
  - 新增 `Folder` 数据模型
  - 收藏时选择文件夹
  - 文件夹重命名/删除/排序

- [ ] **标签系统** (2 天)
  - 多标签支持（一个收藏可有多个标签）
  - 标签筛选界面
  - 标签云展示（热门标签）

- [ ] **导出/导入功能** (2 天)
  - 导出为 JSON 文件（包含 Base64 图片）
  - 从 JSON 导入（去重处理）
  - 分享收藏列表（生成链接码）

- [ ] **JavDB 元数据集成** (4 天)
  - 调研 JavDB API（或爬虫方案）
  - 根据视频标题匹配元数据
  - 显示演员、标签、评分
  - 缓存策略（避免重复请求）

#### 可选任务

- [ ] 收藏统计图表（每日新增、分类分布）
- [ ] 智能分类建议（基于标签自动归档）

---

## 🚀 中期规划 (v1.4 - v2.0)

### 里程碑 4: v1.4 - 架构现代化 (预计 4 周)

**主题**: 技术栈升级与重构

#### 核心任务

1. **数据库迁移** (1 周)
   - SharedPreferences → Room Database
   - 设计表结构：
     - `favorites` (收藏)
     - `folders` (文件夹)
     - `tags` (标签)
     - `play_history` (播放历史)
     - `ad_rules_cache` (规则缓存)
   - 数据迁移脚本

2. **异步改造** (1 周)
   - 引入 Kotlin Coroutines
   - 替换所有 `Thread` 和 `AsyncTask`
   - 使用 `Flow` 实现响应式数据流

3. **MVVM 架构重构** (1.5 周)
   ```
   presentation/
   ├── viewmodel/
   │   ├── MainViewModel.kt
   │   ├── FavoritesViewModel.kt
   │   └── SettingsViewModel.kt
   ├── view/
   │   └── (现有 Activity)
   └── adapter/
   
   domain/
   ├── usecase/
   │   ├── AddFavoriteUseCase.kt
   │   ├── ExtractVideoUseCase.kt
   │   └── UpdateAdRulesUseCase.kt
   └── model/
   
   data/
   ├── repository/
   │   ├── FavoritesRepository.kt
   │   └── AdRulesRepository.kt
   └── local/
       └── database/
   ```

4. **依赖注入** (0.5 周)
   - 引入 Hilt（Dagger 的 Android 版）
   - 提供全局单例（Database, Repository）

---

### 里程碑 5: v1.5 - 用户体验革新 (预计 3 周)

**主题**: UI/UX 全面升级

#### 设计目标

- Material Design 3 全面适配
- 深色模式（AMOLED 优化）
- 流畅动画（Transition API）
- 手势操作（左滑返回、下拉刷新）

#### 具体任务

1. **UI 组件升级** (1 周)
   - 替换为 Material 3 组件（Button, Card, Dialog）
   - 统一配色方案（动态取色 Material You）
   - 字体系统（支持可变字体）

2. **深色模式** (3 天)
   - 实现 Light/Dark/Auto 三种模式
   - AMOLED 纯黑模式（#000000 背景）
   - 主题切换动画

3. **手势与动画** (1 周)
   - 左滑关闭当前页面
   - 下拉刷新（收藏夹、设置页）
   - 共享元素转场（收藏卡片 → 详情页）
   - 加载动画（Lottie 集成）

4. **无障碍支持** (2 天)
   - ContentDescription 完善
   - 大字体模式适配
   - TalkBack 测试

---

### 里程碑 6: v1.6 - 高级广告拦截 (预计 2 周)

**主题**: 广告拦截系统 2.0

#### 创新功能

1. **自定义规则编辑器** (1 周)
   - 可视化规则编辑界面
   - 正则表达式测试工具
   - 导入 AdGuard/uBlock 规则

2. **社区规则订阅** (3 天)
   - 支持订阅第三方规则列表
   - 自动更新机制
   - 规则冲突检测

3. **HTTPS 拦截（实验性）** (3 天)
   - 需要 Root 权限
   - 使用 VpnService API
   - 本地 CA 证书安装

4. **拦截统计** (1 天)
   - 显示已拦截请求数
   - 按域名统计（Top 10）
   - 节省流量统计

---

### 里程碑 7: v2.0 - 社区版 (预计 6 周)

**主题**: 去中心化社区功能

#### 核心功能

1. **匿名评论系统** (2 周)
   - 端到端加密（Signal Protocol）
   - 基于区块链的身份验证（可选）
   - 内容审核机制（用户举报 + AI 过滤）

2. **推荐算法** (2 周)
   - 协同过滤（基于用户行为）
   - 冷启动策略（热门推荐）
   - 隐私保护（本地计算，不上传数据）

3. **列表分享** (1 周)
   - 生成分享码（加密收藏列表）
   - 二维码分享
   - 过期时间控制

4. **P2P 资源网络** (1 周)
   - WebRTC 实现（无中心服务器）
   - Torrent 协议支持
   - 去中心化存储（IPFS）

---

## 🌍 长期愿景 (v2.0+)

> **⚠️ 跨平台规划说明**  
> 以下为长期愿景规划，当前项目专注于 **Android Native 平台**。  
> iOS/桌面/浏览器扩展版本为未来可能的扩展方向，不影响当前 Android 开发路线。

### 跨平台战略

#### iOS 版本 (预计 3 个月)

**技术栈**:
- Swift 5.9 + SwiftUI
- WebKit (WKWebView)
- Keychain (安全存储)
- Face ID / Touch ID

**差异化功能**:
- Siri Shortcuts 集成
- iCloud 同步（可选）
- Widget 支持

---

#### 桌面版 (预计 2 个月)

**技术栈**:
- Tauri (Rust + WebView)
- 或 Electron (跨平台性更好)

**优势**:
- 键盘快捷键支持
- 多窗口管理
- 系统托盘常驻

---

#### 浏览器扩展 (预计 1 个月)

**支持浏览器**:
- Chrome / Edge (Manifest V3)
- Firefox (WebExtensions)

**核心功能**:
- 广告拦截（复用现有规则）
- 视频提取（注入 Content Script）
- 收藏同步（浏览器 Storage API）

---

### AI 赋能计划

#### 智能推荐引擎 (v2.5)

**技术方案**:
- TensorFlow Lite 模型（设备端推理）
- 训练数据：用户行为日志（匿名化）
- 推荐维度：演员、类型、时长、新旧

**隐私保障**:
- 联邦学习（Federated Learning）
- 模型本地训练，仅上传梯度
- 可完全禁用

---

#### 视频内容分析 (v2.6)

**功能**:
1. **演员识别**
   - ML Kit Face Detection
   - 对比 JavDB 演员库
   - 自动标注收藏夹

2. **场景分类**
   - TensorFlow Lite 图像分类模型
   - 自动打标签（室内/户外/多人等）

3. **语音识别**
   - 提取视频音轨
   - Google Speech-to-Text API
   - 生成字幕（可选）

---

### 开放生态建设

#### 插件系统 (v3.0)

**架构设计**:
```kotlin
// 插件接口
interface BrowserPlugin {
    val id: String
    val name: String
    val version: String
    
    fun onPageLoad(url: String, html: String)
    fun onVideoDetected(videoUrl: String)
    fun getCustomMenuItems(): List<MenuItem>
}

// 插件管理器
object PluginManager {
    fun loadPlugin(pluginFile: File): BrowserPlugin
    fun unloadPlugin(pluginId: String)
    fun listPlugins(): List<BrowserPlugin>
}
```

**插件语言**:
- Lua (轻量级脚本)
- JavaScript (熟悉度高)

**应用场景**:
- 自定义视频提取器（用户贡献新站点）
- 第三方广告规则
- UI 主题定制

---

#### 开放 API (v3.1)

**RESTful API**:
```
GET  /api/v1/favorites         # 获取收藏列表
POST /api/v1/favorites         # 添加收藏
PUT  /api/v1/favorites/:id     # 更新收藏
DELETE /api/v1/favorites/:id   # 删除收藏

GET  /api/v1/video/extract?url=xxx  # 视频提取服务
GET  /api/v1/rules/latest           # 最新广告规则
```

**认证方式**:
- OAuth 2.0
- API Key (个人使用)

**使用场景**:
- 第三方客户端开发
- 自动化脚本（Cron 定时抓取）
- 智能家居集成（"Alexa, 播放我的收藏"）

---

## 📈 性能优化路线图

### 阶段 1: 基础优化 (v1.x)

- [ ] 启动时间优化（目标 < 1 秒）
  - 延迟加载非必要组件
  - SharedPreferences 异步读取
  - WebView 预热

- [ ] 内存占用优化（目标 < 150MB）
  - Glide 内存缓存限制
  - WebView 缓存策略调整
  - Bitmap 复用池

- [ ] 网络请求优化
  - HTTP/2 支持（OkHttp 升级）
  - 请求队列管理
  - 超时重试机制

---

### 阶段 2: 深度优化 (v2.x)

- [ ] APK 体积优化（目标 < 5MB）
  - R8 完全混淆
  - 资源压缩（WebP 图片）
  - 移除未使用的依赖

- [ ] 渲染性能优化
  - WebView GPU 加速
  - RecyclerView 预加载
  - 布局层级扁平化

- [ ] 电池续航优化
  - Doze 模式适配
  - 后台任务限制
  - 唤醒锁优化

---

## 🛡️ 安全加固计划

### 2026 Q3 - Q4

1. **网络安全**
   - [ ] Certificate Pinning（防中间人攻击）
   - [ ] HTTPS 强制（所有请求）
   - [ ] 域名白名单机制

2. **数据安全**
   - [ ] 数据库加密（SQLCipher）
   - [ ] 文件存储加密（Android Keystore）
   - [ ] 内存数据擦除（退出时）

3. **应用安全**
   - [ ] Root 检测（可选警告）
   - [ ] 反调试（检测 Frida, Xposed）
   - [ ] 完整性校验（防二次打包）

4. **隐私合规**
   - [ ] GDPR 合规（欧盟用户）
   - [ ] 隐私政策完善
   - [ ] 数据导出功能（用户权利）

---

## 🧪 测试策略

### 当前状态
- 单元测试: 0% 覆盖率 ❌
- 集成测试: 无 ❌
- UI 测试: 无 ❌
- 性能测试: 手动 ⚠️

### 目标状态 (v2.0)

| 测试类型 | 目标覆盖率 | 工具 |
|---------|-----------|------|
| 单元测试 | 70% | JUnit 5 + MockK |
| 集成测试 | 50% | Robolectric |
| UI 测试 | 30% | Espresso |
| 性能测试 | 自动化 | Firebase Performance |

### 关键测试用例

1. **广告拦截测试**
   - 验证 58 个域名全部被拦截
   - 测试 DOM 清理效果
   - 检查性能影响（加载时间对比）

2. **视频提取测试**
   - 每个站点至少 10 个真实 URL
   - 覆盖不同视频格式
   - 异常 HTML 处理

3. **隐私功能测试**
   - 生物识别流程（模拟器 + 真机）
   - FLAG_SECURE 验证（截图测试）
   - 数据加密验证（查看加密后的文件）

---

## 🎨 设计系统规划

### v1.5 设计规范

#### 配色方案

**浅色模式**:
- Primary: `#1976D2` (Material Blue 700)
- Secondary: `#FF5722` (Deep Orange 500)
- Background: `#FAFAFA`
- Surface: `#FFFFFF`

**深色模式**:
- Primary: `#42A5F5` (Material Blue 400)
- Secondary: `#FF7043` (Deep Orange 400)
- Background: `#121212`
- Surface: `#1E1E1E`

**AMOLED 模式**:
- Background: `#000000` (纯黑)
- Surface: `#0A0A0A`

#### 字体系统

- 标题: Roboto Bold
- 正文: Roboto Regular
- 代码: Roboto Mono
- 支持动态字体大小（系统设置）

#### 间距规范

- Micro: 4dp
- Small: 8dp
- Medium: 16dp
- Large: 24dp
- XLarge: 32dp

---

## 📦 发布策略

### 分发渠道

1. **GitHub Releases** (主渠道)
   - 每个版本发布 APK + AAB
   - 提供更新日志
   - 自动构建（GitHub Actions）

2. **Google Play** (计划中)
   - 需解决政策合规问题
   - 可能需要修改应用描述
   - 使用 App Bundle 格式

3. **F-Droid** (开源渠道)
   - 完全开源版本
   - 移除 Firebase 等闭源组件
   - 社区维护

4. **自建分发网站** (备用)
   - CDN 加速
   - 版本更新 API
   - 下载统计

### 版本命名规则

```
v<major>.<minor>.<patch>[-<pre-release>]

示例:
v1.0.0        # 正式版
v1.1.0-beta.1 # 测试版
v2.0.0-rc.2   # 候选版
```

---

## 🤝 社区与贡献

### 贡献者招募

**当前急需**:
- [ ] iOS 开发者（Swift）
- [ ] 视频网站爬虫专家
- [ ] UI/UX 设计师
- [ ] 文档工程师（多语言支持）

**贡献指南**:
1. Fork 仓库
2. 创建功能分支 (`feature/xxx`)
3. 提交 PR（附详细说明）
4. 通过 CI 检查（Lint + Test）
5. Code Review 后合并

### 开源许可

**当前**: Apache License 2.0  
**未来考虑**: AGPLv3（防止闭源商业化）

---

## 📊 KPI 与成功指标

### 技术指标

| 指标 | 当前值 | v1.5 目标 | v2.0 目标 |
|------|--------|----------|----------|
| 启动时间 | 1.5s | <1s | <0.5s |
| 内存占用 | 180MB | <150MB | <120MB |
| APK 体积 | 8.5MB | <6MB | <5MB |
| 崩溃率 | N/A | <0.5% | <0.1% |
| 测试覆盖率 | 0% | 50% | 70% |

### 用户指标

| 指标 | 当前值 | 短期目标 | 长期目标 |
|------|--------|---------|---------|
| GitHub Stars | N/A | 1,000 | 10,000 |
| 活跃用户 | N/A | 5,000 | 50,000 |
| 用户留存率 (D7) | N/A | 60% | 80% |
| 平均评分 | N/A | 4.5+ | 4.8+ |

---

## 🎯 总结与优先级

### 立即行动（本月内）

1. 🔴 **启用代码混淆** - 保护知识产权
2. 🔴 **数据加密** - 保障用户隐私
3. 🔴 **Crashlytics** - 建立监控体系

### 下季度重点（Q3 2026）

1. 🟡 **完成四站视频支持** - 功能完整性
2. 🟡 **架构重构** - 技术债务清理
3. 🟡 **JavDB 集成** - 用户体验提升

### 年度目标（2026）

1. 🟢 **发布 v2.0 社区版** - 建立用户生态
2. 🟢 **iOS 版本上线** - 扩大用户群
3. 🟢 **测试覆盖率达 70%** - 代码质量保障

---

**文档维护**: 每季度更新一次路线图  
**反馈渠道**: GitHub Issues / Discussions  
**项目主页**: [github.com/fekilooo/javbrowser](https://github.com/fekilooo/javbrowser)
