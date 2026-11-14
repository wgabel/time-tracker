package pl.wgabel.timetracker.ui.sessions

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Window
import android.widget.ScrollView
import pl.wgabel.timetracker.design.AppUi

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

    fun show(config: Config, onSave: (Long) -> Boolean): Dialog {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val content = TimeSelectionView(context, components, config, onSave) { dialog.dismiss() }
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
