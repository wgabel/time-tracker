package pl.wgabel.timetracker.tabs

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.widget.LinearLayout
import pl.wgabel.timetracker.design.AppUi

/** Infrastructure shared by the timer screen and its cards. */
internal class TimersScreenEnvironment(
    val context: Context,
    val database: () -> SQLiteDatabase,
    val projectId: () -> Long,
    val archived: () -> Boolean,
    val components: AppUi
)

/** Screen-specific presentation and navigation boundaries. */
internal class TimersScreenView(
    val content: () -> LinearLayout,
    val title: () -> String,
    val emptyMessage: () -> String,
    val addButtonDescription: () -> String,
    val onAddParticipant: () -> Unit,
    val recentActivityRenderer: RecentActivityRenderer
)
