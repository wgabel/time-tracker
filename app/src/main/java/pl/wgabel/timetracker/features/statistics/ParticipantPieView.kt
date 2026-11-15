package pl.wgabel.timetracker.features.statistics

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View

internal class ParticipantPieView(context: Context, private val slices: List<Pair<Int, Long>>, private val total: Long) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        val radius = minOf(width, height) / 2f
        val centerX = width / 2f
        val centerY = height / 2f
        var start = -90f
        for ((color, duration) in slices) {
            if (duration <= 0L) continue
            val sweep = (360.0 * duration / total).toFloat()
            paint.color = color
            canvas.drawArc(centerX - radius, centerY - radius, centerX + radius, centerY + radius, start, sweep, true, paint)
            start += sweep
        }
    }
}
