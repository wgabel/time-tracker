package pl.wgabel.timetracker.tabs

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import java.text.DateFormat
import java.util.Date
import pl.wgabel.timetracker.design.AppUi
import pl.wgabel.timetracker.design.AppUi.ButtonRole

/** Builds the recent activity section; persistence is handled by its data source. */
internal class RecentActivityRenderer(
    private val context: Context,
    private val target: () -> LinearLayout,
    private val components: AppUi,
    private val participantColor: (Int) -> Int,
    private val participantName: (Int) -> String,
    private val dateFormat: () -> DateFormat,
    private val openHistory: () -> Unit
) {
    fun render(entries: List<RecentActivityEntry>) {
        val polish = context.resources.configuration.locales[0].language == "pl"
        val heading = components.label(if (polish) "Ostatnia aktywność" else "Recent activity", 21f, true, null).apply {
            letterSpacing = -0.02f
        }
        target().addView(View(context), LinearLayout.LayoutParams(1, components.dp(12)))
        target().addView(heading)
        target().addView(View(context), LinearLayout.LayoutParams(1, components.dp(12)))
        if (entries.isEmpty()) {
            target().addView(components.label(if (polish) "Brak aktywności" else "No activity yet", 14f, false, components.color("muted")))
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
                setPadding(components.dp(12), components.dp(10), components.dp(12), components.dp(10))
                if (!isEvent) background = components.shape(components.color("surface"), 12)
            }
            row.addView(View(context).apply {
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(participantColor(person))
                    cornerRadius = components.dp(3).toFloat()
                }
            }, LinearLayout.LayoutParams(components.dp(5), components.dp(40)).apply { rightMargin = components.dp(12) })
            val details = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            details.addView(components.label((if (isEvent) "⚑  " else "◷  ") + title, 15f, true, null))
            details.addView(components.label(participantName(person) + " · " + format.format(Date(whenAt)), 12f, false, components.color("muted")))
            row.addView(details, LinearLayout.LayoutParams(0, -2, 1f))
            target().addView(row)
            target().addView(View(context), LinearLayout.LayoutParams(1, components.dp(7)))
        }
        target().addView(components.styledAction(if (polish) "Pokaż wszystkie  →" else "Show all  →", ButtonRole.SECONDARY, openHistory))
    }
}
