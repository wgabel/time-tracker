package pl.wgabel.timetracker.widgets

import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import pl.wgabel.timetracker.R
import pl.wgabel.timetracker.TimeTrackerApplication

/** Supplies scrollable widget rows without inflating arbitrary views in the launcher. */
class WidgetParticipantListService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        ParticipantFactory(intent.getIntExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, -1))

    private inner class ParticipantFactory(private val widgetId: Int) : RemoteViewsFactory {
        private val container get() = (applicationContext as TimeTrackerApplication).container
        private var projectId: Long? = null
        private var participantIds: List<Int> = emptyList()

        override fun onCreate() = onDataSetChanged()

        override fun onDataSetChanged() {
            val selectedProjectId = WidgetProjectPreferences(applicationContext).projectId(widgetId)
            val validProject = container.projects.list(archived = false).any { it.first == selectedProjectId }
            projectId = selectedProjectId.takeIf { validProject }
            participantIds = projectId?.let { container.participants.participantIds(it) } ?: emptyList()
        }

        override fun onDestroy() { participantIds = emptyList() }
        override fun getCount(): Int = participantIds.size
        override fun getViewTypeCount(): Int = 1
        override fun hasStableIds(): Boolean = true
        override fun getItemId(position: Int): Long = participantIds[position].toLong()
        override fun getLoadingView(): RemoteViews? = null

        override fun getViewAt(position: Int): RemoteViews? {
            val selectedProjectId = projectId ?: return null
            val participantId = participantIds.getOrNull(position) ?: return null
            val snapshot = container.participants.timerCardSnapshot(selectedProjectId, participantId, "")
            val running = snapshot.activeStartedAt != null
            val row = RemoteViews(packageName, R.layout.widget_timer_participant)
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
                    SystemClock.elapsedRealtime() - (System.currentTimeMillis() - startedAt), "%s", true)
            }
            row.setInt(R.id.widget_start_action, "setImageAlpha", if (running) 80 else 255)
            row.setInt(R.id.widget_stop_action, "setImageAlpha", if (running) 255 else 80)
            row.setInt(R.id.widget_event_action, "setImageAlpha", if (running) 255 else 80)
            if (!running) row.setOnClickFillInIntent(R.id.widget_start_action,
                actionIntent(selectedProjectId, participantId, TimerWidgetProvider.ACTION_START))
            if (running) {
                row.setOnClickFillInIntent(R.id.widget_stop_action,
                    actionIntent(selectedProjectId, participantId, TimerWidgetProvider.ACTION_STOP))
                row.setOnClickFillInIntent(R.id.widget_event_action,
                    actionIntent(selectedProjectId, participantId, TimerWidgetProvider.ACTION_EVENT))
            }
            return row
        }

        private fun actionIntent(projectId: Long, participantId: Int, action: String): Intent =
            Intent().apply {
                this.action = action
                putExtra(TimerWidgetProvider.EXTRA_PROJECT_ID, projectId)
                putExtra(TimerWidgetProvider.EXTRA_PARTICIPANT_ID, participantId)
            }
    }
}
