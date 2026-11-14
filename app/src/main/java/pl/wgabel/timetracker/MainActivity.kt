package pl.wgabel.timetracker

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.app.Activity
import android.content.Intent
import android.net.Uri
import java.io.OutputStreamWriter
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.widget.Toast
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.content.ContentValues
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.animation.ValueAnimator
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.graphics.Color
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.DashPathEffect
import android.widget.FrameLayout
import android.graphics.Path
import android.graphics.RectF
import android.view.MotionEvent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.ImageButton
import android.widget.NumberPicker
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import pl.wgabel.timetracker.design.DesignTokens
import pl.wgabel.timetracker.design.AppUi
import pl.wgabel.timetracker.design.AppUi.ButtonRole
import pl.wgabel.timetracker.tabs.TimersScreenController
import pl.wgabel.timetracker.tabs.TimersScreenEnvironment
import pl.wgabel.timetracker.tabs.TimersScreenView
import pl.wgabel.timetracker.tabs.TimerCardPresentation
import pl.wgabel.timetracker.tabs.TimerCardCommands
import pl.wgabel.timetracker.tabs.TimerCardUi
import pl.wgabel.timetracker.tabs.TimerCardActions
import pl.wgabel.timetracker.tabs.TimersInteractionService
import pl.wgabel.timetracker.tabs.ParticipantDataSource
import pl.wgabel.timetracker.tabs.AddParticipantDialog
import pl.wgabel.timetracker.tabs.ParticipantColorPicker
import pl.wgabel.timetracker.tabs.ITabService
import pl.wgabel.timetracker.tabs.TimersService
import pl.wgabel.timetracker.tabs.TimersRenderer
import pl.wgabel.timetracker.tabs.RecentActivityRenderer
import pl.wgabel.timetracker.tabs.HistoryService
import pl.wgabel.timetracker.tabs.StatisticsService
import pl.wgabel.timetracker.tabs.SettingsService
import pl.wgabel.timetracker.data.ProjectRepository

class MainActivity : Activity() {
    private val databaseService: DatabaseService by lazy { SQLiteDatabaseService(this) }
    private val db: SQLiteDatabase get() = databaseService.database
    private val languageService: LanguageService by lazy { PreferencesLanguageService(this) }
    private val themeService: ThemeService by lazy { PreferencesThemeService(this) }
    private val splashScreen: SplashScreenService by lazy {
        AnimatedSplashScreen(this, { darkTheme }, { blue }, { muted }, { ink })
    }
    private val projectRepository get() = ProjectRepository(db)
    private val sessionRepository get() = SessionRepository(db)
    private val eventRepository get() = EventRepository(db, sessionRepository)
    private val timerSessionController get() = TimerSessionController(db)
    private lateinit var content: LinearLayout
    private lateinit var navigation: LinearLayout
    private lateinit var topHeader: LinearLayout
    private lateinit var stickyDateContainer: LinearLayout
    private var calendarHeading: View? = null
    private var calendarHeadingExpanded = true
    private var calendarHeadingAnimator: android.animation.ValueAnimator? = null
    private val handler = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences("preferences", MODE_PRIVATE) }
    private var tab = 0
    // Tab services are lifecycle scaffolding. Existing UI is still rendered below.
    private val timersInteractionService: TimersInteractionService by lazy {
        TimersInteractionService(
            context = this,
            database = { db },
            projectId = { requireNotNull(projectId) },
            archived = { isArchived() },
            refresh = { renderContent() },
            openEventEditor = { session, person, time -> editEvent(null, session, person, time, "Inne", "") }
        )
    }
    private val timersScreen: TimersScreenController by lazy {
        TimersScreenController(
            environment = TimersScreenEnvironment(
                context = this,
                database = { db },
                projectId = { requireNotNull(projectId) },
                archived = { isArchived() },
                components = appUi
            ),
            presentation = TimerCardPresentation(
                placeCard = ::addCard,
                animateActiveCard = ::markActive,
                createIcon = ::iconButton,
                resolveString = ::tr,
                formatDuration = ::duration
            ),
            commands = TimerCardCommands(
                toggle = timersInteractionService::toggle,
                addEvent = timersInteractionService::addEvent,
                selectStartTime = ::quickStartPicker,
                subscribeTick = { update -> timerUpdates.add(update) }
            ),
            view = TimersScreenView(
                content = { content },
                title = { if (resources.configuration.locales[0].language == "pl") "Uczestnicy" else "Participants" },
                emptyMessage = { tr(R.string.first_person) },
                addButtonDescription = { tr(R.string.add_person) },
                onAddParticipant = ::addParticipant,
                recentActivityRenderer = recentActivityRenderer
            )

        )
    }
    private val recentActivityRenderer: RecentActivityRenderer by lazy {
        RecentActivityRenderer(
            context = this,
            target = { content },
            components = appUi,
            participantColor = { personColor(it) },
            participantName = { personName(it) },
            dateFormat = { DatePresentation.shortDateTime(resources) },
            openHistory = { tab = 2; render() }
        )
    }
    private val tabServices: List<ITabService> by lazy {
        listOf(timersScreen.service, StatisticsService(), HistoryService(), SettingsService())
    }
    private var visibleTabService: ITabService? = null

    private fun synchronizeTabService(): ITabService? {
        val next = if (projectId == null) null else tabServices.getOrNull(tab)
        if (visibleTabService !== next) {
            visibleTabService?.onHide()
            visibleTabService = next
            next?.onShow()
        }
        return next
    }

    private var pendingExportProject: Long? = null
    private var dayOffset = 0
    private var statsPeriod = 1
    private var statsOffset = 0
    private var timelineOffset = 0
    private var historyPerson = 0
    private var projectId: Long? = null
    private var showArchived = false
    private var showAppSettings = false
    private val timerUpdates = mutableListOf<(Long) -> Unit>()
    private val darkTheme get() = themeService.isDark
    // Theme values are loaded once from assets/design_tokens.json, not hardcoded in Activity.
    private val designTokens: DesignTokens by lazy { DesignTokens(this) }
    private val appUi: AppUi by lazy { AppUi(this, designTokens) { darkTheme } }
    private fun themeColor(name: String) = appUi.color(name)
    private val blue get() = themeColor("blue")
    private val green get() = themeColor("green")
    private val ink get() = themeColor("ink")
    private val muted get() = themeColor("muted")
    private val surface get() = themeColor("surface")
    private val background get() = themeColor("background")
    private val darkHeader get() = themeColor("darkHeader")
    private val subtle get() = themeColor("subtle")
    private val subtleNeutral get() = themeColor("subtleNeutral")
    private val subtleWarm get() = themeColor("subtleWarm")
    private val secondaryButtonText get() = if (darkTheme) ink else blue
    private val dateFormat get() = DatePresentation.date(resources)
    private val clockFormat get() = DatePresentation.time(resources)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        languageService.applyLanguage()
        themeService.applyThemeToSystem()
        databaseService.database
        themeService.applyThemeToSystem()
        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(themeColor("headerBackground"))
        }
        topHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(themeColor("headerBackground"))
            setPadding(dp(12), dp(7), dp(12), dp(7))
            minimumHeight = dp(54)
        }
        outer.addView(topHeader)
        // Android 15+ may lay out the app edge-to-edge even when older target
        // versions did not. Reserve the actual system-bar insets, not a fixed dp.
        if (Build.VERSION.SDK_INT >= 20) {
            outer.setOnApplyWindowInsetsListener { view, insets ->
                val top = insets.systemWindowInsetTop
                val bottom = insets.systemWindowInsetBottom
                view.setPadding(0, top, 0, bottom)
                insets
            }
            outer.requestApplyInsets()
        }
        stickyDateContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), 0, dp(16), dp(5))
            setBackgroundColor(if (darkTheme) surface else darkHeader)
            visibility = View.GONE
            elevation = dp(4).toFloat()
        }
        outer.addView(stickyDateContainer)
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(this@MainActivity.background)
        }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(20))
        }
        scroll.setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
            if (projectId != null && (tab == 1 || tab == 2) && kotlin.math.abs(scrollY - oldScrollY) > dp(2)) {
                setCalendarHeadingExpanded(scrollY == 0 || scrollY < oldScrollY)
            }
        }
        scroll.addView(content)
        outer.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        navigation = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(if (darkTheme) surface else darkHeader)
            setPadding(dp(8), dp(5), dp(8), dp(7))
            elevation = dp(7).toFloat()
        }
        outer.addView(navigation)
        setContentView(outer)
        render()
        splashScreen.show(outer)
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
        }
        syncTimerNotification()
        handler.post(object : Runnable {
            override fun run() {
                if (!isFinishing) {
                    if (tab == 0 && projectId != null) {
                        val now = System.currentTimeMillis()
                        timerUpdates.forEach { it(now) }
                    }
                    handler.postDelayed(this, 1000)
                }
            }
        })
    }

    // Animated startup overlay, shown once on Activity creation. The project screen
    // is already rendered underneath so the transition never flashes a blank frame.
    // Compatibility delegates while individual renderers migrate to AppUi directly.
    private fun dp(n: Int): Int = appUi.dp(n)
    private fun shape(color: Int, radius: Int = designTokens.dimension("shapeRadiusDp")) =
        appUi.shape(color, radius)
    private fun tr(id: Int): String = getString(id)
    private fun label(value: String, size: Float = 16f, bold: Boolean = false, color: Int = ink): TextView =
        appUi.label(value, size, bold, color)
    private fun gap(parent: LinearLayout, height: Int = 12) = appUi.gap(parent, height)
    private fun card(): LinearLayout = appUi.card()

    private data class AnimatedBlock(val offset: Float, val y: Float, val speed: Float, val size: Int, val opacity: Int)

    private fun activeCardBackground(): Drawable = object : Drawable(), android.graphics.drawable.Animatable {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        // Use monotonic elapsed time so animator repeats cannot reset particle positions.
        // Generate particle properties once; never randomize inside draw().
        private val random = kotlin.random.Random(System.nanoTime())
        private val blocks = List(24) {
            AnimatedBlock(random.nextFloat(), random.nextFloat(),
                .65f + random.nextFloat() * .8f,
                4 + random.nextInt(5), 28 + random.nextInt(42))
        }
        private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 3600L
            repeatCount = ValueAnimator.INFINITE
            interpolator = android.view.animation.LinearInterpolator()
            addUpdateListener { invalidateSelf() }
        }
        override fun setVisible(visible: Boolean, restart: Boolean): Boolean {
            val changed = super.setVisible(visible, restart)
            if (!visible) animator.cancel()
            return changed
        }
        override fun start() {
            if (android.provider.Settings.Global.getFloat(contentResolver,
                    android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f &&
                !animator.isRunning) animator.start()
        }
        override fun stop() { animator.cancel() }
        override fun isRunning(): Boolean = animator.isRunning
        override fun draw(canvas: Canvas) {
            val b = bounds
            val w = b.width().toFloat()
            val h = b.height().toFloat()
            val radius = dp(13).toFloat()
            val rect = RectF(b)
            p.style = Paint.Style.FILL
            p.shader = LinearGradient(0f, 0f, w, 0f,
                intArrayOf(surface, surface, if (darkTheme) Color.rgb(31, 76, 53) else Color.rgb(216, 246, 226)),
                floatArrayOf(0f, .65f, 1f), Shader.TileMode.CLAMP)
            canvas.drawRoundRect(rect, radius, radius, p)
            p.shader = null
            canvas.save()
            val clip = Path().apply { addRoundRect(rect, radius, radius, Path.Direction.CW) }
            canvas.clipPath(clip)
            for (block in blocks) {
                // Independent randomized starting positions and speeds.
                val elapsedCycles = android.os.SystemClock.uptimeMillis().toDouble() / 3600.0
                val progress = ((elapsedCycles * block.speed + block.offset) % 1.0).toFloat()
                val eased = progress * progress * (3f - 2f * progress)
                val side = dp(block.size).toFloat()
                val x = -side + eased * (w + side * 2f)
                val y = block.y * (h - side).coerceAtLeast(0f)
                p.color = if (darkTheme) Color.argb(block.opacity, 114, 226, 154)
                    else Color.argb(block.opacity, 47, 166, 93)
                canvas.drawRoundRect(RectF(x, y, x + side, y + side),
                    dp(1).toFloat(), dp(1).toFloat(), p)
            }
            canvas.restore()
            p.style = Paint.Style.STROKE
            p.strokeWidth = resources.displayMetrics.density.coerceAtLeast(1f)
            p.color = if (darkTheme) Color.rgb(78, 159, 106) else Color.rgb(121, 199, 146)
            canvas.drawRoundRect(RectF(b.left + p.strokeWidth / 2, b.top + p.strokeWidth / 2,
                b.right - p.strokeWidth / 2, b.bottom - p.strokeWidth / 2), radius, radius, p)
            p.style = Paint.Style.FILL
        }
        override fun setAlpha(alpha: Int) { p.alpha = alpha }
        override fun setColorFilter(filter: android.graphics.ColorFilter?) { p.colorFilter = filter }
        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    private fun markActive(view: LinearLayout) {
        val animated = activeCardBackground()
        view.background = animated
        view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                (animated as android.graphics.drawable.Animatable).start()
            }
            override fun onViewDetachedFromWindow(v: View) {
                (animated as android.graphics.drawable.Animatable).stop()
            }
        })
    }

    private fun addCard(view: LinearLayout) {
        content.addView(view, LinearLayout.LayoutParams(-1, -2))
        gap(content, 14)
    }

    private fun action(text: String, bg: Int, fg: Int = Color.WHITE, click: () -> Unit): Button =
        appUi.action(text, bg, fg, click)

    private fun styledAction(text: String, role: ButtonRole, click: () -> Unit): Button =
        appUi.styledAction(text, role, click)

    private fun blendColors(base: Int, overlay: Int, fraction: Float): Int {
        val backgroundWeight = 1f - fraction
        return Color.rgb(
            (Color.red(base) * backgroundWeight + Color.red(overlay) * fraction).toInt(),
            (Color.green(base) * backgroundWeight + Color.green(overlay) * fraction).toInt(),
            (Color.blue(base) * backgroundWeight + Color.blue(overlay) * fraction).toInt()
        )
    }

    private fun participantAction(
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

    private fun participants(): List<Int> =
        ParticipantDataSource(db).ids(requireNotNull(projectId))

    private fun personName(person: Int): String =
        ParticipantDataSource(db).name(person, tr(R.string.person_fallback) + person)

    private fun projectName(): String =
        projectRepository.name(requireNotNull(projectId), getString(R.string.project_fallback))
    private fun createProject() {
        val input = EditText(this).apply { hint = tr(R.string.project_name_hint); setSingleLine(true) }
        val holder = LinearLayout(this).apply { setPadding(dp(20), 0, dp(20), 0); addView(input) }
        val dialog = AlertDialog.Builder(this).setTitle(tr(R.string.new_project)).setView(holder)
            .setNegativeButton(tr(R.string.cancel), null).setPositiveButton(tr(R.string.add), null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = input.text.toString().trim()
                if (name.isEmpty()) { input.error = tr(R.string.name_required); return@setOnClickListener }
                val id = projectRepository.create(name)
                if (id > 0) { projectId = id; tab = 0; dayOffset = 0; historyPerson = 0; dialog.dismiss(); render() }
            }
        }
        dialog.show()
    }
    private val addParticipantDialog: AddParticipantDialog by lazy {
        AddParticipantDialog(
            context = this,
            db = { db },
            projectId = { requireNotNull(projectId) },
            nextColor = { nextParticipantColor() },
            colorPicker = { color, onChange -> colorPicker(color, onChange) },
            refresh = { renderContent() },
            string = { tr(it) }
        )
    }

    private fun addParticipant() = addParticipantDialog.show()

    // Deterministic, entirely offline miniature landscapes.
    private fun iconVariant(id: Long): Long = projectRepository.iconVariant(id)

    private fun regenerateProjectIcon() {
        if (isArchived()) return
        projectRepository.regenerateIcon(requireNotNull(projectId))
        renderContent()
    }

    private fun drawCreatureIcon(c: Canvas, r: java.util.Random) {
        val palettes = arrayOf(
            intArrayOf(0xFFFCE9CB.toInt(), 0xFFDA916E.toInt(), 0xFF633E44.toInt()),
            intArrayOf(0xFFD8F0E4.toInt(), 0xFF6AB5A1.toInt(), 0xFF264C57.toInt()),
            intArrayOf(0xFFE9E2FB.toInt(), 0xFF9B89D6.toInt(), 0xFF43365F.toInt()),
            intArrayOf(0xFFFFE3E8.toInt(), 0xFFE7A2B3.toInt(), 0xFF6D4656.toInt()),
            intArrayOf(0xFFE0EDFF.toInt(), 0xFF729ED4.toInt(), 0xFF304C76.toInt()),
            intArrayOf(0xFFFFF0BA.toInt(), 0xFFD9AF50.toInt(), 0xFF65503A.toInt())
        )
        val palette = palettes[r.nextInt(palettes.size)]
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        fun fill(color: Int) { paint.color = color; paint.style = Paint.Style.FILL }
        fun circle(x: Float, y: Float, radius: Float) = c.drawCircle(x, y, radius, paint)
        fun oval(left: Float, top: Float, right: Float, bottom: Float) =
            c.drawOval(RectF(left, top, right, bottom), paint)
        fun triangle(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) {
            c.drawPath(Path().apply {
                moveTo(x1, y1); lineTo(x2, y2); lineTo(x3, y3); close()
            }, paint)
        }
        fill(palette[0])
        c.drawRoundRect(0f, 0f, 100f, 100f, 20f, 20f, paint)
        c.save()
        c.clipPath(Path().apply { addRoundRect(RectF(0f, 0f, 100f, 100f), 20f, 20f, Path.Direction.CW) })
        val shape = r.nextInt(8)
        val ears = r.nextInt(7)
        fill(palette[1])
        when (ears) {
            0 -> { triangle(19f, 39f, 22f, 8f, 47f, 29f); triangle(53f, 29f, 78f, 8f, 81f, 39f) }
            1 -> { oval(24f, 2f, 40f, 48f); oval(60f, 2f, 76f, 48f) }
            2 -> { circle(25f, 29f, 17f); circle(75f, 29f, 17f) }
            3 -> { triangle(15f, 35f, 9f, 13f, 38f, 28f); triangle(62f, 28f, 91f, 13f, 85f, 35f) }
            4 -> { oval(5f, 31f, 36f, 52f); oval(64f, 31f, 95f, 52f) }
            5 -> { triangle(31f, 34f, 28f, 7f, 45f, 32f); triangle(55f, 32f, 72f, 7f, 69f, 34f) }
            else -> { circle(30f, 24f, 9f); circle(70f, 24f, 9f) }
        }
        when (shape) {
            0 -> circle(50f, 58f, 37f)
            1 -> oval(17f, 23f, 83f, 93f)
            2 -> c.drawRoundRect(17f, 25f, 83f, 91f, 24f, 24f, paint)
            3 -> oval(10f, 34f, 90f, 86f)
            4 -> { circle(50f, 57f, 34f); oval(18f, 60f, 82f, 95f) }
            5 -> { triangle(13f, 35f, 87f, 35f, 50f, 96f); circle(50f, 52f, 28f) }
            6 -> oval(22f, 17f, 78f, 96f)
            else -> { c.drawRoundRect(12f, 32f, 88f, 90f, 33f, 33f, paint) }
        }
        val eyeY = 51f + r.nextInt(10)
        val spacing = 12f + r.nextInt(9)
        val eyeType = r.nextInt(5)
        fill(palette[2])
        when (eyeType) {
            0 -> { circle(50f-spacing, eyeY, 3.5f); circle(50f+spacing, eyeY, 3.5f) }
            1 -> { oval(46f-spacing, eyeY-6f, 54f-spacing, eyeY+6f); oval(46f+spacing, eyeY-6f, 54f+spacing, eyeY+6f) }
            2 -> { oval(45f-spacing, eyeY-2f, 55f-spacing, eyeY+2f); oval(45f+spacing, eyeY-2f, 55f+spacing, eyeY+2f) }
            3 -> { circle(50f-spacing, eyeY, 5f); circle(50f+spacing, eyeY, 5f); fill(0xFFFFFFFF.toInt()); circle(48f-spacing, eyeY-2f, 2f); circle(48f+spacing, eyeY-2f, 2f) }
            else -> { circle(50f-spacing, eyeY, 2.5f); circle(50f+spacing, eyeY, 2.5f) }
        }
        fill(palette[0])
        if (r.nextBoolean()) oval(34f, 65f, 66f, 85f)
        fill(palette[2])
        when (r.nextInt(5)) {
            0 -> triangle(44f, 68f, 56f, 68f, 50f, 77f)
            1 -> oval(44f, 70f, 56f, 75f)
            2 -> { circle(44f, 74f, 3f); circle(56f, 74f, 3f) }
            3 -> c.drawArc(RectF(41f, 68f, 59f, 82f), 0f, 180f, false, paint)
            else -> c.drawRoundRect(47f, 68f, 53f, 80f, 3f, 3f, paint)
        }
        c.restore()
    }

    private fun projectIcon(id: Long, size: Int = 40): View = object : View(this) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private fun color(value: Long) { p.color = value.toInt(); p.style = Paint.Style.FILL }
        override fun onDraw(c: Canvas) {
            super.onDraw(c)
            if (width == 0 || height == 0) return
            val save = c.save()
            c.scale(width / 100f, height / 100f)
            val r = java.util.Random((id xor 0xBAA055L) + iconVariant(id) * 0x5DEECE66DL)
            // Alternate icon families on regeneration, so the next result looks distinctly different.
            if (((id + iconVariant(id)) and 1L) == 1L) {
                drawCreatureIcon(c, r)
                c.restoreToCount(save)
                return
            }
            val biome = r.nextInt(8) // meadow, beach, forest, desert, snow, lake, coast, hills
            val night = r.nextInt(4) == 0
            val sunset = !night && r.nextInt(4) == 0
            val sky = if (night) 0xFF192C52L else if (sunset) 0xFFF5A782L else 0xFF8EC9E8L
            color(sky)
            c.drawRoundRect(0f, 0f, 100f, 100f, 20f, 20f, p)
            c.save()
            c.clipPath(Path().apply { addRoundRect(RectF(0f, 0f, 100f, 100f), 20f, 20f, Path.Direction.CW) })
            val sunX = 18f + r.nextInt(62)
            val sunY = 16f + r.nextInt(20)
            color(if (night) 0xFFF4EDCFL else 0xFFFFE19AL)
            c.drawCircle(sunX, sunY, if (night) 8f else 12f, p)
            if (night) {
                color(sky)
                c.drawCircle(sunX + 4f, sunY - 3f, 8f, p)
                color(0xFFE9EAF5L)
                repeat(5) {
                    val x = 9f + r.nextInt(83)
                    val y = 8f + r.nextInt(38)
                    c.drawCircle(x, y, 1f + r.nextInt(2), p)
                }
            } else if (r.nextBoolean()) {
                color(0xFFF7F5EAL)
                c.drawOval(RectF(9f, 20f, 36f, 29f), p)
                c.drawOval(RectF(53f, 12f, 77f, 20f), p)
            }
            val mountain = biome == 0 || biome == 3 || biome == 4 || biome == 7 || (biome == 5 && r.nextBoolean())
            if (mountain) {
                color(if (night) 0xFF34466BL else if (biome == 4) 0xFFB6CBD8L else 0xFF7B9CA0L)
                val peaks = Path().apply {
                    moveTo(-12f, 69f)
                    lineTo(20f, 30f + r.nextInt(16))
                    lineTo(45f, 64f)
                    lineTo(70f, 27f + r.nextInt(17))
                    lineTo(115f, 72f)
                    lineTo(115f, 100f)
                    lineTo(-12f, 100f)
                    close()
                }
                c.drawPath(peaks, p)
            }
            val ground = when (biome) {
                1 -> 0xFFF4D28AL // sand
                3 -> 0xFFE9AE68L // desert
                4 -> 0xFFE8F3F4L // snow
                5 -> 0xFF4E9FC0L // lake
                6 -> 0xFF4A9FB6L // coast
                2 -> 0xFF356D58L // forest
                else -> 0xFF75B77AL // meadow / hills
            }
            color(ground)
            val horizon = if (biome == 1 || biome == 5 || biome == 6) 62f else 70f
            c.drawOval(RectF(-25f, horizon, 130f, 139f), p)
            if (biome == 1 || biome == 6) {
                color(0xFF4FABC5L)
                c.drawRect(0f, 51f, 100f, 70f, p)
                color(if (biome == 1) 0xFFF4D28AL else 0xFFE7C38BL)
                c.drawOval(RectF(-25f, 67f, 130f, 138f), p)
            }
            if (biome == 5) {
                color(0xFFB1DEE0L)
                c.drawRoundRect(20f, 81f, 75f, 84f, 2f, 2f, p)
            }
            val treeX = 22f + r.nextInt(56)
            when (biome) {
                1, 6 -> { // palm
                    color(0xFF7B5B3EL)
                    c.drawRect(treeX, 57f, treeX + 5f, 91f, p)
                    color(0xFF297D66L)
                    c.drawOval(RectF(treeX - 21f, 46f, treeX + 5f, 57f), p)
                    c.drawOval(RectF(treeX, 44f, treeX + 27f, 57f), p)
                }
                3 -> { // cactus
                    color(0xFF327E5BL)
                    c.drawRoundRect(treeX, 61f, treeX + 11f, 94f, 5f, 5f, p)
                    c.drawRoundRect(treeX - 9f, 69f, treeX - 2f, 82f, 3f, 3f, p)
                    c.drawRoundRect(treeX + 13f, 66f, treeX + 20f, 81f, 3f, 3f, p)
                }
                else -> {
                    color(0xFF70533CL)
                    c.drawRect(treeX - 3f, 67f, treeX + 4f, 94f, p)
                    color(if (biome == 4) 0xFF2D6966L else if (night) 0xFF275A56L else 0xFF2F8766L)
                    if (biome == 2 || biome == 4 || r.nextBoolean()) {
                        val pine = Path().apply {
                            moveTo(treeX, 39f)
                            lineTo(treeX - 19f, 80f)
                            lineTo(treeX + 19f, 80f)
                            close()
                        }
                        c.drawPath(pine, p)
                    } else {
                        c.drawCircle(treeX, 59f, 19f, p)
                        c.drawCircle(treeX - 10f, 66f, 13f, p)
                    }
                }
            }
            c.restore()
            c.restoreToCount(save)
        }
    }.apply {
        layoutParams = LinearLayout.LayoutParams(dp(size), dp(size))
        contentDescription = tr(R.string.icon_description)
    }

    private fun isArchived(): Boolean = projectId?.let(projectRepository::isArchived) ?: false

    private fun archiveProject() {
        if (isArchived()) return
        val count = projectRepository.activeTimerCount(requireNotNull(projectId))
        if (count > 0) {
            AlertDialog.Builder(this).setTitle(tr(R.string.active_timers))
                .setMessage(tr(R.string.stop_before_archive))
                .setPositiveButton(android.R.string.ok, null).show()
            return
        }
        AlertDialog.Builder(this).setTitle(tr(R.string.archive_confirm))
            .setMessage(tr(R.string.archive_description))
            .setNegativeButton(tr(R.string.cancel), null)
            .setPositiveButton(tr(R.string.archive_verb)) { _, _ ->
                if (!projectRepository.archiveIfNoActiveTimers(requireNotNull(projectId))) return@setPositiveButton
                projectId = null
                showArchived = false
                render()
            }.show()
    }

    private fun renderAppSettings() {
        content.addView(styledAction(tr(R.string.back_projects), ButtonRole.SECONDARY) {
            showAppSettings = false
            render()
        })
        gap(content, 16)
        val app = card()
        app.addView(label(tr(R.string.application), 19f, true))
        gap(app, 12)
        app.addView(styledAction("🌐  " + tr(R.string.language), ButtonRole.SECONDARY) { languageService.selectLanguage() })
        gap(app, 10)
        app.addView(styledAction("◐  " + tr(R.string.theme), ButtonRole.SECONDARY) { themeService.selectTheme() })
        gap(app, 12)
        val version = try { packageManager.getPackageInfo(packageName, 0).versionName ?: "—" } catch (_: Exception) { "—" }
        app.addView(label(tr(R.string.version) + "v" + version, 14f, false, muted))
        gap(app, 12)
        app.addView(label(tr(R.string.local_data), 13f, false, muted))
        addCard(app)
    }

    private fun renderProjects() {
        val heading = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        heading.addView(label(if (showArchived) tr(R.string.archived_projects) else tr(R.string.projects), 24f, true))
        if (!showArchived) {
            val addButton = styledAction("＋", ButtonRole.PRIMARY) { createProject() }.apply {
                textSize = 24f
                contentDescription = tr(R.string.add_project)
                minHeight = dp(42)
                minimumHeight = dp(42)
                setPadding(0, 0, 0, 0)
            }
            heading.addView(addButton, LinearLayout.LayoutParams(dp(42), dp(42)).apply {
                setMargins(dp(12), 0, 0, 0)
            })
        }
        content.addView(heading)
        gap(content, 14)
        val projects = projectRepository.list(showArchived)
        if (projects.isEmpty()) {
            content.addView(label(tr(R.string.no_projects), 16f, false, muted))
            gap(content, 18)
        }
        projects.forEach { (id, name) ->
            val item = card()
            val counts = projectRepository.participantAndActiveTimerCounts(id)
            val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            line.addView(projectIcon(id))
            line.addView(label(name, 19f, true).apply {
                setPadding(dp(12), 0, dp(6), 0)
                setSingleLine(true)
                ellipsize = android.text.TextUtils.TruncateAt.END
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            if (counts.second > 0) {
                line.addView(label("● ${counts.second} " + tr(R.string.active_suffix).trim(), 12f, true, green).apply {
                    setPadding(dp(8), dp(7), dp(8), dp(7))
                    background = GradientDrawable().apply {
                        setColor(if (darkTheme) Color.rgb(35, 71, 54) else Color.rgb(230, 241, 235))
                        cornerRadius = dp(16).toFloat()
                    }
                })
            }
            line.addView(label("›", 23f, false, muted).apply { setPadding(dp(7), 0, 0, 0) })
            item.addView(line)
            val footer = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            if (showArchived) footer.addView(label(tr(R.string.archived), 11f, true, Color.rgb(166, 99, 34)))
            footer.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
            footer.addView(label("${counts.first} " + tr(R.string.participants_count), 12f, false, muted))
            gap(item, 7)
            item.addView(footer)
            item.setOnClickListener {
                projectId = id; tab = 0; dayOffset = 0; historyPerson = 0; render()
            }
            addCard(item)
        }
        if (showArchived) {
            content.addView(styledAction(tr(R.string.back_projects), ButtonRole.SECONDARY) {
                showArchived = false; render()
            })
        } else {
            content.addView(styledAction(tr(R.string.archived_link), ButtonRole.WARNING) {
                showArchived = true; render()
            })
            gap(content, 18)
            content.addView(styledAction("⚙  " + tr(R.string.settings), ButtonRole.SECONDARY) {
                showAppSettings = true
                render()
            })
        }
    }
    private fun personColor(person: Int): Int =
        db.rawQuery("SELECT color FROM participants WHERE id=? AND project_id=?",
            arrayOf(person.toString(), projectId.toString())).use { cursor ->
            if (cursor.moveToFirst() && cursor.getInt(0) != 0) cursor.getInt(0)
            else ParticipantColors.legacyColor(person)
        }

    private fun nextParticipantColor(): Int =
        ParticipantDataSource(db).suggestedColor(requireNotNull(projectId))

    private val participantColorPicker: ParticipantColorPicker by lazy {
        ParticipantColorPicker(this) { value, size, bold, color ->
            label(value, size, bold, color)
        }
    }

    private fun colorPicker(initial: Int, onSelected: (Int) -> Unit): LinearLayout =
        participantColorPicker.create(initial, onSelected)

    private fun editParticipantColor(person: Int) {
        if (isArchived()) return
        val selected = intArrayOf(personColor(person))
        AlertDialog.Builder(this)
            .setTitle(personName(person))
            .setView(colorPicker(selected[0]) { selected[0] = it })
            .setNegativeButton(tr(R.string.cancel), null)
            .setPositiveButton(tr(R.string.save)) { _, _ ->
                db.update("participants", ContentValues().apply { put("color", selected[0]) },
                    "id=? AND project_id=?", arrayOf(person.toString(), projectId.toString()))
                render()
            }.show()
    }

    private fun activeStart(person: Int): Long? =
        timerSessionController.activeStart(projectId!!, person)

    private fun quickStartPicker(person: Int, sessionId: Long? = null) =
        timersInteractionService.quickStartPicker(person, sessionId)

    private fun toggle(person: Int) = timersInteractionService.toggle(person)

    private fun syncTimerNotification() = timersInteractionService.syncNotification()

    private fun duration(ms: Long): String = TimeMath.duration(ms)

    private fun dayStart(offset: Int): Long = TimeMath.dayStart(offset)

    private fun total(person: Int, start: Long, end: Long, now: Long = System.currentTimeMillis()): Long =
        SessionDaySummary(db).totalDuration(projectId!!, person, start, end, now)

    private fun setCalendarHeadingExpanded(expanded: Boolean) {
        if (calendarHeadingExpanded == expanded) return
        val heading = calendarHeading ?: return
        calendarHeadingExpanded = expanded
        calendarHeadingAnimator?.cancel()
        val startHeight = heading.height
        val targetHeight = if (expanded) dp(40) else 0
        calendarHeadingAnimator = android.animation.ValueAnimator.ofInt(startHeight, targetHeight).apply {
            duration = 220L
            interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            addUpdateListener { animator ->
                val height = animator.animatedValue as Int
                heading.layoutParams = heading.layoutParams.apply { this.height = height }
                heading.alpha = height.toFloat() / dp(40)
            }
            start()
        }
    }

    // Shared day navigation for Statistics timelines and History. Timers always shows today.
    private fun dayNavigator(forHistory: Boolean) {
        val selectedOffset = if (forHistory) dayOffset else timelineOffset
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
        val activity = mutableMapOf<Long, MutableSet<Int>>()
        db.rawQuery(
            "SELECT person,started_at,ended_at FROM sessions WHERE project_id=? AND started_at<? AND (ended_at IS NULL OR ended_at>?)",
            arrayOf(projectId.toString(), weekEnd.toString(), weekStart.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val person = cursor.getInt(0)
                val start = maxOf(weekStart, cursor.getLong(1))
                val finish = minOf(weekEnd, if (cursor.isNull(2)) System.currentTimeMillis() else cursor.getLong(2))
                val date = Calendar.getInstance().apply {
                    timeInMillis = start
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }
                while (date.timeInMillis < finish) {
                    activity.getOrPut(date.timeInMillis) { mutableSetOf() }.add(person)
                    date.add(Calendar.DAY_OF_YEAR, 1)
                }
            }
        }
        // Event-only days should also be marked in the day navigator.
        db.rawQuery(
            "SELECT DISTINCT person,happened_at FROM events WHERE project_id=? AND happened_at>=? AND happened_at<?",
            arrayOf(projectId.toString(), weekStart.toString(), weekEnd.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val date = Calendar.getInstance().apply {
                    timeInMillis = cursor.getLong(1)
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                activity.getOrPut(date) { mutableSetOf() }.add(cursor.getInt(0))
            }
        }
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
            DatePickerDialog(this@MainActivity, { _, year, month, day ->
                val target = Calendar.getInstance().apply {
                    set(year, month, day, 0, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                var offset = 0
                while (dayStart(offset) > target) offset--
                while (dayStart(offset + 1) <= target) offset++
                if (forHistory) dayOffset = offset else timelineOffset = offset
                renderContent()
            }, chosen.get(Calendar.YEAR), chosen.get(Calendar.MONTH), chosen.get(Calendar.DAY_OF_MONTH)).apply {
                datePicker.maxDate = System.currentTimeMillis()
            }.show()
            }
        }, LinearLayout.LayoutParams(dp(46), dp(40)))
        panel.addView(heading, LinearLayout.LayoutParams(-1, dp(40)))
        calendarHeading = heading
        calendarHeadingExpanded = true
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
                setPadding(dp(1), dp(3), dp(1), dp(3))
                background = shape(if (chosen) calendarSelected else Color.TRANSPARENT, 12)
                alpha = if (future) .40f else 1f
            }
            cell.addView(label(shortDay.format(Date(date)), 11f, false, calendarMuted).apply { gravity = Gravity.CENTER })
            cell.addView(label(Calendar.getInstance().apply { timeInMillis = date }.get(Calendar.DAY_OF_MONTH).toString(),
                16f, true, calendarText).apply { gravity = Gravity.CENTER })
            val dots = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
            activity[date]?.take(3)?.forEach { person ->
                dots.addView(View(this).apply { background = shape(personColor(person), 4) },
                    LinearLayout.LayoutParams(dp(5), dp(5)).apply { setMargins(dp(1), dp(3), dp(1), 0) })
            }
            cell.addView(dots, LinearLayout.LayoutParams(-1, dp(11)))
            if (!future) cell.setOnClickListener {
                var offset = 0
                while (dayStart(offset) > date) offset--
                while (dayStart(offset + 1) <= date) offset++
                if (forHistory) dayOffset = offset else timelineOffset = offset
                renderContent()
            }
            strip.addView(cell, LinearLayout.LayoutParams(0, dp(59), 1f))
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
                        if (kotlin.math.abs(dx) > dp(48) && kotlin.math.abs(dx) > kotlin.math.abs(dy) * 1.5f) {
                            val target = Calendar.getInstance().apply {
                                timeInMillis = selected
                                add(Calendar.DAY_OF_YEAR, if (dx > 0) -7 else 7)
                            }.timeInMillis
                            if (target <= today) {
                                var offset = selectedOffset
                                while (dayStart(offset) > target) offset--
                                while (dayStart(offset + 1) <= target) offset++
                                if (forHistory) dayOffset = offset else timelineOffset = offset
                                renderContent()
                            }
                            true
                        } else false
                    }
                    else -> false
                }
            }
        }
        panel.addView(strip)
        if (tab == 1 || tab == 2) {
            stickyDateContainer.addView(panel)
        } else {
            addCard(panel)
        }
    }

    private fun addEvent(person: Int) = timersInteractionService.addEvent(person)

    private fun addHistoricalEvent(sessionId: Long, person: Int, started: Long, ended: Long) {
        if (ended <= started || isArchived()) return
        // Use the start as the initial event time. The editor checks the complete
        // timestamp, not only the displayed hour, against the session boundaries.
        editEvent(null, sessionId, person, started, "Inne", "", started to ended)
    }

    private fun editEvent(
        id: Long?, sessionId: Long, person: Int, timestamp: Long,
        currentType: String, currentNote: String,
        allowedRange: Pair<Long, Long>? = null
    ) {
        // Resolve session boundaries before opening the form.
        val sessionBounds = db.rawQuery(
            "SELECT started_at, ended_at FROM sessions WHERE id=? AND project_id=? AND person=?",
            arrayOf(sessionId.toString(), projectId.toString(), person.toString())
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getLong(0) to (if (cursor.isNull(1)) null else cursor.getLong(1))
            } else null
        }
        if (sessionBounds == null) {
            Toast.makeText(this, tr(R.string.session_update_failed), Toast.LENGTH_LONG).show()
            return
        }
        EventEditor(this).show(
            id = id,
            timestamp = timestamp,
            currentType = currentType,
            currentNote = currentNote,
            sessionBounds = sessionBounds,
            timeButtonFactory = { styledAction(it, ButtonRole.SECONDARY) {} },
            rangeLabelFactory = { label(it, 13f, false, muted) },
            cancelText = tr(R.string.cancel),
            saveText = tr(R.string.save),
            onSave = { chosenTimestamp, type, description ->
                if (!sessionRepository.eventWithinSession(projectId!!, sessionId, person, chosenTimestamp)) {
                    false
                } else if (eventRepository.save(projectId!!, sessionId, person, id, chosenTimestamp, type, description)) {
                    renderContent()
                    true
                } else false
            },
            onInvalidTime = {
                Toast.makeText(this, "Godzina wydarzenia musi mieścić się w czasie pomiaru", Toast.LENGTH_LONG).show()
            },
            onSaveFailed = {
                Toast.makeText(this, tr(R.string.session_update_failed), Toast.LENGTH_LONG).show()
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

    private fun eventList(sessionId: Long, person: Int, container: LinearLayout) {
        val eventColor = personColor(person)
        db.rawQuery(
            "SELECT id,happened_at,type,note FROM events WHERE project_id=? AND session_id=? ORDER BY happened_at,id",
            arrayOf(projectId.toString(), sessionId.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                val timestamp = cursor.getLong(1)
                val type = cursor.getString(2)
                val note = cursor.getString(3)
                val eventRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(20), 0, dp(6), 0)
                }
                // A connected timeline on the section background, not another card.
                val rail = object : View(this) {
                    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = eventColor }
                    override fun onDraw(canvas: Canvas) {
                        super.onDraw(canvas)
                        val cx = width / 2f
                        paint.strokeWidth = dp(2).toFloat()
                        canvas.drawLine(cx, 0f, cx, height.toFloat(), paint)
                        canvas.drawCircle(cx, height / 2f, dp(5).toFloat(), paint)
                    }
                }
                eventRow.addView(rail, LinearLayout.LayoutParams(dp(14), dp(62)))
                val details = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(10), 0, 0, 0)
                }
                val eventTitle = if (note.isNotBlank()) note else type
                details.addView(label(eventTitle, 14f, true))
                val secondary = if (note.isNotBlank()) type + " · " else ""
                details.addView(label(secondary + clockFormat.format(Date(timestamp)), 12f, false, muted))
                val categoryIcon = eventCategoryIcon(type)
                eventRow.addView(categoryIcon, LinearLayout.LayoutParams(dp(18), dp(18)).apply {
                    leftMargin = dp(8)
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
                            .setNegativeButton(tr(R.string.cancel), null)
                            .setPositiveButton(tr(R.string.delete)) { _, _ ->
                                db.delete("events", "id=? AND project_id=?",
                                    arrayOf(id.toString(), projectId.toString()))
                                renderContent()
                            }.show()
                    }.apply {
                        background = shape(
                            if (darkTheme) Color.rgb(76, 45, 49) else Color.rgb(252, 233, 233), 10)
                    }
                    tray.addView(edit, LinearLayout.LayoutParams(dp(48), dp(48)))
                    tray.addView(View(this), LinearLayout.LayoutParams(dp(8), dp(1)))
                    tray.addView(delete, LinearLayout.LayoutParams(dp(48), dp(48)))
                    val arrow = label("›", 23f, false, muted).apply { gravity = Gravity.CENTER }
                    eventRow.addView(arrow, LinearLayout.LayoutParams(dp(20), dp(48)))
                    eventRow.addView(tray, LinearLayout.LayoutParams(0, dp(48)))
                    HistoryActionTray(
                        eventRow, tray, arrow,
                        HistoryActionTray.Dimension.WIDTH,
                        expandedSizePx = dp(104),
                        slideOffsetPx = dp(36)
                    ).attach(eventTitle + ". Dotknij, aby pokazać lub ukryć akcje wydarzenia")
                }
                container.addView(eventRow)
            }
        }
    }

    private fun pickDateTime(initial: Long, title: String, onChosen: (Long) -> Unit) {
        DateTimeEditor(this).show(DateTimeEditor.Config(
            title = title, initial = initial, max = System.currentTimeMillis()
        )) { chosen -> onChosen(chosen); true }
    }

    private fun addSession() {
        val ids = participants()
        if (ids.isEmpty()) { addParticipant(); return }
        val people = ids.map { personName(it) }.toTypedArray()
        AlertDialog.Builder(this).setTitle(tr(R.string.select_person))
            .setItems(people) { _, which ->
                val person = ids[which]
                val now = System.currentTimeMillis()
                pickDateTime(now - 3600000L, tr(R.string.start_measurement)) { start ->
                    pickDateTime(now, tr(R.string.end_measurement)) { end ->
                        if (sessionRepository.insert(projectId!!, person, start, end)) {
                            dayOffset = 0
                            while (dayStart(dayOffset) > start) dayOffset--
                            while (dayStart(dayOffset + 1) <= start) dayOffset++
                            renderContent()
                            Toast.makeText(this, tr(R.string.measurement_added), Toast.LENGTH_SHORT).show()
                        } else Toast.makeText(this, tr(R.string.invalid_measurement), Toast.LENGTH_LONG).show()
                    }
                }
            }.setNegativeButton(tr(R.string.cancel), null).show()
    }

    private fun deleteSession(id: Long) {
        AlertDialog.Builder(this)
            .setTitle(tr(R.string.delete_measurement))
            .setMessage(tr(R.string.delete_measurement_warning))
            .setNegativeButton(tr(R.string.cancel), null)
            .setPositiveButton(tr(R.string.delete)) { _, _ ->
                var deleted = 0
                db.beginTransaction()
                try {
                    val eligible = db.rawQuery(
                        "SELECT 1 FROM sessions WHERE id=? AND project_id=? AND ended_at IS NOT NULL",
                        arrayOf(id.toString(), projectId.toString())
                    ).use { it.moveToFirst() }
                    if (eligible) {
                        db.delete("events", "session_id=? AND project_id=?",
                            arrayOf(id.toString(), projectId.toString()))
                        deleted = db.delete("sessions",
                            "id=? AND project_id=? AND ended_at IS NOT NULL",
                            arrayOf(id.toString(), projectId.toString()))
                    }
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
                if (deleted > 0) {
                    renderContent()
                    Toast.makeText(this, tr(R.string.measurement_deleted), Toast.LENGTH_SHORT).show()
                }
            }.show()
    }

    private fun editActiveSessionStart(id: Long, person: Int, started: Long) {
        quickStartPicker(person, id)
    }

    private fun editSession(id: Long, person: Int, started: Long, ended: Long?) {
        if (isArchived()) return
        if (ended == null) { editActiveSessionStart(id, person, started); return }
        val pl = resources.configuration.locales[0].language == "pl"
        SessionEditor(this).editCompleted(started, ended) { newStart, newEnd ->
            val updated = sessionRepository.update(projectId!!, person, id, newStart, newEnd)
            if (updated) {
                renderContent()
                Toast.makeText(this, tr(R.string.session_updated), Toast.LENGTH_SHORT).show()
            } else Toast.makeText(this, if (pl) "Czas koliduje z sesją lub wydarzeniami."
                else "Time conflicts with a session or events.", Toast.LENGTH_LONG).show()
            updated
        }
    }

    private fun avatar(person: Int): View {
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
            contentDescription = tr(R.string.participant_description_prefix) + name
        }
    }

    private fun editIconButton(onClick: () -> Unit): View =
        iconButton("edit", if (darkTheme) Color.rgb(164, 208, 205) else darkHeader, onClick)

    private fun deleteIconButton(onClick: () -> Unit): View =
        iconButton("delete", Color.rgb(170, 42, 42), onClick)

    private fun iconButton(kind: String, color: Int, onClick: () -> Unit): View {
        return object : View(this) {
            private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = dp(2).toFloat()
                strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; this.color = color
            }
            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                val cx = width / 2f; val cy = height / 2f
                canvas.save()
                canvas.translate(cx - dp(12), cy - dp(12))
                if (kind == "bookmark-add") {
                    val bookmark = Path().apply {
                        moveTo(dp(5).toFloat(), dp(3).toFloat())
                        lineTo(dp(15).toFloat(), dp(3).toFloat())
                        lineTo(dp(15).toFloat(), dp(20).toFloat())
                        lineTo(dp(10).toFloat(), dp(16).toFloat())
                        lineTo(dp(5).toFloat(), dp(20).toFloat())
                        close()
                    }
                    canvas.drawPath(bookmark, p)
                    canvas.drawLine(dp(16).toFloat(), dp(9).toFloat(), dp(23).toFloat(), dp(9).toFloat(), p)
                    canvas.drawLine(dp(19).toFloat(), dp(6).toFloat(), dp(19).toFloat(), dp(13).toFloat(), p)
                } else if (kind == "clock") {
                    canvas.drawCircle(dp(12).toFloat(), dp(12).toFloat(), dp(9).toFloat(), p)
                    canvas.drawLine(dp(12).toFloat(), dp(12).toFloat(), dp(12).toFloat(), dp(6).toFloat(), p)
                    canvas.drawLine(dp(12).toFloat(), dp(12).toFloat(), dp(16).toFloat(), dp(14).toFloat(), p)
                } else if (kind == "edit") {
                    // Recognizable pencil: closed shaft, pointed nib and eraser cap.
                    val pencil = Path().apply {
                        moveTo(dp(4).toFloat(), dp(16).toFloat())
                        lineTo(dp(15).toFloat(), dp(5).toFloat())
                        lineTo(dp(19).toFloat(), dp(9).toFloat())
                        lineTo(dp(8).toFloat(), dp(20).toFloat())
                        close()
                    }
                    canvas.drawPath(pencil, p)
                    canvas.drawLine(dp(13).toFloat(), dp(7).toFloat(), dp(17).toFloat(), dp(11).toFloat(), p)
                    canvas.drawLine(dp(4).toFloat(), dp(16).toFloat(), dp(8).toFloat(), dp(20).toFloat(), p)
                    canvas.drawLine(dp(4).toFloat(), dp(16).toFloat(), dp(3).toFloat(), dp(21).toFloat(), p)
                    canvas.drawLine(dp(3).toFloat(), dp(21).toFloat(), dp(8).toFloat(), dp(20).toFloat(), p)
                } else {
                    canvas.drawRoundRect(RectF(dp(6).toFloat(), dp(7).toFloat(), dp(18).toFloat(), dp(21).toFloat()),
                        dp(1).toFloat(), dp(1).toFloat(), p)
                    canvas.drawLine(dp(4).toFloat(), dp(5).toFloat(), dp(20).toFloat(), dp(5).toFloat(), p)
                    canvas.drawLine(dp(9).toFloat(), dp(3).toFloat(), dp(15).toFloat(), dp(3).toFloat(), p)
                    canvas.drawLine(dp(10).toFloat(), dp(10).toFloat(), dp(10).toFloat(), dp(17).toFloat(), p)
                    canvas.drawLine(dp(14).toFloat(), dp(10).toFloat(), dp(14).toFloat(), dp(17).toFloat(), p)
                }
                canvas.restore()
            }
        }.apply {
            background = shape(if (kind == "edit" || kind == "clock") if (darkTheme) Color.rgb(43, 60, 83) else Color.rgb(232, 239, 253) else if (darkTheme) Color.rgb(76, 45, 49) else Color.rgb(252, 233, 233), 9)
            stateListAnimator = null
            elevation = 0f
            contentDescription = when (kind) {
                "edit" -> tr(R.string.edit_entry)
                "clock" -> if (resources.configuration.locales[0].language == "pl") "Rozpocznij sesję wcześniej" else "Start session in the past"
                else -> tr(R.string.delete_entry)
            }
            isClickable = true
            setOnClickListener { onClick() }
        }
    }

    private fun sessionList(start: Long, end: Long, limit: Int = 100) {
        val list = HistorySessionList(this, db)
        val count = list.populate(
            projectId!!, start, end, limit,
            if (tab == 2 && historyPerson != 0) historyPerson else null
        ) { session ->
            val id = session.id
            val person = session.participantId
            val originalStart = session.startedAt
            val originalEnd = session.endedAt
            val from = maxOf(start, originalStart)
            val until = minOf(end, originalEnd ?: System.currentTimeMillis())
            val isHistory = tab == 2
            val running = if (originalEnd == null) "  • trwa" else ""
            val rangeLabel = label(clockFormat.format(Date(from)) + " – " +
                clockFormat.format(Date(until)) + running, 13f, false, muted)
            val elapsedLabel = label(duration(until - from), 16f, true)
            val name = personName(person)
            val group = HistorySessionRow(
                this,
                historyMode = isHistory,
                rowBackground = shape(surface, 12),
                avatar = avatar(person),
                participantName = label(name, 16f, true),
                dateLabel = if (isHistory) null else label(
                    "  " + name + "  •  " + dateFormat.format(Date(originalStart)), 13f, true
                ),
                rangeLabel = rangeLabel,
                elapsedLabel = elapsedLabel,
                gap = { container, size -> gap(container, size) },
                dp = { size -> dp(size) }
            )
            if (originalEnd == null) markActive(group.mainRow)
            if (originalEnd == null && tab == 0) timerUpdates.add { tick ->
                val currentEnd = minOf(end, tick)
                rangeLabel.text = clockFormat.format(Date(from)) + " – " +
                    clockFormat.format(Date(currentEnd)) + running
                elapsedLabel.text = duration(currentEnd - from)
            }
            if (isHistory && !isArchived()) {
                val addEventAction = iconButton(
                    "bookmark-add",
                    if (darkTheme) Color.rgb(133, 224, 180) else Color.rgb(35, 124, 98)
                ) {
                    if (originalEnd == null) addEvent(person)
                    else addHistoricalEvent(id, person, originalStart, originalEnd)
                }.apply {
                    background = shape(
                        if (darkTheme) Color.rgb(34, 70, 59) else Color.rgb(229, 244, 237), 10)
                    contentDescription = "Dodaj wydarzenie"
                }
                val editAction = editIconButton {
                    if (originalEnd == null) editActiveSessionStart(id, person, originalStart)
                    else editSession(id, person, originalStart, originalEnd)
                }.apply { background = shape(subtle, 10) }
                val deleteAction = if (originalEnd == null) null else deleteIconButton {
                    deleteSession(id)
                }.apply {
                    background = shape(
                        if (darkTheme) Color.rgb(76, 45, 49) else Color.rgb(252, 233, 233), 10)
                }
                group.attachActions(
                    trayBackground = shape(surface, 12),
                    addEvent = addEventAction,
                    edit = editAction,
                    delete = deleteAction,
                    arrow = label("›", 23f, false, muted),
                    description = name + ", " + duration(until - from) +
                        ". Dotknij, aby pokazać lub ukryć akcje"
                )
            }
            if (isHistory) eventList(id, person, group)
            list.addView(group, LinearLayout.LayoutParams(-1, -2))
            gap(list, 7)
        }
        if (count == 0) list.addView(label(tr(R.string.no_sessions), 15f, false, muted))
        content.addView(list, LinearLayout.LayoutParams(-1, -2))
    }

    private fun selectedDayEvents(start: Long, end: Long) {
        val events = mutableListOf<Triple<Int, String, String>>()
        db.rawQuery(
            "SELECT person,happened_at,type,note FROM events WHERE project_id=? AND happened_at>=? AND happened_at<? ORDER BY happened_at DESC",
            arrayOf(projectId.toString(), start.toString(), end.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val person = cursor.getInt(0)
                val time = clockFormat.format(Date(cursor.getLong(1)))
                val type = cursor.getString(2)
                val note = cursor.getString(3)
                events.add(Triple(person, time, type + if (note.isBlank()) "" else " · " + note))
            }
        }
        if (events.isEmpty()) {
            content.addView(label("Brak wydarzeń w wybranym dniu", 14f, false, muted))
            return
        }
        events.forEach { (person, time, description) ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), dp(12), dp(14), dp(12))
                background = shape(surface, 16)
            }
            row.addView(label("⚑", 22f, true, personColor(person)),
                LinearLayout.LayoutParams(dp(34), -2))
            val details = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            details.addView(label(description, 15f, true))
            gap(details, 3)
            details.addView(label(time + "  ·  " + personName(person), 13f, false, muted))
            row.addView(details, LinearLayout.LayoutParams(0, -2, 1f))
            content.addView(row, LinearLayout.LayoutParams(-1, -2))
            gap(content, 8)
        }
    }

    private fun periodRange(): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        when (statsPeriod) {
            0 -> cal.add(Calendar.DAY_OF_YEAR, statsOffset)
            1 -> { cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek); cal.add(Calendar.WEEK_OF_YEAR, statsOffset) }
            2 -> { cal.set(Calendar.DAY_OF_MONTH, 1); cal.add(Calendar.MONTH, statsOffset) }
            else -> {
                var earliest = System.currentTimeMillis()
                db.rawQuery("SELECT MIN(started_at) FROM sessions WHERE project_id=?", arrayOf(projectId.toString())).use { c ->
                    if (c.moveToFirst() && !c.isNull(0)) earliest = c.getLong(0)
                }
                return earliest to System.currentTimeMillis()
            }
        }
        val from = cal.timeInMillis
        when (statsPeriod) {
            0 -> cal.add(Calendar.DAY_OF_YEAR, 1)
            1 -> cal.add(Calendar.WEEK_OF_YEAR, 1)
            else -> cal.add(Calendar.MONTH, 1)
        }
        return from to cal.timeInMillis
    }

    private fun intervals(start: Long, end: Long): List<Triple<Int, Long, Long>> {
        val result = mutableListOf<Triple<Int, Long, Long>>()
        val now = System.currentTimeMillis()
        db.rawQuery(
            "SELECT person,started_at,ended_at FROM sessions WHERE project_id=? AND started_at<? AND (ended_at IS NULL OR ended_at>?) ORDER BY started_at",
            arrayOf(projectId.toString(), end.toString(), start.toString())
        ).use { c ->
            while (c.moveToNext()) {
                val clipped = TimeMath.clippedInterval(
                    c.getLong(1),
                    if (c.isNull(2)) null else c.getLong(2),
                    start,
                    end,
                    now
                )
                if (clipped != null) {
                    result.add(Triple(c.getInt(0), clipped.first, clipped.second))
                }
            }
        }
        return result
    }

    private fun barChart(from: Long, to: Long, target: LinearLayout) {
        data class Bucket(val label: String, val start: Long, val end: Long, val millis: Long)
        val points = mutableListOf<Bucket>()
        val cal = Calendar.getInstance().apply { timeInMillis = from }
        val endLimit = minOf(to, System.currentTimeMillis())
        if (statsPeriod == 0) {
            for (hour in 0..23) {
                val start = Calendar.getInstance().apply {
                    timeInMillis = from
                    set(Calendar.HOUR_OF_DAY, hour)
                }.timeInMillis
                val end = Calendar.getInstance().apply {
                    timeInMillis = start
                    add(Calendar.HOUR_OF_DAY, 1)
                }.timeInMillis
                points.add(Bucket(String.format(Locale.ROOT, "%02d", hour), start, end,
                    intervals(start, minOf(end, endLimit)).sumOf { it.third - it.second }))
            }
        } else {
            var count = 0
            while (cal.timeInMillis < endLimit && count < 1000) {
                val start = cal.timeInMillis
                when (statsPeriod) {
                    1, 2 -> cal.add(Calendar.DAY_OF_YEAR, 1)
                    else -> cal.add(Calendar.MONTH, 1)
                }
                val end = cal.timeInMillis
                val name = when (statsPeriod) {
                    1 -> DatePresentation.weekdayShort(resources).format(Date(start))
                    2 -> SimpleDateFormat("d", resources.configuration.locales[0]).format(Date(start))
                    else -> DatePresentation.monthYear(resources).format(Date(start))
                }
                points.add(Bucket(name, start, end,
                    intervals(start, minOf(end, endLimit)).sumOf { it.third - it.second }))
                count++
            }
        }
        if (points.isEmpty()) {
            target.addView(label(tr(R.string.no_measurements), 14f, false, muted))
            return
        }
        val unit = when (statsPeriod) {
            0 -> "godzinach"
            1, 2 -> "dniach"
            else -> "miesiącach"
        }
        target.addView(label("Suma czasów uczestników w kolejnych $unit", 13f, false, muted))
        gap(target, 8)
        val maxMillis = (points.maxOfOrNull { it.millis } ?: 0L).coerceAtLeast(1L)
        val axisMaxMinutes = ((maxMillis / 60000.0 / 30.0).let { kotlin.math.ceil(it) } * 30.0)
            .toLong().coerceAtLeast(30L)
        val axisMax = axisMaxMinutes * 60000L
        val selectedInfo = label("Dotknij słupka, aby zobaczyć dokładny czas", 13f, false, muted)
        val chart = object : View(this) {
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            private var selected = -1
            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                val left = dp(52).toFloat()
                val right = width.toFloat() - dp(4)
                val top = dp(12).toFloat()
                val bottom = height.toFloat() - dp(30)
                val plotHeight = bottom - top
                val step = (right - left) / points.size
                for (tick in 0..4) {
                    val y = bottom - tick * plotHeight / 4f
                    paint.color = subtle
                    paint.strokeWidth = dp(1).toFloat()
                    canvas.drawLine(left, y, right, y, paint)
                    val minutes = axisMaxMinutes * tick / 4
                    val tickLabel = if (minutes == 0L) "0" else if (minutes % 60L == 0L)
                        "${minutes / 60} h" else if (minutes < 60L) "${minutes} min"
                        else "${minutes / 60} h ${minutes % 60} m"
                    paint.color = muted
                    paint.textSize = dp(10).toFloat()
                    paint.textAlign = Paint.Align.RIGHT
                    canvas.drawText(tickLabel, left - dp(7), y + dp(4), paint)
                }
                points.forEachIndexed { index, point ->
                    val barHeight = (point.millis.toDouble() / axisMax * plotHeight).toFloat()
                        .coerceAtLeast(if (point.millis > 0L) dp(2).toFloat() else 0f)
                    val x = left + index * step + step * .12f
                    paint.color = if (selected == index) darkHeader else blue
                    canvas.drawRoundRect(RectF(x, bottom - barHeight, x + step * .76f, bottom),
                        dp(3).toFloat(), dp(3).toFloat(), paint)
                    if (points.size <= 12 || index % ((points.size + 7) / 8) == 0) {
                        paint.color = muted
                        paint.textSize = dp(10).toFloat()
                        paint.textAlign = Paint.Align.CENTER
                        canvas.drawText(point.label, x + step * .38f, height - dp(7).toFloat(), paint)
                    }
                }
            }
            override fun onTouchEvent(event: MotionEvent): Boolean {
                if (event.action == MotionEvent.ACTION_DOWN) return true
                if (event.action != MotionEvent.ACTION_UP) return super.onTouchEvent(event)
                val left = dp(52).toFloat()
                val available = width.toFloat() - dp(56)
                if (event.x < left || available <= 0f) return true
                val index = (((event.x - left) / available) * points.size).toInt()
                    .coerceIn(0, points.lastIndex)
                selected = index
                invalidate()
                val point = points[index]
                val dateTime = SimpleDateFormat("dd.MM HH:mm", resources.configuration.locales[0])
                selectedInfo.text = "${dateTime.format(Date(point.start))} – " +
                    "${dateTime.format(Date(point.end))}: ${duration(point.millis)}"
                return true
            }
        }
        target.addView(chart, LinearLayout.LayoutParams(-1, dp(210)))
        gap(target, 8)
        target.addView(selectedInfo)
        gap(target, 14)
        val peak = points.maxByOrNull { it.millis }
        val active = points.count { it.millis > 0L }
        val average = if (active > 0) points.sumOf { it.millis } / active else 0L
        val metrics = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        metrics.addView(label("Najwięcej: ${peak?.label ?: "—"} · ${duration(peak?.millis ?: 0L)}",
            14f, true, ink))
        gap(metrics, 7)
        metrics.addView(label("Średnio na aktywny przedział: ${duration(average)}",
            13f, false, muted))
        gap(metrics, 7)
        metrics.addView(label("Przedziały z aktywnością: $active / ${points.size}",
            13f, false, muted))
        target.addView(metrics)
    }

    private fun renderPeriodTotals(from: Long, to: Long) {
        val entries = participants().map { it to total(it, from, to) }
        val sum = entries.sumOf { it.second }
        val maximum = (entries.maxOfOrNull { it.second } ?: 0L).coerceAtLeast(1L)
        val c = card()
        c.addView(label("01 — ŁĄCZNY CZAS", 12f, true, muted))
        gap(c, 8)
        c.addView(label(duration(sum), 29f, true, ink))
        c.addView(label("Suma czasów uczestników w wybranym okresie", 12f, false, muted))
        gap(c, 16)
        c.addView(label("Czas według uczestników", 17f, true, ink))
        gap(c, 12)
        entries.forEach { (person, time) ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            row.addView(View(this).apply { background = shape(personColor(person), 5) },
                LinearLayout.LayoutParams(dp(10), dp(10)).apply { rightMargin = dp(8) })
            row.addView(label(personName(person), 14f, false, ink),
                LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(label(duration(time), 14f, true, ink))
            c.addView(row)
            gap(c, 5)
            val fraction = (time.toFloat() / maximum).coerceIn(0f, 1f)
            val bar = LinearLayout(this).apply { background = shape(subtle, 5) }
            if (fraction > 0f) bar.addView(View(this).apply {
                background = shape(personColor(person), 5)
            }, LinearLayout.LayoutParams(0, dp(8), fraction))
            if (fraction < 1f) bar.addView(View(this),
                LinearLayout.LayoutParams(0, dp(8), 1f - fraction))
            c.addView(bar, LinearLayout.LayoutParams(-1, dp(8)))
            gap(c, 12)
        }
        if (sum > 0L) {
            gap(c, 8)
            c.addView(View(this).apply { background = shape(subtle, 1) },
                LinearLayout.LayoutParams(-1, dp(1)))
            gap(c, 16)
            c.addView(label("Udział w łącznym czasie", 17f, true, ink))
            gap(c, 12)
            val shareRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val ring = object : View(this) {
                private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = dp(19).toFloat()
                    strokeCap = Paint.Cap.BUTT
                }
                private val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = ink
                    textAlign = Paint.Align.CENTER
                }
                override fun onDraw(canvas: Canvas) {
                    super.onDraw(canvas)
                    val cx = width / 2f
                    val cy = height / 2f
                    val radius = minOf(width, height) / 2f - dp(13)
                    val bounds = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
                    paint.color = subtle
                    canvas.drawArc(bounds, 0f, 360f, false, paint)
                    var angle = -90f
                    entries.filter { it.second > 0L }.forEach { (person, time) ->
                        val sweep = (time.toDouble() / sum * 360.0).toFloat()
                        paint.color = personColor(person)
                        canvas.drawArc(bounds, angle, sweep, false, paint)
                        angle += sweep
                    }
                    centerPaint.color = ink
                    centerPaint.typeface = Typeface.DEFAULT_BOLD
                    centerPaint.textSize = dp(19).toFloat()
                    canvas.drawText("100%", cx, cy - dp(2), centerPaint)
                    centerPaint.typeface = Typeface.DEFAULT
                    centerPaint.textSize = dp(9).toFloat()
                    canvas.drawText("łącznego czasu", cx, cy + dp(13), centerPaint)
                }
            }
            shareRow.addView(ring, LinearLayout.LayoutParams(0, dp(160), 1f))
            val legend = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(10), 0, 0, 0)
            }
            entries.forEach { (person, time) ->
                val item = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                item.addView(View(this).apply { background = shape(personColor(person), 5) },
                    LinearLayout.LayoutParams(dp(9), dp(9)).apply { rightMargin = dp(7) })
                item.addView(label(personName(person), 12f, false, ink),
                    LinearLayout.LayoutParams(0, -2, 1f))
                val percent = time.toDouble() * 100.0 / sum
                item.addView(label(String.format(resources.configuration.locales[0], "%.1f%%", percent),
                    12f, true, ink))
                legend.addView(item)
                gap(legend, 12)
            }
            shareRow.addView(legend, LinearLayout.LayoutParams(0, -2, 1f))
            c.addView(shareRow)
        }
        addCard(c)
    }

    private fun renderParticipantPie(from: Long, to: Long) {
        val c = card()
        c.addView(label("02 — UDZIAŁ W ŁĄCZNYM CZASIE", 12f, true, muted))
        gap(c, 6)
        c.addView(label(tr(R.string.participant_share), 13f, false, muted))
        gap(c, 12)
        val people = participants()
        val totals = mutableMapOf<Int, Long>()
        intervals(from, minOf(to, System.currentTimeMillis())).forEach { (person, start, end) ->
            totals[person] = (totals[person] ?: 0L) + (end - start)
        }
        val entries = people.map { it to (totals[it] ?: 0L) }
        val total = entries.sumOf { it.second }
        if (total <= 0L) {
            c.addView(label(tr(R.string.no_period), 14f, false, muted))
            addCard(c)
            return
        }
        val pie = object : View(this) {
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                val diameter = minOf(width.toFloat() - dp(16), height.toFloat() - dp(8))
                val x = (width - diameter) / 2f
                val y = (height - diameter) / 2f
                val bounds = RectF(x, y, x + diameter, y + diameter)
                var angle = -90f
                entries.filter { it.second > 0L }.forEach { (person, duration) ->
                    val sweep = duration.toDouble() / total.toDouble() * 360.0
                    paint.color = personColor(person)
                    paint.style = Paint.Style.FILL
                    canvas.drawArc(bounds, angle, sweep.toFloat(), true, paint)
                    angle += sweep.toFloat()
                }
            }
        }
        c.addView(pie, LinearLayout.LayoutParams(-1, dp(205)))
        gap(c, 10)
        entries.forEach { (person, duration) ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            row.addView(View(this).apply {
                background = shape(personColor(person), 5)
            }, LinearLayout.LayoutParams(dp(13), dp(13)))
            row.addView(label("  " + personName(person), 14f, true),
                LinearLayout.LayoutParams(0, -2, 1f))
            val seconds = duration / 1000L
            val timeText = if (seconds < 60L && duration > 0L) "${seconds} s"
                else "${seconds / 3600} h ${(seconds % 3600) / 60} min"
            val percent = duration.toDouble() * 100.0 / total.toDouble()
            row.addView(label(String.format(resources.configuration.locales[0], "%s  ·  %.1f%%", timeText, percent),
                13f, false, ink))
            c.addView(row)
            gap(c, 8)
        }
        addCard(c)
    }

    // Particle overlay for a currently running interval, clipped to its colored bar.
    private fun drawRunningBlocks(canvas: Canvas, paint: Paint, area: RectF, color: Int, vertical: Boolean) {
        if (area.width() <= 0f || area.height() <= 0f) return
        val t = android.os.SystemClock.uptimeMillis().toDouble() / 3600.0
        canvas.save()
        val clip = Path().apply { addRoundRect(area, dp(3).toFloat(), dp(3).toFloat(), Path.Direction.CW) }
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
            val side = dp(4 + (fraction(37) * 5).toInt()).toFloat()
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
            canvas.drawRoundRect(RectF(x, y, x + side, y + side), dp(1).toFloat(), dp(1).toFloat(), paint)
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

    private fun renderTimeline(inHistory: Boolean = false) {
        val c = if (inHistory) LinearLayout(this).apply { orientation = LinearLayout.VERTICAL } else card()
        if (!inHistory) {
            c.addView(label(tr(R.string.daily_timeline), 20f, true))
            gap(c, 8)
        }
        val day = dayStart(if (inHistory) dayOffset else timelineOffset)
        val people = if (inHistory && historyPerson != 0) participants().filter { it == historyPerson } else participants()
        if (people.isEmpty()) {
            c.addView(label(tr(R.string.timeline_hint), 14f, false, muted))
        } else {
            val end = dayStart((if (inHistory) dayOffset else timelineOffset) + 1)
            val sessions = intervals(day, end)
            val detailSessions = mutableListOf<Triple<Long, Int, Pair<Long, Long>>>()
            db.rawQuery("SELECT id,person,started_at,ended_at FROM sessions WHERE project_id=? AND started_at<? AND (ended_at IS NULL OR ended_at>?)",
                arrayOf(projectId.toString(), end.toString(), day.toString())).use { cur ->
                while (cur.moveToNext()) {
                    detailSessions.add(Triple(cur.getLong(0), cur.getInt(1),
                        maxOf(day, cur.getLong(2)) to minOf(end, if (cur.isNull(3)) System.currentTimeMillis() else cur.getLong(3))))
                }
            }
            // Capture active session IDs and starts, rather than guessing from end timestamps.
            val runningStarts = if (day <= System.currentTimeMillis() && end > System.currentTimeMillis())
                people.mapNotNull { person -> activeStart(person)?.let { person to it } }.toMap()
            else emptyMap()
            val timeline = object : View(this) {
                override fun onTouchEvent(event: MotionEvent): Boolean {
                    if (event.action != MotionEvent.ACTION_UP) return true
                    val row = ((event.y - dp(7)) / dp(32)).toInt()
                    if (row !in people.indices) return true
                    val left = if (inHistory) 0f else dp(80).toFloat()
                    val widthScale = (width.toFloat() - dp(5) - left).coerceAtLeast(1f)
                    val fraction = ((event.x - left) / widthScale).coerceIn(0f, 1f)
                    val moment = day + ((end - day) * fraction).toLong()
                    val person = people[row]
                    val chosen = detailSessions.lastOrNull { it.second == person && moment >= it.third.first && moment <= it.third.second }
                    if (chosen != null) {
                        db.rawQuery("SELECT started_at,ended_at FROM sessions WHERE id=?", arrayOf(chosen.first.toString())).use { cur ->
                            if (cur.moveToFirst()) {
                                val started = cur.getLong(0)
                                val ended = if (cur.isNull(1)) null else cur.getLong(1)
                                if (ended != null && !isArchived()) editSession(chosen.first, person, started, ended)
                                else Toast.makeText(this@MainActivity, tr(R.string.stop_before_edit_short), Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    return true
                }
                private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                override fun onDraw(canvas: Canvas) {
                    super.onDraw(canvas)
                    val left = if (inHistory) 0f else dp(80).toFloat()
                    val right = width.toFloat() - dp(5)
                    val trackWidth = (right - left).coerceAtLeast(1f)
                    val rowHeight = dp(32).toFloat()
                    people.forEachIndexed { index, person ->
                        val y = dp(7) + index * rowHeight
                        paint.color = ink
                        paint.textSize = dp(11).toFloat()
                        paint.textAlign = Paint.Align.LEFT
                        if (!inHistory) {
                            val name = personName(person)
                            canvas.drawText(if (name.length > 11) name.take(10) + "…" else name, dp(2).toFloat(), y + dp(15), paint)
                        }
                        paint.color = if (darkTheme) Color.rgb(65, 75, 90) else Color.rgb(234, 237, 242)
                        canvas.drawRoundRect(RectF(left, y, right, y + dp(21)), dp(3).toFloat(), dp(3).toFloat(), paint)
                        sessions.filter { it.first == person }.forEach { item ->
                            val running = runningStarts[person] != null &&
                                item.second == maxOf(day, runningStarts[person]!!)
                            val finish = if (running) minOf(end, System.currentTimeMillis()) else item.third
                            val x1 = left + ((item.second - day).toFloat() / (end - day)) * trackWidth
                            val x2 = left + ((finish - day).toFloat() / (end - day)) * trackWidth
                            paint.color = personColor(person)
                            canvas.drawRoundRect(RectF(x1, y, maxOf(x1 + dp(2), x2), y + dp(21)), dp(3).toFloat(), dp(3).toFloat(), paint)
                            if (running) {
                                drawRunningBlocks(canvas, paint,
                                    RectF(x1, y, maxOf(x1 + dp(2), x2), y + dp(21)),
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
                            val tickLength = if (major) dp(7) else dp(4)
                            canvas.drawLine(x, y + dp(22), x, y + dp(22) + tickLength, paint)
                        }
                    }
                    if (!inHistory) {
                    paint.color = muted
                    paint.textSize = dp(10).toFloat()
                    paint.textAlign = Paint.Align.CENTER
                    for (hour in listOf(0, 6, 12, 18, 24)) {
                        val x = left + hour / 24f * trackWidth
                        canvas.drawText(String.format(Locale.ROOT, "%02d", hour), x, height - dp(3).toFloat(), paint)
                    }
                    }
                }
            }
            c.addView(timeline, LinearLayout.LayoutParams(-1, dp(32) * people.size + (if (inHistory) dp(4) else dp(25))))
            animateRunningChart(timeline, runningStarts.isNotEmpty())
        }
        if (inHistory) {
            content.addView(c, LinearLayout.LayoutParams(-1, -2))
            gap(content, 8)
        } else addCard(c)
    }


    // Fixed-height daily view: the time scale and event labels stay pinned horizontally.
    // Only participant tracks respond to horizontal swipes.
    private fun renderVerticalDailyTimeline() {
        val polish = resources.configuration.locales[0].language == "pl"
        val panel = card()
        panel.addView(label(if (polish) "Pionowa oś dnia" else "Vertical daily timeline", 20f, true))
        gap(panel, 8)
        val day = dayStart(timelineOffset)
        val end = dayStart(timelineOffset + 1)
        val sessions = intervals(day, end)
        data class EventMark(val person: Int, val at: Long, val type: String, val note: String)
        val events = mutableListOf<EventMark>()
        db.rawQuery(
            """SELECT e.person,e.happened_at,e.type,e.note FROM events e
                INNER JOIN sessions s ON s.id=e.session_id AND s.project_id=e.project_id
                    AND s.person=e.person
                WHERE e.project_id=? AND e.happened_at>=? AND e.happened_at<?
                    AND e.happened_at>=s.started_at
                    AND (s.ended_at IS NULL OR e.happened_at<=s.ended_at)
                ORDER BY e.happened_at""",
            arrayOf(projectId.toString(), day.toString(), end.toString())
        ).use { cur ->
            while (cur.moveToNext()) events.add(EventMark(cur.getInt(0), cur.getLong(1), cur.getString(2), cur.getString(3)))
        }
        val activePeople = sessions.map { it.first }.distinct()
        val runningStarts = if (day <= System.currentTimeMillis() && end > System.currentTimeMillis())
            activePeople.mapNotNull { person -> activeStart(person)?.let { person to it } }.toMap()
        else emptyMap()
        if (sessions.isEmpty() && events.isEmpty()) {
            panel.addView(label(if (polish) "Brak aktywności tego dnia" else "No activity on this day", 14f, false, muted))
            addCard(panel)
            return
        }

        val names = activePeople.associateWith { personName(it) }
        // The full local day is always shown. Grow the chart when event labels
        // need more vertical room, instead of clipping the bottom of the day.
        // Size for the actual distribution of events, not just their count.
        // Dense clusters near midnight need extra room below their timestamps.
        val labelGap = dp(64).toFloat()
        var fullHeight = dp(480)
        for (attempt in 0 until 200) {
            var nextLabel = dp(12).toFloat()
            for (event in events) {
                val exactY = ((event.at - day).toDouble() / (end - day) * fullHeight).toFloat()
                nextLabel = maxOf(exactY, nextLabel) + labelGap
            }
            if (events.isEmpty() || nextLabel - labelGap + dp(54) <= fullHeight) break
            fullHeight += dp(64)
        }
        val graph = object : View(this) {
            private val p = Paint(Paint.ANTI_ALIAS_FLAG)
            private var horizontalOffset = 0f
            private var downX = 0f
            private var downY = 0f
            private var initialOffset = 0f
            private var swiping = false
            private val scaleWidth get() = dp(38).toFloat()
            private val rightEdge get() = width * .40f
            private val participantWidth get() = (rightEdge - scaleWidth - dp(6)).coerceAtLeast(dp(12).toFloat())
            // Two participants fill the same width that many compact tracks would occupy.
            private val visibleCapacity get() = (participantWidth / dp(19)).toInt().coerceAtLeast(1)
            private val trackStep get() = if (activePeople.size <= visibleCapacity)
                participantWidth / activePeople.size.coerceAtLeast(1) else dp(19).toFloat()
            private val trackWidth get() = (trackStep - dp(3)).coerceAtLeast(dp(8).toFloat())
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
                        if (!swiping && kotlin.math.abs(dx) > dp(8) &&
                            kotlin.math.abs(dx) > kotlin.math.abs(dy) && maxOffset > 0f) {
                            swiping = true
                        }
                        if (swiping) {
                            horizontalOffset = (initialOffset - dx).coerceIn(0f, maxOffset)
                            invalidate()
                        } else if (kotlin.math.abs(dy) > dp(8)) {
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
                val eventLeft = rightEdge + dp(8)
                val hourPaint = p
                for (hour in 0..24) {
                    val cal = Calendar.getInstance().apply { timeInMillis = day; add(Calendar.HOUR_OF_DAY, hour) }
                    val at = cal.timeInMillis
                    if (at > end) break
                    val y = yAt(at).coerceAtMost(fullHeight - dp(2).toFloat())
                    hourPaint.color = if (darkTheme) Color.rgb(70, 78, 91) else Color.rgb(226, 231, 238)
                    hourPaint.strokeWidth = resources.displayMetrics.density
                    canvas.drawLine(scaleWidth - dp(5), y, rightEdge, y, hourPaint)
                    hourPaint.color = muted
                    hourPaint.textSize = dp(10).toFloat()
                    hourPaint.textAlign = Paint.Align.LEFT
                    canvas.drawText(String.format(Locale.ROOT, "%02d", cal.get(Calendar.HOUR_OF_DAY)), dp(1).toFloat(),
                        (y + dp(4)).coerceIn(dp(10).toFloat(), fullHeight - dp(2).toFloat()), hourPaint)
                }
                canvas.save()
                canvas.clipRect(scaleWidth, 0f, rightEdge, fullHeight.toFloat())
                activePeople.forEachIndexed { index, person ->
                    val x = scaleWidth + dp(3) + index * trackStep - horizontalOffset
                    if (x + trackWidth < scaleWidth || x > rightEdge) return@forEachIndexed
                    p.color = personColor(person)
                    p.alpha = 38
                    canvas.drawRoundRect(RectF(x, 0f, x + trackWidth, fullHeight.toFloat()), dp(5).toFloat(), dp(5).toFloat(), p)
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
                        canvas.drawRoundRect(RectF(x, top, x + trackWidth, maxOf(top + dp(2), bottom)),
                            dp(5).toFloat(), dp(5).toFloat(), p)
                        if (running) {
                            drawRunningBlocks(canvas, p,
                                RectF(x, top, x + trackWidth, maxOf(top + dp(2), bottom)),
                                personColor(person), true)
                        }
                        if (longest != null && start == longest.second && finish == longest.third &&
                            bottom - top > dp(24)) {
                            canvas.save()
                            canvas.clipRect(x, top, x + trackWidth, bottom)
                            canvas.rotate(90f, x + trackWidth / 2, (top + bottom) / 2)
                            p.color = Color.WHITE
                            p.textSize = dp(10).toFloat()
                            p.textAlign = Paint.Align.CENTER
                            p.typeface = android.graphics.Typeface.DEFAULT_BOLD
                            val name = names[person] ?: ""
                            val maxChars = ((bottom - top) / dp(7)).toInt().coerceAtLeast(1)
                            canvas.drawText(if (name.length > maxChars) name.take(maxChars - 1) + "…" else name,
                                x + trackWidth / 2, (top + bottom) / 2 + dp(3), p)
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
                    val minimum = if (labelPositions.isEmpty()) dp(12).toFloat()
                        else labelPositions.last() + labelGap
                    labelPositions.add(maxOf(yAt(event.at), minimum))
                }
                events.forEachIndexed { index, e ->
                    val y = yAt(e.at)
                    val labelY = labelPositions[index]
                    val eventColor = personColor(e.person)
                    val cardLeft = eventLeft
                    val cardRight = width - dp(3).toFloat()
                    val cardTop = labelY - dp(13)
                    val cardBottom = labelY + dp(48)
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
                        dp(8).toFloat(), dp(8).toFloat(), p
                    )
                    // Colored accent and connector identify the participant.
                    p.color = eventColor
                    canvas.drawRoundRect(
                        RectF(cardLeft, cardTop + dp(5), cardLeft + dp(3), cardBottom - dp(5)),
                        dp(2).toFloat(), dp(2).toFloat(), p
                    )
                    p.strokeWidth = dp(1).toFloat()
                    canvas.drawLine(rightEdge + dp(1), y, cardLeft, labelY, p)
                    canvas.drawCircle(rightEdge - dp(3), y, dp(4).toFloat(), p)
                    p.color = ink
                    p.textAlign = Paint.Align.LEFT
                    p.textSize = dp(11).toFloat()
                    p.typeface = android.graphics.Typeface.DEFAULT_BOLD
                    val stamp = clockFormat.format(Date(e.at))
                    val textX = cardLeft + dp(9)
                    val textWidth = (cardRight - textX - dp(6)).coerceAtLeast(dp(30).toFloat())
                    drawWrappedText(canvas, "$stamp · ${e.type}", textX, labelY + dp(4), textWidth, 2, dp(13).toFloat())
                    p.typeface = android.graphics.Typeface.DEFAULT
                    p.color = muted
                    p.textSize = dp(10).toFloat()
                    val description = (names[e.person] ?: "") + if (e.note.isBlank()) "" else " · " + e.note
                    drawWrappedText(canvas, description, textX, labelY + dp(30), textWidth, 2, dp(12).toFloat())
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
        addCard(panel)
    }

    private fun renderStats() {
        dayNavigator(false)
        gap(content, 8)
        renderTimeline()
        gap(content, 18)
        renderVerticalDailyTimeline()
        gap(content, 22)
        content.addView(label("Podsumowanie okresu", 22f, true, ink))
        gap(content, 12)
        val c = card()
        c.addView(label("WYBIERZ OKRES", 12f, true, muted))
        gap(c, 12)
        val selector = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf(tr(R.string.day), tr(R.string.week), tr(R.string.month), tr(R.string.all)).forEachIndexed { index, name ->
            val chosen = statsPeriod == index
            val control = styledAction(name, if (chosen) ButtonRole.PRIMARY else ButtonRole.SECONDARY) {
                statsPeriod = index
                statsOffset = 0
                renderContent()
            }
            selector.addView(outlinedSelection(control, darkHeader, chosen),
                LinearLayout.LayoutParams(0, dp(53), 1f).apply { setMargins(dp(1), 0, dp(1), 0) })
        }
        c.addView(selector)
        gap(c, 8)
        if (statsPeriod != 3) {
            val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            nav.addView(styledAction("‹", ButtonRole.SECONDARY) { statsOffset--; renderContent() },
                LinearLayout.LayoutParams(dp(50), dp(42)))
            val range = periodRange()
            nav.addView(label(dateFormat.format(Date(range.first)), 14f, true).apply { gravity = Gravity.CENTER },
                LinearLayout.LayoutParams(0, dp(42), 1f))
            nav.addView(styledAction("›", ButtonRole.SECONDARY) {
                if (statsOffset < 0) { statsOffset++; renderContent() }
            }, LinearLayout.LayoutParams(dp(50), dp(42)))
            c.addView(nav)
        }
        addCard(c)
        val (from, to) = periodRange()
        if (to > from) {
            renderPeriodTotals(from, to)
            gap(content, 16)
            val trend = card()
            trend.addView(label("PRZEBIEG CZASU W OKRESIE", 12f, true, muted))
            gap(trend, 12)
            barChart(from, to, trend)
            addCard(trend)
        }
    }

    private fun outlinedSelection(button: Button, color: Int, selected: Boolean): FrameLayout =
        AnimatedSelectionFrame(this, button, color, selected)

    private fun renderHistory() {
        // Day selection is the first control, followed by actions and participant filters.
        dayNavigator(true)
        gap(content, 8)

        // Wrap filters into as many rows as needed, with no horizontal scrolling.
        val availableWidth = resources.displayMetrics.widthPixels - dp(32)
        val filterRows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        var filterRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        var usedWidth = 0
        (listOf(0) + participants()).forEach { person ->
            val name = if (person == 0) tr(R.string.all_people) else personName(person)
            val selected = historyPerson == person
            val participantColor = if (person == 0) darkHeader else personColor(person)
            val button = participantAction(name, participantColor, selected) {
                historyPerson = person
                renderContent()
            }.apply {
                textSize = 13f
                minHeight = dp(38)
                minimumHeight = dp(38)
                setPadding(dp(12), 0, dp(12), 0)
                isSingleLine = true
                setAllCaps(false)
            }
            val measuredWidth = (button.paint.measureText(name) + dp(44)).toInt()
                .coerceAtLeast(dp(78)).coerceAtMost(availableWidth)
            val widthWithMargins = measuredWidth + dp(6)
            if (usedWidth > 0 && usedWidth + widthWithMargins > availableWidth) {
                filterRows.addView(filterRow)
                filterRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                usedWidth = 0
            }
            filterRow.addView(outlinedSelection(button, participantColor, selected),
                LinearLayout.LayoutParams(measuredWidth, dp(50)).apply {
                    setMargins(dp(2), dp(2), dp(4), dp(4))
                })
            usedWidth += widthWithMargins
        }
        if (filterRow.childCount > 0) filterRows.addView(filterRow)
        content.addView(filterRows)
        gap(content, 12)
        renderTimeline(inHistory = true)
        val measurementsHeading = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        measurementsHeading.addView(label(tr(R.string.measurements_heading), 21f, true, ink).apply {
            letterSpacing = -0.02f
        }, LinearLayout.LayoutParams(0, -2, 1f))
        if (!isArchived()) {
            val addMeasurement = styledAction("＋", ButtonRole.PRIMARY) { addSession() }.apply {
                contentDescription = tr(R.string.add_measurement).removePrefix("+").trim()
                textSize = 24f
                minHeight = dp(40)
                minimumHeight = dp(40)
                minWidth = dp(44)
                minimumWidth = dp(44)
                setPadding(0, 0, 0, 0)
            }
            measurementsHeading.addView(addMeasurement, LinearLayout.LayoutParams(dp(44), dp(40)))
        }
        gap(content, 12)
        content.addView(measurementsHeading)
        gap(content, 12)
        sessionList(dayStart(dayOffset), dayStart(dayOffset + 1), 500)
    }

    private fun rename(person: Int) {
        val input = EditText(this).apply { setText(personName(person)); setSingleLine(true) }
        val holder = LinearLayout(this).apply {
            setPadding(dp(20), 0, dp(20), 0)
            addView(input)
        }
        AlertDialog.Builder(this).setTitle(tr(R.string.rename_person_prefix) + person)
            .setView(holder)
            .setNegativeButton(tr(R.string.cancel), null)
            .setPositiveButton(tr(R.string.save)) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    db.update("participants", ContentValues().apply { put("name", name) }, "id=? AND project_id=?", arrayOf(person.toString(), projectId.toString()))
                    renderContent()
                }
            }.show()
    }

    private fun renameProject() {
        val id = projectId ?: return
        val input = EditText(this).apply { setText(projectName()); setSingleLine(true); selectAll() }
        AlertDialog.Builder(this).setTitle(tr(R.string.rename_project_title)).setView(input)
            .setNegativeButton(tr(R.string.cancel), null).setPositiveButton(tr(R.string.save)) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    projectRepository.rename(id, name)
                    renderContent()
                }
            }.show()
    }

    private fun removeParticipant(person: Int) {
        db.rawQuery("SELECT COUNT(*) FROM sessions WHERE project_id=? AND person=?",
            arrayOf(projectId.toString(), person.toString())).use { c ->
            c.moveToFirst()
            if (c.getLong(0) > 0L) {
                AlertDialog.Builder(this).setTitle(tr(R.string.participant_has_measurements))
                    .setMessage(tr(R.string.cannot_delete_participant))
                    .setPositiveButton("OK", null).show()
                return
            }
        }
        AlertDialog.Builder(this).setTitle(tr(R.string.delete_participant))
            .setMessage(tr(R.string.delete_participant_prefix) + personName(person) + tr(R.string.delete_participant_suffix))
            .setNegativeButton(tr(R.string.cancel), null)
            .setPositiveButton(tr(R.string.delete)) { _, _ ->
                db.delete("participants", "id=? AND project_id=?",
                    arrayOf(person.toString(), projectId.toString()))
                renderContent()
            }.show()
    }

    private fun exportCsv() {
        pendingExportProject = projectId
        val name = projectName().replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/csv"
            putExtra(Intent.EXTRA_TITLE, "timetrkr_${name}.csv")
        }
        startActivityForResult(intent, 301)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 301) return
        val id = pendingExportProject
        pendingExportProject = null
        val uri: Uri = data?.data ?: return
        if (resultCode != Activity.RESULT_OK || id == null) return
        fun csv(value: String) = "\"" + value.replace("\"", "\"\"") + "\""
        try {
            contentResolver.openOutputStream(uri)?.use { output ->
                OutputStreamWriter(output, Charsets.UTF_8).use { writer ->
                    writer.write("\uFEFF")
                    writer.write(tr(R.string.csv_header) + "\n")
                    val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT)
                    db.rawQuery(
                        "SELECT p.name,s.started_at,s.ended_at FROM sessions s JOIN participants p ON p.id=s.person WHERE s.project_id=? ORDER BY s.started_at",
                        arrayOf(id.toString())
                    ).use { cursor ->
                        while (cursor.moveToNext()) {
                            val name = cursor.getString(0)
                            val start = cursor.getLong(1)
                            val end = if (cursor.isNull(2)) null else cursor.getLong(2)
                            val date = fmt.format(Date(start))
                            val endDate = end?.let { fmt.format(Date(it)) } ?: ""
                            val secs = ((end ?: System.currentTimeMillis()) - start).coerceAtLeast(0) / 1000
                            writer.write(listOf(csv(name), csv(date.take(10)), csv(date),
                                csv(endDate), secs.toString(), if (end == null) tr(R.string.csv_running) else tr(R.string.csv_finished)).joinToString(";") + "\n")
                        }
                    }
                }
            } ?: throw IllegalStateException(tr(R.string.export_file_error))
            Toast.makeText(this, tr(R.string.export_done), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, tr(R.string.export_failed_prefix) + e.message, Toast.LENGTH_LONG).show()
        }
    }

    private fun renderSettings() {
        if (isArchived()) {
            content.addView(label(tr(R.string.archive_readonly), 23f, true, Color.rgb(151, 87, 31)))
            gap(content, 12)
            val info = card()
            info.addView(label(tr(R.string.archived_project), 19f, true))
            gap(info, 8)
            info.addView(label(projectName(), 17f, false))
            gap(info, 10)
            info.addView(label(tr(R.string.participants_colon) + participants().joinToString(", ") { personName(it) }, 14f, false, muted))
            gap(info, 10)
            info.addView(label(tr(R.string.locked_data), 14f, false, muted))
            addCard(info)
            content.addView(styledAction(tr(R.string.export_csv), ButtonRole.SECONDARY) { exportCsv() })
            return
        }
        val project = card()
        // The project name already appears in the persistent app header.
        project.addView(styledAction(tr(R.string.rename_project), ButtonRole.SECONDARY) {
            renameProject()
        })
        gap(project, 14)
        val iconRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        iconRow.addView(projectIcon(projectId!!, 60))
        iconRow.addView(label(tr(R.string.project_icon), 16f, true).apply { setPadding(dp(14), 0, 0, 0) })
        project.addView(iconRow)
        gap(project, 10)
        project.addView(styledAction(tr(R.string.regenerate_icon), ButtonRole.SECONDARY) {
            regenerateProjectIcon()
        })
        addCard(project)

        val people = card()
        val projectParticipants = participants()
        people.addView(label(tr(R.string.participants_label) + projectParticipants.size, 19f, true))
        gap(people, 10)
        projectParticipants.forEach { person ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(52)
            }
            row.addView(avatar(person), LinearLayout.LayoutParams(dp(36), dp(36)))
            row.addView(label(personName(person), 15f, true).apply {
                setPadding(dp(10), 0, dp(4), 0)
                setSingleLine(true)
                ellipsize = android.text.TextUtils.TruncateAt.END
            }, LinearLayout.LayoutParams(0, -2, 1f))
            val arrow = label("›", 23f, false, muted).apply { gravity = Gravity.CENTER }
            row.addView(arrow, LinearLayout.LayoutParams(dp(20), dp(48)))
            val tray = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                clipChildren = true
            }
            tray.addView(label("●", 24f, true, personColor(person)).apply {
                gravity = Gravity.CENTER
                contentDescription = "Color"
                setOnClickListener { editParticipantColor(person) }
            }, LinearLayout.LayoutParams(dp(42), dp(48)))
            tray.addView(editIconButton { rename(person) },
                LinearLayout.LayoutParams(dp(42), dp(48)))
            tray.addView(deleteIconButton { removeParticipant(person) },
                LinearLayout.LayoutParams(dp(42), dp(48)))
            row.addView(tray, LinearLayout.LayoutParams(0, dp(48)))
            var expanded = false
            var animation: ValueAnimator? = null
            row.isClickable = true
            row.isFocusable = true
            row.contentDescription = personName(person) +
                ". Dotknij, aby pokazać lub ukryć akcje uczestnika"
            row.setOnClickListener {
                expanded = !expanded
                animation?.cancel()
                val currentWidth = tray.layoutParams.width.coerceAtLeast(0)
                val targetWidth = if (expanded) dp(126) else 0
                if (expanded && currentWidth == 0) {
                    tray.translationX = dp(36).toFloat()
                    tray.alpha = 0f
                }
                val fromOffset = tray.translationX
                val fromAlpha = tray.alpha
                val fromArrowRotation = arrow.rotation
                animation = ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = 260L
                    interpolator = android.view.animation.PathInterpolator(0.4f, 0f, 0.2f, 1f)
                    addUpdateListener { animator ->
                        val fraction = animator.animatedValue as Float
                        tray.layoutParams = tray.layoutParams.apply {
                            width = currentWidth + ((targetWidth - currentWidth) * fraction).toInt()
                        }
                        tray.translationX = fromOffset * (1f - fraction) +
                            (if (expanded) 0f else dp(36).toFloat()) * fraction
                        tray.alpha = fromAlpha + ((if (expanded) 1f else 0f) - fromAlpha) * fraction
                        arrow.rotation = fromArrowRotation + ((if (expanded) 90f else 0f) - fromArrowRotation) * fraction
                    }
                    start()
                }
            }
            people.addView(row)
            gap(people, 7)
        }
        addCard(people)

        val data = card()
        data.addView(label(tr(R.string.project_data), 19f, true))
        gap(data, 12)
        data.addView(styledAction(tr(R.string.export_csv), ButtonRole.SECONDARY) {
            exportCsv()
        })
        gap(data, 8)
        data.addView(styledAction(tr(R.string.archive_project), ButtonRole.WARNING) { archiveProject() })
        addCard(data)


    }

    private fun renderContent() {
        synchronizeTabService()?.refresh()
        timerUpdates.clear()
        content.removeAllViews()
        calendarHeadingAnimator?.cancel()
        calendarHeading = null
        calendarHeadingExpanded = true
        stickyDateContainer.removeAllViews()
        stickyDateContainer.visibility = if (projectId != null && (tab == 1 || tab == 2)) View.VISIBLE else View.GONE
        stickyDateContainer.setBackgroundColor(if (darkTheme) surface else darkHeader)
        if (projectId == null) {
            content.setBackgroundColor(background)
            if (showAppSettings) renderAppSettings() else renderProjects()
            return
        }
        val archived = isArchived()
        content.setBackgroundColor(if (archived) if (darkTheme) Color.rgb(53, 41, 34) else Color.rgb(252, 239, 219) else background)
        if (archived) {
            content.addView(label(tr(R.string.archive_banner), 13f, true, Color.rgb(147, 82, 27)))
            gap(content, 7)
        }
        when (tab) {
            0 -> timersScreen.render(requireNotNull(projectId))
            1 -> renderStats()
            2 -> renderHistory()
            else -> renderSettings()
        }
    }

    private fun activeCountText(count: Int): String {
        val language = resources.configuration.locales[0].language
        return when (language) {
            "pl" -> {
                val suffix = if (count == 1) "aktywny" else "aktywnych"
                "$count $suffix"
            }
            else -> "$count active"
        }
    }

    private fun renderTopHeader() {
        topHeader.removeAllViews()
        if (projectId == null) {
            topHeader.addView(label("◷  " + tr(R.string.app_title), 21f, true, Color.WHITE))
            return
        }
        val left = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        left.addView(label("‹", 29f, false, Color.WHITE).apply {
            gravity = Gravity.CENTER
            contentDescription = tr(R.string.back_to_projects_description)
            setOnClickListener { projectId = null; render() }
        }, LinearLayout.LayoutParams(dp(38), dp(40)))
        left.addView(projectIcon(projectId!!, 32), LinearLayout.LayoutParams(dp(32), dp(32)))
        topHeader.addView(left)

        val details = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), 0, 0, 0)
        }
        details.addView(label(projectName(), 17f, true, Color.WHITE).apply {
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
        }, LinearLayout.LayoutParams(-1, -2))

        val activePeople = mutableListOf<Int>()
        db.rawQuery(
            "SELECT DISTINCT person FROM sessions WHERE project_id=? AND ended_at IS NULL ORDER BY person",
            arrayOf(projectId.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) activePeople.add(cursor.getInt(0))
        }
        if (activePeople.isNotEmpty()) {
            val status = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(3), 0, 0)
            }
            activePeople.forEach { person ->
                status.addView(View(this).apply {
                    background = shape(personColor(person), 4)
                }, LinearLayout.LayoutParams(dp(7), dp(7)).apply {
                    rightMargin = dp(4)
                })
            }
            status.addView(label(activeCountText(activePeople.size), 12f, false,
                Color.rgb(185, 208, 205)).apply {
                setPadding(dp(3), 0, 0, 0)
            })
            details.addView(status)
        }
        topHeader.addView(details, LinearLayout.LayoutParams(0, -2, 1f))
    }

    private fun render() {
        renderTopHeader()
        renderContent()
        navigation.removeAllViews()
        if (projectId == null) { navigation.visibility = View.GONE; return }
        navigation.visibility = View.VISIBLE
        listOf("◷\n" + tr(R.string.timers), "▥\n" + tr(R.string.statistics), "☷\n" + tr(R.string.history), "⚙\n" + tr(R.string.settings)).forEachIndexed { index, name ->
            val item = label(name, 12f, tab == index,
                if (darkTheme) { if (tab == index) blue else muted }
                else if (tab == index) Color.WHITE else Color.rgb(183, 204, 202)).apply {
                gravity = Gravity.CENTER
                setPadding(dp(2), dp(8), dp(2), dp(8))
                background = if (tab == index) shape(if (darkTheme) subtle else Color.rgb(44, 79, 83), 16)
                    else shape(Color.TRANSPARENT, 16)
                contentDescription = name.replace("\n", " ")
                setOnClickListener { tab = index; render() }
            }
            navigation.addView(item, LinearLayout.LayoutParams(0, dp(58), 1f).apply {
                leftMargin = dp(3)
                rightMargin = dp(3)
            })
        }
    }

    // A resumed Activity does not necessarily reattach its views, so restart
    // running-session backgrounds explicitly when returning from the background.
    private fun updateActiveAnimations(view: View, running: Boolean) {
        (view.background as? android.graphics.drawable.Animatable)?.let { animation ->
            if (running) animation.start() else animation.stop()
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                updateActiveAnimations(view.getChildAt(index), running)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::content.isInitialized) updateActiveAnimations(content, true)
    }

    override fun onPause() {
        if (::content.isInitialized) updateActiveAnimations(content, false)
        super.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        databaseService.close()
        super.onDestroy()
    }
}
