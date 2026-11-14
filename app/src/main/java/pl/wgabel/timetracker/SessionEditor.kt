package pl.wgabel.timetracker

import android.content.Context

/** Coordinates editing a completed session using the shared date/time picker. */
internal class SessionEditor(private val context: Context) {
    fun editCompleted(started: Long, ended: Long, onSave: (Long, Long) -> Boolean) {
        val polish = context.resources.configuration.locales[0].language == "pl"
        DateTimeEditor(context).show(DateTimeEditor.Config(
            title = if (polish) "Początek sesji" else "Session start",
            initial = started,
            max = System.currentTimeMillis()
        )) { newStart ->
            DateTimeEditor(context).show(DateTimeEditor.Config(
                title = if (polish) "Koniec sesji" else "Session end",
                initial = ended,
                min = newStart + 1L,
                max = System.currentTimeMillis()
            )) { newEnd -> onSave(newStart, newEnd) }
            true
        }
    }
}
