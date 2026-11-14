package pl.wgabel.timetracker

import android.database.sqlite.SQLiteDatabase

/**
 * Database operations for starting/stopping timers. The UI and notification
 * lifecycle stay in MainActivity; this class only owns session persistence.
 */
internal class TimerSessionController(private val db: SQLiteDatabase) {
    fun activeStart(projectId: Long, person: Int): Long? =
        db.rawQuery(
            "SELECT started_at FROM sessions WHERE project_id=? AND person=? AND ended_at IS NULL LIMIT 1",
            arrayOf(projectId.toString(), person.toString())
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0) else null
        }

    fun toggle(projectId: Long, person: Int, now: Long = System.currentTimeMillis()) {
        db.beginTransaction()
        try {
            if (activeStart(projectId, person) == null) {
                db.execSQL(
                    "INSERT INTO sessions(project_id,person,started_at) VALUES (?,?,?)",
                    arrayOf(projectId, person, now)
                )
            } else {
                db.execSQL(
                    "UPDATE sessions SET ended_at=? WHERE project_id=? AND person=? AND ended_at IS NULL",
                    arrayOf(now, projectId, person)
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun hasAnyActiveSession(): Boolean =
        db.rawQuery("SELECT 1 FROM sessions WHERE ended_at IS NULL LIMIT 1", null)
            .use { it.moveToFirst() }
}
