package pl.wgabel.timetracker.ui.sessions

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import pl.wgabel.timetracker.ui.HistoryActionTray

/** Layout for one session in history or the recent-activity feed. */
internal class HistorySessionRow(
    context: Context,
    private val historyMode: Boolean,
    rowBackground: android.graphics.drawable.Drawable,
    avatar: View,
    participantName: TextView,
    dateLabel: TextView?,
    rangeLabel: TextView,
    elapsedLabel: TextView,
    private val gap: (LinearLayout, Int) -> Unit,
    private val densityPixels: (Int) -> Int
) : LinearLayout(context) {
    val mainRow = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(densityPixels(12), densityPixels(10), densityPixels(10), densityPixels(10))
        background = rowBackground
    }

    init {
        orientation = VERTICAL
        val left = LinearLayout(context).apply { orientation = VERTICAL }
        if (historyMode) {
            mainRow.addView(avatar, LayoutParams(densityPixels(38), densityPixels(38)).apply {
                rightMargin = densityPixels(10)
            })
            left.addView(participantName)
            gap(left, 4)
            left.addView(rangeLabel)
        } else {
            val heading = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            heading.addView(avatar, LayoutParams(densityPixels(29), densityPixels(29)))
            if (dateLabel != null) heading.addView(dateLabel)
            left.addView(heading)
            gap(left, 5)
            left.addView(rangeLabel)
            gap(left, 3)
            left.addView(elapsedLabel)
        }
        mainRow.addView(left, LayoutParams(0, -2, 1f))
        if (historyMode) {
            mainRow.addView(elapsedLabel.apply { setSingleLine(true) },
                LayoutParams(-2, -2).apply { rightMargin = densityPixels(6) })
        }
        addView(mainRow)
    }

    fun attachActions(
        trayBackground: android.graphics.drawable.Drawable,
        addEvent: View,
        edit: View,
        delete: View?,
        arrow: TextView,
        description: String
    ) {
        val tray = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL or Gravity.RIGHT
            setPadding(densityPixels(12), 0, densityPixels(12), 0)
            background = trayBackground
            clipChildren = true
        }
        tray.addView(addEvent, LayoutParams(densityPixels(52), densityPixels(48)))
        tray.addView(View(context), LayoutParams(densityPixels(12), densityPixels(1)))
        tray.addView(edit, LayoutParams(densityPixels(52), densityPixels(48)))
        tray.addView(View(context), LayoutParams(densityPixels(12), densityPixels(1)))
        if (delete != null) tray.addView(delete, LayoutParams(densityPixels(52), densityPixels(48)))
        arrow.gravity = Gravity.CENTER
        mainRow.addView(arrow, LayoutParams(densityPixels(20), densityPixels(48)))
        addView(tray, LayoutParams(-1, 0))
        HistoryActionTray(
            mainRow, tray, arrow, HistoryActionTray.Dimension.HEIGHT,
            expandedSizePx = densityPixels(64), slideOffsetPx = densityPixels(36)
        ).attach(description)
    }
}
