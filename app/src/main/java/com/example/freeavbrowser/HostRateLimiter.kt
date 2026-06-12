package com.example.freeavbrowser

/**
 * 按域名限制请求速率，避免触发 CDN 速率保护。
 * 每个 host 强制最小请求间隔。
 */
class HostRateLimiter(private val minIntervalMs: Long = 200L) {
    private val lastRequestTime = mutableMapOf<String, Long>()

    @Synchronized
    fun acquire(host: String) {
        val now = System.currentTimeMillis()
        val last = lastRequestTime[host] ?: 0L
        val elapsed = now - last

        if (elapsed < minIntervalMs) {
            Thread.sleep(minIntervalMs - elapsed)
        }

        lastRequestTime[host] = System.currentTimeMillis()
    }
}
