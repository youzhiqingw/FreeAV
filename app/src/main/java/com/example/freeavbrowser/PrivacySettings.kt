package com.example.freeavbrowser

import android.content.Context
import android.content.SharedPreferences

class PrivacySettings(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("privacy_prefs", Context.MODE_PRIVATE)
    
    companion object {
        private const val KEY_LOCK_ENABLED = "lock_enabled"
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        private const val KEY_SELECTED_ICON = "selected_icon"
        private const val KEY_LAST_UNLOCK_TIME = "last_unlock_time"
        private const val KEY_DARK_MODE = "dark_mode"
        private const val KEY_AD_BLOCKING_ENABLED = "ad_blocking_enabled"
        private const val KEY_UPDATE_INTERVAL = "update_interval"
        private const val KEY_PROXY_MODE = "proxy_mode"
        private const val KEY_EASYLIST_ENABLED = "easylist_enabled"
        private const val KEY_CLOUDFLARE_BYPASS = "cloudflare_bypass_enabled"
        private const val KEY_CF_COOKIES = "cloudflare_cookies_json"
        private const val KEY_SCREENSHOT_BLOCK = "screenshot_block_enabled"
        private const val KEY_PRIVACY_MODE = "privacy_mode_enabled"

        const val ICON_DEFAULT = "default"
        const val ICON_CALCULATOR = "calculator"
        const val ICON_NOTES = "notes"
        const val ICON_FILE = "file"
        const val UPDATE_NEVER = 0
        const val UPDATE_48H = 48
    }
    
    var isLockEnabled: Boolean
        get() = prefs.getBoolean(KEY_LOCK_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_LOCK_ENABLED, value).apply()
    
    var isBiometricEnabled: Boolean
        get() = prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, value).apply()
    
    var selectedIcon: String
        get() = prefs.getString(KEY_SELECTED_ICON, ICON_DEFAULT) ?: ICON_DEFAULT
        set(value) = prefs.edit().putString(KEY_SELECTED_ICON, value).apply()
    
    var lastUnlockTime: Long
        get() = prefs.getLong(KEY_LAST_UNLOCK_TIME, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_UNLOCK_TIME, value).apply()
    
    fun shouldLock(): Boolean {
        if (!isLockEnabled) return false
        
        val currentTime = System.currentTimeMillis()
        val twoHoursMillis = 2 * 60 * 60 * 1000L // 2 hours in milliseconds
        
        return (currentTime - lastUnlockTime) > twoHoursMillis
    }
    
    fun updateUnlockTime() {
        lastUnlockTime = System.currentTimeMillis()
    }

    var darkMode: Int
        get() = prefs.getInt(KEY_DARK_MODE, androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        set(value) = prefs.edit().putInt(KEY_DARK_MODE, value).apply()

    var isAdBlockingEnabled: Boolean
        get() = prefs.getBoolean(KEY_AD_BLOCKING_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_AD_BLOCKING_ENABLED, value).apply()

    var updateInterval: Int
        get() = prefs.getInt(KEY_UPDATE_INTERVAL, UPDATE_NEVER)
        set(value) = prefs.edit().putInt(KEY_UPDATE_INTERVAL, value).apply()

    fun shouldUpdateRules(lastUpdateTime: Long): Boolean {
        if (updateInterval == UPDATE_NEVER) return false
        val intervalMs = updateInterval * 3600 * 1000L
        return System.currentTimeMillis() - lastUpdateTime > intervalMs
    }

    var proxyMode: String
        get() = prefs.getString(KEY_PROXY_MODE, "none") ?: "none"
        set(value) = prefs.edit().putString(KEY_PROXY_MODE, value).apply()

    var isEasyListEnabled: Boolean
        get() = prefs.getBoolean(KEY_EASYLIST_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_EASYLIST_ENABLED, value).apply()

    // PIN Code Support
    var pinCode: String?
        get() = prefs.getString("app_pin_code", null)
        set(value) = prefs.edit().putString("app_pin_code", value).apply()

    fun isPinSet(): Boolean {
        return !pinCode.isNullOrEmpty()
    }

    fun validatePin(inputPin: String): Boolean {
        return inputPin == pinCode
    }
    
    // Get current icon resource ID based on selected icon
    val currentIconResourceId: Int
        get() = when (selectedIcon) {
            ICON_CALCULATOR -> R.drawable.ic_launcher_calculator
            ICON_NOTES -> R.drawable.ic_launcher_notes
            ICON_FILE -> R.drawable.ic_launcher_file
            else -> R.drawable.ic_launcher  // Default
        }
    
    // Get current app label based on selected icon
    val currentAppLabel: String
        get() = when (selectedIcon) {
            ICON_CALCULATOR -> "Calculator"
            ICON_NOTES -> "Notes"
            ICON_FILE -> "File Manager"
            else -> "JAV Browser"  // Default
        }

    var isCloudflareBypassEnabled: Boolean
        get() = prefs.getBoolean(KEY_CLOUDFLARE_BYPASS, false)
        set(value) = prefs.edit().putBoolean(KEY_CLOUDFLARE_BYPASS, value).apply()

    fun saveCloudflareCookie(host: String, value: String) {
        val map = getCloudflareCookiesMap().toMutableMap()
        map[host] = value
        prefs.edit().putString(KEY_CF_COOKIES, org.json.JSONObject(map as Map<*, *>).toString()).apply()
    }

    fun getCloudflareCookie(host: String): String? {
        return getCloudflareCookiesMap()[host]
    }

    private fun getCloudflareCookiesMap(): Map<String, String> {
        val json = prefs.getString(KEY_CF_COOKIES, "{}") ?: "{}"
        return try {
            org.json.JSONObject(json).let { obj ->
                obj.keys().asSequence().associateWith { obj.getString(it) }
            }
        } catch (e: Exception) { emptyMap() }
    }

    var isScreenshotBlockEnabled: Boolean
        get() = prefs.getBoolean(KEY_SCREENSHOT_BLOCK, true)
        set(value) = prefs.edit().putBoolean(KEY_SCREENSHOT_BLOCK, value).apply()

    var isPrivacyModeEnabled: Boolean
        get() = prefs.getBoolean(KEY_PRIVACY_MODE, false)
        set(value) = prefs.edit().putBoolean(KEY_PRIVACY_MODE, value).apply()
}
