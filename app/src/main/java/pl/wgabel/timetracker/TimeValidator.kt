package pl.wgabel.timetracker

/** Pure millisecond-based interval rules, independent of Android and SQLite. */
internal object TimeValidator {
    data class Interval(val start: Long, val end: Long?)

    fun valid(interval: Interval, now: Long): Boolean =
        interval.start <= now &&
            (interval.end == null || (interval.end > interval.start && interval.end <= now))

    /** Intervals are half-open: adjacent sessions do not overlap. */
    fun overlaps(first: Interval, second: Interval, now: Long): Boolean {
        val firstEnd = first.end ?: now
        val secondEnd = second.end ?: now
        return first.start < secondEnd && second.start < firstEnd
    }

    fun canSave(candidate: Interval, others: List<Interval>, events: List<Long>, now: Long): Boolean =
        valid(candidate, now) &&
            others.none { overlaps(candidate, it, now) } &&
            events.all { eventWithin(it, candidate, now) }

    fun eventWithin(timestamp: Long, session: Interval, now: Long): Boolean =
        timestamp >= session.start && timestamp <= (session.end ?: now) && timestamp <= now
}
