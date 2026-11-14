package pl.wgabel.timetracker.tabs

/** Placeholder tab logic; not wired into MainActivity yet. */
class HistoryService(
    renderer: ITabRenderer<HistoryViewState> = HistoryRenderer()
) : BaseTabService<HistoryViewState>(renderer) {
    override fun onInitialize() = Unit
}
