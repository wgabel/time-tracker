package pl.wgabel.timetracker.features.timers

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout

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
    sessionRow: TimerSessionHistoryRow?,
    controls: TimerCardControls?,
    private val spacing: (LinearLayout, Int) -> Unit
) : LinearLayout(context) {
    private fun densityPixels(value: Int) = (value * resources.displayMetrics.density + .5f).toInt()

    init {
        orientation = VERTICAL
        addView(header)
        spacing(this, 12)
        val timeRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        timeRow.addView(clock.totalColumn, LayoutParams(0, -2, 1f))
        timeRow.addView(ring, LayoutParams(densityPixels(80), densityPixels(80)))
        addView(timeRow)
        spacing(this, 10)
        addView(strip, LayoutParams(-1, -2))
        if (sessionRow != null) {
            spacing(this, 14)
            addView(sessionRow)
        }
        if (controls != null) {
            spacing(this, 12)
            addView(controls)
        }
    }

    fun updateClock(clock: TimerCardClock, ring: View, now: Long) {
        clock.update(now)
        ring.invalidate()
    }
}
