package pl.wgabel.timetracker.tabs

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.view.View
import android.widget.LinearLayout
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
    private val placeCard: (LinearLayout) -> Unit,
    private val animateActiveCard: (LinearLayout) -> Unit,
    private val createIcon: (String, Int, () -> Unit) -> View,
    private val resolveString: (Int) -> String,
    private val formatDuration: (Long) -> String,
    private val interactions: TimersInteractionService,
    private val selectStartTime: (Int) -> Unit,
    private val subscribeTick: ((Long) -> Unit) -> Unit
) {
    private val host = TimerCardHost(
        placeCard = placeCard,
        animateActiveCard = animateActiveCard,
        createIcon = createIcon,
        resolveString = resolveString,
        formatDuration = formatDuration,
        toggleTimer = interactions::toggle,
        createEvent = interactions::addEvent,
        selectStartTime = selectStartTime,
        subscribeTick = subscribeTick
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
