package com.example.freeavbrowser

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.google.android.material.switchmaterial.SwitchMaterial

class SettingsSwitchRow @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val iconView: ImageView
    private val titleView: TextView
    private val subtitleView: TextView
    private val switchView: SwitchMaterial

    var isChecked: Boolean
        get() = switchView.isChecked
        set(value) { switchView.isChecked = value }

    fun setOnCheckedChangeListener(listener: (Boolean) -> Unit) {
        switchView.setOnCheckedChangeListener { _, isChecked -> listener(isChecked) }
    }

    init {
        LayoutInflater.from(context).inflate(R.layout.view_settings_switch_row, this, true)

        iconView = findViewById(R.id.settings_icon)
        titleView = findViewById(R.id.settings_title)
        subtitleView = findViewById(R.id.settings_subtitle)
        switchView = findViewById(R.id.settings_switch)

        attrs?.let {
            val typedArray = context.obtainStyledAttributes(it, R.styleable.SettingsSwitchRow)
            try {
                val iconRes = typedArray.getResourceId(R.styleable.SettingsSwitchRow_settingsIcon, 0)
                if (iconRes != 0) iconView.setImageResource(iconRes)

                val title = typedArray.getString(R.styleable.SettingsSwitchRow_settingsTitle)
                if (!title.isNullOrEmpty()) titleView.text = title

                val subtitle = typedArray.getString(R.styleable.SettingsSwitchRow_settingsSubtitle)
                if (!subtitle.isNullOrEmpty()) {
                    subtitleView.text = subtitle
                    subtitleView.visibility = VISIBLE
                } else {
                    subtitleView.visibility = GONE
                }

                switchView.isChecked = typedArray.getBoolean(R.styleable.SettingsSwitchRow_settingsChecked, false)
            } finally {
                typedArray.recycle()
            }
        }
    }

    fun setTitle(title: String) { titleView.text = title }
    fun setSubtitle(subtitle: String?) {
        if (!subtitle.isNullOrEmpty()) {
            subtitleView.text = subtitle
            subtitleView.visibility = VISIBLE
        } else {
            subtitleView.visibility = GONE
        }
    }
    fun setIcon(resId: Int) { iconView.setImageResource(resId) }
    fun getSubtitleView(): TextView = subtitleView
}
