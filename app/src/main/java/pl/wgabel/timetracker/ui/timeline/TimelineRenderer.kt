package pl.wgabel.timetracker.ui.timeline

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.Toast
import java.util.Calendar
import java.util.Date
import java.util.Locale
import pl.wgabel.timetracker.AppContainer
import pl.wgabel.timetracker.AppNavigation
import pl.wgabel.timetracker.R
import pl.wgabel.timetracker.model.SessionEvent
import pl.wgabel.timetracker.ui.ScreenUi
import pl.wgabel.timetracker.ui.sessions.SessionDialogs

internal class TimelineRenderer(
    context: Context, container: AppContainer, navigation: AppNavigation
) : ScreenUi(context, container) {
    override val navigator = navigation
    private val dialogs = SessionDialogs(context, container, navigation)
    private fun activeStart(person: Int) = sessionRepository.activeStart(requireNotNull(projectId), person)
    private fun intervals(start: Long, end: Long) = sessionRepository.clippedIntervals(requireNotNull(projectId), start, end)
    private fun drawRunningBlocks(canvas: Canvas, paint: Paint, area: RectF, color: Int, vertical: Boolean) {
        if (area.width() <= 0f || area.height() <= 0f) return
        val t = android.os.SystemClock.uptimeMillis().toDouble() / 3600.0
        canvas.save()
        val clip = Path().apply { addRoundRect(area, densityPixels(3).toFloat(), densityPixels(3).toFloat(), Path.Direction.CW) }
        canvas.clipPath(clip)
        for (i in 0 until 24) {
            // Stable pseudo-random particle properties: no jitter between frames.
            fun fraction(seed: Int): Float {
                val n = kotlin.math.sin(seed.toDouble() * 12.9898 + i * 78.233) * 43758.5453
                return (n - kotlin.math.floor(n)).toFloat()
            }
            val speed = .65f + fraction(11) * .8f
            val progress = ((t * speed + fraction(23)) % 1.0).toFloat()
            val eased = progress * progress * (3f - 2f * progress)
            val side = densityPixels(4 + (fraction(37) * 5).toInt()).toFloat()
            val x = if (vertical) area.left + fraction(41) * (area.width() - side).coerceAtLeast(0f)
                else area.left - side + eased * (area.width() + 2f * side)
            val y = if (vertical) area.top - side + eased * (area.height() + 2f * side)
                else area.top + fraction(41) * (area.height() - side).coerceAtLeast(0f)
            // Blend each participant's bar color toward white, retaining its hue.
            val lighten = .35f + fraction(53) * .20f
            paint.color = Color.argb(180 + (fraction(59) * 55).toInt(),
                (Color.red(color) + (255 - Color.red(color)) * lighten).toInt(),
                (Color.green(color) + (255 - Color.green(color)) * lighten).toInt(),
                (Color.blue(color) + (255 - Color.blue(color)) * lighten).toInt())
            canvas.drawRoundRect(RectF(x, y, x + side, y + side), densityPixels(1).toFloat(), densityPixels(1).toFloat(), paint)
        }
        canvas.restore()
    }

    private fun animateRunningChart(view: View, active: Boolean) {
        if (!active) return
        val ticker = object : Runnable {
            override fun run() {
                if (!view.isAttachedToWindow) return
                view.invalidate()
                view.postDelayed(this, 33L)
            }
        }
        view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) { v.post(ticker) }
            override fun onViewDetachedFromWindow(v: View) { v.removeCallbacks(ticker) }
        })
        // The view can stay attached when returning from background.
        view.viewTreeObserver.addOnWindowFocusChangeListener { focused ->
            view.removeCallbacks(ticker)
            if (focused && view.isAttachedToWindow) view.post(ticker)
        }
    }

    fun renderTimeline(target: LinearLayout, selectedOffset: Int, participantFilter: Int? = null, inHistory: Boolean = false) {
        if (inHistory) {
            renderHistoryTimeline(target, selectedOffset, participantFilter)
            return
        }
        val c = if (inHistory) LinearLayout(this).apply { orientation = LinearLayout.VERTICAL } else card()
        if (!inHistory) {
            c.addView(label(getString(R.string.daily_timeline), 20f, true))
            gap(c, 8)
        }
        val day = dayStart(selectedOffset)
        val people = if (inHistory && participantFilter != null) participants().filter { it == participantFilter } else participants()
        if (people.isEmpty()) {
            c.addView(label(getString(R.string.timeline_hint), 14f, false, muted))
        } else {
            val end = dayStart((selectedOffset) + 1)
            val sessions = intervals(day, end)
            val storedSessions = sessionRepository.sessionsInRange(requireNotNull(projectId), day, end).sortedBy { it.id }
            val detailSessions = storedSessions.map { session ->
                Triple(session.id, session.participantId, maxOf(day, session.startedAt) to minOf(end, session.endedAt ?: System.currentTimeMillis()))
            }
            // Capture active session IDs and starts, rather than guessing from end timestamps.
            val runningStarts = if (day <= System.currentTimeMillis() && end > System.currentTimeMillis())
                people.mapNotNull { person -> activeStart(person)?.let { person to it } }.toMap()
            else emptyMap()
            val timeline = object : View(this) {
                override fun onTouchEvent(event: MotionEvent): Boolean {
                    if (event.action != MotionEvent.ACTION_UP) return true
                    val row = ((event.y - densityPixels(7)) / densityPixels(32)).toInt()
                    if (row !in people.indices) return true
                    val left = if (inHistory) 0f else densityPixels(80).toFloat()
                    val widthScale = (width.toFloat() - densityPixels(5) - left).coerceAtLeast(1f)
                    val fraction = ((event.x - left) / widthScale).coerceIn(0f, 1f)
                    val moment = day + ((end - day) * fraction).toLong()
                    val person = people[row]
                    val chosen = detailSessions.lastOrNull { it.second == person && moment >= it.third.first && moment <= it.third.second }
                    if (chosen != null) {
                        val session = storedSessions.firstOrNull { it.id == chosen.first }
                        if (session != null) {
                            if (session.endedAt != null && !isArchived()) dialogs.editSession(session.id, person, session.startedAt, session.endedAt)
                            else Toast.makeText(this@TimelineRenderer, getString(R.string.stop_before_edit_short), Toast.LENGTH_SHORT).show()
                        }
                    }
                    return true
                }
                private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                override fun onDraw(canvas: Canvas) {
                    super.onDraw(canvas)
                    val left = if (inHistory) 0f else densityPixels(80).toFloat()
                    val right = width.toFloat() - densityPixels(5)
                    val trackWidth = (right - left).coerceAtLeast(1f)
                    val rowHeight = densityPixels(32).toFloat()
                    people.forEachIndexed { index, person ->
                        val y = densityPixels(7) + index * rowHeight
                        paint.color = ink
                        paint.textSize = densityPixels(11).toFloat()
                        paint.textAlign = Paint.Align.LEFT
                        if (!inHistory) {
                            val name = personName(person)
                            canvas.drawText(if (name.length > 11) name.take(10) + "…" else name, densityPixels(2).toFloat(), y + densityPixels(15), paint)
                        }
                        paint.color = if (darkTheme) Color.rgb(65, 75, 90) else Color.rgb(234, 237, 242)
                        canvas.drawRoundRect(RectF(left, y, right, y + densityPixels(21)), densityPixels(3).toFloat(), densityPixels(3).toFloat(), paint)
                        sessions.filter { it.first == person }.forEach { item ->
                            val running = runningStarts[person] != null &&
                                item.second == maxOf(day, runningStarts[person]!!)
                            val finish = if (running) minOf(end, System.currentTimeMillis()) else item.third
                            val x1 = left + ((item.second - day).toFloat() / (end - day)) * trackWidth
                            val x2 = left + ((finish - day).toFloat() / (end - day)) * trackWidth
                            paint.color = personColor(person)
                            canvas.drawRoundRect(RectF(x1, y, maxOf(x1 + densityPixels(2), x2), y + densityPixels(21)), densityPixels(3).toFloat(), densityPixels(3).toFloat(), paint)
                            if (running) {
                                drawRunningBlocks(canvas, paint,
                                    RectF(x1, y, maxOf(x1 + densityPixels(2), x2), y + densityPixels(21)),
                                    personColor(person), false)
                            }
                        }
                        // Hour ticks directly below each timeline track, with only a 1dp gap.
                        paint.strokeWidth = resources.displayMetrics.density.coerceAtLeast(1f)
                        for (hour in 0..24) {
                            val x = left + hour / 24f * trackWidth
                            val major = hour % 6 == 0
                            paint.color = if (darkTheme) {
                                if (major) Color.rgb(172, 186, 207) else Color.rgb(116, 132, 153)
                            } else {
                                if (major) Color.rgb(100, 113, 132) else Color.rgb(157, 169, 184)
                            }
                            val tickLength = if (major) densityPixels(7) else densityPixels(4)
                            canvas.drawLine(x, y + densityPixels(22), x, y + densityPixels(22) + tickLength, paint)
                        }
                    }
                    if (!inHistory) {
                    paint.color = muted
                    paint.textSize = densityPixels(10).toFloat()
                    paint.textAlign = Paint.Align.CENTER
                    for (hour in listOf(0, 6, 12, 18, 24)) {
                        val x = left + hour / 24f * trackWidth
                        canvas.drawText(String.format(Locale.ROOT, "%02d", hour), x, height - densityPixels(3).toFloat(), paint)
                    }
                    }
                }
            }
            c.addView(timeline, LinearLayout.LayoutParams(-1, densityPixels(32) * people.size + (if (inHistory) densityPixels(4) else densityPixels(25))))
            animateRunningChart(timeline, runningStarts.isNotEmpty())
        }
        if (inHistory) {
            target.addView(c, LinearLayout.LayoutParams(-1, -2))
            gap(target, 8)
        } else { target.addView(c); gap(target, 14) }
    }


    /** History owns a separate chart layout; session/event lists and Statistics stay unchanged. */
    private fun renderHistoryTimeline(target: LinearLayout, selectedOffset: Int, participantFilter: Int?) {
        val panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val people = participants().filter { participantFilter == null || it == participantFilter }
        if (people.isEmpty()) {
            panel.addView(label(getString(R.string.timeline_hint), 14f, false, muted))
            target.addView(panel)
            return
        }
        val day = dayStart(selectedOffset)
        val end = dayStart(selectedOffset + 1)
        val sessions = sessionRepository.sessionsInRange(requireNotNull(projectId), day, end).sortedBy { it.id }
        val events = eventRepository.eventsForDay(requireNotNull(projectId), day, end, withinSessionsOnly = true)
        val details = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = shape(subtleNeutral, 12)
            setPadding(densityPixels(12), densityPixels(10), densityPixels(12), densityPixels(10))
            visibility = View.GONE
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        var selectedEventIds = emptySet<Long>()
        val markers = mutableListOf<View>()
        fun describeEvents(group: List<SessionEvent>): String =
            resources.getQuantityString(R.plurals.timeline_event_count, group.size, group.size) +
                " · " + personName(group.first().participantId)
        fun selectEvents(group: List<SessionEvent>) {
            selectedEventIds = group.map { it.id }.toSet()
            details.removeAllViews()
            details.addView(label(describeEvents(group), 14f, true))
            group.forEach { event ->
                details.addView(label(clockFormat.format(Date(event.happenedAt)) + " · " + event.type +
                    if (event.note.isBlank()) "" else ": " + event.note, 14f))
            }
            details.visibility = View.VISIBLE
            markers.forEach { it.isSelected = (it.tag as? List<*>)?.any { event ->
                event is SessionEvent && event.id in selectedEventIds
            } == true; it.invalidate() }
        }
        val trackInset = densityPixels(24)
        people.forEach { person ->
            val personSessions = sessions.filter { it.participantId == person }
            val participantEvents = events.filter { it.participantId == person }
            val heading = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            heading.addView(View(this).apply { background = shape(personColor(person), 8) },
                LinearLayout.LayoutParams(densityPixels(8), densityPixels(8)).apply { rightMargin = densityPixels(8) })
            heading.addView(label(personName(person), 16f, true).apply { maxLines = 2 },
                LinearLayout.LayoutParams(0, -2, 1f))
            val total = personSessions.sumOf { session ->
                (minOf(end, session.endedAt ?: System.currentTimeMillis()) - maxOf(day, session.startedAt)).coerceAtLeast(0L)
            }
            heading.addView(label(formatDuration(total), 13f, false, muted).apply {
                setPadding(densityPixels(8), 0, 0, 0)
            })
            panel.addView(heading)

            val track = object : View(this) {
                private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                private var touchedX = 0f
                init {
                    contentDescription = getString(R.string.timeline_participant_sessions, personName(person), formatDuration(total))
                    setOnClickListener {
                        val fraction = ((touchedX - trackInset) / (width - 2f * trackInset).coerceAtLeast(1f)).coerceIn(0f, 1f)
                        val moment = day + ((end - day) * fraction).toLong()
                        val chosen = personSessions.lastOrNull {
                            moment >= maxOf(day, it.startedAt) && moment <= minOf(end, it.endedAt ?: System.currentTimeMillis())
                        }
                        if (chosen != null) {
                            val finish = chosen.endedAt
                            if (finish != null && !isArchived()) dialogs.editSession(chosen.id, person, chosen.startedAt, finish)
                            else Toast.makeText(this@TimelineRenderer, getString(R.string.stop_before_edit_short), Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                override fun onTouchEvent(event: MotionEvent): Boolean {
                    touchedX = event.x
                    return super.onTouchEvent(event)
                }
                override fun onDraw(canvas: Canvas) {
                    val left = trackInset.toFloat()
                    val right = (width - trackInset).toFloat().coerceAtLeast(left + 1f)
                    val middle = height / 2f
                    paint.color = if (darkTheme) Color.rgb(65, 75, 90) else Color.rgb(234, 237, 242)
                    canvas.drawRoundRect(RectF(left, middle - densityPixels(3), right, middle + densityPixels(3)),
                        densityPixels(3).toFloat(), densityPixels(3).toFloat(), paint)
                    personSessions.forEach { session ->
                        val start = maxOf(day, session.startedAt)
                        val finish = minOf(end, session.endedAt ?: System.currentTimeMillis())
                        if (finish <= start) return@forEach
                        val first = left + (start - day).toDouble().div(end - day).toFloat() * (right - left)
                        val last = left + (finish - day).toDouble().div(end - day).toFloat() * (right - left)
                        val area = RectF(first, middle - densityPixels(7), minOf(right, maxOf(first + densityPixels(2), last)), middle + densityPixels(7))
                        paint.color = personColor(person)
                        canvas.drawRoundRect(area, densityPixels(3).toFloat(), densityPixels(3).toFloat(), paint)
                        if (session.endedAt == null) drawRunningBlocks(canvas, paint, area, personColor(person), false)
                    }
                }
            }
            class EventButton : View(this@TimelineRenderer) {
                private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                var group = emptyList<SessionEvent>()
                init {
                    isFocusable = true
                    setOnClickListener { if (group.isNotEmpty()) selectEvents(group) }
                }
                fun bindEvents(events: List<SessionEvent>) {
                    group = events
                    tag = events
                    contentDescription = describeEvents(events) + " · " +
                        events.joinToString("; ") { clockFormat.format(Date(it.happenedAt)) + " · " + it.type }
                    isSelected = events.any { it.id in selectedEventIds }
                }
                override fun onInitializeAccessibilityNodeInfo(info: android.view.accessibility.AccessibilityNodeInfo) {
                    super.onInitializeAccessibilityNodeInfo(info)
                    info.className = android.widget.Button::class.java.name
                }
                override fun onDraw(canvas: Canvas) {
                    val centerX = width / 2f
                    val centerY = height / 2f
                    paint.typeface = Typeface.DEFAULT_BOLD
                    paint.textSize = 12f * resources.displayMetrics.scaledDensity
                    val count = group.size.toString()
                    val radius = if (group.size > 1) maxOf(densityPixels(12).toFloat(), paint.measureText(count) / 2 + densityPixels(3))
                        .coerceAtMost(width / 2f - densityPixels(5)) else densityPixels(6).toFloat()
                    paint.color = surface
                    canvas.drawCircle(centerX, centerY, radius + densityPixels(2), paint)
                    paint.color = personColor(person)
                    canvas.drawCircle(centerX, centerY, radius, paint)
                    if (group.size > 1) {
                        // Choose readable text even when a participant has a custom light color.
                        val color = personColor(person)
                        paint.color = if (Color.luminance(color) > .179) Color.BLACK else Color.WHITE
                        paint.textAlign = Paint.Align.CENTER
                        if (paint.measureText(count) > radius * 1.7f) paint.textSize *= radius * 1.7f / paint.measureText(count)
                        canvas.drawText(count, centerX, centerY - (paint.ascent() + paint.descent()) / 2, paint)
                    }
                    if (isSelected || isPressed || isFocused) {
                        paint.color = ink
                        paint.style = Paint.Style.STROKE
                        paint.strokeWidth = densityPixels(2).toFloat()
                        canvas.drawCircle(centerX, centerY, radius + densityPixels(4), paint)
                        paint.style = Paint.Style.FILL
                    }
                }
            }
            val row = object : ViewGroup(this) {
                private var groups = emptyList<List<SessionEvent>>()
                private val eventButtons = participantEvents.map { EventButton().also { markers.add(it) } }
                init {
                    addView(track)
                    eventButtons.forEach { addView(it) }
                }
                override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                    val measuredWidth = View.MeasureSpec.getSize(widthMeasureSpec)
                    val rowHeight = densityPixels(48)
                    setMeasuredDimension(measuredWidth, rowHeight)
                    groups = TimelineEventClusters.groupEvents(participantEvents, day, end,
                        (measuredWidth - 2f * trackInset).coerceAtLeast(1f), densityPixels(48).toFloat())
                    track.measure(View.MeasureSpec.makeMeasureSpec(measuredWidth, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(rowHeight, View.MeasureSpec.EXACTLY))
                    eventButtons.forEachIndexed { index, button ->
                        button.visibility = if (index < groups.size) View.VISIBLE else View.GONE
                        if (index < groups.size) {
                            button.bindEvents(groups[index])
                            button.measure(View.MeasureSpec.makeMeasureSpec(densityPixels(44), View.MeasureSpec.EXACTLY),
                                View.MeasureSpec.makeMeasureSpec(rowHeight, View.MeasureSpec.EXACTLY))
                        }
                    }
                }
                override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
                    track.layout(0, 0, width, height)
                    groups.forEachIndexed { index, group ->
                        val center = trackInset + TimelineEventClusters.centerFraction(group, day, end) * (width - 2f * trackInset)
                        val buttonLeft = (center - densityPixels(22)).toInt()
                        eventButtons[index].layout(buttonLeft, 0, buttonLeft + densityPixels(44), height)
                    }
                }
            }
            panel.addView(row, LinearLayout.LayoutParams(-1, densityPixels(48)))
            animateRunningChart(track, personSessions.any { it.endedAt == null } && day <= System.currentTimeMillis() && end > System.currentTimeMillis())
            gap(panel, 6)
        }
        panel.addView(object : View(this) {
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            override fun onDraw(canvas: Canvas) {
                paint.color = muted
                paint.textSize = 11f * resources.displayMetrics.scaledDensity
                for (hour in listOf(0, 6, 12, 18, 24)) {
                    val moment = if (hour == 24) end else Calendar.getInstance().apply {
                        timeInMillis = day
                        set(Calendar.HOUR_OF_DAY, hour)
                    }.timeInMillis
                    val position = trackInset + (moment - day).toDouble().div(end - day).toFloat() * (width - 2f * trackInset)
                    paint.textAlign = when (hour) { 0 -> Paint.Align.LEFT; 24 -> Paint.Align.RIGHT; else -> Paint.Align.CENTER }
                    canvas.drawText(String.format(Locale.ROOT, "%02d:00", hour), position, -paint.ascent(), paint)
                }
            }
        }.apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO },
            LinearLayout.LayoutParams(-1, (18 * resources.displayMetrics.scaledDensity).toInt()))
        panel.addView(label(getString(R.string.timeline_legend), 12f, false, muted))
        gap(panel, 8)
        panel.addView(details, LinearLayout.LayoutParams(-1, -2))
        target.addView(panel, LinearLayout.LayoutParams(-1, -2))
        gap(target, 8)
    }

    // Fixed-height daily view: the time scale and event labels stay pinned horizontally.
    // Only participant tracks respond to horizontal swipes.
    fun renderVerticalDailyTimeline(target: LinearLayout, selectedOffset: Int) {
        val polish = resources.configuration.locales[0].language == "pl"
        val panel = card()
        panel.addView(label(if (polish) "Pionowa oś dnia" else "Vertical daily timeline", 20f, true))
        gap(panel, 8)
        val day = dayStart(selectedOffset)
        val end = dayStart(selectedOffset + 1)
        val sessions = intervals(day, end)
        val events = eventRepository.eventsForDay(requireNotNull(projectId), day, end, withinSessionsOnly = true)
        val activePeople = sessions.map { it.first }.distinct()
        val runningStarts = if (day <= System.currentTimeMillis() && end > System.currentTimeMillis())
            activePeople.mapNotNull { person -> activeStart(person)?.let { person to it } }.toMap()
        else emptyMap()
        if (sessions.isEmpty() && events.isEmpty()) {
            panel.addView(label(if (polish) "Brak aktywności tego dnia" else "No activity on this day", 14f, false, muted))
            target.addView(panel); gap(target, 14)
            return
        }

        val names = activePeople.associateWith { personName(it) }
        // The full local day is always shown. Grow the chart when event labels
        // need more vertical room, instead of clipping the bottom of the day.
        // Size for the actual distribution of events, not just their count.
        // Dense clusters near midnight need extra room below their timestamps.
        val labelGap = densityPixels(64).toFloat()
        var fullHeight = densityPixels(480)
        for (attempt in 0 until 200) {
            var nextLabel = densityPixels(12).toFloat()
            for (event in events) {
                val exactY = ((event.happenedAt - day).toDouble() / (end - day) * fullHeight).toFloat()
                nextLabel = maxOf(exactY, nextLabel) + labelGap
            }
            if (events.isEmpty() || nextLabel - labelGap + densityPixels(54) <= fullHeight) break
            fullHeight += densityPixels(64)
        }
        val graph = object : View(this) {
            private val p = Paint(Paint.ANTI_ALIAS_FLAG)
            private var horizontalOffset = 0f
            private var downX = 0f
            private var downY = 0f
            private var initialOffset = 0f
            private var swiping = false
            private val scaleWidth get() = densityPixels(38).toFloat()
            private val rightEdge get() = width * .40f
            private val participantWidth get() = (rightEdge - scaleWidth - densityPixels(6)).coerceAtLeast(densityPixels(12).toFloat())
            // Two participants fill the same width that many compact tracks would occupy.
            private val visibleCapacity get() = (participantWidth / densityPixels(19)).toInt().coerceAtLeast(1)
            private val trackStep get() = if (activePeople.size <= visibleCapacity)
                participantWidth / activePeople.size.coerceAtLeast(1) else densityPixels(19).toFloat()
            private val trackWidth get() = (trackStep - densityPixels(3)).coerceAtLeast(densityPixels(8).toFloat())
            private fun drawWrappedText(canvas: Canvas, value: String, x: Float, baseline: Float,
                                        availableWidth: Float, maxLines: Int, lineHeight: Float) {
                val words = value.split(Regex("\\s+"))
                var line = ""
                var lineIndex = 0
                for (word in words) {
                    val candidate = if (line.isEmpty()) word else "$line $word"
                    if (p.measureText(candidate) <= availableWidth) {
                        line = candidate
                    } else {
                        if (line.isNotEmpty()) {
                            canvas.drawText(line, x, baseline + lineIndex * lineHeight, p)
                            lineIndex++
                            if (lineIndex >= maxLines) return
                        }
                        line = word
                        // Handle unbroken long words without drawing outside the panel.
                        while (line.isNotEmpty() && p.measureText(line) > availableWidth) {
                            val count = p.breakText(line, true, availableWidth, null).coerceAtLeast(1)
                            canvas.drawText(line.take(count), x, baseline + lineIndex * lineHeight, p)
                            lineIndex++
                            if (lineIndex >= maxLines) return
                            line = line.drop(count)
                        }
                    }
                }
                if (line.isNotEmpty() && lineIndex < maxLines)
                    canvas.drawText(line, x, baseline + lineIndex * lineHeight, p)
            }
            private fun yAt(time: Long) = ((time - day).toDouble() / (end - day) * fullHeight).toFloat()
            override fun onTouchEvent(event: MotionEvent): Boolean {
                val maxOffset = (activePeople.size * trackStep - participantWidth).coerceAtLeast(0f)
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = event.x; downY = event.y
                        initialOffset = horizontalOffset
                        swiping = false
                        if (event.x in scaleWidth..rightEdge && maxOffset > 0f)
                            parent.requestDisallowInterceptTouchEvent(true)
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.x - downX
                        val dy = event.y - downY
                        if (!swiping && kotlin.math.abs(dx) > densityPixels(8) &&
                            kotlin.math.abs(dx) > kotlin.math.abs(dy) && maxOffset > 0f) {
                            swiping = true
                        }
                        if (swiping) {
                            horizontalOffset = (initialOffset - dx).coerceIn(0f, maxOffset)
                            invalidate()
                        } else if (kotlin.math.abs(dy) > densityPixels(8)) {
                            parent.requestDisallowInterceptTouchEvent(false)
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        parent.requestDisallowInterceptTouchEvent(false)
                        return true
                    }
                }
                return false
            }
            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                canvas.save()
                canvas.clipRect(0f, 0f, width.toFloat(), height.toFloat())
                val eventLeft = rightEdge + densityPixels(8)
                val hourPaint = p
                for (hour in 0..24) {
                    val cal = Calendar.getInstance().apply { timeInMillis = day; add(Calendar.HOUR_OF_DAY, hour) }
                    val at = cal.timeInMillis
                    if (at > end) break
                    val y = yAt(at).coerceAtMost(fullHeight - densityPixels(2).toFloat())
                    hourPaint.color = if (darkTheme) Color.rgb(70, 78, 91) else Color.rgb(226, 231, 238)
                    hourPaint.strokeWidth = resources.displayMetrics.density
                    canvas.drawLine(scaleWidth - densityPixels(5), y, rightEdge, y, hourPaint)
                    hourPaint.color = muted
                    hourPaint.textSize = densityPixels(10).toFloat()
                    hourPaint.textAlign = Paint.Align.LEFT
                    canvas.drawText(String.format(Locale.ROOT, "%02d", cal.get(Calendar.HOUR_OF_DAY)), densityPixels(1).toFloat(),
                        (y + densityPixels(4)).coerceIn(densityPixels(10).toFloat(), fullHeight - densityPixels(2).toFloat()), hourPaint)
                }
                canvas.save()
                canvas.clipRect(scaleWidth, 0f, rightEdge, fullHeight.toFloat())
                activePeople.forEachIndexed { index, person ->
                    val x = scaleWidth + densityPixels(3) + index * trackStep - horizontalOffset
                    if (x + trackWidth < scaleWidth || x > rightEdge) return@forEachIndexed
                    p.color = personColor(person)
                    p.alpha = 38
                    canvas.drawRoundRect(RectF(x, 0f, x + trackWidth, fullHeight.toFloat()), densityPixels(5).toFloat(), densityPixels(5).toFloat(), p)
                    p.alpha = 255
                    val parts = sessions.filter { it.first == person }
                    val longest = parts.maxByOrNull { it.third - it.second }
                    parts.forEach { (_, start, finish) ->
                        val running = runningStarts[person] != null &&
                            start == maxOf(day, runningStarts[person]!!)
                        val currentFinish = if (running) minOf(end, System.currentTimeMillis()) else finish
                        val top = yAt(start)
                        val bottom = yAt(currentFinish)
                        p.color = personColor(person)
                        canvas.drawRoundRect(RectF(x, top, x + trackWidth, maxOf(top + densityPixels(2), bottom)),
                            densityPixels(5).toFloat(), densityPixels(5).toFloat(), p)
                        if (running) {
                            drawRunningBlocks(canvas, p,
                                RectF(x, top, x + trackWidth, maxOf(top + densityPixels(2), bottom)),
                                personColor(person), true)
                        }
                        if (longest != null && start == longest.second && finish == longest.third &&
                            bottom - top > densityPixels(24)) {
                            canvas.save()
                            canvas.clipRect(x, top, x + trackWidth, bottom)
                            canvas.rotate(90f, x + trackWidth / 2, (top + bottom) / 2)
                            p.color = Color.WHITE
                            p.textSize = densityPixels(10).toFloat()
                            p.textAlign = Paint.Align.CENTER
                            p.typeface = android.graphics.Typeface.DEFAULT_BOLD
                            val name = names[person] ?: ""
                            val maxChars = ((bottom - top) / densityPixels(7)).toInt().coerceAtLeast(1)
                            canvas.drawText(if (name.length > maxChars) name.take(maxChars - 1) + "…" else name,
                                x + trackWidth / 2, (top + bottom) / 2 + densityPixels(3), p)
                            p.typeface = android.graphics.Typeface.DEFAULT
                            canvas.restore()
                        }
                    }
                }
                canvas.restore()
                // Keep dots at exact times. Lay out descriptions with a minimum
                // separation, including clusters near the end of the day.
                val labelPositions = mutableListOf<Float>()
                events.forEach { event ->
                    val minimum = if (labelPositions.isEmpty()) densityPixels(12).toFloat()
                        else labelPositions.last() + labelGap
                    labelPositions.add(maxOf(yAt(event.happenedAt), minimum))
                }
                events.forEachIndexed { index, e ->
                    val y = yAt(e.happenedAt)
                    val labelY = labelPositions[index]
                    val eventColor = personColor(e.participantId)
                    val cardLeft = eventLeft
                    val cardRight = width - densityPixels(3).toFloat()
                    val cardTop = labelY - densityPixels(13)
                    val cardBottom = labelY + densityPixels(48)
                    // Use a solid 10% tint so text remains readable in both themes.
                    val cardBase = if (darkTheme) Color.rgb(21, 26, 35) else Color.WHITE
                    val tint = Color.rgb(
                        (Color.red(cardBase) * .90f + Color.red(eventColor) * .10f).toInt(),
                        (Color.green(cardBase) * .90f + Color.green(eventColor) * .10f).toInt(),
                        (Color.blue(cardBase) * .90f + Color.blue(eventColor) * .10f).toInt()
                    )
                    p.style = Paint.Style.FILL
                    p.color = tint
                    canvas.drawRoundRect(
                        RectF(cardLeft, cardTop, cardRight, cardBottom),
                        densityPixels(8).toFloat(), densityPixels(8).toFloat(), p
                    )
                    // Colored accent and connector identify the participant.
                    p.color = eventColor
                    canvas.drawRoundRect(
                        RectF(cardLeft, cardTop + densityPixels(5), cardLeft + densityPixels(3), cardBottom - densityPixels(5)),
                        densityPixels(2).toFloat(), densityPixels(2).toFloat(), p
                    )
                    p.strokeWidth = densityPixels(1).toFloat()
                    canvas.drawLine(rightEdge + densityPixels(1), y, cardLeft, labelY, p)
                    canvas.drawCircle(rightEdge - densityPixels(3), y, densityPixels(4).toFloat(), p)
                    p.color = ink
                    p.textAlign = Paint.Align.LEFT
                    p.textSize = densityPixels(11).toFloat()
                    p.typeface = android.graphics.Typeface.DEFAULT_BOLD
                    val stamp = clockFormat.format(Date(e.happenedAt))
                    val textX = cardLeft + densityPixels(9)
                    val textWidth = (cardRight - textX - densityPixels(6)).coerceAtLeast(densityPixels(30).toFloat())
                    drawWrappedText(canvas, "$stamp · ${e.type}", textX, labelY + densityPixels(4), textWidth, 2, densityPixels(13).toFloat())
                    p.typeface = android.graphics.Typeface.DEFAULT
                    p.color = muted
                    p.textSize = densityPixels(10).toFloat()
                    val description = (names[e.participantId] ?: "") + if (e.note.isBlank()) "" else " · " + e.note
                    drawWrappedText(canvas, description, textX, labelY + densityPixels(30), textWidth, 2, densityPixels(12).toFloat())
                }
                canvas.restore()
            }
        }
        // Height expands with event density; the surrounding statistics page scrolls.
        // Horizontal swiping remains limited to participant tracks.
        panel.addView(graph, LinearLayout.LayoutParams(-1, fullHeight))
        animateRunningChart(graph, runningStarts.isNotEmpty())
        if (activePeople.size > 3) {
            gap(panel, 5)
            panel.addView(label(if (polish) "Przesuń paski uczestników w bok" else "Swipe participant tracks sideways", 12f, false, muted))
        }
        target.addView(panel); gap(target, 14)
    }

}

