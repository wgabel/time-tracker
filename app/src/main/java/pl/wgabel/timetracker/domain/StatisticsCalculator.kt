package pl.wgabel.timetracker.domain

import java.util.Calendar
import pl.wgabel.timetracker.model.Session
import pl.wgabel.timetracker.model.SessionEvent
import pl.wgabel.timetracker.model.TimeInterval
import pl.wgabel.timetracker.TimeMath

internal object StatisticsCalculator {
    fun totalDuration(intervals: List<TimeInterval>, start: Long, end: Long, now: Long): Long =
        intervals.sumOf { interval ->
            val clipped = TimeMath.clippedInterval(interval.start, interval.end, start, end, now)
            if (clipped == null) 0L else clipped.second - clipped.first
        }

    /** A -1 end preserves the live-session sentinel used by DaySessionStrip. */
    fun stripSegments(intervals: List<TimeInterval>, start: Long, end: Long): List<Pair<Long, Long>> =
        intervals.mapNotNull { interval ->
            val from = maxOf(start, interval.start)
            val to = interval.end?.let { minOf(end, it) } ?: -1L
            if (to == -1L || to > from) from to to else null
        }

    fun activityByDay(sessions: List<Session>, events: List<SessionEvent>, start: Long, end: Long,
                      now: Long): Map<Long, Set<Int>> {
        val activity = mutableMapOf<Long, MutableSet<Int>>()
        sessions.forEach { session ->
            val interval = TimeMath.clippedInterval(session.startedAt, session.endedAt, start, end, now) ?: return@forEach
            val date = Calendar.getInstance().apply {
                timeInMillis = interval.first
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            while (date.timeInMillis < interval.second) {
                activity.getOrPut(date.timeInMillis) { mutableSetOf() }.add(session.participantId)
                date.add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        events.filter { it.happenedAt >= start && it.happenedAt < end }.forEach { event ->
            val date = Calendar.getInstance().apply {
                timeInMillis = event.happenedAt
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            activity.getOrPut(date) { mutableSetOf() }.add(event.participantId)
        }
        return activity
    }

    fun periodRange(period: Int, offset: Int, earliestStart: Long?, now: Long,
                    calendar: Calendar = Calendar.getInstance()): Pair<Long, Long> {
        if (period == 3) return (earliestStart ?: now) to now
        calendar.timeInMillis = now
        calendar.set(Calendar.HOUR_OF_DAY, 0); calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0); calendar.set(Calendar.MILLISECOND, 0)
        when (period) {
            0 -> calendar.add(Calendar.DAY_OF_YEAR, offset)
            1 -> { calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek); calendar.add(Calendar.WEEK_OF_YEAR, offset) }
            2 -> { calendar.set(Calendar.DAY_OF_MONTH, 1); calendar.add(Calendar.MONTH, offset) }
        }
        val start = calendar.timeInMillis
        calendar.add(when (period) { 0 -> Calendar.DAY_OF_YEAR; 1 -> Calendar.WEEK_OF_YEAR; else -> Calendar.MONTH }, 1)
        return start to calendar.timeInMillis
    }

    data class TrendBucket(val start: Long, val end: Long, val milliseconds: Long)

    fun trendBuckets(period: Int, from: Long, to: Long, intervals: List<Triple<Int, Long, Long>>,
                     now: Long): List<TrendBucket> {
        val endLimit = minOf(to, now)
        fun bucket(start: Long, end: Long): TrendBucket = TrendBucket(start, end,
            intervals.sumOf { (_, sessionStart, sessionEnd) ->
                (minOf(end, endLimit, sessionEnd) - maxOf(start, sessionStart)).coerceAtLeast(0L)
            })
        if (period == 0) return (0..23).map { hour ->
            val calendar = Calendar.getInstance().apply { timeInMillis = from; set(Calendar.HOUR_OF_DAY, hour) }
            val start = calendar.timeInMillis
            calendar.add(Calendar.HOUR_OF_DAY, 1)
            bucket(start, calendar.timeInMillis)
        }
        val calendar = Calendar.getInstance().apply { timeInMillis = from }
        return buildList {
            while (calendar.timeInMillis < endLimit && size < 1_000) {
                val start = calendar.timeInMillis
                calendar.add(if (period == 1 || period == 2) Calendar.DAY_OF_YEAR else Calendar.MONTH, 1)
                add(bucket(start, calendar.timeInMillis))
            }
        }
    }
}
