package pl.wgabel.timetracker.tabs

/** Collects timer-tab state and delegates its presentation to a renderer. */
class TimersService(
    renderer: ITabRenderer<TimersViewState>,
    private val loadState: (Long) -> TimersViewState
) : BaseTabService<TimersViewState>(renderer) {
    override fun onInitialize() = Unit

    fun render(projectId: Long) {
        initialize()
        renderer.render(loadState(projectId))
    }
}
