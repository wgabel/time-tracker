package pl.wgabel.timetracker.tabs

import android.view.View
import android.widget.LinearLayout

/**
 * Explicit boundary between timer-card rendering and its host screen.
 * Keeps host-only operations separate from reusable AppUi components.
 */
internal class TimerCardHost(
    private val placeCard: (LinearLayout) -> Unit,
    private val animateActiveCard: (LinearLayout) -> Unit,
    private val createIcon: (String, Int, () -> Unit) -> View,
    private val resolveString: (Int) -> String,
    private val formatDuration: (Long) -> String,
    private val toggleTimer: (Int) -> Unit,
    private val createEvent: (Int) -> Unit,
    private val selectStartTime: (Int) -> Unit,
    private val subscribeTick: ((Long) -> Unit) -> Unit
) : TimerCardUi, TimerCardActions {
    override fun addCard(view: LinearLayout) = placeCard(view)
    override fun markActive(view: LinearLayout) = animateActiveCard(view)
    override fun iconButton(kind: String, color: Int, click: () -> Unit): View =
        createIcon(kind, color, click)
    override fun string(id: Int): String = resolveString(id)
    override fun duration(millis: Long): String = formatDuration(millis)
    override fun toggle(person: Int) = toggleTimer(person)
    override fun addEvent(person: Int) = createEvent(person)
    override fun quickStartPicker(person: Int) = selectStartTime(person)
    override fun registerUpdate(update: (Long) -> Unit) = subscribeTick(update)
}
