package pl.wgabel.timetracker.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import pl.wgabel.timetracker.MainActivity
import pl.wgabel.timetracker.R
import pl.wgabel.timetracker.TimeTrackerApplication

/** A launcher-only widget: all commands are executed by the existing application screens. */
class TimerWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, widgetIds: IntArray) {
        refreshAllWidgets(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) refreshAllWidgets(context)
    }

    companion object {
        const val ACTION_REFRESH = "pl.wgabel.timetracker.widgets.REFRESH"
        const val ACTION_TIMER = "pl.wgabel.timetracker.widgets.TIMER"
        const val ACTION_EVENT = "pl.wgabel.timetracker.widgets.EVENT"
        const val EXTRA_PROJECT_ID = "widgetProjectId"
        const val EXTRA_PARTICIPANT_ID = "widgetParticipantId"

        fun refreshAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, TimerWidgetProvider::class.java))
            if (ids.isEmpty()) return
            val container = (context.applicationContext as TimeTrackerApplication).container
            val projects = container.projects.list(archived = false)
            val project = projects.firstOrNull { container.projects.activeTimerCount(it.first) > 0 } ?: projects.firstOrNull()
            for (widgetId in ids) {
                val views = RemoteViews(context.packageName, R.layout.widget_timers)
                views.removeAllViews(R.id.widget_participants)
                if (project == null) {
                    views.setTextViewText(R.id.widget_title, context.getString(R.string.widget_no_projects))
                } else {
                    views.setTextViewText(R.id.widget_title, project.second)
                    container.participants.participantIds(project.first).forEach { person ->
                        val snapshot = container.participants.timerCardSnapshot(project.first, person, "")
                        val row = RemoteViews(context.packageName, R.layout.widget_timer_participant)
                        row.setTextViewText(R.id.widget_participant_name, snapshot.participantName)
                        row.setTextViewText(R.id.widget_timer_action, if (snapshot.activeStartedAt == null) "▶ START" else "■ STOP")
                        row.setViewVisibility(R.id.widget_event_action, if (snapshot.activeStartedAt == null) View.GONE else View.VISIBLE)
                        row.setOnClickPendingIntent(R.id.widget_timer_action,
                            createLaunchIntent(context, project.first, person, ACTION_TIMER))
                        row.setOnClickPendingIntent(R.id.widget_event_action,
                            createLaunchIntent(context, project.first, person, ACTION_EVENT))
                        views.addView(R.id.widget_participants, row)
                    }
                }
                manager.updateAppWidget(widgetId, views)
            }
        }

        private fun createLaunchIntent(context: Context, projectId: Long, participantId: Int, action: String): PendingIntent {
            val requestCode = (projectId * 100000 + participantId * 2 + if (action == ACTION_EVENT) 1 else 0).toInt()
            val intent = Intent(context, MainActivity::class.java).apply {
                this.action = action
                putExtra(EXTRA_PROJECT_ID, projectId)
                putExtra(EXTRA_PARTICIPANT_ID, participantId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            return PendingIntent.getActivity(context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
    }
}
