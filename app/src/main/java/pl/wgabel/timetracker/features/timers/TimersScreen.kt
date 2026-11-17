package pl.wgabel.timetracker.features.timers

import pl.wgabel.timetracker.time.DatePresentation
import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.widget.LinearLayout
import pl.wgabel.timetracker.AppContainer
import pl.wgabel.timetracker.AppNavigation
import pl.wgabel.timetracker.R
import pl.wgabel.timetracker.design.AppUi.ButtonRole
import pl.wgabel.timetracker.domain.StatisticsCalculator
import pl.wgabel.timetracker.features.projects.ParticipantDialogs
import pl.wgabel.timetracker.model.TimeInterval
import pl.wgabel.timetracker.ui.timeline.TimelineRenderer
import pl.wgabel.timetracker.ui.ActiveTimerStyle
import pl.wgabel.timetracker.ui.ScreenUi
import pl.wgabel.timetracker.ui.events.EventsScreen
import java.util.Date

internal class TimersScreen(
    context: Context, container: AppContainer, navigation: AppNavigation
) : ScreenUi(context, container) {
    override val navigator = navigation
    private val controller = TimersController(container.projects, container.participants, container.sessions, container.events, container.sessionService)
    private val timeline = TimelineRenderer(context, container, navigation)
    private val events = EventsScreen(context, container, navigation)
    private val participantsEditor = ParticipantDialogs(context, container, navigation)
    private val activeStyle = ActiveTimerStyle(context, appUi)
    private val updates = mutableListOf<(Long) -> Unit>()
    private val recentActivity = RecentActivityRenderer(
        this, { content }, appUi, ::personColor, ::personName,
        { DatePresentation.shortDateTime(resources) }, navigator::openHistory
    )

    fun updateTimers(now: Long) {
        val state = controller.state ?: return
        if (now < state.dayStart || now >= state.dayEnd) navigator.refreshContent()
        else updates.forEach { it(now) }
    }

    fun renderTimers() {
        updates.clear()
        val project = requireNotNull(projectId)
        val state = controller.loadState(project, getString(R.string.person_fallback), System.currentTimeMillis())
        val heading = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        heading.addView(label(if (resources.configuration.locales[0].language == "pl") "Uczestnicy" else "Participants", 21f, true).apply {
            letterSpacing = -0.02f
        }, LinearLayout.LayoutParams(0, -2, 1f))
        if (!state.archived) heading.addView(styledAction("＋", ButtonRole.PRIMARY) { participantsEditor.addParticipant() }.apply {
            contentDescription = getString(R.string.add_person).removePrefix("+").trim()
            textSize = 24f
            minHeight = densityPixels(40); minimumHeight = densityPixels(40); minWidth = densityPixels(44); minimumWidth = densityPixels(44)
            setPadding(0, 0, 0, 0)
        }, LinearLayout.LayoutParams(densityPixels(44), densityPixels(40)))
        gap(content, 12); content.addView(heading); gap(content, 12)
        if (state.cards.isEmpty()) {
            content.addView(label(getString(R.string.first_person))); gap(content, 12)
        }
        state.cards.forEach { renderTimerCard(it, state) }
        recentActivity.render(state.recentActivity)
    }
    private fun renderTimerCard(snapshot: TimersViewState.Card, state: TimersViewState) {
        val start = state.dayStart
        val end = state.dayEnd
        val now = state.now
        val c = appUi.card()
        val cardData = snapshot.participant
        val person = cardData.participantId
        val active = cardData.activeStartedAt
        val intervals = snapshot.sessions.map { TimeInterval(it.startedAt, it.endedAt) }
        fun measuredDuration(tick: Long) = StatisticsCalculator.totalDuration(intervals, start, end, tick)
        if (active != null) activeStyle.markActive(c)
        val header = TimerCardHeader(
            this,
            appUi.label(cardData.participantName, 19f, true, appUi.color("ink")),
            appUi.label(if (active == null) "● " + getString(R.string.timer_stopped)
                else "● " + (if (resources.configuration.locales[0].language == "pl") "TRWA" else "RUNNING"),
                12f, true, if (active == null) appUi.color("muted") else cardData.participantColor),
            appUi.shape(cardData.participantColor, 6)
        )
        val totalLabel = appUi.label(formatDuration(measuredDuration(now)), 36f, true, appUi.color("ink")).apply {
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            letterSpacing = -0.035f
            setFontFeatureSettings("tnum")
        }
        val runningSession = snapshot.sessions.firstOrNull { it.endedAt == null }
        val sessionRow = runningSession?.takeUnless { state.archived }?.let { session ->
            val formatter = if (session.startedAt < start) DatePresentation.shortDateTime(resources)
                else DatePresentation.shortTime(resources)
            TimerSessionHistoryRow(this, appUi, formatter.format(Date(session.startedAt))) {
                navigator.openActiveSessionInHistory(person, session.id)
            }.apply { showDuration(formatDuration(now - session.startedAt)) }
        }
        val clock = TimerCardClock(
            this, totalLabel,
            appUi.label(getString(R.string.today_total), 13f, false, appUi.color("muted")),
            formatTotal = { tick -> formatDuration(measuredDuration(tick)) }
        )
        val ring = DayOccupancyRing(
            this, start, end,
            measuredDuration = { measuredDuration(System.currentTimeMillis()) },
            participantColor = cardData.participantColor,
            trackColor = appUi.color("subtleNeutral"),
            textColor = appUi.color("ink"),
            captionColor = appUi.color("muted")
        )
        val strip = timeline.createTimerTrack(start, end, person, snapshot.sessions, snapshot.events)
        val controls = if (state.archived) null else {
            val button = if (active == null)
                appUi.action("▶  START", cardData.participantColor) { toggleTimer(person) }
            else appUi.styledAction("■  STOP", ButtonRole.DANGER) { toggleTimer(person) }
            TimerCardControls(this, appUi, button).apply {
                if (active != null) {
                    val eventIcon = iconButton("bookmark-add", appUi.color("eventIcon")) { events.addEvent(person) }
                    addEventButton(eventIcon) { events.addEvent(person) }
                }
            }
        }
        val cardView = TimerCardView(
            this, header, clock, ring, strip,
            sessionRow, controls,
            spacing = { container, size -> appUi.gap(container, size) }
        )
        c.addView(cardView)
        if (active != null) updates.add { tick ->
            cardView.updateClock(clock, ring, tick)
            sessionRow?.showDuration(formatDuration(tick - active))
        }
        addCard(c)
    }

    private fun toggleTimer(person: Int) {
        if (controller.toggleTimer(requireNotNull(projectId), person, System.currentTimeMillis())) navigator.refreshContent()
    }
}
