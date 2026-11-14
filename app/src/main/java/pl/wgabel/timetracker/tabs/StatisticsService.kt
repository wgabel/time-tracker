package pl.wgabel.timetracker.tabs

/** Placeholder tab logic; not wired into MainActivity yet. */
class StatisticsService(
    renderer: ITabRenderer<StatisticsViewState> = StatisticsRenderer()
) : BaseTabService<StatisticsViewState>(renderer) {
    override fun onInitialize() = Unit
}
