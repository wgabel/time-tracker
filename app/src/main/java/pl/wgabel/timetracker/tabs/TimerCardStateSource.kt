package pl.wgabel.timetracker.tabs

import android.database.sqlite.SQLiteDatabase
import pl.wgabel.timetracker.SessionDaySummary
import pl.wgabel.timetracker.data.TimerCardDataSource
import pl.wgabel.timetracker.model.TimerCardData

/** Data needed by a timer card, loaded separately from Android View composition. */
internal class TimerCardStateSource(private val db: SQLiteDatabase) {
    fun load(projectId: Long, person: Int, start: Long, end: Long, fallbackName: String): TimerCardState {
        val data = TimerCardDataSource(db).load(projectId, person, fallbackName)
        val summary = SessionDaySummary(db)
        return TimerCardState(data, summary, summary.intervals(projectId, person, start, end))
    }
}

internal class TimerCardState(
    val data: TimerCardData,
    val summary: SessionDaySummary,
    val intervals: List<Pair<Long, Long>>
) {
    fun duration(start: Long, end: Long, now: Long): Long =
        summary.duration(intervals, start, end, now)
}
