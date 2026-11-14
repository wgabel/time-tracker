package pl.wgabel.timetracker

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

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
    private val dp: (Int) -> Int
) : LinearLayout(context) {
    val mainRow = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(12), dp(10), dp(10), dp(10))
        background = rowBackground
    }

    init {
        orientation = VERTICAL
        val left = LinearLayout(context).apply { orientation = VERTICAL }
        if (historyMode) {
            mainRow.addView(avatar, LayoutParams(dp(38), dp(38)).apply {
                rightMargin = dp(10)
            })
            left.addView(participantName)
            gap(left, 4)
            left.addView(rangeLabel)
        } else {
            val heading = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            heading.addView(avatar, LayoutParams(dp(29), dp(29)))
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
                LayoutParams(-2, -2).apply { rightMargin = dp(6) })
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
            setPadding(dp(12), 0, dp(12), 0)
            background = trayBackground
            clipChildren = true
        }
        tray.addView(addEvent, LayoutParams(dp(52), dp(48)))
        tray.addView(View(context), LayoutParams(dp(12), dp(1)))
        tray.addView(edit, LayoutParams(dp(52), dp(48)))
        tray.addView(View(context), LayoutParams(dp(12), dp(1)))
        if (delete != null) tray.addView(delete, LayoutParams(dp(52), dp(48)))
        arrow.gravity = Gravity.CENTER
        mainRow.addView(arrow, LayoutParams(dp(20), dp(48)))
        addView(tray, LayoutParams(-1, 0))
        HistoryActionTray(
            mainRow, tray, arrow, HistoryActionTray.Dimension.HEIGHT,
            expandedSizePx = dp(64), slideOffsetPx = dp(36)
        ).attach(description)
    }
}
