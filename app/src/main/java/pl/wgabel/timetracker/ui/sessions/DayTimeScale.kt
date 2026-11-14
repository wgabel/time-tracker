package pl.wgabel.timetracker.ui.sessions

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.LinearLayout
import android.widget.SeekBar
import pl.wgabel.timetracker.R
import pl.wgabel.timetracker.design.AppUi

/** Coarse day selection; programmatic updates keep every precise minute representable. */
internal class DayTimeScale(context: Context, private val components: AppUi,
                            private val onSelected: (Int) -> Unit) : LinearLayout(context) {
    private val scale = SeekBar(context).apply {
        id = R.id.time_scale
        max = 1_439
        keyProgressIncrement = 5
        contentDescription = context.getString(R.string.time_day_scale)
        setPadding(components.densityPixels(13), 0, components.densityPixels(13), 0)
        splitTrack = false
        progressTintList = null
        progressBackgroundTintList = null
        thumbTintList = null
        progressDrawable = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(components.color("blue"), components.color("timeScaleMiddle"), components.color("timeScaleEnd")))
            .apply {
                cornerRadius = components.densityPixels(5).toFloat()
                setSize(0, components.densityPixels(8))
            }
        thumb = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(components.color("ink"))
            setSize(components.densityPixels(26), components.densityPixels(26))
        }
        thumbOffset = components.densityPixels(13)
    }

    init {
        orientation = VERTICAL
        addView(scale, LayoutParams(-1, components.densityPixels(48)))
        addView(object : View(context) {
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = components.color("muted")
                strokeWidth = resources.displayMetrics.density
                textSize = 11 * resources.displayMetrics.scaledDensity
            }
            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                val left = scale.paddingLeft.toFloat()
                val trackWidth = width - scale.paddingLeft - scale.paddingRight
                val locale = resources.configuration.locales[0]
                val denseLabels = paint.measureText("00:00") * 5 > width
                for (hour in 0..24) {
                    val x = left + trackWidth * hour / 24f
                    val major = hour % 6 == 0
                    paint.alpha = if (major) 255 else 160
                    canvas.drawLine(x, 0f, x, components.densityPixels(if (major) 11 else 6).toFloat(), paint)
                    if (major) {
                        val text = if (hour == 0 || hour == 24 || !denseLabels)
                            String.format(locale, "%02d:00", hour) else String.format(locale, "%02d", hour)
                        paint.alpha = 255
                        paint.textAlign = when (hour) { 0 -> Paint.Align.LEFT; 24 -> Paint.Align.RIGHT; else -> Paint.Align.CENTER }
                        canvas.drawText(text, when (hour) { 0 -> 0f; 24 -> width.toFloat(); else -> x },
                            components.densityPixels(14) - paint.fontMetrics.ascent, paint)
                    }
                }
            }
        }, LayoutParams(-1, components.densityPixels(18) + (14 * resources.displayMetrics.scaledDensity).toInt()))
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
