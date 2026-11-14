package pl.wgabel.timetracker.features.projects

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import pl.wgabel.timetracker.AppContainer
import pl.wgabel.timetracker.AppNavigation
import pl.wgabel.timetracker.R
import pl.wgabel.timetracker.design.AppUi.ButtonRole
import pl.wgabel.timetracker.ui.ScreenUi

internal class ProjectsScreen(
    context: Context, container: AppContainer, navigation: AppNavigation
) : ScreenUi(context, container) {
    override val navigator = navigation
    var showArchived = false
    private fun createProject() {
        val input = EditText(this).apply { hint = getString(R.string.project_name_hint); setSingleLine(true) }
        val holder = LinearLayout(this).apply { setPadding(densityPixels(20), 0, densityPixels(20), 0); addView(input) }
        val dialog = AlertDialog.Builder(this).setTitle(getString(R.string.new_project)).setView(holder)
            .setNegativeButton(getString(R.string.cancel), null).setPositiveButton(getString(R.string.add), null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = input.text.toString().trim()
                if (name.isEmpty()) { input.error = getString(R.string.name_required); return@setOnClickListener }
                val id = projectRepository.create(name)
                if (id > 0) { navigator.openProject(id); dialog.dismiss() }
            }
        }
        dialog.show()
    }
    fun renderProjects() {
        val heading = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        heading.addView(label(if (showArchived) getString(R.string.archived_projects) else getString(R.string.projects), 24f, true))
        if (!showArchived) {
            val addButton = styledAction("＋", ButtonRole.PRIMARY) { createProject() }.apply {
                textSize = 24f
                contentDescription = getString(R.string.add_project)
                minHeight = densityPixels(42)
                minimumHeight = densityPixels(42)
                setPadding(0, 0, 0, 0)
            }
            heading.addView(addButton, LinearLayout.LayoutParams(densityPixels(42), densityPixels(42)).apply {
                setMargins(densityPixels(12), 0, 0, 0)
            })
        }
        content.addView(heading)
        gap(content, 14)
        val projects = projectRepository.list(showArchived)
        if (projects.isEmpty()) {
            content.addView(label(getString(R.string.no_projects), 16f, false, muted))
            gap(content, 18)
        }
        projects.forEach { (id, name) ->
            val item = card()
            val counts = projectRepository.participantAndActiveTimerCounts(id)
            val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            line.addView(projectIcon(id))
            line.addView(label(name, 19f, true).apply {
                setPadding(densityPixels(12), 0, densityPixels(6), 0)
                setSingleLine(true)
                ellipsize = android.text.TextUtils.TruncateAt.END
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            if (counts.second > 0) {
                line.addView(label("● ${counts.second} " + getString(R.string.active_suffix).trim(), 12f, true, green).apply {
                    setPadding(densityPixels(8), densityPixels(7), densityPixels(8), densityPixels(7))
                    background = GradientDrawable().apply {
                        setColor(if (darkTheme) Color.rgb(35, 71, 54) else Color.rgb(230, 241, 235))
                        cornerRadius = densityPixels(16).toFloat()
                    }
                })
            }
            line.addView(label("›", 23f, false, muted).apply { setPadding(densityPixels(7), 0, 0, 0) })
            item.addView(line)
            val footer = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            if (showArchived) footer.addView(label(getString(R.string.archived), 11f, true, Color.rgb(166, 99, 34)))
            footer.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
            footer.addView(label("${counts.first} " + getString(R.string.participants_count), 12f, false, muted))
            gap(item, 7)
            item.addView(footer)
            item.setOnClickListener {
                navigator.openProject(id)
            }
            addCard(item)
        }
        if (showArchived) {
            content.addView(styledAction(getString(R.string.back_projects), ButtonRole.SECONDARY) {
                navigator.openProjects(false)
            })
        } else {
            content.addView(styledAction(getString(R.string.archived_link), ButtonRole.WARNING) {
                navigator.openProjects(true)
            })
            gap(content, 18)
            content.addView(styledAction("⚙  " + getString(R.string.settings), ButtonRole.SECONDARY) {
                navigator.openAppSettings()
            })
        }
    }
}
