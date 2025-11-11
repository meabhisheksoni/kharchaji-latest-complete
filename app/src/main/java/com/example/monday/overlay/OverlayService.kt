package com.example.monday.overlay

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
import com.example.monday.MainActivity
import com.example.monday.R
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

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "overlay_service_channel"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)
        return START_STICKY
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

        // Get screen dimensions
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
        params?.x = 0 // Start at right edge
        params?.y = screenHeight / 3 // Middle-ish of screen

        windowManager?.addView(floatingView, params)

        val overlayButton = floatingView?.findViewById<FrameLayout>(R.id.overlay_button_container)
        
        overlayButton?.setOnTouchListener(object : View.OnTouchListener {
            private var lastAction = 0
            private val CLICK_THRESHOLD = 10
            private var isDragging = false

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params?.x ?: 0
                        initialY = params?.y ?: 0
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        lastAction = MotionEvent.ACTION_DOWN
                        isDragging = false
                        
                        // Expand on touch
                        expandButton()
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val deltaY = (event.rawY - initialTouchY).toInt()
                        
                        if (Math.abs(deltaY) > CLICK_THRESHOLD) {
                            isDragging = true
                            // Only allow vertical movement, keep X fixed at edge
                            params?.y = initialY + deltaY
                            windowManager?.updateViewLayout(floatingView, params)
                        }
                        lastAction = MotionEvent.ACTION_MOVE
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!isDragging) {
                            // It's a click
                            openExpenseInput()
                        }
                        
                        // Collapse after release
                        collapseButton()
                        lastAction = MotionEvent.ACTION_UP
                        return true
                    }
                }
                return false
            }
        })
        
        // Start collapsed
        collapseButton()
    }

    private fun expandButton() {
        isExpanded = true
        floatingView?.findViewById<FrameLayout>(R.id.overlay_button_container)?.let { container ->
            val animator = ValueAnimator.ofFloat(0.7f, 1f)
            animator.duration = 150
            animator.interpolator = DecelerateInterpolator()
            animator.addUpdateListener { animation ->
                val scale = animation.animatedValue as Float
                container.scaleX = scale
                container.scaleY = scale
            }
            animator.start()
        }
    }

    private fun collapseButton() {
        isExpanded = false
        floatingView?.findViewById<FrameLayout>(R.id.overlay_button_container)?.let { container ->
            val animator = ValueAnimator.ofFloat(1f, 0.7f)
            animator.duration = 200
            animator.interpolator = DecelerateInterpolator()
            animator.addUpdateListener { animation ->
                val scale = animation.animatedValue as Float
                container.scaleX = scale
                container.scaleY = scale
            }
            animator.start()
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
        if (floatingView != null) {
            windowManager?.removeView(floatingView)
        }
    }
}
