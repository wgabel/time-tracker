package pl.wgabel.timetracker

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
    private val blue = Color.rgb(34, 112, 237)
    private val green = Color.rgb(48, 158, 80)
    private val ink = Color.rgb(26, 34, 43)
    private val muted = Color.rgb(99, 109, 120)
    private val surface = Color.WHITE
    private val background = Color.rgb(246, 248, 251)
    private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale("pl", "PL"))
    private val clockFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = object : SQLiteOpenHelper(this, "sessions.db", null, 2) {
            override fun onCreate(database: SQLiteDatabase) {
                database.execSQL("CREATE TABLE projects (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL)")
                database.execSQL("CREATE TABLE participants (id INTEGER PRIMARY KEY AUTOINCREMENT, project_id INTEGER NOT NULL, name TEXT NOT NULL)")
                database.execSQL("CREATE TABLE sessions (id INTEGER PRIMARY KEY AUTOINCREMENT, project_id INTEGER NOT NULL, person INTEGER NOT NULL, started_at INTEGER NOT NULL, ended_at INTEGER)")
                database.execSQL("CREATE UNIQUE INDEX one_active_per_person ON sessions(person) WHERE ended_at IS NULL")
            }
            override fun onUpgrade(database: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
                if (oldVersion < 2) {
                    database.execSQL("CREATE TABLE projects (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL)")
                    database.execSQL("CREATE TABLE participants (id INTEGER PRIMARY KEY AUTOINCREMENT, project_id INTEGER NOT NULL, name TEXT NOT NULL)")
                    database.execSQL("INSERT INTO projects(name) VALUES ('Poprzednie pomiary')")
                    database.execSQL("INSERT INTO participants(id,project_id,name) VALUES (1,1,?)", arrayOf(prefs.getString("name_1", "Osoba 1")))
                    database.execSQL("INSERT INTO participants(id,project_id,name) VALUES (2,1,?)", arrayOf(prefs.getString("name_2", "Osoba 2")))
                    database.execSQL("ALTER TABLE sessions ADD COLUMN project_id INTEGER NOT NULL DEFAULT 1")
                }
            }
        }.writableDatabase
        window.statusBarColor = Color.rgb(25, 34, 44)
        window.navigationBarColor = background
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(this@MainActivity.background)
        }
        val heading = TextView(this).apply {
            text = "◷  Dwa Liczniki"
            textSize = 25f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(25, 34, 44))
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
        handler.post(object : Runnable {
            override fun run() {
                if (!isFinishing) {
                    if (tab == 0) renderContent()
                    handler.postDelayed(this, 1000)
                }
            }
        })
    }

    private fun dp(n: Int): Int = (n * resources.displayMetrics.density + .5f).toInt()
    private fun shape(color: Int, radius: Int = 18): GradientDrawable =
        GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }

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
        background = shape(surface)
        elevation = dp(2).toFloat()
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
            background = shape(bg, 12)
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
            return if (c.moveToFirst()) c.getString(0) else "Osoba $person"
        }
    }
    private fun projectName(): String {
        db.rawQuery("SELECT name FROM projects WHERE id=?", arrayOf(projectId.toString())).use { c ->
            return if (c.moveToFirst()) c.getString(0) else "Projekt"
        }
    }
    private fun createProject() {
        val input = EditText(this).apply { hint = "Nazwa projektu"; setSingleLine(true) }
        val holder = LinearLayout(this).apply { setPadding(dp(20), 0, dp(20), 0); addView(input) }
        val dialog = AlertDialog.Builder(this).setTitle("Nowy projekt").setView(holder)
            .setNegativeButton("Anuluj", null).setPositiveButton("Dodaj", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = input.text.toString().trim()
                if (name.isEmpty()) { input.error = "Podaj nazwę"; return@setOnClickListener }
                val id = db.insert("projects", null, ContentValues().apply { put("name", name) })
                if (id > 0) { projectId = id; tab = 0; dayOffset = 0; historyPerson = 0; dialog.dismiss(); render() }
            }
        }
        dialog.show()
    }
    private fun addParticipant() {
        val input = EditText(this).apply { hint = "Imię lub nazwa uczestnika"; setSingleLine(true) }
        val holder = LinearLayout(this).apply { setPadding(dp(20), 0, dp(20), 0); addView(input) }
        val dialog = AlertDialog.Builder(this).setTitle("Dodaj uczestnika").setView(holder)
            .setNegativeButton("Anuluj", null).setPositiveButton("Dodaj", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = input.text.toString().trim()
                if (name.isEmpty()) { input.error = "Podaj nazwę"; return@setOnClickListener }
                db.insert("participants", null, ContentValues().apply {
                    put("project_id", projectId); put("name", name)
                })
                dialog.dismiss()
                renderContent()
            }
        }
        dialog.show()
    }
    private fun renderProjects() {
        content.addView(label("Projekty", 24f, true))
        gap(content, 14)
        val projects = mutableListOf<Pair<Long, String>>()
        db.rawQuery("SELECT id,name FROM projects ORDER BY id DESC", null).use { c ->
            while (c.moveToNext()) projects.add(c.getLong(0) to c.getString(1))
        }
        if (projects.isEmpty()) {
            content.addView(label("Nie masz jeszcze żadnego projektu.", 16f, false, muted))
            gap(content, 18)
        }
        projects.forEach { (id, name) ->
            val item = card()
            item.addView(label("▤   $name    ›", 19f, true))
            item.setOnClickListener {
                projectId = id; tab = 0; dayOffset = 0; historyPerson = 0; render()
            }
            addCard(item)
        }
        content.addView(action("+ Dodaj projekt", blue) { createProject() })
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
        renderContent()
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
            if (dayOffset == 0) "Dzisiaj, " + dateFormat.format(Date(dayStart(0))) else dateFormat.format(Date(dayStart(dayOffset))),
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
        val name = label("●  " + personName(person), 20f, true, personColor(person))
        c.addView(name)
        gap(c, 12)
        c.addView(label(duration(total(person, start, end, now)), 37f, true))
        gap(c, 5)
        c.addView(label("Suma wybranego dnia", 14f, false, muted))
        if (dayOffset == 0) {
            gap(c, 14)
            c.addView(label(if (active == null) "Licznik zatrzymany" else "Trwa: " + duration(now - active), 16f, false, muted))
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
        AlertDialog.Builder(this).setTitle("Wybierz osobę")
            .setItems(people) { _, which ->
                val person = ids[which]
                val now = System.currentTimeMillis()
                pickDateTime(now - 3600000L, "Początek pomiaru") { start ->
                    pickDateTime(now, "Koniec pomiaru") { end ->
                        if (start >= end || end > System.currentTimeMillis()) {
                            Toast.makeText(this, "Koniec musi być później niż początek i nie może być w przyszłości.", Toast.LENGTH_LONG).show()
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
                                Toast.makeText(this, "Dodano pomiar.", Toast.LENGTH_SHORT).show()
                            } else Toast.makeText(this, "Nie udało się dodać pomiaru.", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }.setNegativeButton("Anuluj", null).show()
    }

    private fun deleteSession(id: Long) {
        AlertDialog.Builder(this)
            .setTitle("Usunąć pomiar?")
            .setMessage("Usunięcie wpisu jest nieodwracalne. Zmieni również statystyki.")
            .setNegativeButton("Anuluj", null)
            .setPositiveButton("Usuń") { _, _ ->
                val deleted = db.delete("sessions", "id=? AND ended_at IS NOT NULL", arrayOf(id.toString()))
                if (deleted > 0) {
                    renderContent()
                    Toast.makeText(this, "Pomiar usunięty.", Toast.LENGTH_SHORT).show()
                }
            }.show()
    }

    private fun editSession(id: Long, person: Int, started: Long, ended: Long?) {
        if (ended == null) {
            Toast.makeText(this, "Zatrzymaj licznik przed edycją sesji.", Toast.LENGTH_LONG).show()
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
        val format = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale("pl", "PL"))
        AlertDialog.Builder(this)
            .setTitle("Edytuj sesję: " + personName(person))
            .setMessage("Od: " + format.format(Date(started)) + "\nDo: " + format.format(Date(ended)) +
                "\n\nWybierz nowe daty i godziny rozpoczęcia oraz zakończenia.")
            .setNegativeButton("Anuluj", null)
            .setPositiveButton("Edytuj") { _, _ ->
                chooseDateTime(started, "Nowy początek") { newStart ->
                    chooseDateTime(ended, "Nowy koniec") { newEnd ->
                        if (newEnd <= newStart) {
                            Toast.makeText(this, "Koniec musi być później niż początek.", Toast.LENGTH_LONG).show()
                        } else if (newEnd > System.currentTimeMillis()) {
                            Toast.makeText(this, "Koniec nie może być w przyszłości.", Toast.LENGTH_LONG).show()
                        } else {
                            val values = android.content.ContentValues().apply {
                                put("started_at", newStart)
                                put("ended_at", newEnd)
                            }
                            val updated = db.update("sessions", values, "id=? AND ended_at IS NOT NULL", arrayOf(id.toString()))
                            if (updated == 1) {
                                Toast.makeText(this, "Sesja zaktualizowana.", Toast.LENGTH_SHORT).show()
                                renderContent()
                            } else {
                                Toast.makeText(this, "Nie udało się zaktualizować sesji.", Toast.LENGTH_LONG).show()
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
            contentDescription = "Uczestnik: $name"
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
            background = shape(if (kind == "edit") Color.rgb(232, 239, 253) else Color.rgb(252, 233, 233), 10)
            contentDescription = if (kind == "edit") "Edytuj wpis" else "Usuń wpis"
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
                left.addView(label(clockFormat.format(Date(from)) + " – " +
                    clockFormat.format(Date(until)) + running, 13f, false, muted))
                gap(left, 3)
                left.addView(label(duration(until - from), 16f, true))
                row.addView(left, LinearLayout.LayoutParams(0, -2, 1f))
                if (tab == 2 && originalEnd != null) {
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
        if (count == 0) content.addView(label("Brak sesji w wybranym dniu.", 15f, false, muted))
    }

    private fun renderTimers() {
        datePickerRow()
        val start = dayStart(dayOffset)
        val end = dayStart(dayOffset + 1)
        val now = System.currentTimeMillis()
        content.addView(action("+ Dodaj osobę", Color.rgb(226, 235, 252), blue) { addParticipant() })
        gap(content, 12)
        if (participants().isEmpty()) {
            content.addView(label("Dodaj pierwszą osobę, aby rozpocząć pomiary.", 16f, false, muted))
            gap(content, 12)
        }
        participants().forEach { timerCard(it, start, end, now) }
        content.addView(label("Sesje z wybranego dnia", 21f, true))
        gap(content, 12)
        sessionList(start, end, 8)
        gap(content, 8)
        content.addView(action("Zobacz pełną historię", Color.rgb(226, 235, 252), blue) {
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
                    1 -> SimpleDateFormat("EEE", Locale("pl", "PL")).format(Date(start))
                    2 -> SimpleDateFormat("d", Locale("pl", "PL")).format(Date(start))
                    else -> SimpleDateFormat("MM.yy", Locale("pl", "PL")).format(Date(start))
                }
                points.add(name to intervals(start, finish).sumOf { it.third - it.second })
                count++
            }
        }
        if (points.isEmpty()) {
            content.addView(label("Brak pomiarów w tym okresie.", 14f, false, muted))
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
        content.addView(label("Łącznie: ${minutes / 60} h ${minutes % 60} min (osobogodziny)", 15f, true))
    }

    private fun renderTimeline(inHistory: Boolean = false) {
        val c = card()
        c.addView(label("Oś czasu dnia", 20f, true))
        gap(c, 8)
        if (!inHistory) {
        val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        nav.addView(action("‹", Color.rgb(229, 233, 239), ink) { timelineOffset--; renderContent() },
            LinearLayout.LayoutParams(dp(50), dp(44)))
        val day = dayStart(if (inHistory) dayOffset else timelineOffset)
        nav.addView(label(dateFormat.format(Date(day)), 15f, true).apply { gravity = Gravity.CENTER },
            LinearLayout.LayoutParams(0, dp(44), 1f))
        nav.addView(action("›", Color.rgb(229, 233, 239), ink) {
            if (timelineOffset < 0) { timelineOffset++; renderContent() }
        }, LinearLayout.LayoutParams(dp(50), dp(44)))
        c.addView(nav)
        }
        val people = if (inHistory && historyPerson != 0) participants().filter { it == historyPerson } else participants()
        if (people.isEmpty()) {
            c.addView(label("Dodaj uczestnika, aby zobaczyć oś czasu.", 14f, false, muted))
        } else {
            val end = dayStart(timelineOffset + 1)
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
                    val left = dp(80).toFloat()
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
                                if (ended != null) editSession(chosen.first, person, started, ended)
                                else Toast.makeText(this@MainActivity, "Zatrzymaj licznik przed edycją.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    return true
                }
                private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                override fun onDraw(canvas: Canvas) {
                    super.onDraw(canvas)
                    val left = dp(80).toFloat()
                    val right = width.toFloat() - dp(5)
                    val trackWidth = (right - left).coerceAtLeast(1f)
                    val rowHeight = dp(32).toFloat()
                    people.forEachIndexed { index, person ->
                        val y = dp(7) + index * rowHeight
                        paint.color = ink
                        paint.textSize = dp(11).toFloat()
                        paint.textAlign = Paint.Align.LEFT
                        val name = personName(person)
                        canvas.drawText(if (name.length > 11) name.take(10) + "…" else name, dp(2).toFloat(), y + dp(15), paint)
                        paint.color = Color.rgb(234, 237, 242)
                        canvas.drawRoundRect(RectF(left, y, right, y + dp(21)), dp(3).toFloat(), dp(3).toFloat(), paint)
                        sessions.filter { it.first == person }.forEach { item ->
                            val x1 = left + ((item.second - day).toFloat() / (end - day)) * trackWidth
                            val x2 = left + ((item.third - day).toFloat() / (end - day)) * trackWidth
                            paint.color = personColor(person)
                            canvas.drawRoundRect(RectF(x1, y, maxOf(x1 + dp(2), x2), y + dp(21)), dp(3).toFloat(), dp(3).toFloat(), paint)
                        }
                    }
                    paint.color = muted
                    paint.textSize = dp(10).toFloat()
                    paint.textAlign = Paint.Align.CENTER
                    for (hour in listOf(0, 6, 12, 18, 24)) {
                        val x = left + hour / 24f * trackWidth
                        canvas.drawText(String.format(Locale.ROOT, "%02d", hour), x, height - dp(3).toFloat(), paint)
                    }
                }
            }
            c.addView(timeline, LinearLayout.LayoutParams(-1, dp(32) * people.size + dp(25)))
        }
        addCard(c)
    }

    private fun renderStats() {
        content.addView(label("Statystyki", 24f, true))
        gap(content, 12)
        val c = card()
        c.addView(label("Czas w projekcie", 20f, true))
        gap(c, 12)
        val selector = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("Dzień", "Tydzień", "Miesiąc", "Całość").forEachIndexed { index, name ->
            selector.addView(action(name, if (statsPeriod == index) blue else Color.rgb(229, 233, 239),
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
            nav.addView(action("‹", Color.rgb(229, 233, 239), ink) { statsOffset--; renderContent() },
                LinearLayout.LayoutParams(dp(50), dp(42)))
            val range = periodRange()
            nav.addView(label(dateFormat.format(Date(range.first)), 14f, true).apply { gravity = Gravity.CENTER },
                LinearLayout.LayoutParams(0, dp(42), 1f))
            nav.addView(action("›", Color.rgb(229, 233, 239), ink) {
                if (statsOffset < 0) { statsOffset++; renderContent() }
            }, LinearLayout.LayoutParams(dp(50), dp(42)))
            c.addView(nav)
        }
        addCard(c)
        val (from, to) = periodRange()
        if (to > from) barChart(from, to)
        gap(content, 18)
        renderTimeline()
    }

    private fun renderHistory() {
        content.addView(label("Historia", 24f, true))
        gap(content, 10)
        content.addView(action("+ Dodaj pomiar", blue) { addSession() })
        gap(content, 12)
        val filters = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        (listOf(0) + participants()).forEach { person ->
            val name = if (person == 0) "Wszyscy" else personName(person)
            val button = action(name, if (historyPerson == person) blue else Color.rgb(229, 233, 239),
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
        AlertDialog.Builder(this).setTitle("Nazwa osoby $person")
            .setView(holder)
            .setNegativeButton("Anuluj", null)
            .setPositiveButton("Zapisz") { _, _ ->
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
        AlertDialog.Builder(this).setTitle("Zmień nazwę projektu").setView(input)
            .setNegativeButton("Anuluj", null).setPositiveButton("Zapisz") { _, _ ->
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
                AlertDialog.Builder(this).setTitle("Uczestnik ma pomiary")
                    .setMessage("Nie można usunąć uczestnika z historią. Jego dane muszą pozostać przypisane do pomiarów.")
                    .setPositiveButton("OK", null).show()
                return
            }
        }
        AlertDialog.Builder(this).setTitle("Usunąć uczestnika?")
            .setMessage("Usunąć ${personName(person)} z projektu?")
            .setNegativeButton("Anuluj", null)
            .setPositiveButton("Usuń") { _, _ ->
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
                    writer.write("Uczestnik;Data;Od;Do;Czas (sekundy);Status\n")
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
                                csv(endDate), secs.toString(), if (end == null) "Trwa" else "Zakończony").joinToString(";") + "\n")
                        }
                    }
                }
            } ?: throw IllegalStateException("Nie można otworzyć pliku")
            Toast.makeText(this, "Wyeksportowano pomiary.", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Błąd eksportu: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun renderSettings() {
        content.addView(label("Ustawienia", 24f, true))
        gap(content, 12)
        val project = card()
        project.addView(label("PROJEKT", 13f, true, muted))
        gap(project, 8)
        project.addView(label(projectName(), 20f, true))
        gap(project, 8)
        project.addView(action("✎  Zmień nazwę projektu", Color.rgb(226, 235, 252), blue) {
            renameProject()
        })
        addCard(project)

        val people = card()
        people.addView(label("Uczestnicy · ${participants().size}", 19f, true))
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
        people.addView(action("+ Dodaj uczestnika", Color.rgb(226, 235, 252), blue) {
            addParticipant()
        })
        addCard(people)

        val data = card()
        data.addView(label("Dane projektu", 19f, true))
        gap(data, 12)
        data.addView(action("↓  Eksport pomiarów (CSV)", Color.rgb(226, 235, 252), blue) {
            exportCsv()
        })
        gap(data, 8)
        data.addView(label("Archiwizacja i usuwanie projektów pojawią się w kolejnym etapie.", 13f, false, muted))
        addCard(data)

        val app = card()
        app.addView(label("Aplikacja", 19f, true))
        gap(app, 8)
        val version = try { packageManager.getPackageInfo(packageName, 0).versionName } catch (_: Exception) { "—" }
        app.addView(label("Wersja: $version", 14f, false, muted))
        gap(app, 8)
        app.addView(label("Pomiary są zapisane lokalnie. Eksport CSV nie jest pełną kopią zapasową. Nie odinstalowuj aplikacji przed zabezpieczeniem danych.", 13f, false, muted))
        addCard(app)
    }

    private fun renderContent() {
        content.removeAllViews()
        if (projectId == null) { renderProjects(); return }
        content.addView(action("‹  Projekty  /  " + projectName(), Color.rgb(226, 235, 252), blue) {
            projectId = null; render()
        })
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
        listOf("◷\nLiczniki", "▥\nStatystyki", "☷\nHistoria", "⚙\nUstawienia").forEachIndexed { index, name ->
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
