package pl.wgabel.timetracker.ui.timeline

import pl.wgabel.timetracker.model.SessionEvent

/** Screen-space grouping only: persisted event timestamps and associations never change. */
internal object TimelineEventClusters {
    fun groupEvents(events: List<SessionEvent>, dayStart: Long, dayEnd: Long,
                    trackWidth: Float, minimumSeparation: Float): List<List<SessionEvent>> {
        require(dayEnd > dayStart && trackWidth > 0f && minimumSeparation > 0f)
        val groups = mutableListOf<MutableList<SessionEvent>>()
        events.filter { it.happenedAt >= dayStart && it.happenedAt < dayEnd }
            .groupBy { it.participantId }.values.forEach { participantEvents ->
                var current: MutableList<SessionEvent>? = null
                participantEvents.sortedWith(compareBy<SessionEvent> { it.happenedAt }.thenBy { it.id })
                    .forEach { event ->
                        val previous = current?.lastOrNull()
                        val distance = previous?.let {
                            (event.happenedAt - it.happenedAt).toDouble() / (dayEnd - dayStart) * trackWidth
                        }
                        if (distance == null || distance >= minimumSeparation) {
                            current = mutableListOf(event).also { groups.add(it) }
                        } else {
                            current!!.add(event)
                        }
                    }
            }
        return groups
    }

    fun centerFraction(events: List<SessionEvent>, dayStart: Long, dayEnd: Long): Float =
        events.map { (it.happenedAt - dayStart).toDouble() / (dayEnd - dayStart) }.average().toFloat()
}
