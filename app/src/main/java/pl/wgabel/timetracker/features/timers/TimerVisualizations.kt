package pl.wgabel.timetracker.features.timers

import android.content.Context
import android.graphics.Canvas
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
    private fun densityPixels(value: Int): Int = (value * resources.displayMetrics.density + .5f).toInt()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = densityPixels(7).toFloat()
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
        val inset = densityPixels(7).toFloat()
        val rect = RectF(inset, inset, width - inset, height - inset)
        paint.color = trackColor
        canvas.drawArc(rect, 0f, 360f, false, paint)
        paint.color = participantColor
        canvas.drawArc(rect, -90f, (percent * 3.6).toFloat(), false, paint)
        paint.style = Paint.Style.FILL
        paint.color = textColor
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        paint.textSize = densityPixels(16).toFloat()
        canvas.drawText("${percent.toInt()}%", width / 2f,
            height / 2f - (paint.ascent() + paint.descent()) / 2f - densityPixels(5), paint)
        paint.typeface = Typeface.DEFAULT
        paint.textSize = densityPixels(10).toFloat()
        paint.color = captionColor
        canvas.drawText(if (resources.configuration.locales[0].language == "pl") "dnia" else "day",
            width / 2f, height / 2f + densityPixels(14), paint)
        paint.style = Paint.Style.STROKE
    }
}
