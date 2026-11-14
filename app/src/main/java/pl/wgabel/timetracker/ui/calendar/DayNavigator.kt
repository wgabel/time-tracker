package pl.wgabel.timetracker.ui.calendar

import android.animation.ValueAnimator
import android.app.DatePickerDialog
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import pl.wgabel.timetracker.AppContainer
import pl.wgabel.timetracker.AppNavigation
import pl.wgabel.timetracker.domain.StatisticsCalculator
import pl.wgabel.timetracker.time.DatePresentation
import pl.wgabel.timetracker.ui.ScreenUi

internal class DayNavigator(
    context: Context, container: AppContainer, navigation: AppNavigation
) : ScreenUi(context, container) {
    override val navigator = navigation
    lateinit var stickyDateContainer: LinearLayout
    private var heading: View? = null
    private var headingExpanded = true
    private var headingAnimator: ValueAnimator? = null
    fun clearHeading() {
        headingAnimator?.cancel()
        heading = null
        headingExpanded = true
    }
    fun setCalendarHeadingExpanded(expanded: Boolean) {
        if (headingExpanded == expanded) return
        val heading = heading ?: return
        headingExpanded = expanded
        headingAnimator?.cancel()
        val startHeight = heading.height
        val targetHeight = if (expanded) densityPixels(40) else 0
        headingAnimator = android.animation.ValueAnimator.ofInt(startHeight, targetHeight).apply {
            duration = 220L
            interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            addUpdateListener { animator ->
                val height = animator.animatedValue as Int
                heading.layoutParams = heading.layoutParams.apply { this.height = height }
                heading.alpha = height.toFloat() / densityPixels(40)
            }
            start()
        }
    }

    // Shared day navigation for Statistics timelines and History. Timers always shows today.
    fun renderDayNavigator(selectedOffset: Int, onDaySelected: (Int) -> Unit) {
        val selected = dayStart(selectedOffset)
        val today = dayStart(0)
        val polish = resources.configuration.locales[0].language == "pl"
        val calendar = Calendar.getInstance().apply {
            timeInMillis = selected
            firstDayOfWeek = Calendar.MONDAY
            val back = (get(Calendar.DAY_OF_WEEK) + 5) % 7
            add(Calendar.DAY_OF_YEAR, -back)
        }
        val weekStart = calendar.timeInMillis
        val weekEnd = Calendar.getInstance().apply {
            timeInMillis = weekStart
            add(Calendar.DAY_OF_YEAR, 7)
        }.timeInMillis
        val activity = StatisticsCalculator.activityByDay(
            sessionRepository.sessionsInRange(requireNotNull(projectId), weekStart, weekEnd),
            eventRepository.eventsForDay(requireNotNull(projectId), weekStart, weekEnd),
            weekStart, weekEnd, System.currentTimeMillis()
        )
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(if (darkTheme) surface else darkHeader)
        }
        val calendarText = Color.WHITE
        val calendarMuted = if (darkTheme) Color.rgb(171, 181, 196) else Color.rgb(195, 217, 215)
        val calendarSelected = if (darkTheme) Color.rgb(55, 106, 156) else Color.rgb(67, 138, 153)
        val heading = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val headingText = java.text.SimpleDateFormat("LLLL yyyy", resources.configuration.locales[0]).format(Date(selected))
        heading.addView(label(headingText, 15f, true, calendarText).apply { setOnClickListener { heading.getChildAt(1).performClick() } },
            LinearLayout.LayoutParams(0, -2, 1f))
        heading.addView(label("▦", 23f, false, calendarText).apply {
            gravity = Gravity.CENTER
            contentDescription = if (resources.configuration.locales[0].language == "pl") "Wybierz datę" else "Choose date"
            setOnClickListener {
            val chosen = Calendar.getInstance().apply { timeInMillis = selected }
            DatePickerDialog(this@DayNavigator, { _, year, month, day ->
                val target = Calendar.getInstance().apply {
                    set(year, month, day, 0, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                var offset = 0
                while (dayStart(offset) > target) offset--
                while (dayStart(offset + 1) <= target) offset++
                onDaySelected(offset)
            }, chosen.get(Calendar.YEAR), chosen.get(Calendar.MONTH), chosen.get(Calendar.DAY_OF_MONTH)).apply {
                datePicker.maxDate = System.currentTimeMillis()
            }.show()
            }
        }, LinearLayout.LayoutParams(densityPixels(46), densityPixels(40)))
        panel.addView(heading, LinearLayout.LayoutParams(-1, densityPixels(40)))
        this.heading = heading
        headingExpanded = true
        val strip = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val shortDay = DatePresentation.weekdayShort(resources)
        for (index in 0 until 7) {
            val date = Calendar.getInstance().apply {
                timeInMillis = weekStart
                add(Calendar.DAY_OF_YEAR, index)
            }.timeInMillis
            val chosen = date == selected
            val future = date > today
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(densityPixels(1), densityPixels(3), densityPixels(1), densityPixels(3))
                background = shape(if (chosen) calendarSelected else Color.TRANSPARENT, 12)
                alpha = if (future) .40f else 1f
            }
            cell.addView(label(shortDay.format(Date(date)), 11f, false, calendarMuted).apply { gravity = Gravity.CENTER })
            cell.addView(label(Calendar.getInstance().apply { timeInMillis = date }.get(Calendar.DAY_OF_MONTH).toString(),
                16f, true, calendarText).apply { gravity = Gravity.CENTER })
            val dots = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
            activity[date]?.take(3)?.forEach { person ->
                dots.addView(View(this).apply { background = shape(personColor(person), 4) },
                    LinearLayout.LayoutParams(densityPixels(5), densityPixels(5)).apply { setMargins(densityPixels(1), densityPixels(3), densityPixels(1), 0) })
            }
            cell.addView(dots, LinearLayout.LayoutParams(-1, densityPixels(11)))
            if (!future) cell.setOnClickListener {
                var offset = 0
                while (dayStart(offset) > date) offset--
                while (dayStart(offset + 1) <= date) offset++
                onDaySelected(offset)
            }
            strip.addView(cell, LinearLayout.LayoutParams(0, densityPixels(59), 1f))
        }
        // Week navigation via swipes is handled by the individual day cells.
        for (index in 0 until strip.childCount) {
            val cell = strip.getChildAt(index)
            var downX = 0f
            var downY = 0f
            cell.setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        downX = event.rawX
                        downY = event.rawY
                        false
                    }
                    android.view.MotionEvent.ACTION_UP -> {
                        val dx = event.rawX - downX
                        val dy = event.rawY - downY
                        if (kotlin.math.abs(dx) > densityPixels(48) && kotlin.math.abs(dx) > kotlin.math.abs(dy) * 1.5f) {
                            val target = Calendar.getInstance().apply {
                                timeInMillis = selected
                                add(Calendar.DAY_OF_YEAR, if (dx > 0) -7 else 7)
                            }.timeInMillis
                            if (target <= today) {
                                var offset = selectedOffset
                                while (dayStart(offset) > target) offset--
                                while (dayStart(offset + 1) <= target) offset++
                                onDaySelected(offset)
                            }
                            true
                        } else false
                    }
                    else -> false
                }
            }
        }
        panel.addView(strip)
        stickyDateContainer.addView(panel)
    }
}
