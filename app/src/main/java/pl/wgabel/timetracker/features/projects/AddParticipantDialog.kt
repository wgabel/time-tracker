package pl.wgabel.timetracker.features.projects

import android.app.AlertDialog
import android.content.Context
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import pl.wgabel.timetracker.R
import pl.wgabel.timetracker.data.ParticipantRepository

/** Creates participants without placing the dialog or persistence code in MainActivity. */
internal class AddParticipantDialog(
    private val context: Context,
    private val participants: ParticipantRepository,
    private val projectId: () -> Long,
    private val nextColor: () -> Int,
    private val colorPicker: (Int, (Int) -> Unit) -> View,
    private val refresh: () -> Unit,
    private val string: (Int) -> String
) {
    fun show() {
        val input = EditText(context).apply { hint = string(R.string.participant_hint); setSingleLine(true) }
        val selectedColor = intArrayOf(nextColor())
        val padding = (20 * context.resources.displayMetrics.density + .5f).toInt()
        val holder = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, 0, padding, 0)
            addView(input)
            addView(colorPicker(selectedColor[0]) { selectedColor[0] = it })
        }
        val dialog = AlertDialog.Builder(context).setTitle(string(R.string.new_participant)).setView(holder)
            .setNegativeButton(string(R.string.cancel), null)
            .setPositiveButton(string(R.string.add), null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = input.text.toString().trim()
                if (name.isEmpty()) { input.error = string(R.string.name_required); return@setOnClickListener }
                if (!participants.createParticipant(projectId(), name, selectedColor[0])) return@setOnClickListener
                dialog.dismiss()
                refresh()
            }
        }
        dialog.show()
    }
}
