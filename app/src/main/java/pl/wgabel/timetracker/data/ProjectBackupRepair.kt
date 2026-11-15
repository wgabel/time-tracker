package pl.wgabel.timetracker.data

import org.json.JSONArray
import org.json.JSONObject

/** Repairs exported snapshots without touching the database. */
internal object ProjectBackupRepair {
    /**
     * Repairs the exported snapshot, never the original database.
     * Missing or inconsistent event references are linked to a matching existing
     * session when possible. Otherwise a zero-duration recovery session is added
     * at the event timestamp, avoiding invented elapsed childcare time.
     */
    fun repairEventReferencesForExport(backup: JSONObject) {
        val sessions = backup.getJSONArray("sessions")
        val events = backup.getJSONArray("events")
        val sessionById = mutableMapOf<Long, JSONObject>()
        var nextSessionId = 1L
        for (index in 0 until sessions.length()) {
            val session = sessions.getJSONObject(index)
            val identifier = session.getLong("id")
            sessionById[identifier] = session
            nextSessionId = maxOf(nextSessionId, identifier + 1)
        }
        val repairs = JSONArray()
        for (index in 0 until events.length()) {
            val event = events.getJSONObject(index)
            val person = event.getLong("person")
            val timestamp = event.getLong("happenedAt")
            val originalSessionId = event.getLong("sessionId")
            val originalSession = sessionById[originalSessionId]
            val originalIsValid = originalSession != null &&
                originalSession.getLong("person") == person &&
                timestamp >= originalSession.getLong("startedAt") &&
                (originalSession.isNull("endedAt") || timestamp <= originalSession.getLong("endedAt"))
            if (originalIsValid) continue

            val matchingSession = sessionById.values
                .filter { session ->
                    session.getLong("person") == person &&
                        timestamp >= session.getLong("startedAt") &&
                        (session.isNull("endedAt") || timestamp <= session.getLong("endedAt"))
                }
                .minByOrNull { it.getLong("id") }

            val replacementSessionId = if (matchingSession != null) {
                matchingSession.getLong("id")
            } else {
                val identifier = nextSessionId++
                val recoverySession = JSONObject()
                    .put("id", identifier)
                    .put("person", person)
                    .put("startedAt", timestamp)
                    .put("endedAt", timestamp)
                sessions.put(recoverySession)
                sessionById[identifier] = recoverySession
                identifier
            }
            event.put("sessionId", replacementSessionId)
            repairs.put(JSONObject()
                .put("eventId", event.getLong("id"))
                .put("originalSessionId", originalSessionId)
                .put("exportedSessionId", replacementSessionId)
                .put("createdRecoverySession", matchingSession == null))
        }
        if (repairs.length() > 0) {
            backup.put("exportRepairs", repairs)
        }
    }

}
