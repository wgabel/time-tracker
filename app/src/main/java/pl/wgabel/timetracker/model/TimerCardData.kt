package pl.wgabel.timetracker.model

/** Immutable participant identity and active-session snapshot for a timer card. */
internal data class TimerCardData(
    val participantId: Int,
    val participantName: String,
    val participantColor: Int,
    val activeStartedAt: Long?
) {
    val isRunning: Boolean get() = activeStartedAt != null
}
