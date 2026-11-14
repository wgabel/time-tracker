package pl.wgabel.timetracker

import android.database.sqlite.SQLiteDatabase

/**
 * Reads session intervals for one participant and day. Database access and
 * clipping logic live here; drawing and formatting stay in the UI layer.
 */
internal class SessionDaySummary(private val db: SQLiteDatabase) {
    data class Interval(val start: Long, val end: Long?)

    fun intervals(projectId: Long, participantId: Int, start: Long, end: Long): List<Interval> {
        val result = mutableListOf<Interval>()
        db.rawQuery(
            "SELECT started_at,ended_at FROM sessions WHERE project_id=? AND person=? AND started_at<? AND (ended_at IS NULL OR ended_at>?) ORDER BY started_at",
            arrayOf(projectId.toString(), participantId.toString(), end.toString(), start.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result.add(Interval(cursor.getLong(0), if (cursor.isNull(1)) null else cursor.getLong(1)))
            }
        }
        return result
    }

    fun totalDuration(
        projectId: Long, participantId: Int, start: Long, end: Long,
        now: Long = System.currentTimeMillis()
    ): Long = duration(intervals(projectId, participantId, start, end), start, end, now)

    fun duration(intervals: List<Interval>, start: Long, end: Long, now: Long): Long =
        intervals.sumOf { interval ->
            val clipped = TimeMath.clippedInterval(interval.start, interval.end, start, end, now)
            if (clipped == null) 0L else clipped.second - clipped.first
        }

    /** A -1 end preserves the live-session sentinel used by DaySessionStrip. */
    fun stripSegments(intervals: List<Interval>, start: Long, end: Long): List<Pair<Long, Long>> =
        intervals.mapNotNull { interval ->
            val from = maxOf(start, interval.start)
            val to = interval.end?.let { minOf(end, it) } ?: -1L
            if (to == -1L || to > from) from to to else null
        }
}
