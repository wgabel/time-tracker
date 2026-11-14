package pl.wgabel.timetracker.tabs

/** Owns the timers tab entry point, independent of its rendering implementation. */
class TimersService(
    renderer: ITabRenderer<TimersViewState> = TimersRenderer()
) : BaseTabService<TimersViewState>(renderer) {
    override fun onInitialize() = Unit

    fun render(projectId: Long) {
        initialize()
        renderer.render(TimersViewState(projectId))
    }
}
