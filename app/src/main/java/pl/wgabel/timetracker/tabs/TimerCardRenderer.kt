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
    private val darkTheme: () -> Boolean,
    private val ink: Int,
    private val muted: Int,
    private val subtleNeutral: Int,
    private val card: () -> LinearLayout,
    private val addCard: (LinearLayout) -> Unit,
    private val markActive: (LinearLayout) -> Unit,
    private val label: (String, Float, Boolean, Int) -> TextView,
    private val shape: (Int, Int) -> GradientDrawable,
    private val gap: (LinearLayout, Int) -> Unit,
    private val action: (String, Int, () -> Unit) -> Button,
    private val stopButton: (String, () -> Unit) -> Button,
    private val iconButton: (String, Int, () -> Unit) -> View,
    private val toggle: (Int) -> Unit,
    private val addEvent: (Int) -> Unit,
    private val quickStartPicker: (Int) -> Unit,
    private val registerUpdate: ((Long) -> Unit) -> Unit,
    private val string: (Int) -> String,
    private val duration: (Long) -> String
) {
    fun render(person: Int, start: Long, end: Long, now: Long) {
        val c = card()
        val state = TimerCardStateSource(db).load(
            projectId(), person, start, end, string(R.string.person_fallback) + person
        )
        val cardData = state.data
        val active = cardData.activeStartedAt
        val summary = state.summary
        val intervals = state.intervals
        fun measuredDuration(tick: Long) = state.duration(start, end, tick)
        if (active != null) markActive(c)
        val header = TimerCardHeader(
            context,
            label(cardData.participantName, 19f, true, ink),
            label(if (active == null) "● " + string(R.string.timer_stopped)
                else "● " + (if (context.resources.configuration.locales[0].language == "pl") "TRWA" else "RUNNING"),
                12f, true, if (active == null) muted else cardData.participantColor),
            shape(cardData.participantColor, 6)
        )
        val totalLabel = label(duration(measuredDuration(now)), 36f, true, ink).apply {
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            letterSpacing = -0.035f
            setFontFeatureSettings("tnum")
        }
        val statusLabel = if (!archived()) label(
            if (active == null) string(R.string.timer_stopped) else string(R.string.running_prefix) + duration(now - active),
            16f, false, muted
        ) else null
        val clock = TimerCardClock(
            context,
            totalLabel,
            label(string(R.string.selected_day_total), 13f, false, muted),
            statusLabel,
            formatTotal = { tick -> duration(measuredDuration(tick)) },
            formatStatus = if (active != null) ({ tick: Long -> string(R.string.running_prefix) + duration(tick - active) }) else null
        )
        val ring = DayOccupancyRing(
            context, start, end,
            measuredDuration = { measuredDuration(System.currentTimeMillis()) },
            participantColor = cardData.participantColor,
            trackColor = subtleNeutral,
            textColor = ink,
            captionColor = muted
        )
        val strip = DaySessionStrip(
            context, start, end, summary.stripSegments(intervals, start, end),
            cardData.participantColor, subtleNeutral
        )
        val controls = if (statusLabel == null) null else {
            val button = if (active == null)
                action("▶  START", cardData.participantColor) { toggle(person) }
            else stopButton("■  STOP") { toggle(person) }
            if (active != null) {
                TimerCardControls(
                    context = context,
                    running = true,
                    primaryButton = button,
                    secondaryIcon = iconButton(
                        "bookmark-add",
                        if (darkTheme()) Color.rgb(133, 224, 180) else Color.rgb(35, 124, 98)
                    ) { addEvent(person) },
                    eventLabel = label("EVENT", 13f, true, ink),
                    secondaryBackground = shape(subtleNeutral, 12),
                    onEventClick = { addEvent(person) }
                )
            } else {
                val backdate = iconButton("clock", ink) { quickStartPicker(person) }.apply {
                    contentDescription = if (context.resources.configuration.locales[0].language == "pl")
                        "Rozpocznij sesję wcześniej" else "Start session in the past"
                }
                TimerCardControls(
                    context = context,
                    running = false,
                    primaryButton = button,
                    secondaryIcon = backdate,
                    eventLabel = null,
                    secondaryBackground = shape(subtleNeutral, 12),
                    onEventClick = null
                )
            }
        }
        val cardView = TimerCardView(
            context, header, clock, ring, strip,
            listOf("00:00", "06:00", "12:00", "18:00", "24:00").map {
                label(it, 10f, false, muted)
            },
            statusLabel, controls,
            spacing = { container, size -> gap(container, size) }
        )
        c.addView(cardView)
        if (active != null) registerUpdate { tick ->
            cardView.updateClock(clock, ring, strip, tick)
        }
        addCard(c)
    }

}
