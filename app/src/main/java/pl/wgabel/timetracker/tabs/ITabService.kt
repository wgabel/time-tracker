package pl.wgabel.timetracker.tabs

/** Lifecycle contract for a single application tab. */
interface ITabService {
    fun initialize()
    fun refresh()
    fun onShow()
    fun onHide()
}
