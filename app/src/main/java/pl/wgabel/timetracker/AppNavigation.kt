package pl.wgabel.timetracker

/** Feature-to-host boundary; screens do not depend on Activity or each other. */
internal interface AppNavigation {
    val projectId: Long?
    fun openProject(projectId: Long)
    fun openProjects(archived: Boolean = false)
    fun openAppSettings()
    fun openCounterHistory(person: Int, dayOffset: Int)
    fun openActiveSessionInHistory(person: Int, sessionId: Long)
    fun openHistory()
    fun refreshContent()
    fun refreshNavigation()
    fun exportProject(projectId: Long, suggestedName: String)
    fun importProject(projectId: Long)
}

