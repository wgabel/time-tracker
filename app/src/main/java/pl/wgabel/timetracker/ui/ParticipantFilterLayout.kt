package pl.wgabel.timetracker.ui

import android.content.Context
import android.view.View
import android.view.ViewGroup
import pl.wgabel.timetracker.design.DesignTokens
import kotlin.math.roundToInt

/** Wraps buttons using the actual space inside the parent card, including in split-screen. */
internal class ParticipantFilterLayout(context: Context, tokens: DesignTokens) : ViewGroup(context) {
    private fun pixels(value: Int) = (value * resources.displayMetrics.density).roundToInt()
    private val minimumWidthPx = pixels(tokens.dimension("participantFilterMinWidthDp"))
    private val buttonHeight = pixels(tokens.dimension("participantFilterHeightDp"))
    private val gap = pixels(tokens.dimension("participantFilterGapDp"))

    fun addOption(button: View, desiredWidth: Int) {
        addView(button, LayoutParams(maxOf(minimumWidthPx, desiredWidth), buttonHeight))
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        var occupied = 0
        var rows = if (childCount == 0) 0 else 1
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            val childWidth = minOf(width, child.layoutParams.width)
            child.measure(MeasureSpec.makeMeasureSpec(childWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(buttonHeight, MeasureSpec.EXACTLY))
            if (occupied > 0 && occupied + childWidth > width) {
                rows++
                occupied = 0
            }
            occupied += childWidth + gap
        }
        setMeasuredDimension(width, resolveSize(rows * (buttonHeight + gap), heightMeasureSpec))
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        var x = 0
        var y = 0
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            if (x > 0 && x + child.measuredWidth > width) {
                x = 0
                y += buttonHeight + gap
            }
            val start = if (layoutDirection == LAYOUT_DIRECTION_RTL) width - x - child.measuredWidth else x
            child.layout(start, y, start + child.measuredWidth, y + buttonHeight)
            x += child.measuredWidth + gap
        }
    }
}
