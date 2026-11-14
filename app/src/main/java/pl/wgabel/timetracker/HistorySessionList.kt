package pl.wgabel.timetracker

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.widget.LinearLayout

/**
 * Session list container and query boundary for history and recent activity.
 * The activity supplies row rendering and actions, preserving their behavior.
 */
internal class HistorySessionList(
    context: Context,
    private val db: SQLiteDatabase
) : LinearLayout(context) {
    internal data class Session(
        val id: Long,
        val participantId: Int,
        val startedAt: Long,
        val endedAt: Long?
    )

    init {
        orientation = VERTICAL
    }

    fun populate(
        projectId: Long,
        start: Long,
        end: Long,
        limit: Int,
        participantFilter: Int?,
        renderRow: (Session) -> Unit
    ): Int {
        var count = 0
        val filter = if (participantFilter != null) " AND person=?" else ""
        val arguments = mutableListOf(
            projectId.toString(), end.toString(), start.toString()
        )
        if (participantFilter != null) arguments.add(participantFilter.toString())
        db.rawQuery(
            "SELECT id,person,started_at,ended_at FROM sessions " +
                "WHERE project_id=? AND started_at<? AND (ended_at IS NULL OR ended_at>?)" +
                filter + " ORDER BY started_at DESC LIMIT ?",
            (arguments + limit.coerceAtLeast(0).toString()).toTypedArray()
        ).use { cursor ->
            while (cursor.moveToNext()) {
                renderRow(
                    Session(
                        cursor.getLong(0),
                        cursor.getInt(1),
                        cursor.getLong(2),
                        if (cursor.isNull(3)) null else cursor.getLong(3)
                    )
                )
                count++
            }
        }
        return count
    }
}
