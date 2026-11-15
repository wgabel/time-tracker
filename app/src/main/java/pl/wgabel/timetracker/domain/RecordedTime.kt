package pl.wgabel.timetracker.domain

import java.util.Calendar

/** Non-overlapping, half-open spans per counter, clipped by the repository to one snapshot. */
internal class RecordedTime(rawIntervals: List<Triple<Int, Long, Long>>) {
    data class Span(val start: Long, val end: Long)
    data class Coverage(val single: Long, val simultaneous: Long, val soloByPerson: Map<Int, Long>,
                        val sharedByPair: Map<Pair<Int, Int>, Long>) {
        val elapsed get() = single + simultaneous
    }
    data class Bucket(val start: Long, val end: Long, val durations: MutableMap<Int, Long> = mutableMapOf())

    private val intervals = rawIntervals.groupBy { it.first }.mapValues { (_, entries) ->
        val merged = mutableListOf<Span>()
        for ((_, start, end) in entries.sortedBy { it.second }) {
            if (end <= start) continue
            val previous = merged.lastOrNull()
            if (previous != null && previous.end >= start) {
                merged[merged.lastIndex] = Span(previous.start, maxOf(previous.end, end))
            } else merged.add(Span(start, end))
        }
        merged
    }
    val totals = intervals.mapValues { (_, spans) -> spans.sumOf { it.end - it.start } }

    fun coverage(people: Set<Int>): Coverage {
        data class Boundary(val time: Long, val person: Int, val starting: Boolean)
        val boundaries = people.flatMap { person ->
            intervals[person].orEmpty().flatMap { span ->
                listOf(Boundary(span.start, person, true), Boundary(span.end, person, false))
            }
        }.sortedBy { it.time }
        val active = mutableMapOf<Int, Long>()
        val sharedByPair = mutableMapOf<Pair<Int, Int>, Long>()
        val solo = mutableMapOf<Int, Long>()
        var previous = boundaries.firstOrNull()?.time ?: 0L
        var simultaneous = 0L
        for (boundary in boundaries) {
            val duration = boundary.time - previous
            if (active.size == 1) {
                val person = active.keys.first()
                solo[person] = solo.getOrDefault(person, 0L) + duration
            } else if (active.size > 1) simultaneous += duration
            if (boundary.starting) {
                active[boundary.person] = boundary.time
            } else {
                val start = active.remove(boundary.person)!!
                for ((other, otherStart) in active) {
                    val overlap = boundary.time - maxOf(start, otherStart)
                    if (overlap <= 0) continue
                    val pair = minOf(boundary.person, other) to maxOf(boundary.person, other)
                    sharedByPair[pair] = sharedByPair.getOrDefault(pair, 0L) + overlap
                }
            }
            previous = boundary.time
        }
        return Coverage(solo.values.sum(), simultaneous, solo, sharedByPair)
    }

    fun buckets(period: Int, start: Long, end: Long, people: Set<Int>): List<Bucket> {
        val calendar = Calendar.getInstance().apply { timeInMillis = start }
        if (period == 3) {
            calendar.set(Calendar.DAY_OF_MONTH, 1)
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
        }
        val field = when (period) {
            0 -> Calendar.HOUR_OF_DAY
            3 -> Calendar.MONTH
            else -> Calendar.DAY_OF_YEAR
        }
        val result = mutableListOf<Bucket>()
        while (calendar.timeInMillis < end && start < end) {
            val from = maxOf(start, calendar.timeInMillis)
            calendar.add(field, 1)
            result.add(Bucket(from, minOf(end, calendar.timeInMillis)))
        }
        for (person in people) {
            var firstBucket = 0
            for (span in intervals[person].orEmpty()) {
                while (firstBucket < result.size && result[firstBucket].end <= span.start) firstBucket++
                var index = firstBucket
                while (index < result.size && result[index].start < span.end) {
                    val bucket = result[index++]
                    val duration = minOf(bucket.end, span.end) - maxOf(bucket.start, span.start)
                    if (duration > 0) bucket.durations[person] = bucket.durations.getOrDefault(person, 0L) + duration
                }
            }
        }
        return result
    }
}
