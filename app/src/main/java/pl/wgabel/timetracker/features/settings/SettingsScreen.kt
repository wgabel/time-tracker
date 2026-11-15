package pl.wgabel.timetracker.features.settings

import android.animation.ValueAnimator
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import pl.wgabel.timetracker.AppContainer
import pl.wgabel.timetracker.AppNavigation
import pl.wgabel.timetracker.R
import pl.wgabel.timetracker.design.AppUi.ButtonRole
import pl.wgabel.timetracker.features.projects.ParticipantDialogs
import pl.wgabel.timetracker.settings.LanguageService
import pl.wgabel.timetracker.settings.ThemeService
import pl.wgabel.timetracker.ui.ScreenUi

internal class SettingsScreen(
    context: Context, container: AppContainer, navigation: AppNavigation,
    private val languageService: LanguageService,
    private val themeService: ThemeService
) : ScreenUi(context, container) {
    override val navigator = navigation
    private val participantsEditor = ParticipantDialogs(context, container, navigation)
    fun renderAppSettings() {
        content.addView(styledAction(getString(R.string.back_projects), ButtonRole.SECONDARY) {
            navigator.openProjects()
        })
        gap(content, 16)
        val app = card()
        app.addView(label(getString(R.string.application), 19f, true))
        gap(app, 12)
        app.addView(styledAction("🌐  " + getString(R.string.language), ButtonRole.SECONDARY) { languageService.selectLanguage() })
        gap(app, 10)
        app.addView(styledAction("◐  " + getString(R.string.theme), ButtonRole.SECONDARY) { themeService.selectTheme() })
        gap(app, 12)
        val version = try { packageManager.getPackageInfo(packageName, 0).versionName ?: "—" } catch (_: Exception) { "—" }
        app.addView(label(getString(R.string.version) + "v" + version, 14f, false, muted))
        gap(app, 12)
        app.addView(label(getString(R.string.local_data), 13f, false, muted))
        addCard(app)
    }

    private fun regenerateProjectIcon() {
        if (isArchived()) return
        projectRepository.regenerateIcon(requireNotNull(projectId))
        navigator.refreshContent()
    }

    private fun archiveProject() {
        if (isArchived()) return
        val count = projectRepository.activeTimerCount(requireNotNull(projectId))
        if (count > 0) {
            AlertDialog.Builder(this).setTitle(getString(R.string.active_timers))
                .setMessage(getString(R.string.stop_before_archive))
                .setPositiveButton(android.R.string.ok, null).show()
            return
        }
        AlertDialog.Builder(this).setTitle(getString(R.string.archive_confirm))
            .setMessage(getString(R.string.archive_description))
            .setNegativeButton(getString(R.string.cancel), null)
            .setPositiveButton(getString(R.string.archive_verb)) { _, _ ->
                if (!projectRepository.archiveIfNoActiveTimers(requireNotNull(projectId))) return@setPositiveButton
                navigator.openProjects()
            }.show()
    }

    private fun renameProject() {
        val id = projectId ?: return
        val input = EditText(this).apply { setText(projectName()); setSingleLine(true); selectAll() }
        AlertDialog.Builder(this).setTitle(getString(R.string.rename_project_title)).setView(input)
            .setNegativeButton(getString(R.string.cancel), null).setPositiveButton(getString(R.string.save)) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    projectRepository.rename(id, name)
                    navigator.refreshContent()
                }
            }.show()
    }

    fun renderSettings() {
        if (isArchived()) {
            content.addView(label(getString(R.string.archive_readonly), 23f, true, Color.rgb(151, 87, 31)))
            gap(content, 12)
            val info = card()
            info.addView(label(getString(R.string.archived_project), 19f, true))
            gap(info, 8)
            info.addView(label(projectName(), 17f, false))
            gap(info, 10)
            info.addView(label(getString(R.string.participants_colon) + participants().joinToString(", ") { personName(it) }, 14f, false, muted))
            gap(info, 10)
            info.addView(label(getString(R.string.locked_data), 14f, false, muted))
            addCard(info)
            addBackupActions(content)
            return
        }
        val project = card()
        // The project name already appears in the persistent app header.
        project.addView(styledAction(getString(R.string.rename_project), ButtonRole.SECONDARY) {
            renameProject()
        })
        gap(project, 14)
        val iconRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        iconRow.addView(projectIcon(projectId!!, 60))
        iconRow.addView(label(getString(R.string.project_icon), 16f, true).apply { setPadding(densityPixels(14), 0, 0, 0) })
        project.addView(iconRow)
        gap(project, 10)
        project.addView(styledAction(getString(R.string.regenerate_icon), ButtonRole.SECONDARY) {
            regenerateProjectIcon()
        })
        addCard(project)

        val people = card()
        val projectParticipants = participants()
        people.addView(label(getString(R.string.participants_label) + projectParticipants.size, 19f, true))
        gap(people, 10)
        projectParticipants.forEach { person ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = densityPixels(52)
            }
            row.addView(avatar(person), LinearLayout.LayoutParams(densityPixels(36), densityPixels(36)))
            row.addView(label(personName(person), 15f, true).apply {
                setPadding(densityPixels(10), 0, densityPixels(4), 0)
                setSingleLine(true)
                ellipsize = android.text.TextUtils.TruncateAt.END
            }, LinearLayout.LayoutParams(0, -2, 1f))
            val arrow = label("›", 23f, false, muted).apply { gravity = Gravity.CENTER }
            row.addView(arrow, LinearLayout.LayoutParams(densityPixels(20), densityPixels(48)))
            val tray = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                clipChildren = true
            }
            tray.addView(label("●", 24f, true, personColor(person)).apply {
                gravity = Gravity.CENTER
                contentDescription = "Color"
                setOnClickListener { participantsEditor.editParticipantColor(person) }
            }, LinearLayout.LayoutParams(densityPixels(42), densityPixels(48)))
            tray.addView(editIconButton { participantsEditor.renameParticipant(person) },
                LinearLayout.LayoutParams(densityPixels(42), densityPixels(48)))
            tray.addView(deleteIconButton { participantsEditor.removeParticipant(person) },
                LinearLayout.LayoutParams(densityPixels(42), densityPixels(48)))
            row.addView(tray, LinearLayout.LayoutParams(0, densityPixels(48)))
            var expanded = false
            var animation: ValueAnimator? = null
            row.isClickable = true
            row.isFocusable = true
            row.contentDescription = personName(person) +
                ". Dotknij, aby pokazać lub ukryć akcje uczestnika"
            row.setOnClickListener {
                expanded = !expanded
                animation?.cancel()
                val currentWidth = tray.layoutParams.width.coerceAtLeast(0)
                val targetWidth = if (expanded) densityPixels(126) else 0
                if (expanded && currentWidth == 0) {
                    tray.translationX = densityPixels(36).toFloat()
                    tray.alpha = 0f
                }
                val fromOffset = tray.translationX
                val fromAlpha = tray.alpha
                val fromArrowRotation = arrow.rotation
                animation = ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = 260L
                    interpolator = android.view.animation.PathInterpolator(0.4f, 0f, 0.2f, 1f)
                    addUpdateListener { animator ->
                        val fraction = animator.animatedValue as Float
                        tray.layoutParams = tray.layoutParams.apply {
                            width = currentWidth + ((targetWidth - currentWidth) * fraction).toInt()
                        }
                        tray.translationX = fromOffset * (1f - fraction) +
                            (if (expanded) 0f else densityPixels(36).toFloat()) * fraction
                        tray.alpha = fromAlpha + ((if (expanded) 1f else 0f) - fromAlpha) * fraction
                        arrow.rotation = fromArrowRotation + ((if (expanded) 90f else 0f) - fromArrowRotation) * fraction
                    }
                    start()
                }
            }
            people.addView(row)
            gap(people, 7)
        }
        addCard(people)

        val data = card()
        data.addView(label(getString(R.string.project_data), 19f, true))
        gap(data, 12)
        addBackupActions(data)
        gap(data, 8)
        data.addView(styledAction(getString(R.string.archive_project), ButtonRole.WARNING) { archiveProject() })
        addCard(data)


    }

    private fun addBackupActions(parent: LinearLayout) {
        parent.addView(styledAction(getString(R.string.export_json), ButtonRole.SECONDARY) {
            val name = projectName().replace(Regex("[^a-zA-Z0-9_-]"), "_")
            navigator.exportProject(requireNotNull(projectId), "timetrkr_${name}.json")
        })
        gap(parent, 8)
        parent.addView(styledAction(getString(R.string.import_json), ButtonRole.SECONDARY) {
            navigator.importProject(requireNotNull(projectId))
        })
    }
}
