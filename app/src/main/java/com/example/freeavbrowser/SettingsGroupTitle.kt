package com.example.freeavbrowser

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.TextView

class SettingsGroupTitle @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val titleView: TextView

    var title: String
        get() = titleView.text.toString()
        set(value) { titleView.text = value }

    init {
        LayoutInflater.from(context).inflate(R.layout.view_settings_group_title, this, true)
        titleView = findViewById(R.id.group_title)

        attrs?.let {
            val typedArray = context.obtainStyledAttributes(it, R.styleable.SettingsGroupTitle)
            try {
                val title = typedArray.getString(R.styleable.SettingsGroupTitle_groupTitle)
                if (!title.isNullOrEmpty()) titleView.text = title
            } finally {
                typedArray.recycle()
            }
        }
    }
}
