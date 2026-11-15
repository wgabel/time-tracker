package pl.wgabel.timetracker.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.os.SystemClock
import android.view.View
import pl.wgabel.timetracker.MainActivity
import pl.wgabel.timetracker.R
import pl.wgabel.timetracker.TimeTrackerApplication

/** Launcher shortcuts: changes happen in the app through its existing services. */
class TimerWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, widgetIds: IntArray) {
        refreshAllWidgets(context)
    }

    override fun onDeleted(context: Context, widgetIds: IntArray) {
        val preferences = WidgetProjectPreferences(context)
        widgetIds.forEach(preferences::remove)
    }

    companion object {
        const val ACTION_START = "pl.wgabel.timetracker.widgets.START"
        const val ACTION_STOP = "pl.wgabel.timetracker.widgets.STOP"
        const val ACTION_EVENT = "pl.wgabel.timetracker.widgets.EVENT"
        const val EXTRA_PROJECT_ID = "widgetProjectId"
        const val EXTRA_PARTICIPANT_ID = "widgetParticipantId"

        fun refreshAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, TimerWidgetProvider::class.java))
            if (ids.isEmpty()) return
            val container = (context.applicationContext as TimeTrackerApplication).container
            val projects = container.projects.list(archived = false)
            val preferences = WidgetProjectPreferences(context)
            ids.forEach { widgetId ->
                val views = RemoteViews(context.packageName, R.layout.widget_timers)
                views.removeAllViews(R.id.widget_participants)
                val project = projects.firstOrNull { it.first == preferences.projectId(widgetId) }
                views.setTextViewText(R.id.widget_title, project?.second ?: context.getString(R.string.widget_select_project))
                val runningCount = project?.let { container.projects.activeTimerCount(it.first) } ?: 0
                views.setTextViewText(R.id.widget_active_count, if (runningCount == 1) "● 1 aktywny" else "● $runningCount aktywnych")
                val chooserIntent = Intent(context, WidgetProjectSelectionActivity::class.java).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                views.setOnClickPendingIntent(R.id.widget_project_selector, PendingIntent.getActivity(
                    context, widgetId, chooserIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                ))
                project?.let { (projectId, _) ->
                    container.participants.participantIds(projectId).forEach { participantId ->
                        val snapshot = container.participants.timerCardSnapshot(projectId, participantId, "")
                        val running = snapshot.activeStartedAt != null
                        val row = RemoteViews(context.packageName, R.layout.widget_timer_participant)
                        row.setTextViewText(R.id.widget_participant_name, snapshot.participantName)
                        row.setInt(R.id.widget_participant_color, "setBackgroundColor", snapshot.participantColor)
                        row.setInt(R.id.widget_participant_row, "setBackgroundResource",
                            if (running) R.drawable.widget_row_active else R.drawable.widget_row_background)
                        row.setInt(R.id.widget_stop_action, "setBackgroundResource",
                            if (running) R.drawable.widget_button_active else R.drawable.widget_button_secondary)
                        row.setViewVisibility(R.id.widget_timer_elapsed, if (running) View.VISIBLE else View.GONE)
                        row.setViewVisibility(R.id.widget_timer_idle, if (running) View.GONE else View.VISIBLE)
                        snapshot.activeStartedAt?.let { startedAt ->
                            row.setChronometer(R.id.widget_timer_elapsed,
                                SystemClock.elapsedRealtime() - (System.currentTimeMillis() - startedAt),
                                "%s", true)
                        }
                        row.setFloat(R.id.widget_start_action, "setAlpha", if (running) 0.3f else 1f)
                        row.setFloat(R.id.widget_stop_action, "setAlpha", if (running) 1f else 0.3f)
                        row.setFloat(R.id.widget_event_action, "setAlpha", if (running) 1f else 0.3f)
                        if (!running) row.setOnClickPendingIntent(R.id.widget_start_action,
                            launchIntent(context, widgetId, projectId, participantId, ACTION_START))
                        if (running) {
                            row.setOnClickPendingIntent(R.id.widget_stop_action,
                                launchIntent(context, widgetId, projectId, participantId, ACTION_STOP))
                            row.setOnClickPendingIntent(R.id.widget_event_action,
                                launchIntent(context, widgetId, projectId, participantId, ACTION_EVENT))
                        }
                        views.addView(R.id.widget_participants, row)
                    }
                }
                manager.updateAppWidget(widgetId, views)
            }
        }

        private fun launchIntent(context: Context, widgetId: Int, projectId: Long, participantId: Int, action: String): PendingIntent {
            val actionIndex = when (action) { ACTION_START -> 0; ACTION_STOP -> 1; else -> 2 }
            val intent = Intent(context, MainActivity::class.java).apply {
                this.action = action
                putExtra(EXTRA_PROJECT_ID, projectId)
                putExtra(EXTRA_PARTICIPANT_ID, participantId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                // Unique identity across widgets and projects; extras are not part of PendingIntent identity.
                data = android.net.Uri.parse("timetrkr://widget/$widgetId/$projectId/$participantId/$actionIndex")
            }
            return PendingIntent.getActivity(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
    }
}
