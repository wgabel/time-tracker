package pl.wgabel.timetracker

import org.junit.Assert.*
import org.junit.Test

class TimeValidatorTest {
    private val now = 100_000L
    private fun interval(start: Long, end: Long? = null) = TimeValidator.Interval(start, end)

    @Test fun normalSessionIsValid() = assertTrue(TimeValidator.valid(interval(10, 20), now))
    @Test fun equalStartEndIsInvalid() = assertFalse(TimeValidator.valid(interval(10, 10), now))
    @Test fun reversedSessionIsInvalid() = assertFalse(TimeValidator.valid(interval(20, 10), now))
    @Test fun futureStartIsInvalid() = assertFalse(TimeValidator.valid(interval(now + 1), now))
    @Test fun futureEndIsInvalid() = assertFalse(TimeValidator.valid(interval(10, now + 1), now))
    @Test fun activeSessionIsValid() = assertTrue(TimeValidator.valid(interval(10), now))
    @Test fun overlappingSessionsRejected() = assertFalse(TimeValidator.canSave(interval(15, 25), listOf(interval(10, 20)), emptyList(), now))
    @Test fun adjacentSessionsAllowed() = assertTrue(TimeValidator.canSave(interval(20, 30), listOf(interval(10, 20)), emptyList(), now))
    @Test fun precedingSessionAllowed() = assertTrue(TimeValidator.canSave(interval(1, 10), listOf(interval(10, 20)), emptyList(), now))
    @Test fun enclosingSessionRejected() = assertFalse(TimeValidator.canSave(interval(5, 25), listOf(interval(10, 20)), emptyList(), now))
    @Test fun identicalSessionsRejected() = assertFalse(TimeValidator.canSave(interval(10, 20), listOf(interval(10, 20)), emptyList(), now))
    @Test fun activeSessionOverlapRejected() = assertFalse(TimeValidator.canSave(interval(15), listOf(interval(10, 20)), emptyList(), now))
    @Test fun eventInsideSessionAllowed() = assertTrue(TimeValidator.eventWithin(15, interval(10, 20), now))
    @Test fun eventAtStartAllowed() = assertTrue(TimeValidator.eventWithin(10, interval(10, 20), now))
    @Test fun eventAtEndAllowed() = assertTrue(TimeValidator.eventWithin(20, interval(10, 20), now))
    @Test fun eventBeforeStartRejected() = assertFalse(TimeValidator.eventWithin(9, interval(10, 20), now))
    @Test fun eventAfterEndRejected() = assertFalse(TimeValidator.eventWithin(21, interval(10, 20), now))
    @Test fun eventInFutureRejected() = assertFalse(TimeValidator.eventWithin(now + 1, interval(10), now))
    @Test fun shorteningSessionPastEventRejected() = assertFalse(TimeValidator.canSave(interval(10, 20), emptyList(), listOf(21), now))
    @Test fun midnightCrossingIsAllowed() = assertTrue(TimeValidator.valid(interval(86_399_000L, 86_401_000L), now))
}
