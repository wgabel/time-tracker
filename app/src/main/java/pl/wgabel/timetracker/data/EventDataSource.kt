package pl.wgabel.timetracker.data

import android.database.sqlite.SQLiteDatabase

internal data class DailyEvent(val participantId: Int, val happenedAt: Long, val type: String, val note: String)

/** Read-only event queries; formatting belongs to the presentation layer. */
internal class EventDataSource(private val database: SQLiteDatabase) {
    fun eventsForDay(projectId: Long, start: Long, end: Long): List<DailyEvent> {
        val events = mutableListOf<DailyEvent>()
        database.rawQuery(
            "SELECT person,happened_at,type,note FROM events WHERE project_id=? AND happened_at>=? AND happened_at<? ORDER BY happened_at DESC",
            arrayOf(projectId.toString(), start.toString(), end.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                events.add(DailyEvent(cursor.getInt(0), cursor.getLong(1), cursor.getString(2), cursor.getString(3)))
            }
        }
        return events
    }
}
