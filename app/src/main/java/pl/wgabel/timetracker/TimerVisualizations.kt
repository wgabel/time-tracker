package pl.wgabel.timetracker

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View

/** Visual indicators for a participant's daily timer card. */
internal class DayOccupancyRing(
    context: Context,
    private val start: Long,
    private val end: Long,
    private val measuredDuration: () -> Long,
    private val participantColor: Int,
    private val trackColor: Int,
    private val textColor: Int,
    private val captionColor: Int
) : View(context) {
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + .5f).toInt()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(7).toFloat()
        strokeCap = Paint.Cap.ROUND
    }

    init {
        contentDescription = if (resources.configuration.locales[0].language == "pl")
            "Procent doby objęty pomiarami" else "Percentage of day measured"
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val dayLength = (end - start).coerceAtLeast(1L)
        val percent = (measuredDuration().toDouble() / dayLength * 100.0).coerceIn(0.0, 100.0)
        val inset = dp(7).toFloat()
        val rect = RectF(inset, inset, width - inset, height - inset)
        paint.color = trackColor
        canvas.drawArc(rect, 0f, 360f, false, paint)
        paint.color = participantColor
        canvas.drawArc(rect, -90f, (percent * 3.6).toFloat(), false, paint)
        paint.style = Paint.Style.FILL
        paint.color = textColor
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        paint.textSize = dp(16).toFloat()
        canvas.drawText("${percent.toInt()}%", width / 2f,
            height / 2f - (paint.ascent() + paint.descent()) / 2f - dp(5), paint)
        paint.typeface = Typeface.DEFAULT
        paint.textSize = dp(10).toFloat()
        paint.color = captionColor
        canvas.drawText(if (resources.configuration.locales[0].language == "pl") "dnia" else "day",
            width / 2f, height / 2f + dp(14), paint)
        paint.style = Paint.Style.STROKE
    }
}

/** Horizontal daily timeline; a -1 end timestamp denotes an active session. */
internal class DaySessionStrip(
    context: Context,
    private val start: Long,
    private val end: Long,
    private val segments: List<Pair<Long, Long>>,
    private val participantColor: Int,
    private val trackColor: Int
) : View(context) {
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + .5f).toInt()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val widthF = width.toFloat()
        val dayLength = (end - start).coerceAtLeast(1L).toFloat()
        paint.color = trackColor
        canvas.drawRoundRect(0f, 0f, widthF, height.toFloat(), dp(4).toFloat(), dp(4).toFloat(), paint)
        paint.color = participantColor
        segments.forEach { (from, to) ->
            val x1 = (from - start) / dayLength * widthF
            val x2 = ((if (to == -1L) minOf(end, System.currentTimeMillis()) else to) - start) / dayLength * widthF
            canvas.drawRoundRect(x1, 0f, x2, height.toFloat(), dp(3).toFloat(), dp(3).toFloat(), paint)
        }
    }
}
