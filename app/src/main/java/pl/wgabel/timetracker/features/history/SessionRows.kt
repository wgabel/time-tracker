package pl.wgabel.timetracker.features.history

import android.content.Context
import android.graphics.Color
import android.widget.LinearLayout
import java.util.Date
import pl.wgabel.timetracker.AppContainer
import pl.wgabel.timetracker.AppNavigation
import pl.wgabel.timetracker.R
import pl.wgabel.timetracker.ui.ActiveTimerStyle
import pl.wgabel.timetracker.ui.ScreenUi
import pl.wgabel.timetracker.ui.events.EventsScreen
import pl.wgabel.timetracker.ui.sessions.HistorySessionRow
import pl.wgabel.timetracker.ui.sessions.SessionDialogs

internal class SessionRows(
    context: Context, container: AppContainer, navigation: AppNavigation
) : ScreenUi(context, container) {
    override val navigator = navigation
    private val activeStyle = ActiveTimerStyle(context, appUi)
    private val events = EventsScreen(context, container, navigation)
    private val dialogs = SessionDialogs(context, container, navigation)
    fun renderSessions(start: Long, end: Long, participantFilter: Int?, target: LinearLayout, limit: Int = 500) {
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val sessions = sessionRepository.sessionsInRange(requireNotNull(projectId), start, end, limit, participantFilter)
        sessions.forEach { session ->
            val id = session.id
            val person = session.participantId
            val originalStart = session.startedAt
            val originalEnd = session.endedAt
            val from = maxOf(start, originalStart)
            val until = minOf(end, originalEnd ?: System.currentTimeMillis())
            val isHistory = true
            val running = if (originalEnd == null) "  • trwa" else ""
            val rangeLabel = label(clockFormat.format(Date(from)) + " – " +
                clockFormat.format(Date(until)) + running, 13f, false, muted)
            val elapsedLabel = label(formatDuration(until - from), 16f, true)
            val name = personName(person)
            val group = HistorySessionRow(
                this,
                historyMode = isHistory,
                rowBackground = shape(surface, 12),
                avatar = avatar(person),
                participantName = label(name, 16f, true),
                dateLabel = if (isHistory) null else label(
                    "  " + name + "  •  " + dateFormat.format(Date(originalStart)), 13f, true
                ),
                rangeLabel = rangeLabel,
                elapsedLabel = elapsedLabel,
                gap = { container, size -> gap(container, size) },
                densityPixels = { size -> densityPixels(size) }
            )
            if (originalEnd == null) activeStyle.markActive(group.mainRow)
            if (isHistory && !isArchived()) {
                val addEventAction = iconButton(
                    "bookmark-add",
                    if (darkTheme) Color.rgb(133, 224, 180) else Color.rgb(35, 124, 98)
                ) {
                    if (originalEnd == null) events.addEvent(person)
                    else events.addHistoricalEvent(id, person, originalStart, originalEnd)
                }.apply {
                    background = shape(
                        if (darkTheme) Color.rgb(34, 70, 59) else Color.rgb(229, 244, 237), 10)
                    contentDescription = "Dodaj wydarzenie"
                }
                val editAction = editIconButton {
                    if (originalEnd == null) dialogs.showStartTimePicker(person, id)
                    else dialogs.editSession(id, person, originalStart, originalEnd)
                }.apply { background = shape(subtle, 10) }
                val deleteAction = if (originalEnd == null) null else deleteIconButton {
                    dialogs.deleteSession(id)
                }.apply {
                    background = shape(
                        if (darkTheme) Color.rgb(76, 45, 49) else Color.rgb(252, 233, 233), 10)
                }
                group.attachActions(
                    trayBackground = shape(surface, 12),
                    addEvent = addEventAction,
                    edit = editAction,
                    delete = deleteAction,
                    arrow = label("›", 23f, false, muted),
                    description = name + ", " + formatDuration(until - from) +
                        ". Dotknij, aby pokazać lub ukryć akcje"
                )
            }
            if (isHistory) events.eventList(id, person, group)
            list.addView(group, LinearLayout.LayoutParams(-1, -2))
            gap(list, 7)
        }
        if (sessions.isEmpty()) list.addView(label(getString(R.string.no_sessions), 15f, false, muted))
        target.addView(list, LinearLayout.LayoutParams(-1, -2))
    }

}
