package pl.wgabel.timetracker

import android.app.Application

class TimeTrackerApplication : Application() {
    private var dependencies: AppContainer? = null
    internal val container: AppContainer
        get() = synchronized(this) {
            dependencies ?: AppContainer(this).also { dependencies = it }
        }

    /** Process teardown and isolated instrumentation fixtures; never Activity teardown. */
    internal fun releaseContainer() = synchronized(this) {
        dependencies?.close()
        dependencies = null
    }

    override fun onTerminate() {
        releaseContainer()
        super.onTerminate()
    }
}
