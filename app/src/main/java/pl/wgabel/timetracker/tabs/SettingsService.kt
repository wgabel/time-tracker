package pl.wgabel.timetracker.tabs

/** Placeholder tab logic; not wired into MainActivity yet. */
class SettingsService(
    renderer: ITabRenderer<SettingsViewState> = SettingsRenderer()
) : BaseTabService<SettingsViewState>(renderer) {
    override fun onInitialize() = Unit
}
