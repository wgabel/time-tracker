package pl.wgabel.timetracker.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase

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
            if (hasSessions(projectId, participantId)) return false
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
}
