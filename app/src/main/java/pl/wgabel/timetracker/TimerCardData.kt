package pl.wgabel.timetracker

import android.database.sqlite.SQLiteDatabase

/** Immutable data needed to initialize one participant timer card. */
internal data class TimerCardData(
    val participantId: Int,
    val participantName: String,
    val participantColor: Int,
    val activeStartedAt: Long?
) {
    val isRunning: Boolean get() = activeStartedAt != null
}

/**
 * Fetches participant identity and active-session state in one read.
 * Presentation and translations remain the responsibility of the activity.
 */
internal class TimerCardDataSource(private val db: SQLiteDatabase) {
    fun load(projectId: Long, participantId: Int, fallbackName: String): TimerCardData =
        db.rawQuery(
            """SELECT p.name, p.color,
                      (SELECT s.started_at FROM sessions s
                       WHERE s.project_id=p.project_id AND s.person=p.id AND s.ended_at IS NULL
                       LIMIT 1)
               FROM participants p
               WHERE p.id=? AND p.project_id=?""",
            arrayOf(participantId.toString(), projectId.toString())
        ).use { cursor ->
            if (!cursor.moveToFirst()) {
                TimerCardData(participantId, fallbackName, ParticipantColors.legacyColor(participantId), null)
            } else {
                val storedColor = cursor.getInt(1)
                TimerCardData(
                    participantId = participantId,
                    participantName = cursor.getString(0),
                    participantColor = if (storedColor != 0) storedColor else ParticipantColors.legacyColor(participantId),
                    activeStartedAt = if (cursor.isNull(2)) null else cursor.getLong(2)
                )
            }
        }
}
