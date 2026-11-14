package pl.wgabel.timetracker

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Composes a participant's timer card from the existing styled subviews.
 * Data queries, navigation, callbacks and theme choices remain in MainActivity.
 */
internal class TimerCardView(
    context: Context,
    header: TimerCardHeader,
    clock: TimerCardClock,
    ring: View,
    strip: View,
    timelineLabels: List<TextView>,
    statusLabel: TextView?,
    controls: TimerCardControls?,
    private val spacing: (LinearLayout, Int) -> Unit
) : LinearLayout(context) {
    private fun dp(value: Int) = (value * resources.displayMetrics.density + .5f).toInt()

    init {
        orientation = VERTICAL
        addView(header)
        spacing(this, 12)
        val timeRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        timeRow.addView(clock.totalColumn, LayoutParams(0, -2, 1f))
        timeRow.addView(ring, LayoutParams(dp(80), dp(80)))
        addView(timeRow)
        spacing(this, 10)
        addView(strip, LayoutParams(-1, dp(9)))
        spacing(this, 5)
        val ticks = LinearLayout(context).apply { orientation = HORIZONTAL }
        timelineLabels.forEachIndexed { index, label ->
            label.gravity = when (index) {
                0 -> Gravity.START
                timelineLabels.lastIndex -> Gravity.END
                else -> Gravity.CENTER
            }
            ticks.addView(label, LayoutParams(0, -2, 1f))
        }
        addView(ticks)
        if (statusLabel != null && controls != null) {
            spacing(this, 14)
            addView(statusLabel)
            spacing(this, 12)
            addView(controls)
        }
    }

    fun updateClock(clock: TimerCardClock, ring: View, strip: View, now: Long) {
        clock.update(now)
        ring.invalidate()
        strip.invalidate()
    }
}
