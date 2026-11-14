package pl.wgabel.timetracker.ui

import android.animation.ValueAnimator
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.LinearLayout
import pl.wgabel.timetracker.design.AppUi

/** Owns the animated background and its attachment lifecycle, independent of any screen. */
internal class ActiveTimerStyle(context: Context, private val components: AppUi) : ContextWrapper(context) {
    private val darkTheme get() = getSharedPreferences("preferences", MODE_PRIVATE).getString("app_theme", "light") == "dark"
    private val surface get() = components.color("surface")
    private fun densityPixels(value: Int) = components.densityPixels(value)
    private data class AnimatedBlock(val offset: Float, val y: Float, val speed: Float, val size: Int, val opacity: Int)

    private fun activeCardBackground(): Drawable = object : Drawable(), android.graphics.drawable.Animatable {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        // Use monotonic elapsed time so animator repeats cannot reset particle positions.
        // Generate particle properties once; never randomize inside draw().
        private val random = kotlin.random.Random(System.nanoTime())
        private val blocks = List(24) {
            AnimatedBlock(random.nextFloat(), random.nextFloat(),
                .65f + random.nextFloat() * .8f,
                4 + random.nextInt(5), 28 + random.nextInt(42))
        }
        private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 3600L
            repeatCount = ValueAnimator.INFINITE
            interpolator = android.view.animation.LinearInterpolator()
            addUpdateListener { invalidateSelf() }
        }
        override fun setVisible(visible: Boolean, restart: Boolean): Boolean {
            val changed = super.setVisible(visible, restart)
            if (!visible) animator.cancel()
            return changed
        }
        override fun start() {
            if (android.provider.Settings.Global.getFloat(contentResolver,
                    android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f &&
                !animator.isRunning) animator.start()
        }
        override fun stop() { animator.cancel() }
        override fun isRunning(): Boolean = animator.isRunning
        override fun draw(canvas: Canvas) {
            val b = bounds
            val w = b.width().toFloat()
            val h = b.height().toFloat()
            val radius = densityPixels(13).toFloat()
            val rect = RectF(b)
            p.style = Paint.Style.FILL
            p.shader = LinearGradient(0f, 0f, w, 0f,
                intArrayOf(surface, surface, if (darkTheme) Color.rgb(31, 76, 53) else Color.rgb(216, 246, 226)),
                floatArrayOf(0f, .65f, 1f), Shader.TileMode.CLAMP)
            canvas.drawRoundRect(rect, radius, radius, p)
            p.shader = null
            canvas.save()
            val clip = Path().apply { addRoundRect(rect, radius, radius, Path.Direction.CW) }
            canvas.clipPath(clip)
            for (block in blocks) {
                // Independent randomized starting positions and speeds.
                val elapsedCycles = android.os.SystemClock.uptimeMillis().toDouble() / 3600.0
                val progress = ((elapsedCycles * block.speed + block.offset) % 1.0).toFloat()
                val eased = progress * progress * (3f - 2f * progress)
                val side = densityPixels(block.size).toFloat()
                val x = -side + eased * (w + side * 2f)
                val y = block.y * (h - side).coerceAtLeast(0f)
                p.color = if (darkTheme) Color.argb(block.opacity, 114, 226, 154)
                    else Color.argb(block.opacity, 47, 166, 93)
                canvas.drawRoundRect(RectF(x, y, x + side, y + side),
                    densityPixels(1).toFloat(), densityPixels(1).toFloat(), p)
            }
            canvas.restore()
            p.style = Paint.Style.STROKE
            p.strokeWidth = resources.displayMetrics.density.coerceAtLeast(1f)
            p.color = if (darkTheme) Color.rgb(78, 159, 106) else Color.rgb(121, 199, 146)
            canvas.drawRoundRect(RectF(b.left + p.strokeWidth / 2, b.top + p.strokeWidth / 2,
                b.right - p.strokeWidth / 2, b.bottom - p.strokeWidth / 2), radius, radius, p)
            p.style = Paint.Style.FILL
        }
        override fun setAlpha(alpha: Int) { p.alpha = alpha }
        override fun setColorFilter(filter: android.graphics.ColorFilter?) { p.colorFilter = filter }
        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    fun markActive(view: LinearLayout) {
        val animated = activeCardBackground()
        view.background = animated
        view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                (animated as android.graphics.drawable.Animatable).start()
            }
            override fun onViewDetachedFromWindow(v: View) {
                (animated as android.graphics.drawable.Animatable).stop()
            }
        })
    }

}
