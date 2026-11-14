package pl.wgabel.timetracker

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import pl.wgabel.timetracker.TimeValidator
import pl.wgabel.timetracker.tabs.RecentActivityEntry
import pl.wgabel.timetracker.model.Session
import pl.wgabel.timetracker.model.TimeInterval
import pl.wgabel.timetracker.TimeMath

/**
 * Centralizes session interval checks and writes. All intervals use epoch milliseconds.
 * The caller must supply the current project and participant identifiers.
 */
internal class SessionRepository(private val db: SQLiteDatabase) {
    private fun isValidInterval(project: Long, person: Int, start: Long, end: Long?,
                      excludeId: Long? = null, now: Long = System.currentTimeMillis()): Boolean {
        if (!TimeValidator.valid(TimeValidator.Interval(start, end), now)) return false
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
        db.beginTransaction()
        try {
            if (!isValidInterval(project, person, start, end)) return false
            val changed = db.insert("sessions", null, ContentValues().apply {
                put("project_id", project)
                put("person", person)
                put("started_at", start)
                if (end != null) put("ended_at", end)
            }) != -1L
            db.setTransactionSuccessful()
            return changed
        } finally {
            db.endTransaction()
        }
    }

    fun update(project: Long, person: Int, id: Long, start: Long, end: Long?): Boolean {
        db.beginTransaction()
        try {
            if (!isValidInterval(project, person, start, end, id)) return false
            val changed = db.update("sessions", ContentValues().apply {
                put("started_at", start)
                if (end == null) putNull("ended_at") else put("ended_at", end)
            }, "id=? AND project_id=? AND person=?",
                arrayOf(id.toString(), project.toString(), person.toString())) == 1
            db.setTransactionSuccessful()
            return changed
        } finally {
            db.endTransaction()
        }
    }

    /** Delete only completed sessions; delete their events in the same transaction. */
    fun deleteCompleted(project: Long, sessionId: Long): Boolean {
        db.beginTransaction()
        try {
            val eligible = db.rawQuery(
                "SELECT 1 FROM sessions WHERE id=? AND project_id=? AND ended_at IS NOT NULL",
                arrayOf(sessionId.toString(), project.toString())
            ).use { it.moveToFirst() }
            if (!eligible) return false
            db.delete("events", "session_id=? AND project_id=?",
                arrayOf(sessionId.toString(), project.toString()))
            val deleted = db.delete("sessions", "id=? AND project_id=? AND ended_at IS NOT NULL",
                arrayOf(sessionId.toString(), project.toString())) == 1
            if (deleted) db.setTransactionSuccessful()
            return deleted
        } finally {
            db.endTransaction()
        }
    }

    fun eventWithinSession(project: Long, sessionId: Long, person: Int, time: Long,
                           now: Long = System.currentTimeMillis()): Boolean =
        db.rawQuery("SELECT 1 FROM sessions WHERE id=? AND project_id=? AND person=? " +
            "AND started_at<=? AND (ended_at IS NULL OR ended_at>=?) LIMIT 1",
            arrayOf(sessionId.toString(), project.toString(), person.toString(),
                time.toString(), time.toString())
        ).use { it.moveToFirst() } && time <= now
    fun earliestSessionStart(projectId: Long): Long? =
        db.rawQuery("SELECT MIN(started_at) FROM sessions WHERE project_id=?",
            arrayOf(projectId.toString())).use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
        }

    fun clippedIntervals(projectId: Long, start: Long, end: Long, now: Long = System.currentTimeMillis()): List<Triple<Int, Long, Long>> {
        val intervals = mutableListOf<Triple<Int, Long, Long>>()
        db.rawQuery(
            "SELECT person,started_at,ended_at FROM sessions WHERE project_id=? AND started_at<? AND (ended_at IS NULL OR ended_at>?) ORDER BY started_at",
            arrayOf(projectId.toString(), end.toString(), start.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val clipped = TimeMath.clippedInterval(
                    cursor.getLong(1),
                    if (cursor.isNull(2)) null else cursor.getLong(2),
                    start, end, now
                )
                if (clipped != null) intervals.add(Triple(cursor.getInt(0), clipped.first, clipped.second))
            }
        }
        return intervals
    }
    fun activeStart(projectId: Long, person: Int): Long? =
        db.rawQuery(
            "SELECT started_at FROM sessions WHERE project_id=? AND person=? AND ended_at IS NULL LIMIT 1",
            arrayOf(projectId.toString(), person.toString())
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0) else null
        }

    fun toggleTimer(projectId: Long, person: Int, now: Long = System.currentTimeMillis()): Boolean {
        db.beginTransaction()
        try {
            val active = findActiveSession(projectId, person)
            val changed = if (active == null) {
                insert(projectId, person, now, null)
            } else {
                update(projectId, person, active.id, active.startedAt, now)
            }
            if (changed) db.setTransactionSuccessful()
            return changed
        } finally {
            db.endTransaction()
        }
    }

    fun findActiveSession(projectId: Long, participantId: Int): Session? =
        db.rawQuery("SELECT id,person,started_at FROM sessions WHERE project_id=? AND person=? AND ended_at IS NULL LIMIT 1",
            arrayOf(projectId.toString(), participantId.toString())).use { cursor ->
            if (cursor.moveToFirst()) Session(cursor.getLong(0), cursor.getInt(1), cursor.getLong(2), null) else null
        }

    fun hasAnyActiveSession(): Boolean =
        db.rawQuery("SELECT 1 FROM sessions WHERE ended_at IS NULL LIMIT 1", null)
            .use { it.moveToFirst() }

    fun findSession(projectId: Long, sessionId: Long, participantId: Int): Session? =
        db.rawQuery("SELECT id,person,started_at,ended_at FROM sessions WHERE id=? AND project_id=? AND person=?",
            arrayOf(sessionId.toString(), projectId.toString(), participantId.toString())).use { cursor ->
            if (cursor.moveToFirst()) Session(cursor.getLong(0), cursor.getInt(1), cursor.getLong(2),
                if (cursor.isNull(3)) null else cursor.getLong(3)) else null
        }

    fun sessionsInRange(projectId: Long, start: Long, end: Long, limit: Int = Int.MAX_VALUE,
                        participantFilter: Int? = null): List<Session> {
        val filter = if (participantFilter != null) " AND person=?" else ""
        val arguments = mutableListOf(projectId.toString(), end.toString(), start.toString())
        if (participantFilter != null) arguments.add(participantFilter.toString())
        return db.rawQuery("SELECT id,person,started_at,ended_at FROM sessions " +
            "WHERE project_id=? AND started_at<? AND (ended_at IS NULL OR ended_at>?)" +
            filter + " ORDER BY started_at DESC LIMIT ?",
            (arguments + limit.coerceAtLeast(0).toString()).toTypedArray()).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(Session(cursor.getLong(0), cursor.getInt(1), cursor.getLong(2),
                    if (cursor.isNull(3)) null else cursor.getLong(3)))
            }
        }
    }

    fun participantIntervals(projectId: Long, participantId: Int, start: Long, end: Long): List<TimeInterval> =
        sessionsInRange(projectId, start, end, participantFilter = participantId)
            .sortedBy { it.startedAt }.map { TimeInterval(it.startedAt, it.endedAt) }

    fun activeParticipantIds(projectId: Long): List<Int> =
        db.rawQuery("SELECT DISTINCT person FROM sessions WHERE project_id=? AND ended_at IS NULL ORDER BY person",
            arrayOf(projectId.toString())).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getInt(0)) }
        }

    data class ActiveTimer(val project: String, val person: String, val start: Long)

    fun activeTimers(): List<ActiveTimer> = db.rawQuery("""
        SELECT projects.name, participants.name, sessions.started_at
        FROM sessions JOIN projects ON projects.id=sessions.project_id
        JOIN participants ON participants.id=sessions.person AND participants.project_id=sessions.project_id
        WHERE sessions.ended_at IS NULL ORDER BY projects.name, participants.name
    """.trimIndent(), null).use { cursor ->
        buildList { while (cursor.moveToNext()) add(ActiveTimer(cursor.getString(0), cursor.getString(1), cursor.getLong(2))) }
    }

    data class ExportSession(val name: String, val start: Long, val end: Long?)

    fun exportSessions(projectId: Long): List<ExportSession> =
        db.rawQuery("SELECT p.name,s.started_at,s.ended_at FROM sessions s JOIN participants p ON p.id=s.person " +
            "WHERE s.project_id=? ORDER BY s.started_at", arrayOf(projectId.toString())).use { cursor ->
            buildList { while (cursor.moveToNext()) add(ExportSession(cursor.getString(0), cursor.getLong(1),
                if (cursor.isNull(2)) null else cursor.getLong(2))) }
        }
    fun recentActivity(projectId: Long, limit: Int = 6): List<RecentActivityEntry> {
        require(limit > 0)
        val entries = mutableListOf<RecentActivityEntry>()
        db.rawQuery(
            """SELECT occurred_at,person,description FROM (
                SELECT COALESCE(ended_at,started_at) AS occurred_at,person,'session' AS description
                FROM sessions WHERE project_id=?
                UNION ALL
                SELECT happened_at AS occurred_at,person,'event:' || type || ':' || note AS description
                FROM events WHERE project_id=?
            ) ORDER BY occurred_at DESC LIMIT ?""",
            arrayOf(projectId.toString(), projectId.toString(), limit.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                entries.add(RecentActivityEntry(cursor.getLong(0), cursor.getInt(1), cursor.getString(2)))
            }
        }
        return entries
    }
}
