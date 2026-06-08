package com.example.javbrowser

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator

/**
 * Adds a subtle scale-down / scale-up animation on touch for tactile feedback.
 * Complements Material 3's built-in ripple effect with a springy scale motion.
 *
 * @param scaleDown The scale factor on press (default 0.95f = 5% shrink)
 * @param duration  Animation duration in ms (default 150)
 */
@SuppressLint("ClickableViewAccessibility")
fun View.setScaleAnimation(scaleDown: Float = 0.95f, duration: Long = 150) {
    val interpolator = OvershootInterpolator(2f)
    setOnTouchListener { v, event ->
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                v.animate()
                    .scaleX(scaleDown)
                    .scaleY(scaleDown)
                    .setDuration(duration)
                    .setInterpolator(interpolator)
                    .start()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                v.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(duration)
                    .setInterpolator(interpolator)
                    .start()
            }
        }
        // Return false so the click listener still fires
        false
    }
}
