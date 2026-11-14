package pl.wgabel.timetracker.model

/** Stored session identity and boundaries in epoch milliseconds. */
internal data class Session(
    val id: Long,
    val participantId: Int,
    val startedAt: Long,
    val endedAt: Long?
)
