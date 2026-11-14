package pl.wgabel.timetracker.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import pl.wgabel.timetracker.design.ParticipantColors
import pl.wgabel.timetracker.model.TimerCardData

/** Owns participant edits and deletion safeguards. */
internal class ParticipantRepository(private val database: SQLiteDatabase) {
    fun rename(projectId: Long, participantId: Int, name: String): Boolean =
        database.update("participants", ContentValues().apply { put("name", name) },
            "id=? AND project_id=?", arrayOf(participantId.toString(), projectId.toString())) == 1

    fun hasSessions(projectId: Long, participantId: Int): Boolean =
        database.rawQuery(
            "SELECT 1 FROM sessions WHERE project_id=? AND person=? LIMIT 1",
            arrayOf(projectId.toString(), participantId.toString())
        ).use { it.moveToFirst() }

    /** Prevents deleting a participant with sessions, including a concurrent new session. */
    fun deleteIfNoSessions(projectId: Long, participantId: Int): Boolean {
        database.beginTransaction()
        try {
            if (hasSessions(projectId, participantId) || database.rawQuery(
                "SELECT 1 FROM events WHERE project_id=? AND person=? LIMIT 1",
                arrayOf(projectId.toString(), participantId.toString())).use { it.moveToFirst() }) return false
            val deleted = database.delete("participants", "id=? AND project_id=?",
                arrayOf(participantId.toString(), projectId.toString())) == 1
            database.setTransactionSuccessful()
            return deleted
        } finally {
            database.endTransaction()
        }
    }

    fun color(projectId: Long, participantId: Int): Int? =
        database.rawQuery("SELECT color FROM participants WHERE id=? AND project_id=?",
            arrayOf(participantId.toString(), projectId.toString())).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else null
        }

    fun updateColor(projectId: Long, participantId: Int, color: Int): Boolean =
        database.update("participants", ContentValues().apply { put("color", color) },
            "id=? AND project_id=?", arrayOf(participantId.toString(), projectId.toString())) == 1
    fun participantIds(projectId: Long): List<Int> {
        val ids = mutableListOf<Int>()
        database.rawQuery("SELECT id FROM participants WHERE project_id=? ORDER BY id",
            arrayOf(projectId.toString())).use { cursor ->
            while (cursor.moveToNext()) ids.add(cursor.getInt(0))
        }
        return ids
    }

    fun name(projectId: Long, person: Int, fallback: String): String =
        database.rawQuery("SELECT name FROM participants WHERE id=? AND project_id=?", arrayOf(person.toString(), projectId.toString())).use {
            if (it.moveToFirst()) it.getString(0) else fallback
        }

    fun suggestedColor(projectId: Long): Int {
        val count = database.rawQuery("SELECT COUNT(*) FROM participants WHERE project_id=?",
            arrayOf(projectId.toString())).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
        return ParticipantColors.suggested(count)
    }

    fun timerCardSnapshot(projectId: Long, participantId: Int, fallbackName: String): TimerCardData =
        database.rawQuery(
            """SELECT p.name, p.color,
                      (SELECT s.started_at FROM sessions s
                       WHERE s.project_id=p.project_id AND s.person=p.id AND s.ended_at IS NULL
                       LIMIT 1)
               FROM participants p
               WHERE p.id=? AND p.project_id=?""",
            arrayOf(participantId.toString(), projectId.toString())
        ).use { cursor ->
            if (!cursor.moveToFirst()) {
                TimerCardData(participantId, fallbackName, ParticipantColors.legacyColor(participantId), null)
            } else {
                val storedColor = cursor.getInt(1)
                TimerCardData(
                    participantId = participantId,
                    participantName = cursor.getString(0),
                    participantColor = if (storedColor != 0) storedColor else ParticipantColors.legacyColor(participantId),
                    activeStartedAt = if (cursor.isNull(2)) null else cursor.getLong(2)
                )
            }
        }

    fun createParticipant(projectId: Long, name: String, color: Int): Boolean =
        database.insert("participants", null, ContentValues().apply {
            put("project_id", projectId); put("name", name); put("color", color)
        }) != -1L

    fun belongsToProject(projectId: Long, participantId: Int): Boolean =
        database.rawQuery("SELECT 1 FROM participants WHERE id=? AND project_id=?",
            arrayOf(participantId.toString(), projectId.toString())).use { it.moveToFirst() }
}
