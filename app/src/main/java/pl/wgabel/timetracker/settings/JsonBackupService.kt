package pl.wgabel.timetracker.settings

import android.content.Context
import android.net.Uri
import pl.wgabel.timetracker.data.ProjectBackupRepository

internal class JsonBackupService(
    private val context: Context,
    private val repository: ProjectBackupRepository
) {
    fun prepareExport(projectId: Long): String = repository.exportProject(projectId)

    fun writeExport(contents: String, destination: Uri) {
        require(contents.isNotBlank()) { "Empty project backup" }
        val output = context.contentResolver.openOutputStream(destination, "wt")
            ?: error("Cannot open backup destination")
        output.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write(contents)
            writer.flush()
        }
    }

    fun readBackup(source: Uri): String =
        context.contentResolver.openInputStream(source)?.bufferedReader(Charsets.UTF_8)?.use {
            it.readText()
        } ?: error("Cannot open backup file")

    fun validateBackup(contents: String) = repository.validate(contents)

    fun restoreProject(projectId: Long, contents: String) = repository.restoreProject(projectId, contents)
}
