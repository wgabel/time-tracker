package pl.wgabel.timetracker.tabs

import android.content.Context
import android.view.Gravity
import android.widget.LinearLayout
import pl.wgabel.timetracker.design.AppUi
import pl.wgabel.timetracker.design.AppUi.ButtonRole

/** Native View composition for the Timers tab. Data and actions are injected. */
internal class TimersRenderer(
    private val context: Context,
    private val content: () -> LinearLayout,
    private val title: () -> String,
    private val emptyMessage: () -> String,
    private val ui: AppUi,
    private val addButtonDescription: () -> String,
    private val onAddParticipant: () -> Unit,
    private val renderCard: (Int, Long, Long, Long) -> Unit,
    private val renderRecentActivity: () -> Unit
) : ITabRenderer<TimersViewState> {
    override fun render(state: TimersViewState) {
        val target = content()
        val heading = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        heading.addView(ui.label(title(), 21f, true).apply {
            letterSpacing = -0.02f
        }, LinearLayout.LayoutParams(0, -2, 1f))
        if (!state.archived) {
            heading.addView(ui.styledAction("＋", ButtonRole.PRIMARY, onAddParticipant).apply {
                contentDescription = addButtonDescription().removePrefix("+").trim()
                textSize = 24f
                minHeight = ui.dp(40)
                minimumHeight = ui.dp(40)
                minWidth = ui.dp(44)
                minimumWidth = ui.dp(44)
                setPadding(0, 0, 0, 0)
            }, LinearLayout.LayoutParams(ui.dp(44), ui.dp(40)))
        }
        ui.gap(target, 12)
        target.addView(heading)
        ui.gap(target, 12)
        if (state.participantIds.isEmpty()) {
            target.addView(ui.label(emptyMessage(), 16f, false))
            ui.gap(target, 12)
        }
        state.participantIds.forEach { renderCard(it, state.dayStart, state.dayEnd, state.now) }
        renderRecentActivity()
    }
}
