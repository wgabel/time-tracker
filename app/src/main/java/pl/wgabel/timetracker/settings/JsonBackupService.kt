package pl.wgabel.timetracker.settings

import android.content.Context
import android.net.Uri
import pl.wgabel.timetracker.data.ProjectBackupRepository

internal class JsonBackupService(
    private val context: Context,
    private val repository: ProjectBackupRepository
) {
    fun exportProject(projectId: Long, destination: Uri) {
        val contents = repository.exportProject(projectId)
        context.contentResolver.openOutputStream(destination)?.bufferedWriter(Charsets.UTF_8)?.use {
            it.write(contents)
        } ?: error("Cannot open backup destination")
    }

    fun readBackup(source: Uri): String =
        context.contentResolver.openInputStream(source)?.bufferedReader(Charsets.UTF_8)?.use {
            it.readText()
        } ?: error("Cannot open backup file")

    fun validateBackup(contents: String) = repository.validate(contents)

    fun restoreProject(projectId: Long, contents: String) = repository.restoreProject(projectId, contents)
}
