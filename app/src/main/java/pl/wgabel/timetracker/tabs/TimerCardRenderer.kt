package pl.wgabel.timetracker.tabs

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import pl.wgabel.timetracker.*

/** Composes one timer card while actions and theme tokens are injected by its host. */
internal class TimerCardRenderer(
    private val context: Context,
    private val db: android.database.sqlite.SQLiteDatabase,
    private val projectId: () -> Long,
    private val archived: () -> Boolean,
    private val ui: TimerCardUi,
    private val actions: TimerCardActions
) {
    fun render(person: Int, start: Long, end: Long, now: Long) {
        val c = ui.card()
        val state = TimerCardStateSource(db).load(
            projectId(), person, start, end, ui.string(R.string.person_fallback) + person
        )
        val cardData = state.data
        val active = cardData.activeStartedAt
        val summary = state.summary
        val intervals = state.intervals
        fun measuredDuration(tick: Long) = state.duration(start, end, tick)
        if (active != null) ui.markActive(c)
        val header = TimerCardHeader(
            context,
            ui.label(cardData.participantName, 19f, true, ui.ink),
            ui.label(if (active == null) "● " + ui.string(R.string.timer_stopped)
                else "● " + (if (context.resources.configuration.locales[0].language == "pl") "TRWA" else "RUNNING"),
                12f, true, if (active == null) ui.muted else cardData.participantColor),
            ui.shape(cardData.participantColor, 6)
        )
        val totalLabel = ui.label(ui.duration(measuredDuration(now)), 36f, true, ui.ink).apply {
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            letterSpacing = -0.035f
            setFontFeatureSettings("tnum")
        }
        val statusLabel = if (!archived()) ui.label(
            if (active == null) ui.string(R.string.timer_stopped) else ui.string(R.string.running_prefix) + ui.duration(now - active),
            16f, false, ui.muted
        ) else null
        val clock = TimerCardClock(
            context,
            totalLabel,
            ui.label(ui.string(R.string.selected_day_total), 13f, false, ui.muted),
            statusLabel,
            formatTotal = { tick -> ui.duration(measuredDuration(tick)) },
            formatStatus = if (active != null) ({ tick: Long -> ui.string(R.string.running_prefix) + ui.duration(tick - active) }) else null
        )
        val ring = DayOccupancyRing(
            context, start, end,
            measuredDuration = { measuredDuration(System.currentTimeMillis()) },
            participantColor = cardData.participantColor,
            trackColor = ui.subtleNeutral,
            textColor = ui.ink,
            captionColor = ui.muted
        )
        val strip = DaySessionStrip(
            context, start, end, summary.stripSegments(intervals, start, end),
            cardData.participantColor, ui.subtleNeutral
        )
        val controls = if (statusLabel == null) null else {
            val button = if (active == null)
                ui.action("▶  START", cardData.participantColor) { actions.toggle(person) }
            else ui.stopButton("■  STOP") { actions.toggle(person) }
            if (active != null) {
                TimerCardControls(
                    context = context,
                    running = true,
                    primaryButton = button,
                    secondaryIcon = ui.iconButton(
                        "bookmark-add",
                        if (ui.darkTheme) Color.rgb(133, 224, 180) else Color.rgb(35, 124, 98)
                    ) { actions.addEvent(person) },
                    eventLabel = ui.label("EVENT", 13f, true, ui.ink),
                    secondaryBackground = ui.shape(ui.subtleNeutral, 12),
                    onEventClick = { actions.addEvent(person) }
                )
            } else {
                val backdate = ui.iconButton("clock", ui.ink) { actions.quickStartPicker(person) }.apply {
                    contentDescription = if (context.resources.configuration.locales[0].language == "pl")
                        "Rozpocznij sesję wcześniej" else "Start session in the past"
                }
                TimerCardControls(
                    context = context,
                    running = false,
                    primaryButton = button,
                    secondaryIcon = backdate,
                    eventLabel = null,
                    secondaryBackground = ui.shape(ui.subtleNeutral, 12),
                    onEventClick = null
                )
            }
        }
        val cardView = TimerCardView(
            context, header, clock, ring, strip,
            listOf("00:00", "06:00", "12:00", "18:00", "24:00").map {
                ui.label(it, 10f, false, ui.muted)
            },
            statusLabel, controls,
            spacing = { container, size -> ui.gap(container, size) }
        )
        c.addView(cardView)
        if (active != null) actions.registerUpdate { tick ->
            cardView.updateClock(clock, ring, strip, tick)
        }
        ui.addCard(c)
    }

}
