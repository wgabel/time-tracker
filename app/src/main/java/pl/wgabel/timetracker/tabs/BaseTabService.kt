package pl.wgabel.timetracker.tabs

/** Shared tab lifecycle. UI rendering stays delegated to the renderer. */
abstract class BaseTabService<TState>(
    protected val renderer: ITabRenderer<TState>
) : ITabService {
    private var initialized = false

    final override fun initialize() {
        if (initialized) return
        onInitialize()
        initialized = true
    }

    override fun refresh() = onRefresh()

    override fun onShow() {
        initialize()
    }

    override fun onHide() = Unit

    protected abstract fun onInitialize()

    protected open fun onRefresh() = Unit
}
