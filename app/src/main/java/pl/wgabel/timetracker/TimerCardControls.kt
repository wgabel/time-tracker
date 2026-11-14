package pl.wgabel.timetracker

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Layout of timer actions. The activity supplies styled controls and callbacks;
 * this view owns only their arrangement and interaction surface.
 */
internal class TimerCardControls(
    context: Context,
    running: Boolean,
    primaryButton: Button,
    secondaryIcon: View,
    eventLabel: TextView?,
    secondaryBackground: Drawable?,
    onEventClick: (() -> Unit)?
) : LinearLayout(context) {
    private fun dp(value: Int) = (value * resources.displayMetrics.density + .5f).toInt()

    init {
        orientation = HORIZONTAL
        if (running) {
            addView(primaryButton, LayoutParams(0, dp(54), 2f))
            val eventButton = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER
                background = secondaryBackground
                isClickable = true
                isFocusable = true
                contentDescription = "Dodaj wydarzenie"
                setOnClickListener { onEventClick?.invoke() }
            }
            secondaryIcon.apply {
                background = null
                isFocusable = false
            }
            eventButton.addView(secondaryIcon, LayoutParams(dp(30), dp(40)))
            requireNotNull(eventLabel) { "Running timer requires an event label" }
            eventButton.addView(eventLabel)
            addView(eventButton, LayoutParams(0, dp(54), 1f).apply { leftMargin = dp(8) })
        } else {
            addView(primaryButton, LayoutParams(0, dp(54), 1f))
            secondaryIcon.background = secondaryBackground
            addView(secondaryIcon, LayoutParams(dp(54), dp(54)).apply { leftMargin = dp(8) })
        }
    }
}
