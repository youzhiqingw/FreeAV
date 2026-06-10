package com.example.freeavbrowser

import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText

class SettingsActivity : AppCompatActivity() {

    private lateinit var toolbar: MaterialToolbar
    private lateinit var switchAdBlocking: SwitchMaterial
    private lateinit var switchLock: SwitchMaterial
    private lateinit var switchScreenshotBlock: SwitchMaterial
    private lateinit var switchBiometric: SwitchMaterial
    private lateinit var switchPrivacyMode: SwitchMaterial
    private lateinit var switchDomainMapping: SwitchMaterial

    private lateinit var itemAdRules: LinearLayout
    private lateinit var itemUpdateRules: LinearLayout
    private lateinit var itemProxyMode: LinearLayout
    private lateinit var itemUpdateMapping: LinearLayout

    private lateinit var panelAdRules: LinearLayout
    private lateinit var iconArrowRules: ImageView

    private lateinit var iconDefault: FrameLayout
    private lateinit var iconCalculator: FrameLayout
    private lateinit var iconNotes: FrameLayout
    private lateinit var iconFile: FrameLayout

    private lateinit var toggleDarkMode: MaterialButtonToggleGroup
    private lateinit var tvDarkModeLabel: TextView
    private lateinit var tvBlockedCount: TextView
    private lateinit var tvRulesStatus: TextView
    private lateinit var tvUpdateRulesSubtitle: TextView
    private lateinit var tvProxyModeLabel: TextView

    private lateinit var etRuleUrl: TextInputEditText
    private lateinit var btnAddRule: MaterialButton

    private lateinit var privacySettings: PrivacySettings
    private lateinit var appIconManager: AppIconManager
    private lateinit var biometricHelper: BiometricHelper
    private lateinit var adFilterRules: AdFilterRules

    private var selectedIconId: String = PrivacySettings.ICON_DEFAULT
    private var isAdRulesPanelExpanded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Prevent screenshots and hide content in recent apps
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_SECURE,
            android.view.WindowManager.LayoutParams.FLAG_SECURE
        )
        setContentView(R.layout.activity_settings)

        privacySettings = PrivacySettings(this)
        appIconManager = AppIconManager(this)
        adFilterRules = AdFilterRules(this)
        biometricHelper = BiometricHelper(this, privacySettings)

        initViews()
        loadSettings()
        setupListeners()
        updateRulesStatus()

        // Enter transition animation
        @Suppress("DEPRECATION")
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    @Suppress("DEPRECATION")
    override fun finish() {
        super.finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    private fun initViews() {
        // Toolbar
        toolbar = findViewById(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        // Switches
        switchAdBlocking = findViewById(R.id.switch_ad_blocking)
        switchLock = findViewById(R.id.switch_lock)
        switchScreenshotBlock = findViewById(R.id.switch_screenshot_block)
        switchBiometric = findViewById(R.id.switch_biometric)
        switchPrivacyMode = findViewById(R.id.switch_privacy_mode)
        switchDomainMapping = findViewById(R.id.switch_domain_mapping)

        // Expandable items
        itemAdRules = findViewById(R.id.item_ad_rules)
        itemUpdateRules = findViewById(R.id.item_update_rules)
        itemProxyMode = findViewById(R.id.item_proxy_mode)
        itemUpdateMapping = findViewById(R.id.item_update_mapping)

        // Panels
        panelAdRules = findViewById(R.id.panel_ad_rules)
        iconArrowRules = findViewById(R.id.icon_arrow_rules)

        // Icon options
        iconDefault = findViewById(R.id.icon_default)
        iconCalculator = findViewById(R.id.icon_calculator)
        iconNotes = findViewById(R.id.icon_notes)
        iconFile = findViewById(R.id.icon_file)

        // Dark mode toggle
        toggleDarkMode = findViewById(R.id.toggle_dark_mode)
        tvDarkModeLabel = findViewById(R.id.tv_dark_mode_label)

        // TextViews
        tvBlockedCount = findViewById(R.id.tv_blocked_count)
        tvRulesStatus = findViewById(R.id.tv_rules_status)
        tvUpdateRulesSubtitle = findViewById(R.id.tv_update_rules_subtitle)
        tvProxyModeLabel = findViewById(R.id.tv_proxy_mode_label)

        // Rule management
        etRuleUrl = findViewById(R.id.et_rule_url)
        btnAddRule = findViewById(R.id.btn_add_rule)
    }

    private fun loadSettings() {
        // Load switches state
        switchAdBlocking.isChecked = privacySettings.isAdBlockingEnabled
        switchLock.isChecked = privacySettings.isLockEnabled
        switchBiometric.isChecked = privacySettings.isLockEnabled

        // Load selected icon
        selectedIconId = privacySettings.selectedIcon
        updateIconSelection()

        // Load dark mode setting
        val darkMode = privacySettings.darkMode
        val (checkedId, label) = when (darkMode) {
            androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO -> R.id.btn_light to "关闭"
            androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES -> R.id.btn_dark to "开启"
            else -> R.id.btn_system to "跟随系统"
        }
        toggleDarkMode.check(checkedId)
        tvDarkModeLabel.text = label

        // Load domain mapping
        switchDomainMapping.isChecked = privacySettings.isDomainMappingEnabled

        // Load proxy mode
        tvProxyModeLabel.text = when (privacySettings.proxyMode) {
            "system" -> "系统代理"
            "http" -> "HTTP"
            "https" -> "HTTPS"
            "socks" -> "SOCKS"
            else -> "无"
        }
    }

    private fun setupListeners() {
        // Ad blocking switch
        switchAdBlocking.setOnCheckedChangeListener { _, isChecked ->
            privacySettings.isAdBlockingEnabled = isChecked
            Toast.makeText(this, if (isChecked) "广告拦截已启用" else "广告拦截已停用", Toast.LENGTH_SHORT).show()
        }

        // Lock switch
        switchLock.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (biometricHelper.canAuthenticate()) {
                    biometricHelper.authenticate(
                        onSuccess = {
                            privacySettings.isLockEnabled = true
                            privacySettings.updateUnlockTime()
                            switchBiometric.isChecked = true
                            Toast.makeText(this, "应用锁已启用", Toast.LENGTH_SHORT).show()
                        },
                        onError = { error ->
                            switchLock.isChecked = false
                            Toast.makeText(this, "验证失败：$error", Toast.LENGTH_SHORT).show()
                        }
                    )
                } else {
                    switchLock.isChecked = false
                    Toast.makeText(this, "此设备不支持生物识别", Toast.LENGTH_LONG).show()
                }
            } else {
                privacySettings.isLockEnabled = false
                switchBiometric.isChecked = false
                Toast.makeText(this, "应用锁已停用", Toast.LENGTH_SHORT).show()
            }
        }

        // Screenshot block switch
        switchScreenshotBlock.setOnCheckedChangeListener { _, isChecked ->
            Toast.makeText(this, if (isChecked) "防截屏已启用" else "防截屏已停用", Toast.LENGTH_SHORT).show()
        }

        // Biometric switch
        switchBiometric.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked != privacySettings.isLockEnabled) {
                switchLock.isChecked = isChecked
            }
        }

        // Privacy mode switch
        switchPrivacyMode.setOnCheckedChangeListener { _, isChecked ->
            Toast.makeText(this, if (isChecked) "隐私模式已启用" else "隐私模式已停用", Toast.LENGTH_SHORT).show()
        }

        // Domain mapping switch
        switchDomainMapping.setOnCheckedChangeListener { _, isChecked ->
            privacySettings.isDomainMappingEnabled = isChecked
            Toast.makeText(this, if (isChecked) "自定义域名已启用" else "已恢复默认域名", Toast.LENGTH_SHORT).show()
        }

        // Expandable items
        itemAdRules.setOnClickListener {
            toggleAdRulesPanel()
        }

        itemUpdateRules.setOnClickListener {
            val currentInterval = privacySettings.updateInterval
            val options = arrayOf("从不更新", "每48小时")
            val checkedItem = if (currentInterval == PrivacySettings.UPDATE_48H) 1 else 0

            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("云端规则更新")
                .setSingleChoiceItems(options, checkedItem) { dialog, which ->
                    val interval = if (which == 1) PrivacySettings.UPDATE_48H else PrivacySettings.UPDATE_NEVER
                    privacySettings.updateInterval = interval
                    updateRulesStatus()
                    dialog.dismiss()
                }
                .setNegativeButton("取消", null)
                .show()
        }

        itemProxyMode.setOnClickListener {
            val options = arrayOf("无", "系统代理", "HTTP", "HTTPS", "SOCKS")
            val values = arrayOf("none", "system", "http", "https", "socks")
            val current = privacySettings.proxyMode
            val checkedItem = values.indexOf(current).coerceAtLeast(0)

            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("代理模式")
                .setSingleChoiceItems(options, checkedItem) { dialog, which ->
                    privacySettings.proxyMode = values[which]
                    tvProxyModeLabel.text = options[which]
                    dialog.dismiss()
                }
                .setNegativeButton("取消", null)
                .show()
        }


        itemUpdateMapping.setOnClickListener {
            val suffix = privacySettings.missavSuffix
            val input = android.widget.EditText(this)
            input.setText(suffix)
            input.setSingleLine()
            input.hint = "如: ai, ws, cc, com, top"

            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("修改 MISSAV 域名后缀")
                .setMessage("前缀固定为 missav.\n请输入后缀（不含点）：")
                .setView(input)
                .setPositiveButton("确定") { _, _ ->
                    val newSuffix = input.text.toString().trim().lowercase()
                    if (newSuffix.isNotEmpty() && newSuffix.matches(Regex("^[a-z0-9.-]+$"))) {
                        privacySettings.missavSuffix = newSuffix
                        Toast.makeText(this, "域名已更新为 missav.$newSuffix", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "无效的后缀格式", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("取消", null)
                .show()
        }

        // Icon selection
        iconDefault.setOnClickListener { selectIcon(PrivacySettings.ICON_DEFAULT) }
        iconCalculator.setOnClickListener { selectIcon(PrivacySettings.ICON_CALCULATOR) }
        iconNotes.setOnClickListener { selectIcon(PrivacySettings.ICON_NOTES) }
        iconFile.setOnClickListener { selectIcon(PrivacySettings.ICON_FILE) }

        // Dark mode toggle
        toggleDarkMode.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val mode = when (checkedId) {
                    R.id.btn_light -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
                    R.id.btn_dark -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
                    else -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
                privacySettings.darkMode = mode
                androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(mode)

                tvDarkModeLabel.text = when (checkedId) {
                    R.id.btn_light -> "关闭"
                    R.id.btn_dark -> "开启"
                    else -> "跟随系统"
                }
            }
        }

        // Add rule button
        btnAddRule.setOnClickListener {
            val url = etRuleUrl.text.toString().trim()
            if (url.isNotEmpty()) {
                Toast.makeText(this, "添加规则：$url（功能未实现）", Toast.LENGTH_SHORT).show()
                etRuleUrl.setText("")
            } else {
                Toast.makeText(this, "请输入规则链接", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun toggleAdRulesPanel() {
        isAdRulesPanelExpanded = !isAdRulesPanelExpanded

        if (isAdRulesPanelExpanded) {
            panelAdRules.visibility = View.VISIBLE
            iconArrowRules.rotation = 90f
        } else {
            panelAdRules.visibility = View.GONE
            iconArrowRules.rotation = 0f
        }
    }

    private fun selectIcon(iconId: String) {
        if (iconId == selectedIconId) return

        val iconName = when (iconId) {
            PrivacySettings.ICON_DEFAULT -> "浏览器"
            PrivacySettings.ICON_CALCULATOR -> "计算器"
            PrivacySettings.ICON_NOTES -> "备忘录"
            PrivacySettings.ICON_FILE -> "文件管理器"
            else -> "JAV 浏览器"
        }

        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("更换应用图标")
            .setMessage("确定要将图标更换为「$iconName」吗？\n\n旧图标将从桌面消失，新图标会出现。")
            .setPositiveButton("确定") { _, _ ->
                appIconManager.switchIcon(iconId)
                privacySettings.selectedIcon = iconId
                selectedIconId = iconId
                updateIconSelection()
                Toast.makeText(this, "图标已更换，请在桌面寻找新图标", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun updateIconSelection() {
        // Reset all icons
        iconDefault.isSelected = false
        iconCalculator.isSelected = false
        iconNotes.isSelected = false
        iconFile.isSelected = false

        // Set selected icon
        when (selectedIconId) {
            PrivacySettings.ICON_DEFAULT -> iconDefault.isSelected = true
            PrivacySettings.ICON_CALCULATOR -> iconCalculator.isSelected = true
            PrivacySettings.ICON_NOTES -> iconNotes.isSelected = true
            PrivacySettings.ICON_FILE -> iconFile.isSelected = true
        }
    }

    private fun updateRulesStatus() {
        // Show real rule stats
        val stats = adFilterRules.getRulesStats()
        tvBlockedCount.text = "JSON 规则 ${stats["total"]} 条 · 内置规则 v${adFilterRules.getVersion()}"

        // Update rules stats with last update time
        val lastUpdate = if (adFilterRules.getLastUpdateTime() > 0) {
            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                .format(java.util.Date(adFilterRules.getLastUpdateTime()))
        } else {
            "从未更新"
        }

        val updateLabel = if (privacySettings.updateInterval == PrivacySettings.UPDATE_NEVER) "从不更新" else "每48小时"
        val statusText = "更新策略: $updateLabel · 上次: $lastUpdate"
        tvRulesStatus.text = statusText
        tvUpdateRulesSubtitle.text = updateLabel
    }

    private fun refreshDomainDisplay() {
        val domain = "missav." + privacySettings.missavSuffix
        if (privacySettings.isDomainMappingEnabled) {
            tvProxyModeLabel.text = "自定义域名: $domain"
        } else {
            tvProxyModeLabel.text = "默认域名: $domain"
        }
    }
}

