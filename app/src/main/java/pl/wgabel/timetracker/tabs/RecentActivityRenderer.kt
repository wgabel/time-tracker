package pl.wgabel.timetracker.tabs

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import java.text.DateFormat
import java.util.Date

/** Builds the recent activity section; persistence is handled by its data source. */
class RecentActivityRenderer(
    private val context: Context,
    private val target: () -> LinearLayout,
    private val text: (String, Float, Boolean, Int?) -> TextView,
    private val mutedColor: () -> Int,
    private val surfaceBackground: () -> android.graphics.drawable.Drawable,
    private val participantColor: (Int) -> Int,
    private val participantName: (Int) -> String,
    private val dateFormat: () -> DateFormat,
    private val action: (String, () -> Unit) -> View,
    private val openHistory: () -> Unit
) {
    fun render(entries: List<RecentActivityEntry>) {
        val polish = context.resources.configuration.locales[0].language == "pl"
        val heading = text(if (polish) "Ostatnia aktywność" else "Recent activity", 21f, true, null).apply {
            letterSpacing = -0.02f
        }
        target().addView(View(context), LinearLayout.LayoutParams(1, dp(12)))
        target().addView(heading)
        target().addView(View(context), LinearLayout.LayoutParams(1, dp(12)))
        if (entries.isEmpty()) {
            target().addView(text(if (polish) "Brak aktywności" else "No activity yet", 14f, false, mutedColor()))
        }
        val format = dateFormat()
        entries.forEach { (whenAt, person, description) ->
            val isEvent = description.startsWith("event:")
            val title = if (isEvent) {
                val parts = description.removePrefix("event:").split(":", limit = 2)
                parts.getOrElse(1) { "" }.ifBlank { parts[0] }
            } else if (polish) "Sesja" else "Session"
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(10), dp(12), dp(10))
                if (!isEvent) background = surfaceBackground()
            }
            row.addView(View(context).apply {
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(participantColor(person))
                    cornerRadius = dp(3).toFloat()
                }
            }, LinearLayout.LayoutParams(dp(5), dp(40)).apply { rightMargin = dp(12) })
            val details = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            details.addView(text((if (isEvent) "⚑  " else "◷  ") + title, 15f, true, null))
            details.addView(text(participantName(person) + " · " + format.format(Date(whenAt)), 12f, false, mutedColor()))
            row.addView(details, LinearLayout.LayoutParams(0, -2, 1f))
            target().addView(row)
            target().addView(View(context), LinearLayout.LayoutParams(1, dp(7)))
        }
        target().addView(action(if (polish) "Pokaż wszystkie  →" else "Show all  →", openHistory))
    }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density + .5f).toInt()
}
