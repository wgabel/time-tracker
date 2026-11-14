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
}
