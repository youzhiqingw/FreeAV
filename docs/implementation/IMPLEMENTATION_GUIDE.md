# JAV Browser - Android 实施指南

> **文档版本**: v1.0  
> **技术栈**: Android (Kotlin) + WebView + NanoHTTPD  
> **基于**: missav 文件夹研究成果（Python 项目算法提取）  
> **更新日期**: 2026-06-07

---

## 📋 概述

本文档基于对开源 Python 项目的研究（JableTV-Modern、missAV_api、NASSAV、jav-play-go），**提取核心算法和解决方案**，转换为适合 **javbrowser Android 应用**的 Kotlin 实现方案。

### 文档定位
- ✅ **可实施内容**: 多镜像切换、M3U8 下载、元数据抓取、Cloudflare 绕过
- ✅ **技术栈**: 100% 基于 Kotlin/Android/Gradle
- ❌ **不包含**: Python GUI、浏览器扩展、Go 服务器（仅借鉴算法逻辑）

---

## 🎯 核心功能映射

### Python 项目 → Android 实现对照表

| Python 项目功能 | Android 实现方案 | 优先级 |
|----------------|------------------|--------|
| **多镜像域名切换** | DomainConfig 扩展 + 自动故障转移 | 🔴 高 |
| **M3U8 解析下载** | ExoPlayer + 本地缓存 | 🔴 高 |
| **元数据抓取 (JavBus)** | Jsoup + Retrofit | 🟡 中 |
| **批量下载队列** | WorkManager + Room 数据库 | 🟡 中 |
| **GUI 浏览器** | 已实现 (WebView) | ✅ 完成 |
| **Cloudflare 绕过** | OkHttp Interceptor + User-Agent 轮换 | 🟡 中 |
| **NAS 同步** | SMB/FTP 客户端库 | 🟢 低 |

---

## 🛠️ 技术实施方案

### 1. 多镜像域名自动切换 (基于 JableTV-Modern v2.3.2)

#### 问题
- 当前 `DomainConfig` 仅支持单域名
- Cloudflare 封锁导致部分地区无法访问

#### 解决方案

**DomainConfig.kt 扩展**:
```kotlin
object DomainConfig {
    // 多镜像域名列表（优先级从高到低）
    private val MISSAV_MIRRORS = listOf(
        "missav.ai",
        "missav.ws", 
        "missav123.com",
        "missav.live"
    )
    
    private val JABLE_MIRRORS = listOf(
        "jable.tv",
        "fs1.app"
    )
    
    private val ROU_MIRRORS = listOf(
        "rouva3.xyz",
        "rou.video"
    )
    
    private val AVJOY_MIRRORS = listOf(
        "avjoy.me"
    )
    
    // 当前活动索引（SharedPreferences 存储）
    private var missavIndex = 0
    private var jableIndex = 0
    
    /**
     * 获取当前活动域名
     */
    fun getMissavDomain(): String = MISSAV_MIRRORS[missavIndex]
    fun getJableDomain(): String = JABLE_MIRRORS[jableIndex]
    
    /**
     * 切换到下一个镜像（故障转移）
     * @return 是否还有可用镜像
     */
    fun switchToNextMissavMirror(): Boolean {
        missavIndex++
        if (missavIndex >= MISSAV_MIRRORS.size) {
            missavIndex = 0
            return false // 已尝试所有镜像
        }
        saveMirrorIndex("missav", missavIndex)
        return true
    }
    
    fun switchToNextJableMirror(): Boolean {
        jableIndex++
        if (jableIndex >= JABLE_MIRRORS.size) {
            jableIndex = 0
            return false
        }
        saveMirrorIndex("jable", jableIndex)
        return true
    }
    
    /**
     * 自动重试逻辑（在 WebViewClient 中使用）
     */
    suspend fun loadUrlWithMirrorFallback(
        webView: WebView,
        originalUrl: String,
        onAllMirrorsFailed: () -> Unit
    ) {
        var url = originalUrl
        var attempts = 0
        
        while (attempts < MISSAV_MIRRORS.size) {
            try {
                val response = testUrlAvailability(url)
                if (response.isSuccessful) {
                    webView.loadUrl(url)
                    return
                }
                
                // Cloudflare 403 错误，切换镜像
                if (response.code == 403) {
                    if (!switchToNextMissavMirror()) {
                        onAllMirrorsFailed()
                        return
                    }
                    url = url.replace(MISSAV_MIRRORS[attempts], getMissavDomain())
                }
            } catch (e: Exception) {
                Log.e("DomainConfig", "Mirror test failed: ${e.message}")
            }
            attempts++
        }
        onAllMirrorsFailed()
    }
    
    private suspend fun testUrlAvailability(url: String): Response {
        return withContext(Dispatchers.IO) {
            OkHttpClient().newCall(
                Request.Builder()
                    .url(url)
                    .head() // 仅发送 HEAD 请求测试
                    .build()
            ).execute()
        }
    }
}
```

**MainActivity.kt 中使用**:
```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
    webView.webViewClient = object : WebViewClient() {
        override fun onReceivedHttpError(
            view: WebView,
            request: WebResourceRequest,
            errorResponse: WebResourceResponse
        ) {
            if (errorResponse.statusCode == 403) {
                // Cloudflare 阻挡，尝试切换镜像
                lifecycleScope.launch {
                    DomainConfig.loadUrlWithMirrorFallback(
                        webView = view,
                        originalUrl = request.url.toString(),
                        onAllMirrorsFailed = {
                            runOnUiThread {
                                Toast.makeText(
                                    this@MainActivity,
                                    "所有镜像均被阻挡，请使用 VPN 或更换网络",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    )
                }
            }
        }
    }
}
```

---

### 2. M3U8 视频下载器 (基于 missAV_api + NASSAV)

#### 当前状态
- ✅ 视频检测已实现 (VideoExtractor)
- ✅ 外部播放器调用已实现 (VideoProxyServer)
- ❌ **缺失**: 下载到本地功能

#### 实现方案

**新增 VideoDownloader.kt**:
```kotlin
class VideoDownloader(private val context: Context) {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    
    /**
     * 下载 M3U8 视频
     * @param m3u8Url HLS 播放列表 URL
     * @param outputFile 输出文件路径
     * @param onProgress 下载进度回调 (0-100)
     */
    suspend fun downloadM3U8(
        m3u8Url: String,
        outputFile: File,
        onProgress: (Int) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            // 1. 下载 M3U8 播放列表
            val playlist = downloadPlaylist(m3u8Url)
            val tsUrls = parseM3U8(playlist, m3u8Url)
            
            // 2. 下载所有 TS 分片
            val tsFiles = mutableListOf<File>()
            tsUrls.forEachIndexed { index, tsUrl ->
                val tsFile = File(context.cacheDir, "temp_$index.ts")
                downloadFile(tsUrl, tsFile)
                tsFiles.add(tsFile)
                
                val progress = ((index + 1) * 100) / tsUrls.size
                onProgress(progress)
            }
            
            // 3. 合并 TS 文件为 MP4（使用 FFmpeg）
            val mergedFile = mergeToMP4(tsFiles, outputFile)
            
            // 4. 清理临时文件
            tsFiles.forEach { it.delete() }
            
            Result.success(mergedFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private fun downloadPlaylist(url: String): String {
        val request = Request.Builder()
            .url(url)
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            .build()
        
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Failed to download playlist: ${response.code}")
            return response.body?.string() ?: throw IOException("Empty response")
        }
    }
    
    private fun parseM3U8(content: String, baseUrl: String): List<String> {
        val tsUrls = mutableListOf<String>()
        val baseUri = URI(baseUrl)
        
        content.lines().forEach { line ->
            if (line.isNotBlank() && !line.startsWith("#")) {
                val absoluteUrl = if (line.startsWith("http")) {
                    line
                } else {
                    baseUri.resolve(line).toString()
                }
                tsUrls.add(absoluteUrl)
            }
        }
        return tsUrls
    }
    
    private fun downloadFile(url: String, outputFile: File) {
        val request = Request.Builder()
            .url(url)
            .addHeader("Referer", url.substringBefore("/", ""))
            .build()
        
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Download failed: ${response.code}")
            
            response.body?.byteStream()?.use { input ->
                outputFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
    }
    
    /**
     * 使用 FFmpeg 合并 TS 文件为 MP4
     * 需要集成 FFmpeg 库 (如 mobile-ffmpeg)
     */
    private fun mergeToMP4(tsFiles: List<File>, outputFile: File): File {
        // 创建文件列表
        val listFile = File(context.cacheDir, "concat_list.txt")
        listFile.writeText(tsFiles.joinToString("\n") { "file '${it.absolutePath}'" })
        
        // FFmpeg 命令: 无损合并 + 重新封装
        val command = "-f concat -safe 0 -i ${listFile.absolutePath} " +
                     "-c copy -movflags +faststart ${outputFile.absolutePath}"
        
        // 使用 mobile-ffmpeg 执行
        val result = FFmpeg.execute(command)
        
        if (result != 0) {
            throw IOException("FFmpeg merge failed with code $result")
        }
        
        listFile.delete()
        return outputFile
    }
}
```

**依赖添加 (app/build.gradle.kts)**:
```kotlin
dependencies {
    // FFmpeg for Android
    implementation("com.arthenica:mobile-ffmpeg-full:4.4.LTS")
    
    // 或使用更轻量的 ffmpeg-kit
    implementation("com.arthenica:ffmpeg-kit-full:5.1")
}
```

**在 MainActivity 中集成**:
```kotlin
private fun showDownloadDialog(videoUrl: String, videoTitle: String) {
    AlertDialog.Builder(this)
        .setTitle("下载视频")
        .setMessage("是否下载: $videoTitle")
        .setPositiveButton("下载") { _, _ ->
            startVideoDownload(videoUrl, videoTitle)
        }
        .setNegativeButton("取消", null)
        .show()
}

private fun startVideoDownload(m3u8Url: String, title: String) {
    val outputFile = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
        "$title.mp4"
    )
    
    lifecycleScope.launch {
        val progressDialog = ProgressDialog(this@MainActivity).apply {
            setMessage("下载中...")
            setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
            setCancelable(false)
            show()
        }
        
        val downloader = VideoDownloader(this@MainActivity)
        val result = downloader.downloadM3U8(
            m3u8Url = m3u8Url,
            outputFile = outputFile,
            onProgress = { progress ->
                runOnUiThread {
                    progressDialog.progress = progress
                }
            }
        )
        
        progressDialog.dismiss()
        
        result.onSuccess {
            Toast.makeText(this@MainActivity, "下载完成: ${it.absolutePath}", Toast.LENGTH_LONG).show()
        }.onFailure {
            Toast.makeText(this@MainActivity, "下载失败: ${it.message}", Toast.LENGTH_LONG).show()
        }
    }
}
```

---

### 3. JavBus 元数据集成 (基于 NASSAV)

#### 功能说明
自动从 JavBus 抓取视频元数据（演员、标签、发布时间、封面）并关联到收藏夹。

#### 实现方案

**新增 JavBusMetadataService.kt**:
```kotlin
data class VideoMetadata(
    val videoId: String,
    val title: String,
    val releaseDate: String?,
    val studio: String?,
    val actresses: List<String>,
    val genres: List<String>,
    val coverUrl: String?,
    val rating: Float?
)

class JavBusMetadataService {
    
    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .build()
    
    private val baseUrl = "https://www.javbus.com"
    
    /**
     * 根据视频 ID 抓取元数据
     */
    suspend fun fetchMetadata(videoId: String): Result<VideoMetadata> = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/$videoId"
            val html = downloadHtml(url)
            val metadata = parseHtml(html, videoId)
            Result.success(metadata)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private fun downloadHtml(url: String): String {
        val request = Request.Builder()
            .url(url)
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            .build()
        
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            return response.body?.string() ?: throw IOException("Empty body")
        }
    }
    
    private fun parseHtml(html: String, videoId: String): VideoMetadata {
        val doc = Jsoup.parse(html)
        
        // 解析标题
        val title = doc.select("h3").text()
        
        // 解析发布日期
        val releaseDate = doc.select("p:contains(發行日期) + p").text()
        
        // 解析制作商
        val studio = doc.select("p:contains(製作商) a").text()
        
        // 解析演员
        val actresses = doc.select("div.star-name a").map { it.text() }
        
        // 解析类别
        val genres = doc.select("span.genre a").map { it.text() }
        
        // 解析封面
        val coverUrl = doc.select("a.bigImage img").attr("src")
        
        return VideoMetadata(
            videoId = videoId,
            title = title,
            releaseDate = releaseDate,
            studio = studio,
            actresses = actresses,
            genres = genres,
            coverUrl = coverUrl,
            rating = null
        )
    }
}
```

**扩展 FavoriteItem 数据结构**:
```kotlin
data class FavoriteItem(
    val id: String,
    val title: String,
    val url: String,
    val thumbnail: String,
    val addTime: Long,
    
    // 新增字段
    val metadata: VideoMetadata? = null
)
```

**在收藏时自动抓取元数据**:
```kotlin
// FavoritesManager.kt
suspend fun addFavoriteWithMetadata(
    title: String,
    url: String,
    thumbnail: String
): Result<FavoriteItem> = withContext(Dispatchers.IO) {
    try {
        // 从 URL 提取视频 ID
        val videoId = extractVideoId(url) // 如 "SSIS-123"
        
        // 抓取元数据
        val metadataService = JavBusMetadataService()
        val metadataResult = metadataService.fetchMetadata(videoId)
        
        val favorite = FavoriteItem(
            id = UUID.randomUUID().toString(),
            title = title,
            url = url,
            thumbnail = thumbnail,
            addTime = System.currentTimeMillis(),
            metadata = metadataResult.getOrNull()
        )
        
        saveFavorite(favorite)
        Result.success(favorite)
    } catch (e: Exception) {
        Result.failure(e)
    }
}

private fun extractVideoId(url: String): String {
    // 示例: https://missav.ai/cn/ssis-123 -> SSIS-123
    val regex = Regex("([a-zA-Z]+-\\d+)", RegexOption.IGNORE_CASE)
    return regex.find(url)?.value?.uppercase() ?: ""
}
```

---

### 4. 下载队列管理 (基于 NASSAV + JableTV-Modern)

#### 功能说明
- 支持批量添加下载任务
- 后台下载（使用 WorkManager）
- 断点续传
- 优先级队列

#### 实现方案

**新增 DownloadTask 数据模型**:
```kotlin
@Entity(tableName = "download_tasks")
data class DownloadTask(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val videoId: String,
    val title: String,
    val url: String,
    val m3u8Url: String,
    
    @ColumnInfo(name = "status")
    val status: DownloadStatus = DownloadStatus.PENDING,
    
    val progress: Int = 0, // 0-100
    val fileSize: Long = 0,
    val downloadedSize: Long = 0,
    
    val priority: Int = 0, // 数字越大优先级越高
    
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

enum class DownloadStatus {
    PENDING,    // 等待中
    DOWNLOADING, // 下载中
    PAUSED,     // 已暂停
    COMPLETED,  // 已完成
    FAILED      // 失败
}
```

**Room Database 配置**:
```kotlin
@Database(
    entities = [DownloadTask::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun downloadTaskDao(): DownloadTaskDao
    
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "javbrowser_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

@Dao
interface DownloadTaskDao {
    @Query("SELECT * FROM download_tasks ORDER BY priority DESC, createdAt ASC")
    fun getAllTasks(): Flow<List<DownloadTask>>
    
    @Query("SELECT * FROM download_tasks WHERE status = :status")
    suspend fun getTasksByStatus(status: DownloadStatus): List<DownloadTask>
    
    @Insert
    suspend fun insertTask(task: DownloadTask): Long
    
    @Update
    suspend fun updateTask(task: DownloadTask)
    
    @Delete
    suspend fun deleteTask(task: DownloadTask)
}
```

**WorkManager 后台下载**:
```kotlin
class VideoDownloadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    override suspend fun doWork(): Result {
        val taskId = inputData.getLong("task_id", -1)
        if (taskId == -1L) return Result.failure()
        
        val dao = AppDatabase.getDatabase(applicationContext).downloadTaskDao()
        val task = dao.getTasksByStatus(DownloadStatus.PENDING).find { it.id == taskId }
            ?: return Result.failure()
        
        // 更新状态为下载中
        dao.updateTask(task.copy(status = DownloadStatus.DOWNLOADING))
        
        val downloader = VideoDownloader(applicationContext)
        val outputFile = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
            "${task.title}.mp4"
        )
        
        val downloadResult = downloader.downloadM3U8(
            m3u8Url = task.m3u8Url,
            outputFile = outputFile,
            onProgress = { progress ->
                // 更新进度
                runBlocking {
                    dao.updateTask(task.copy(progress = progress))
                }
            }
        )
        
        return if (downloadResult.isSuccess) {
            dao.updateTask(task.copy(
                status = DownloadStatus.COMPLETED,
                progress = 100
            ))
            Result.success()
        } else {
            dao.updateTask(task.copy(status = DownloadStatus.FAILED))
            Result.failure()
        }
    }
}
```

**DownloadQueueManager 管理类**:
```kotlin
class DownloadQueueManager(private val context: Context) {
    
    private val workManager = WorkManager.getInstance(context)
    private val dao = AppDatabase.getDatabase(context).downloadTaskDao()
    
    /**
     * 添加下载任务到队列
     */
    suspend fun enqueueDownload(
        videoId: String,
        title: String,
        url: String,
        m3u8Url: String,
        priority: Int = 0
    ): Long {
        val task = DownloadTask(
            videoId = videoId,
            title = title,
            url = url,
            m3u8Url = m3u8Url,
            priority = priority
        )
        
        val taskId = dao.insertTask(task)
        
        // 创建 WorkManager 请求
        val workRequest = OneTimeWorkRequestBuilder<VideoDownloadWorker>()
            .setInputData(
                workDataOf("task_id" to taskId)
            )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        
        workManager.enqueue(workRequest)
        
        return taskId
    }
    
    /**
     * 暂停下载
     */
    suspend fun pauseDownload(taskId: Long) {
        val task = dao.getTasksByStatus(DownloadStatus.DOWNLOADING)
            .find { it.id == taskId }
        
        task?.let {
            dao.updateTask(it.copy(status = DownloadStatus.PAUSED))
            // WorkManager 不支持暂停，需要取消后重新排队
            workManager.cancelAllWorkByTag("download_$taskId")
        }
    }
    
    /**
     * 恢复下载
     */
    suspend fun resumeDownload(taskId: Long) {
        val task = dao.getTasksByStatus(DownloadStatus.PAUSED)
            .find { it.id == taskId }
        
        task?.let {
            dao.updateTask(it.copy(status = DownloadStatus.PENDING))
            enqueueDownload(it.videoId, it.title, it.url, it.m3u8Url, it.priority)
        }
    }
    
    /**
     * 获取所有任务（LiveData）
     */
    fun getAllTasksFlow(): Flow<List<DownloadTask>> = dao.getAllTasks()
}
```

---

### 5. Cloudflare 绕过优化

#### 问题
- Python 项目使用 `curl_cffi` 模拟真实浏览器 TLS 指纹
- Android OkHttp 的默认 TLS 指纹容易被识别

#### 解决方案

**User-Agent 轮换 Interceptor**:
```kotlin
class UserAgentRotationInterceptor : Interceptor {
    
    private val userAgents = listOf(
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1"
    )
    
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .header("User-Agent", userAgents.random())
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.9,zh-CN;q=0.8,zh;q=0.7")
            .header("Accept-Encoding", "gzip, deflate, br")
            .header("Connection", "keep-alive")
            .header("Upgrade-Insecure-Requests", "1")
            .build()
        
        return chain.proceed(request)
    }
}
```

**集成到 OkHttpClient**:
```kotlin
val client = OkHttpClient.Builder()
    .addInterceptor(UserAgentRotationInterceptor())
    .addInterceptor { chain ->
        val request = chain.request()
        val response = chain.proceed(request)
        
        // 403 错误时自动重试
        if (response.code == 403) {
            response.close()
            Thread.sleep(2000) // 延迟 2 秒
            return@addInterceptor chain.proceed(request)
        }
        
        response
    }
    .build()
```

---

### 6. 关键依赖库汇总

**新增到 app/build.gradle.kts**:
```kotlin
dependencies {
    // 现有依赖...
    
    // M3U8 下载与 FFmpeg
    implementation("com.arthenica:ffmpeg-kit-full:5.1")
    
    // HTML 解析（JavBus 元数据）
    implementation("org.jsoup:jsoup:1.16.1")
    
    // Room 数据库
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")
    
    // WorkManager（后台下载）
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    
    // Retrofit（可选，用于 API 调用）
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
}
```

---

## 📊 实施优先级与时间估算

### Phase 1: 高优先级功能 (2-3 周)

| 功能 | 工作量 | 技术风险 | 业务价值 |
|------|--------|---------|---------|
| **多镜像域名切换** | 2 天 | 低 | 高 - 解决地区访问问题 |
| **M3U8 视频下载** | 5 天 | 中 - FFmpeg 集成 | 高 - 核心功能 |
| **下载队列管理** | 3 天 | 低 | 中 - 改善用户体验 |
| **Cloudflare 绕过** | 2 天 | 中 - 可能随时失效 | 高 - 稳定性保障 |

### Phase 2: 中优先级功能 (2-4 周)

| 功能 | 工作量 | 技术风险 | 业务价值 |
|------|--------|---------|---------|
| **JavBus 元数据集成** | 4 天 | 中 - 网站结构变更 | 中 - 增强收藏夹 |
| **断点续传** | 3 天 | 中 | 中 - 改善下载体验 |
| **下载通知** | 2 天 | 低 | 低 - 锦上添花 |

### Phase 3: 低优先级功能 (按需实现)

| 功能 | 工作量 | 技术风险 | 业务价值 |
|------|--------|---------|---------|
| **NAS 同步** | 5 天 | 高 - SMB 协议复杂 | 低 - 小众需求 |
| **云存储同步** | 4 天 | 中 | 低 - 依赖第三方 API |
| **视频质量选择** | 3 天 | 中 - M3U8 解析 | 中 - 用户友好 |

---

## 🎯 实施步骤详解

### Step 1: 多镜像域名切换

**前置条件**: 无  
**预计时间**: 2 天

#### 实施清单
- [ ] 修改 `DomainConfig.kt`，添加镜像列表
- [ ] 实现 `switchToNextMirror()` 逻辑
- [ ] 在 `MainActivity.WebViewClient` 中集成故障转移
- [ ] 添加 SharedPreferences 持久化当前镜像索引
- [ ] 测试所有镜像的可用性
- [ ] 添加用户提示（"所有镜像均被阻挡"）

#### 验收标准
- ✅ 第一个镜像失败时自动切换到第二个
- ✅ 所有镜像失败时显示明确提示
- ✅ 应用重启后记住上次成功的镜像

---

### Step 2: M3U8 视频下载

**前置条件**: 完成 Step 1  
**预计时间**: 5 天

#### 实施清单
- [ ] 添加 FFmpeg-kit 依赖
- [ ] 实现 `VideoDownloader.kt`
- [ ] 实现 M3U8 播放列表解析
- [ ] 实现 TS 分片下载
- [ ] 集成 FFmpeg 合并逻辑
- [ ] 添加下载进度回调
- [ ] 在 MainActivity 中添加下载按钮
- [ ] 添加存储权限请求（Android 10+）
- [ ] 测试不同站点的 M3U8 格式

#### 验收标准
- ✅ 成功下载 JABLE.TV 视频为 MP4
- ✅ 成功下载 MISSAV 视频为 MP4
- ✅ 下载进度实时更新
- ✅ 合并后的 MP4 可在播放器中拖拽进度条

---

### Step 3: 下载队列管理

**前置条件**: 完成 Step 2  
**预计时间**: 3 天

#### 实施清单
- [ ] 创建 Room 数据库和 DAO
- [ ] 实现 `DownloadTask` 数据模型
- [ ] 实现 `VideoDownloadWorker`
- [ ] 实现 `DownloadQueueManager`
- [ ] 创建下载管理界面（新 Activity）
- [ ] 添加任务列表 RecyclerView
- [ ] 实现暂停/恢复/取消功能
- [ ] 添加任务优先级调整

#### 验收标准
- ✅ 可同时添加多个下载任务
- ✅ 任务按优先级排序
- ✅ 可暂停和恢复下载
- ✅ 应用关闭后下载继续进行

---

### Step 4: JavBus 元数据集成

**前置条件**: 无（独立功能）  
**预计时间**: 4 天

#### 实施清单
- [ ] 添加 Jsoup 依赖
- [ ] 实现 `JavBusMetadataService.kt`
- [ ] 实现 HTML 解析逻辑
- [ ] 扩展 `FavoriteItem` 数据结构
- [ ] 修改收藏夹添加逻辑
- [ ] 在收藏夹详情页显示元数据
- [ ] 添加元数据刷新功能
- [ ] 处理网络失败情况

#### 验收标准
- ✅ 添加收藏时自动获取元数据
- ✅ 显示演员列表、标签、发布日期
- ✅ 元数据解析失败时仍可保存收藏
- ✅ 支持手动刷新元数据

---

## ⚠️ 技术风险与缓解方案

### 风险 1: FFmpeg 集成导致 APK 体积激增

**风险等级**: 🟡 中  
**影响**: APK 从 8.5MB 增至 40MB+

**缓解方案**:
1. 使用 `ffmpeg-kit-min` 替代 `ffmpeg-kit-full`（仅包含必需编解码器）
2. 使用 Android App Bundle (AAB) 格式发布，自动按架构拆分
3. 提供"无下载功能"的 Lite 版本

```kotlin
// 轻量版依赖
implementation("com.arthenica:ffmpeg-kit-min:5.1")
```

---

### 风险 2: Room 数据库迁移问题

**风险等级**: 🟡 中  
**影响**: 应用更新后用户数据丢失

**缓解方案**:
1. 从 v1.0 就启用 Room，避免后期迁移
2. 使用 `fallbackToDestructiveMigration()` 仅在开发阶段
3. 生产环境提供完整的 Migration 策略

```kotlin
@Database(entities = [DownloadTask::class], version = 2)
abstract class AppDatabase : RoomDatabase() {
    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE download_tasks ADD COLUMN priority INTEGER NOT NULL DEFAULT 0")
            }
        }
        
        fun getDatabase(context: Context): AppDatabase {
            return Room.databaseBuilder(context, AppDatabase::class.java, "javbrowser_database")
                .addMigrations(MIGRATION_1_2)
                .build()
        }
    }
}
```

---

### 风险 3: Cloudflare 封锁策略更新

**风险等级**: 🔴 高  
**影响**: 所有镜像均无法访问

**缓解方案**:
1. 定期监控 Cloudflare 验证机制变化
2. 准备 WebView 截屏验证码识别（OCR）
3. 提供代理设置选项（用户自建 VPN）
4. 集成 Tor 网络（高级功能）

```kotlin
// 代理设置
class ProxyConfig(private val context: Context) {
    
    fun setProxy(proxyHost: String, proxyPort: Int) {
        val sharedPrefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        sharedPrefs.edit()
            .putString("proxy_host", proxyHost)
            .putInt("proxy_port", proxyPort)
            .apply()
    }
    
    fun getOkHttpClientWithProxy(): OkHttpClient {
        val sharedPrefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val proxyHost = sharedPrefs.getString("proxy_host", null)
        val proxyPort = sharedPrefs.getInt("proxy_port", 0)
        
        return if (proxyHost != null && proxyPort > 0) {
            OkHttpClient.Builder()
                .proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(proxyHost, proxyPort)))
                .build()
        } else {
            OkHttpClient()
        }
    }
}
```

---

### 风险 4: JavBus HTML 结构变更

**风险等级**: 🟡 中  
**影响**: 元数据抓取失败

**缓解方案**:
1. 使用多个选择器（fallback 机制）
2. 定期检查官方网站更新
3. 支持多个元数据源（JavDB、JavLibrary）

```kotlin
private fun parseTitle(doc: Document): String {
    return doc.select("h3").firstOrNull()?.text()
        ?: doc.select("title").text().substringBefore("-") // fallback
        ?: "Unknown Title"
}
```

---

## 📚 参考资源与学习材料

### 官方文档
1. **FFmpeg-kit**: https://github.com/arthenica/ffmpeg-kit
2. **Room**: https://developer.android.com/training/data-storage/room
3. **WorkManager**: https://developer.android.com/topic/libraries/architecture/workmanager
4. **Jsoup**: https://jsoup.org/

### 开源项目参考
1. **JableTV-Modern**: https://github.com/Alos21750/JableTV-MissAV-Downloader-GUI-2026
   - 重点学习: M3U8 解析、多镜像切换、Cloudflare 绕过
2. **NASSAV**: https://github.com/Satoing/NASSAV
   - 重点学习: JavBus 元数据抓取、NFO 生成
3. **missAV_api**: https://github.com/EchterAlsFake/missAV_api
   - 重点学习: API 封装设计

### Android 开发最佳实践
1. **后台任务**: 使用 WorkManager 替代 Service（Android 8.0+ 限制）
2. **数据持久化**: SharedPreferences → Room → 云端同步（渐进式）
3. **权限请求**: Android 10+ 使用 Scoped Storage
4. **崩溃监控**: 集成 Firebase Crashlytics

---

## 🎉 总结

本实施指南将 Python 开源项目的核心功能完整转换为 Android/Kotlin 实现方案，优先级排序如下：

### 必须实现 (v1.1)
1. ✅ 多镜像域名切换
2. ✅ M3U8 视频下载
3. ✅ Cloudflare 绕过优化

### 强烈建议 (v1.2)
4. ✅ 下载队列管理
5. ✅ JavBus 元数据集成

### 可选增强 (v1.3+)
6. 断点续传
7. 视频质量选择
8. NAS/云存储同步

**下一步行动**: 从 Phase 1 的多镜像域名切换开始实施，预计 2 天内完成并提交 PR。

---

**文档维护**: 每完成一个功能模块后更新本文档  
**问题反馈**: GitHub Issues - [fekilooo/javbrowser](https://github.com/fekilooo/javbrowser)
