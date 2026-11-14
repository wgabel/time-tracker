package pl.wgabel.timetracker.ui

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale
import pl.wgabel.timetracker.AppContainer
import pl.wgabel.timetracker.AppNavigation
import pl.wgabel.timetracker.R
import pl.wgabel.timetracker.design.AppUi
import pl.wgabel.timetracker.design.AppUi.ButtonRole
import pl.wgabel.timetracker.design.ParticipantColors
import pl.wgabel.timetracker.time.DatePresentation
import pl.wgabel.timetracker.time.TimeMath

/** Shared native presentation primitives. No SQL, feature actions or navigation state. */
internal abstract class ScreenUi(
    context: Context,
    protected val container: AppContainer
) : ContextWrapper(context) {
    protected abstract val navigator: AppNavigation
    protected open val projectId get() = navigator.projectId
    protected val projectRepository get() = container.projects
    protected val participantRepository get() = container.participants
    protected val sessionRepository get() = container.sessions
    protected val eventRepository get() = container.events
    protected val darkTheme get() = getSharedPreferences("preferences", MODE_PRIVATE).getString("app_theme", "light") == "dark"
    protected val designTokens = container.designTokens
    protected val appUi = AppUi(context, designTokens) { darkTheme }
    protected fun themeColor(name: String) = appUi.color(name)
    protected val blue get() = themeColor("blue")
    protected val green get() = themeColor("green")
    protected val ink get() = themeColor("ink")
    protected val muted get() = themeColor("muted")
    protected val surface get() = themeColor("surface")
    protected val background get() = themeColor("background")
    protected val darkHeader get() = themeColor("darkHeader")
    protected val subtle get() = themeColor("subtle")
    protected val subtleNeutral get() = themeColor("subtleNeutral")
    protected val subtleWarm get() = themeColor("subtleWarm")
    protected val secondaryButtonText get() = if (darkTheme) ink else blue
    protected val dateFormat get() = DatePresentation.date(resources)
    protected val clockFormat get() = DatePresentation.time(resources)


    val content by lazy { LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(appUi.densityPixels(16), appUi.densityPixels(14), appUi.densityPixels(16), appUi.densityPixels(20))
    } }
    protected fun projectIcon(projectId: Long, size: Int = 40): View =
        ProjectIcons(this, projectRepository, appUi).createIcon(projectId, size)
    protected fun densityPixels(n: Int): Int = appUi.densityPixels(n)
    protected fun shape(color: Int, radius: Int = designTokens.dimension("shapeRadiusDp")) =
        appUi.shape(color, radius)
    protected fun label(value: String, size: Float = 16f, bold: Boolean = false, color: Int = ink): TextView =
        appUi.label(value, size, bold, color)
    protected fun gap(parent: LinearLayout, height: Int = 12) = appUi.gap(parent, height)
    protected fun card(): LinearLayout = appUi.card()

    protected fun addCard(view: LinearLayout) {
        content.addView(view, LinearLayout.LayoutParams(-1, -2))
        gap(content, 14)
    }

    protected fun action(text: String, bg: Int, fg: Int = Color.WHITE, click: () -> Unit): Button =
        appUi.action(text, bg, fg, click)

    protected fun styledAction(text: String, role: ButtonRole, click: () -> Unit): Button =
        appUi.styledAction(text, role, click)

    protected fun blendColors(base: Int, overlay: Int, fraction: Float): Int {
        val backgroundWeight = 1f - fraction
        return Color.rgb(
            (Color.red(base) * backgroundWeight + Color.red(overlay) * fraction).toInt(),
            (Color.green(base) * backgroundWeight + Color.green(overlay) * fraction).toInt(),
            (Color.blue(base) * backgroundWeight + Color.blue(overlay) * fraction).toInt()
        )
    }

    protected fun participantAction(
        text: String,
        participantColor: Int,
        selected: Boolean,
        click: () -> Unit
    ): Button {
        val base = if (darkTheme) surface else Color.WHITE
        val tintStrength = if (darkTheme) 0.20f else 0.14f
        val fill = if (selected) participantColor else blendColors(base, participantColor, tintStrength)
        return action(text, fill, if (selected) Color.WHITE else ink, click)
    }

    protected fun participants(): List<Int> =
        participantRepository.participantIds(requireNotNull(projectId))

    protected fun personName(person: Int): String =
        participantRepository.name(requireNotNull(projectId), person, getString(R.string.person_fallback) + person)

    protected fun projectName(): String =
        projectRepository.name(requireNotNull(projectId), getString(R.string.project_fallback))
    protected fun isArchived(): Boolean = projectId?.let(projectRepository::isArchived) ?: false

    protected fun personColor(person: Int): Int =
        participantRepository.color(requireNotNull(projectId), person)
            ?.takeIf { it != 0 } ?: ParticipantColors.legacyColor(person)

    protected fun avatar(person: Int): View {
        val name = personName(person).trim()
        val initials = name.split(Regex("\\s+")).filter { it.isNotBlank() }
            .take(2).map { it.take(1).uppercase(Locale.getDefault()) }.joinToString("")
        val color = personColor(person)
        return TextView(this).apply {
            text = initials.ifEmpty { "?" }
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }
            contentDescription = getString(R.string.participant_description_prefix) + name
        }
    }

    protected fun editIconButton(onClick: () -> Unit): View =
        iconButton("edit", if (darkTheme) Color.rgb(164, 208, 205) else darkHeader, onClick)

    protected fun deleteIconButton(onClick: () -> Unit): View =
        iconButton("delete", Color.rgb(170, 42, 42), onClick)

    protected fun iconButton(kind: String, color: Int, onClick: () -> Unit): View {
        return object : View(this) {
            private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = densityPixels(2).toFloat()
                strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; this.color = color
            }
            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                val cx = width / 2f; val cy = height / 2f
                canvas.save()
                canvas.translate(cx - densityPixels(12), cy - densityPixels(12))
                if (kind == "bookmark-add") {
                    val bookmark = Path().apply {
                        moveTo(densityPixels(5).toFloat(), densityPixels(3).toFloat())
                        lineTo(densityPixels(15).toFloat(), densityPixels(3).toFloat())
                        lineTo(densityPixels(15).toFloat(), densityPixels(20).toFloat())
                        lineTo(densityPixels(10).toFloat(), densityPixels(16).toFloat())
                        lineTo(densityPixels(5).toFloat(), densityPixels(20).toFloat())
                        close()
                    }
                    canvas.drawPath(bookmark, p)
                    canvas.drawLine(densityPixels(16).toFloat(), densityPixels(9).toFloat(), densityPixels(23).toFloat(), densityPixels(9).toFloat(), p)
                    canvas.drawLine(densityPixels(19).toFloat(), densityPixels(6).toFloat(), densityPixels(19).toFloat(), densityPixels(13).toFloat(), p)
                } else if (kind == "clock") {
                    canvas.drawCircle(densityPixels(12).toFloat(), densityPixels(12).toFloat(), densityPixels(9).toFloat(), p)
                    canvas.drawLine(densityPixels(12).toFloat(), densityPixels(12).toFloat(), densityPixels(12).toFloat(), densityPixels(6).toFloat(), p)
                    canvas.drawLine(densityPixels(12).toFloat(), densityPixels(12).toFloat(), densityPixels(16).toFloat(), densityPixels(14).toFloat(), p)
                } else if (kind == "edit") {
                    // Recognizable pencil: closed shaft, pointed nib and eraser cap.
                    val pencil = Path().apply {
                        moveTo(densityPixels(4).toFloat(), densityPixels(16).toFloat())
                        lineTo(densityPixels(15).toFloat(), densityPixels(5).toFloat())
                        lineTo(densityPixels(19).toFloat(), densityPixels(9).toFloat())
                        lineTo(densityPixels(8).toFloat(), densityPixels(20).toFloat())
                        close()
                    }
                    canvas.drawPath(pencil, p)
                    canvas.drawLine(densityPixels(13).toFloat(), densityPixels(7).toFloat(), densityPixels(17).toFloat(), densityPixels(11).toFloat(), p)
                    canvas.drawLine(densityPixels(4).toFloat(), densityPixels(16).toFloat(), densityPixels(8).toFloat(), densityPixels(20).toFloat(), p)
                    canvas.drawLine(densityPixels(4).toFloat(), densityPixels(16).toFloat(), densityPixels(3).toFloat(), densityPixels(21).toFloat(), p)
                    canvas.drawLine(densityPixels(3).toFloat(), densityPixels(21).toFloat(), densityPixels(8).toFloat(), densityPixels(20).toFloat(), p)
                } else {
                    canvas.drawRoundRect(RectF(densityPixels(6).toFloat(), densityPixels(7).toFloat(), densityPixels(18).toFloat(), densityPixels(21).toFloat()),
                        densityPixels(1).toFloat(), densityPixels(1).toFloat(), p)
                    canvas.drawLine(densityPixels(4).toFloat(), densityPixels(5).toFloat(), densityPixels(20).toFloat(), densityPixels(5).toFloat(), p)
                    canvas.drawLine(densityPixels(9).toFloat(), densityPixels(3).toFloat(), densityPixels(15).toFloat(), densityPixels(3).toFloat(), p)
                    canvas.drawLine(densityPixels(10).toFloat(), densityPixels(10).toFloat(), densityPixels(10).toFloat(), densityPixels(17).toFloat(), p)
                    canvas.drawLine(densityPixels(14).toFloat(), densityPixels(10).toFloat(), densityPixels(14).toFloat(), densityPixels(17).toFloat(), p)
                }
                canvas.restore()
            }
        }.apply {
            background = shape(if (kind == "edit" || kind == "clock") if (darkTheme) Color.rgb(43, 60, 83) else Color.rgb(232, 239, 253) else if (darkTheme) Color.rgb(76, 45, 49) else Color.rgb(252, 233, 233), 9)
            stateListAnimator = null
            elevation = 0f
            contentDescription = when (kind) {
                "edit" -> getString(R.string.edit_entry)
                "clock" -> if (resources.configuration.locales[0].language == "pl") "Rozpocznij sesję wcześniej" else "Start session in the past"
                else -> getString(R.string.delete_entry)
            }
            isClickable = true
            setOnClickListener { onClick() }
        }
    }

    protected fun outlinedSelection(button: Button, color: Int, selected: Boolean): FrameLayout =
        AnimatedSelectionFrame(this, button, color, selected)

    protected fun formatDuration(ms: Long): String = TimeMath.formatDuration(ms)

    protected fun dayStart(offset: Int): Long = TimeMath.dayStart(offset)

}
