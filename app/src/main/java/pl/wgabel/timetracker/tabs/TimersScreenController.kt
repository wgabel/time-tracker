package pl.wgabel.timetracker.tabs


/**
 * Owns the Timers tab composition: cards, tab renderer, and state loading.
 * The Activity supplies only screen boundaries and actions.
 */
internal class TimersScreenController(
    environment: TimersScreenEnvironment,
    presentation: TimerCardPresentation,
    commands: TimerCardCommands,
    view: TimersScreenView,
    loadState: (Long) -> TimersViewState
) {
    private val cards = TimerCardFactory(
        context = environment.context,
        database = environment.database,
        projectId = environment.projectId,
        archived = environment.archived,
        components = environment.components,
        presentation = presentation,
        commands = commands
    )

    val service: TimersService = TimersService(
        renderer = TimersRenderer(
            context = context,
            content = view.content,
            title = view.title,
            emptyMessage = view.emptyMessage,
            ui = environment.components,
            addButtonDescription = view.addButtonDescription,
            onAddParticipant = view.onAddParticipant,
            renderCard = cards::render,
            renderRecentActivity = view.renderRecentActivity
        ),
        loadState = loadState
    )

    fun render(project: Long) = service.render(project)
}
