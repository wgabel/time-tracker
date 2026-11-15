package pl.wgabel.timetracker.features.history

import java.time.Instant
import java.time.ZoneId
import android.app.AlertDialog
import android.content.Context
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.Toast
import pl.wgabel.timetracker.AppContainer
import pl.wgabel.timetracker.AppNavigation
import pl.wgabel.timetracker.R
import pl.wgabel.timetracker.design.AppUi.ButtonRole
import pl.wgabel.timetracker.features.projects.ParticipantDialogs
import pl.wgabel.timetracker.ui.ScreenUi
import pl.wgabel.timetracker.ui.calendar.DayNavigator
import pl.wgabel.timetracker.ui.sessions.DateTimeEditor
import pl.wgabel.timetracker.ui.timeline.TimelineRenderer

internal class HistoryScreen(
    context: Context, container: AppContainer, navigation: AppNavigation
) : ScreenUi(context, container) {
    override val navigator = navigation
    data class State(var dayOffset: Int = 0, var participantFilter: Int = 0)
    val state = State()
    lateinit var calendar: DayNavigator
    private val timeline = TimelineRenderer(context, container, navigation)
    private val rows = SessionRows(context, container, navigation)
    private val participantsEditor = ParticipantDialogs(context, container, navigation)
    private fun pickDateTime(initial: Long, title: String, onChosen: (Long) -> Unit) {
        DateTimeEditor(this, appUi).show(DateTimeEditor.Config(
            title = title, initial = initial, max = System.currentTimeMillis(),
            quickOffsets = initial >= dayStart(0)
        )) { chosen -> onChosen(chosen); true }
    }

    private fun addSession() {
        val selectedDayStart = dayStart(state.dayOffset)
        val ids = participants()
        if (ids.isEmpty()) { participantsEditor.addParticipant(); return }
        val people = ids.map { personName(it) }.toTypedArray()
        AlertDialog.Builder(this).setTitle(getString(R.string.select_person))
            .setItems(people) { _, which ->
                val person = ids[which]
                val zone = ZoneId.systemDefault()
                val now = Instant.now().atZone(zone)
                val selectedDate = Instant.ofEpochMilli(selectedDayStart).atZone(zone).toLocalDate()
                val reference = now.with(selectedDate).toInstant().toEpochMilli()
                val hour = 3_600_000L
                val initialStart = (reference - hour).coerceAtLeast(selectedDayStart)
                pickDateTime(initialStart, getString(R.string.start_measurement)) { start ->
                    val startDate = Instant.ofEpochMilli(start).atZone(zone).toLocalDate()
                    val nextMidnight = startDate.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                    val initialEnd = minOf(start + hour, nextMidnight - 1, System.currentTimeMillis())
                    pickDateTime(initialEnd, getString(R.string.end_measurement)) { end ->
                        if (container.sessionService.createSession(projectId!!, person, start, end)) {
                            state.dayOffset = 0
                            while (dayStart(state.dayOffset) > start) state.dayOffset--
                            while (dayStart(state.dayOffset + 1) <= start) state.dayOffset++
                            navigator.refreshContent()
                            Toast.makeText(this, getString(R.string.measurement_added), Toast.LENGTH_SHORT).show()
                        } else Toast.makeText(this, getString(R.string.invalid_measurement), Toast.LENGTH_LONG).show()
                    }
                }
            }.setNegativeButton(getString(R.string.cancel), null).show()
    }

    fun renderHistory() {
        // Day selection is the first control, followed by actions and participant filters.
        calendar.renderDayNavigator(state.dayOffset) { state.dayOffset = it; navigator.refreshContent() }
        gap(content, 8)

        val selected = state.participantFilter.takeIf { it != 0 }
        content.addView(participantFilterButtons(participants(), selected?.let { setOf(it) }) { person ->
            state.participantFilter = person ?: 0
            navigator.refreshContent()
        })
        gap(content, 12)
        timeline.renderTimeline(content, state.dayOffset, state.participantFilter.takeIf { it != 0 }, inHistory = true)
        val measurementsHeading = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        measurementsHeading.addView(label(getString(R.string.measurements_heading), 21f, true, ink).apply {
            letterSpacing = -0.02f
        }, LinearLayout.LayoutParams(0, -2, 1f))
        if (!isArchived()) {
            val addMeasurement = styledAction("＋", ButtonRole.PRIMARY) { addSession() }.apply {
                contentDescription = getString(R.string.add_measurement).removePrefix("+").trim()
                textSize = 24f
                minHeight = densityPixels(40)
                minimumHeight = densityPixels(40)
                minWidth = densityPixels(44)
                minimumWidth = densityPixels(44)
                setPadding(0, 0, 0, 0)
            }
            measurementsHeading.addView(addMeasurement, LinearLayout.LayoutParams(densityPixels(44), densityPixels(40)))
        }
        gap(content, 12)
        content.addView(measurementsHeading)
        gap(content, 12)
        rows.renderSessions(dayStart(state.dayOffset), dayStart(state.dayOffset + 1), state.participantFilter.takeIf { it != 0 }, content)
    }

}
