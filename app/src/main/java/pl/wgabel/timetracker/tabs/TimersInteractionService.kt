package pl.wgabel.timetracker.tabs

import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import android.database.sqlite.SQLiteDatabase
import pl.wgabel.timetracker.DateTimeEditor
import pl.wgabel.timetracker.SessionRepository
import pl.wgabel.timetracker.TimerNotificationService
import pl.wgabel.timetracker.TimerSessionController

/** Owns timer commands and notification synchronization, independently of MainActivity. */
internal class TimersInteractionService(
    private val context: Context,
    private val database: () -> SQLiteDatabase,
    private val projectId: () -> Long,
    private val archived: () -> Boolean,
    private val refresh: () -> Unit,
    private val openEventEditor: (Long, Int, Long) -> Unit
) {
    fun addEvent(person: Int) {
        val sessionId = ParticipantDataSource(database()).activeSessionId(projectId(), person) ?: return
        openEventEditor(sessionId, person, System.currentTimeMillis())
    }

    fun toggle(person: Int) {
        TimerSessionController(database()).toggle(projectId(), person)
        syncNotification()
        refresh()
    }

    fun quickStartPicker(person: Int, sessionId: Long? = null) {
        if (archived()) return
        val id = projectId()
        val original = if (sessionId == null) null else database().rawQuery(
            "SELECT started_at FROM sessions WHERE id=? AND project_id=? AND person=? AND ended_at IS NULL",
            arrayOf(sessionId.toString(), id.toString(), person.toString())
        ).use { if (it.moveToFirst()) it.getLong(0) else null }
        if (sessionId != null && original == null) return
        val polish = context.resources.configuration.locales[0].language == "pl"
        DateTimeEditor(context).show(DateTimeEditor.Config(
            title = if (polish) "Początek sesji" else "Session start",
            initial = original ?: System.currentTimeMillis() - 15 * 60_000L,
            max = System.currentTimeMillis()
        )) { selected ->
            val repository = SessionRepository(database())
            val changed = if (sessionId == null) {
                repository.insert(id, person, selected, null)
            } else {
                repository.update(id, person, sessionId, selected, null)
            }
            if (changed) { syncNotification(); refresh() }
            else Toast.makeText(context, if (polish) "Czas koliduje z sesją lub wydarzeniami."
                else "Time conflicts with a session or events.", Toast.LENGTH_LONG).show()
            changed
        }
    }

    fun syncNotification() {
        val hasActive = TimerSessionController(database()).hasAnyActiveSession()
        val intent = Intent(context, TimerNotificationService::class.java)
        if (hasActive) {
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent)
            else context.startService(intent)
        } else {
            context.stopService(intent)
        }
    }
}
