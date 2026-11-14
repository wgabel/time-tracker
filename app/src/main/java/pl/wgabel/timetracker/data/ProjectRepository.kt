package pl.wgabel.timetracker.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase

/** Owns project persistence and project-list aggregates; does not build Android Views. */
internal class ProjectRepository(private val database: SQLiteDatabase) {
    fun name(projectId: Long, fallback: String): String =
        database.rawQuery("SELECT name FROM projects WHERE id=?", arrayOf(projectId.toString())).use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else fallback
        }

    fun create(name: String): Long =
        database.insert("projects", null, ContentValues().apply { put("name", name) })

    fun rename(projectId: Long, name: String): Boolean =
        database.update("projects", ContentValues().apply { put("name", name) },
            "id=?", arrayOf(projectId.toString())) == 1

    fun iconVariant(projectId: Long): Long =
        database.rawQuery("SELECT icon_variant FROM projects WHERE id=?", arrayOf(projectId.toString())).use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0) else 0L
        }

    fun regenerateIcon(projectId: Long) {
        database.execSQL("UPDATE projects SET icon_variant=icon_variant+1 WHERE id=? AND archived=0",
            arrayOf(projectId))
    }

    fun isArchived(projectId: Long): Boolean =
        database.rawQuery("SELECT archived FROM projects WHERE id=?", arrayOf(projectId.toString())).use { cursor ->
            cursor.moveToFirst() && cursor.getInt(0) == 1
        }

    fun activeTimerCount(projectId: Long): Int =
        database.rawQuery("SELECT COUNT(*) FROM sessions WHERE project_id=? AND ended_at IS NULL",
            arrayOf(projectId.toString())).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }

    /** Enforces the no-active-timers rule inside the same database transaction as archiving. */
    fun archiveIfNoActiveTimers(projectId: Long): Boolean {
        database.beginTransaction()
        try {
            if (activeTimerCount(projectId) > 0) return false
            val changed = database.update("projects", ContentValues().apply { put("archived", 1) },
                "id=? AND archived=0", arrayOf(projectId.toString())) == 1
            database.setTransactionSuccessful()
            return changed
        } finally {
            database.endTransaction()
        }
    }

    fun list(archived: Boolean): List<Pair<Long, String>> {
        val projects = mutableListOf<Pair<Long, String>>()
        database.rawQuery("SELECT id,name FROM projects WHERE archived=? ORDER BY id DESC",
            arrayOf(if (archived) "1" else "0")).use { cursor ->
            while (cursor.moveToNext()) projects.add(cursor.getLong(0) to cursor.getString(1))
        }
        return projects
    }

    fun participantAndActiveTimerCounts(projectId: Long): Pair<Int, Int> =
        database.rawQuery(
            "SELECT (SELECT COUNT(*) FROM participants WHERE project_id=?), (SELECT COUNT(*) FROM sessions WHERE project_id=? AND ended_at IS NULL)",
            arrayOf(projectId.toString(), projectId.toString())
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) to cursor.getInt(1) else 0 to 0
        }
}
