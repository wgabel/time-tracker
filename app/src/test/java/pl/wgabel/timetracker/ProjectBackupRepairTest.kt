package pl.wgabel.timetracker

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import pl.wgabel.timetracker.data.ProjectBackupRepair

class ProjectBackupRepairTest {
    private fun session(identifier: Long, participant: Long, start: Long, end: Long): JSONObject =
        JSONObject().put("id", identifier).put("person", participant)
            .put("startedAt", start).put("endedAt", end)

    private fun event(identifier: Long, sessionIdentifier: Long, participant: Long, time: Long): JSONObject =
        JSONObject().put("id", identifier).put("sessionId", sessionIdentifier)
            .put("person", participant).put("happenedAt", time)
            .put("type", "Inne").put("note", "")

    private fun backup(sessions: List<JSONObject>, events: List<JSONObject>): JSONObject =
        JSONObject().put("sessions", JSONArray().also { array -> sessions.forEach(array::put) })
            .put("events", JSONArray().also { array -> events.forEach(array::put) })

    @Test fun validEventRemainsUnchanged() {
        val snapshot = backup(listOf(session(10, 1, 100, 300)), listOf(event(20, 10, 1, 200)))
        ProjectBackupRepair.repairEventReferencesForExport(snapshot)
        assertEquals(10L, snapshot.getJSONArray("events").getJSONObject(0).getLong("sessionId"))
        assertEquals(1, snapshot.getJSONArray("sessions").length())
        assertFalse(snapshot.has("exportRepairs"))
    }

    @Test fun missingSessionRelinksToExistingMatchingSession() {
        val snapshot = backup(listOf(session(10, 1, 100, 300)), listOf(event(20, 99, 1, 200)))
        ProjectBackupRepair.repairEventReferencesForExport(snapshot)
        assertEquals(10L, snapshot.getJSONArray("events").getJSONObject(0).getLong("sessionId"))
        assertFalse(snapshot.getJSONArray("exportRepairs").getJSONObject(0).getBoolean("createdRecoverySession"))
    }

    @Test fun eventOutsideSessionCreatesZeroDurationRecoverySession() {
        val snapshot = backup(listOf(session(10, 1, 300, 500)), listOf(event(20, 10, 1, 200)))
        ProjectBackupRepair.repairEventReferencesForExport(snapshot)
        val recovered = snapshot.getJSONArray("sessions").getJSONObject(1)
        assertEquals(200L, recovered.getLong("startedAt"))
        assertEquals(200L, recovered.getLong("endedAt"))
        assertEquals(recovered.getLong("id"), snapshot.getJSONArray("events").getJSONObject(0).getLong("sessionId"))
        assertTrue(snapshot.getJSONArray("exportRepairs").getJSONObject(0).getBoolean("createdRecoverySession"))
    }

    @Test fun eventAssignedToOtherParticipantIsRecoveredWithoutChangingOriginalSession() {
        val snapshot = backup(listOf(session(10, 1, 100, 300)), listOf(event(20, 10, 2, 200)))
        ProjectBackupRepair.repairEventReferencesForExport(snapshot)
        assertEquals(1L, snapshot.getJSONArray("sessions").getJSONObject(0).getLong("person"))
        assertEquals(2L, snapshot.getJSONArray("sessions").getJSONObject(1).getLong("person"))
    }

    @Test fun multipleOrphansAtSameTimestampReuseRecoverySession() {
        val snapshot = backup(emptyList(), listOf(event(20, 99, 1, 200), event(21, 98, 1, 200)))
        ProjectBackupRepair.repairEventReferencesForExport(snapshot)
        assertEquals(1, snapshot.getJSONArray("sessions").length())
        val events = snapshot.getJSONArray("events")
        assertEquals(events.getJSONObject(0).getLong("sessionId"), events.getJSONObject(1).getLong("sessionId"))
        assertEquals(2, snapshot.getJSONArray("exportRepairs").length())
    }

    @Test fun repairIsIdempotent() {
        val snapshot = backup(emptyList(), listOf(event(20, 99, 1, 200)))
        ProjectBackupRepair.repairEventReferencesForExport(snapshot)
        val once = snapshot.toString()
        ProjectBackupRepair.repairEventReferencesForExport(snapshot)
        assertEquals(once, snapshot.toString())
    }
}
