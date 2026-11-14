package pl.wgabel.timetracker.tabs

import android.database.sqlite.SQLiteDatabase
import pl.wgabel.timetracker.TimeMath

/** Loads timer-tab state independently of Activity and its view hierarchy. */
internal class TimersStateDataSource(
    private val database: () -> SQLiteDatabase
) {
    fun load(projectId: Long): TimersViewState {
        val db = database()
        val archived = db.rawQuery(
            "SELECT archived FROM projects WHERE id=?",
            arrayOf(projectId.toString())
        ).use { it.moveToFirst() && it.getInt(0) == 1 }

        return TimersViewState(
            projectId = projectId,
            dayStart = TimeMath.dayStart(0),
            dayEnd = TimeMath.dayStart(1),
            now = System.currentTimeMillis(),
            participantIds = ParticipantDataSource(db).ids(projectId),
            archived = archived
        )
    }
}
