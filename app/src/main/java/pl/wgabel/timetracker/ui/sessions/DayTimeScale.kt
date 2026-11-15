package pl.wgabel.timetracker.ui.sessions

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.View
import android.widget.LinearLayout
import android.widget.SeekBar
import pl.wgabel.timetracker.R
import pl.wgabel.timetracker.design.AppUi
import kotlin.math.roundToInt

/** Coarse day selection; programmatic updates keep every precise minute representable. */
internal class DayTimeScale(context: Context, private val components: AppUi,
                            private val onSelected: (Int) -> Unit) : LinearLayout(context) {
    private val style = components.dayTimeScaleStyle()
    private val thumbRadius = components.dimensionPixels("timeScaleThumbDiameterDp") / 2
    private val scale = SeekBar(context).apply {
        id = R.id.time_scale
        max = 1_439
        keyProgressIncrement = 5
        contentDescription = context.getString(R.string.time_day_scale)
        setPadding(thumbRadius, 0, thumbRadius, 0)
        splitTrack = false
        progressTintList = null
        progressBackgroundTintList = null
        thumbTintList = null
        progressDrawable = DaylightTrackDrawable(components, style.colors, style.positions)
        thumb = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(components.color("timeScaleThumbFill"))
            setStroke(components.dimensionPixels("timeScaleThumbStrokeDp"), components.color("timeScaleThumbStroke"))
            val diameter = components.dimensionPixels("timeScaleThumbDiameterDp")
            setSize(diameter, diameter)
        }
        thumbOffset = thumbRadius
    }

    init {
        orientation = VERTICAL
        addView(scale, LayoutParams(-1, components.dimensionPixels("buttonMinHeightDp")))
        addView(object : View(context) {
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = components.color("muted")
                strokeWidth = components.dimensionPixels("timeScaleTickStrokeDp").toFloat()
                textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,
                    components.dimension("timeScaleLabelTextSp").toFloat(), resources.displayMetrics)
            }
            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                val left = scale.paddingLeft.toFloat()
                val trackWidth = width - scale.paddingLeft - scale.paddingRight
                val locale = resources.configuration.locales[0]
                val denseLabels = paint.measureText("00:00") * (24 / style.labelIntervalHours + 1) > width
                for (hour in 0..24) {
                    val x = left + trackWidth * hour / 24f
                    val major = hour % style.labelIntervalHours == 0
                    paint.alpha = (255 * components.proportion(if (major) "timeScaleMajorTickOpacity"
                        else "timeScaleMinorTickOpacity")).roundToInt()
                    canvas.drawLine(x, 0f, x, components.dimensionPixels(if (major) "timeScaleMajorTickHeightDp"
                        else "timeScaleMinorTickHeightDp").toFloat(), paint)
                    if (major) {
                        val text = if (hour == 0 || hour == 24 || !denseLabels)
                            String.format(locale, "%02d:00", hour) else String.format(locale, "%02d", hour)
                        paint.alpha = (255 * components.proportion("timeScaleMajorTickOpacity")).roundToInt()
                        paint.textAlign = when (hour) { 0 -> Paint.Align.LEFT; 24 -> Paint.Align.RIGHT; else -> Paint.Align.CENTER }
                        canvas.drawText(text, when (hour) { 0 -> 0f; 24 -> width.toFloat(); else -> x },
                            components.dimensionPixels("timeScaleLabelTopDp") - paint.fontMetrics.ascent, paint)
                    }
                }
            }
        }, LayoutParams(-1, components.dimensionPixels("timeScaleLabelHeightPaddingDp") +
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,
                components.dimension("timeScaleLabelHeightTextSp").toFloat(), resources.displayMetrics).toInt()))
        scale.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(view: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) onSelected(progress)
            }
            override fun onStartTrackingTouch(view: SeekBar?) = Unit
            override fun onStopTrackingTouch(view: SeekBar?) = Unit
        })
    }

    fun setMinuteOfDay(minute: Int, description: String) {
        scale.progress = minute.coerceIn(0, 1_439)
        scale.contentDescription = context.getString(R.string.time_scale_value, description)
        if (android.os.Build.VERSION.SDK_INT >= 30) scale.stateDescription = description
    }
}

/** LinearGradient supports positioned daylight stops on every supported Android version (API 26+). */
private class DaylightTrackDrawable(components: AppUi, private val colors: IntArray,
                                    private val positions: FloatArray) : Drawable() {
    private val trackHeight = components.dimensionPixels("timeScaleTrackHeightDp")
    private val radius = components.dimensionPixels("timeScaleTrackRadiusDp").toFloat()
    private val rectangle = RectF()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = components.color("timeScaleTrackStroke")
        strokeWidth = components.dimensionPixels("timePickerStrokeDp").toFloat()
    }

    override fun onBoundsChange(bounds: Rect) {
        val inset = outline.strokeWidth / 2
        val halfHeight = minOf(trackHeight, bounds.height()) / 2f
        rectangle.set(bounds.left + inset, bounds.exactCenterY() - halfHeight + inset,
            bounds.right - inset, bounds.exactCenterY() + halfHeight - inset)
        fill.shader = if (rectangle.width() > 0) LinearGradient(rectangle.left, 0f, rectangle.right, 0f,
            colors, positions, Shader.TileMode.CLAMP) else null
    }

    override fun draw(canvas: Canvas) {
        canvas.drawRoundRect(rectangle, radius, radius, fill)
        canvas.drawRoundRect(rectangle, radius, radius, outline)
    }

    override fun setAlpha(alpha: Int) { fill.alpha = alpha; outline.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(filter: android.graphics.ColorFilter?) {
        fill.colorFilter = filter; outline.colorFilter = filter; invalidateSelf()
    }
    override fun getIntrinsicHeight(): Int = trackHeight
    @Deprecated("Deprecated in Java") override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
