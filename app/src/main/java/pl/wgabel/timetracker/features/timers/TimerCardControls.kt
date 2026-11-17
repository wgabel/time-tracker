package pl.wgabel.timetracker.features.timers

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import pl.wgabel.timetracker.R
import pl.wgabel.timetracker.design.AppUi

internal class TimerCardControls(
    context: Context,
    private val ui: AppUi,
    primaryButton: Button
) : LinearLayout(context) {
    init {
        gravity = Gravity.START or Gravity.CENTER_VERTICAL
        addView(primaryButton, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
    }

    fun addEventButton(icon: View, onClick: () -> Unit) {
        val padding = ui.densityPixels(10)
        val button = LinearLayout(context).apply {
            gravity = Gravity.CENTER
            minimumHeight = ui.dimensionPixels("buttonMinHeightDp")
            setPadding(padding, 0, padding, 0)
            background = ui.shape(ui.color("subtleNeutral"), ui.dimension("buttonRadiusDp"))
            contentDescription = context.getString(R.string.timer_add_event)
            isFocusable = true
            setOnClickListener { onClick() }
        }
        icon.background = null
        icon.isClickable = false
        icon.isFocusable = false
        icon.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        button.addView(icon, LayoutParams(ui.densityPixels(30), ui.densityPixels(36)))
        button.addView(ui.label("EVENT", 13f, true))
        addView(button, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            marginStart = ui.densityPixels(8)
        })
    }
}
