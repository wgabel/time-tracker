package pl.wgabel.timetracker.tabs

/** Transitional renderer adapter. MainActivity still owns the legacy View tree. */
class TimersRenderer(
    private val renderLegacyView: (TimersViewState) -> Unit = {}
) : ITabRenderer<TimersViewState> {
    override fun render(state: TimersViewState) = renderLegacyView(state)
}
