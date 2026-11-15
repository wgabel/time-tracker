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

        // Wrap filters into as many rows as needed, with no horizontal scrolling.
        val availableWidth = resources.displayMetrics.widthPixels - densityPixels(32)
        val filterRows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        var filterRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        var usedWidth = 0
        (listOf(0) + participants()).forEach { person ->
            val name = if (person == 0) getString(R.string.all_people) else personName(person)
            val selected = state.participantFilter == person
            val participantColor = if (person == 0) darkHeader else personColor(person)
            val button = participantAction(name, participantColor, selected) {
                state.participantFilter = person
                navigator.refreshContent()
            }.apply {
                textSize = 13f
                minHeight = densityPixels(38)
                minimumHeight = densityPixels(38)
                setPadding(densityPixels(12), 0, densityPixels(12), 0)
                isSingleLine = true
                setAllCaps(false)
            }
            val measuredWidth = (button.paint.measureText(name) + densityPixels(44)).toInt()
                .coerceAtLeast(densityPixels(78)).coerceAtMost(availableWidth)
            val widthWithMargins = measuredWidth + densityPixels(6)
            if (usedWidth > 0 && usedWidth + widthWithMargins > availableWidth) {
                filterRows.addView(filterRow)
                filterRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                usedWidth = 0
            }
            filterRow.addView(outlinedSelection(button, participantColor, selected),
                LinearLayout.LayoutParams(measuredWidth, densityPixels(50)).apply {
                    setMargins(densityPixels(2), densityPixels(2), densityPixels(4), densityPixels(4))
                })
            usedWidth += widthWithMargins
        }
        if (filterRow.childCount > 0) filterRows.addView(filterRow)
        content.addView(filterRows)
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
