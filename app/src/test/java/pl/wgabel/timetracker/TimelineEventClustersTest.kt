package pl.wgabel.timetracker

import org.junit.Assert.assertEquals
import org.junit.Test
import pl.wgabel.timetracker.model.SessionEvent
import pl.wgabel.timetracker.ui.timeline.TimelineEventClusters

class TimelineEventClustersTest {
    private fun event(id: Long, time: Long, person: Int = 1) = SessionEvent(id, 1, person, time, "Sen", "")

    @Test fun nearbyEventsAreGroupedInTimestampOrderWithoutLosingEntries() {
        val events = listOf(event(3, 850), event(2, 202), event(1, 200))
        val groups = TimelineEventClusters.groupEvents(events, 0, 1000, 300f, 48f)
        assertEquals(listOf(listOf(1L, 2L), listOf(3L)), groups.map { it.map { event -> event.id } })
        assertEquals(events.toSet(), groups.flatten().toSet())
    }

    @Test fun eventsOfDifferentParticipantsNeverMerge() {
        val groups = TimelineEventClusters.groupEvents(listOf(event(1, 200), event(2, 200, 2)), 0, 1000, 300f, 48f)
        assertEquals(listOf(1, 1), groups.map { it.size })
    }

    @Test fun groupingRespondsToAvailableWidth() {
        val events = listOf(event(1, 200), event(2, 300))
        assertEquals(1, TimelineEventClusters.groupEvents(events, 0, 1000, 300f, 48f).size)
        assertEquals(2, TimelineEventClusters.groupEvents(events, 0, 1000, 600f, 48f).size)
    }

    @Test fun eventsAtDayEndBelongToNextDayAndDayStartIsIncluded() {
        val groups = TimelineEventClusters.groupEvents(listOf(event(1, -1), event(2, 0), event(3, 999), event(4, 1000)),
            0, 1000, 300f, 48f)
        assertEquals(listOf(2L, 3L), groups.flatten().map { it.id })
    }

    @Test fun repeatedTimestampsHaveStableOrderAndExactCount() {
        val events = (100L downTo 1L).map { event(it, 500) }
        val groups = TimelineEventClusters.groupEvents(events, 0, 1000, 300f, 48f)
        assertEquals(1, groups.size)
        assertEquals((1L..100L).toList(), groups.single().map { it.id })
        assertEquals(.5f, TimelineEventClusters.centerFraction(groups.single(), 0, 1000), .0001f)
    }

    @Test fun actualDayLengthIsUsedForShortAndLongLocalDays() {
        for (hours in listOf(23L, 25L)) {
            val dayStart = 1_790_000_000_000L
            val dayEnd = dayStart + hours * 3_600_000L
            val events = listOf(event(1, dayStart + (dayEnd - dayStart) / 2))
            val groups = TimelineEventClusters.groupEvents(events, dayStart, dayEnd, 300f, 48f)
            assertEquals(.5f, TimelineEventClusters.centerFraction(groups.single(), dayStart, dayEnd), .0001f)
        }
    }

    @Test fun emptyDayHasNoMarkers() {
        assertEquals(emptyList<List<SessionEvent>>(), TimelineEventClusters.groupEvents(emptyList(), 0, 1000, 300f, 48f))
    }
}
