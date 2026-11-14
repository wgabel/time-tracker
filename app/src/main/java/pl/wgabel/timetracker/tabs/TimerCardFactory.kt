package pl.wgabel.timetracker.tabs

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import pl.wgabel.timetracker.design.AppUi

/**
 * Composition root for the timer-card feature.
 *
 * MainActivity supplies screen-level dependencies once. The feature owns the
 * renderer and its UI/action adapters, keeping implementation wiring out of Activity.
 */
internal class TimerCardFactory(
    private val context: Context,
    private val database: () -> SQLiteDatabase,
    private val projectId: () -> Long,
    private val archived: () -> Boolean,
    private val components: AppUi,
    presentation: TimerCardPresentation,
    commands: TimerCardCommands
) {
    private val host = TimerCardHost(
        placeCard = presentation.placeCard,
        animateActiveCard = presentation.animateActiveCard,
        createIcon = presentation.createIcon,
        resolveString = presentation.resolveString,
        formatDuration = presentation.formatDuration,
        toggleTimer = commands.toggle,
        createEvent = commands.addEvent,
        selectStartTime = commands.selectStartTime,
        subscribeTick = commands.subscribeTick
    )

    // A single instance for the current Activity; database access stays lazy.
    private val renderer: TimerCardRenderer by lazy {
        TimerCardRenderer(
            context = context,
            db = database(),
            projectId = projectId,
            archived = archived,
            ui = host,
            components = components,
            actions = host
        )
    }

    fun render(person: Int, start: Long, end: Long, now: Long) =
        renderer.render(person, start, end, now)
}
