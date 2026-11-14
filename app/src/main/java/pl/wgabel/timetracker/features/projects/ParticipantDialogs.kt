package pl.wgabel.timetracker.features.projects

import android.app.AlertDialog
import android.content.Context
import android.widget.EditText
import android.widget.LinearLayout
import pl.wgabel.timetracker.AppContainer
import pl.wgabel.timetracker.AppNavigation
import pl.wgabel.timetracker.R
import pl.wgabel.timetracker.ui.ScreenUi

internal class ParticipantDialogs(
    context: Context, container: AppContainer, navigation: AppNavigation
) : ScreenUi(context, container) {
    override val navigator = navigation
    private val addParticipantDialog: AddParticipantDialog by lazy {
        AddParticipantDialog(
            context = this,
            participants = participantRepository,
            projectId = { requireNotNull(projectId) },
            nextColor = { nextParticipantColor() },
            colorPicker = { color, onChange -> colorPicker(color, onChange) },
            refresh = { navigator.refreshContent() },
            string = { getString(it) }
        )
    }

    fun addParticipant() = addParticipantDialog.show()

    private fun nextParticipantColor(): Int =
        participantRepository.suggestedColor(requireNotNull(projectId))

    private val participantColorPicker: ParticipantColorPicker by lazy {
        ParticipantColorPicker(this) { value, size, bold, color ->
            label(value, size, bold, color)
        }
    }

    private fun colorPicker(initial: Int, onSelected: (Int) -> Unit): LinearLayout =
        participantColorPicker.create(initial, onSelected)

    fun editParticipantColor(person: Int) {
        if (isArchived()) return
        val selected = intArrayOf(personColor(person))
        AlertDialog.Builder(this)
            .setTitle(personName(person))
            .setView(colorPicker(selected[0]) { selected[0] = it })
            .setNegativeButton(getString(R.string.cancel), null)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                participantRepository.updateColor(requireNotNull(projectId), person, selected[0])
                navigator.refreshNavigation()
            }.show()
    }

    fun renameParticipant(person: Int) {
        val input = EditText(this).apply { setText(personName(person)); setSingleLine(true) }
        val holder = LinearLayout(this).apply {
            setPadding(densityPixels(20), 0, densityPixels(20), 0)
            addView(input)
        }
        AlertDialog.Builder(this).setTitle(getString(R.string.rename_person_prefix) + person)
            .setView(holder)
            .setNegativeButton(getString(R.string.cancel), null)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    participantRepository.rename(requireNotNull(projectId), person, name)
                    navigator.refreshContent()
                }
            }.show()
    }

    fun removeParticipant(person: Int) {
        if (participantRepository.hasSessions(requireNotNull(projectId), person)) {
            AlertDialog.Builder(this).setTitle(getString(R.string.participant_has_measurements))
                .setMessage(getString(R.string.cannot_delete_participant))
                .setPositiveButton("OK", null).show()
            return
        }
        AlertDialog.Builder(this).setTitle(getString(R.string.delete_participant))
            .setMessage(getString(R.string.delete_participant_prefix) + personName(person) + getString(R.string.delete_participant_suffix))
            .setNegativeButton(getString(R.string.cancel), null)
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                participantRepository.deleteIfNoSessions(requireNotNull(projectId), person)
                navigator.refreshContent()
            }.show()
    }

}
