package pl.wgabel.timetracker.design

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** Shared native View factories. Owns presentation only; no database or navigation logic. */
internal class AppUi(
    private val context: Context,
    private val tokens: DesignTokens,
    private val isDark: () -> Boolean
) {
    internal enum class ButtonRole { PRIMARY, SECONDARY, DANGER, WARNING }

    fun dp(value: Int): Int = (value * context.resources.displayMetrics.density + .5f).toInt()
    fun color(name: String): Int = tokens.color(name, isDark())
    fun dimension(name: String): Int = tokens.dimension(name)

    fun shape(fill: Int, radiusDp: Int = dimension("shapeRadiusDp")): GradientDrawable =
        GradientDrawable().apply { setColor(fill); cornerRadius = dp(radiusDp).toFloat() }

    fun label(value: String, sizeSp: Float = dimension("defaultTextSp").toFloat(),
              bold: Boolean = false, foreground: Int = color("ink")): TextView =
        TextView(context).apply {
            text = value
            textSize = sizeSp
            setTextColor(foreground)
            if (bold) setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER_VERTICAL
        }

    fun gap(parent: LinearLayout, heightDp: Int = dimension("defaultGapDp")) {
        parent.addView(View(context), LinearLayout.LayoutParams(1, dp(heightDp)))
    }

    fun card(): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        val padding = dp(dimension("cardPaddingDp"))
        setPadding(padding, padding, padding, padding)
        background = shape(color("surface"), dimension("cardRadiusDp")).apply {
            setStroke(dp(dimension("cardStrokeDp")), color("cardStroke"))
        }
        elevation = dp(dimension("cardElevationDp")).toFloat()
    }

    fun action(text: String, backgroundColor: Int, foreground: Int = Color.WHITE,
               click: () -> Unit): Button = Button(context).apply {
        this.text = text
        textSize = dimension("buttonTextSp").toFloat()
        setTypeface(null, Typeface.BOLD)
        minHeight = dp(dimension("buttonMinHeightDp"))
        isAllCaps = false
        setTextColor(foreground)
        background = shape(backgroundColor, dimension("buttonRadiusDp"))
        stateListAnimator = null
        elevation = 0f
        setOnClickListener { click() }
    }

    fun styledAction(text: String, role: ButtonRole, click: () -> Unit): Button {
        val (fill, foreground) = when (role) {
            ButtonRole.PRIMARY -> (if (isDark()) color("blue") else color("darkHeader")) to Color.WHITE
            ButtonRole.SECONDARY -> color("subtleNeutral") to color("ink")
            ButtonRole.DANGER -> color("dangerBackground") to color("dangerText")
            ButtonRole.WARNING -> color("subtleWarm") to color("warningText")
        }
        return action(text, fill, foreground, click)
    }
}
