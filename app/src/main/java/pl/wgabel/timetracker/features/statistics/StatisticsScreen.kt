package pl.wgabel.timetracker.features.statistics

import android.app.AlertDialog
import android.icu.text.MeasureFormat
import android.icu.util.MeasureUnit
import android.icu.util.Measure
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
import java.text.NumberFormat
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
    private var chartSelection: Set<Int>? = null
    private var pairsExpanded = false
    private var expanded = false

    fun resetSelection() {
        chartSelection = null
        pairsExpanded = false
        expanded = false
        state.dayOffset = 0
    }

    fun saveSelection(bundle: Bundle) {
        bundle.putIntArray("statisticsChartSelection", chartSelection?.toIntArray())
        bundle.putBoolean("statisticsPairsExpanded", pairsExpanded)
        bundle.putBoolean("statisticsExpanded", expanded)
    }

    fun restoreSelection(bundle: Bundle) {
        chartSelection = bundle.getIntArray("statisticsChartSelection")?.toSet()
        pairsExpanded = bundle.getBoolean("statisticsPairsExpanded")
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
        val ranked = counters.sortedByDescending { recorded.totals.getOrDefault(it.id, 0L) }
        val total = ranked.sumOf { recorded.totals.getOrDefault(it.id, 0L) }
        val coverage = recorded.coverage(ranked.map { it.id }.toSet())
        addPeriodSummary(ranked, recorded, coverage, total)
        addParticipantShares(ranked, recorded, total) { showDetails(it, recorded, from, to, coverage) }
        renderChart(recorded, counters, from, to)
    }

    private fun periodDuration(milliseconds: Long): String {
        val seconds = milliseconds / 1000
        val units = mutableListOf<Measure>()
        val hours = seconds / 3600
        val minutes = seconds / 60 % 60
        if (hours > 0) units.add(Measure(hours, MeasureUnit.HOUR))
        if (minutes > 0) units.add(Measure(minutes, MeasureUnit.MINUTE))
        if (seconds % 60 > 0 || units.isEmpty()) units.add(Measure(seconds % 60, MeasureUnit.SECOND))
        return MeasureFormat.getInstance(DatePresentation.locale(resources),
            MeasureFormat.FormatWidth.SHORT).formatMeasures(*units.toTypedArray())
    }

    private fun durationAndShare(duration: Long, total: Long): String {
        val share = NumberFormat.getPercentInstance(DatePresentation.locale(resources)).format(duration.toDouble() / total)
        return getString(R.string.statistics_duration_share, periodDuration(duration), share)
    }

    private fun addPeriodSummary(ranked: List<Counter>, recorded: RecordedTime, coverage: RecordedTime.Coverage, total: Long) {
        val summary = card()
        val title = when (state.period) {
            0 -> R.string.summary_day
            1 -> R.string.summary_week
            2 -> R.string.summary_month
            else -> R.string.statistics_summary
        }
        summary.addView(label(getString(title), 19f, true))
        gap(summary, 8)
        summary.addView(label(periodDuration(total), 29f, true))
        summary.addView(label(getString(R.string.participants_total_time), 15f))
        if (total > 0L) {
            val maximum = recorded.totals.getValue(ranked.first().id)
            val leaders = ranked.takeWhile { recorded.totals[it.id] == maximum }
            gap(summary, 12)
            val highlight = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = shape(blendColors(surface, if (leaders.size == 1) leaders.first().color else muted, 0.15f))
                val inset = densityPixels(10)
                setPadding(inset, inset, inset, inset)
            }
            highlight.addView(label(getString(R.string.most_time), 14f))
            for (leader in leaders) {
                val row = LinearLayout(this)
                row.addView(label("●  ", 16f, true, leader.color))
                row.addView(label(getString(R.string.participant_share_line, leader.name, durationAndShare(maximum, total)), 16f, true))
                highlight.addView(row)
            }
            summary.addView(highlight)
            gap(summary, 12)
            summary.addView(label(getString(R.string.shared_time), 17f, true))
            val byId = ranked.associateBy { it.id }
            val pairs = coverage.sharedByPair.entries.sortedByDescending { it.value }
            for ((pair, duration) in if (pairsExpanded) pairs else pairs.take(4)) {
                val first = byId.getValue(pair.first)
                val second = byId.getValue(pair.second)
                val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
                row.addView(label("●", 13f, true, first.color))
                row.addView(label("●  ", 13f, true, second.color))
                row.addView(label(getString(R.string.shared_pair, first.name, second.name), 15f),
                    LinearLayout.LayoutParams(0, -2, 1f))
                row.addView(label(periodDuration(duration), 14f, true))
                gap(summary, 6)
                summary.addView(row)
            }
            if (pairs.size > 4) {
                val text = if (pairsExpanded) getString(R.string.statistics_collapse) else getString(R.string.show_pairs, pairs.size)
                summary.addView(styledAction(text, ButtonRole.SECONDARY) { pairsExpanded = !pairsExpanded; navigator.refreshContent() })
            }
            gap(summary, 8)
            summary.addView(label(if (pairs.isEmpty()) getString(R.string.no_shared_time)
                else getString(R.string.shared_time_total, periodDuration(coverage.simultaneous)), 14f, false, muted))
        }
        addCard(summary)
    }

    private fun addParticipantShares(ranked: List<Counter>, recorded: RecordedTime, total: Long, openDetails: (Counter) -> Unit) {
        val card = card()
        card.addView(label(getString(R.string.statistics_by_counter), 18f, true))
        if (total == 0L) {
            card.addView(label(getString(R.string.no_measurements), 14f, false, muted))
            addCard(card)
            return
        }
        gap(card, 8)
        val chartRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val slices = ranked.map { it.color to recorded.totals.getOrDefault(it.id, 0L) }
        chartRow.addView(ParticipantPieView(this, slices, total),
            LinearLayout.LayoutParams(0, densityPixels(designTokens.dimension("statisticsPieHeightDp")), 0.42f))
        val legend = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(densityPixels(8), 0, 0, 0) }
        for (counter in if (expanded) ranked else ranked.take(4)) {
            val row = LinearLayout(this).apply { minimumHeight = densityPixels(48); gravity = Gravity.CENTER_VERTICAL }
            row.addView(label("●  ", 14f, true, counter.color))
            val details = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            details.addView(label(counter.name, 15f, true))
            details.addView(label(durationAndShare(recorded.totals.getOrDefault(counter.id, 0L), total), 13f))
            row.addView(details)
            row.setOnClickListener { openDetails(counter) }
            legend.addView(row)
        }
        chartRow.addView(legend, LinearLayout.LayoutParams(0, -2, 0.58f))
        card.addView(chartRow)
        if (ranked.size > 4) {
            val title = if (expanded) getString(R.string.statistics_collapse) else getString(R.string.statistics_expand, ranked.size)
            card.addView(styledAction(title, ButtonRole.SECONDARY) { expanded = !expanded; navigator.refreshContent() })
        }
        addCard(card)
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

    private fun showDetails(counter: Counter, recorded: RecordedTime, from: Long, to: Long, coverage: RecordedTime.Coverage) {
        val finished = sessionRepository.sessionsInRange(requireNotNull(projectId), from, to, participantFilter = counter.id)
            .filter { it.startedAt >= from && it.endedAt != null }
        val lengths = finished.map { requireNotNull(it.endedAt) - it.startedAt }.sorted()
        val median = if (lengths.isEmpty()) "—" else {
            val lower = lengths[(lengths.size - 1) / 2]
            formatDuration(lower + (lengths[lengths.size / 2] - lower) / 2)
        }
        val total = recorded.totals.getOrDefault(counter.id, 0L)
        val solo = coverage.soloByPerson.getOrDefault(counter.id, 0L)
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
