package pl.wgabel.timetracker.ui.sessions

import android.app.DatePickerDialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import java.time.LocalDate
import java.util.Date
import pl.wgabel.timetracker.R
import pl.wgabel.timetracker.design.AppUi
import pl.wgabel.timetracker.design.AppUi.ButtonRole
import pl.wgabel.timetracker.time.DatePresentation
import pl.wgabel.timetracker.time.DateTimeSelection

/** Native picker composition; all controls render the same local date/time selection. */
internal class TimeSelectionView(
    context: Context,
    private val components: AppUi,
    private val config: DateTimeEditor.Config,
    private val onSave: (Long) -> Boolean,
    private val dismiss: () -> Unit
) : FrameLayout(context) {
    private val selection = DateTimeSelection(config.initial)
    private val content = components.card()
    private val scale = DayTimeScale(context, components) {
        selection.selectMinuteFromScale(it)
        renderSelection()
    }
    private val hourControl = createControl(R.string.time_hour, R.id.time_hour, 23,
        R.string.time_hour_increase, R.string.time_hour_decrease) {
        selection.adjustHours(it); renderSelection()
    }
    private val minuteControl = createControl(R.string.time_minute, R.id.time_minute, 59,
        R.string.time_minute_increase, R.string.time_minute_decrease) {
        selection.adjustMinutes(it); renderSelection()
    }
    private val secondsControl = if (config.seconds) createControl(R.string.time_seconds, R.id.time_second, 59,
        R.string.time_second_increase, R.string.time_second_decrease) {
        selection.adjustSeconds(it); renderSelection()
    }.apply { visibility = View.GONE } else null
    private val summary = components.label("", 13f, false, components.color("muted")).apply {
        id = R.id.time_picker_selected
        gravity = Gravity.CENTER
        minHeight = components.densityPixels(48)
        isFocusable = true
        contentDescription = context.getString(R.string.time_change_date)
        tooltipText = contentDescription
        setOnClickListener { selectDate() }
    }
    private val error = components.label("", 13f, false, components.color("dangerText")).apply {
        id = R.id.time_picker_error
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
    }
    private val secondsToggle = if (config.seconds) createDetailsButton("") {
        secondsControl?.visibility = if (secondsControl?.visibility == View.VISIBLE) View.GONE else View.VISIBLE
    } else null
    private val offsetToggle = createDetailsButton("") {
        val offsets = selection.availableOffsets
        if (offsets.size == 2) {
            selection.selectOffset(offsets[(offsets.indexOf(selection.selectedOffset) + 1) % offsets.size])
            renderSelection()
        }
    }
    private val save = components.styledAction(context.getString(R.string.save), ButtonRole.PRIMARY) {
        if (selection.validationError(config.min, config.max) == null) {
            selection.timestamp?.let { timestamp ->
                if (onSave(timestamp)) dismiss()
                else { error.text = context.getString(R.string.time_save_failed); error.visibility = View.VISIBLE }
            }
        } else renderSelection()
    }.apply { id = R.id.time_picker_save }

    init {
        addView(content, LayoutParams(-1, -2))
        val header = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(View(context), LinearLayout.LayoutParams(components.densityPixels(48), 1))
        header.addView(components.label(config.title, 21f, true).apply { gravity = Gravity.CENTER },
            LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(ImageButton(context).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            imageTintList = ColorStateList.valueOf(components.color("muted"))
            background = components.shape(Color.TRANSPARENT, 9)
            contentDescription = context.getString(R.string.cancel)
            setOnClickListener { dismiss() }
        }, LinearLayout.LayoutParams(components.densityPixels(48), components.densityPixels(48)))
        content.addView(header)
        components.gap(content, 12)
        content.addView(components.label(context.getString(R.string.time_choose_daytime), 15f, false, components.color("muted")))
        content.addView(scale)
        components.gap(content, 12)
        val fields = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        fields.addView(hourControl, LinearLayout.LayoutParams(0, -2, 1f))
        fields.addView(View(context), LinearLayout.LayoutParams(components.densityPixels(12), 1))
        fields.addView(minuteControl, LinearLayout.LayoutParams(0, -2, 1f))
        content.addView(fields)
        components.gap(content, 18)
        val adjustments = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        adjustments.addView(createMinuteAdjustment("−", -1, R.string.time_minute_decrease),
            LinearLayout.LayoutParams(components.densityPixels(52), components.densityPixels(48)))
        adjustments.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
        adjustments.addView(createMinuteAdjustment("+", 1, R.string.time_minute_increase),
            LinearLayout.LayoutParams(components.densityPixels(52), components.densityPixels(48)))
        content.addView(adjustments)
        components.gap(content, 12)
        content.addView(View(context).apply { setBackgroundColor(components.color("cardStroke")) },
            LinearLayout.LayoutParams(-1, components.densityPixels(1)))
        content.addView(summary)
        secondsToggle?.let { content.addView(it) }
        secondsControl?.let { content.addView(it) }
        content.addView(offsetToggle)
        if (config.quickOffsets) addQuickOffsets()
        content.addView(error)
        components.gap(content, 10)
        val actions = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(components.styledAction(context.getString(R.string.cancel), ButtonRole.SECONDARY, dismiss),
            LinearLayout.LayoutParams(0, components.densityPixels(48), 1f))
        actions.addView(View(context), LinearLayout.LayoutParams(components.densityPixels(10), 1))
        actions.addView(save, LinearLayout.LayoutParams(0, components.densityPixels(48), 1f))
        content.addView(actions)
        renderSelection()
    }

    private fun createControl(title: Int, valueId: Int, maximum: Int, increase: Int, decrease: Int,
                              onAdjust: (Int) -> Unit): SwipeTimePartControl =
        SwipeTimePartControl(context, components, context.getString(title), valueId, maximum,
            context.getString(increase), context.getString(decrease), onAdjust)

    private fun createMinuteAdjustment(text: String, amount: Int, description: Int): Button =
        components.styledAction(text, ButtonRole.SECONDARY) {
            selection.adjustMinutes(amount)
            renderSelection()
            minuteControl.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SELECTED)
        }.apply {
            textSize = 24f
            minWidth = 0; minimumWidth = 0
            setPadding(0, 0, 0, 0)
            contentDescription = context.getString(description)
        }

    private fun createDetailsButton(text: String, onClick: () -> Unit): TextView =
        components.label(text, 13f, false, components.color("muted")).apply {
            minHeight = components.densityPixels(48)
            isFocusable = true
            gravity = Gravity.CENTER_VERTICAL
            setOnClickListener { onClick() }
        }

    private fun addQuickOffsets() {
        val shortcuts = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        val toggle = createDetailsButton(context.getString(R.string.time_shortcuts)) {
            shortcuts.visibility = if (shortcuts.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }.apply {
            id = R.id.time_picker_shortcuts
            contentDescription = context.getString(R.string.time_shortcuts)
        }
        val buttons = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        listOf(5, 10, 15, 30).forEach { minutes ->
            buttons.addView(components.styledAction(context.getString(R.string.time_minutes_ago, minutes), ButtonRole.SECONDARY) {
                selection.selectTimestamp(System.currentTimeMillis() - minutes * 60_000L)
                renderSelection()
            }.apply {
                textSize = 12f; minWidth = 0; minimumWidth = 0; setPadding(0, 0, 0, 0)
            }, LinearLayout.LayoutParams(0, components.densityPixels(48), 1f).apply {
                setMargins(components.densityPixels(2), 0, components.densityPixels(2), 0)
            })
        }
        shortcuts.addView(buttons)
        content.addView(toggle)
        content.addView(shortcuts)
    }

    private fun selectDate() {
        val selected = selection.dateTime
        DatePickerDialog(context, { _, year, month, day ->
            selection.selectDate(LocalDate.of(year, month + 1, day))
            renderSelection()
        }, selected.year, selected.monthValue - 1, selected.dayOfMonth).apply {
            config.min?.let { datePicker.minDate = it }
            config.max?.let { datePicker.maxDate = it }
        }.show()
    }

    private fun renderSelection() {
        val selected = selection.dateTime
        val time = DatePresentation.timeOfDay(resources, selected.toLocalTime(), config.seconds)
        hourControl.setValue(selected.hour)
        minuteControl.setValue(selected.minute)
        secondsControl?.setValue(selected.second)
        scale.setMinuteOfDay(selection.minuteOfDay, time)
        summary.text = context.getString(R.string.time_selected, DatePresentation.calendarDate(resources, selected.toLocalDate()), time)
        summary.contentDescription = "${summary.text}. ${context.getString(R.string.time_change_date)}"
        secondsToggle?.text = context.getString(R.string.time_seconds_value, selected.second)
        offsetToggle.visibility = if (selection.availableOffsets.size == 2) View.VISIBLE else View.GONE
        offsetToggle.text = context.getString(R.string.time_repeated_hour, selection.selectedOffset?.id.orEmpty())
        val problem = selection.validationError(config.min, config.max)
        error.text = when (problem) {
            DateTimeSelection.ValidationError.MISSING_LOCAL_TIME -> context.getString(R.string.time_missing_local_time)
            DateTimeSelection.ValidationError.BEFORE_MINIMUM -> context.getString(R.string.time_before_minimum,
                DatePresentation.dateTime(resources).format(Date(requireNotNull(config.min))))
            DateTimeSelection.ValidationError.AFTER_MAXIMUM -> context.getString(R.string.time_after_maximum,
                DatePresentation.dateTime(resources).format(Date(requireNotNull(config.max))))
            null -> ""
        }
        error.visibility = if (problem == null) View.GONE else View.VISIBLE
        save.isEnabled = problem == null
        save.alpha = if (save.isEnabled) 1f else .45f
    }
}
