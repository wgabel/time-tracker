package pl.wgabel.timetracker.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject

/** Project-scoped JSON backup; IDs are remapped on restore. */
internal class ProjectBackupRepository(private val database: SQLiteDatabase) {
    fun exportProject(projectId: Long): String {
        val project = database.rawQuery("SELECT name,archived,icon_variant FROM projects WHERE id=?", arrayOf(projectId.toString())).use { cursor ->
            require(cursor.moveToFirst()) { "Project not found" }
            JSONObject().put("name", cursor.getString(0)).put("archived", cursor.getInt(1) == 1).put("iconVariant", cursor.getLong(2))
        }
        fun rows(sql: String, fields: List<String>): JSONArray {
            val result = JSONArray()
            database.rawQuery(sql, arrayOf(projectId.toString())).use { cursor ->
                while (cursor.moveToNext()) {
                    val item = JSONObject()
                    fields.forEachIndexed { index, field ->
                        item.put(field, if (cursor.isNull(index)) JSONObject.NULL else when (cursor.getType(index)) {
                            android.database.Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(index)
                            else -> cursor.getString(index)
                        })
                    }
                    result.put(item)
                }
            }
            return result
        }
        val backup = JSONObject().put("format", "timetrkr-project").put("schemaVersion", 1)
            .put("project", project)
            .put("participants", rows("SELECT id,name,color FROM participants WHERE project_id=?", listOf("id","name","color")))
            .put("sessions", rows("SELECT id,person,started_at,ended_at FROM sessions WHERE project_id=?", listOf("id","person","startedAt","endedAt")))
            .put("events", rows("SELECT id,session_id,person,happened_at,type,note FROM events WHERE project_id=?", listOf("id","sessionId","person","happenedAt","type","note")))
        val serializedBackup = backup.toString(2)
        validate(serializedBackup)
        return serializedBackup
    }

    data class Preview(val projectName: String, val participants: Int, val sessions: Int, val events: Int)

    fun validate(json: String): Preview {
        val root = JSONObject(json)
        require(root.getString("format") == "timetrkr-project" && root.getInt("schemaVersion") == 1) { "Unsupported backup format" }
        val project = root.getJSONObject("project")
        require(project.getString("name").isNotBlank()) { "Empty project name" }
        require(project.getLong("iconVariant") >= 0)
        val participants = root.getJSONArray("participants")
        val sessions = root.getJSONArray("sessions")
        val events = root.getJSONArray("events")
        val participantIds = mutableSetOf<Long>()
        val sessionIds = mutableSetOf<Long>()
        val sessionOwners = mutableMapOf<Long, Long>()
        val sessionBounds = mutableMapOf<Long, Pair<Long, Long?>>()
        val activeParticipants = mutableSetOf<Long>()

        for (index in 0 until participants.length()) {
            val item = participants.getJSONObject(index)
            val id = item.getLong("id")
            require(id > 0 && participantIds.add(id) && item.getString("name").isNotBlank()) { "Invalid participant" }
            item.getInt("color")
        }
        for (index in 0 until sessions.length()) {
            val item = sessions.getJSONObject(index)
            val id = item.getLong("id")
            val person = item.getLong("person")
            val start = item.getLong("startedAt")
            val end = if (item.isNull("endedAt")) null else item.getLong("endedAt")
            require(id > 0 && sessionIds.add(id) && person in participantIds && start > 0 && start <= System.currentTimeMillis() && (end == null || (end >= start && end <= System.currentTimeMillis()))) { "Invalid session" }
            require(end != null || activeParticipants.add(person)) { "Duplicate active timer" }
            sessionOwners[id] = person
            sessionBounds[id] = start to end

        }
        val eventIds = mutableSetOf<Long>()
        for (index in 0 until events.length()) {
            val item = events.getJSONObject(index)
            val id = item.getLong("id")
            val sessionId = item.getLong("sessionId")
            val person = item.getLong("person")
            val happenedAt = item.getLong("happenedAt")
            val bounds = sessionBounds[sessionId]
                ?: error("Event $id references missing session $sessionId. The source data must be repaired before importing.")
            require(id > 0 && eventIds.add(id)) { "Duplicate or invalid event ID: $id" }
            require(sessionOwners[sessionId] == person) { "Event $id belongs to a different participant than session $sessionId" }
            require(happenedAt >= bounds.first && happenedAt <= (bounds.second ?: System.currentTimeMillis())) {
                "Event $id falls outside session $sessionId. The source data must be repaired before importing."
            }
            item.getString("type")
            item.getString("note")
        }
        return Preview(project.getString("name"), participants.length(), sessions.length(), events.length())
    }

    /** Replace only the selected project, in one transaction. */
    fun restoreProject(projectId: Long, json: String) {
        validate(json)
        val root = JSONObject(json)
        val project = root.getJSONObject("project")
        database.beginTransaction()
        try {
            require(database.rawQuery("SELECT 1 FROM projects WHERE id=?", arrayOf(projectId.toString())).use { it.moveToFirst() }) { "Project not found" }
            require(database.rawQuery("SELECT 1 FROM sessions WHERE project_id=? AND ended_at IS NULL LIMIT 1", arrayOf(projectId.toString())).use { !it.moveToFirst() }) { "Stop running timers before import" }
            database.delete("events", "project_id=?", arrayOf(projectId.toString()))
            database.delete("sessions", "project_id=?", arrayOf(projectId.toString()))
            database.delete("participants", "project_id=?", arrayOf(projectId.toString()))
            database.update("projects", ContentValues().apply {
                put("name", project.getString("name"))
                put("archived", if (project.getBoolean("archived")) 1 else 0)
                put("icon_variant", project.getLong("iconVariant"))
            }, "id=?", arrayOf(projectId.toString()))
            val participantMap = mutableMapOf<Long, Long>()
            val participants = root.getJSONArray("participants")
            for (index in 0 until participants.length()) {
                val item = participants.getJSONObject(index)
                val id = database.insertOrThrow("participants", null, ContentValues().apply {
                    put("project_id", projectId)
                    put("name", item.getString("name"))
                    put("color", item.getInt("color"))
                })
                participantMap[item.getLong("id")] = id
            }
            val sessionMap = mutableMapOf<Long, Long>()
            val sessions = root.getJSONArray("sessions")
            for (index in 0 until sessions.length()) {
                val item = sessions.getJSONObject(index)
                val id = database.insertOrThrow("sessions", null, ContentValues().apply {
                    put("project_id", projectId)
                    put("person", requireNotNull(participantMap[item.getLong("person")]))
                    put("started_at", item.getLong("startedAt"))
                    if (!item.isNull("endedAt")) put("ended_at", item.getLong("endedAt"))
                })
                sessionMap[item.getLong("id")] = id
            }
            val events = root.getJSONArray("events")
            for (index in 0 until events.length()) {
                val item = events.getJSONObject(index)
                database.insertOrThrow("events", null, ContentValues().apply {
                    put("project_id", projectId)
                    put("session_id", requireNotNull(sessionMap[item.getLong("sessionId")]))
                    put("person", requireNotNull(participantMap[item.getLong("person")]))
                    put("happened_at", item.getLong("happenedAt"))
                    put("type", item.getString("type"))
                    put("note", item.getString("note"))
                })
            }
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
    }
}
