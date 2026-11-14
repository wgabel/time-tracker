package pl.wgabel.timetracker.tabs

import android.content.Context
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import pl.wgabel.timetracker.ParticipantColors
import java.util.Locale

/** Reusable participant color selector shared by creation and settings dialogs. */
internal class ParticipantColorPicker(
    private val context: Context,
    private val label: (String, Float, Boolean, Int) -> TextView
) {
    fun create(initial: Int, onSelected: (Int) -> Unit): LinearLayout {
        val container = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val swatches = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        var selectedColor = initial
        val preview = label("●", 30f, true, initial).apply { gravity = Gravity.CENTER }
        container.addView(preview)
        ParticipantColors.palette.toList().chunked(5).forEach { rowColors ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_HORIZONTAL
            }
            rowColors.forEach { color ->
                val swatch = label("●", 29f, true, color).apply {
                    gravity = Gravity.CENTER
                    contentDescription = String.format(Locale.ROOT, "#%06X", color and 0xFFFFFF)
                    setOnClickListener {
                        selectedColor = color
                        preview.setTextColor(selectedColor)
                        onSelected(selectedColor)
                    }
                }
                row.addView(swatch, LinearLayout.LayoutParams(0, dp(50), 1f))
            }
            swatches.addView(row)
        }
        container.addView(swatches)
        return container
    }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density + .5f).toInt()
}
