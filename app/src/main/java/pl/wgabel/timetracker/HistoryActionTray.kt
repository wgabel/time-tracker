package pl.wgabel.timetracker

import android.animation.ValueAnimator
import android.view.View
import android.view.animation.PathInterpolator

/**
 * Shared sliding action tray for history sessions and event rows.
 * The original 260 ms animation and 36 dp slide offset are preserved.
 */
internal class HistoryActionTray(
    private val row: View,
    private val tray: View,
    private val arrow: View,
    private val dimension: Dimension,
    private val expandedSizePx: Int,
    private val slideOffsetPx: Int
) {
    enum class Dimension { WIDTH, HEIGHT }

    private var expanded = false
    private var animation: ValueAnimator? = null

    fun attach(description: String) {
        row.isClickable = true
        row.isFocusable = true
        row.contentDescription = description
        row.setOnClickListener { toggle() }
    }

    private fun toggle() {
        expanded = !expanded
        animation?.cancel()
        val targetSize = if (expanded) expandedSizePx else 0
        val currentSize = (if (dimension == Dimension.WIDTH) tray.layoutParams.width
            else tray.layoutParams.height).coerceAtLeast(0)
        if (expanded && currentSize == 0) {
            tray.translationX = slideOffsetPx.toFloat()
            tray.alpha = 0f
        }
        val fromOffset = tray.translationX
        val fromAlpha = tray.alpha
        animation = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 260L
            interpolator = PathInterpolator(0.4f, 0f, 0.2f, 1f)
            addUpdateListener { animator ->
                val fraction = animator.animatedValue as Float
                val size = currentSize + ((targetSize - currentSize) * fraction).toInt()
                tray.layoutParams = tray.layoutParams.apply {
                    if (dimension == Dimension.WIDTH) width = size else height = size
                }
                tray.translationX = fromOffset * (1f - fraction) +
                    (if (expanded) 0f else slideOffsetPx.toFloat()) * fraction
                arrow.rotation = (if (expanded) 90f else 0f) * fraction +
                    (if (expanded) 0f else 90f) * (1f - fraction)
                tray.alpha = fromAlpha + ((if (expanded) 1f else 0f) - fromAlpha) * fraction
            }
            start()
        }
    }
}
