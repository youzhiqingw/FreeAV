package com.example.freeavbrowser

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.view.animation.CycleInterpolator
import android.view.animation.TranslateAnimation
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class LockActivity : AppCompatActivity() {

    private lateinit var privacySettings: PrivacySettings
    private lateinit var biometricHelper: BiometricHelper
    private lateinit var tvPinDisplay: TextView
    private var currentPinInput = StringBuilder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Prevent screenshots and hide content in recent apps
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        
        privacySettings = PrivacySettings(this)
        
        // Set activity title and task description based on privacy mode
        title = privacySettings.currentAppLabel
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            setTaskDescription(
                android.app.ActivityManager.TaskDescription.Builder()
                    .setLabel(privacySettings.currentAppLabel)
                    .build()
            )
        } else {
            @Suppress("DEPRECATION")
            setTaskDescription(
                android.app.ActivityManager.TaskDescription(
                    privacySettings.currentAppLabel,
                    android.graphics.BitmapFactory.decodeResource(resources, privacySettings.currentIconResourceId)
                )
            )
        }
        
        setContentView(R.layout.activity_lock)


        biometricHelper = BiometricHelper(this, privacySettings)
        tvPinDisplay = findViewById(R.id.tv_pin_display)

        setupPinPad()
        setupBiometricButton()

        // Handle back press with OnBackPressedDispatcher (replaces deprecated onBackPressed)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                moveTaskToBack(true)
            }
        })

        // Auto-start biometric only if user enabled it and device supports it
        if (privacySettings.isBiometricEnabled && biometricHelper.canAuthenticate()) {
            startBiometricAuth()
        }

        // Enter transition animation
        @Suppress("DEPRECATION")
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    @Suppress("DEPRECATION")
    override fun finish() {
        super.finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    private fun setupPinPad() {
        // Find all number buttons in the grid
        val gridLayout = findViewById<android.widget.GridLayout>(R.id.grid_pin)
        for (i in 0 until gridLayout.childCount) {
            val view = gridLayout.getChildAt(i)
            if (view is Button && view.tag != null) {
                view.setScaleAnimation(0.9f, 120)
                view.setOnClickListener {
                    appendPinDigit(view.tag.toString())
                }
            }
        }

        val btnDelete = findViewById<MaterialButton>(R.id.btn_delete)
        btnDelete.setScaleAnimation(0.9f, 120)
        btnDelete.setOnClickListener {
            if (currentPinInput.isNotEmpty()) {
                currentPinInput.deleteCharAt(currentPinInput.length - 1)
                updatePinDisplay()
            }
        }

        val btnEnter = findViewById<MaterialButton>(R.id.btn_enter)
        btnEnter.setScaleAnimation(0.9f, 120)
        btnEnter.setOnClickListener {
            verifyPin()
        }
        
        updatePinDisplay()
    }

    private fun appendPinDigit(digit: String) {
        if (currentPinInput.length < 6) {
            currentPinInput.append(digit)
            updatePinDisplay()
        }
    }

    private fun updatePinDisplay() {
        val sb = StringBuilder()
        for (i in currentPinInput.indices) {
            sb.append("•")
        }
        if (sb.isEmpty()) {
            tvPinDisplay.text = getString(R.string.enter_pin)
            tvPinDisplay.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_HeadlineMedium)
        } else {
            tvPinDisplay.text = sb.toString()
            tvPinDisplay.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_HeadlineLarge)
        }
    }

    private fun verifyPin() {
        val input = currentPinInput.toString()
        if (privacySettings.validatePin(input)) {
            unlockApp()
        } else {
            Toast.makeText(this, R.string.incorrect_pin, Toast.LENGTH_SHORT).show()
            currentPinInput.clear()
            updatePinDisplay()
            
            // Shake animation on incorrect PIN
            val shake = TranslateAnimation(0f, 20f, 0f, 0f).apply {
                duration = 400
                interpolator = CycleInterpolator(4f)
            }
            tvPinDisplay.startAnimation(shake)
        }
    }

    private fun setupBiometricButton() {
        val btnBiometric = findViewById<Button>(R.id.btn_use_biometric)
        if (privacySettings.isBiometricEnabled && biometricHelper.canAuthenticate()) {
            btnBiometric.visibility = View.VISIBLE
            btnBiometric.setScaleAnimation()
            btnBiometric.setOnClickListener {
                startBiometricAuth()
            }
        } else {
            btnBiometric.visibility = View.GONE
        }
    }

    private fun startBiometricAuth() {
        biometricHelper.authenticate(
            onSuccess = {
                unlockApp()
            },
            onError = {
                // Toast.makeText(this, "Authentication failed", Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun unlockApp() {
        privacySettings.updateUnlockTime()
        setResult(RESULT_OK)
        finish()
    }
}
