package com.example.monday.core.utils

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * CompactToast: Renders a sleek, ultra-compact pill HUD notification at the bottom
 * with refined typography and minimal padding to replace bloated OS-level toasts.
 */
object CompactToast {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var activeHudView: View? = null
    private var activeWindowManager: WindowManager? = null

    fun show(context: Context, message: String) {
        mainHandler.post {
            try {
                val appContext = context.applicationContext
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(appContext)) {
                    showOverlayHud(appContext, message)
                } else {
                    Toast.makeText(appContext, message, Toast.LENGTH_SHORT).show()
                }
            } catch (_: Exception) {
                try {
                    Toast.makeText(context.applicationContext, message, Toast.LENGTH_SHORT).show()
                } catch (_: Exception) {}
            }
        }
    }

    private fun showOverlayHud(context: Context, message: String) {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        
        // Remove previous HUD if still visible
        dismissCurrentHud()

        val density = context.resources.displayMetrics.density

        // Create compact pill container
        val pillLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            
            val padHorizontal = (14 * density).toInt()
            val padVertical = (6 * density).toInt()
            setPadding(padHorizontal, padVertical, padHorizontal, padVertical)

            val backgroundDrawable = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 18 * density
                setColor(Color.parseColor("#E61E1E1E")) // Frosted dark obsidian
                setStroke((1 * density).toInt(), Color.parseColor("#334CAF50")) // Subtle emerald border
            }
            background = backgroundDrawable
            elevation = 6 * density
        }

        // Green checkmark icon
        val checkIcon = TextView(context).apply {
            text = "✓"
            setTextColor(Color.parseColor("#4CAF50"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            val marginEnd = (6 * density).toInt()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, marginEnd, 0) }
        }
        pillLayout.addView(checkIcon)

        // Refined compact message text (12.5sp)
        val textView = TextView(context).apply {
            text = message
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            setTypeface(typeface, android.graphics.Typeface.NORMAL)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        pillLayout.addView(textView)

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = (64 * density).toInt() // Positioned neatly above system navigation bar
        }

        pillLayout.alpha = 0f
        pillLayout.translationY = 12 * density

        try {
            windowManager.addView(pillLayout, params)
            activeHudView = pillLayout
            activeWindowManager = windowManager

            // Fast smooth fade in
            pillLayout.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(160)
                .start()

            // Auto dismiss after 1.8 seconds
            mainHandler.postDelayed({
                if (activeHudView == pillLayout) {
                    pillLayout.animate()
                        .alpha(0f)
                        .translationY(12 * density)
                        .setDuration(160)
                        .withEndAction {
                            dismissCurrentHud()
                        }
                        .start()
                }
            }, 1800L)
        } catch (_: Exception) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun dismissCurrentHud() {
        try {
            activeHudView?.let { view ->
                if (view.isAttachedToWindow) {
                    activeWindowManager?.removeView(view)
                }
            }
        } catch (_: Exception) {}
        activeHudView = null
        activeWindowManager = null
    }
}
