package pl.wgabel.timetracker.tabs

/** Receives an immutable snapshot; must not access persistence directly. */
interface ITabRenderer<TState> {
    fun render(state: TState)
}
