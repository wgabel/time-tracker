package pl.wgabel.timetracker

import android.app.Activity
import android.app.AlertDialog
import android.widget.Toast
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import pl.wgabel.timetracker.features.history.HistoryScreen
import pl.wgabel.timetracker.features.projects.ProjectsScreen
import pl.wgabel.timetracker.features.settings.SettingsScreen
import pl.wgabel.timetracker.features.statistics.StatisticsScreen
import pl.wgabel.timetracker.features.timers.TimersScreen
import pl.wgabel.timetracker.settings.JsonBackupService
import pl.wgabel.timetracker.settings.LanguageService
import pl.wgabel.timetracker.settings.ThemeService
import pl.wgabel.timetracker.ui.ScreenUi
import pl.wgabel.timetracker.ui.calendar.DayNavigator

/** App navigation and persistent chrome. Features own their views, state, dialogs and actions. */
internal class AppCoordinator(
    context: Context, container: AppContainer,
    language: LanguageService, theme: ThemeService,
    private val launchDocument: (Intent, Int) -> Unit
) : ScreenUi(context, container), AppNavigation {
    override val navigator: AppNavigation get() = this
    override var projectId: Long? = null
        private set
    private var tab = 0
    private var showAppSettings = false
    private var pendingExportProject: Long? = null
    private var pendingImportProject: Long? = null
    private lateinit var navigation: LinearLayout
    private lateinit var topHeader: LinearLayout
    private lateinit var stickyDateContainer: LinearLayout
    private val calendar = DayNavigator(context, container, this)
    private val projects = ProjectsScreen(context, container, this)
    private val timers = TimersScreen(context, container, this)
    private val history = HistoryScreen(context, container, this).also { it.calendar = calendar }
    private val statistics = StatisticsScreen(context, container, this).also { it.calendar = calendar }
    private val settings = SettingsScreen(context, container, this, language, theme)
    private val jsonBackup = JsonBackupService(context, container.projectBackups)
    val root: View = createRootView()

    private fun createRootView(): View {
        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(themeColor("headerBackground"))
        }
        topHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(themeColor("headerBackground"))
            setPadding(densityPixels(12), densityPixels(7), densityPixels(12), densityPixels(7))
            minimumHeight = densityPixels(54)
        }
        outer.addView(topHeader)
        // Android 15+ may lay out the app edge-to-edge even when older target
        // versions did not. Reserve the actual system-bar insets, not a fixed dp.
        if (Build.VERSION.SDK_INT >= 20) {
            outer.setOnApplyWindowInsetsListener { view, insets ->
                val top = insets.systemWindowInsetTop
                val bottom = insets.systemWindowInsetBottom
                view.setPadding(0, top, 0, bottom)
                insets
            }
            outer.requestApplyInsets()
        }
        stickyDateContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(densityPixels(16), 0, densityPixels(16), densityPixels(5))
            setBackgroundColor(if (darkTheme) surface else darkHeader)
            visibility = View.GONE
            elevation = densityPixels(4).toFloat()
        }
        calendar.stickyDateContainer = stickyDateContainer
        outer.addView(stickyDateContainer)
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(this@AppCoordinator.background)
        }
        content.setPadding(0, 0, 0, 0)
        scroll.setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
            if (projectId != null && (tab == 1 || tab == 2) && kotlin.math.abs(scrollY - oldScrollY) > densityPixels(2)) {
                calendar.setCalendarHeadingExpanded(scrollY == 0 || scrollY < oldScrollY)
            }
        }
        scroll.addView(content)
        outer.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        navigation = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(if (darkTheme) surface else darkHeader)
            setPadding(densityPixels(8), densityPixels(5), densityPixels(8), densityPixels(7))
            elevation = densityPixels(7).toFloat()
        }
        outer.addView(navigation)
        return outer

    }

    override fun openProject(projectId: Long) {
        this.projectId = projectId
        tab = 0
        showAppSettings = false
        history.state.dayOffset = 0
        history.state.participantFilter = 0
        renderNavigation()
    }

    override fun openProjects(archived: Boolean) {
        projectId = null
        projects.showArchived = archived
        showAppSettings = false
        renderNavigation()
    }

    override fun openAppSettings() { showAppSettings = true; renderNavigation() }
    override fun openHistory() { tab = 2; renderNavigation() }
    override fun refreshContent() { renderTopHeader(); renderCurrentScreen() }
    override fun refreshNavigation() = renderNavigation()

    private fun renderCurrentScreen() {
        content.removeAllViews()
        calendar.clearHeading()
        stickyDateContainer.removeAllViews()
        stickyDateContainer.visibility = if (projectId != null && (tab == 1 || tab == 2)) View.VISIBLE else View.GONE
        stickyDateContainer.setBackgroundColor(if (darkTheme) surface else darkHeader)
        val screen = if (projectId == null) {
            if (showAppSettings) settings else projects
        } else when (tab) {
            0 -> timers
            1 -> statistics
            2 -> history
            else -> settings
        }
        val target = screen.content
        target.removeAllViews()
        val archived = isArchived()
        val contentColor = if (archived) if (darkTheme) Color.rgb(53, 41, 34) else Color.rgb(252, 239, 219) else background
        target.setBackgroundColor(contentColor)
        content.setBackgroundColor(contentColor)
        if (archived) {
            target.addView(label(getString(R.string.archive_banner), 13f, true, Color.rgb(147, 82, 27)))
            gap(target, 7)
        }
        content.addView(target, LinearLayout.LayoutParams(-1, -2))
        if (projectId == null) {
            if (showAppSettings) settings.renderAppSettings() else projects.renderProjects()
        } else when (tab) {
            0 -> timers.renderTimers()
            1 -> statistics.renderStatistics()
            2 -> history.renderHistory()
            else -> settings.renderSettings()
        }
    }

    fun updateTimers(now: Long) {
        if (tab == 0 && projectId != null) timers.updateTimers(now)
    }

    override fun exportProject(projectId: Long, suggestedName: String) {
        pendingExportProject = projectId
        launchDocument(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, suggestedName)
        }, 301)
    }

    override fun importProject(projectId: Long) {
        pendingImportProject = projectId
        launchDocument(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }, 302)
    }

    fun completeExport(resultCode: Int, data: Intent?) {
        val project = pendingExportProject
        pendingExportProject = null
        if (resultCode == Activity.RESULT_OK && project != null) data?.data?.let { uri ->
            try {
                jsonBackup.exportProject(project, uri)
                Toast.makeText(this, getString(R.string.backup_export_done), Toast.LENGTH_SHORT).show()
            } catch (error: Exception) {
                Toast.makeText(this, getString(R.string.backup_failed, error.message ?: ""), Toast.LENGTH_LONG).show()
            }
        }
    }

    fun completeImport(resultCode: Int, data: Intent?) {
        val project = pendingImportProject
        pendingImportProject = null
        if (resultCode != Activity.RESULT_OK || project == null) return
        val uri = data?.data ?: return
        try {
            val contents = jsonBackup.readBackup(uri)
            val preview = jsonBackup.validateBackup(contents)
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.backup_import_title))
                .setMessage(getString(R.string.backup_import_confirm, preview.projectName, preview.participants, preview.sessions, preview.events))
                .setNegativeButton(getString(R.string.cancel), null)
                .setPositiveButton(getString(R.string.backup_replace)) { _, _ ->
                    try {
                        jsonBackup.restoreProject(project, contents)
                        container.notifications.synchronizeTimerNotifications()
                        refreshContent()
                        Toast.makeText(this, getString(R.string.backup_import_done), Toast.LENGTH_SHORT).show()
                    } catch (error: Exception) {
                        Toast.makeText(this, getString(R.string.backup_failed, error.message ?: ""), Toast.LENGTH_LONG).show()
                    }
                }.show()
        } catch (error: Exception) {
            Toast.makeText(this, getString(R.string.backup_failed, error.message ?: ""), Toast.LENGTH_LONG).show()
        }
    }

    fun restoreState(state: Bundle?) {
        if (state != null) {
            projectId = state.getLong("projectId", -1).takeIf { it > 0 }
            tab = state.getInt("tab", 0).coerceIn(0, 3)
            projects.showArchived = state.getBoolean("showArchived")
            showAppSettings = state.getBoolean("showAppSettings")
            pendingExportProject = state.getLong("exportProject", -1).takeIf { it > 0 }
            pendingImportProject = state.getLong("importProject", -1).takeIf { it > 0 }
            history.state.dayOffset = state.getInt("historyDay")
            history.state.participantFilter = state.getInt("historyPerson")
            statistics.state.dayOffset = state.getInt("statisticsDay")
            statistics.state.period = state.getInt("statisticsPeriod", 1).coerceIn(0, 3)
            statistics.state.periodOffset = state.getInt("statisticsOffset")
        }
        renderNavigation()
    }

    fun saveState(state: Bundle) {
        state.putLong("projectId", projectId ?: -1)
        state.putInt("tab", tab)
        state.putBoolean("showArchived", projects.showArchived)
        state.putBoolean("showAppSettings", showAppSettings)
        state.putLong("exportProject", pendingExportProject ?: -1)
        state.putLong("importProject", pendingImportProject ?: -1)
        state.putInt("historyDay", history.state.dayOffset)
        state.putInt("historyPerson", history.state.participantFilter)
        state.putInt("statisticsDay", statistics.state.dayOffset)
        state.putInt("statisticsPeriod", statistics.state.period)
        state.putInt("statisticsOffset", statistics.state.periodOffset)
    }
    private fun activeCountText(count: Int): String {
        val language = resources.configuration.locales[0].language
        return when (language) {
            "pl" -> {
                val suffix = if (count == 1) "aktywny" else "aktywnych"
                "$count $suffix"
            }
            else -> "$count active"
        }
    }

    private fun renderTopHeader() {
        topHeader.removeAllViews()
        if (projectId == null) {
            topHeader.addView(label("◷  " + getString(R.string.app_title), 21f, true, Color.WHITE))
            return
        }
        val left = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        left.addView(label("‹", 29f, false, Color.WHITE).apply {
            gravity = Gravity.CENTER
            contentDescription = getString(R.string.back_to_projects_description)
            setOnClickListener { openProjects(projects.showArchived) }
        }, LinearLayout.LayoutParams(densityPixels(38), densityPixels(40)))
        left.addView(projectIcon(projectId!!, 32), LinearLayout.LayoutParams(densityPixels(32), densityPixels(32)))
        topHeader.addView(left)

        val details = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(densityPixels(12), 0, 0, 0)
        }
        details.addView(label(projectName(), 17f, true, Color.WHITE).apply {
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
        }, LinearLayout.LayoutParams(-1, -2))

        val activePeople = sessionRepository.activeParticipantIds(requireNotNull(projectId))
        if (activePeople.isNotEmpty()) {
            val status = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, densityPixels(3), 0, 0)
            }
            activePeople.forEach { person ->
                status.addView(View(this).apply {
                    background = shape(personColor(person), 4)
                }, LinearLayout.LayoutParams(densityPixels(7), densityPixels(7)).apply {
                    rightMargin = densityPixels(4)
                })
            }
            status.addView(label(activeCountText(activePeople.size), 12f, false,
                Color.rgb(185, 208, 205)).apply {
                setPadding(densityPixels(3), 0, 0, 0)
            })
            details.addView(status)
        }
        topHeader.addView(details, LinearLayout.LayoutParams(0, -2, 1f))
    }

    private fun renderNavigation() {
        renderTopHeader()
        renderCurrentScreen()
        navigation.removeAllViews()
        if (projectId == null) { navigation.visibility = View.GONE; return }
        navigation.visibility = View.VISIBLE
        listOf("◷\n" + getString(R.string.timers), "▥\n" + getString(R.string.statistics), "☷\n" + getString(R.string.history), "⚙\n" + getString(R.string.settings)).forEachIndexed { index, name ->
            val item = label(name, 12f, tab == index,
                if (darkTheme) { if (tab == index) blue else muted }
                else if (tab == index) Color.WHITE else Color.rgb(183, 204, 202)).apply {
                gravity = Gravity.CENTER
                setPadding(densityPixels(2), densityPixels(8), densityPixels(2), densityPixels(8))
                background = if (tab == index) shape(if (darkTheme) subtle else Color.rgb(44, 79, 83), 16)
                    else shape(Color.TRANSPARENT, 16)
                contentDescription = name.replace("\n", " ")
                setOnClickListener { tab = index; renderNavigation() }
            }
            navigation.addView(item, LinearLayout.LayoutParams(0, densityPixels(58), 1f).apply {
                leftMargin = densityPixels(3)
                rightMargin = densityPixels(3)
            })
        }
    }

    // A resumed Activity does not necessarily reattach its views, so restart
    // running-session backgrounds explicitly when returning from the background.
    fun updateActiveAnimations(view: View, running: Boolean) {
        (view.background as? android.graphics.drawable.Animatable)?.let { animation ->
            if (running) animation.start() else animation.stop()
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                updateActiveAnimations(view.getChildAt(index), running)
            }
        }
    }


    fun dispose() { calendar.clearHeading(); updateActiveAnimations(root, false) }
}
