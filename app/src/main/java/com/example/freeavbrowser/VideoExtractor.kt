package com.example.freeavbrowser

import java.util.regex.Pattern

object VideoExtractor {

    fun extractJable(html: String): String? {
        // Pattern: var hlsUrl = 'URL'
        val pattern = Pattern.compile("var\\s+hlsUrl\\s*=\\s*'([^']+)'")
        val matcher = pattern.matcher(html)
        if (matcher.find()) {
            return matcher.group(1)
        }
        return null
    }

    fun extractMissAV(html: String): String? {
        // Pattern: eval(function(p,a,c,k,e,d)...)
        // We need to find the packed code and unpack it.
        // This is a simplified unpacker for the specific format described.
        
        // 1. Find the eval block
        val evalPattern = Pattern.compile("eval\\(function\\(p,a,c,k,e,d\\)\\{.*\\}\\('([^']+)',(\\d+),(\\d+),'([^']+)'\\.split\\('\\|'\\)")
        val matcher = evalPattern.matcher(html)
        
        if (matcher.find()) {
            val payload = matcher.group(1) ?: ""
            val radix = matcher.group(2)?.toInt() ?: 36
            @Suppress("UNUSED_VARIABLE")
            val count = matcher.group(3)?.toInt() ?: 0
            val dictString = matcher.group(4) ?: ""
            val dict = dictString.split("|")

            // 2. Unpack
            // The logic described: replace Base36 words in payload with dict words.
            // Since we don't have a full JS engine, we'll try a heuristic replacement.
            // The payload looks like: f='8://7.6/...'
            // We need to replace words like 'e', 'c', 'a' with dict[14], dict[12], dict[10] etc.
            
            // Regex to find all alphanumeric words in payload that match the radix encoding
            // But Dean Edwards packer matches \b\w+\b usually.
            
            // Let's implement a simple replacer.
            // Iterate through the dictionary. If dict[i] is empty, it means the token is the index itself (in base 36), 
            // but usually the packer fills all slots or uses empty for "no replacement".
            // Actually, the packer logic is: if (dict[i]) replace regex /\\b(base36(i))\\b/ with dict[i]
            
            var unpacked = payload
            
            // We need to handle the replacement order carefully or use a single pass.
            // But for this specific case, let's try replacing from largest index to smallest to avoid sub-match issues?
            // Or just use word boundaries.
            
            // The JS code usually iterates backwards: while(c--) ...
            // So we should also iterate from largest index to smallest to avoid replacing substrings of larger keys if they overlap (though \b helps).
            
            for (i in dict.indices.reversed()) {
                val word = dict[i]
                if (word.isNotEmpty()) {
                    val key = i.toString(radix)
                    // Replace \bkey\b with word
                    val regex = "\\b$key\\b"
                    unpacked = unpacked.replace(Regex(regex), word)
                }
            }
            
            // 3. Extract URL from unpacked code
            // Look for source='...' or similar
            // The user example: source='https://...'
            // Or f='...' as in the example.
            
            // Let's look for .m3u8
            val urlPattern = Pattern.compile("['\"](https?://[^'\"]+\\.m3u8)['\"]")
            val urlMatcher = urlPattern.matcher(unpacked)
            if (urlMatcher.find()) {
                return urlMatcher.group(1)
            }
        }
        
        // Fallback: Check for thumbnail UUID if the above fails (Heuristic)
        // urls: ["https:\/\/nineyu.com\/UUID\/seek\/_0.jpg"...]
        val uuidPattern = Pattern.compile("urls:\\s*\\[\"https:\\\\/\\\\/[^/]+\\\\/([a-f0-9\\-]+)\\\\/seek")
        val uuidMatcher = uuidPattern.matcher(html)
        if (uuidMatcher.find()) {
            val uuid = uuidMatcher.group(1)
            // Construct URL: https://surrit.com/{UUID}/playlist.m3u8
            // Note: Domain might change, this is risky.
            return "https://surrit.com/$uuid/playlist.m3u8"
        }

        return null
    }

    fun extractRouVideo(html: String): String? {
        try {
            // New logic: Check for "ev" object with "d" and "k"
            val evBlockPattern = Pattern.compile("\"ev\"\\s*:\\s*\\{([^}]+)\\}")
            val evBlockMatcher = evBlockPattern.matcher(html)
            
            if (evBlockMatcher.find()) {
                val block = evBlockMatcher.group(1) ?: ""
                val dMatcher = Pattern.compile("\"d\"\\s*:\\s*\"([^\"]+)\"").matcher(block)
                val kMatcher = Pattern.compile("\"k\"\\s*:\\s*(\\d+)").matcher(block)
                
                if (dMatcher.find() && kMatcher.find()) {
                    val dBase64 = dMatcher.group(1) ?: ""
                    val kStr = kMatcher.group(1) ?: "0"
                    val k = kStr.toInt()
                    
                    // Decode base64
                    val decodedBytes = android.util.Base64.decode(dBase64, android.util.Base64.DEFAULT)
                    // Shift characters
                    val builder = java.lang.StringBuilder()
                    for (byte in decodedBytes) {
                        val shifted = (byte.toInt() and 0xFF) - k
                        builder.append(shifted.toChar())
                    }
                    val decryptedJson = builder.toString()
                    
                    // Extract videoUrl
                    val urlPattern = Pattern.compile("\"videoUrl\"\\s*:\\s*\"([^\"]+)\"")
                    val urlMatcher = urlPattern.matcher(decryptedJson)
                    if (urlMatcher.find()) {
                        var videoUrl = urlMatcher.group(1)
                        if (videoUrl != null) {
                            // Replace index.jpg with index.m3u8 to get standard format
                            videoUrl = videoUrl.replace("index.jpg", "index.m3u8")
                            return videoUrl.replace("\\/", "/") // Unescape slashes if any
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("VideoExtractor", "Error extracting MissAV video: ${e.message}", e)
        }

        // Fallback to old method: Look for <video> tag with src attribute containing .m3u8
        val pattern = Pattern.compile("<video[^>]+src=[\"']([^\"']+\\.m3u8[^\"']*)[\"']", Pattern.CASE_INSENSITIVE)
        val matcher = pattern.matcher(html)
        if (matcher.find()) {
            var url = matcher.group(1)
            // Decode HTML entities like &amp; to &
            if (url != null) {
                url = url.replace("&amp;", "&")
                    .replace("&lt;", "<")
                    .replace("&gt;", ">")
                    .replace("&quot;", "\"")
                return url
            }
        }
        return null
    }
    fun extractAvJoy(html: String): String? {
        // Find all <source> tags and pick the one with the highest res value
        val sourceTagPattern = Pattern.compile("<source([^>]+)>", Pattern.CASE_INSENSITIVE)
        val srcPattern = Pattern.compile("src=[\"']([^\"']+\\.mp4[^\"']*)[\"']", Pattern.CASE_INSENSITIVE)
        val resPattern = Pattern.compile("res=[\"'](\\d+)[\"']", Pattern.CASE_INSENSITIVE)

        val tagMatcher = sourceTagPattern.matcher(html)
        var bestUrl: String? = null
        var bestRes = -1

        while (tagMatcher.find()) {
            val attrs = tagMatcher.group(1) ?: continue
            val srcMatcher = srcPattern.matcher(attrs)
            if (srcMatcher.find()) {
                val url = srcMatcher.group(1) ?: continue
                val resMatcher = resPattern.matcher(attrs)
                val res = if (resMatcher.find()) resMatcher.group(1)?.toIntOrNull() ?: 0 else 0
                if (res > bestRes) {
                    bestRes = res
                    bestUrl = url
                }
            }
        }

        if (bestUrl != null) return bestUrl

        // Fallback: <video src="...mp4">
        val videoPattern = Pattern.compile(
            "<video[^>]+src=[\"']([^\"']+\\.mp4[^\"']*)[\"']",
            Pattern.CASE_INSENSITIVE
        )
        val videoMatcher = videoPattern.matcher(html)
        if (videoMatcher.find()) {
            return videoMatcher.group(1)
        }

        return null
    }

    fun fetchBestQualityUrl(masterUrl: String, callback: (String) -> Unit) {
        kotlin.concurrent.thread {
            try {
                val connection = java.net.URL(masterUrl).openConnection() as java.net.HttpURLConnection
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                connection.connectTimeout = 10000
                connection.readTimeout = 10000

                if (connection.responseCode == 200) {
                    val content = connection.inputStream.bufferedReader().use { it.readText() }
                    val lines = content.lines()

                    var bestUrl: String? = null
                    var maxResolution = 0

                    for (i in lines.indices) {
                        val line = lines[i]
                        if (line.startsWith("#EXT-X-STREAM-INF")) {
                            val resMatcher = Pattern.compile("RESOLUTION=(\\d+)x(\\d+)").matcher(line)
                            if (resMatcher.find()) {
                                val width = resMatcher.group(1)?.toInt() ?: 0
                                val height = resMatcher.group(2)?.toInt() ?: 0
                                val pixelCount = width * height

                                if (pixelCount > maxResolution) {
                                    maxResolution = pixelCount
                                    if (i + 1 < lines.size) {
                                        var nextLine = lines[i + 1].trim()
                                        if (nextLine.isNotEmpty() && !nextLine.startsWith("#")) {
                                            // Handle URL construction with URI to support relative paths and query params
                                            try {
                                                val masterUri = java.net.URI(masterUrl)
                                                // Resolve relative path correctly
                                                val nextUri = masterUri.resolve(nextLine)

                                                // If the new URL doesn't have query params, but the master did, inherit them
                                                // This is a heuristic for sites that use token authentication in the URL
                                                if (masterUri.rawQuery != null && nextUri.rawQuery == null) {
                                                    bestUrl = nextUri.toString() + "?" + masterUri.rawQuery
                                                } else {
                                                    bestUrl = nextUri.toString()
                                                }
                                            } catch (e: Exception) {
                                                // Fallback to simple string concatenation if URI parsing fails
                                                if (!nextLine.startsWith("http")) {
                                                    val baseUrl = masterUrl.substringBeforeLast("/") + "/"
                                                    bestUrl = baseUrl + nextLine
                                                } else {
                                                    bestUrl = nextLine
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    val finalUrl = bestUrl ?: masterUrl
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        callback(finalUrl)
                    }
                } else {
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        callback(masterUrl)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    callback(masterUrl)
                }
            }
        }
    }

    fun extractHentaiHaven(html: String): String? {
        // Pattern 1: Look for video.js player source
        val videoJsPattern = Pattern.compile("sources?\\s*:\\s*\\[?\\s*\\{[^}]*src\\s*:\\s*[\"']([^\"']+\\.m3u8[^\"']*)[\"']", Pattern.CASE_INSENSITIVE)
        val matcher1 = videoJsPattern.matcher(html)
        if (matcher1.find()) {
            return matcher1.group(1)
        }

        // Pattern 2: Direct m3u8 URL in script
        val m3u8Pattern = Pattern.compile("[\"'](https?://[^\"']+\\.m3u8[^\"']*)[\"']")
        val matcher2 = m3u8Pattern.matcher(html)
        if (matcher2.find()) {
            return matcher2.group(1)
        }

        // Pattern 3: MP4 video source
        val mp4Pattern = Pattern.compile("<video[^>]+src\\s*=\\s*[\"']([^\"']+\\.mp4[^\"']*)[\"']", Pattern.CASE_INSENSITIVE)
        val matcher3 = mp4Pattern.matcher(html)
        if (matcher3.find()) {
            return matcher3.group(1)
        }

        return null
    }

    fun extractHanime(html: String): String? {
        // Hanime.tv typically uses HLS streaming
        // Pattern 1: Look for player config with m3u8
        val configPattern = Pattern.compile("videos_manifest\\s*[:\\{]\\s*[\"']?servers?[\"']?\\s*:\\s*\\[?[^\\]]*[\"']([^\"']+\\.m3u8[^\"']*)[\"']", Pattern.CASE_INSENSITIVE)
        val matcher1 = configPattern.matcher(html)
        if (matcher1.find()) {
            return matcher1.group(1)
        }

        // Pattern 2: Direct server URL
        val serverPattern = Pattern.compile("[\"'](https?://[^\"']*stream[^\"']*\\.m3u8[^\"']*)[\"']", Pattern.CASE_INSENSITIVE)
        val matcher2 = serverPattern.matcher(html)
        if (matcher2.find()) {
            return matcher2.group(1)
        }

        // Pattern 3: Generic m3u8 URL
        val m3u8Pattern = Pattern.compile("[\"'](https?://[^\"']+\\.m3u8[^\"']*)[\"']")
        val matcher3 = m3u8Pattern.matcher(html)
        if (matcher3.find()) {
            return matcher3.group(1)
        }

        return null
    }

    fun extractWatchHentai(html: String): String? {
        // Similar to generic video.js pattern
        // Pattern 1: video.js source
        val sourcePattern = Pattern.compile("source\\s*:\\s*[\"']([^\"']+\\.(m3u8|mp4)[^\"']*)[\"']", Pattern.CASE_INSENSITIVE)
        val matcher1 = sourcePattern.matcher(html)
        if (matcher1.find()) {
            return matcher1.group(1)
        }

        // Pattern 2: <video> or <source> tag
        val videoTagPattern = Pattern.compile("<(?:video|source)[^>]+src\\s*=\\s*[\"']([^\"']+\\.(m3u8|mp4)[^\"']*)[\"']", Pattern.CASE_INSENSITIVE)
        val matcher2 = videoTagPattern.matcher(html)
        if (matcher2.find()) {
            return matcher2.group(1)
        }

        return null
    }

    fun extractOppai(html: String): String? {
        // Pattern 1: Look for stream URLs
        val streamPattern = Pattern.compile("[\"'](https?://[^\"']*(?:stream|video)[^\"']*\\.(?:m3u8|mp4)[^\"']*)[\"']", Pattern.CASE_INSENSITIVE)
        val matcher = streamPattern.matcher(html)
        if (matcher.find()) {
            return matcher.group(1)
        }

        // Pattern 2: Generic m3u8/mp4
        val genericPattern = Pattern.compile("[\"'](https?://[^\"']+\\.(?:m3u8|mp4)[^\"']*)[\"']")
        val matcher2 = genericPattern.matcher(html)
        if (matcher2.find()) {
            return matcher2.group(1)
        }

        return null
    }

    fun extractMuchoHentai(html: String): String? {
        // Pattern 1: Player source
        val playerPattern = Pattern.compile("file\\s*:\\s*[\"']([^\"']+\\.(?:m3u8|mp4)[^\"']*)[\"']", Pattern.CASE_INSENSITIVE)
        val matcher = playerPattern.matcher(html)
        if (matcher.find()) {
            return matcher.group(1)
        }

        // Pattern 2: Video tag
        val videoPattern = Pattern.compile("<video[^>]+src\\s*=\\s*[\"']([^\"']+\\.(?:m3u8|mp4)[^\"']*)[\"']", Pattern.CASE_INSENSITIVE)
        val matcher2 = videoPattern.matcher(html)
        if (matcher2.find()) {
            return matcher2.group(1)
        }

        return null
    }

    fun extractHentaiMama(html: String): String? {
        // Similar pattern to others
        val pattern = Pattern.compile("[\"'](https?://[^\"']+\\.(?:m3u8|mp4)[^\"']*)[\"']")
        val matcher = pattern.matcher(html)
        if (matcher.find()) {
            return matcher.group(1)
        }
        return null
    }

    fun extractHentaiFreak(html: String): String? {
        // Pattern for HD streams
        val hdPattern = Pattern.compile("[\"'](https?://[^\"']*(?:hd|720|1080)[^\"']*\\.(?:m3u8|mp4)[^\"']*)[\"']", Pattern.CASE_INSENSITIVE)
        val matcher = hdPattern.matcher(html)
        if (matcher.find()) {
            return matcher.group(1)
        }

        // Fallback to generic
        val genericPattern = Pattern.compile("[\"'](https?://[^\"']+\\.(?:m3u8|mp4)[^\"']*)[\"']")
        val matcher2 = genericPattern.matcher(html)
        if (matcher2.find()) {
            return matcher2.group(1)
        }

        return null
    }

    fun extractXanimeporn(html: String): String? {
        // Generic extractor
        val pattern = Pattern.compile("[\"'](https?://[^\"']+\\.(?:m3u8|mp4)[^\"']*)[\"']")
        val matcher = pattern.matcher(html)
        if (matcher.find()) {
            return matcher.group(1)
        }
        return null
    }

    fun extractKissHentai(html: String): String? {
        // Pattern 1: Player config
        val configPattern = Pattern.compile("(?:source|file)\\s*:\\s*[\"']([^\"']+\\.(?:m3u8|mp4)[^\"']*)[\"']", Pattern.CASE_INSENSITIVE)
        val matcher = configPattern.matcher(html)
        if (matcher.find()) {
            return matcher.group(1)
        }

        // Pattern 2: Generic
        val genericPattern = Pattern.compile("[\"'](https?://[^\"']+\\.(?:m3u8|mp4)[^\"']*)[\"']")
        val matcher2 = genericPattern.matcher(html)
        if (matcher2.find()) {
            return matcher2.group(1)
        }

        return null
    }

    fun extractHentaiCity(html: String): String? {
        // Generic MP4/M3U8 extractor
        val pattern = Pattern.compile("[\"'](https?://[^\"']+\\.(?:m3u8|mp4)[^\"']*)[\"']")
        val matcher = pattern.matcher(html)
        if (matcher.find()) {
            return matcher.group(1)
        }
        return null
    }

    fun extractHentaiUniverse(html: String): String? {
        // HD stream pattern
        val pattern = Pattern.compile("[\"'](https?://[^\"']+\\.(?:m3u8|mp4)[^\"']*)[\"']")
        val matcher = pattern.matcher(html)
        if (matcher.find()) {
            return matcher.group(1)
        }
        return null
    }

    fun extractAnimeIDHentai(html: String): String? {
        // Anime-style naming pattern
        val pattern = Pattern.compile("[\"'](https?://[^\"']+\\.(?:m3u8|mp4)[^\"']*)[\"']")
        val matcher = pattern.matcher(html)
        if (matcher.find()) {
            return matcher.group(1)
        }
        return null
    }

    fun extractOhentai(html: String): String? {
        // Multi-format support
        val pattern = Pattern.compile("[\"'](https?://[^\"']+\\.(?:m3u8|mp4)[^\"']*)[\"']")
        val matcher = pattern.matcher(html)
        if (matcher.find()) {
            return matcher.group(1)
        }
        return null
    }
}
