package pl.wgabel.timetracker.features.statistics

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import pl.wgabel.timetracker.AppContainer
import pl.wgabel.timetracker.AppNavigation
import pl.wgabel.timetracker.R
import pl.wgabel.timetracker.design.AppUi.ButtonRole
import pl.wgabel.timetracker.domain.StatisticsCalculator
import pl.wgabel.timetracker.time.DatePresentation
import pl.wgabel.timetracker.ui.ScreenUi
import pl.wgabel.timetracker.ui.calendar.DayNavigator
import pl.wgabel.timetracker.ui.timeline.TimelineRenderer

internal class StatisticsScreen(
    context: Context, container: AppContainer, navigation: AppNavigation
) : ScreenUi(context, container) {
    override val navigator = navigation
    data class State(var dayOffset: Int = 0, var period: Int = 1, var periodOffset: Int = 0)
    val state = State()
    lateinit var calendar: DayNavigator
    private val timeline = TimelineRenderer(context, container, navigation)
    private fun intervals(start: Long, end: Long) = sessionRepository.clippedIntervals(requireNotNull(projectId), start, end)
    private fun total(person: Int, start: Long, end: Long) = StatisticsCalculator.totalDuration(
        sessionRepository.participantIntervals(requireNotNull(projectId), person, start, end), start, end, System.currentTimeMillis())
    private fun periodRange(): Pair<Long, Long> = StatisticsCalculator.periodRange(
        state.period, state.periodOffset, sessionRepository.earliestSessionStart(requireNotNull(projectId)),
        System.currentTimeMillis())

    private fun barChart(from: Long, to: Long, target: LinearLayout) {
        data class Bucket(val label: String, val start: Long, val end: Long, val millis: Long)
        val now = System.currentTimeMillis()
        val points = StatisticsCalculator.trendBuckets(state.period, from, to,
            intervals(from, minOf(to, now)), now).map { bucket ->
            val name = when (state.period) {
                0 -> DatePresentation.chartHour(resources).format(Date(bucket.start))
                1 -> DatePresentation.weekdayShort(resources).format(Date(bucket.start))
                2 -> SimpleDateFormat("d", resources.configuration.locales[0]).format(Date(bucket.start))
                else -> DatePresentation.monthYear(resources).format(Date(bucket.start))
            }
            Bucket(name, bucket.start, bucket.end, bucket.milliseconds)
        }
        if (points.isEmpty()) {
            target.addView(label(getString(R.string.no_measurements), 14f, false, muted))
            return
        }
        val unit = when (state.period) {
            0 -> "godzinach"
            1, 2 -> "dniach"
            else -> "miesiącach"
        }
        target.addView(label("Suma czasów uczestników w kolejnych $unit", 13f, false, muted))
        gap(target, 8)
        val maxMillis = (points.maxOfOrNull { it.millis } ?: 0L).coerceAtLeast(1L)
        val axisMaxMinutes = ((maxMillis / 60000.0 / 30.0).let { kotlin.math.ceil(it) } * 30.0)
            .toLong().coerceAtLeast(30L)
        val axisMax = axisMaxMinutes * 60000L
        val selectedInfo = label("Dotknij słupka, aby zobaczyć dokładny czas", 13f, false, muted)
        val chart = object : View(this) {
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            private var selected = -1
            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                val left = densityPixels(52).toFloat()
                val right = width.toFloat() - densityPixels(4)
                val top = densityPixels(12).toFloat()
                val bottom = height.toFloat() - densityPixels(30)
                val plotHeight = bottom - top
                val step = (right - left) / points.size
                for (tick in 0..4) {
                    val y = bottom - tick * plotHeight / 4f
                    paint.color = subtle
                    paint.strokeWidth = densityPixels(1).toFloat()
                    canvas.drawLine(left, y, right, y, paint)
                    val minutes = axisMaxMinutes * tick / 4
                    val tickLabel = if (minutes == 0L) "0" else if (minutes % 60L == 0L)
                        "${minutes / 60} h" else if (minutes < 60L) "${minutes} min"
                        else "${minutes / 60} h ${minutes % 60} m"
                    paint.color = muted
                    paint.textSize = densityPixels(10).toFloat()
                    paint.textAlign = Paint.Align.RIGHT
                    canvas.drawText(tickLabel, left - densityPixels(7), y + densityPixels(4), paint)
                }
                points.forEachIndexed { index, point ->
                    val barHeight = (point.millis.toDouble() / axisMax * plotHeight).toFloat()
                        .coerceAtLeast(if (point.millis > 0L) densityPixels(2).toFloat() else 0f)
                    val x = left + index * step + step * .12f
                    paint.color = if (selected == index) darkHeader else blue
                    canvas.drawRoundRect(RectF(x, bottom - barHeight, x + step * .76f, bottom),
                        densityPixels(3).toFloat(), densityPixels(3).toFloat(), paint)
                    if (points.size <= 12 || index % ((points.size + 7) / 8) == 0) {
                        paint.color = muted
                        paint.textSize = densityPixels(10).toFloat()
                        paint.textAlign = Paint.Align.CENTER
                        canvas.drawText(point.label, x + step * .38f, height - densityPixels(7).toFloat(), paint)
                    }
                }
            }
            override fun onTouchEvent(event: MotionEvent): Boolean {
                if (event.action == MotionEvent.ACTION_DOWN) return true
                if (event.action != MotionEvent.ACTION_UP) return super.onTouchEvent(event)
                val left = densityPixels(52).toFloat()
                val available = width.toFloat() - densityPixels(56)
                if (event.x < left || available <= 0f) return true
                val index = (((event.x - left) / available) * points.size).toInt()
                    .coerceIn(0, points.lastIndex)
                selected = index
                invalidate()
                val point = points[index]
                val dateTime = DatePresentation.chartDateTime(resources)
                selectedInfo.text = "${dateTime.format(Date(point.start))} – " +
                    "${dateTime.format(Date(point.end))}: ${formatDuration(point.millis)}"
                return true
            }
        }
        target.addView(chart, LinearLayout.LayoutParams(-1, densityPixels(210)))
        gap(target, 8)
        target.addView(selectedInfo)
        gap(target, 14)
        val peak = points.maxByOrNull { it.millis }
        val active = points.count { it.millis > 0L }
        val average = if (active > 0) points.sumOf { it.millis } / active else 0L
        val metrics = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        metrics.addView(label("Najwięcej: ${peak?.label ?: "—"} · ${formatDuration(peak?.millis ?: 0L)}",
            14f, true, ink))
        gap(metrics, 7)
        metrics.addView(label("Średnio na aktywny przedział: ${formatDuration(average)}",
            13f, false, muted))
        gap(metrics, 7)
        metrics.addView(label("Przedziały z aktywnością: $active / ${points.size}",
            13f, false, muted))
        target.addView(metrics)
    }

    private fun renderPeriodTotals(from: Long, to: Long) {
        val entries = participants().map { it to total(it, from, to) }
        val sum = entries.sumOf { it.second }
        val maximum = (entries.maxOfOrNull { it.second } ?: 0L).coerceAtLeast(1L)
        val c = card()
        c.addView(label("01 — ŁĄCZNY CZAS", 12f, true, muted))
        gap(c, 8)
        c.addView(label(formatDuration(sum), 29f, true, ink))
        c.addView(label("Suma czasów uczestników w wybranym okresie", 12f, false, muted))
        gap(c, 16)
        c.addView(label("Czas według uczestników", 17f, true, ink))
        gap(c, 12)
        entries.forEach { (person, time) ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            row.addView(View(this).apply { background = shape(personColor(person), 5) },
                LinearLayout.LayoutParams(densityPixels(10), densityPixels(10)).apply { rightMargin = densityPixels(8) })
            row.addView(label(personName(person), 14f, false, ink),
                LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(label(formatDuration(time), 14f, true, ink))
            c.addView(row)
            gap(c, 5)
            val fraction = (time.toFloat() / maximum).coerceIn(0f, 1f)
            val bar = LinearLayout(this).apply { background = shape(subtle, 5) }
            if (fraction > 0f) bar.addView(View(this).apply {
                background = shape(personColor(person), 5)
            }, LinearLayout.LayoutParams(0, densityPixels(8), fraction))
            if (fraction < 1f) bar.addView(View(this),
                LinearLayout.LayoutParams(0, densityPixels(8), 1f - fraction))
            c.addView(bar, LinearLayout.LayoutParams(-1, densityPixels(8)))
            gap(c, 12)
        }
        if (sum > 0L) {
            gap(c, 8)
            c.addView(View(this).apply { background = shape(subtle, 1) },
                LinearLayout.LayoutParams(-1, densityPixels(1)))
            gap(c, 16)
            c.addView(label("Udział w łącznym czasie", 17f, true, ink))
            gap(c, 12)
            val shareRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val ring = object : View(this) {
                private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = densityPixels(19).toFloat()
                    strokeCap = Paint.Cap.BUTT
                }
                private val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = ink
                    textAlign = Paint.Align.CENTER
                }
                override fun onDraw(canvas: Canvas) {
                    super.onDraw(canvas)
                    val cx = width / 2f
                    val cy = height / 2f
                    val radius = minOf(width, height) / 2f - densityPixels(13)
                    val bounds = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
                    paint.color = subtle
                    canvas.drawArc(bounds, 0f, 360f, false, paint)
                    var angle = -90f
                    entries.filter { it.second > 0L }.forEach { (person, time) ->
                        val sweep = (time.toDouble() / sum * 360.0).toFloat()
                        paint.color = personColor(person)
                        canvas.drawArc(bounds, angle, sweep, false, paint)
                        angle += sweep
                    }
                    centerPaint.color = ink
                    centerPaint.typeface = Typeface.DEFAULT_BOLD
                    centerPaint.textSize = densityPixels(19).toFloat()
                    canvas.drawText("100%", cx, cy - densityPixels(2), centerPaint)
                    centerPaint.typeface = Typeface.DEFAULT
                    centerPaint.textSize = densityPixels(9).toFloat()
                    canvas.drawText("łącznego czasu", cx, cy + densityPixels(13), centerPaint)
                }
            }
            shareRow.addView(ring, LinearLayout.LayoutParams(0, densityPixels(160), 1f))
            val legend = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(densityPixels(10), 0, 0, 0)
            }
            entries.forEach { (person, time) ->
                val item = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                item.addView(View(this).apply { background = shape(personColor(person), 5) },
                    LinearLayout.LayoutParams(densityPixels(9), densityPixels(9)).apply { rightMargin = densityPixels(7) })
                item.addView(label(personName(person), 12f, false, ink),
                    LinearLayout.LayoutParams(0, -2, 1f))
                val percent = time.toDouble() * 100.0 / sum
                item.addView(label(String.format(resources.configuration.locales[0], "%.1f%%", percent),
                    12f, true, ink))
                legend.addView(item)
                gap(legend, 12)
            }
            shareRow.addView(legend, LinearLayout.LayoutParams(0, -2, 1f))
            c.addView(shareRow)
        }
        addCard(c)
    }

    fun renderStatistics() {
        calendar.renderDayNavigator(state.dayOffset) { state.dayOffset = it; navigator.refreshContent() }
        gap(content, 8)
        timeline.renderTimeline(content, state.dayOffset)
        gap(content, 18)
        timeline.renderVerticalDailyTimeline(content, state.dayOffset)
        gap(content, 22)
        content.addView(label("Podsumowanie okresu", 22f, true, ink))
        gap(content, 12)
        val c = card()
        c.addView(label("WYBIERZ OKRES", 12f, true, muted))
        gap(c, 12)
        val selector = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf(getString(R.string.day), getString(R.string.week), getString(R.string.month), getString(R.string.all)).forEachIndexed { index, name ->
            val chosen = state.period == index
            val control = styledAction(name, if (chosen) ButtonRole.PRIMARY else ButtonRole.SECONDARY) {
                state.period = index
                state.periodOffset = 0
                navigator.refreshContent()
            }
            selector.addView(outlinedSelection(control, darkHeader, chosen),
                LinearLayout.LayoutParams(0, densityPixels(53), 1f).apply { setMargins(densityPixels(1), 0, densityPixels(1), 0) })
        }
        c.addView(selector)
        gap(c, 8)
        if (state.period != 3) {
            val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            nav.addView(styledAction("‹", ButtonRole.SECONDARY) { state.periodOffset--; navigator.refreshContent() },
                LinearLayout.LayoutParams(densityPixels(50), densityPixels(42)))
            val range = periodRange()
            nav.addView(label(dateFormat.format(Date(range.first)), 14f, true).apply { gravity = Gravity.CENTER },
                LinearLayout.LayoutParams(0, densityPixels(42), 1f))
            nav.addView(styledAction("›", ButtonRole.SECONDARY) {
                if (state.periodOffset < 0) { state.periodOffset++; navigator.refreshContent() }
            }, LinearLayout.LayoutParams(densityPixels(50), densityPixels(42)))
            c.addView(nav)
        }
        addCard(c)
        val (from, to) = periodRange()
        if (to > from) {
            renderPeriodTotals(from, to)
            gap(content, 16)
            val trend = card()
            trend.addView(label("PRZEBIEG CZASU W OKRESIE", 12f, true, muted))
            gap(trend, 12)
            barChart(from, to, trend)
            addCard(trend)
        }
    }

}
