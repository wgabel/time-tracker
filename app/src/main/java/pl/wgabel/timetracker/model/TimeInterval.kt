package pl.wgabel.timetracker.model

/** Time range in epoch milliseconds; a null end denotes an active session. */
internal data class TimeInterval(val start: Long, val end: Long?)
