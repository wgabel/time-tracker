package pl.wgabel.timetracker.features.timers

import pl.wgabel.timetracker.data.ParticipantRepository
import pl.wgabel.timetracker.data.ProjectRepository
import pl.wgabel.timetracker.data.SessionRepository
import pl.wgabel.timetracker.domain.SessionService
import pl.wgabel.timetracker.time.TimeMath

/** Owns the immutable screen snapshot and timer actions; no Views or Activity callbacks. */
internal class TimersController(
    private val projects: ProjectRepository,
    private val participants: ParticipantRepository,
    private val sessions: SessionRepository,
    private val sessionService: SessionService
) {
    var state: TimersViewState? = null
        private set

    fun loadState(projectId: Long, fallbackName: String, now: Long): TimersViewState {
        val start = TimeMath.dayStart(0)
        val end = TimeMath.dayStart(1)
        return TimersViewState(projectId, start, end, now,
            participants.participantIds(projectId).map { person ->
                TimersViewState.Card(
                    participants.timerCardSnapshot(projectId, person, fallbackName + person),
                    sessions.participantIntervals(projectId, person, start, end)
                )
            }, projects.isArchived(projectId), sessions.recentActivity(projectId)
        ).also { state = it }
    }

    fun toggleTimer(projectId: Long, participantId: Int, now: Long): Boolean =
        sessionService.toggleTimer(projectId, participantId, now)
}
