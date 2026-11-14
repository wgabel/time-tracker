package pl.wgabel.timetracker.settings

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.widget.Toast
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import pl.wgabel.timetracker.R
import pl.wgabel.timetracker.data.SessionRepository

/** Session CSV format is unchanged; export remains separate from a complete database backup. */
internal class CsvExportService(context: Context, private val sessions: SessionRepository) : ContextWrapper(context) {
    fun exportCsv(uri: Uri, id: Long) {
        fun escapeCsvField(value: String) = "\"" + value.replace("\"", "\"\"") + "\""
        try {
            contentResolver.openOutputStream(uri)?.use { output ->
                OutputStreamWriter(output, Charsets.UTF_8).use { writer ->
                    writer.write("\uFEFF")
                    writer.write(getString(R.string.csv_header) + "\n")
                    val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT)
                    sessions.exportSessions(id).forEach { row ->
                            val name = row.name
                            val start = row.start
                            val end = row.end
                            val date = fmt.format(Date(start))
                            val endDate = end?.let { fmt.format(Date(it)) } ?: ""
                            val secs = ((end ?: System.currentTimeMillis()) - start).coerceAtLeast(0) / 1000
                            writer.write(listOf(escapeCsvField(name), escapeCsvField(date.take(10)), escapeCsvField(date),
                                escapeCsvField(endDate), secs.toString(), if (end == null) getString(R.string.csv_running) else getString(R.string.csv_finished)).joinToString(";") + "\n")
                    }
                }
            } ?: throw IllegalStateException(getString(R.string.export_file_error))
            Toast.makeText(this, getString(R.string.export_done), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.export_failed_prefix) + e.message, Toast.LENGTH_LONG).show()
        }
    }
}
