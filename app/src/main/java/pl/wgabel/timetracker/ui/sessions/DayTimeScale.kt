package pl.wgabel.timetracker.ui.sessions

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.widget.LinearLayout
import android.widget.SeekBar
import pl.wgabel.timetracker.R
import pl.wgabel.timetracker.design.DayTimelineAxis
import pl.wgabel.timetracker.design.AppUi

/** Coarse day selection; programmatic updates keep every precise minute representable. */
internal class DayTimeScale(context: Context, private val components: AppUi,
                            private val onSelected: (Int) -> Unit) : LinearLayout(context) {
    private val axis = DayTimelineAxis(components)
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
        progressDrawable = axis.createTrack()
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
        addView(axis.createRuler(context, thumbRadius))
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
