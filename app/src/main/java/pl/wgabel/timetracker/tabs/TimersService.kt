package pl.wgabel.timetracker.tabs

/** Placeholder tab logic; not wired into MainActivity yet. */
class TimersService(
    renderer: ITabRenderer<TimersViewState> = TimersRenderer()
) : BaseTabService<TimersViewState>(renderer) {
    override fun onInitialize() = Unit
}
