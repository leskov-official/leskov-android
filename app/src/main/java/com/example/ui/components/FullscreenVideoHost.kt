package com.example.ui.components

import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/** Owns Chromium's custom video view and releases it before the Activity is destroyed. */
class FullscreenVideoHost(private val activity: ComponentActivity) {
    private var container: FrameLayout? = null
    val isActive: Boolean get() = container != null
    private var callback: WebChromeClient.CustomViewCallback? = null
    private var previousFocus: View? = null
    private val back = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = hide()
    }

    init { activity.onBackPressedDispatcher.addCallback(activity, back) }

    fun show(view: View, customCallback: WebChromeClient.CustomViewCallback) {
        if (container != null) {
            customCallback.onCustomViewHidden()
            return
        }
        previousFocus = activity.currentFocus
        callback = customCallback
        val overlay = FrameLayout(activity).apply {
            setBackgroundColor(Color.BLACK)
            addView(view, FrameLayout.LayoutParams(-1, -1))
            keepScreenOn = true
            isFocusableInTouchMode = true
        }
        container = overlay
        (activity.window.decorView as ViewGroup).addView(overlay, ViewGroup.LayoutParams(-1, -1))
        overlay.requestFocus()
        WindowCompat.getInsetsController(activity.window, overlay).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
        back.isEnabled = true
    }

    fun hide() {
        val overlay = container ?: return
        container = null
        back.isEnabled = false
        overlay.keepScreenOn = false
        overlay.removeAllViews()
        (overlay.parent as? ViewGroup)?.removeView(overlay)
        WindowCompat.getInsetsController(activity.window, activity.window.decorView)
            .show(WindowInsetsCompat.Type.systemBars())
        previousFocus?.requestFocus()
        previousFocus = null
        val pending = callback
        callback = null
        pending?.onCustomViewHidden()
    }
}
