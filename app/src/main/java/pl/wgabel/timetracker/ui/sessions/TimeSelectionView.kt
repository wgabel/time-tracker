package pl.wgabel.timetracker.ui.sessions

import android.app.DatePickerDialog
import android.content.Context
import android.content.res.ColorStateList
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
import pl.wgabel.timetracker.time.SessionTimeSelection

/** Native picker composition; all controls render the same local date/time selection. */
internal class TimeSelectionView(
    context: Context,
    private val components: AppUi,
    private val config: DateTimeEditor.Config,
    private val onSave: (Long) -> Boolean,
    private val dismiss: () -> Unit,
    private val sessionSelection: SessionTimeSelection? = null
) : FrameLayout(context) {
    private val singleSelection = if (sessionSelection == null) DateTimeSelection(config.initial) else null
    private val selection: DateTimeSelection get() = sessionSelection?.selection ?: requireNotNull(singleSelection)
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
    private val summary = components.label("", components.dimension("timePickerDetailTextSp").toFloat(), false, components.color("muted")).apply {
        id = R.id.time_picker_selected
        gravity = Gravity.CENTER
        minHeight = components.dimensionPixels("buttonMinHeightDp")
        isFocusable = true
        contentDescription = context.getString(R.string.time_change_date)
        tooltipText = contentDescription
        setOnClickListener { selectDate() }
    }
    private val error = components.label("", components.dimension("timePickerDetailTextSp").toFloat(), false, components.color("dangerText")).apply {
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
        if (validationMessage() == null) {
            selection.timestamp?.let { timestamp ->
                if (onSave(timestamp)) dismiss()
                else { error.text = context.getString(R.string.time_save_failed); error.visibility = View.VISIBLE }
            }
        } else renderSelection()
    }.apply { id = R.id.time_picker_save }
    private val startChoice = sessionSelection?.let { createBoundaryButton(SessionTimeSelection.Boundary.START) }
    private val endChoice = sessionSelection?.let { createBoundaryButton(SessionTimeSelection.Boundary.END) }

    init {
        addView(content, LayoutParams(-1, -2))
        val header = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(View(context), LinearLayout.LayoutParams(components.dimensionPixels("buttonMinHeightDp"), 1))
        header.addView(components.label(config.title, components.dimension("timePickerTitleTextSp").toFloat(), true).apply { gravity = Gravity.CENTER },
            LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(ImageButton(context).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            imageTintList = ColorStateList.valueOf(components.color("muted"))
            background = components.shape(components.color("transparent"), components.dimension("buttonRadiusDp"))
            contentDescription = context.getString(R.string.cancel)
            setOnClickListener { dismiss() }
        }, LinearLayout.LayoutParams(components.dimensionPixels("buttonMinHeightDp"), components.dimensionPixels("buttonMinHeightDp")))
        content.addView(header)
        components.gap(content)
        if (startChoice != null && endChoice != null) {
            val boundaries = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            boundaries.addView(startChoice, LinearLayout.LayoutParams(0, components.dimensionPixels("timePickerBoundaryHeightDp"), 1f))
            boundaries.addView(View(context), LinearLayout.LayoutParams(components.dimensionPixels("timePickerActionGapDp"), 1))
            boundaries.addView(endChoice, LinearLayout.LayoutParams(0, components.dimensionPixels("timePickerBoundaryHeightDp"), 1f))
            content.addView(boundaries)
            components.gap(content, components.dimension("timePickerSmallGapDp"))
        } else {
            content.addView(components.label(context.getString(R.string.time_choose_daytime), components.dimension("timePickerHintTextSp").toFloat(), false, components.color("muted")))
        }
        content.addView(scale)
        components.gap(content)
        val fields = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        fields.addView(hourControl, LinearLayout.LayoutParams(0, -2, 1f))
        fields.addView(View(context), LinearLayout.LayoutParams(components.dimensionPixels("defaultGapDp"), 1))
        fields.addView(minuteControl, LinearLayout.LayoutParams(0, -2, 1f))
        content.addView(fields)
        components.gap(content, components.dimension("timePickerAdjustmentGapDp"))
        val adjustments = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        adjustments.addView(createMinuteAdjustment("−", -1, R.string.time_minute_decrease),
            LinearLayout.LayoutParams(components.dimensionPixels("timePickerAdjustmentWidthDp"), components.dimensionPixels("buttonMinHeightDp")))
        adjustments.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
        adjustments.addView(createMinuteAdjustment("+", 1, R.string.time_minute_increase),
            LinearLayout.LayoutParams(components.dimensionPixels("timePickerAdjustmentWidthDp"), components.dimensionPixels("buttonMinHeightDp")))
        content.addView(adjustments)
        components.gap(content)
        content.addView(View(context).apply { setBackgroundColor(components.color("cardStroke")) },
            LinearLayout.LayoutParams(-1, components.dimensionPixels("timePickerStrokeDp")))
        content.addView(summary)
        secondsToggle?.let { content.addView(it) }
        secondsControl?.let { content.addView(it) }
        content.addView(offsetToggle)
        if (config.quickOffsets) addQuickOffsets()
        content.addView(error)
        components.gap(content, components.dimension("timePickerActionGapDp"))
        val actions = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(components.styledAction(context.getString(R.string.cancel), ButtonRole.SECONDARY, dismiss),
            LinearLayout.LayoutParams(0, components.dimensionPixels("buttonMinHeightDp"), 1f))
        actions.addView(View(context), LinearLayout.LayoutParams(components.dimensionPixels("timePickerActionGapDp"), 1))
        actions.addView(save, LinearLayout.LayoutParams(0, components.dimensionPixels("buttonMinHeightDp"), 1f))
        content.addView(actions)
        renderSelection()
    }

    private fun createBoundaryButton(boundary: SessionTimeSelection.Boundary): Button =
        components.styledAction("", ButtonRole.SECONDARY) {
            sessionSelection?.selectBoundary(boundary)
            renderSelection()
        }.apply {
            id = if (boundary == SessionTimeSelection.Boundary.START) R.id.time_session_start else R.id.time_session_end
            textSize = components.dimension("timePickerBoundaryTextSp").toFloat()
            minWidth = 0; minimumWidth = 0
            setPadding(components.dimensionPixels("timePickerBoundaryPaddingDp"), 0, components.dimensionPixels("timePickerBoundaryPaddingDp"), 0)
        }

    private fun renderBoundary(button: Button?, boundary: SessionTimeSelection.Boundary, title: Int) {
        val session = sessionSelection ?: return
        button ?: return
        val draft = if (boundary == SessionTimeSelection.Boundary.START) session.start else session.end
        val time = DatePresentation.timeOfDay(resources, draft.dateTime.toLocalTime(), false)
        button.text = "${context.getString(title)}\n$time"
        button.contentDescription = "${context.getString(title)}: ${DatePresentation.calendarDate(resources, draft.dateTime.toLocalDate())}, $time"
        button.isSelected = session.selectedBoundary == boundary
        button.background = components.shape(components.color(if (button.isSelected) "subtle" else "subtleNeutral"),
            components.dimension("buttonRadiusDp")).apply {
            setStroke(components.dimensionPixels("timePickerStrokeDp"), components.color(if (button.isSelected) "blue" else "cardStroke"))
        }
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
            textSize = components.dimension("timePickerAdjustmentTextSp").toFloat()
            minWidth = 0; minimumWidth = 0
            setPadding(0, 0, 0, 0)
            contentDescription = context.getString(description)
        }

    private fun createDetailsButton(text: String, onClick: () -> Unit): TextView =
        components.label(text, components.dimension("timePickerDetailTextSp").toFloat(), false, components.color("muted")).apply {
            minHeight = components.dimensionPixels("buttonMinHeightDp")
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
                textSize = components.dimension("timePickerShortcutTextSp").toFloat(); minWidth = 0; minimumWidth = 0; setPadding(0, 0, 0, 0)
            }, LinearLayout.LayoutParams(0, components.dimensionPixels("buttonMinHeightDp"), 1f).apply {
                setMargins(components.dimensionPixels("timePickerShortcutMarginDp"), 0, components.dimensionPixels("timePickerShortcutMarginDp"), 0)
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
        renderBoundary(startChoice, SessionTimeSelection.Boundary.START, R.string.session_time_start)
        renderBoundary(endChoice, SessionTimeSelection.Boundary.END, R.string.session_time_end)
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
        val problem = validationMessage()
        error.text = problem.orEmpty()
        error.visibility = if (problem == null) View.GONE else View.VISIBLE
        save.isEnabled = problem == null
        save.alpha = if (save.isEnabled) 1f else components.proportion("timePickerDisabledOpacity")
    }

    private fun validationMessage(): String? {
        sessionSelection?.let { session ->
            return when (session.validationError(config.max)) {
                SessionTimeSelection.ValidationError.START_MISSING_LOCAL_TIME -> boundaryError(R.string.session_time_start,
                    context.getString(R.string.time_missing_local_time))
                SessionTimeSelection.ValidationError.END_MISSING_LOCAL_TIME -> boundaryError(R.string.session_time_end,
                    context.getString(R.string.time_missing_local_time))
                SessionTimeSelection.ValidationError.START_AFTER_MAXIMUM -> boundaryError(R.string.session_time_start,
                    context.getString(R.string.time_after_maximum, maximumDescription()))
                SessionTimeSelection.ValidationError.END_AFTER_MAXIMUM -> boundaryError(R.string.session_time_end,
                    context.getString(R.string.time_after_maximum, maximumDescription()))
                SessionTimeSelection.ValidationError.END_NOT_AFTER_START -> context.getString(R.string.session_time_order)
                null -> null
            }
        }
        return when (selection.validationError(config.min, config.max)) {
            DateTimeSelection.ValidationError.MISSING_LOCAL_TIME -> context.getString(R.string.time_missing_local_time)
            DateTimeSelection.ValidationError.BEFORE_MINIMUM -> context.getString(R.string.time_before_minimum,
                DatePresentation.dateTime(resources).format(Date(requireNotNull(config.min))))
            DateTimeSelection.ValidationError.AFTER_MAXIMUM -> context.getString(R.string.time_after_maximum,
                maximumDescription())
            null -> null
        }
    }

    private fun boundaryError(title: Int, message: String): String =
        context.getString(R.string.session_time_error, context.getString(title), message)

    private fun maximumDescription(): String =
        DatePresentation.dateTime(resources).format(Date(requireNotNull(config.max)))
}
