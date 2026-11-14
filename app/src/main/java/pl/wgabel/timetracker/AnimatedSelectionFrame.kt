package pl.wgabel.timetracker

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.view.animation.LinearInterpolator
import android.widget.Button
import android.widget.FrameLayout

/**
 * Selected button wrapper with a stationary button and an animated dashed outline.
 * The animation is tied to the view lifecycle and respects the system animation scale.
 */
internal class AnimatedSelectionFrame(
    context: Context,
    button: Button,
    color: Int,
    private val selected: Boolean
) : FrameLayout(context) {
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + .5f).toInt()
    private val dashLength = dp(7).toFloat()
    private val gapLength = dp(5).toFloat()
    private val cycle = dashLength + gapLength
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2).toFloat()
        this.color = color
    }
    private var dashPhase = 0f
    private var dashAnimator: ValueAnimator? = null

    init {
        setPadding(dp(5), dp(5), dp(5), dp(5))
        addView(button, LayoutParams(-1, -1))
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        if (!selected) return
        val inset = dp(1).toFloat() + outlinePaint.strokeWidth / 2f
        if (width <= 2 * inset || height <= 2 * inset) return
        outlinePaint.pathEffect = DashPathEffect(
            floatArrayOf(dashLength, gapLength), dashPhase)
        canvas.drawRoundRect(
            RectF(inset, inset, width - inset, height - inset),
            dp(14).toFloat(), dp(14).toFloat(), outlinePaint)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!selected) return
        val scale = ValueAnimator.getDurationScale()
        if (scale <= 0f) {
            dashPhase = 0f
            invalidate()
            return
        }
        dashAnimator?.cancel()
        dashAnimator = ValueAnimator.ofFloat(0f, -cycle).apply {
            duration = (1800L * scale).toLong().coerceAtLeast(100L)
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                dashPhase = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        dashAnimator?.cancel()
        dashAnimator = null
        super.onDetachedFromWindow()
    }
}
