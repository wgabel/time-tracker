package pl.wgabel.timetracker

import java.util.Calendar
import java.util.Locale

/**
 * Time calculations shared by timer cards, statistics and history.
 *
 * All timestamps are Unix epoch milliseconds. Calendar boundaries are based on
 * the device's local time zone, so DST transitions are handled by Calendar.
 */
internal object TimeMath {
    fun duration(milliseconds: Long): String {
        val seconds = milliseconds.coerceAtLeast(0L) / 1_000L
        return String.format(
            Locale.ROOT,
            "%02d:%02d:%02d",
            seconds / 3_600L,
            (seconds / 60L) % 60L,
            seconds % 60L
        )
    }

    fun dayStart(offset: Int, calendar: Calendar = Calendar.getInstance()): Long =
        calendar.apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, offset)
        }.timeInMillis

    /**
     * Returns the intersection of a session with the requested time window.
     * A running session is capped at [now], never extended into the future.
     */
    fun clippedInterval(
        startedAt: Long,
        endedAt: Long?,
        windowStart: Long,
        windowEnd: Long,
        now: Long
    ): Pair<Long, Long>? {
        val start = maxOf(windowStart, startedAt)
        val end = minOf(windowEnd, endedAt ?: now)
        return if (end > start) start to end else null
    }
}
