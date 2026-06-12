package com.example.freeavbrowser

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView

class SettingsNavRow @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val iconView: ImageView
    private val titleView: TextView
    private val subtitleView: TextView
    private val arrowView: ImageView

    var title: String
        get() = titleView.text.toString()
        set(value) { titleView.text = value }

    var subtitle: String?
        get() = if (subtitleView.visibility == VISIBLE) subtitleView.text.toString() else null
        set(value) {
            if (!value.isNullOrEmpty()) {
                subtitleView.text = value
                subtitleView.visibility = VISIBLE
            } else {
                subtitleView.visibility = GONE
            }
        }

    init {
        LayoutInflater.from(context).inflate(R.layout.view_settings_nav_row, this, true)

        iconView = findViewById(R.id.settings_icon)
        titleView = findViewById(R.id.settings_title)
        subtitleView = findViewById(R.id.settings_subtitle)
        arrowView = findViewById(R.id.settings_arrow)

        attrs?.let {
            val typedArray = context.obtainStyledAttributes(it, R.styleable.SettingsNavRow)
            try {
                val iconRes = typedArray.getResourceId(R.styleable.SettingsNavRow_settingsIcon, 0)
                if (iconRes != 0) iconView.setImageResource(iconRes)

                val title = typedArray.getString(R.styleable.SettingsNavRow_settingsTitle)
                if (!title.isNullOrEmpty()) titleView.text = title

                val subtitle = typedArray.getString(R.styleable.SettingsNavRow_settingsSubtitle)
                if (!subtitle.isNullOrEmpty()) {
                    subtitleView.text = subtitle
                    subtitleView.visibility = VISIBLE
                } else {
                    subtitleView.visibility = GONE
                }
            } finally {
                typedArray.recycle()
            }
        }
    }

    fun setIcon(resId: Int) { iconView.setImageResource(resId) }
    fun getSubtitleView(): TextView = subtitleView
    fun getArrowView(): ImageView = arrowView
    override fun setOnClickListener(l: OnClickListener?) {
        super.setOnClickListener(l)
        isClickable = true
        isFocusable = true
    }
}
