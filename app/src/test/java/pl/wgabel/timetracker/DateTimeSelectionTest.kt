package pl.wgabel.timetracker

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.*
import org.junit.Test
import pl.wgabel.timetracker.time.DateTimeSelection

class DateTimeSelectionTest {
    private val initial = Instant.parse("2026-11-13T21:01:37.123Z").toEpochMilli()
    private fun selection() = DateTimeSelection(initial, ZoneOffset.UTC)

    @Test fun untouchedSelectionKeepsTheExactPersistedTimestamp() {
        assertEquals(initial, selection().timestamp)
    }

    @Test fun preciseMinuteAndScaleShareOneValueWithoutRoundingOnRender() {
        val selection = selection()
        selection.selectMinuteFromScale(773)
        assertEquals(775, selection.minuteOfDay)
        selection.adjustMinutes(1)
        assertEquals(776, selection.minuteOfDay)
        assertEquals(12, selection.dateTime.hour)
        assertEquals(56, selection.dateTime.minute)
    }

    @Test fun hourAdjustmentRetainsMinutesSecondsAndMilliseconds() {
        val selection = selection()
        selection.adjustHours(1)
        assertEquals(Instant.parse("2026-11-13T22:01:37.123Z").toEpochMilli(), selection.timestamp)
    }

    @Test fun minuteAdjustmentCarriesAcrossHoursInBothDirections() {
        val selection = selection()
        selection.selectMinuteOfDay(12 * 60 + 59)
        selection.adjustMinutes(1)
        assertEquals(13 * 60, selection.minuteOfDay)
        selection.adjustMinutes(-1)
        assertEquals(12 * 60 + 59, selection.minuteOfDay)
    }

    @Test fun timeAdjustmentsNeverSilentlyChangeTheSelectedDate() {
        val selection = selection()
        val date = selection.dateTime.toLocalDate()
        selection.adjustMinutes(Int.MAX_VALUE)
        assertEquals(1_439, selection.minuteOfDay)
        assertEquals(date, selection.dateTime.toLocalDate())
        selection.adjustMinutes(Int.MIN_VALUE)
        assertEquals(0, selection.minuteOfDay)
        assertEquals(date, selection.dateTime.toLocalDate())
    }

    @Test fun hourBoundsRetainTheOtherTimeParts() {
        val selection = selection()
        selection.adjustHours(Int.MAX_VALUE)
        assertEquals(23, selection.dateTime.hour)
        assertEquals(1, selection.dateTime.minute)
        selection.adjustHours(Int.MIN_VALUE)
        assertEquals(0, selection.dateTime.hour)
        assertEquals(1, selection.dateTime.minute)
        assertEquals(37, selection.dateTime.second)
    }

    @Test fun dateSelectionRetainsPreciseWallClockTime() {
        val selection = selection()
        selection.selectDate(LocalDate.of(2026, 11, 12))
        assertEquals(Instant.parse("2026-11-12T21:01:37.123Z").toEpochMilli(), selection.timestamp)
    }

    @Test fun secondAdjustmentCarriesIntoMinutesAndOnlyExplicitlyDropsSubseconds() {
        val selection = selection()
        selection.adjustSeconds(23)
        assertEquals(Instant.parse("2026-11-13T21:02:00Z").toEpochMilli(), selection.timestamp)
    }

    @Test fun boundsAreInclusiveAndInvalidSelectionsAreNotSilentlyClamped() {
        val selection = selection()
        assertNull(selection.validationError(initial, initial))
        assertEquals(DateTimeSelection.ValidationError.BEFORE_MINIMUM, selection.validationError(initial + 1, null))
        assertEquals(DateTimeSelection.ValidationError.AFTER_MAXIMUM, selection.validationError(null, initial - 1))
        assertEquals(initial, selection.timestamp)
    }

    @Test fun shortcutsCanSelectADifferentDayAndRetainTheChosenInstant() {
        val selection = selection()
        val selected = Instant.parse("2026-11-12T23:59:58.555Z").toEpochMilli()
        selection.selectTimestamp(selected)
        assertEquals(selected, selection.timestamp)
        assertEquals(LocalDate.of(2026, 11, 12), selection.dateTime.toLocalDate())
    }

    @Test fun nonexistentSpringClockTimeIsRejectedRatherThanNormalized() {
        val selection = DateTimeSelection(Instant.parse("2026-03-29T00:30:00Z").toEpochMilli(), ZoneId.of("Europe/Warsaw"))
        selection.selectMinuteOfDay(2 * 60 + 30)
        assertEquals(2, selection.dateTime.hour)
        assertNull(selection.timestamp)
        assertEquals(DateTimeSelection.ValidationError.MISSING_LOCAL_TIME, selection.validationError(null, null))
        selection.adjustHours(1)
        assertEquals(Instant.parse("2026-03-29T01:30:00Z").toEpochMilli(), selection.timestamp)
    }

    @Test fun editingTheSecondAutumnOccurrencePreservesItsOffset() {
        val timestamp = Instant.parse("2026-10-25T01:30:37.123Z").toEpochMilli()
        val selection = DateTimeSelection(timestamp, ZoneId.of("Europe/Warsaw"))
        selection.adjustMinutes(1)
        selection.adjustMinutes(-1)
        assertEquals(timestamp, selection.timestamp)
        assertEquals(ZoneOffset.ofHours(1), selection.selectedOffset)
    }

    @Test fun repeatedClockHourCanBeSelectedExplicitlyWithoutChangingItsDisplay() {
        val selection = DateTimeSelection(Instant.parse("2026-10-25T00:30:00Z").toEpochMilli(), ZoneId.of("Europe/Warsaw"))
        assertEquals(2, selection.availableOffsets.size)
        selection.selectOffset(ZoneOffset.ofHours(1))
        assertEquals(2 * 60 + 30, selection.minuteOfDay)
        assertEquals(Instant.parse("2026-10-25T01:30:00Z").toEpochMilli(), selection.timestamp)
    }
}
