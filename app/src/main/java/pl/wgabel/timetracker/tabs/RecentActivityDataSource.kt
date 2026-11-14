package pl.wgabel.timetracker.tabs

import android.database.sqlite.SQLiteDatabase

/** Reads recent activity without depending on Android Views. */
class RecentActivityDataSource(private val database: SQLiteDatabase) {
    fun load(projectId: Long, limit: Int = 6): List<RecentActivityEntry> {
        require(limit > 0)
        val entries = mutableListOf<RecentActivityEntry>()
        database.rawQuery(
            """SELECT occurred_at,person,description FROM (
                SELECT COALESCE(ended_at,started_at) AS occurred_at,person,'session' AS description
                FROM sessions WHERE project_id=?
                UNION ALL
                SELECT happened_at AS occurred_at,person,'event:' || type || ':' || note AS description
                FROM events WHERE project_id=?
            ) ORDER BY occurred_at DESC LIMIT ?""",
            arrayOf(projectId.toString(), projectId.toString(), limit.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                entries.add(RecentActivityEntry(cursor.getLong(0), cursor.getInt(1), cursor.getString(2)))
            }
        }
        return entries
    }
}
