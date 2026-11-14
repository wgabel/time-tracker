package pl.wgabel.timetracker

import android.app.*
import android.content.*
import android.os.*
import android.widget.RemoteViews
import java.util.Locale
import pl.wgabel.timetracker.MainActivity
import pl.wgabel.timetracker.R
import pl.wgabel.timetracker.TimeTrackerApplication
import pl.wgabel.timetracker.data.SessionRepository
import pl.wgabel.timetracker.time.DatePresentation

class TimerNotificationService : Service() {
    private val notificationId = 42
    private val channelId = "active_timers_visible_v2"
    override fun onBind(intent: Intent?) = null

    override fun onCreate() {
        super.onCreate()
        val code = getSharedPreferences("preferences", MODE_PRIVATE).getString("app_language", "") ?: ""
        if (code.isNotEmpty()) {
            val config = android.content.res.Configuration(resources.configuration)
            config.setLocale(Locale(code))
            resources.updateConfiguration(config, resources.displayMetrics)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(channelId, getString(R.string.notification_channel), NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = getString(R.string.notification_channel_description)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setSound(null, null)
            enableVibration(false)
            enableLights(false)
            setShowBadge(false)
        })
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val items = loadActiveTimers()
        if (items.isEmpty()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(notificationId, buildNotification(items))
        return START_STICKY
    }

    private fun loadActiveTimers(): List<SessionRepository.ActiveTimer> =
        (application as TimeTrackerApplication).container.sessions.activeTimers()

    private fun formatStartTime(start: Long): String =
        DatePresentation.shortTime(resources).format(java.util.Date(start))

    private fun buildNotification(items: List<SessionRepository.ActiveTimer>): Notification {
        val projectCount = items.map { it.project }.distinct().size
        val title = if (projectCount == 1) items.first().project else getString(R.string.notification_active_projects, projectCount)
        val lines = items.take(4).map { if (projectCount == 1) getString(R.string.notification_since, it.person, formatStartTime(it.start)) else getString(R.string.notification_project_since, it.project, it.person, formatStartTime(it.start)) }
        val description = lines.joinToString("\n") + if (items.size > 4) "\n" + getString(R.string.notification_more, items.size - 4) else ""
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val layout = RemoteViews(packageName, R.layout.notification_timer).apply {
            setTextViewText(R.id.notification_project, title)
            setTextViewText(R.id.notification_rows, description)
        }
        return Notification.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(getString(R.string.notification_tracking))
            .setContentText(getString(R.string.notification_active_count, title, items.size))
            .setWhen(System.currentTimeMillis() - (System.currentTimeMillis() - items.minOf { it.start }).coerceAtLeast(0))
            .setUsesChronometer(true)
            .setCustomBigContentView(layout)
            .setStyle(Notification.DecoratedCustomViewStyle())
            .setCustomContentView(layout)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setCategory(Notification.CATEGORY_STATUS)
            .build()
    }

}
