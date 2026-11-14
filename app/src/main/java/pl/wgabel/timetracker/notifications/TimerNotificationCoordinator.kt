package pl.wgabel.timetracker.notifications

import android.content.Context
import android.content.Intent
import pl.wgabel.timetracker.TimerNotificationService
import pl.wgabel.timetracker.data.SessionRepository

/** Synchronizes Android foreground-service lifetime with persisted timers, never with rendering. */
internal class TimerNotificationCoordinator(private val context: Context, private val sessions: SessionRepository) {
    fun synchronizeTimerNotifications() {
        val intent = Intent(context, TimerNotificationService::class.java)
        if (sessions.hasAnyActiveSession()) context.startForegroundService(intent)
        else context.stopService(intent)
    }
}
