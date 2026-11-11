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
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var db: SQLiteDatabase
    private lateinit var content: LinearLayout
    private lateinit var navigation: LinearLayout
    private val handler = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences("preferences", MODE_PRIVATE) }
    private var tab = 0
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
    private val darkTheme get() = prefs.getString("app_theme", "light") == "dark"
    private val blue get() = if (darkTheme) Color.rgb(113, 169, 255) else Color.rgb(34, 112, 237)
    private val green get() = if (darkTheme) Color.rgb(101, 211, 142) else Color.rgb(48, 158, 80)
    private val ink get() = if (darkTheme) Color.rgb(238, 241, 248) else Color.rgb(26, 34, 43)
    private val muted get() = if (darkTheme) Color.rgb(171, 181, 196) else Color.rgb(99, 109, 120)
    private val surface get() = if (darkTheme) Color.rgb(35, 42, 54) else Color.WHITE
    private val background get() = if (darkTheme) Color.rgb(21, 26, 35) else Color.rgb(242, 244, 247)
    private val subtle get() = if (darkTheme) Color.rgb(52, 62, 79) else Color.rgb(226, 235, 252)
    private val subtleNeutral get() = if (darkTheme) Color.rgb(51, 59, 72) else Color.rgb(229, 233, 239)
    private val subtleWarm get() = if (darkTheme) Color.rgb(73, 56, 42) else Color.rgb(245, 229, 209)
    private val secondaryButtonText get() = if (darkTheme) ink else blue
    private val dateFormat get() = SimpleDateFormat("dd MMM yyyy", resources.configuration.locales[0])
    private val clockFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyLanguage()
        applyThemeToSystem()
        db = object : SQLiteOpenHelper(this, "sessions.db", null, 4) {
            override fun onCreate(database: SQLiteDatabase) {
                database.execSQL("CREATE TABLE projects (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, archived INTEGER NOT NULL DEFAULT 0, icon_variant INTEGER NOT NULL DEFAULT 0)")
                database.execSQL("CREATE TABLE participants (id INTEGER PRIMARY KEY AUTOINCREMENT, project_id INTEGER NOT NULL, name TEXT NOT NULL)")
                database.execSQL("CREATE TABLE sessions (id INTEGER PRIMARY KEY AUTOINCREMENT, project_id INTEGER NOT NULL, person INTEGER NOT NULL, started_at INTEGER NOT NULL, ended_at INTEGER)")
                database.execSQL("CREATE UNIQUE INDEX one_active_per_person ON sessions(person) WHERE ended_at IS NULL")
            }
            override fun onUpgrade(database: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
                if (oldVersion < 2) {
                    database.execSQL("CREATE TABLE projects (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL)")
                    database.execSQL("CREATE TABLE participants (id INTEGER PRIMARY KEY AUTOINCREMENT, project_id INTEGER NOT NULL, name TEXT NOT NULL)")
                    database.execSQL("INSERT INTO projects(name) VALUES ('Poprzednie pomiary')")
                    database.execSQL("INSERT INTO participants(id,project_id,name) VALUES (1,1,?)", arrayOf(prefs.getString("name_1", tr(R.string.legacy_person_1))))
                    database.execSQL("INSERT INTO participants(id,project_id,name) VALUES (2,1,?)", arrayOf(prefs.getString("name_2", tr(R.string.legacy_person_2))))
                    database.execSQL("ALTER TABLE sessions ADD COLUMN project_id INTEGER NOT NULL DEFAULT 1")
                }
                if (oldVersion < 3) database.execSQL("ALTER TABLE projects ADD COLUMN archived INTEGER NOT NULL DEFAULT 0")
                if (oldVersion < 4) database.execSQL("ALTER TABLE projects ADD COLUMN icon_variant INTEGER NOT NULL DEFAULT 0")
            }
        }.writableDatabase
        applyThemeToSystem()
        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(this@MainActivity.background)
        }
        val heading = TextView(this).apply {
            text = "◷  " + tr(R.string.app_title)
            textSize = 25f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setBackgroundColor(if (darkTheme) Color.rgb(13, 17, 25) else Color.rgb(25, 34, 44))
            setPadding(dp(20), dp(18), dp(20), dp(18))
        }
        outer.addView(heading)
        val scroll = ScrollView(this).apply { isFillViewport = true }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(20))
        }
        scroll.addView(content)
        outer.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        navigation = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(surface)
        }
        outer.addView(navigation)
        setContentView(outer)
        render()
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

    private fun dp(n: Int): Int = (n * resources.displayMetrics.density + .5f).toInt()
    private fun shape(color: Int, radius: Int = 18): GradientDrawable =
        GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }

    private fun tr(id: Int): String = getString(id)

    private fun applyThemeToSystem() {
        window.statusBarColor = if (darkTheme) Color.rgb(13, 17, 25) else Color.rgb(25, 34, 44)
        window.navigationBarColor = background
        window.decorView.systemUiVisibility = if (darkTheme) 0 else View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }

    private fun selectTheme() {
        val choices = arrayOf(tr(R.string.theme_light), tr(R.string.theme_dark))
        val selected = if (darkTheme) 1 else 0
        AlertDialog.Builder(this).setTitle(tr(R.string.theme))
            .setSingleChoiceItems(choices, selected) { dialog, which ->
                prefs.edit().putString("app_theme", if (which == 1) "dark" else "light").apply()
                dialog.dismiss()
                recreate()
            }.setNegativeButton(tr(R.string.cancel), null).show()
    }

    private fun selectLanguage() {
        val choices = arrayOf(tr(R.string.system_language), tr(R.string.polish), tr(R.string.english))
        val codes = arrayOf("", "pl", "en")
        val selected = codes.indexOf(prefs.getString("app_language", "") ?: "").coerceAtLeast(0)
        AlertDialog.Builder(this).setTitle(tr(R.string.language))
            .setSingleChoiceItems(choices, selected) { dialog, which ->
                prefs.edit().putString("app_language", codes[which]).apply()
                dialog.dismiss()
                recreate()
            }.setNegativeButton(tr(R.string.cancel), null).show()
    }

    private fun applyLanguage() {
        val code = prefs.getString("app_language", "") ?: ""
        val locale = if (code.isEmpty()) android.content.res.Resources.getSystem().configuration.locales[0] else Locale(code)
        Locale.setDefault(locale)
        val config = android.content.res.Configuration(resources.configuration)
        config.setLocale(locale)
        resources.updateConfiguration(config, resources.displayMetrics)
    }

    private fun label(value: String, size: Float = 16f, bold: Boolean = false, color: Int = ink): TextView =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER_VERTICAL
        }

    private fun gap(parent: LinearLayout, height: Int = 12) {
        parent.addView(View(this), LinearLayout.LayoutParams(1, dp(height)))
    }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(18), dp(18), dp(18))
        background = shape(surface, 14).apply { setStroke(dp(1), if (darkTheme) Color.rgb(66, 77, 95) else Color.rgb(220, 225, 232)) }
        elevation = dp(2).toFloat()
    }

    private fun activeCardBackground(): Drawable = object : Drawable(), android.graphics.drawable.Animatable {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private var phase = 0f
        private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 6500L
            repeatCount = ValueAnimator.INFINITE
            interpolator = android.view.animation.LinearInterpolator()
            addUpdateListener { phase = it.animatedValue as Float; invalidateSelf() }
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
            for (i in 0 until 8) {
                val x = w * (.71f + (i * .037f)) - dp(12) * phase
                val y = ((i * .173f + phase * (.16f + i % 3 * .025f)) % 1f) * (h + dp(24)) - dp(12)
                val side = dp(5 + i % 3).toFloat()
                p.color = if (darkTheme) Color.argb(55, 114, 226, 154) else Color.argb(58, 47, 166, 93)
                canvas.drawRoundRect(RectF(x, y, x + side, y + side), dp(1).toFloat(), dp(1).toFloat(), p)
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
        Button(this).apply {
            this.text = text
            textSize = 16f
            isAllCaps = false
            setTextColor(fg)
            background = shape(bg, 10)
            stateListAnimator = null
            elevation = 0f
            setOnClickListener { click() }
        }

    private fun participants(): List<Int> {
        val ids = mutableListOf<Int>()
        db.rawQuery("SELECT id FROM participants WHERE project_id=? ORDER BY id", arrayOf(projectId.toString())).use { c ->
            while (c.moveToNext()) ids.add(c.getInt(0))
        }
        return ids
    }
    private fun personName(person: Int): String {
        db.rawQuery("SELECT name FROM participants WHERE id=?", arrayOf(person.toString())).use { c ->
            return if (c.moveToFirst()) c.getString(0) else tr(R.string.person_fallback) + person
        }
    }
    private fun projectName(): String {
        db.rawQuery("SELECT name FROM projects WHERE id=?", arrayOf(projectId.toString())).use { c ->
            return if (c.moveToFirst()) c.getString(0) else tr(R.string.project_fallback)
        }
    }
    private fun createProject() {
        val input = EditText(this).apply { hint = tr(R.string.project_name_hint); setSingleLine(true) }
        val holder = LinearLayout(this).apply { setPadding(dp(20), 0, dp(20), 0); addView(input) }
        val dialog = AlertDialog.Builder(this).setTitle(tr(R.string.new_project)).setView(holder)
            .setNegativeButton(tr(R.string.cancel), null).setPositiveButton(tr(R.string.add), null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = input.text.toString().trim()
                if (name.isEmpty()) { input.error = tr(R.string.name_required); return@setOnClickListener }
                val id = db.insert("projects", null, ContentValues().apply { put("name", name) })
                if (id > 0) { projectId = id; tab = 0; dayOffset = 0; historyPerson = 0; dialog.dismiss(); render() }
            }
        }
        dialog.show()
    }
    private fun addParticipant() {
        val input = EditText(this).apply { hint = tr(R.string.participant_hint); setSingleLine(true) }
        val holder = LinearLayout(this).apply { setPadding(dp(20), 0, dp(20), 0); addView(input) }
        val dialog = AlertDialog.Builder(this).setTitle(tr(R.string.new_participant)).setView(holder)
            .setNegativeButton(tr(R.string.cancel), null).setPositiveButton(tr(R.string.add), null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = input.text.toString().trim()
                if (name.isEmpty()) { input.error = tr(R.string.name_required); return@setOnClickListener }
                db.insert("participants", null, ContentValues().apply {
                    put("project_id", projectId); put("name", name)
                })
                dialog.dismiss()
                renderContent()
            }
        }
        dialog.show()
    }
    // Deterministic, entirely offline miniature landscapes.
    private fun iconVariant(id: Long): Long =
        db.rawQuery("SELECT icon_variant FROM projects WHERE id=?", arrayOf(id.toString())).use {
            if (it.moveToFirst()) it.getLong(0) else 0L
        }

    private fun regenerateProjectIcon() {
        if (isArchived()) return
        db.execSQL("UPDATE projects SET icon_variant=icon_variant+1 WHERE id=? AND archived=0", arrayOf(projectId))
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

    private fun isArchived(): Boolean {
        val id = projectId ?: return false
        return db.rawQuery("SELECT archived FROM projects WHERE id=?", arrayOf(id.toString())).use {
            it.moveToFirst() && it.getInt(0) == 1
        }
    }

    private fun archiveProject() {
        if (isArchived()) return
        val count = db.rawQuery("SELECT COUNT(*) FROM sessions WHERE project_id=? AND ended_at IS NULL", arrayOf(projectId.toString())).use {
            if (it.moveToFirst()) it.getInt(0) else 0
        }
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
                db.execSQL("UPDATE projects SET archived=1 WHERE id=? AND archived=0", arrayOf(projectId))
                projectId = null
                showArchived = false
                render()
            }.show()
    }

    private fun renderAppSettings() {
        val back = label("←  " + tr(R.string.back_projects).removePrefix("← "), 16f, true, blue).apply {
            setPadding(0, dp(8), 0, dp(16))
            setOnClickListener { showAppSettings = false; render() }
        }
        content.addView(back)
        content.addView(label(tr(R.string.settings), 24f, true))
        gap(content, 16)
        val app = card()
        app.addView(label(tr(R.string.application), 19f, true))
        gap(app, 12)
        app.addView(action("🌐  " + tr(R.string.language), subtle, secondaryButtonText) { selectLanguage() })
        gap(app, 10)
        app.addView(action("◐  " + tr(R.string.theme), subtle, secondaryButtonText) { selectTheme() })
        gap(app, 12)
        val version = try { packageManager.getPackageInfo(packageName, 0).versionName ?: "—" } catch (_: Exception) { "—" }
        app.addView(label(tr(R.string.version) + version, 14f, false, muted))
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
            val addButton = label("+", 27f, true, Color.WHITE).apply {
                gravity = Gravity.CENTER
                contentDescription = tr(R.string.add_project)
                background = shape(blue, 12)
                setOnClickListener { createProject() }
            }
            heading.addView(addButton, LinearLayout.LayoutParams(dp(42), dp(42)).apply {
                setMargins(dp(12), 0, 0, 0)
            })
        }
        content.addView(heading)
        gap(content, 14)
        val projects = mutableListOf<Pair<Long, String>>()
        db.rawQuery("SELECT id,name FROM projects WHERE archived=? ORDER BY id DESC", arrayOf(if (showArchived) "1" else "0")).use { c ->
            while (c.moveToNext()) projects.add(c.getLong(0) to c.getString(1))
        }
        if (projects.isEmpty()) {
            content.addView(label(tr(R.string.no_projects), 16f, false, muted))
            gap(content, 18)
        }
        projects.forEach { (id, name) ->
            val item = card()
            val counts = db.rawQuery(
                "SELECT (SELECT COUNT(*) FROM participants WHERE project_id=?), (SELECT COUNT(*) FROM sessions WHERE project_id=? AND ended_at IS NULL)",
                arrayOf(id.toString(), id.toString())
            ).use { c -> if (c.moveToFirst()) c.getInt(0) to c.getInt(1) else 0 to 0 }
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
            content.addView(action(tr(R.string.back_projects), subtleWarm, ink) {
                showArchived = false; render()
            })
        } else {
            content.addView(action(tr(R.string.archived_link), subtleWarm, Color.rgb(133, 76, 27)) {
                showArchived = true; render()
            })
            gap(content, 18)
            content.addView(label("⚙  " + tr(R.string.settings), 14f, false, muted).apply {
                gravity = Gravity.CENTER
                setPadding(dp(12), dp(14), dp(12), dp(14))
                setOnClickListener { showAppSettings = true; render() }
            })
        }
    }
    private fun personColor(person: Int) = if (participants().indexOf(person) % 2 == 0) blue else green

    private fun activeStart(person: Int): Long? {
        db.rawQuery("SELECT started_at FROM sessions WHERE project_id=? AND person=? AND ended_at IS NULL LIMIT 1", arrayOf(projectId.toString(), person.toString())).use {
            return if (it.moveToFirst()) it.getLong(0) else null
        }
    }

    private fun toggle(person: Int) {
        val now = System.currentTimeMillis()
        db.beginTransaction()
        try {
            if (activeStart(person) == null) {
                db.execSQL("INSERT INTO sessions(project_id,person,started_at) VALUES (?,?,?)", arrayOf(projectId, person, now))
            } else {
                db.execSQL("UPDATE sessions SET ended_at=? WHERE project_id=? AND person=? AND ended_at IS NULL", arrayOf(now, projectId, person))
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        syncTimerNotification()
        renderContent()
    }

    private fun syncTimerNotification() {
        val hasActive = db.rawQuery("SELECT 1 FROM sessions WHERE ended_at IS NULL LIMIT 1", null).use { it.moveToFirst() }
        val intent = Intent(this, TimerNotificationService::class.java)
        if (hasActive) {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
        } else {
            stopService(intent)
        }
    }

    private fun duration(ms: Long): String {
        val s = ms.coerceAtLeast(0) / 1000
        return String.format(Locale.ROOT, "%02d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60)
    }

    private fun dayStart(offset: Int): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        add(Calendar.DAY_OF_YEAR, offset)
    }.timeInMillis

    private fun total(person: Int, start: Long, end: Long, now: Long = System.currentTimeMillis()): Long {
        var sum = 0L
        db.rawQuery(
            "SELECT started_at,ended_at FROM sessions WHERE project_id=? AND person=? AND started_at<? AND (ended_at IS NULL OR ended_at>?)",
            arrayOf(projectId.toString(), person.toString(), end.toString(), start.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val a = maxOf(start, cursor.getLong(0))
                val b = minOf(end, if (cursor.isNull(1)) now else cursor.getLong(1))
                sum += (b - a).coerceAtLeast(0)
            }
        }
        return sum
    }

    private fun datePickerRow() {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val previous = action("‹", Color.TRANSPARENT, ink) { dayOffset--; renderContent() }
        row.addView(previous, LinearLayout.LayoutParams(dp(54), dp(48)))
        val date = label(
            if (dayOffset == 0) tr(R.string.today) + dateFormat.format(Date(dayStart(0))) else dateFormat.format(Date(dayStart(dayOffset))),
            16f, true
        ).apply { gravity = Gravity.CENTER }
        row.addView(date, LinearLayout.LayoutParams(0, dp(48), 1f))
        val next = action("›", Color.TRANSPARENT, if (dayOffset >= 0) muted else ink) {
            if (dayOffset < 0) { dayOffset++; renderContent() }
        }
        row.addView(next, LinearLayout.LayoutParams(dp(54), dp(48)))
        content.addView(row)
        gap(content, 10)
    }

    private fun timerCard(person: Int, start: Long, end: Long, now: Long) {
        val c = card()
        val active = activeStart(person)
        if (active != null) markActive(c)
        val name = label("●  " + personName(person), 20f, true, personColor(person))
        c.addView(name)
        gap(c, 12)
        val totalLabel = label(duration(total(person, start, end, now)), 37f, true)
        c.addView(totalLabel)
        if (active != null) timerUpdates.add { tick ->
            totalLabel.text = duration(total(person, start, end, tick))
        }
        gap(c, 5)
        c.addView(label(tr(R.string.selected_day_total), 14f, false, muted))
        if (dayOffset == 0 && !isArchived()) {
            gap(c, 14)
            val statusLabel = label(if (active == null) tr(R.string.timer_stopped) else tr(R.string.running_prefix) + duration(now - active), 16f, false, muted)
            c.addView(statusLabel)
            if (active != null) timerUpdates.add { tick ->
                statusLabel.text = tr(R.string.running_prefix) + duration(tick - active)
            }
            gap(c, 12)
            val button = action(if (active == null) "▶  START" else "■  STOP",
                if (active == null) personColor(person) else Color.rgb(185, 48, 48)) { toggle(person) }
            c.addView(button, LinearLayout.LayoutParams(-1, dp(54)))
        }
        addCard(c)
    }

    private fun pickDateTime(initial: Long, title: String, onChosen: (Long) -> Unit) {
        val cal = Calendar.getInstance().apply { timeInMillis = initial }
        DatePickerDialog(this, { _, year, month, day ->
            val selected = Calendar.getInstance().apply {
                timeInMillis = initial
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month)
                set(Calendar.DAY_OF_MONTH, day)
            }
            TimePickerDialog(this, { _, hour, minute ->
                selected.set(Calendar.HOUR_OF_DAY, hour)
                selected.set(Calendar.MINUTE, minute)
                selected.set(Calendar.SECOND, 0)
                selected.set(Calendar.MILLISECOND, 0)
                onChosen(selected.timeInMillis)
            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).apply { setTitle(title) }.show()
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))
            .apply { setTitle(title) }.show()
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
                        if (start >= end || end > System.currentTimeMillis()) {
                            Toast.makeText(this, tr(R.string.invalid_measurement), Toast.LENGTH_LONG).show()
                        } else {
                            val values = ContentValues().apply {
                                put("person", person)
                                put("project_id", projectId)
                                put("started_at", start)
                                put("ended_at", end)
                            }
                            if (db.insert("sessions", null, values) != -1L) {
                                dayOffset = 0
                                while (dayStart(dayOffset) > start) dayOffset--
                                while (dayStart(dayOffset + 1) <= start) dayOffset++
                                renderContent()
                                Toast.makeText(this, tr(R.string.measurement_added), Toast.LENGTH_SHORT).show()
                            } else Toast.makeText(this, tr(R.string.measurement_add_failed), Toast.LENGTH_LONG).show()
                        }
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
                val deleted = db.delete("sessions", "id=? AND ended_at IS NOT NULL", arrayOf(id.toString()))
                if (deleted > 0) {
                    renderContent()
                    Toast.makeText(this, tr(R.string.measurement_deleted), Toast.LENGTH_SHORT).show()
                }
            }.show()
    }

    private fun editSession(id: Long, person: Int, started: Long, ended: Long?) {
        if (isArchived()) return
        if (ended == null) {
            Toast.makeText(this, tr(R.string.stop_before_edit), Toast.LENGTH_LONG).show()
            return
        }
        fun chooseDateTime(initial: Long, title: String, onChosen: (Long) -> Unit) {
            val cal = Calendar.getInstance().apply { timeInMillis = initial }
            DatePickerDialog(this, { _, year, month, day ->
                val selected = Calendar.getInstance().apply {
                    timeInMillis = initial
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, day)
                }
                TimePickerDialog(this, { _, hour, minute ->
                    selected.set(Calendar.HOUR_OF_DAY, hour)
                    selected.set(Calendar.MINUTE, minute)
                    selected.set(Calendar.SECOND, 0)
                    selected.set(Calendar.MILLISECOND, 0)
                    onChosen(selected.timeInMillis)
                }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true)
                    .apply { setTitle(title) }.show()
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))
                .apply { setTitle(title) }.show()
        }
        val format = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT, resources.configuration.locales[0])
        AlertDialog.Builder(this)
            .setTitle(tr(R.string.edit_session_prefix) + personName(person))
            .setMessage(tr(R.string.from_prefix) + format.format(Date(started)) + "\n" + tr(R.string.to_prefix) + format.format(Date(ended)) +
                "\n\n" + tr(R.string.edit_dates_instruction))
            .setNegativeButton(tr(R.string.cancel), null)
            .setPositiveButton(tr(R.string.edit)) { _, _ ->
                chooseDateTime(started, tr(R.string.new_start)) { newStart ->
                    chooseDateTime(ended, tr(R.string.new_end)) { newEnd ->
                        if (newEnd <= newStart) {
                            Toast.makeText(this, tr(R.string.end_after_start), Toast.LENGTH_LONG).show()
                        } else if (newEnd > System.currentTimeMillis()) {
                            Toast.makeText(this, tr(R.string.end_not_future), Toast.LENGTH_LONG).show()
                        } else {
                            val values = android.content.ContentValues().apply {
                                put("started_at", newStart)
                                put("ended_at", newEnd)
                            }
                            val updated = db.update("sessions", values, "id=? AND ended_at IS NOT NULL", arrayOf(id.toString()))
                            if (updated == 1) {
                                Toast.makeText(this, tr(R.string.session_updated), Toast.LENGTH_SHORT).show()
                                renderContent()
                            } else {
                                Toast.makeText(this, tr(R.string.session_update_failed), Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            }.show()
    }

    private fun avatar(person: Int): View {
        val name = personName(person).trim()
        val initials = name.split(Regex("\\s+")).filter { it.isNotBlank() }
            .take(2).map { it.take(1).uppercase(Locale.getDefault()) }.joinToString("")
        val palette = intArrayOf(0xFF356AC3.toInt(), 0xFF21896F.toInt(), 0xFF9B59B6.toInt(),
            0xFFD47A25.toInt(), 0xFFC34F69.toInt(), 0xFF526E83.toInt())
        val color = palette[Math.floorMod(person, palette.size)]
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
                if (kind == "edit") {
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
            background = shape(if (kind == "edit") if (darkTheme) Color.rgb(43, 60, 83) else Color.rgb(232, 239, 253) else if (darkTheme) Color.rgb(76, 45, 49) else Color.rgb(252, 233, 233), 9)
            stateListAnimator = null
            elevation = 0f
            contentDescription = if (kind == "edit") tr(R.string.edit_entry) else tr(R.string.delete_entry)
            isClickable = true
            setOnClickListener { onClick() }
        }
    }

    private fun sessionList(start: Long, end: Long, limit: Int = 100) {
        var count = 0
        val personClause = if (tab == 2 && historyPerson != 0) " AND person=$historyPerson" else ""
        db.rawQuery(
            "SELECT id,person,started_at,ended_at FROM sessions WHERE project_id=? AND started_at<? AND (ended_at IS NULL OR ended_at>?)$personClause ORDER BY started_at DESC LIMIT $limit",
            arrayOf(projectId.toString(), end.toString(), start.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                val person = cursor.getInt(1)
                val originalStart = cursor.getLong(2)
                val originalEnd = if (cursor.isNull(3)) null else cursor.getLong(3)
                val from = maxOf(start, originalStart)
                val until = minOf(end, originalEnd ?: System.currentTimeMillis())
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(12), dp(10), dp(10), dp(10))
                    background = shape(surface, 12)
                }
                if (originalEnd == null) markActive(row)
                val left = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                val heading = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                }
                heading.addView(avatar(person), LinearLayout.LayoutParams(dp(29), dp(29)))
                heading.addView(label("  " + personName(person) + "  •  " +
                    dateFormat.format(Date(originalStart)), 13f, true), LinearLayout.LayoutParams(-2, -2))
                left.addView(heading)
                gap(left, 5)
                val running = if (originalEnd == null) "  • trwa" else ""
                val rangeLabel = label(clockFormat.format(Date(from)) + " – " +
                    clockFormat.format(Date(until)) + running, 13f, false, muted)
                left.addView(rangeLabel)
                gap(left, 3)
                val elapsedLabel = label(duration(until - from), 16f, true)
                left.addView(elapsedLabel)
                if (originalEnd == null && tab == 0) timerUpdates.add { tick ->
                    val currentEnd = minOf(end, tick)
                    rangeLabel.text = clockFormat.format(Date(from)) + " – " +
                        clockFormat.format(Date(currentEnd)) + running
                    elapsedLabel.text = duration(currentEnd - from)
                }
                row.addView(left, LinearLayout.LayoutParams(0, -2, 1f))
                if (tab == 2 && originalEnd != null && !isArchived()) {
                    val controls = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                    controls.addView(iconButton("edit", blue) {
                        editSession(id, person, originalStart, originalEnd)
                    }, LinearLayout.LayoutParams(dp(42), dp(40)))
                    gap(controls, 4)
                    controls.addView(iconButton("delete", Color.rgb(170, 42, 42)) {
                        deleteSession(id)
                    }, LinearLayout.LayoutParams(dp(42), dp(40)))
                    row.addView(controls)
                }
                content.addView(row, LinearLayout.LayoutParams(-1, -2))
                gap(content, 7)
                count++
            }
        }
        if (count == 0) content.addView(label(tr(R.string.no_sessions), 15f, false, muted))
    }

    private fun renderTimers() {
        datePickerRow()
        val start = dayStart(dayOffset)
        val end = dayStart(dayOffset + 1)
        val now = System.currentTimeMillis()
        if (!isArchived()) content.addView(action(tr(R.string.add_person), subtle, secondaryButtonText) { addParticipant() })
        gap(content, 12)
        if (participants().isEmpty()) {
            content.addView(label(tr(R.string.first_person), 16f, false, muted))
            gap(content, 12)
        }
        participants().forEach { timerCard(it, start, end, now) }
        content.addView(label(tr(R.string.selected_day_sessions), 21f, true))
        gap(content, 12)
        sessionList(start, end, 8)
        gap(content, 8)
        content.addView(action(tr(R.string.full_history), subtle, secondaryButtonText) {
            tab = 2
            render()
        })
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
        db.rawQuery(
            "SELECT person,started_at,ended_at FROM sessions WHERE project_id=? AND started_at<? AND (ended_at IS NULL OR ended_at>?) ORDER BY started_at",
            arrayOf(projectId.toString(), end.toString(), start.toString())
        ).use { c ->
            while (c.moveToNext()) {
                val from = maxOf(start, c.getLong(1))
                val until = minOf(end, if (c.isNull(2)) System.currentTimeMillis() else c.getLong(2))
                if (until > from) result.add(Triple(c.getInt(0), from, until))
            }
        }
        return result
    }

    private fun barChart(from: Long, to: Long) {
        val points = mutableListOf<Pair<String, Long>>()
        val cal = Calendar.getInstance().apply { timeInMillis = from }
        val endLimit = if (statsPeriod == 3) System.currentTimeMillis() else to
        if (statsPeriod == 0) {
            for (h in 0..23) {
                val start = Calendar.getInstance().apply {
                    timeInMillis = from; set(Calendar.HOUR_OF_DAY, h)
                }.timeInMillis
                val nextHour = Calendar.getInstance().apply {
                    timeInMillis = start; add(Calendar.HOUR_OF_DAY, 1)
                }.timeInMillis
                points.add(String.format(Locale.ROOT, "%02d", h) to intervals(start, nextHour).sumOf { it.third - it.second })
            }
        } else {
            var count = 0
            while (cal.timeInMillis < endLimit && count < 1000) {
                val start = cal.timeInMillis
                when (statsPeriod) {
                    1 -> cal.add(Calendar.DAY_OF_YEAR, 1)
                    2 -> cal.add(Calendar.DAY_OF_YEAR, 1)
                    else -> cal.add(Calendar.MONTH, 1)
                }
                val finish = cal.timeInMillis
                val name = when (statsPeriod) {
                    1 -> SimpleDateFormat("EEE", resources.configuration.locales[0]).format(Date(start))
                    2 -> SimpleDateFormat("d", resources.configuration.locales[0]).format(Date(start))
                    else -> SimpleDateFormat("MM.yy", resources.configuration.locales[0]).format(Date(start))
                }
                points.add(name to intervals(start, finish).sumOf { it.third - it.second })
                count++
            }
        }
        if (points.isEmpty()) {
            content.addView(label(tr(R.string.no_measurements), 14f, false, muted))
            return
        }
        val maxValue = (points.maxOfOrNull { it.second } ?: 0L).coerceAtLeast(1L)
        val chart = object : View(this) {
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                val w = width.toFloat()
                val h = height.toFloat()
                val top = dp(12).toFloat()
                val bottom = h - dp(28)
                val plotH = bottom - top
                val step = (w - dp(8)) / points.size
                points.forEachIndexed { index, point ->
                    val bh = (point.second.toFloat() / maxValue * plotH).coerceAtLeast(if (point.second > 0) dp(2).toFloat() else 0f)
                    paint.color = blue
                    val x = dp(4) + index * step + step * 0.12f
                    canvas.drawRoundRect(RectF(x, bottom - bh, x + step * 0.76f, bottom), dp(3).toFloat(), dp(3).toFloat(), paint)
                    if (points.size <= 12 || index % ((points.size + 7) / 8) == 0) {
                        paint.color = muted
                        paint.textSize = dp(10).toFloat()
                        paint.textAlign = Paint.Align.CENTER
                        canvas.drawText(point.first, x + step * 0.38f, h - dp(8), paint)
                    }
                }
            }
        }
        content.addView(chart, LinearLayout.LayoutParams(-1, dp(180)))
        val minutes = points.sumOf { it.second } / 60000L
        content.addView(label(getString(R.string.total_person_hours, minutes / 60, minutes % 60), 15f, true))
    }

    private fun renderParticipantPie(from: Long, to: Long) {
        val c = card()
        c.addView(label(tr(R.string.participant_compare), 20f, true))
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

    private fun renderTimeline(inHistory: Boolean = false) {
        val c = if (inHistory) LinearLayout(this).apply { orientation = LinearLayout.VERTICAL } else card()
        if (!inHistory) {
            c.addView(label(tr(R.string.daily_timeline), 20f, true))
            gap(c, 8)
        }
        val day = dayStart(if (inHistory) dayOffset else timelineOffset)
        if (!inHistory) {
        val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        nav.addView(action("‹", subtleNeutral, ink) { timelineOffset--; renderContent() },
            LinearLayout.LayoutParams(dp(50), dp(44)))
        nav.addView(label(dateFormat.format(Date(day)), 15f, true).apply { gravity = Gravity.CENTER },
            LinearLayout.LayoutParams(0, dp(44), 1f))
        nav.addView(action("›", subtleNeutral, ink) {
            if (timelineOffset < 0) { timelineOffset++; renderContent() }
        }, LinearLayout.LayoutParams(dp(50), dp(44)))
        c.addView(nav)
        }
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
                            val x1 = left + ((item.second - day).toFloat() / (end - day)) * trackWidth
                            val x2 = left + ((item.third - day).toFloat() / (end - day)) * trackWidth
                            paint.color = personColor(person)
                            canvas.drawRoundRect(RectF(x1, y, maxOf(x1 + dp(2), x2), y + dp(21)), dp(3).toFloat(), dp(3).toFloat(), paint)
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
        }
        if (inHistory) {
            content.addView(c, LinearLayout.LayoutParams(-1, -2))
            gap(content, 8)
        } else addCard(c)
    }

    private fun renderStats() {
        content.addView(label(tr(R.string.statistics), 24f, true))
        gap(content, 12)
        val c = card()
        c.addView(label(tr(R.string.time_project), 20f, true))
        gap(c, 12)
        val selector = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf(tr(R.string.day), tr(R.string.week), tr(R.string.month), tr(R.string.all)).forEachIndexed { index, name ->
            selector.addView(action(name, if (statsPeriod == index) blue else subtleNeutral,
                if (statsPeriod == index) Color.WHITE else ink) {
                statsPeriod = index
                statsOffset = 0
                renderContent()
            }, LinearLayout.LayoutParams(0, dp(43), 1f).apply { setMargins(dp(1), 0, dp(1), 0) })
        }
        c.addView(selector)
        gap(c, 8)
        if (statsPeriod != 3) {
            val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            nav.addView(action("‹", subtleNeutral, ink) { statsOffset--; renderContent() },
                LinearLayout.LayoutParams(dp(50), dp(42)))
            val range = periodRange()
            nav.addView(label(dateFormat.format(Date(range.first)), 14f, true).apply { gravity = Gravity.CENTER },
                LinearLayout.LayoutParams(0, dp(42), 1f))
            nav.addView(action("›", subtleNeutral, ink) {
                if (statsOffset < 0) { statsOffset++; renderContent() }
            }, LinearLayout.LayoutParams(dp(50), dp(42)))
            c.addView(nav)
        }
        addCard(c)
        val (from, to) = periodRange()
        if (to > from) {
            barChart(from, to)
            gap(content, 18)
            renderParticipantPie(from, to)
        }
        gap(content, 18)
        renderTimeline()
    }

    private fun renderHistory() {
        content.addView(label(tr(R.string.history), 24f, true))
        gap(content, 10)
        if (!isArchived()) content.addView(action(tr(R.string.add_measurement), blue) { addSession() })
        gap(content, 12)
        val filters = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        (listOf(0) + participants()).forEach { person ->
            val name = if (person == 0) tr(R.string.all_people) else personName(person)
            val button = action(name, if (historyPerson == person) blue else subtleNeutral,
                if (historyPerson == person) Color.WHITE else ink) {
                historyPerson = person
                renderContent()
            }
            filters.addView(button, LinearLayout.LayoutParams(dp(130), dp(46)).apply {
                setMargins(dp(2), 0, dp(2), 0)
            })
        }
        val filterScroll = HorizontalScrollView(this)
        filterScroll.addView(filters)
        content.addView(filterScroll)
        gap(content, 12)
        datePickerRow()
        renderTimeline(inHistory = true)
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
                    db.update("projects", ContentValues().apply { put("name", name) }, "id=?",
                        arrayOf(id.toString()))
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
            content.addView(action(tr(R.string.export_csv), subtleWarm, ink) { exportCsv() })
            return
        }
        content.addView(label(tr(R.string.settings), 24f, true))
        gap(content, 12)
        val project = card()
        project.addView(label(tr(R.string.project), 13f, true, muted))
        gap(project, 8)
        project.addView(label(projectName(), 20f, true))
        gap(project, 8)
        project.addView(action(tr(R.string.rename_project), subtle, secondaryButtonText) {
            renameProject()
        })
        gap(project, 14)
        val iconRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        iconRow.addView(projectIcon(projectId!!, 60))
        iconRow.addView(label(tr(R.string.project_icon), 16f, true).apply { setPadding(dp(14), 0, 0, 0) })
        project.addView(iconRow)
        gap(project, 10)
        project.addView(action(tr(R.string.regenerate_icon), subtle, secondaryButtonText) {
            regenerateProjectIcon()
        })
        addCard(project)

        val people = card()
        people.addView(label(tr(R.string.participants_label) + participants().size, 19f, true))
        gap(people, 10)
        participants().forEach { person ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            row.addView(avatar(person), LinearLayout.LayoutParams(dp(36), dp(36)))
            val name = label(personName(person), 15f, true).apply { setPadding(dp(10), 0, dp(4), 0) }
            row.addView(name, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(iconButton("edit", blue) { rename(person) },
                LinearLayout.LayoutParams(dp(42), dp(42)))
            gap(row, 5)
            row.addView(iconButton("delete", Color.rgb(170, 42, 42)) { removeParticipant(person) },
                LinearLayout.LayoutParams(dp(42), dp(42)))
            people.addView(row)
            gap(people, 7)
        }
        people.addView(action(tr(R.string.add_participant), subtle, secondaryButtonText) {
            addParticipant()
        })
        addCard(people)

        val data = card()
        data.addView(label(tr(R.string.project_data), 19f, true))
        gap(data, 12)
        data.addView(action(tr(R.string.export_csv), subtle, secondaryButtonText) {
            exportCsv()
        })
        gap(data, 8)
        data.addView(action(tr(R.string.archive_project), subtleWarm, Color.rgb(143, 80, 28)) { archiveProject() })
        addCard(data)


    }

    private fun renderContent() {
        timerUpdates.clear()
        content.removeAllViews()
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
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(label("←", 29f, false, ink).apply {
            gravity = Gravity.CENTER
            contentDescription = tr(R.string.back_to_projects_description)
            setOnClickListener { projectId = null; render() }
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        header.addView(projectIcon(projectId!!, 38))
        header.addView(label(projectName(), 21f, true, ink).apply {
            setSingleLine(true)
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(dp(10), 0, dp(5), 0)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val active = db.rawQuery("SELECT COUNT(*) FROM sessions WHERE project_id=? AND ended_at IS NULL", arrayOf(projectId.toString())).use {
            if (it.moveToFirst()) it.getInt(0) else 0
        }
        if (active > 0) {
            header.addView(label("● $active " + tr(R.string.active_plural).trim(), 12f, true, green).apply {
                setPadding(dp(8), dp(7), dp(8), dp(7))
                background = GradientDrawable().apply {
                    setColor(if (darkTheme) Color.rgb(35, 71, 54) else Color.rgb(230, 241, 235))
                    cornerRadius = dp(16).toFloat()
                }
            })
        }
        content.addView(header)
        gap(content, 12)
        when (tab) {
            0 -> renderTimers()
            1 -> renderStats()
            2 -> renderHistory()
            else -> renderSettings()
        }
    }

    private fun render() {
        renderContent()
        navigation.removeAllViews()
        if (projectId == null) { navigation.visibility = View.GONE; return }
        navigation.visibility = View.VISIBLE
        listOf("◷\n" + tr(R.string.timers), "▥\n" + tr(R.string.statistics), "☷\n" + tr(R.string.history), "⚙\n" + tr(R.string.settings)).forEachIndexed { index, name ->
            val item = label(name, 12f, tab == index, if (tab == index) blue else muted).apply {
                gravity = Gravity.CENTER
                setPadding(dp(2), dp(10), dp(2), dp(10))
                setOnClickListener { tab = index; render() }
            }
            navigation.addView(item, LinearLayout.LayoutParams(0, dp(66), 1f))
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        db.close()
        super.onDestroy()
    }
}
