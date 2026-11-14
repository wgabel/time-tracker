package pl.wgabel.timetracker.tabs

/** Snapshot used to build the timers screen. */
data class TimersViewState(
    val projectId: Long,
    val dayStart: Long,
    val dayEnd: Long,
    val now: Long,
    val participantIds: List<Int>,
    val archived: Boolean
)
