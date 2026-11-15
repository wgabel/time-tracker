package pl.wgabel.timetracker

import org.junit.Assert.assertEquals
import org.junit.Test
import pl.wgabel.timetracker.domain.RecordedTime
import java.time.LocalDate
import java.time.ZoneId

class RecordedTimeTest {
    @Test fun threeCountersAndDuplicateSessionsCountEachPersonOnce() {
        val data = RecordedTime(listOf(Triple(1, 0L, 10L), Triple(1, 5L, 15L),
            Triple(2, 5L, 15L), Triple(3, 5L, 15L)))
        val all = data.coverage(setOf(1, 2, 3))
        assertEquals(15L, all.elapsed)
        assertEquals(10L, all.simultaneous)
        assertEquals(5L, all.soloByPerson[1])
        assertEquals(35L, data.totals.values.sum())
        assertEquals(15L, data.coverage(setOf(1)).single)
        assertEquals(0L, data.coverage(emptySet()).elapsed)
    }

    @Test fun touchingCountersHaveNoOverlap() {
        val result = RecordedTime(listOf(Triple(1, 0L, 5L), Triple(2, 5L, 10L))).coverage(setOf(1, 2))
        assertEquals(0L, result.simultaneous)
        assertEquals(10L, result.single)
    }

    @Test fun allTimeBucketsSplitAtCalendarMonthAndRespectSelectedPeople() {
        fun midnight(date: String) = LocalDate.parse(date).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val start = midnight("2026-01-31")
        val boundary = midnight("2026-02-01")
        val end = midnight("2026-02-02")
        val data = RecordedTime(listOf(Triple(1, start, end), Triple(2, start, end)))
        val buckets = data.buckets(3, start, end, setOf(1))
        assertEquals(listOf(start, boundary), buckets.map { it.start })
        assertEquals(listOf(mapOf(1 to boundary - start), mapOf(1 to end - boundary)), buckets.map { it.durations })
    }
}
