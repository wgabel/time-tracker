package pl.wgabel.timetracker.design

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.view.View
import android.widget.LinearLayout
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

/** Shared daylight rail and hour ruler. History uses real elapsed positions on DST transition days. */
internal class DayTimelineAxis(private val components: AppUi, private val sessionRail: Boolean = false) {
    private val style = components.dayTimeScaleStyle(sessionRail)

    fun createTrack(): Drawable = DaylightTrackDrawable(components, style.colors, style.positions, sessionRail)

    fun createRuler(context: Context, inset: Int, dayRange: LongRange? = null): View {
        val ticks = if (dayRange == null) (0..24).map { it to it / 24f } else {
            val zone = ZoneId.systemDefault()
            val date = Instant.ofEpochMilli(dayRange.first).atZone(zone).toLocalDate()
            (0..23).flatMap { hour ->
                val local = date.atTime(hour, 0)
                zone.rules.getValidOffsets(local).map { offset ->
                    val elapsed = local.toInstant(offset).toEpochMilli() - dayRange.first
                    hour to elapsed.toDouble().div(dayRange.last - dayRange.first).toFloat()
                }
            } + (24 to 1f)
        }
        return object : View(context) {
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = components.color("timeScaleRuler")
                strokeWidth = components.dimensionPixels("timeScaleTickStrokeDp").toFloat()
                textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,
                    components.dimension("timeScaleLabelTextSp").toFloat(), resources.displayMetrics)
            }
            private val majorHeight = components.dimensionPixels("timeScaleMajorTickHeightDp").toFloat()
            private val minorHeight = components.dimensionPixels("timeScaleMinorTickHeightDp").toFloat()
            private val majorAlpha = (255 * components.proportion("timeScaleMajorTickOpacity")).roundToInt()
            private val minorAlpha = (255 * components.proportion("timeScaleMinorTickOpacity")).roundToInt()
            private val baseline = components.dimensionPixels("timeScaleLabelTopDp") - paint.ascent()
            private val labels = (0..24).map { String.format(resources.configuration.locales[0], "%02d:00", it) }

            init {
                importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
                layoutParams = LinearLayout.LayoutParams(-1,
                    components.dimensionPixels("timeScaleLabelHeightPaddingDp") +
                    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,
                        components.dimension("timeScaleLabelHeightTextSp").toFloat(), resources.displayMetrics).toInt())
            }

            override fun onDraw(canvas: Canvas) {
                val available = (width - 2 * inset).coerceAtLeast(1)
                val labelWidth = paint.measureText(labels.first())
                var interval = style.labelIntervalHours
                while (interval < 24 && available * interval / 24f < labelWidth * 1.5f) interval *= 2
                for ((hour, fraction) in ticks) {
                    val x = inset + available * fraction
                    val major = hour % style.labelIntervalHours == 0
                    paint.alpha = if (major) majorAlpha else minorAlpha
                    canvas.drawLine(x, 0f, x, if (major) majorHeight else minorHeight, paint)
                    if (hour == 24 || hour % interval == 0) {
                        paint.textAlign = Paint.Align.CENTER
                        val half = paint.measureText(labels[hour]) / 2f
                        val center = x.coerceIn(half, maxOf(half, width - half))
                        canvas.drawText(labels[hour], center, baseline, paint)
                    }
                }
            }
        }
    }
}

/** LinearGradient supports positioned daylight stops on every supported Android version (API 26+). */
private class DaylightTrackDrawable(components: AppUi, private val colors: IntArray,
                                    private val positions: FloatArray, private val sessionRail: Boolean) : Drawable() {
    private val trackHeight = components.dimensionPixels("timeScaleTrackHeightDp")
    private val radius = components.dimensionPixels("timeScaleTrackRadiusDp").toFloat()
    private val rectangle = RectF()
    private val starStyle = if (sessionRail) null else components.nightStarStyle()
    private val density = components.densityPixels(100) / 100f
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = components.color("timeScaleTrackStroke")
        strokeWidth = components.dimensionPixels("timePickerStrokeDp").toFloat()
    }

    override fun onBoundsChange(bounds: Rect) {
        val inset = if (sessionRail) 0f else outline.strokeWidth / 2
        val halfHeight = minOf(trackHeight, bounds.height()) / 2f
        rectangle.set(bounds.left + inset, bounds.exactCenterY() - halfHeight + inset,
            bounds.right - inset, bounds.exactCenterY() + halfHeight - inset)
        fill.shader = if (rectangle.width() > 0) LinearGradient(rectangle.left, 0f, rectangle.right, 0f,
            colors, positions, Shader.TileMode.CLAMP) else null
    }

    override fun draw(canvas: Canvas) {
        canvas.drawRoundRect(rectangle, radius, radius, fill)
        if (!sessionRail) {
            drawNightStars(canvas)
            canvas.drawRoundRect(rectangle, radius, radius, outline)
        }
    }

    private fun drawNightStars(canvas: Canvas) {
        if (rectangle.width() <= 0f || rectangle.height() <= 0f) return
        val stars = starStyle ?: return
        val spacing = stars.spacingDp * density
        val minimumSize = stars.minimumSizeDp * density
        val sizeVariation = (stars.maximumSizeDp - stars.minimumSizeDp) * density
        val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = stars.color }
        canvas.save()
        canvas.clipRect(rectangle)
        for ((start, end) in stars.ranges) {
            val startX = rectangle.left + start * rectangle.width()
            val endX = rectangle.left + end * rectangle.width()
            var index = 0
            var x = startX + spacing / 2f
            while (x < endX) {
                val variation = ((index * 37 + 11) % 7) / 6f
                val diameter = minimumSize + sizeVariation * variation
                val centerY = rectangle.centerY() + ((index * 13 % 5) - 2) * rectangle.height() / 10f
                val half = diameter / 2f
                val opacity = stars.minimumOpacity +
                    (stars.maximumOpacity - stars.minimumOpacity) * ((index * 17 % 7) / 6f)
                starPaint.alpha = (opacity * 255).toInt()
                val shape = android.graphics.Path().apply {
                    moveTo(x, centerY - half)
                    quadTo(x + diameter * .12f, centerY - diameter * .12f, x + half, centerY)
                    quadTo(x + diameter * .12f, centerY + diameter * .12f, x, centerY + half)
                    quadTo(x - diameter * .12f, centerY + diameter * .12f, x - half, centerY)
                    quadTo(x - diameter * .12f, centerY - diameter * .12f, x, centerY - half)
                    close()
                }
                canvas.drawPath(shape, starPaint)
                index++
                x += spacing
            }
        }
        canvas.restore()
    }

    override fun setAlpha(alpha: Int) { fill.alpha = alpha; outline.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(filter: android.graphics.ColorFilter?) {
        fill.colorFilter = filter; outline.colorFilter = filter; invalidateSelf()
    }
    override fun getIntrinsicHeight(): Int = trackHeight
    @Deprecated("Deprecated in Java") override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
