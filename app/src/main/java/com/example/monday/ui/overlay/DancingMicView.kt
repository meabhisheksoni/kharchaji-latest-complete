package com.example.monday.ui.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import com.example.monday.R
import kotlin.math.cos
import kotlin.math.sin

/**
 * DancingMicView: Ultra-compact, sleek mascot mic with subtle elastic wire physics.
 */
class DancingMicView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val wirePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#43A047")
        strokeWidth = 2.5f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val wireShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#25000000")
        strokeWidth = 4f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val micBitmap: Bitmap by lazy {
        val drawable = ContextCompat.getDrawable(context, R.drawable.ic_mic_mascot)
            ?: throw IllegalStateException("Resource ic_mic_mascot not found")
        val size = (22 * resources.displayMetrics.density).toInt()
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        bmp
    }

    private val wirePath = Path()
    private var animator: ValueAnimator? = null
    private var isAnimationRunning = false

    // Physics & oscillation parameters
    private var animationStartTime = 0L
    private var timeSeconds = 0f
    private var joltOffsetX = 0f
    private var joltOffsetY = 0f
    private var joltRotation = 0f
    private var targetJoltX = 0f
    private var targetJoltY = 0f
    private var targetJoltRot = 0f
    private var nextJoltTimeMs = 0L

    init {
        startDancing()
    }

    fun startDancing() {
        if (isAnimationRunning) return
        isAnimationRunning = true
        animationStartTime = SystemClock.uptimeMillis()
        nextJoltTimeMs = animationStartTime + 3000L

        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1000L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                val now = SystemClock.uptimeMillis()
                timeSeconds = (now - animationStartTime) / 1000f

                // Subtle organic impulse every 3-6s
                if (now > nextJoltTimeMs) {
                    val density = resources.displayMetrics.density
                    targetJoltX = ((-4..4).random() * density)
                    targetJoltY = ((-3..3).random() * density)
                    targetJoltRot = (-8..8).random().toFloat()
                    nextJoltTimeMs = now + (3000L..6000L).random()
                }

                // Smooth Lerp
                joltOffsetX += (targetJoltX - joltOffsetX) * 0.15f
                joltOffsetY += (targetJoltY - joltOffsetY) * 0.15f
                joltRotation += (targetJoltRot - joltRotation) * 0.15f

                targetJoltX *= 0.90f
                targetJoltY *= 0.90f
                targetJoltRot *= 0.90f

                invalidate()
            }
            start()
        }
    }

    fun pauseDancing() {
        isAnimationRunning = false
        animator?.cancel()
        animator = null
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val density = resources.displayMetrics.density
        val w = width.toFloat()
        val h = height.toFloat()

        // Anchor 1: Bottom center of wire entering side tab
        val anchorBottomX = w - (9f * density)
        val anchorBottomY = h - (2f * density)

        // Subtle compound harmonic motion
        val baseHarmonicX = sin(timeSeconds * 1.8f) * (3f * density) + sin(timeSeconds * 3.7f) * (1.2f * density)
        val baseHarmonicY = cos(timeSeconds * 2.4f) * (2f * density)
        val rotationDeg = sin(timeSeconds * 2.1f) * 6f + joltRotation

        // Anchor 2: Floating Mic Head Position
        val micCenterX = (w / 2f) + baseHarmonicX + joltOffsetX
        val micCenterY = (14f * density) + baseHarmonicY + joltOffsetY

        // Bézier Control Point
        val controlX = (anchorBottomX + micCenterX) / 2f - (sin(timeSeconds * 1.5f) * 4f * density)
        val controlY = (anchorBottomY + micCenterY) / 2f + (3f * density)

        // Draw Wire
        wirePath.reset()
        wirePath.moveTo(micCenterX, micCenterY + (7f * density))
        wirePath.quadTo(controlX, controlY, anchorBottomX, anchorBottomY)

        canvas.drawPath(wirePath, wireShadowPaint)
        canvas.drawPath(wirePath, wirePaint)

        // Draw Mic Head
        canvas.save()
        canvas.rotate(rotationDeg, micCenterX, micCenterY)
        val left = micCenterX - (micBitmap.width / 2f)
        val top = micCenterY - (micBitmap.height / 2f)
        canvas.drawBitmap(micBitmap, left, top, null)
        canvas.restore()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        pauseDancing()
    }
}
