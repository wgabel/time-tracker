package pl.wgabel.timetracker.tabs

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/** Native View composition for the Timers tab. Data and actions are injected. */
class TimersRenderer(
    private val context: Context,
    private val content: () -> LinearLayout,
    private val title: () -> String,
    private val emptyMessage: () -> String,
    private val addParticipantDescription: () -> String,
    private val text: (String, Float, Boolean) -> TextView,
    private val addButton: (() -> Unit) -> View,
    private val renderCard: (Int, Long, Long, Long) -> Unit,
    private val renderRecentActivity: () -> Unit,
    private val gap: (Int) -> Unit
) : ITabRenderer<TimersViewState> {
    override fun render(state: TimersViewState) {
        val target = content()
        val heading = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        heading.addView(text(title(), 21f, true).apply {
            letterSpacing = -0.02f
        }, LinearLayout.LayoutParams(0, -2, 1f))
        if (!state.archived) {
            heading.addView(addButton(), LinearLayout.LayoutParams(dp(44), dp(40)))
        }
        gap(12)
        target.addView(heading)
        gap(12)
        if (state.participantIds.isEmpty()) {
            target.addView(text(emptyMessage(), 16f, false))
            gap(12)
        }
        state.participantIds.forEach { renderCard(it, state.dayStart, state.dayEnd, state.now) }
        renderRecentActivity()
    }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density + .5f).toInt()
}
