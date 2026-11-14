package pl.wgabel.timetracker.model



/** Persisted session annotation. All timestamps are epoch milliseconds. */
internal data class SessionEvent(
    val id: Long, val sessionId: Long, val participantId: Int,
    val happenedAt: Long, val type: String, val note: String
)
