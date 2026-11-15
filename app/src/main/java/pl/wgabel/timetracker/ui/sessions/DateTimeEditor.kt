package pl.wgabel.timetracker.ui.sessions

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Window
import android.widget.ScrollView
import pl.wgabel.timetracker.design.AppUi
import pl.wgabel.timetracker.R
import pl.wgabel.timetracker.time.SessionTimeSelection

/** The shared, themed time dialog. Selection and validation belong to its content/state. */
internal class DateTimeEditor(private val context: Context, private val components: AppUi) {
    data class Config(
        val title: String,
        val initial: Long,
        val min: Long? = null,
        val max: Long? = null,
        val seconds: Boolean = false,
        val quickOffsets: Boolean = true
    )

    fun show(config: Config, onSave: (Long) -> Boolean): Dialog = showSelection(config, null, onSave)

    fun editCompletedSession(started: Long, ended: Long, onSave: (Long, Long) -> Boolean): Dialog {
        val session = SessionTimeSelection(started, ended)
        return showSelection(Config(context.getString(R.string.session_time_edit), started,
            max = System.currentTimeMillis()), session) {
            val start = session.start.timestamp
            val end = session.end.timestamp
            start != null && end != null && onSave(start, end)
        }
    }

    private fun showSelection(config: Config, session: SessionTimeSelection?, onSave: (Long) -> Boolean): Dialog {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val content = TimeSelectionView(context, components, config, onSave, { dialog.dismiss() }, session)
        val scroll = ScrollView(context).apply {
            isFillViewport = false
            background = components.shape(components.color("surface"), components.dimension("cardRadiusDp"))
            clipToOutline = true
            addView(content)
        }
        dialog.setContentView(scroll)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.show()
        dialog.window?.setLayout(
            minOf(context.resources.displayMetrics.widthPixels - components.densityPixels(32), components.densityPixels(440)),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
        return dialog
    }
}
