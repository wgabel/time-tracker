package pl.wgabel.timetracker.ui.sessions

import android.app.AlertDialog
import android.content.Context
import android.widget.Toast
import pl.wgabel.timetracker.AppContainer
import pl.wgabel.timetracker.AppNavigation
import pl.wgabel.timetracker.R
import pl.wgabel.timetracker.model.Session
import pl.wgabel.timetracker.ui.ScreenUi

internal class SessionDialogs(
    context: Context, container: AppContainer, navigation: AppNavigation
) : ScreenUi(context, container) {
    override val navigator = navigation
    fun showStartTimePicker(person: Int, sessionId: Long) {
        if (isArchived()) return
        val id = requireNotNull(projectId)
        val original = sessionRepository.findSession(id, sessionId, person)
            ?.takeIf { it.endedAt == null }?.startedAt ?: return
        val polish = resources.configuration.locales[0].language == "pl"
        DateTimeEditor(this, appUi).show(DateTimeEditor.Config(
            title = if (polish) "Początek sesji" else "Session start",
            initial = original,
            max = System.currentTimeMillis()
        )) { selected ->
            val changed = container.sessionService.updateSession(id, person, sessionId, selected, null)
            if (changed) { navigator.refreshContent() }
            else Toast.makeText(this, if (polish) "Czas koliduje z sesją lub wydarzeniami."
                else "Time conflicts with a session or events.", Toast.LENGTH_LONG).show()
            changed
        }
    }


    fun deleteSession(id: Long) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.delete_measurement))
            .setMessage(getString(R.string.delete_measurement_warning))
            .setNegativeButton(getString(R.string.cancel), null)
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                val deleted = container.sessionService.deleteCompletedSession(requireNotNull(projectId), id)
                if (deleted) {
                    navigator.refreshContent()
                    Toast.makeText(this, getString(R.string.measurement_deleted), Toast.LENGTH_SHORT).show()
                }
            }.show()
    }

    fun editSession(id: Long, person: Int, started: Long, ended: Long?) {
        if (isArchived()) return
        if (ended == null) { showStartTimePicker(person, id); return }
        val pl = resources.configuration.locales[0].language == "pl"
        DateTimeEditor(this, appUi).editCompletedSession(started, ended) { newStart, newEnd ->
            val updated = container.sessionService.updateSession(projectId!!, person, id, newStart, newEnd)
            if (updated) {
                navigator.refreshContent()
                Toast.makeText(this, getString(R.string.session_updated), Toast.LENGTH_SHORT).show()
            } else Toast.makeText(this, if (pl) "Czas koliduje z sesją lub wydarzeniami."
                else "Time conflicts with a session or events.", Toast.LENGTH_LONG).show()
            updated
        }
    }

}
