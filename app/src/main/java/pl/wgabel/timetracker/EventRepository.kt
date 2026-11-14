package pl.wgabel.timetracker

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase

/** Single persistence entry point for session-bound events. */
internal class EventRepository(
    private val db: SQLiteDatabase,
    private val sessions: SessionRepository
) {
    fun save(project: Long, sessionId: Long, person: Int, eventId: Long?,
             timestamp: Long, type: String, note: String): Boolean {
        if (!sessions.eventWithinSession(project, sessionId, person, timestamp)) return false
        val values = ContentValues().apply {
            put("project_id", project)
            put("session_id", sessionId)
            put("person", person)
            put("happened_at", timestamp)
            put("type", type)
            put("note", note)
        }
        return if (eventId == null) db.insert("events", null, values) != -1L
        else db.update("events", values, "id=? AND project_id=? AND session_id=? AND person=?",
            arrayOf(eventId.toString(), project.toString(), sessionId.toString(), person.toString())) == 1
    }
}
