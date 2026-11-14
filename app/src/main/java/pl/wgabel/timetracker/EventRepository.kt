package pl.wgabel.timetracker

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import pl.wgabel.timetracker.model.SessionEvent

/** Single persistence entry point for session-bound events. */
internal class EventRepository(
    private val db: SQLiteDatabase,
    private val sessions: SessionRepository
) {
    fun save(project: Long, sessionId: Long, person: Int, eventId: Long?,
             timestamp: Long, type: String, note: String): Boolean {
        db.beginTransaction()
        try {
            if (!sessions.eventWithinSession(project, sessionId, person, timestamp)) return false
            val values = ContentValues().apply {
                put("project_id", project)
                put("session_id", sessionId)
                put("person", person)
                put("happened_at", timestamp)
                put("type", type)
                put("note", note)
            }
            val changed = if (eventId == null) db.insert("events", null, values) != -1L
            else db.update("events", values, "id=? AND project_id=? AND session_id=? AND person=?",
                arrayOf(eventId.toString(), project.toString(), sessionId.toString(), person.toString())) == 1
            db.setTransactionSuccessful()
            return changed
        } finally {
            db.endTransaction()
        }
    }

    fun sessionEvents(projectId: Long, sessionId: Long): List<SessionEvent> =
        queryEvents("e.project_id=? AND e.session_id=?", arrayOf(projectId.toString(), sessionId.toString()),
            order = "e.happened_at,e.id")

    fun eventsForDay(projectId: Long, start: Long, end: Long, withinSessionsOnly: Boolean = false): List<SessionEvent> =
        queryEvents("e.project_id=? AND e.happened_at>=? AND e.happened_at<?",
            arrayOf(projectId.toString(), start.toString(), end.toString()), withinSessionsOnly,
            if (withinSessionsOnly) "e.happened_at" else "e.happened_at DESC")

    private fun queryEvents(selection: String, arguments: Array<String>, withinSessionsOnly: Boolean = false,
                            order: String): List<SessionEvent> {
        val association = if (withinSessionsOnly) " INNER JOIN sessions s ON s.id=e.session_id " +
            "AND s.project_id=e.project_id AND s.person=e.person " else " "
        val bounds = if (withinSessionsOnly) " AND e.happened_at>=s.started_at " +
            "AND (s.ended_at IS NULL OR e.happened_at<=s.ended_at)" else ""
        return db.rawQuery("SELECT e.id,e.session_id,e.person,e.happened_at,e.type,e.note FROM events e" +
            association + "WHERE " + selection + bounds + " ORDER BY " + order, arguments).use { cursor ->
            buildList { while (cursor.moveToNext()) add(SessionEvent(cursor.getLong(0), cursor.getLong(1),
                cursor.getInt(2), cursor.getLong(3), cursor.getString(4), cursor.getString(5))) }
        }
    }

    fun deleteEvent(projectId: Long, eventId: Long): Boolean =
        db.delete("events", "id=? AND project_id=?", arrayOf(eventId.toString(), projectId.toString())) == 1
}
