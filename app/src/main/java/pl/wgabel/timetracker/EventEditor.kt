package pl.wgabel.timetracker

import android.app.Activity
import android.app.AlertDialog
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.Toast
import android.view.View
import java.util.Date

/** Event form UI; persistence and session-bound checks remain with the caller. */
internal class EventEditor(private val activity: Activity) {
    fun show(
        id: Long?,
        timestamp: Long,
        currentType: String,
        currentNote: String,
        sessionBounds: Pair<Long, Long?>,
        timeButtonFactory: (String) -> Button,
        rangeLabelFactory: (String) -> View,
        cancelText: String,
        saveText: String,
        onSave: (Long, String, String) -> Boolean,
        onInvalidTime: () -> Unit,
        onSaveFailed: () -> Unit
    ) {
        val types = arrayOf("Lek", "Posiłek", "Sen", "Spacer", "Inne")
        val density = activity.resources.displayMetrics.density
        fun dp(value: Int) = (value * density + 0.5f).toInt()
        val panel = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(10), dp(20), dp(8))
        }
        val dateTimeFormat = DatePresentation.dateTime(activity.resources)
        var chosenTimestamp = timestamp
        val timeButton = timeButtonFactory(dateTimeFormat.format(Date(chosenTimestamp)))
        val sessionEnd = sessionBounds.second ?: System.currentTimeMillis()
        panel.addView(rangeLabelFactory(activity.getString(
            R.string.event_time_range,
            dateTimeFormat.format(Date(sessionBounds.first)),
            dateTimeFormat.format(Date(sessionEnd))
        )))
        timeButton.setOnClickListener {
            DateTimeEditor(activity).show(DateTimeEditor.Config(
                title = if (activity.resources.configuration.locales[0].language == "pl") "Czas wydarzenia" else "Event time",
                initial = chosenTimestamp,
                min = sessionBounds.first,
                max = minOf(sessionBounds.second ?: Long.MAX_VALUE, System.currentTimeMillis()),
                seconds = true
            )) { chosen ->
                chosenTimestamp = chosen
                timeButton.text = dateTimeFormat.format(Date(chosenTimestamp))
                true
            }
        }
        panel.addView(timeButton)
        val picker = Spinner(activity)
        picker.adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, types)
        picker.setSelection(types.indexOf(currentType).coerceAtLeast(0))
        panel.addView(picker)
        val note = EditText(activity).apply {
            hint = "Opis (dla leku: nazwa, stężenie, dawka)"
            setText(currentNote)
            minLines = 2
        }
        panel.addView(note)
        val dialog = AlertDialog.Builder(activity)
            .setTitle(if (id == null) "Dodaj wydarzenie" else "Edytuj wydarzenie")
            .setView(panel)
            .setNegativeButton(cancelText, null)
            .setPositiveButton(saveText, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val type = types[picker.selectedItemPosition]
                val description = note.text.toString().trim()
                val latestEnd = minOf(sessionBounds.second ?: Long.MAX_VALUE, System.currentTimeMillis())
                if (chosenTimestamp < sessionBounds.first || chosenTimestamp > latestEnd) {
                    onInvalidTime()
                } else if (type == "Lek" && description.isEmpty()) {
                    note.error = "Wpisz nazwę i dawkę leku"
                } else if (onSave(chosenTimestamp, type, description)) {
                    dialog.dismiss()
                } else {
                    onSaveFailed()
                }
            }
        }
        dialog.show()
    }
}
