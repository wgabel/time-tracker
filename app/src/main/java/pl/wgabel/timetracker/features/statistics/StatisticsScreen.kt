package pl.wgabel.timetracker.features.statistics

import android.app.AlertDialog
import android.os.Bundle
import java.time.Instant
import java.time.ZoneId
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Calendar
import pl.wgabel.timetracker.domain.RecordedTime
import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import java.text.SimpleDateFormat
import java.util.Date
import pl.wgabel.timetracker.AppContainer
import pl.wgabel.timetracker.AppNavigation
import pl.wgabel.timetracker.R
import pl.wgabel.timetracker.design.AppUi.ButtonRole
import pl.wgabel.timetracker.domain.StatisticsCalculator
import pl.wgabel.timetracker.time.DatePresentation
import pl.wgabel.timetracker.ui.ScreenUi
import pl.wgabel.timetracker.ui.calendar.DayNavigator

internal class StatisticsScreen(
    context: Context, container: AppContainer, navigation: AppNavigation
) : ScreenUi(context, container) {
    override val navigator = navigation
    data class State(var dayOffset: Int = 0, var period: Int = 1)
    val state = State()
    lateinit var calendar: DayNavigator
    private var summarySelection: Set<Int>? = null
    private var chartSelection: Set<Int>? = null
    private var expanded = false

    fun resetSelection() {
        summarySelection = null
        chartSelection = null
        expanded = false
        state.dayOffset = 0
    }

    fun saveSelection(bundle: Bundle) {
        bundle.putIntArray("statisticsSummarySelection", summarySelection?.toIntArray())
        bundle.putIntArray("statisticsChartSelection", chartSelection?.toIntArray())
        bundle.putBoolean("statisticsExpanded", expanded)
    }

    fun restoreSelection(bundle: Bundle) {
        summarySelection = bundle.getIntArray("statisticsSummarySelection")?.toSet()
        chartSelection = bundle.getIntArray("statisticsChartSelection")?.toSet()
        expanded = bundle.getBoolean("statisticsExpanded")
    }

    private fun periodRange(): Pair<Long, Long> {
        val now = System.currentTimeMillis()
        val anchor = if (state.period == 3) now else dayStart(state.dayOffset)
        val earliest = if (state.period == 3) sessionRepository.earliestSessionStart(requireNotNull(projectId)) else null
        return StatisticsCalculator.periodRange(state.period, 0, earliest, anchor,
            Calendar.getInstance(DatePresentation.locale(resources)))
    }

    private fun movePeriod(direction: Int) {
        val today = LocalDate.now()
        val selected = today.plusDays(state.dayOffset.toLong())
        val next = when (state.period) {
            0 -> selected.plusDays(direction.toLong())
            1 -> selected.plusWeeks(direction.toLong())
            else -> selected.plusMonths(direction.toLong())
        }
        state.dayOffset = ChronoUnit.DAYS.between(today, minOf(today, next)).toInt()
        navigator.refreshContent()
    }

    private fun rangeLabel(from: Long, to: Long): String {
        val first = dateFormat.format(Date(from))
        val last = dateFormat.format(Date(maxOf(from, to - 1)))
        return if (first == last) first else "$first – $last"
    }

    fun renderStatistics() {
        calendar.renderDayNavigator(state.dayOffset) { state.dayOffset = it; navigator.refreshContent() }
        content.addView(label(getString(R.string.statistics_summary), 22f, true, ink))
        gap(content, 12)
        val c = card()
        val selector = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf(getString(R.string.day), getString(R.string.week), getString(R.string.month), getString(R.string.all)).forEachIndexed { index, name ->
            val chosen = state.period == index
            val control = styledAction(name, if (chosen) ButtonRole.PRIMARY else ButtonRole.SECONDARY) {
                state.period = index
                navigator.refreshContent()
            }
            selector.addView(outlinedSelection(control, darkHeader, chosen),
                LinearLayout.LayoutParams(0, densityPixels(53), 1f).apply { setMargins(densityPixels(1), 0, densityPixels(1), 0) })
        }
        c.addView(selector)
        gap(c, 8)
        if (state.period != 3) {
            val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            nav.addView(styledAction("‹", ButtonRole.SECONDARY) { movePeriod(-1) },
                LinearLayout.LayoutParams(densityPixels(50), densityPixels(42)))
            val range = periodRange()
            nav.addView(label(rangeLabel(range.first, range.second), 14f, true).apply { gravity = Gravity.CENTER },
                LinearLayout.LayoutParams(0, densityPixels(42), 1f))
            nav.addView(styledAction("›", ButtonRole.SECONDARY) {
                movePeriod(1)
            }, LinearLayout.LayoutParams(densityPixels(50), densityPixels(42)))
            c.addView(nav)
        }
        addCard(c)
        val (from, to) = periodRange()
        renderPeriod(from, to)
    }

    private data class Counter(val id: Int, val name: String, val color: Int)

    private fun renderPeriod(from: Long, to: Long) {
        val now = System.currentTimeMillis()
        val counters = participants().map { Counter(it, personName(it), personColor(it)) }
        if (counters.isEmpty() || from >= to) {
            content.addView(label(getString(R.string.no_measurements), 14f, false, muted))
            return
        }
        val recorded = RecordedTime(sessionRepository.clippedIntervals(requireNotNull(projectId), from, minOf(now, to), now))
        val selected = counters.filter { summarySelection?.contains(it.id) != false }
        val coverage = recorded.coverage(selected.map { it.id }.toSet())
        val summary = card()
        summary.addView(label(getString(R.string.statistics_recorded), 17f, true))
        summary.addView(label(formatDuration(coverage.elapsed), 29f, true))
        summary.addView(statisticsParticipantButtons(counters, summarySelection) { summarySelection = it })
        if (coverage.elapsed > 0) {
            val band = LinearLayout(this)
            addSegment(band, themeColor("eventIcon"), coverage.single.toFloat())
            addSegment(band, themeColor("timeScaleRuler"), coverage.simultaneous.toFloat())
            gap(summary, 8)
            summary.addView(band)
        }
        summary.addView(label(getString(R.string.statistics_single, formatDuration(coverage.single)), 14f))
        summary.addView(label(getString(R.string.statistics_multiple, formatDuration(coverage.simultaneous)), 14f))
        addCard(summary)

        val list = card()
        list.addView(label(getString(R.string.statistics_by_counter), 18f, true))
        val sum = recorded.totals.values.sum()
        for (counter in if (expanded) counters else counters.take(3)) {
            val duration = recorded.totals.getOrDefault(counter.id, 0L)
            val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            row.addView(label("● ", 16f, true, counter.color))
            row.addView(label(counter.name, 16f, true), LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(label(formatDuration(duration) + "   ›", 15f))
            row.minimumHeight = densityPixels(48)
            row.isFocusable = true
            row.setOnClickListener { showDetails(counter, recorded, from, to, counters) }
            list.addView(row)
            if (sum > 0) {
                val track = LinearLayout(this)
                addSegment(track, counter.color, duration.toFloat())
                addSegment(track, subtleNeutral, (sum - duration).toFloat())
                list.addView(track)
            }
        }
        if (counters.size > 3) {
            val title = if (expanded) getString(R.string.statistics_collapse) else getString(R.string.statistics_expand, counters.size)
            list.addView(styledAction(title, ButtonRole.SECONDARY) { expanded = !expanded; navigator.refreshContent() })
        }
        addCard(list)
        renderChart(recorded, counters, from, to)
    }

    private fun addSegment(row: LinearLayout, color: Int, weight: Float) {
        if (weight > 0f) row.addView(View(this).apply { setBackgroundColor(color) },
            LinearLayout.LayoutParams(0, densityPixels(6), weight))
    }

    private fun statisticsParticipantButtons(counters: List<Counter>, selection: Set<Int>?,
                                             update: (Set<Int>?) -> Unit): View {
        val people = counters.map { it.id }
        val current = selection?.intersect(people.toSet())
        return participantFilterButtons(people, current) { person ->
            val next = person?.let { id ->
                val selected = current.orEmpty()
                if (id in selected) selected - id else selected + id
            }
            update(next)
            navigator.refreshContent()
        }
    }

    private fun showDetails(counter: Counter, recorded: RecordedTime, from: Long, to: Long, counters: List<Counter>) {
        val finished = sessionRepository.sessionsInRange(requireNotNull(projectId), from, to, participantFilter = counter.id)
            .filter { it.startedAt >= from && it.endedAt != null }
        val lengths = finished.map { requireNotNull(it.endedAt) - it.startedAt }.sorted()
        val median = if (lengths.isEmpty()) "—" else {
            val lower = lengths[(lengths.size - 1) / 2]
            formatDuration(lower + (lengths[lengths.size / 2] - lower) / 2)
        }
        val total = recorded.totals.getOrDefault(counter.id, 0L)
        val comparison = counters.filter { summarySelection?.contains(it.id) != false }.map { it.id }.toSet() + counter.id
        val solo = recorded.coverage(comparison).soloByPerson.getOrDefault(counter.id, 0L)
        val dialogText = buildString {
            appendLine(rangeLabel(from, to))
            appendLine(getString(R.string.statistics_details, formatDuration(total), formatDuration(solo),
                formatDuration(total - solo), lengths.size, median))
            if (finished.isNotEmpty()) {
                appendLine()
                appendLine(getString(R.string.statistics_recent))
                val formatter = DatePresentation.shortDateTime(resources)
                finished.take(3).forEach { appendLine("${formatter.format(Date(it.startedAt))}   ${formatDuration(requireNotNull(it.endedAt) - it.startedAt)}") }
            }
        }
        AlertDialog.Builder(this).setTitle(counter.name).setMessage(dialogText.trimEnd())
            .setNeutralButton(R.string.history) { _, _ ->
                val latest = finished.firstOrNull()?.startedAt
                val offset = latest?.let {
                    val date = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
                    ChronoUnit.DAYS.between(LocalDate.now(), date).toInt()
                } ?: state.dayOffset
                navigator.openCounterHistory(counter.id, offset)
            }.setPositiveButton(android.R.string.ok, null).show()
    }

    private fun renderChart(recorded: RecordedTime, counters: List<Counter>, from: Long, to: Long) {
        val selected = counters.filter { chartSelection?.contains(it.id) != false }
        val chartCard = card()
        val title = when (state.period) {
            0 -> R.string.statistics_hourly
            3 -> R.string.statistics_monthly
            else -> R.string.statistics_daily
        }
        chartCard.addView(label(getString(title), 18f, true))
        chartCard.addView(statisticsParticipantButtons(counters, chartSelection) { chartSelection = it })
        if (selected.isNotEmpty()) {
            val people = selected.map { it.id }
            val buckets = recorded.buckets(state.period, from, to, people.toSet())
            if (state.period == 1 || state.period == 2) {
                chartCard.addView(label(getString(R.string.statistics_days_with_entries,
                    buckets.count { it.durations.isNotEmpty() }, buckets.size), 13f, false, muted))
            }
            val maximum = buckets.maxOfOrNull { it.durations.values.maxOrNull() ?: 0L } ?: 0L
            val unit = if (maximum <= 3_600_000L) 60_000L else 3_600_000L
            val axisMaximum = ((maximum + unit - 1) / unit).coerceAtLeast(1L) * unit

            val formatter = when (state.period) {
                0 -> DatePresentation.chartHour(resources)
                1 -> DatePresentation.weekdayShort(resources)
                2 -> SimpleDateFormat("d", DatePresentation.locale(resources))
                else -> DatePresentation.monthYear(resources)
            }
            val chart = CounterBarChart(this, designTokens)
            chart.display(buckets, people, selected.associate { it.id to it.color }, subtleNeutral, muted, axisMaximum,
                { formatter.format(Date(it.start)) }, { DatePresentation.chartDateTime(resources).format(Date(it.start)) }) { bucket ->
                val details = selected.map { counter ->
                    val duration = bucket.durations[counter.id]?.let(::formatDuration) ?: "—"
                    "${counter.name}    $duration"
                }
                AlertDialog.Builder(this).setTitle(DatePresentation.chartDateTime(resources).format(Date(bucket.start)))
                    .setItems(details.toTypedArray(), null).setPositiveButton(android.R.string.ok, null).show()
            }
            val height = densityPixels(designTokens.dimension("statisticsChartHeightDp"))
            val plot = LinearLayout(this)
            val scale = android.widget.FrameLayout(this)
            for (tick in 0..4) {
                val value = formatDuration(axisMaximum * tick / 4)
                val mark = label(value, 10f, false, muted)
                val position = android.widget.FrameLayout.LayoutParams(-1, -2)
                position.topMargin = ((height - densityPixels(23)) -
                    tick * (height - densityPixels(28)) / 4 - densityPixels(6)).coerceAtLeast(0)
                scale.addView(mark, position)
            }
            plot.addView(scale, LinearLayout.LayoutParams(densityPixels(49), height))
            plot.addView(chart, LinearLayout.LayoutParams(0, height, 1f))
            chartCard.addView(plot)
            chartCard.addView(label(getString(R.string.statistics_chart_hint), 12f, false, muted))
        }
        addCard(chartCard)
    }
}
