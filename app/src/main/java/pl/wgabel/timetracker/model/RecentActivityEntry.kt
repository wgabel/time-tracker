package pl.wgabel.timetracker.model

/** A session or event shown in the Timers tab's recent-activity section. */
data class RecentActivityEntry(
    val occurredAt: Long,
    val participantId: Int,
    val description: String
)
