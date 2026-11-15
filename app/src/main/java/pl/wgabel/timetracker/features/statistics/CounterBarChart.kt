package pl.wgabel.timetracker.features.statistics

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import pl.wgabel.timetracker.design.DesignTokens
import pl.wgabel.timetracker.domain.RecordedTime

/** Each time bucket is one focusable touch target, including gaps between narrow bars. */
internal class CounterBarChart(context: Context, private val tokens: DesignTokens) : HorizontalScrollView(context) {
    private val density = resources.displayMetrics.density
    private fun dimension(name: String) = tokens.dimension(name) * density
    private val minimumBar = dimension("statisticsMinimumBarWidthDp")
    private val maximumBar = dimension("statisticsMaximumBarWidthDp")
    private val barGap = dimension("statisticsBarGapDp")
    private val groupGap = dimension("statisticsGroupGapDp")
    private val columns = LinearLayout(context)
    private var minimumColumnWidth = 1

    init { addView(columns) }

    fun display(buckets: List<RecordedTime.Bucket>, people: List<Int>, colors: Map<Int, Int>,
                axisColor: Int, textColor: Int, axisMaximum: Long,
                label: (RecordedTime.Bucket) -> String, description: (RecordedTime.Bucket) -> String,
                select: (RecordedTime.Bucket) -> Unit) {
        minimumColumnWidth = kotlin.math.ceil(maxOf(32 * density,
            people.size * minimumBar + (people.size - 1).coerceAtLeast(0) * barGap + groupGap)).toInt()
        for (bucket in buckets) {
            val caption = label(bucket)
            val column = object : View(context) {
                private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                override fun onDraw(canvas: Canvas) {
                    val bottom = height - 23 * density
                    val plot = bottom - 5 * density
                    paint.color = axisColor
                    for (index in 0..4) {
                        val y = bottom - index * plot / 4
                        canvas.drawLine(0f, y, width.toFloat(), y, paint)
                    }
                    val count = people.size
                    val bar = ((width - groupGap - (count - 1) * barGap) / count).coerceIn(minimumBar, maximumBar)
                    var left = (width - count * bar - (count - 1) * barGap) / 2
                    people.forEach { person ->
                        val duration = bucket.durations.getOrDefault(person, 0L)
                        if (duration > 0L) {
                            paint.color = colors.getValue(person)
                            val barHeight = (duration.toDouble() / axisMaximum * plot).toFloat().coerceAtLeast(1f)
                            canvas.drawRect(left, bottom - barHeight, left + bar, bottom, paint)
                        }
                        left += bar + barGap
                    }
                    paint.color = textColor
                    paint.textAlign = Paint.Align.CENTER
                    paint.textSize = 11 * resources.displayMetrics.scaledDensity
                    if (bucket.durations.isEmpty() && bucket.start <= System.currentTimeMillis()) {
                        canvas.drawText("–", width / 2f, bottom - 2 * density, paint)
                    }
                    canvas.drawText(caption, width / 2f, height - 5 * density, paint)
                }
            }
            column.contentDescription = description(bucket)
            column.isFocusable = true
            column.setOnClickListener { select(bucket) }
            columns.addView(column, LinearLayout.LayoutParams(minimumColumnWidth, -1))
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val available = MeasureSpec.getSize(widthMeasureSpec)
        val columnWidth = maxOf(minimumColumnWidth, available / columns.childCount.coerceAtLeast(1))
        for (index in 0 until columns.childCount) {
            val column = columns.getChildAt(index)
            if (column.layoutParams.width != columnWidth) {
                column.layoutParams = column.layoutParams.apply { width = columnWidth }
            }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
}
