package pl.wgabel.timetracker

import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test
import pl.wgabel.timetracker.domain.StatisticsCalculator
import pl.wgabel.timetracker.model.Session
import pl.wgabel.timetracker.model.SessionEvent
import pl.wgabel.timetracker.model.TimeInterval
import pl.wgabel.timetracker.time.TimeMath

class StatisticsCalculatorTest {
    @Test fun runningDurationComesFromPersistedStartAndCurrentTime() {
        val intervals = listOf(TimeInterval(100, null), TimeInterval(10, 50))
        assertEquals(140L, StatisticsCalculator.totalDuration(intervals, 0, 1_000, 200))
        assertEquals(240L, StatisticsCalculator.totalDuration(intervals, 0, 1_000, 300))
    }

    @Test fun simultaneousParticipantsContributeTheirOwnTime() {
        assertEquals(200L, StatisticsCalculator.totalDuration(
            listOf(TimeInterval(10, 110), TimeInterval(10, 110)), 0, 200, 200))
    }

    @Test fun dayWindowClipsBothSessionBoundariesAndDoesNotCountFutureTime() {
        assertEquals(35L, StatisticsCalculator.totalDuration(
            listOf(TimeInterval(0, 125), TimeInterval(140, null), TimeInterval(300, null)), 100, 200, 150))
    }

    @Test fun completedIntervalsStayFixedWhileRunningStripUsesItsLiveSentinel() {
        assertEquals(listOf(100L to 150L, 160L to -1L), StatisticsCalculator.stripSegments(
            listOf(TimeInterval(50, 150), TimeInterval(160, null), TimeInterval(10, 20)), 100, 200))
    }

    @Test fun daylightSavingDaysHaveCalendarLengthAndKeepActivityOnCorrectDates() {
        val previous = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Warsaw"))
        try {
            listOf(Triple(2026, Calendar.MARCH, 29) to 23L, Triple(2026, Calendar.OCTOBER, 25) to 25L)
                .forEach { (date, hours) ->
                    val calendar = Calendar.getInstance().apply { clear(); set(date.first, date.second, date.third, 12, 0) }
                    val start = TimeMath.dayStart(0, calendar.clone() as Calendar)
                    val end = TimeMath.dayStart(1, calendar.clone() as Calendar)
                    assertEquals(hours * 3_600_000, end - start)
                    assertEquals(hours * 3_600_000, StatisticsCalculator.totalDuration(
                        listOf(TimeInterval(start, end)), start, end, end))
                    val activity = StatisticsCalculator.activityByDay(
                        listOf(Session(1, 7, start, end)), emptyList(), start, end, end)
                    assertEquals(mapOf(start to setOf(7)), activity)
                }
        } finally { TimeZone.setDefault(previous) }
    }

    @Test fun midnightEndingDoesNotMarkNextDayButEventAtMidnightDoes() {
        val calendar = Calendar.getInstance().apply { clear(); set(2026, Calendar.NOVEMBER, 10, 12, 0) }
        val start = TimeMath.dayStart(0, calendar.clone() as Calendar)
        val midnight = TimeMath.dayStart(1, calendar.clone() as Calendar)
        val end = TimeMath.dayStart(2, calendar.clone() as Calendar)
        val activity = StatisticsCalculator.activityByDay(
            listOf(Session(1, 3, start, midnight)),
            listOf(SessionEvent(1, 1, 4, midnight, "Inne", "")), start, end, end)
        assertEquals(mapOf(start to setOf(3), midnight to setOf(4)), activity)
    }

    @Test fun monthOffsetHandlesLeapYearAndYearBoundary() {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { clear(); set(2024, Calendar.MARCH, 15, 12, 0) }
        val range = StatisticsCalculator.periodRange(2, -1, null, calendar.timeInMillis, calendar.clone() as Calendar)
        assertEquals(29 * 86_400_000L, range.second - range.first)
        val previousYear = StatisticsCalculator.periodRange(2, -3, null, calendar.timeInMillis, calendar.clone() as Calendar)
        val expected = calendar.clone() as Calendar
        expected.clear(); expected.set(2023, Calendar.DECEMBER, 1)
        assertEquals(expected.timeInMillis, previousYear.first)
    }

    @Test fun weekRangeRespectsTheCalendarFirstDayOfWeek() {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear(); set(2026, Calendar.NOVEMBER, 11, 12, 0); firstDayOfWeek = Calendar.MONDAY
        }
        val range = StatisticsCalculator.periodRange(1, 0, null, calendar.timeInMillis, calendar.clone() as Calendar)
        val expected = calendar.clone() as Calendar
        expected.clear(); expected.set(2026, Calendar.NOVEMBER, 9)
        assertEquals(expected.timeInMillis, range.first)
        assertEquals(7 * 86_400_000L, range.second - range.first)
    }

    @Test fun allTimeRangeUsesEarliestSessionAndEmptyHistoryIsEmpty() {
        assertEquals(100L to 300L, StatisticsCalculator.periodRange(3, -99, 100, 300))
        assertEquals(300L to 300L, StatisticsCalculator.periodRange(3, 0, null, 300))
    }

    @Test fun trendClipsSessionBoundariesAndSumsSimultaneousParticipants() {
        val previous = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        try {
            val day = 86_400_000L
            val buckets = StatisticsCalculator.trendBuckets(1, 0, 2 * day,
                listOf(Triple(1, -100L, day + 100), Triple(2, 0L, 1_000L)), 2 * day)
            assertEquals(listOf(day + 1_000, 100L), buckets.map { it.milliseconds })
        } finally { TimeZone.setDefault(previous) }
    }

    @Test fun dailyTrendIncludesEmptyFutureHoursWithoutCountingFutureTime() {
        val previous = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        try {
            val hour = 3_600_000L
            val buckets = StatisticsCalculator.trendBuckets(0, 0, 24 * hour,
                listOf(Triple(1, 0L, 24 * hour)), 2 * hour + 100)
            assertEquals(24, buckets.size)
            assertEquals(2 * hour + 100, buckets.sumOf { it.milliseconds })
            assertEquals(0L, buckets.last().milliseconds)
        } finally { TimeZone.setDefault(previous) }
    }

    @Test fun dailyTrendDoesNotDuplicateOrLoseHoursAtDaylightSavingTransitions() {
        val previous = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Warsaw"))
        try {
            listOf(Triple(2026, Calendar.MARCH, 29) to 23, Triple(2026, Calendar.OCTOBER, 25) to 25)
                .forEach { (date, hours) ->
                    val calendar = Calendar.getInstance().apply { clear(); set(date.first, date.second, date.third, 12, 0) }
                    val start = TimeMath.dayStart(0, calendar.clone() as Calendar)
                    val end = TimeMath.dayStart(1, calendar.clone() as Calendar)
                    val buckets = StatisticsCalculator.trendBuckets(0, start, end, listOf(Triple(1, start, end)), end)
                    assertEquals(hours, buckets.size)
                    assertEquals(end - start, buckets.sumOf { it.milliseconds })
                    assertEquals(hours, buckets.map { it.start }.distinct().size)
                    buckets.zipWithNext().forEach { (first, second) -> assertEquals(first.end, second.start) }
                }
        } finally { TimeZone.setDefault(previous) }
    }

    @Test fun outOfRangeEventsDoNotMarkDaysOutsideTheNavigatorWindow() {
        assertEquals(emptyMap<Long, Set<Int>>(), StatisticsCalculator.activityByDay(emptyList(),
            listOf(SessionEvent(1, 1, 1, 99, "Inne", ""), SessionEvent(2, 1, 1, 200, "Inne", "")),
            100, 200, 200))
    }
}
