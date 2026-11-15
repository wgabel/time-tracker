package pl.wgabel.timetracker.widgets

import android.app.Activity
import android.app.AlertDialog
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import pl.wgabel.timetracker.TimeTrackerApplication

/** The widget header opens a native single-choice project picker. */
class WidgetProjectSelectionActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        val projects = (application as TimeTrackerApplication).container.projects.list(archived = false)
        if (projects.isEmpty()) {
            Toast.makeText(this, "Brak aktywnych projektów", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        val selected = WidgetProjectPreferences(this).projectId(widgetId)
        val initialIndex = projects.indexOfFirst { it.first == selected }.coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle("Wybierz projekt")
            .setSingleChoiceItems(projects.map { it.second }.toTypedArray(), initialIndex) { dialog, index ->
                WidgetProjectPreferences(this).setProjectId(widgetId, projects[index].first)
                TimerWidgetProvider.refreshAllWidgets(this)
                setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
                dialog.dismiss()
                finish()
            }
            .setNegativeButton(android.R.string.cancel) { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }
}
