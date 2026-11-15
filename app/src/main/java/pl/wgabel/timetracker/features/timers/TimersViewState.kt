package pl.wgabel.timetracker.features.timers

import pl.wgabel.timetracker.model.RecentActivityEntry
import pl.wgabel.timetracker.model.Session
import pl.wgabel.timetracker.model.SessionEvent
import pl.wgabel.timetracker.model.TimerCardData

internal data class TimersViewState(
    val projectId: Long,
    val dayStart: Long,
    val dayEnd: Long,
    val now: Long,
    val cards: List<Card>,
    val archived: Boolean,
    val recentActivity: List<RecentActivityEntry>
) {
    data class Card(val participant: TimerCardData, val sessions: List<Session>, val events: List<SessionEvent>)
}
