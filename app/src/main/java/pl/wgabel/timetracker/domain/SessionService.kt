package pl.wgabel.timetracker.domain

import pl.wgabel.timetracker.data.ParticipantRepository
import pl.wgabel.timetracker.data.ProjectRepository
import pl.wgabel.timetracker.data.SessionRepository

internal class SessionService(
    private val sessions: SessionRepository,
    private val projects: ProjectRepository,
    private val participants: ParticipantRepository,
    private val onSessionsChanged: () -> Unit
) {
    private fun canEdit(projectId: Long, person: Int): Boolean =
        !projects.isArchived(projectId) && participants.belongsToProject(projectId, person)

    fun createSession(projectId: Long, person: Int, start: Long, end: Long?): Boolean =
        notifyAfterChange(canEdit(projectId, person) && sessions.insert(projectId, person, start, end))

    fun updateSession(projectId: Long, person: Int, sessionId: Long, start: Long, end: Long?): Boolean =
        notifyAfterChange(canEdit(projectId, person) && sessions.update(projectId, person, sessionId, start, end))

    fun deleteCompletedSession(projectId: Long, sessionId: Long): Boolean =
        notifyAfterChange(!projects.isArchived(projectId) && sessions.deleteCompleted(projectId, sessionId))

    fun toggleTimer(projectId: Long, person: Int, now: Long): Boolean {
        if (!canEdit(projectId, person)) return false
        return notifyAfterChange(sessions.toggleTimer(projectId, person, now))
    }
    private fun notifyAfterChange(changed: Boolean): Boolean {
        if (changed) onSessionsChanged()
        return changed
    }
}
