package pl.wgabel.timetracker

import android.app.Activity
import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup

internal interface SplashScreenService {
    fun show(root: ViewGroup)
}

/** Replace this implementation at the Activity composition point to switch splash designs. */
internal class AnimatedSplashScreen(
    private val activity: Activity,
    private val darkTheme: () -> Boolean,
    private val blue: () -> Int,
    private val muted: () -> Int,
    private val ink: () -> Int
) : SplashScreenService {
    private fun dp(n: Int) = (n * activity.resources.displayMetrics.density + .5f).toInt()
    override fun show(root: ViewGroup) {
        val overlay = object : View(activity) {
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            var progress = 0f
            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                canvas.drawColor(if (darkTheme()) Color.rgb(21, 26, 35) else Color.rgb(246, 247, 251))
                val cx = width / 2f
                val cy = height / 2f - dp(22)
                val radius = dp(68).toFloat()
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = dp(5).toFloat()
                paint.strokeCap = Paint.Cap.ROUND
                paint.color = blue()
                canvas.drawCircle(cx, cy, radius, paint)
                paint.strokeWidth = dp(2).toFloat()
                paint.color = muted()
                for (tick in 0 until 12) {
                    val angle = tick * Math.PI / 6 - Math.PI / 2
                    val inner = radius - dp(if (tick % 3 == 0) 16 else 11)
                    val outer = radius - dp(6)
                    canvas.drawLine(
                        cx + (kotlin.math.cos(angle) * inner).toFloat(),
                        cy + (kotlin.math.sin(angle) * inner).toFloat(),
                        cx + (kotlin.math.cos(angle) * outer).toFloat(),
                        cy + (kotlin.math.sin(angle) * outer).toFloat(), paint
                    )
                }
                // Minute hand makes one complete revolution during the splash.
                val minuteAngle = progress * Math.PI * 2 - Math.PI / 2
                paint.color = blue()
                paint.strokeWidth = dp(4).toFloat()
                canvas.drawLine(cx, cy,
                    cx + (kotlin.math.cos(minuteAngle) * radius * .72).toFloat(),
                    cy + (kotlin.math.sin(minuteAngle) * radius * .72).toFloat(), paint)
                val hourAngle = progress * Math.PI / 3 - Math.PI / 2
                paint.color = ink()
                paint.strokeWidth = dp(6).toFloat()
                canvas.drawLine(cx, cy,
                    cx + (kotlin.math.cos(hourAngle) * radius * .45).toFloat(),
                    cy + (kotlin.math.sin(hourAngle) * radius * .45).toFloat(), paint)
                paint.style = Paint.Style.FILL
                paint.color = blue()
                canvas.drawCircle(cx, cy, dp(6).toFloat(), paint)
                paint.typeface = Typeface.DEFAULT_BOLD
                paint.textAlign = Paint.Align.CENTER
                paint.textSize = dp(28).toFloat()
                paint.color = ink()
                canvas.drawText("timetrkr", cx, cy + radius + dp(58), paint)
            }
        }
        root.addView(overlay, ViewGroup.LayoutParams(-1, -1))
        val clock = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1650L
            interpolator = android.view.animation.LinearInterpolator()
            addUpdateListener {
                overlay.progress = it.animatedValue as Float
                overlay.invalidate()
            }
        }
        clock.start()
        overlay.postDelayed({
            if (overlay.parent != null) {
                overlay.animate().alpha(0f).setDuration(350L).withEndAction {
                    (overlay.parent as? ViewGroup)?.removeView(overlay)
                }.start()
            }
        }, 1650L)
    }

 }
