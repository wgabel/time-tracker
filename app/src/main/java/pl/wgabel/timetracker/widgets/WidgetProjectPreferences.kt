package pl.wgabel.timetracker.widgets

import android.content.Context

/** Each widget instance keeps its own project, independently of other widgets. */
internal class WidgetProjectPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("widget_projects", Context.MODE_PRIVATE)

    fun projectId(widgetId: Int): Long? =
        preferences.getLong("project_$widgetId", -1L).takeIf { it > 0 }

    fun setProjectId(widgetId: Int, projectId: Long) {
        preferences.edit().putLong("project_$widgetId", projectId).apply()
    }

    fun remove(widgetId: Int) {
        preferences.edit().remove("project_$widgetId").apply()
    }
}
