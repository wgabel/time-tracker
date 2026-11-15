package pl.wgabel.timetracker

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.*
import org.junit.Test
import pl.wgabel.timetracker.time.SessionTimeSelection

class SessionTimeSelectionTest {
    private val started = Instant.parse("2026-11-13T07:00:37.123Z").toEpochMilli()
    private val ended = Instant.parse("2026-11-13T09:00:58.456Z").toEpochMilli()
    private fun selection() = SessionTimeSelection(started, ended, ZoneOffset.UTC)

    @Test fun unchangedSessionRetainsBothExactEpochValues() {
        val session = selection()
        assertEquals(started, session.start.timestamp)
        assertEquals(ended, session.end.timestamp)
        assertNull(session.validationError(ended))
    }

    @Test fun changingOnlyTheEndDoesNotModifyTheStart() {
        val session = selection()
        session.selectBoundary(SessionTimeSelection.Boundary.END)
        session.selection.adjustMinutes(1)
        assertEquals(started, session.start.timestamp)
        assertEquals(ended + 60_000, session.end.timestamp)
    }

    @Test fun switchingBoundariesRetainsBothDraftsAndThePreciseMinute() {
        val session = selection()
        session.selection.adjustMinutes(1)
        session.selectBoundary(SessionTimeSelection.Boundary.END)
        session.selection.selectMinuteFromScale(650)
        session.selection.adjustMinutes(1)
        session.selectBoundary(SessionTimeSelection.Boundary.START)
        assertEquals(started + 60_000, session.selection.timestamp)
        session.selectBoundary(SessionTimeSelection.Boundary.END)
        assertEquals(651, session.selection.minuteOfDay)
        assertEquals(58, session.selection.dateTime.second)
        assertEquals(456_000_000, session.selection.dateTime.nano)
    }

    @Test fun endMustBeStrictlyLaterAndSwitchingDoesNotHideTheError() {
        val session = selection()
        session.end.selectTimestamp(started)
        assertEquals(SessionTimeSelection.ValidationError.END_NOT_AFTER_START, session.validationError(null))
        session.selectBoundary(SessionTimeSelection.Boundary.END)
        assertEquals(SessionTimeSelection.ValidationError.END_NOT_AFTER_START, session.validationError(null))
        session.end.selectTimestamp(started - 1)
        assertEquals(SessionTimeSelection.ValidationError.END_NOT_AFTER_START, session.validationError(null))
        session.end.selectTimestamp(started + 1)
        assertNull(session.validationError(null))
    }

    @Test fun futureValidationIncludesTheInactiveBoundaryAndInclusiveMaximum() {
        val session = selection()
        assertEquals(SessionTimeSelection.ValidationError.END_AFTER_MAXIMUM, session.validationError(ended - 1))
        assertNull(session.validationError(ended))
        session.start.selectTimestamp(ended + 1)
        session.selectBoundary(SessionTimeSelection.Boundary.END)
        assertEquals(SessionTimeSelection.ValidationError.START_AFTER_MAXIMUM, session.validationError(ended))
    }

    @Test fun differentCalendarDatesArePreservedWhenEditingAcrossMidnight() {
        val session = selection()
        session.start.selectMinuteOfDay(23 * 60 + 59)
        session.end.selectDate(LocalDate.of(2026, 11, 14))
        session.end.selectMinuteOfDay(0)
        assertNull(session.validationError(null))
        session.selectBoundary(SessionTimeSelection.Boundary.END)
        session.selection.adjustMinutes(1)
        session.selectBoundary(SessionTimeSelection.Boundary.START)
        assertEquals(LocalDate.of(2026, 11, 13), session.selection.dateTime.toLocalDate())
        assertEquals(LocalDate.of(2026, 11, 14), session.end.dateTime.toLocalDate())
    }

    @Test fun nonexistentLocalTimeInEitherBoundaryBlocksTheWholeDraft() {
        val session = SessionTimeSelection(Instant.parse("2026-03-29T00:30:00Z").toEpochMilli(),
            Instant.parse("2026-03-29T02:30:00Z").toEpochMilli(), ZoneId.of("Europe/Warsaw"))
        session.start.selectMinuteOfDay(150)
        session.selectBoundary(SessionTimeSelection.Boundary.END)
        assertEquals(SessionTimeSelection.ValidationError.START_MISSING_LOCAL_TIME, session.validationError(null))
        session.start.selectMinuteOfDay(90)
        session.end.selectMinuteOfDay(150)
        session.selectBoundary(SessionTimeSelection.Boundary.START)
        assertEquals(SessionTimeSelection.ValidationError.END_MISSING_LOCAL_TIME, session.validationError(null))
    }

    @Test fun repeatedHourOffsetsStayIndependentWhenSwitchingBoundaries() {
        val first = Instant.parse("2026-10-25T00:30:37.123Z").toEpochMilli()
        val second = Instant.parse("2026-10-25T01:30:37.123Z").toEpochMilli()
        val session = SessionTimeSelection(first, second, ZoneId.of("Europe/Warsaw"))
        repeat(3) {
            session.selectBoundary(SessionTimeSelection.Boundary.END)
            session.selectBoundary(SessionTimeSelection.Boundary.START)
        }
        assertEquals(first, session.start.timestamp)
        assertEquals(second, session.end.timestamp)
        assertNull(session.validationError(second))
    }
}
