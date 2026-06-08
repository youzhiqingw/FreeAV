# JAV Browser 功能扩展方案（研究参考）

> **⚠️ 技术栈说明**  
> - **当前项目**: Android Native App (Kotlin + WebView + NanoHTTPD)  
> - **本文档**: 基于 Python 开源项目的研究分析（仅供参考借鉴）  
> - **参考项目**: JableTV-MissAV-Downloader-GUI-2026 (Python) · missAV_api (Python) · NASSAV (Python) · jav-play (Browser Extension)  
> - **实际实施**: 请参阅 [IMPLEMENTATION_GUIDE.md](./IMPLEMENTATION_GUIDE.md) 中的 Android/Kotlin 实现方案

---

## 📌 阅读指引

本文档记录了对四个开源项目的深度分析，包括：
- ✅ **可借鉴的算法逻辑**: M3U8 解析、多镜像切换、元数据抓取
- ✅ **问题解决方案**: Cloudflare 绕过、中文时间解析、域名探活
- ❌ **不适用部分**: Python GUI 实现、浏览器扩展架构、Go 本地服务

**使用建议**: 提取算法思路和解决方案，转换为 Kotlin/Android 实现

---

# javbrowser 功能扩展方案

> 基于四个开源项目的深度分析：问题汇总、功能对比、解决路径与扩展建议  
> 参考项目：Alos21750/JableTV-MissAV-Downloader-GUI-2026 · EchterAlsFake/missAV_api · Satoing/NASSAV · aizhimou/jav-play

---

## 一、各项目功能与问题概览

### 1.1 Alos21750/JableTV-MissAV-Downloader-GUI-2026

**技术栈：** Python 3.8+ · CustomTkinter (Material Design) · ThreadPoolExecutor · Token-bucket 限速 · PyInstaller 打包

**核心功能：**

- 内置浏览器（缩略图浏览 · 搜索 · 翻页 · 分类/女优/标签导航）
- 多选批量下载 + 最多 10 路并行
- M3U8/HLS 流解析 + AES-128 自动解密
- TS 片段无需 FFmpeg 自动合并为 MP4
- Token-bucket 全局限速（1~15 MB/s 可调）
- SmallTool：每 24 小时自动抓新中文字幕片，SQLite/JSON 去重

**已记录的 Issue 及修复轨迹：**

| Issue | 问题描述 | 根因 | 修复版本 |
|-------|----------|------|----------|
| #1 | FC2 影片无法下载 | 正则 `[a-z]{2,5}` 不匹配 `fc2-ppv`（含数字） | v2.1.0 |
| #2 | 粘贴列表页 URL 无法批量入队 | 缺少列表页爬取逻辑 | v2.1.2 |
| #3 | 无法跳页 / 无全选按钮 | GUI 功能缺失 | v2.1.2 |
| #4 | 分类描述语言与界面不符 | MissAV 语言前缀处理错误 | v2.1.5 |
| #5 | 粘贴列表 URL 点击下载无响应 | 状态反馈缺失 + HTML 布局解析失败 | v2.1.5 |
| #6 | 版本号格式混乱 | 硬编码字符串散布各处 | v2.1.5 |
| #7 | MissAV URL 含冗余 `/cn/` 前缀 | URL 拼接逻辑错误 | v2.2.0 |
| #8 | 无法选择画质 | 设置页缺少质量选项 | v2.2.0 |
| #9 | SmallTool 首次运行触发大量下载 | 基准日期硬编码为 `2026-04-01` | v2.2.0 |
| — | `一星期前` 等中文数字时间戳解析失败 | 正则只处理阿拉伯数字 + 仅支持「週/周」 | v2.0.3 |
| — | 选中 3 个以上视频死锁 | Tkinter 主线程锁竞争 | v2.0.1 |
| — | 批量下载触发 Cloudflare 限速 | 并发 prepare 阶段同时请求目标站 | v1.1.0 |

**尚未解决 / 潜在风险：**
- Cloudflare Bot Management 升级后 `requests` 直接请求可能失效（无浏览器指纹）
- Windows SmartScreen 未签名警告（用户体验差）
- 仅支持 Windows（`.exe` 打包）；macOS/Linux 用户体验降级
- 无代理配置 UI，对大陆用户不友好

---

### 1.2 EchterAlsFake/missAV_api

**技术栈：** Python 3.10+ · httpx (异步) · BeautifulSoup4 · LGPL-3.0

**核心功能：**

- 异步 Client：`get_video(url)` → Video 对象（title、封面、时长、元数据）
- 多下载器：`threaded`（多线程）/ `FFMPEG`（外部 FFmpeg）/ `default`
- 搜索接口 · 元数据提取 · 质量选择 (`best`/`worst`)
- 已发布至 PyPI：`pip install missAV_api`

**已知问题与限制：**

- 作为非官方 API，站点 HTML 结构变更会直接导致解析失效（无 stable API 保障）
- `httpx` 异步模型与同步代码需要用 `asyncio.run()` 桥接，集成门槛略高
- 不处理 Cloudflare 挑战（依赖站点对普通 httpx 的可访问性）
- 不包含代理支持配置，国内用户需自行封装
- 仅支持 missav.ws，不支持 missav.ai 或镜像域名切换

---

### 1.3 Satoing/NASSAV

**技术栈：** Python 3.11.2 · SQLite (去重) · FFmpeg (合并) · Go 1.22.6 (HTTP API 服务) · curl/pycurl

**核心功能：**

- 插件化多源下载器：每个 Downloader 实现统一接口，权重可配置
  ```json
  { "downloaderName": "MissAV", "domain": "missav.ai", "weight": 300 },
  { "downloaderName": "Jable",  "domain": "jable.tv",  "weight": 500 }
  ```
- 从 JavBus 自动刮削元数据（封面、海报、演员、标签）
- 自动生成 `.nfo` 文件，兼容 Jellyfin / Emby / Kodi
- SQLite 全局去重（番号级别，不重复下载）
- Go HTTP API 服务：远程提交番号任务
- 文件锁：同一时刻只有一个下载任务运行（保护磁盘 IO）
- 代理感知：`IsNeedVideoProxy` 控制下载链路是否走代理

**已记录的 Issue 及问题：**

| Issue | 问题描述 | 根因 |
|-------|----------|------|
| #22 | MissAV 相关站点全部 HTTP 403 | missav.ai 启用 Cloudflare Bot Protection；missav.ws 自签证书 curl 校验失败 |
| #20 | 请求支持 ggjav 作为新数据源 | MissAV 被大陆封锁，社区需要替代源 |
| — | 首次配置门槛高 | 需要 Python + Go + FFmpeg 三套环境 |
| — | 无 Web UI | 只能 CLI / HTTP API 操作，非技术用户难以使用 |

**架构亮点（可借鉴）：**
- Downloader 接口设计：`可以字如注册新数据源，不修改核心逻辑`
- 权重调度：多源时按权重随机选，某源失败自动降权重 fallback

---

### 1.4 aizhimou/jav-play

**技术栈：** TypeScript · WXT 框架 (跨浏览器扩展) · CSS · Chrome MV3

**核心功能：**

- 在 JavDB / JavLibrary 影片详情页注入「跳转播放」按钮
- 跳转模式：打开对应 MissAV/Jable 播放页（新标签）
- 本地播放模式：通过自定义 URL Scheme（`iina://`、`potplayer://`）唤起本地播放器
- Popup 设置：可切换视频源（missav.ws 默认）/ 开关功能
- 支持 IINA（macOS 推荐）、PotPlayer（Windows 推荐）

**已知问题与限制：**

| 问题 | 根因 |
|------|------|
| 不支持 mpv 本地播放 | mpv 未自动注册 OS 协议处理器，注册流程复杂 |
| 不支持 mpc-be | 同上 |
| 视频源仅 2 个可选 | 硬编码了 missav.ws / jable.tv，无扩展接口 |
| 跳转目标不保证有资源 | 仅基于番号拼接 URL，不验证资源是否存在 |
| 仅支持 JavDB + JavLibrary | 不支持 JavBus、MGStage、DMM 等 |
| MV3 限制 | Chrome MV3 移除 `webRequestBlocking`，拦截行为受限 |

---

## 二、跨项目共性问题总结

### 2.1 Cloudflare Bot Management（最高频问题）

**现象：** HTTP 403 / 503 / CAPTCHA  
**触发场景：** 批量请求、无浏览器指纹的 `requests`/`httpx`/`curl`

**问题分级：**

```
Level 1（JS Challenge）— httpx 可通过，设置正确的 User-Agent + Headers 即可
Level 2（Managed Challenge）— 需要完整执行 JS，要用 Playwright/Puppeteer 或 CF-Clearance
Level 3（Bot Fight Mode）— TLS 指纹 + 行为分析，需要 curl-impersonate 或真实浏览器
```

**已有解法：**

| 方案 | 适用场景 | 代价 |
|------|----------|------|
| 伪造 UA + Headers | Level 1 | 低 |
| `cloudscraper` 库 | Level 1~2 | 中（不稳定） |
| Playwright / Puppeteer | Level 2 | 高（需要浏览器进程） |
| `curl-impersonate` / `tls-client` | Level 3 | 高（需要原生依赖） |
| 本地扩展注入（同浏览器会话共享 Cookie） | 扩展集成时 Level 1~2 | 零额外代价（最佳） |

### 2.2 域名/URL 结构不稳定

MissAV 历史域名变更：`missav.com` → `missav.ws` → `missav.ai`  
URL 前缀结构变更：`/cn/` · `/en/` · `/dm13/` (CDN 镜像)

**解法：** 域名映射表外置配置 + 自动探活（HTTP HEAD 请求）：

```python
MISSAV_DOMAINS = ["missav.ai", "missav.ws", "missav.com"]

def probe_domain(domains: list[str]) -> str:
    for domain in domains:
        try:
            r = requests.head(f"https://{domain}", timeout=5)
            if r.status_code < 400:
                return domain
        except Exception:
            continue
    raise RuntimeError("All MissAV domains unreachable")
```

### 2.3 中文相对时间字符串解析

**问题字符串举例：** `一星期前` · `3 個月前` · `二十分鐘前`  
**正确处理：**

```python
CN_NUM = {"一":1,"二":2,"兩":2,"三":3,"四":4,"五":5,"六":6,"七":7,"八":8,"九":9,"十":10}
UNIT_MAP = {
    "秒": "seconds", "分鐘": "minutes", "小時": "hours",
    "天": "days", "週": "weeks", "周": "weeks", "星期": "weeks", "個月": "months", "年": "years"
}

def parse_relative_time(s: str) -> datetime:
    # 匹配：数字（阿拉伯 or 中文） + 时间单位 + 前
    m = re.search(r'(\d+|[一二兩三四五六七八九十百千]+)\s*(' + '|'.join(UNIT_MAP) + r')\s*前', s)
    if not m:
        return datetime.now()
    raw, unit = m.group(1), m.group(2)
    n = int(raw) if raw.isdigit() else sum(CN_NUM.get(c, 0) for c in raw)
    return datetime.now() - timedelta(**{UNIT_MAP[unit]: n})
```

### 2.4 本地播放器协议注册

**问题：** mpv / mpc-be 没有自动注册自定义 URL Scheme  
**解法（Windows 注册表写入）：**

```powershell
# Windows: 注册 mpv:// 协议
New-Item -Path "HKCU:\Software\Classes\mpv" -Force
Set-ItemProperty -Path "HKCU:\Software\Classes\mpv" -Name "URL Protocol" -Value ""
New-Item -Path "HKCU:\Software\Classes\mpv\shell\open\command" -Force
Set-ItemProperty -Path "HKCU:\Software\Classes\mpv\shell\open\command" `
  -Name "(Default)" -Value '"C:\Program Files\mpv\mpv.exe" "%1"'
```

**解法（macOS）：** 通过 `Info.plist` 中 `CFBundleURLTypes` 注册，配合 Swift helper app

---

## 三、javbrowser 功能扩展方案

### 3.1 扩展定位

`javbrowser` 定位为**浏览器扩展 + 本地服务的混合架构**，作为 jav-play 的增强版本：

```
┌─────────────────────────────────────────────────────┐
│                   浏览器扩展层                        │
│  JavDB · JavBus · JavLibrary · DMMゲームズ · MGStage │
│         ↓  番号识别 + UI 注入                         │
├─────────────────────────────────────────────────────┤
│              本地 HTTP 服务 (Go/Python)               │
│  元数据刮削 · 可用性探测 · 本地播放器唤起 · 下载队列   │
└─────────────────────────────────────────────────────┘
```

### 3.2 新增功能模块

#### A. 多源视频可用性探测

**现状：** jav-play 仅拼接 URL 跳转，不验证资源是否存在  
**改进：** 跳转前向本地服务发起探测请求，按优先级返回最可用的播放源

```typescript
// content_script.ts
async function findBestSource(code: string): Promise<SourceResult> {
  const resp = await fetch(`http://localhost:17777/probe?code=${code}`);
  return resp.json(); // { url, source, quality }
}
```

```python
# local_service/probe.py — 并发探测多个数据源
SOURCES = [
    ("MissAV", "https://missav.ai/{code}"),
    ("Jable",  "https://jable.tv/videos/{code}/"),
    ("HohoJ",  "https://hohoj.tv/videos/{code}/"),
]

async def probe_all(code: str) -> list[dict]:
    async with aiohttp.ClientSession() as session:
        tasks = [check_source(session, name, url.format(code=code)) for name, url in SOURCES]
        results = await asyncio.gather(*tasks, return_exceptions=True)
    return [r for r in results if isinstance(r, dict) and r.get("available")]
```

#### B. 扩展视频源注册表（插件化）

借鉴 NASSAV 的 Downloader 权重架构，将视频源配置外置：

```json
{
  "sources": [
    { "name": "MissAV",  "urlPattern": "https://missav.ai/{code}",   "weight": 300, "enabled": true  },
    { "name": "Jable",   "urlPattern": "https://jable.tv/videos/{code}/", "weight": 500, "enabled": true  },
    { "name": "HohoJ",   "urlPattern": "https://hohoj.tv/videos/{code}/", "weight": 200, "enabled": true  },
    { "name": "GGJav",   "urlPattern": "https://ggjav.com/{code}",    "weight": 100, "enabled": false }
  ]
}
```

扩展 Popup 中提供可视化开关，无需重新打包即可启用新数据源。

#### C. 扩展源站支持

**现状：** 仅支持 JavDB + JavLibrary  
**新增支持站点：**

| 站点 | 注入点 | 番号提取方式 |
|------|--------|--------------|
| JavDB (`javdb.com`) | ✅ 已有 | URL path + `data-id` |
| JavLibrary (`javlibrary.com`) | ✅ 已有 | URL path |
| JavBus (`javbus.com`) | 新增 | URL path `/`结尾前的段落 |
| MGStage (`mgstage.com`) | 新增 | `id=SIRO-XXXX` query param |
| DMM (`dmm.co.jp`) | 新增 | `cid=` query param |
| FANZA (`fanza.com`) | 新增 | `itemid=` param |

```typescript
// entrypoints/content.ts — 站点路由
const SITE_MATCHERS: SiteMatcher[] = [
  { hostname: "javdb.com",       extract: (url) => url.pathname.split("/").pop() ?? "" },
  { hostname: "javlibrary.com",  extract: (url) => new URLSearchParams(url.search).get("v") ?? "" },
  { hostname: "javbus.com",      extract: (url) => url.pathname.replace(/^\//, "") },
  { hostname: "mgstage.com",     extract: (url) => new URLSearchParams(url.search).get("id") ?? "" },
];
```

#### D. 本地播放器完整支持（含 mpv/mpc-be）

**架构：** 本地 Go 辅助服务（参考 jav-play-go 思路）负责 URL Scheme 注册 + 播放器调用

```go
// local_service/player.go
type Player struct {
    Name    string
    Scheme  string
    CmdArgs func(url string) []string
}

var Players = []Player{
    {
        Name:   "PotPlayer",
        Scheme: "potplayer",
        CmdArgs: func(url string) []string {
            return []string{`C:\Program Files\DAUM\PotPlayer\PotPlayerMini64.exe`, url}
        },
    },
    {
        Name:   "mpv",
        Scheme: "mpv-javbrowser",
        CmdArgs: func(url string) []string {
            return []string{"mpv", "--no-terminal", url}
        },
    },
    {
        Name:   "IINA",
        Scheme: "iina",
        CmdArgs: func(url string) []string {
            return []string{"open", "-a", "IINA", url}
        },
    },
    {
        Name:   "mpc-be",
        Scheme: "mpc-be-javbrowser",
        CmdArgs: func(url string) []string {
            return []string{`C:\Program Files\MPC-BE x64\mpc-be64.exe`, url}
        },
    },
}
```

安装脚本在首次启动时自动写入注册表（Windows）或 `Info.plist`（macOS），实现零感知注册。

#### E. Cloudflare 旁路（利用扩展同源会话）

**关键洞察：** 浏览器扩展的 `content_script` 运行在与用户真实浏览会话相同的上下文中，CF Cookie（`cf_clearance`）可以直接读取并传递给本地服务，无需任何绕过手段：

```typescript
// background.ts — 提取 CF cookie 传给本地服务
async function getCFClearance(domain: string): Promise<string | null> {
  const cookies = await browser.cookies.getAll({ domain, name: "cf_clearance" });
  return cookies[0]?.value ?? null;
}

// 下载请求时携带 cookie
async function requestDownload(code: string) {
  const cfClear = await getCFClearance("missav.ai");
  await fetch("http://localhost:17777/download", {
    method: "POST",
    body: JSON.stringify({ code, cf_clearance: cfClear }),
  });
}
```

本地服务使用 `cf_clearance` 构造请求头，有效规避 CF 检查：

```python
# local_service/downloader.py
def build_headers(cf_clearance: str) -> dict:
    return {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) ...",
        "Cookie": f"cf_clearance={cf_clearance}",
        "Referer": "https://missav.ai/",
    }
```

#### F. 元数据侧边栏（内嵌式）

在 JavDB/JavBus 影片详情页右侧注入折叠侧边栏，显示：

```
┌─ javbrowser ──────────────────────────┐
│ 🎬 标题（截断）                        │
│ 📅 发行日：2026-03-15                  │
│ ⏱ 时长：120 分钟                       │
│ 🌐 可用源：MissAV ✓  Jable ✓  HohoJ ✗│
│ 📥 [在线播放▾]  [发送到本地]  [下载]   │
│  ↳ MissAV (最高画质)                   │
│  ↳ Jable (最高画质)                    │
└────────────────────────────────────────┘
```

#### G. 下载队列管理（通过本地服务）

参考 JableTV-Downloader 的队列架构，在本地服务中实现：

```python
# local_service/queue_manager.py
import asyncio
from collections import deque
from dataclasses import dataclass

@dataclass
class DownloadTask:
    code: str
    source: str
    url: str
    quality: str = "best"
    status: str = "pending"  # pending|downloading|done|failed

class DownloadQueue:
    def __init__(self, max_concurrent: int = 3):
        self._queue: deque[DownloadTask] = deque()
        self._sem = asyncio.Semaphore(max_concurrent)
    
    async def add(self, task: DownloadTask):
        self._queue.append(task)
        asyncio.create_task(self._process(task))
    
    async def _process(self, task: DownloadTask):
        async with self._sem:
            task.status = "downloading"
            await download_m3u8(task)
            task.status = "done"
```

#### H. Android 端扩展（Han1meViewer 集成方向）

针对 Android 平台（与 Han1meViewer 技术栈对齐），参考以上设计，提供 Kotlin 实现接口：

```kotlin
// SiteParser 插件接口（借鉴 NASSAV Downloader 抽象）
interface JavSiteParser {
    val siteName: String
    val weight: Int
    suspend fun buildVideoUrl(code: String): String?
    suspend fun fetchM3U8(videoUrl: String): String?
}

// MissAV 实现
class MissAVParser : JavSiteParser {
    override val siteName = "MissAV"
    override val weight = 300
    private val probeDomains = listOf("missav.ai", "missav.ws")
    
    override suspend fun buildVideoUrl(code: String): String? {
        val domain = probeDomains.firstOrNull { isReachable(it) } ?: return null
        return "https://$domain/$code"
    }

    override suspend fun fetchM3U8(videoUrl: String): String? {
        // 使用 OkHttp + Jsoup 解析 script 中的 m3u8 URL
        val html = okHttpClient.get(videoUrl)
        return Jsoup.parse(html)
            .select("script")
            .mapNotNull { Regex("""source src='(https?://[^']+\.m3u8[^']*)'""").find(it.data())?.groupValues?.get(1) }
            .firstOrNull()
    }
}

// 统一调度器（带权重 + fallback）
class VideoSourceDispatcher(private val parsers: List<JavSiteParser>) {
    suspend fun dispatch(code: String): Pair<JavSiteParser, String>? {
        val sorted = parsers.sortedByDescending { it.weight }
        for (parser in sorted) {
            val url = parser.buildVideoUrl(code) ?: continue
            val m3u8 = parser.fetchM3U8(url) ?: continue
            return parser to m3u8
        }
        return null
    }
}
```

---

## 四、实施优先级路线图

```
Phase 1 — 基础稳定（2周）
├── 域名探活机制（统一处理 missav.ai/ws 切换）
├── 中文相对时间解析修复（含十位数中文数字）
├── FC2 番号正则通用化（支持 fc2-ppv-* 格式）
└── URL 结构验证单元测试（防止静默解析失败）

Phase 2 — 扩展架构（3周）
├── 视频源注册表（JSON 外置配置 + 权重调度）
├── 本地 HTTP 服务框架（Go 或 Python FastAPI）
├── 多源并发可用性探测
└── 基础下载队列（SQLite 任务持久化 + 去重）

Phase 3 — 集成增强（4周）
├── 浏览器扩展 CF Cookie 提取 + 传递给本地服务
├── mpv / mpc-be 协议注册器（Windows 注册表 / macOS plist）
├── JavBus / MGStage 页面注入支持
├── 元数据侧边栏（可用源状态 + 操作按钮）
└── Android 端 SiteParser 插件接口（Han1meViewer 集成）

Phase 4 — 完善（持续）
├── NFO 元数据生成（JavBus 刮削，兼容 Jellyfin/Emby）
├── 断点续传 + 部分片段清理
├── 代理配置 UI（分流规则：下载走代理，元数据直连）
└── 签名打包 / 自动更新机制
```

---

## 五、技术选型对比

| 功能模块 | 推荐方案 | 备选 | 说明 |
|----------|----------|------|------|
| 本地服务 | Go (net/http) | Python FastAPI | Go 二进制无运行时依赖，分发简单 |
| HLS 解析 | Python m3u8 库 | Go m3u8 库 | Python 生态更丰富 |
| CF 绕过 | 扩展注入 Cookie | cloudscraper | 扩展方案零代价，最稳定 |
| 浏览器扩展框架 | WXT (TypeScript) | Plasmo | WXT 支持 MV3，跨浏览器 |
| Android 网络 | OkHttp + Retrofit | Ktor | 与 Han1meViewer 现有栈一致 |
| 元数据数据库 | SQLite (Room on Android) | JSON 文件 | Room 已在 Han1meViewer 中使用 |
| 播放器协议注册 | Go 安装助手 | Python winreg | 需要原生 OS API 操作 |
| 并发下载 | ThreadPoolExecutor | asyncio | 前者调试更简单，后者吞吐更高 |

---

## 六、参考资源

- [Alos21750/JableTV-MissAV-Downloader-GUI-2026](https://github.com/Alos21750/JableTV-MissAV-Downloader-GUI-2026) — GUI 下载器参考实现
- [EchterAlsFake/missAV_api](https://github.com/EchterAlsFake/missAV_api) — missAV 异步 API 封装
- [Satoing/NASSAV](https://github.com/Satoing/NASSAV) — NAS 自动下载插件化架构参考
- [aizhimou/jav-play](https://github.com/aizhimou/jav-play) — 浏览器扩展 + 本地播放器唤起
- [akiirui/mpv-handler](https://github.com/akiirui/mpv-handler) — mpv 协议注册参考实现（Rust）
- [WXT Framework](https://wxt.dev/) — 跨浏览器扩展开发框架
- JavBus API 元数据字段文档（社区 wiki）
