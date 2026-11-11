package pl.wgabel.timetracker

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.*
import android.widget.RemoteViews
import android.database.sqlite.SQLiteDatabase
import java.util.Locale

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
        val items = loadActive()
        if (items.isEmpty()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(notificationId, buildNotification(items))
        return START_STICKY
    }

    private data class Timer(val project: String, val person: String, val start: Long)

    private fun loadActive(): List<Timer> {
        val database = SQLiteDatabase.openDatabase(getDatabasePath("sessions.db").path, null, SQLiteDatabase.OPEN_READONLY)
        return try {
            val items = mutableListOf<Timer>()
            database.rawQuery("""
                SELECT projects.name, participants.name, sessions.started_at
                FROM sessions JOIN projects ON projects.id=sessions.project_id
                JOIN participants ON participants.id=sessions.person
                WHERE sessions.ended_at IS NULL ORDER BY projects.name, participants.name
            """.trimIndent(), null).use { cursor ->
                while (cursor.moveToNext()) items.add(Timer(cursor.getString(0), cursor.getString(1), cursor.getLong(2)))
            }
            items
        } finally { database.close() }
    }

    private fun startTime(start: Long): String =
        java.text.SimpleDateFormat("HH:mm", resources.configuration.locales[0]).format(java.util.Date(start))

    private fun buildNotification(items: List<Timer>): Notification {
        val projectCount = items.map { it.project }.distinct().size
        val title = if (projectCount == 1) items.first().project else getString(R.string.notification_active_projects, projectCount)
        val lines = items.take(4).map { if (projectCount == 1) getString(R.string.notification_since, it.person, startTime(it.start)) else getString(R.string.notification_project_since, it.project, it.person, startTime(it.start)) }
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
