# 安全审计报告

> **项目**: JAV Browser (Android 应用)
> **审计日期**: 2026-06-07
> **审计范围**: 仅关注是否会对最终用户造成危害

---

## 目录

- [HIGH 风险](#high-风险)
- [MEDIUM 风险](#medium-风险)
- [LOW 风险](#low-风险)
- [修复建议汇总](#修复建议汇总)

---

## HIGH 风险

### 1. 远程配置篡改风险

**风险描述**:

应用从远程 GitHub URL (`https://raw.githubusercontent.com/fekilooo/javbrowser/refs/heads/main/ad-filter-rules.json`) 下载 JSON 配置文件，并直接解析其中的域名和规则列表注入到应用逻辑中。攻击者如果控制该 GitHub 仓库或进行中间人攻击，可以：

1. 修改目标域名指向恶意站点（钓鱼/恶意软件分发）
2. 注入恶意广告拦截规则，可能导致用户访问被劫持的域名

**代码位置**:

- `AdFilterRules.kt:20` - `DEFAULT_CLOUD_URL` 硬编码了远程配置地址
- `AdFilterRules.kt:190-219` - `updateRulesFromCloud()` 方法下载并应用远程配置
- `MainActivity.kt:74-82` - 启动时自动更新云端规则

**修复建议**:

- 对下载的配置文件进行签名验证（使用嵌入的公钥验证 JSON 签名）
- 使用 HTTPS 证书固定（Certificate Pinning）防止中间人攻击
- 限制可配置的域名范围，不允许指向任意域名

---

### 2. APK 自动下载与安装权限

**风险描述**:

应用请求了 `REQUEST_INSTALL_PACKAGES` 权限，并在 WebView 中拦截包含 `.apk` 或 `down_ra` 的链接自动触发外部下载安装。这可能导致用户在浏览成人网站时被诱导安装恶意应用。

**代码位置**:

- `AndroidManifest.xml:6` - `<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />`
- `MainActivity.kt:252-255` - `downloadAndInstallApk()` 方法
- `MainActivity.kt:1183-1190` - 首页硬编码了第三方游戏推广链接

**修复建议**:

- 移除 `REQUEST_INSTALL_PACKAGES` 权限，或至少添加用户确认对话框
- 在触发 APK 下载前显示明确的警告提示
- 审查首页推广链接的安全性

---

## MEDIUM 风险

### 3. JavaScript 注入与 WebView 安全风险

**风险描述**:

1. 应用启用了 `JavaScriptEnabled` 和 `domStorageEnabled`，并向 WebView 暴露了 `Android` JavaScript 接口，包含 `navigateToUrl()` 等方法可被网页调用
2. 大量注入 JavaScript 代码到第三方成人网站执行，包括 DOM 操作、点击模拟等
3. `evaluateJavascript` 执行来自网页 sessionStorage 的内容（`scrollPos__` 键值），存在潜在的 XSS 风险

**代码位置**:

- `MainActivity.kt:201-239` - 启用 JS 并添加 JS 接口
- `MainActivity.kt:502-726` - 向第三方网站注入大量 JS 代码
- `MainActivity.kt:338-357` - 从 sessionStorage 读取并执行内容

**修复建议**:

- 限制 `@JavascriptInterface` 方法的权限，添加 URL 白名单验证
- 对从网页存储读取的数据进行严格过滤和验证
- 考虑使用 Content Security Policy

---

### 4. 本地 HTTP 代理服务器安全风险

**风险描述**:

`VideoProxyServer` 在本地启动 HTTP 代理（非 HTTPS），将用户的视频 URL、Referer、Cookie 等敏感信息通过未加密的本地通道传输。其他应用可能通过扫描本地端口获取这些敏感信息。

**代码位置**:

- `VideoProxyServer.kt:19` - 启动本地 HTTP 服务器（端口 0，系统分配）
- `VideoProxyServer.kt:33-38` - 构建包含敏感信息的代理 URL
- `VideoProxyServer.kt:47-53` - 解码并处理 URL、Cookie 等参数

**修复建议**:

- 使用 HTTPS 本地服务（自签名证书）
- 添加请求来源验证（验证请求来自本应用）
- 代理 URL 中的敏感信息使用加密而非简单的 URL 编码

---

### 5. 隐私数据收集与传输

**风险描述**:

1. 应用访问成人视频网站并提取视频流 URL，这些浏览记录可能通过系统日志、剪贴板等方式泄露
2. 视频 URL 被复制到系统剪贴板，可能被其他应用读取
3. WebView Cookie 被提取并用于代理请求

**代码位置**:

- `MainActivity.kt:959-963` - 视频 URL 复制到剪贴板
- `VideoProxyServer.kt:82-83` - Cookie 被提取并转发
- `MainActivity.kt:952-954` - 从 CookieManager 获取 Cookie

**修复建议**:

- 避免使用系统剪贴板传输敏感 URL
- 添加隐私政策声明数据收集范围
- 考虑使用内部安全存储而非剪贴板

---

## LOW 风险

### 6. 图标伪装功能

**风险描述**:

应用提供图标伪装功能（计算器、笔记、文件管理器），允许用户隐藏应用真实用途。虽然这本身是隐私功能，但可能被用于隐藏不当内容，且 `activity-alias` 全部设置为 `exported="true"` 增加了攻击面。

**代码位置**:

- `AndroidManifest.xml:31-68` - 多个 exported activity alias
- `AppIconManager.kt` - 图标切换逻辑

**修复建议**:

- 将 `activity-alias` 的 `exported` 设为 `false`（不需要外部应用启动）
- 明确告知用户此功能的用途

---

### 7. 生物识别 PIN 码明文存储

**风险描述**:

应用使用生物识别认证，但 PIN 码以明文形式存储在 SharedPreferences 中。

**代码位置**:

- `PrivacySettings.kt:47-48` - PIN 码明文存储

**修复建议**:

- 使用 Android Keystore 加密存储 PIN 码
- 或至少使用哈希（如 bcrypt）存储

---

### 8. 依赖库风险

**风险描述**:

1. 使用 `org.nanohttpd:nanohttpd:2.3.1` 作为本地 HTTP 服务器，该版本较旧（2018年），可能存在已知漏洞
2. 未启用 ProGuard/R8 代码混淆（`isMinifyEnabled = false`），代码容易被逆向分析

**代码位置**:

- `app/build.gradle.kts:50` - NanoHTTPD 依赖
- `app/build.gradle.kts:22` - `isMinifyEnabled = false`

**修复建议**:

- 考虑升级到更新的 HTTP 服务器库或检查 NanoHTTPD 安全公告
- 发布版本启用代码混淆

---

## 修复建议汇总

| 优先级 | 风险项 | 修复措施 |
|--------|--------|----------|
| **HIGH** | 远程配置篡改 | 添加签名验证 + 证书固定 |
| **HIGH** | APK 自动安装 | 添加用户确认 + 移除不必要的权限 |
| **MEDIUM** | WebView JS 注入 | 限制 JS 接口权限 + 过滤输入 |
| **MEDIUM** | 本地代理安全 | 使用 HTTPS + 请求来源验证 |
| **MEDIUM** | 隐私数据泄露 | 避免剪贴板传输敏感数据 |
| **LOW** | 图标伪装 | 设置 exported="false" |
| **LOW** | PIN 明文存储 | 使用 Keystore 加密 |
| **LOW** | 依赖库 | 升级 NanoHTTPD + 启用混淆 |

---

## 总体评估

| 风险类别 | 数量 | 最高等级 |
|---------|------|---------|
| 远程代码执行/配置篡改 | 1 | **HIGH** |
| 恶意软件安装权限滥用 | 1 | **HIGH** |
| WebView/JS 安全风险 | 1 | **MEDIUM** |
| 本地代理安全风险 | 1 | **MEDIUM** |
| 隐私数据泄露 | 1 | **MEDIUM** |
| 图标伪装/导出组件 | 1 | **LOW** |
| 敏感数据存储 | 1 | **LOW** |
| 依赖风险 | 1 | **LOW** |

> **结论**: 该应用存在 **2 个 HIGH 级别** 和 **3 个 MEDIUM 级别** 的安全风险，主要集中于远程配置篡改和 APK 自动安装功能。建议优先修复签名验证和安装权限问题。
