package com.example.monday.ui.overlay

import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import android.content.pm.ServiceInfo
import com.example.monday.MainActivity
import com.example.monday.R
import com.example.monday.core.utils.*
import com.example.monday.data.models.TodoItem
import com.example.monday.widget.WidgetInputActivity

class OverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var params: WindowManager.LayoutParams? = null
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var screenWidth = 0
    private var screenHeight = 0
    private var isExpanded = false
    private var scaleAnimator: ValueAnimator? = null

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "overlay_service_channel"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // CRITICAL: startForeground() MUST be the first call to avoid 5-second ANR timeout.
        // Any initialization (haptic feedback, activity launch, etc.) that blocks before this
        // call will cause Android to kill the service.
        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // API 34+
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                createNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, createNotification())
        }

        // Now safe to do non-time-critical work after the service is in foreground state
        if (intent?.action == "ACTION_QUICK_ADD_PAYMENT") {
            val amount = intent.getStringExtra("EXTRA_AMOUNT") ?: ""
            triggerHapticFeedback()
            openExpenseInputWithAmount(amount)
        }
        return START_STICKY
    }
    
    private fun triggerHapticFeedback() {
        val prefManager = com.example.monday.managers.PreferenceManager.from(this)
        if (prefManager.getPaymentMonitorSetting("enable_vibration") != true) return

        val vibrator = getSystemService(android.os.Vibrator::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(android.os.VibrationEffect.createOneShot(500, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(500)
        }
    }

    private fun openExpenseInputWithAmount(amount: String) {
        val inputIntent = Intent(this, WidgetInputActivity::class.java).apply {
            putExtra("field", "item_name")
            putExtra("prefill_amount", amount.replace(",", ""))
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(inputIntent)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Floating Button Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the floating expense button active"
                setShowBadge(false)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Floating Button Active")
            .setContentText("Tap to add expenses from anywhere")
            .setSmallIcon(R.drawable.ic_add_expense)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        val displayMetrics = DisplayMetrics()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        windowManager?.defaultDisplay?.getMetrics(displayMetrics)
        screenWidth = displayMetrics.widthPixels
        screenHeight = displayMetrics.heightPixels

        floatingView = LayoutInflater.from(this).inflate(R.layout.overlay_button, null)

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        params?.gravity = Gravity.TOP or Gravity.END
        params?.x = 0
        params?.y = screenHeight / 3

        windowManager?.addView(floatingView, params)

        val overlayButton = floatingView?.findViewById<View>(R.id.overlay_button_container)
        
        overlayButton?.addOnLayoutChangeListener { v, left, top, right, bottom, _, _, _, _ ->
            v.pivotX = (right - left).toFloat()
            v.pivotY = (bottom - top).toFloat() / 2f
        }

        overlayButton?.setOnTouchListener(object : View.OnTouchListener {
            private var lastAction = 0
            private val CLICK_THRESHOLD = 30
            private var isDragging = false
            private var longPressStartTime = 0L
            private val LONG_PRESS_DURATION = 500L
            private var longPressRunnable: Runnable? = null

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params?.x ?: 0
                        initialY = params?.y ?: 0
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        lastAction = MotionEvent.ACTION_DOWN
                        isDragging = false
                        longPressStartTime = System.currentTimeMillis()
                        
                        longPressRunnable = Runnable {
                            if (!isDragging) {
                                triggerHapticFeedback()
                                pulseButton()
                                val scanIntent = Intent("com.example.monday.ACTION_SCAN_CART")
                                scanIntent.setPackage(packageName)
                                sendBroadcast(scanIntent)
                            }
                        }
                        v.postDelayed(longPressRunnable, LONG_PRESS_DURATION)
                        expandButton()
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val deltaY = (event.rawY - initialTouchY).toInt()
                        if (Math.abs(deltaY) > CLICK_THRESHOLD) {
                            isDragging = true
                            longPressRunnable?.let { v.removeCallbacks(it) }
                            params?.y = initialY + deltaY
                            windowManager?.updateViewLayout(floatingView, params)
                        }
                        lastAction = MotionEvent.ACTION_MOVE
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        val pressDuration = System.currentTimeMillis() - longPressStartTime
                        longPressRunnable?.let { v.removeCallbacks(it) }
                        if (!isDragging && pressDuration < LONG_PRESS_DURATION) {
                            openExpenseInput()
                        }
                        collapseButton()
                        lastAction = MotionEvent.ACTION_UP
                        return true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        longPressRunnable?.let { v.removeCallbacks(it) }
                        collapseButton()
                        return true
                    }
                }
                return false
            }
        })
        collapseButton()
    }

    private fun expandButton() {
        isExpanded = true
        floatingView?.findViewById<View>(R.id.overlay_button_container)?.let { container ->
            scaleAnimator?.cancel()
            scaleAnimator = ValueAnimator.ofFloat(container.scaleX, 1f).apply {
                duration = 150
                interpolator = DecelerateInterpolator()
                addUpdateListener { animation ->
                    val scale = animation.animatedValue as Float
                    container.scaleX = scale
                    container.scaleY = scale
                }
                start()
            }
        }
    }

    private fun collapseButton() {
        isExpanded = false
        floatingView?.findViewById<View>(R.id.overlay_button_container)?.let { container ->
            scaleAnimator?.cancel()
            scaleAnimator = ValueAnimator.ofFloat(container.scaleX, 0.7f).apply {
                duration = 200
                interpolator = DecelerateInterpolator()
                addUpdateListener { animation ->
                    val scale = animation.animatedValue as Float
                    container.scaleX = scale
                    container.scaleY = scale
                }
                start()
            }
        }
    }

    private fun pulseButton() {
        floatingView?.findViewById<View>(R.id.overlay_button_container)?.let { container ->
            container.animate()
                .scaleX(1.2f)
                .scaleY(1.2f)
                .setDuration(100)
                .withEndAction {
                    container.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(100)
                        .start()
                }
                .start()
        }
    }

    private fun openExpenseInput() {
        val intent = Intent(this, WidgetInputActivity::class.java).apply {
            putExtra("field", "item_name")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (floatingView != null && floatingView?.isAttachedToWindow == true) {
            try {
                windowManager?.removeView(floatingView)
            } catch (e: Exception) {
                android.util.Log.e("OverlayService", "Error removing view: ${e.message}")
            }
        }
    }
}
