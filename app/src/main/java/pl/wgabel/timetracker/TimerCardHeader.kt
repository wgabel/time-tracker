package pl.wgabel.timetracker

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/** Participant identity and running state displayed at the top of a timer card. */
internal class TimerCardHeader(
    context: Context,
    name: TextView,
    status: TextView,
    accentBackground: Drawable
) : LinearLayout(context) {
    private fun dp(value: Int) = (value * resources.displayMetrics.density + .5f).toInt()

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(View(context).apply { background = accentBackground },
            LayoutParams(dp(8), dp(32)).apply { rightMargin = dp(10) })
        name.letterSpacing = -0.01f
        addView(name, LayoutParams(0, -2, 1f))
        addView(status)
    }
}

/**
 * The daily total and running status labels share a single update entrypoint.
 * The activity supplies the measured duration; this class only formats and
 * refreshes its views without owning any timer scheduling or database access.
 */
internal class TimerCardClock(
    context: Context,
    val totalLabel: TextView,
    totalCaption: TextView,
    val statusLabel: TextView?,
    private val formatTotal: (Long) -> String,
    private val formatStatus: ((Long) -> String)?
) {
    val totalColumn = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        addView(totalLabel)
        addView(totalCaption)
    }

    fun update(now: Long) {
        totalLabel.text = formatTotal(now)
        if (formatStatus != null) statusLabel?.text = formatStatus.invoke(now)
    }
}
