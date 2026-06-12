package com.example.freeavbrowser

import android.animation.Animator
import android.animation.Animator.AnimatorListener
import android.animation.ValueAnimator
import android.os.Bundle
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.doOnPreDraw
import androidx.core.widget.NestedScrollView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText

class SettingsActivity : AppCompatActivity() {

    private lateinit var toolbar: MaterialToolbar
    private lateinit var switchAdBlocking: SettingsSwitchRow
    private lateinit var switchLock: SettingsSwitchRow
    private lateinit var switchScreenshotBlock: SettingsSwitchRow
    private lateinit var switchBiometric: SettingsSwitchRow
    private lateinit var switchPrivacyMode: SettingsSwitchRow
    private lateinit var switchEasyList: SettingsSwitchRow
    private lateinit var switchCloudflareBypass: SettingsSwitchRow

    private lateinit var itemAdRules: SettingsNavRow
    private lateinit var itemUpdateRules: SettingsNavRow
    private lateinit var itemProxyMode: SettingsNavRow

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
    private lateinit var btnDownloadEasyList: MaterialButton

    private lateinit var privacySettings: PrivacySettings
    private lateinit var appIconManager: AppIconManager
    private lateinit var biometricHelper: BiometricHelper
    private var adFilterRules: AdFilterRules? = null

    private var selectedIconId: String = PrivacySettings.ICON_DEFAULT
    private var isAdRulesPanelExpanded = false
    private var isSettingPin = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        privacySettings = PrivacySettings(this)
        appIconManager = AppIconManager(this)
        biometricHelper = BiometricHelper(this, privacySettings)
        adFilterRules = try {
            AdFilterRules(this)
        } catch (e: Exception) {
            android.util.Log.e("SettingsActivity", "AdFilterRules 初始化失败: ${e.message}", e)
            null
        }

        // 根据设置条件控制防截屏（FLAG_SECURE）
        applyScreenshotBlock(privacySettings.isScreenshotBlockEnabled)

        initViews()
        try { loadSettings() } catch (_: Exception) { }
        try { setupListeners() } catch (_: Exception) { }
        try { updateRulesStatus() } catch (_: Exception) { }

        // Set dynamic version info
        try {
            val tvVersion = findViewById<android.widget.TextView>(R.id.tv_version)
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            tvVersion.text = "v${packageInfo.versionName} (Build ${packageInfo.longVersionCode})"
        } catch (_: Exception) { }

        // Enter transition animation
        @Suppress("DEPRECATION")
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)

        // Staggered card entrance animations
        animateCardsEntrance()
    }

    @Suppress("DEPRECATION")
    override fun finish() {
        super.finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    override fun onDestroy() {
        panelAnimator?.cancel()
        panelAnimator = null
        super.onDestroy()
    }

    // 条件控制 FLAG_SECURE：启用时阻止截屏和最近任务预览
    private fun applyScreenshotBlock(enabled: Boolean) {
        val flag = android.view.WindowManager.LayoutParams.FLAG_SECURE
        if (enabled) {
            window.setFlags(flag, flag)
        } else {
            window.clearFlags(flag)
        }
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
        switchEasyList = findViewById(R.id.switch_easylist)
        switchCloudflareBypass = findViewById(R.id.switch_cloudflare_bypass)

        // Expandable items
        itemAdRules = findViewById(R.id.item_ad_rules)
        itemUpdateRules = findViewById(R.id.item_update_rules)
        itemProxyMode = findViewById(R.id.item_proxy_mode)

        // Panels
        panelAdRules = findViewById(R.id.panel_ad_rules)

        // Icon options
        iconDefault = findViewById(R.id.icon_default)
        iconCalculator = findViewById(R.id.icon_calculator)
        iconNotes = findViewById(R.id.icon_notes)
        iconFile = findViewById(R.id.icon_file)

        // Dark mode toggle
        toggleDarkMode = findViewById(R.id.toggle_dark_mode)
        tvDarkModeLabel = findViewById(R.id.tv_dark_mode_label)

        // TextViews (now inside custom components) — safe fallbacks
        val fallbackTv = TextView(this)
        val fallbackIv = ImageView(this)
        tvBlockedCount = try { switchAdBlocking.getSubtitleView() } catch (_: Exception) { fallbackTv }
        tvRulesStatus = try { itemAdRules.getSubtitleView() } catch (_: Exception) { fallbackTv }
        tvUpdateRulesSubtitle = try { itemUpdateRules.getSubtitleView() } catch (_: Exception) { fallbackTv }
        tvProxyModeLabel = try { itemProxyMode.getSubtitleView() } catch (_: Exception) { fallbackTv }
        iconArrowRules = try { itemAdRules.getArrowView() } catch (_: Exception) { fallbackIv }

        // Rule management
        etRuleUrl = findViewById(R.id.et_rule_url)
        btnAddRule = findViewById(R.id.btn_add_rule)
        btnDownloadEasyList = findViewById(R.id.btn_download_easylist)
    }

    private fun loadSettings() {
        // Load switches state
        switchAdBlocking.isChecked = privacySettings.isAdBlockingEnabled
        switchLock.isChecked = privacySettings.isLockEnabled
        switchEasyList.isChecked = privacySettings.isEasyListEnabled
        btnDownloadEasyList.isEnabled = privacySettings.isEasyListEnabled
        switchBiometric.isChecked = privacySettings.isBiometricEnabled
        switchCloudflareBypass.isChecked = privacySettings.isCloudflareBypassEnabled
        switchScreenshotBlock.isChecked = privacySettings.isScreenshotBlockEnabled
        switchPrivacyMode.isChecked = privacySettings.isPrivacyModeEnabled

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
        switchAdBlocking.setOnCheckedChangeListener { isChecked ->
            privacySettings.isAdBlockingEnabled = isChecked
            Toast.makeText(this, if (isChecked) "广告拦截已启用" else "广告拦截已停用", Toast.LENGTH_SHORT).show()
        }

        // Lock switch
        switchLock.setOnCheckedChangeListener { isChecked ->
            if (isSettingPin) return@setOnCheckedChangeListener

            if (isChecked) {
                // 先检查是否已设置 PIN
                if (!privacySettings.isPinSet()) {
                    switchLock.isChecked = false
                    showSetPinDialog()
                    return@setOnCheckedChangeListener
                }

                privacySettings.isLockEnabled = true
                Toast.makeText(this, "应用锁已启用", Toast.LENGTH_SHORT).show()
            } else {
                // 关闭锁定
                privacySettings.isLockEnabled = false
                privacySettings.isBiometricEnabled = false
                switchBiometric.isChecked = false
                // 询问是否修改 PIN
                showChangePinOption()
            }
        }

        // Screenshot block switch
        switchScreenshotBlock.setOnCheckedChangeListener { isChecked ->
            privacySettings.isScreenshotBlockEnabled = isChecked
            applyScreenshotBlock(isChecked)
            Toast.makeText(this, if (isChecked) "防截屏已启用" else "防截屏已停用", Toast.LENGTH_SHORT).show()
        }

        // Biometric switch
        switchBiometric.setOnCheckedChangeListener { isChecked ->
            if (isChecked && !privacySettings.isLockEnabled) {
                switchBiometric.isChecked = false
                Toast.makeText(this, "请先开启应用锁", Toast.LENGTH_SHORT).show()
                return@setOnCheckedChangeListener
            }
            privacySettings.isBiometricEnabled = isChecked
        }

        // Privacy mode switch
        switchPrivacyMode.setOnCheckedChangeListener { isChecked ->
            privacySettings.isPrivacyModeEnabled = isChecked
            Toast.makeText(this, if (isChecked) "隐私模式已启用，离开页面时将自动清除浏览记录" else "隐私模式已停用", Toast.LENGTH_SHORT).show()
        }

        // EasyList switch
        switchEasyList.setOnCheckedChangeListener { isChecked ->
            privacySettings.isEasyListEnabled = isChecked
            btnDownloadEasyList.isEnabled = isChecked

            if (!isChecked) {
                Toast.makeText(this, "EasyList 规则已关闭", Toast.LENGTH_SHORT).show()
            }
        }

        // Cloudflare Bypass switch
        switchCloudflareBypass.setOnCheckedChangeListener { isChecked ->
            privacySettings.isCloudflareBypassEnabled = isChecked
            Toast.makeText(
                this,
                if (isChecked) "Cloudflare 绕过已启用" else "Cloudflare 绕过已停用",
                Toast.LENGTH_SHORT
            ).show()
        }

        // Download EasyList button
        btnDownloadEasyList.setOnClickListener {
            if (!privacySettings.isEasyListEnabled) {
                Toast.makeText(this, "请先启用 EasyList 规则", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (adFilterRules == null) {
                Toast.makeText(this, "规则引擎未初始化，请重启应用", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val progressDialog = MaterialAlertDialogBuilder(this)
                .setTitle("下载中...")
                .setMessage("正在从 5 个源下载规则，请稍候")
                .setCancelable(false)
                .create()
            progressDialog.show()

            adFilterRules?.updateRulesFromExternalSources { success, message ->
                progressDialog.dismiss()
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                if (success) {
                    updateRulesStatus()
                }
            }
        }

        // Expandable items
        itemAdRules.setOnClickListener {
            toggleAdRulesPanel()
        }

        itemUpdateRules.setOnClickListener {
            val currentInterval = privacySettings.updateInterval
            val options = arrayOf("从不更新", "每48小时")
            val checkedItem = if (currentInterval == PrivacySettings.UPDATE_48H) 1 else 0

            MaterialAlertDialogBuilder(this)
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

            MaterialAlertDialogBuilder(this)
                .setTitle("代理模式")
                .setSingleChoiceItems(options, checkedItem) { dialog, which ->
                    privacySettings.proxyMode = values[which]
                    tvProxyModeLabel.text = options[which]
                    dialog.dismiss()
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

    private var panelAnimator: ValueAnimator? = null

    private fun toggleAdRulesPanel() {
        // Cancel any in-progress animation to handle rapid toggling
        panelAnimator?.cancel()

        isAdRulesPanelExpanded = !isAdRulesPanelExpanded

        if (isAdRulesPanelExpanded) {
            // Determine starting height: 0 if panel was hidden, current height if mid-animation
            val currentHeight = if (panelAdRules.visibility == View.GONE) {
                panelAdRules.visibility = View.VISIBLE
                0
            } else {
                panelAdRules.height.coerceAtLeast(0)
            }
            panelAdRules.layoutParams.height = currentHeight
            panelAdRules.requestLayout()

            // Measure the full target height using parent width
            val parentWidth = (panelAdRules.parent as? View)?.width ?: 0
            val widthSpec = View.MeasureSpec.makeMeasureSpec(parentWidth, View.MeasureSpec.EXACTLY)
            val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            panelAdRules.measure(widthSpec, heightSpec)
            val fullHeight = panelAdRules.measuredHeight

            // Animate height from current to target
            ValueAnimator.ofInt(currentHeight, fullHeight).apply {
                duration = 300
                interpolator = DecelerateInterpolator()
                var cancelled = false
                addUpdateListener { animator ->
                    panelAdRules.layoutParams.height = animator.animatedValue as Int
                    panelAdRules.requestLayout()
                }
                addListener(object : AnimatorListener {
                    override fun onAnimationStart(animation: Animator) {}
                    override fun onAnimationCancel(animation: Animator) { cancelled = true }
                    override fun onAnimationRepeat(animation: Animator) {}
                    override fun onAnimationEnd(animation: Animator) {
                        if (!cancelled) {
                            // Reset to wrap_content for flexible layout on content change
                            panelAdRules.layoutParams.height =
                                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                            panelAdRules.requestLayout()
                        }
                        panelAnimator = null
                    }
                })
                start()
            }.also { panelAnimator = it }
        } else {
            // Animate height from current to 0, then hide
            val startHeight = panelAdRules.height
            if (startHeight <= 0) {
                panelAdRules.visibility = View.GONE
                return
            }

            ValueAnimator.ofInt(startHeight, 0).apply {
                duration = 250
                interpolator = DecelerateInterpolator()
                var cancelled = false
                addUpdateListener { animator ->
                    panelAdRules.layoutParams.height = animator.animatedValue as Int
                    panelAdRules.requestLayout()
                }
                addListener(object : AnimatorListener {
                    override fun onAnimationStart(animation: Animator) {}
                    override fun onAnimationCancel(animation: Animator) { cancelled = true }
                    override fun onAnimationRepeat(animation: Animator) {}
                    override fun onAnimationEnd(animation: Animator) {
                        if (!cancelled) {
                            panelAdRules.visibility = View.GONE
                        }
                        panelAnimator = null
                    }
                })
                start()
            }.also { panelAnimator = it }
        }

        // Smooth arrow rotation animation (duration synced with expand)
        iconArrowRules.animate()
            .rotation(if (isAdRulesPanelExpanded) 90f else 0f)
            .setDuration(300)
            .start()
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

        // Cancel any in-progress scale animations on icon options
        listOf(iconDefault, iconCalculator, iconNotes, iconFile).forEach { icon ->
            icon.animate().cancel()
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("更换应用图标")
            .setMessage("确定要将图标更换为「$iconName」吗？\n\n旧图标将从桌面消失，新图标会出现。")
            .setPositiveButton("确定") { _, _ ->
                appIconManager.switchIcon(iconId)
                privacySettings.selectedIcon = iconId
                selectedIconId = iconId
                updateIconSelection()

                // Bounce animation on newly selected icon
                val selectedIcon = when (iconId) {
                    PrivacySettings.ICON_DEFAULT -> iconDefault
                    PrivacySettings.ICON_CALCULATOR -> iconCalculator
                    PrivacySettings.ICON_NOTES -> iconNotes
                    PrivacySettings.ICON_FILE -> iconFile
                    else -> null
                }
                selectedIcon?.let { animateIconBounce(it) }

                Toast.makeText(this, "图标已更换，请在桌面寻找新图标", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("取消") { _, _ ->
                // Subtle scale pulse on all icons to signal cancellation
                listOf(iconDefault, iconCalculator, iconNotes, iconFile).forEach { icon ->
                    icon.animate()
                        .scaleX(0.92f).scaleY(0.92f)
                        .setDuration(80)
                        .withEndAction {
                            icon.animate()
                                .scaleX(1f).scaleY(1f)
                                .setDuration(120)
                                .setInterpolator(OvershootInterpolator(1.5f))
                                .start()
                        }
                        .start()
                }
            }
            .show()
    }

    private fun animateIconBounce(view: View) {
        view.animate().cancel()
        view.animate()
            .scaleX(1.18f).scaleY(1.18f)
            .setDuration(120)
            .withEndAction {
                view.animate()
                    .scaleX(1f).scaleY(1f)
                    .setDuration(200)
                    .setInterpolator(OvershootInterpolator(2f))
                    .start()
            }
            .start()
    }

    private fun animateCardsEntrance() {
        val cards = listOfNotNull(
            findViewById<MaterialCardView?>(R.id.card_ad_filtering),
            findViewById<MaterialCardView?>(R.id.card_privacy),
            findViewById<MaterialCardView?>(R.id.card_app_icon),
            findViewById<MaterialCardView?>(R.id.card_display),
            findViewById<MaterialCardView?>(R.id.card_network)
        )

        if (cards.isEmpty()) return

        val rootLayout = findViewById<NestedScrollView>(R.id.nested_scroll_view)
        rootLayout?.doOnPreDraw {
            cards.forEachIndexed { index, card ->
                card.alpha = 0f
                card.translationY = 48f
                card.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay((index * 70).toLong())
                    .setDuration(400)
                    .setInterpolator(DecelerateInterpolator(1.2f))
                    .start()
            }
        }
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
        if (adFilterRules == null) {
            tvBlockedCount.text = "规则加载失败"
            tvRulesStatus.text = "请重启应用"
            tvUpdateRulesSubtitle.text = "更新失败"
            return
        }
        // Show real rule stats
        val rules = adFilterRules
        if (rules == null) {
            tvBlockedCount.text = "规则加载失败"
            tvRulesStatus.text = "请重启应用"
            tvUpdateRulesSubtitle.text = ""
            return
        }
        val stats = rules.getRulesStats()
        tvBlockedCount.text = "JSON 规则 ${stats["total"]} 条 · 内置规则 v${rules.getVersion()}"

        // Update rules stats with last update time
        val lastUpdate = if (rules.getLastUpdateTime() > 0) {
            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                .format(java.util.Date(rules.getLastUpdateTime()))
        } else {
            "从未更新"
        }

        val updateLabel = if (privacySettings.updateInterval == PrivacySettings.UPDATE_NEVER) "从不更新" else "每48小时"
        val statusText = "更新策略: $updateLabel · 上次: $lastUpdate"
        tvRulesStatus.text = statusText
        tvUpdateRulesSubtitle.text = updateLabel
    }

    private fun showSetPinDialog() {
        val input = com.google.android.material.textfield.TextInputEditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            filters = arrayOf(android.text.InputFilter.LengthFilter(6))
            hint = "4-6 位数字"
        }

        val textInputLayout = com.google.android.material.textfield.TextInputLayout(this).apply {
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            )
            boxBackgroundMode = com.google.android.material.textfield.TextInputLayout.BOX_BACKGROUND_OUTLINE
            hint = "PIN 码"
            addView(input)
        }

        val container = android.widget.FrameLayout(this)
        val params = android.widget.FrameLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
        val density = resources.displayMetrics.density
        params.leftMargin = (20 * density).toInt()
        params.rightMargin = (20 * density).toInt()
        textInputLayout.layoutParams = params
        container.addView(textInputLayout)

        MaterialAlertDialogBuilder(this)
            .setTitle("设置 PIN 码")
            .setMessage("设置备份 PIN 码（4-6 位数字），用于在无法使用指纹时解锁")
            .setView(container)
            .setPositiveButton("保存") { _, _ ->
                val pin = input.text.toString()
                if (pin.length in 4..6) {
                    privacySettings.pinCode = pin
                    Toast.makeText(this, "PIN 码已保存", Toast.LENGTH_SHORT).show()
                    // 保存 PIN 后自动开启锁定（避免递归触发监听器）
                    isSettingPin = true
                    switchLock.isChecked = true
                    isSettingPin = false
                } else {
                    Toast.makeText(this, "PIN 码必须是 4-6 位数字", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消") { _, _ ->
                switchLock.isChecked = false
            }
            .show()
    }

    private fun showChangePinOption() {
        if (!privacySettings.isPinSet()) return

        MaterialAlertDialogBuilder(this)
            .setTitle("应用锁已停用")
            .setMessage("是否修改 PIN 码？")
            .setPositiveButton("修改 PIN") { _, _ ->
                showChangePinDialog()
            }
            .setNegativeButton("保留") { _, _ ->
                // 不做修改
            }
            .show()
    }

    private fun showChangePinDialog() {
        // 先验证旧 PIN
        val oldInput = com.google.android.material.textfield.TextInputEditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            filters = arrayOf(android.text.InputFilter.LengthFilter(6))
            hint = "4-6 位数字"
        }

        val oldTextInputLayout = com.google.android.material.textfield.TextInputLayout(this).apply {
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            )
            boxBackgroundMode = com.google.android.material.textfield.TextInputLayout.BOX_BACKGROUND_OUTLINE
            hint = "当前 PIN 码"
            addView(oldInput)
        }

        val container = android.widget.FrameLayout(this)
        val params = android.widget.FrameLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
        val density = resources.displayMetrics.density
        params.leftMargin = (20 * density).toInt()
        params.rightMargin = (20 * density).toInt()
        oldTextInputLayout.layoutParams = params
        container.addView(oldTextInputLayout)

        MaterialAlertDialogBuilder(this)
            .setTitle("修改 PIN 码")
            .setMessage("请输入当前 PIN 码以验证身份")
            .setView(container)
            .setPositiveButton("验证") { _, _ ->
                val oldPin = oldInput.text.toString()
                if (privacySettings.validatePin(oldPin)) {
                    // 旧 PIN 正确，输入新 PIN
                    showNewPinDialog()
                } else {
                    Toast.makeText(this, "PIN 码错误", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showNewPinDialog() {
        val newInput = com.google.android.material.textfield.TextInputEditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            filters = arrayOf(android.text.InputFilter.LengthFilter(6))
            hint = "4-6 位数字"
        }

        val newTextInputLayout = com.google.android.material.textfield.TextInputLayout(this).apply {
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            )
            boxBackgroundMode = com.google.android.material.textfield.TextInputLayout.BOX_BACKGROUND_OUTLINE
            hint = "新 PIN 码"
            addView(newInput)
        }

        val container = android.widget.FrameLayout(this)
        val params = android.widget.FrameLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
        val density = resources.displayMetrics.density
        params.leftMargin = (20 * density).toInt()
        params.rightMargin = (20 * density).toInt()
        newTextInputLayout.layoutParams = params
        container.addView(newTextInputLayout)

        MaterialAlertDialogBuilder(this)
            .setTitle("设置新 PIN 码")
            .setView(container)
            .setPositiveButton("保存") { _, _ ->
                val newPin = newInput.text.toString()
                if (newPin.length in 4..6) {
                    privacySettings.pinCode = newPin
                    Toast.makeText(this, "PIN 码已更新", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "PIN 码必须是 4-6 位数字", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
}

