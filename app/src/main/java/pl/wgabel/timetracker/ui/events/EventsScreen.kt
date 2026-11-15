package pl.wgabel.timetracker.ui.events

import android.app.AlertDialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import java.util.Date
import pl.wgabel.timetracker.AppContainer
import pl.wgabel.timetracker.AppNavigation
import pl.wgabel.timetracker.R
import pl.wgabel.timetracker.design.AppUi.ButtonRole
import pl.wgabel.timetracker.ui.HistoryActionTray
import pl.wgabel.timetracker.ui.ScreenUi

internal class EventsScreen(
    context: Context, container: AppContainer, navigation: AppNavigation
) : ScreenUi(context, container) {
    override val navigator = navigation
    fun addHistoricalEvent(sessionId: Long, person: Int, started: Long, ended: Long) {
        if (ended <= started || isArchived()) return
        // Use the start as the initial event time. The editor checks the complete
        // timestamp, not only the displayed hour, against the session boundaries.
        editEvent(null, sessionId, person, started, "Inne", "")
    }

    fun editEvent(
        id: Long?, sessionId: Long, person: Int, timestamp: Long,
        currentType: String, currentNote: String
    ) {
        // Resolve session boundaries before opening the form.
        if (isArchived()) return
        val sessionBounds = sessionRepository.findSession(requireNotNull(projectId), sessionId, person)
            ?.let { it.startedAt to it.endedAt }
        if (sessionBounds == null) {
            Toast.makeText(this, getString(R.string.session_update_failed), Toast.LENGTH_LONG).show()
            return
        }
        EventEditor(this, appUi).show(
            id = id,
            timestamp = timestamp,
            currentType = currentType,
            currentNote = currentNote,
            sessionBounds = sessionBounds,
            timeButtonFactory = { styledAction(it, ButtonRole.SECONDARY) {} },
            rangeLabelFactory = { label(it, 13f, false, muted) },
            cancelText = getString(R.string.cancel),
            saveText = getString(R.string.save),
            onSave = { chosenTimestamp, type, description ->
                if (!sessionRepository.eventWithinSession(projectId!!, sessionId, person, chosenTimestamp)) {
                    false
                } else if (container.eventService.saveEvent(projectId!!, sessionId, person, id, chosenTimestamp, type, description)) {
                    navigator.refreshContent()
                    true
                } else false
            },
            onInvalidTime = {
                Toast.makeText(this, "Godzina wydarzenia musi mieścić się w czasie pomiaru", Toast.LENGTH_LONG).show()
            },
            onSaveFailed = {
                Toast.makeText(this, getString(R.string.session_update_failed), Toast.LENGTH_LONG).show()
            }
        )
    }

    private fun eventCategoryIcon(type: String): View {
        val color = when (type) {
            "Lek" -> Color.rgb(184, 77, 87)
            "Posiłek" -> Color.rgb(163, 108, 34)
            "Sen" -> Color.rgb(117, 97, 181)
            "Spacer" -> Color.rgb(40, 123, 86)
            else -> Color.rgb(83, 113, 139)
        }
        return object : View(this) {
            private val pen = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 2.4f
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                this.color = color
            }
            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                canvas.save()
                canvas.scale(width / 24f, height / 24f)
                when (type) {
                    "Lek" -> {
                        canvas.save()
                        canvas.rotate(-45f, 12f, 12f)
                        canvas.drawRoundRect(RectF(6f, 2f, 18f, 22f), 6f, 6f, pen)
                        canvas.drawLine(6f, 12f, 18f, 12f, pen)
                        canvas.restore()
                    }
                    "Posiłek" -> {
                        canvas.drawLine(5f, 3f, 5f, 10f, pen)
                        canvas.drawLine(9f, 3f, 9f, 10f, pen)
                        canvas.drawLine(5f, 10f, 9f, 10f, pen)
                        canvas.drawLine(7f, 10f, 7f, 21f, pen)
                        canvas.drawPath(Path().apply {
                            moveTo(19f, 21f); lineTo(19f, 3f)
                            cubicTo(13f, 6f, 13f, 13f, 19f, 14f)
                        }, pen)
                    }
                    "Sen" -> canvas.drawPath(Path().apply {
                        moveTo(19f, 15f)
                        cubicTo(12f, 17f, 7f, 12f, 9f, 4f)
                        cubicTo(0f, 10f, 4f, 21f, 12f, 21f)
                        cubicTo(15f, 21f, 17f, 19f, 19f, 15f)
                    }, pen)
                    "Spacer" -> {
                        canvas.drawOval(RectF(4f, 3f, 10f, 12f), pen)
                        canvas.drawOval(RectF(14f, 12f, 20f, 21f), pen)
                        canvas.drawCircle(12f, 5f, 1f, pen)
                        canvas.drawCircle(12f, 19f, 1f, pen)
                    }
                    else -> canvas.drawPath(Path().apply {
                        moveTo(5f, 3f); lineTo(19f, 3f)
                        lineTo(19f, 21f); lineTo(12f, 16f)
                        lineTo(5f, 21f); close()
                    }, pen)
                }
                canvas.restore()
            }
        }.apply { contentDescription = type }
    }

    fun eventList(sessionId: Long, person: Int, target: LinearLayout) {
        val eventColor = personColor(person)
        eventRepository.sessionEvents(requireNotNull(projectId), sessionId).forEach { event ->
                val id = event.id
                val timestamp = event.happenedAt
                val type = event.type
                val note = event.note
                val eventRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(densityPixels(20), 0, densityPixels(6), 0)
                }
                // A connected timeline on the section background, not another card.
                val rail = object : View(this) {
                    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = eventColor }
                    override fun onDraw(canvas: Canvas) {
                        super.onDraw(canvas)
                        val cx = width / 2f
                        paint.strokeWidth = densityPixels(2).toFloat()
                        canvas.drawLine(cx, 0f, cx, height.toFloat(), paint)
                        canvas.drawCircle(cx, height / 2f, densityPixels(5).toFloat(), paint)
                    }
                }
                eventRow.addView(rail, LinearLayout.LayoutParams(densityPixels(14), densityPixels(62)))
                val details = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(densityPixels(10), 0, 0, 0)
                }
                val eventTitle = if (note.isNotBlank()) note else type
                details.addView(label(eventTitle, 14f, true))
                val secondary = if (note.isNotBlank()) type + " · " else ""
                details.addView(label(secondary + clockFormat.format(Date(timestamp)), 12f, false, muted))
                val categoryIcon = eventCategoryIcon(type)
                eventRow.addView(categoryIcon, LinearLayout.LayoutParams(densityPixels(18), densityPixels(18)).apply {
                    leftMargin = densityPixels(8)
                })
                eventRow.addView(details, LinearLayout.LayoutParams(0, -2, 1f))
                if (!isArchived()) {
                    // Keep the timeline row uncluttered until the event is tapped.
                    val tray = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL or Gravity.RIGHT
                        clipChildren = true
                    }
                    val edit = editIconButton {
                        editEvent(id, sessionId, person, timestamp, type, note)
                    }.apply { background = shape(subtle, 10) }
                    val delete = deleteIconButton {
                        AlertDialog.Builder(this).setTitle("Usunąć wydarzenie?")
                            .setNegativeButton(getString(R.string.cancel), null)
                            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                                container.eventService.deleteEvent(requireNotNull(projectId), id)
                                navigator.refreshContent()
                            }.show()
                    }.apply {
                        background = shape(
                            if (darkTheme) Color.rgb(76, 45, 49) else Color.rgb(252, 233, 233), 10)
                    }
                    tray.addView(edit, LinearLayout.LayoutParams(densityPixels(48), densityPixels(48)))
                    tray.addView(View(this), LinearLayout.LayoutParams(densityPixels(8), densityPixels(1)))
                    tray.addView(delete, LinearLayout.LayoutParams(densityPixels(48), densityPixels(48)))
                    val arrow = label("›", 23f, false, muted).apply { gravity = Gravity.CENTER }
                    eventRow.addView(arrow, LinearLayout.LayoutParams(densityPixels(20), densityPixels(48)))
                    eventRow.addView(tray, LinearLayout.LayoutParams(0, densityPixels(48)))
                    HistoryActionTray(
                        eventRow, tray, arrow,
                        HistoryActionTray.Dimension.WIDTH,
                        expandedSizePx = densityPixels(104),
                        slideOffsetPx = densityPixels(36)
                    ).attach(eventTitle + ". Dotknij, aby pokazać lub ukryć akcje wydarzenia")
                }
                target.addView(eventRow)
        }
    }


    fun addEvent(person: Int) {
        // Capture at the button press, before querying or opening the editor.
        val timestamp = System.currentTimeMillis()
        if (isArchived()) return
        val session = sessionRepository.findActiveSession(requireNotNull(projectId), person) ?: return
        editEvent(null, session.id, person, timestamp, "Inne", "")
    }
}
