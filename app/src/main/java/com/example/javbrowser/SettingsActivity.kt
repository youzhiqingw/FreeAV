package com.example.javbrowser

import android.os.Bundle
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.radiobutton.MaterialRadioButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText

class SettingsActivity : AppCompatActivity() {

    private lateinit var switchLock: SwitchMaterial
    private lateinit var radioGroup: RadioGroup
    private lateinit var btnBack: MaterialButton
    private lateinit var privacySettings: PrivacySettings
    private lateinit var appIconManager: AppIconManager
    private lateinit var biometricHelper: BiometricHelper
    private lateinit var adFilterRules: AdFilterRules
    private lateinit var etCloudUrl: TextInputEditText
    private lateinit var btnUpdateFromCloud: MaterialButton
    private lateinit var tvRulesStatus: android.widget.TextView
    private lateinit var btnExportRules: MaterialButton
    private lateinit var btnImportRules: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Prevent screenshots and hide content in recent apps
        window.setFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE, android.view.WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(R.layout.activity_settings)

        privacySettings = PrivacySettings(this)
        appIconManager = AppIconManager(this)
        adFilterRules = AdFilterRules(this)
        biometricHelper = BiometricHelper(this, privacySettings)

        switchLock = findViewById(R.id.switch_lock)
        radioGroup = findViewById(R.id.radio_group_icon)
        btnBack = findViewById(R.id.btn_back)
        etCloudUrl = findViewById(R.id.et_cloud_url)
        btnUpdateFromCloud = findViewById(R.id.btn_update_from_cloud)
        tvRulesStatus = findViewById(R.id.tv_rules_status)
        btnExportRules = findViewById(R.id.btn_export_rules)
        btnImportRules = findViewById(R.id.btn_import_rules)

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

    private fun loadSettings() {
        // Load lock setting
        switchLock.isChecked = privacySettings.isLockEnabled

        // Load icon setting
        when (privacySettings.selectedIcon) {
            PrivacySettings.ICON_DEFAULT -> findViewById<RadioButton>(R.id.radio_default).isChecked = true
            PrivacySettings.ICON_CALCULATOR -> findViewById<RadioButton>(R.id.radio_calculator).isChecked = true
            PrivacySettings.ICON_NOTES -> findViewById<RadioButton>(R.id.radio_notes).isChecked = true
            PrivacySettings.ICON_FILE -> findViewById<RadioButton>(R.id.radio_file).isChecked = true
        }
    }

    private fun setupListeners() {
        // Lock switch
        switchLock.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                // Test biometric availability
                if (biometricHelper.canAuthenticate()) {
                    biometricHelper.authenticate(
                        onSuccess = {
                            privacySettings.isLockEnabled = true
                            privacySettings.updateUnlockTime()
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
                Toast.makeText(this, "应用锁已停用", Toast.LENGTH_SHORT).show()
            }
        }

        // Icon selection
        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            val selectedIcon = when (checkedId) {
                R.id.radio_default -> PrivacySettings.ICON_DEFAULT
                R.id.radio_calculator -> PrivacySettings.ICON_CALCULATOR
                R.id.radio_notes -> PrivacySettings.ICON_NOTES
                R.id.radio_file -> PrivacySettings.ICON_FILE
                else -> PrivacySettings.ICON_DEFAULT
            }

            if (selectedIcon != privacySettings.selectedIcon) {
                showIconChangeDialog(selectedIcon)
            }
        }

        // Back button
        btnBack.setOnClickListener {
            finish()
        }

        // Set PIN button
        findViewById<Button>(R.id.btn_set_pin).setOnClickListener {
            showSetPinDialog()
        }
    }


    private fun showSetPinDialog() {
        val input = android.widget.EditText(this)
        input.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
        input.filters = arrayOf(android.text.InputFilter.LengthFilter(6))
        input.hint = "请输入 4-6 位 PIN 码"
        
        // Add padding
        val container = android.widget.FrameLayout(this)
        val params = android.widget.FrameLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.leftMargin = resources.getDimensionPixelSize(R.dimen.spacing_md)
        params.rightMargin = resources.getDimensionPixelSize(R.dimen.spacing_md)
        input.layoutParams = params
        container.addView(input)

        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("设置 PIN 码")
            .setMessage("请输入备用 PIN 码（4-6 位数字）")
            .setView(container)
            .setPositiveButton("保存") { _, _ ->
                val pin = input.text.toString()
                if (pin.length in 4..6) {
                    privacySettings.pinCode = pin
                    Toast.makeText(this, "PIN 码已保存", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "PIN 码需为 4-6 位", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showIconChangeDialog(newIcon: String) {
        val iconName = when (newIcon) {
            PrivacySettings.ICON_DEFAULT -> "JAV 浏览器"
            PrivacySettings.ICON_CALCULATOR -> "计算器"
            PrivacySettings.ICON_NOTES -> "备忘录"
            PrivacySettings.ICON_FILE -> "文件管理器"
            else -> "JAV 浏览器"
        }

        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("更换应用图标")
            .setMessage("确定要将图标更换为「$iconName」吗？\n\n旧图标将从桌面消失，新图标会出现。")
            .setPositiveButton("确定") { _, _ ->
                appIconManager.switchIcon(newIcon)
                privacySettings.selectedIcon = newIcon
                Toast.makeText(this, "图标已更换，请在桌面寻找新图标", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("取消") { _, _ ->
                loadSettings() // Revert selection
            }
            .show()
    }
    
    private fun updateRulesStatus() {
        // Load cloud URL
        etCloudUrl.setText(adFilterRules.cloudUrl)
        
        // Update stats
        val stats = adFilterRules.getRulesStats()
        val lastUpdate = if (adFilterRules.getLastUpdateTime() > 0) {
            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                .format(java.util.Date(adFilterRules.getLastUpdateTime()))
        } else {
            "从未更新"
        }
        
        val statusText = """
            版本：${adFilterRules.getVersion()}
            最后更新：$lastUpdate
            
            通用屏蔽 (commonBlock)：${stats["commonBlock"]} 个
            网络拦截（仅专用）：${stats["networkBlock"]} 个
            超链接屏蔽（仅专用）：${stats["linkBlock"]} 个
            Iframe 屏蔽（仅专用）：${stats["iframeBlock"]} 个
            重定向拦截（仅专用）：${stats["redirectBlock"]} 个
            
            总计：${stats["total"]} 条规则
        """.trimIndent()
        
        tvRulesStatus.text = statusText
        
        // Setup listeners
        btnUpdateFromCloud.setOnClickListener {
            val url = etCloudUrl.text.toString().trim()
            if (url.isEmpty()) {
                Toast.makeText(this, "请输入云端规则地址", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            // Save URL
            adFilterRules.cloudUrl = url
            
            // Show progress
            btnUpdateFromCloud.isEnabled = false
            btnUpdateFromCloud.text = "更新中..."
            
            adFilterRules.updateRulesFromCloud(url) { success, message ->
                btnUpdateFromCloud.isEnabled = true
                btnUpdateFromCloud.text = "从云端更新"
                
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                
                if (success) {
                    updateRulesStatus()
                }
            }
        }
        
        btnExportRules.setOnClickListener {
            val json = adFilterRules.exportToJson()
            val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("广告过滤规则", json)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "规则已复制到剪贴板", Toast.LENGTH_SHORT).show()
        }
        
        btnImportRules.setOnClickListener {
            val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clipData = clipboard.primaryClip
            
            if (clipData != null && clipData.itemCount > 0) {
                val json = clipData.getItemAt(0).text.toString()
                
                if (adFilterRules.importFromJson(json)) {
                    Toast.makeText(this, "规则导入成功", Toast.LENGTH_SHORT).show()
                    updateRulesStatus()
                } else {
                    Toast.makeText(this, "规则格式错误，导入失败", Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(this, "剪贴板中没有内容", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
