package pl.wgabel.timetracker.features.timers

import android.content.Context
import android.view.Gravity
import android.widget.LinearLayout
import pl.wgabel.timetracker.R
import pl.wgabel.timetracker.design.AppUi

internal class TimerSessionHistoryRow(
    context: Context,
    ui: AppUi,
    private val startLabel: String,
    onEdit: () -> Unit
) : LinearLayout(context) {
    private val details = ui.label("", 13f, false, ui.color("muted"))

    init {
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = ui.dimensionPixels("buttonMinHeightDp")
        val padding = ui.densityPixels(8)
        setPadding(padding, padding, padding, padding)
        background = ui.shape(ui.color("timerSessionBackground"), ui.dimension("buttonRadiusDp"))
        isFocusable = true
        setOnClickListener { onEdit() }

        val heading = LinearLayout(context).apply {
            orientation = VERTICAL
            addView(ui.label(context.getString(R.string.current_timer_session), 15f, true))
            addView(details)
        }
        addView(ui.label("◷", 20f, true, ui.color("eventIcon")).apply {
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            setPadding(0, 0, padding, 0)
        })
        addView(heading, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1.3f))
        addView(ui.label(context.getString(R.string.edit_in_history), 13f, true, ui.color("eventIcon")).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
        }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        addView(ui.label("›", 22f, true, ui.color("eventIcon")).apply {
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            setPadding(padding, 0, 0, 0)
        })
    }

    fun showDuration(duration: String) {
        details.text = context.getString(R.string.timer_session_details, startLabel, duration)
    }
}
