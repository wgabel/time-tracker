package pl.wgabel.timetracker

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase

/**
 * Centralizes session interval checks and writes. All intervals use epoch milliseconds.
 * The caller must supply the current project and participant identifiers.
 */
internal class SessionRepository(private val db: SQLiteDatabase) {
    fun validInterval(project: Long, person: Int, start: Long, end: Long?,
                      excludeId: Long? = null, now: Long = System.currentTimeMillis()): Boolean {
        if (start > now || (end != null && (end <= start || end > now))) return false
        val until = end ?: now
        val conflict = db.rawQuery(
            "SELECT 1 FROM sessions WHERE project_id=? AND person=? AND id<>? " +
                "AND started_at<? AND (ended_at IS NULL OR ended_at>?) LIMIT 1",
            arrayOf(project.toString(), person.toString(), (excludeId ?: -1L).toString(), until.toString(), start.toString())
        ).use { it.moveToFirst() }
        if (conflict) return false
        if (excludeId != null) {
            val invalidEvents = db.rawQuery(
                "SELECT 1 FROM events WHERE project_id=? AND session_id=? AND (happened_at<? OR happened_at>?) LIMIT 1",
                arrayOf(project.toString(), excludeId.toString(), start.toString(), until.toString())
            ).use { it.moveToFirst() }
            if (invalidEvents) return false
        }
        return true
    }

    fun insert(project: Long, person: Int, start: Long, end: Long?): Boolean {
        if (!validInterval(project, person, start, end)) return false
        return db.insert("sessions", null, ContentValues().apply {
            put("project_id", project)
            put("person", person)
            put("started_at", start)
            if (end != null) put("ended_at", end)
        }) != -1L
    }

    fun update(project: Long, person: Int, id: Long, start: Long, end: Long?): Boolean {
        if (!validInterval(project, person, start, end, id)) return false
        return db.update("sessions", ContentValues().apply {
            put("started_at", start)
            if (end == null) putNull("ended_at") else put("ended_at", end)
        }, "id=? AND project_id=? AND person=?",
            arrayOf(id.toString(), project.toString(), person.toString())) == 1
    }

    fun eventWithinSession(project: Long, sessionId: Long, person: Int, time: Long,
                           now: Long = System.currentTimeMillis()): Boolean =
        db.rawQuery("SELECT 1 FROM sessions WHERE id=? AND project_id=? AND person=? " +
            "AND started_at<=? AND (ended_at IS NULL OR ended_at>=?) LIMIT 1",
            arrayOf(sessionId.toString(), project.toString(), person.toString(),
                time.toString(), time.toString())
        ).use { it.moveToFirst() } && time <= now
}
