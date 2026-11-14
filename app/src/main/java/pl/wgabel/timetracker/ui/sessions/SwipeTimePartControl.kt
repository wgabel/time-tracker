package pl.wgabel.timetracker.ui.sessions

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale
import pl.wgabel.timetracker.R
import pl.wgabel.timetracker.design.AppUi

/** A precise time field with vertical drag, side arrows and accessibility/keyboard actions. */
internal class SwipeTimePartControl(
    context: Context,
    private val components: AppUi,
    private val title: String,
    valueId: Int,
    private val maximum: Int,
    increaseDescription: String,
    decreaseDescription: String,
    private val onAdjust: (Int) -> Unit
) : LinearLayout(context) {
    private var value = 0
    private val locale: Locale get() = resources.configuration.locales[0]
    private val frameBackground = components.shape(components.color("surface"), 13).apply {
        setStroke(components.densityPixels(1), components.color("cardStroke"))
    }
    private val valueLabel = components.label("00", 36f, true).apply {
        id = valueId
        gravity = Gravity.CENTER
        isFocusable = true
        isClickable = true
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        setPadding(0, 0, 0, 0)
        setAutoSizeTextTypeUniformWithConfiguration(20, 36, 1, TypedValue.COMPLEX_UNIT_SP)
        setOnClickListener { requestFocus() }
    }

    init {
        orientation = VERTICAL
        addView(components.label(title, 14f, false, components.color("muted")))
        components.gap(this, 8)
        val frame = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = frameBackground
        }
        frame.addView(valueLabel, LayoutParams(0, components.densityPixels(96), 1f))
        val arrows = LinearLayout(context).apply { orientation = VERTICAL }
        arrows.addView(createArrowButton(true, increaseDescription) { adjustValue(1) },
            LayoutParams(components.densityPixels(48), components.densityPixels(48)))
        arrows.addView(createArrowButton(false, decreaseDescription) { adjustValue(-1) },
            LayoutParams(components.densityPixels(48), components.densityPixels(48)))
        frame.addView(arrows)
        addView(frame, LayoutParams(-1, -2))
        valueLabel.setOnFocusChangeListener { _, focused -> updateOutline(focused) }
        valueLabel.accessibilityDelegate = object : View.AccessibilityDelegate() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.className = "android.widget.NumberPicker"
                info.rangeInfo = AccessibilityNodeInfo.RangeInfo.obtain(
                    AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_INT, 0f, maximum.toFloat(), value.toFloat())
                info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD)
                info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)
            }

            override fun performAccessibilityAction(host: View, action: Int, arguments: Bundle?): Boolean = when (action) {
                AccessibilityNodeInfo.ACTION_SCROLL_FORWARD -> { adjustValue(1); true }
                AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD -> { adjustValue(-1); true }
                else -> super.performAccessibilityAction(host, action, arguments)
            }
        }
        valueLabel.setOnKeyListener { _, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN) false
            else when (keyCode) {
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_PLUS -> { adjustValue(1); true }
                KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_MINUS -> { adjustValue(-1); true }
                else -> false
            }
        }
        var pointerId = MotionEvent.INVALID_POINTER_ID
        var initialY = 0f
        var previousSteps = 0
        var moved = false
        val pixelsPerStep = components.densityPixels(18).toFloat()
        valueLabel.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    pointerId = event.getPointerId(0)
                    initialY = event.y
                    previousSteps = 0
                    moved = false
                    view.requestFocus()
                    view.parent.requestDisallowInterceptTouchEvent(true)
                    updateOutline(true)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val index = event.findPointerIndex(pointerId)
                    if (index >= 0) {
                        val steps = ((initialY - event.getY(index)) / pixelsPerStep).toInt()
                        val difference = steps - previousSteps
                        if (difference != 0) {
                            onAdjust(difference)
                            previousSteps = steps
                            moved = true
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    pointerId = MotionEvent.INVALID_POINTER_ID
                    view.parent.requestDisallowInterceptTouchEvent(false)
                    updateOutline(view.hasFocus())
                    if (event.actionMasked == MotionEvent.ACTION_UP) {
                        if (!moved) view.performClick()
                        else view.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SELECTED)
                    }
                    true
                }
                else -> true
            }
        }
        valueLabel.contentDescription = context.getString(R.string.time_part_accessibility, title, "00")
    }

    fun setValue(selected: Int) {
        value = selected.coerceIn(0, maximum)
        val formatted = String.format(locale, "%02d", value)
        valueLabel.text = formatted
        valueLabel.contentDescription = context.getString(R.string.time_part_accessibility, title, formatted)
    }

    private fun adjustValue(amount: Int) {
        onAdjust(amount)
        valueLabel.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SELECTED)
    }

    private fun updateOutline(focused: Boolean) {
        frameBackground.setStroke(components.densityPixels(1), components.color(if (focused) "blue" else "cardStroke"))
    }

    private fun createArrowButton(up: Boolean, description: String, onClick: () -> Unit): ImageButton =
        ImageButton(context).apply {
            contentDescription = description
            tooltipText = description
            background = components.shape(Color.TRANSPARENT, 9)
            setPadding(components.densityPixels(14), components.densityPixels(14), components.densityPixels(14), components.densityPixels(14))
            setImageDrawable(object : Drawable() {
                private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = components.color("muted")
                    strokeWidth = components.densityPixels(2).toFloat()
                    strokeCap = Paint.Cap.ROUND
                }
                override fun draw(canvas: Canvas) {
                    val centerX = bounds.exactCenterX()
                    val centerY = bounds.exactCenterY()
                    val halfWidth = bounds.width() * .3f
                    val halfHeight = bounds.height() * .15f
                    val direction = if (up) -1 else 1
                    canvas.drawLine(centerX - halfWidth, centerY - direction * halfHeight,
                        centerX, centerY + direction * halfHeight, paint)
                    canvas.drawLine(centerX, centerY + direction * halfHeight,
                        centerX + halfWidth, centerY - direction * halfHeight, paint)
                }
                override fun setAlpha(alpha: Int) { paint.alpha = alpha }
                override fun setColorFilter(filter: android.graphics.ColorFilter?) { paint.colorFilter = filter }
                @Deprecated("Deprecated in Java") override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
                override fun getIntrinsicWidth(): Int = components.densityPixels(20)
                override fun getIntrinsicHeight(): Int = components.densityPixels(20)
            })
            setOnClickListener { onClick() }
        }
}
