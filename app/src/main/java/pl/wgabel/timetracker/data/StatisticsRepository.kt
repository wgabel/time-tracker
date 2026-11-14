package pl.wgabel.timetracker.data

import android.database.sqlite.SQLiteDatabase
import pl.wgabel.timetracker.TimeMath

/** Read-only session queries shared by statistics and timeline presentations. */
internal class StatisticsRepository(private val database: SQLiteDatabase) {
    fun earliestSessionStart(projectId: Long): Long? =
        database.rawQuery("SELECT MIN(started_at) FROM sessions WHERE project_id=?",
            arrayOf(projectId.toString())).use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
        }

    fun clippedIntervals(projectId: Long, start: Long, end: Long, now: Long = System.currentTimeMillis()): List<Triple<Int, Long, Long>> {
        val intervals = mutableListOf<Triple<Int, Long, Long>>()
        database.rawQuery(
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
}
