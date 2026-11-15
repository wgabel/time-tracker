package pl.wgabel.timetracker

import android.content.Context
import pl.wgabel.timetracker.data.EventRepository
import pl.wgabel.timetracker.data.ParticipantRepository
import pl.wgabel.timetracker.data.ProjectBackupRepository
import pl.wgabel.timetracker.data.ProjectRepository
import pl.wgabel.timetracker.data.SQLiteDatabaseService
import pl.wgabel.timetracker.data.SessionRepository
import pl.wgabel.timetracker.design.DesignTokens
import pl.wgabel.timetracker.domain.EventService
import pl.wgabel.timetracker.domain.SessionService
import pl.wgabel.timetracker.notifications.TimerNotificationCoordinator
import pl.wgabel.timetracker.widgets.TimerWidgetProvider

/** One connection owner and one dependency graph per process, including foreground notifications. */
internal class AppContainer(context: Context) : AutoCloseable {
    val designTokens = DesignTokens(context.applicationContext)
    private val database = SQLiteDatabaseService(context.applicationContext)
    val projects = ProjectRepository(database.database)
    val projectBackups = ProjectBackupRepository(database.database)
    val participants = ParticipantRepository(database.database)
    val sessions = SessionRepository(database.database)
    val events = EventRepository(database.database, sessions)
    val notifications = TimerNotificationCoordinator(context.applicationContext, sessions)
    val sessionService = SessionService(sessions, projects, participants) {
        notifications.synchronizeTimerNotifications()
        TimerWidgetProvider.refreshAllWidgets(context.applicationContext)
    }
    val eventService = EventService(events, projects)

    override fun close() = database.close()
}
