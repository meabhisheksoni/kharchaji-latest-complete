package com.example.monday.ui.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Region
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import android.content.pm.ServiceInfo
import com.example.monday.MainActivity
import com.example.monday.R
import com.example.monday.ui.voice.VoiceExpenseActivity
import com.example.monday.widget.WidgetInputActivity

class OverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var params: WindowManager.LayoutParams? = null
    private var screenWidth = 0
    private var screenHeight = 0
    private var lastVoiceLaunchTime = 0L

    private var dancingMicView: DancingMicView? = null

    // Battery Guardian: Pause 60FPS animators when phone screen is turned off
    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> dancingMicView?.pauseDancing()
                Intent.ACTION_SCREEN_ON -> dancingMicView?.startDancing()
            }
        }
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "overlay_service_channel"
        private const val DEBOUNCE_WINDOW_MS = 600L
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                createNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, createNotification())
        }

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
            vibrator?.vibrate(android.os.VibrationEffect.createOneShot(80, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(80)
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
            .setContentText("Tap or speak to add expenses instantly")
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
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        // FLAG_NOT_TOUCH_MODAL allows touches outside the touchable bounds to pass directly to underlying apps
        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        )

        params?.gravity = Gravity.TOP or Gravity.END
        params?.x = 0
        params?.y = screenHeight / 3

        windowManager?.addView(floatingView, params)

        dancingMicView = floatingView?.findViewById(R.id.dancing_mic_view)
        val overlayButton = floatingView?.findViewById<View>(R.id.overlay_button_container)

        // Ensure strictly static scale (never expands or shrinks)
        overlayButton?.scaleX = 1.0f
        overlayButton?.scaleY = 1.0f

        // Attach isolated drag/touch listeners
        dancingMicView?.setOnTouchListener(createDragTouchListener(isMic = true))
        overlayButton?.setOnTouchListener(createDragTouchListener(isMic = false))

        // Configure OS-level precise touchable region for 100% transparent passthrough
        setupTouchableRegion(floatingView, dancingMicView, overlayButton)

        // Register screen on/off listener to preserve battery
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        registerReceiver(screenStateReceiver, filter)
    }

    /**
     * Tells Android WindowManager the exact combined physical touch region.
     * Any pixel outside this region passes directly through to the underlying app!
     */
    private fun setupTouchableRegion(rootView: View?, micView: View?, tabView: View?) {
        if (rootView == null) return
        try {
            val listenerClass = Class.forName("android.view.ViewTreeObserver\$OnComputeInternalInsetsListener")
            val internalInsetsInfoClass = Class.forName("android.view.ViewTreeObserver\$InternalInsetsInfo")
            val touchableRegionField = internalInsetsInfoClass.getField("touchableRegion")
            val setTouchableInsetsMethod = internalInsetsInfoClass.getMethod("setTouchableInsets", Int::class.javaPrimitiveType)
            val TOUCHABLE_INSETS_REGION = 3

            val proxy = java.lang.reflect.Proxy.newProxyInstance(
                rootView.context.classLoader,
                arrayOf(listenerClass)
            ) { _, method, args ->
                if (method.name == "onComputeInternalInsets" && args != null && args.isNotEmpty()) {
                    val insetsInfo = args[0]
                    setTouchableInsetsMethod.invoke(insetsInfo, TOUCHABLE_INSETS_REGION)
                    val region = touchableRegionField.get(insetsInfo) as Region
                    region.setEmpty()

                    val loc = IntArray(2)

                    // 1. Add Dancing Mic physical bounds
                    micView?.let { mic ->
                        if (mic.isAttachedToWindow && mic.visibility == View.VISIBLE) {
                            mic.getLocationOnScreen(loc)
                            region.op(loc[0], loc[1], loc[0] + mic.width, loc[1] + mic.height, Region.Op.UNION)
                        }
                    }

                    // 2. Add Side Tab physical bounds
                    tabView?.let { tab ->
                        if (tab.isAttachedToWindow && tab.visibility == View.VISIBLE) {
                            tab.getLocationOnScreen(loc)
                            region.op(loc[0], loc[1], loc[0] + tab.width, loc[1] + tab.height, Region.Op.UNION)
                        }
                    }
                }
                null
            }

            val addMethod = rootView.viewTreeObserver.javaClass.getMethod("addOnComputeInternalInsetsListener", listenerClass)
            addMethod.invoke(rootView.viewTreeObserver, proxy)
        } catch (_: Exception) {
            // Fallback: Layout margins are already 0dp with FLAG_NOT_TOUCH_MODAL
        }
    }

    /**
     * Factory creating isolated touch listeners per-target to prevent multi-touch coordinate corruption.
     * All scale mutations have been removed so the side bar stays at a permanent, stable, static size.
     */
    private fun createDragTouchListener(isMic: Boolean): View.OnTouchListener {
        return object : View.OnTouchListener {
            private var localInitialX = 0
            private var localInitialY = 0
            private var localInitialTouchY = 0f
            private var touchDownTime = 0L
            private var isDraggingLocal = false
            private var longPressRunnable: Runnable? = null
            private val touchSlop = ViewConfiguration.get(this@OverlayService).scaledTouchSlop

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        localInitialX = params?.x ?: 0
                        localInitialY = params?.y ?: 0
                        localInitialTouchY = event.rawY
                        touchDownTime = SystemClock.uptimeMillis()
                        isDraggingLocal = false

                        if (!isMic) {
                            longPressRunnable = Runnable {
                                if (!isDraggingLocal) {
                                    triggerHapticFeedback()
                                    openVoiceExpenseInput()
                                }
                            }
                            v.postDelayed(longPressRunnable, 500L)
                        }
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val deltaY = (event.rawY - localInitialTouchY).toInt()
                        if (Math.abs(deltaY) > touchSlop) {
                            isDraggingLocal = true
                            longPressRunnable?.let { v.removeCallbacks(it) }
                            params?.y = localInitialY + deltaY
                            windowManager?.updateViewLayout(floatingView, params)
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        val duration = SystemClock.uptimeMillis() - touchDownTime
                        longPressRunnable?.let { v.removeCallbacks(it) }

                        if (!isDraggingLocal) {
                            if (isMic && duration < 400L) {
                                triggerHapticFeedback()
                                openVoiceExpenseInput()
                            } else if (!isMic && duration < 500L) {
                                openExpenseInput()
                            }
                        }
                        return true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        longPressRunnable?.let { v.removeCallbacks(it) }
                        return true
                    }
                }
                return false
            }
        }
    }

    private fun openVoiceExpenseInput() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastVoiceLaunchTime < DEBOUNCE_WINDOW_MS) return // Atomic debounce gate
        lastVoiceLaunchTime = now

        val intent = Intent(this, VoiceExpenseActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
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
        try {
            unregisterReceiver(screenStateReceiver)
        } catch (_: Exception) {}

        dancingMicView?.pauseDancing()

        if (floatingView != null && floatingView?.isAttachedToWindow == true) {
            try {
                windowManager?.removeView(floatingView)
            } catch (e: Exception) {
                android.util.Log.e("OverlayService", "Error removing view: ${e.message}")
            }
        }
    }
}
