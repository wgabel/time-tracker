package pl.wgabel.timetracker.tabs

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.widget.LinearLayout
import pl.wgabel.timetracker.design.AppUi

/**
 * Owns the Timers tab composition: cards, tab renderer, and state loading.
 * The Activity supplies only screen boundaries and actions.
 */
internal class TimersScreenController(
    context: Context,
    database: () -> SQLiteDatabase,
    projectId: () -> Long,
    archived: () -> Boolean,
    components: AppUi,
    presentation: TimerCardPresentation,
    commands: TimerCardCommands,
    content: () -> LinearLayout,
    title: () -> String,
    emptyMessage: () -> String,
    addButtonDescription: () -> String,
    onAddParticipant: () -> Unit,
    renderRecentActivity: () -> Unit,
    loadState: (Long) -> TimersViewState
) {
    private val cards = TimerCardFactory(
        context = context,
        database = database,
        projectId = projectId,
        archived = archived,
        components = components,
        presentation = presentation,
        commands = commands
    )

    val service: TimersService = TimersService(
        renderer = TimersRenderer(
            context = context,
            content = content,
            title = title,
            emptyMessage = emptyMessage,
            ui = components,
            addButtonDescription = addButtonDescription,
            onAddParticipant = onAddParticipant,
            renderCard = cards::render,
            renderRecentActivity = renderRecentActivity
        ),
        loadState = loadState
    )

    fun render(project: Long) = service.render(project)
}
