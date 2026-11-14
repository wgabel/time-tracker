package pl.wgabel.timetracker.domain

import pl.wgabel.timetracker.EventRepository
import pl.wgabel.timetracker.data.ProjectRepository

internal class EventService(private val events: EventRepository, private val projects: ProjectRepository) {
    fun saveEvent(projectId: Long, sessionId: Long, person: Int, eventId: Long?,
                  timestamp: Long, type: String, note: String): Boolean =
        !projects.isArchived(projectId) && (type != "Lek" || note.isNotBlank()) &&
            events.save(projectId, sessionId, person, eventId, timestamp, type, note)

    fun deleteEvent(projectId: Long, eventId: Long): Boolean =
        !projects.isArchived(projectId) && events.deleteEvent(projectId, eventId)
}
