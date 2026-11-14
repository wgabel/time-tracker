package pl.wgabel.timetracker.tabs

import android.database.sqlite.SQLiteDatabase
import pl.wgabel.timetracker.ParticipantColors

/** Participant queries shared by timer and project screens. */
internal class ParticipantDataSource(private val db: SQLiteDatabase) {
    fun ids(projectId: Long): List<Int> {
        val ids = mutableListOf<Int>()
        db.rawQuery("SELECT id FROM participants WHERE project_id=? ORDER BY id",
            arrayOf(projectId.toString())).use { cursor ->
            while (cursor.moveToNext()) ids.add(cursor.getInt(0))
        }
        return ids
    }

    fun name(person: Int, fallback: String): String =
        db.rawQuery("SELECT name FROM participants WHERE id=?", arrayOf(person.toString())).use {
            if (it.moveToFirst()) it.getString(0) else fallback
        }

    fun suggestedColor(projectId: Long): Int {
        val count = db.rawQuery("SELECT COUNT(*) FROM participants WHERE project_id=?",
            arrayOf(projectId.toString())).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
        return ParticipantColors.suggested(count)
    }

    fun activeSessionId(projectId: Long, person: Int): Long? =
        db.rawQuery(
            "SELECT id FROM sessions WHERE project_id=? AND person=? AND ended_at IS NULL LIMIT 1",
            arrayOf(projectId.toString(), person.toString())
        ).use { if (it.moveToFirst()) it.getLong(0) else null }
}
