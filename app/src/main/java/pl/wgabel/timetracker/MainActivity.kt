package pl.wgabel.timetracker

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.graphics.Color
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
    private var dayOffset = 0
    private var statsPeriod = 0
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
        db = object : SQLiteOpenHelper(this, "sessions.db", null, 1) {
            override fun onCreate(database: SQLiteDatabase) {
                database.execSQL("CREATE TABLE sessions (id INTEGER PRIMARY KEY AUTOINCREMENT, person INTEGER NOT NULL, started_at INTEGER NOT NULL, ended_at INTEGER)")
                database.execSQL("CREATE UNIQUE INDEX one_active_per_person ON sessions(person) WHERE ended_at IS NULL")
            }
            override fun onUpgrade(database: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}
        }.writableDatabase
        window.statusBarColor = Color.rgb(25, 34, 44)
        window.navigationBarColor = background
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(background)
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
        val scroll = ScrollView(this).apply { fillViewport = true }
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

    private fun personName(person: Int): String = prefs.getString("name_$person", "Osoba $person") ?: "Osoba $person"
    private fun personColor(person: Int) = if (person == 1) blue else green

    private fun activeStart(person: Int): Long? {
        db.rawQuery("SELECT started_at FROM sessions WHERE person=? AND ended_at IS NULL LIMIT 1", arrayOf(person.toString())).use {
            return if (it.moveToFirst()) it.getLong(0) else null
        }
    }

    private fun toggle(person: Int) {
        val now = System.currentTimeMillis()
        db.beginTransaction()
        try {
            if (activeStart(person) == null) {
                db.execSQL("INSERT INTO sessions(person,started_at) VALUES (?,?)", arrayOf(person, now))
            } else {
                db.execSQL("UPDATE sessions SET ended_at=? WHERE person=? AND ended_at IS NULL", arrayOf(now, person))
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
            "SELECT started_at,ended_at FROM sessions WHERE person=? AND started_at<? AND (ended_at IS NULL OR ended_at>?)",
            arrayOf(person.toString(), end.toString(), start.toString())
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

    private fun sessionList(start: Long, end: Long, limit: Int = 100) {
        var count = 0
        db.rawQuery(
            "SELECT person,started_at,ended_at FROM sessions WHERE started_at<? AND (ended_at IS NULL OR ended_at>?) ORDER BY started_at DESC LIMIT $limit",
            arrayOf(end.toString(), start.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val person = cursor.getInt(0)
                val from = maxOf(start, cursor.getLong(1))
                val until = minOf(end, if (cursor.isNull(2)) System.currentTimeMillis() else cursor.getLong(2))
                val row = card()
                row.addView(label("●  " + personName(person), 15f, true, personColor(person)))
                gap(row, 6)
                val running = if (cursor.isNull(2)) "  • trwa" else ""
                row.addView(label(clockFormat.format(Date(from)) + " – " + clockFormat.format(Date(until)) + running, 14f, false, muted))
                gap(row, 5)
                row.addView(label(duration(until - from), 19f, true))
                addCard(row)
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
        timerCard(1, start, end, now)
        timerCard(2, start, end, now)
        content.addView(label("Sesje z wybranego dnia", 21f, true))
        gap(content, 12)
        sessionList(start, end, 8)
        gap(content, 8)
        content.addView(action("Zobacz pełną historię", Color.rgb(226, 235, 252), blue) {
            tab = 2
            render()
        })
    }

    private fun renderStats() {
        content.addView(label("Statystyki", 24f, true))
        gap(content, 14)
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val periods = listOf("Dzień", "Tydzień", "Miesiąc", "Rok")
        periods.forEachIndexed { index, title ->
            val button = action(title, if (statsPeriod == index) blue else Color.rgb(229, 233, 239),
                if (statsPeriod == index) Color.WHITE else ink) {
                statsPeriod = index
                renderContent()
            }
            val params = LinearLayout.LayoutParams(0, dp(48), 1f)
            params.setMargins(dp(2), 0, dp(2), 0)
            row.addView(button, params)
        }
        content.addView(row)
        gap(content, 16)
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        when (statsPeriod) {
            1 -> { cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek) }
            2 -> { cal.set(Calendar.DAY_OF_MONTH, 1) }
            3 -> { cal.set(Calendar.DAY_OF_YEAR, 1) }
        }
        val from = cal.timeInMillis
        val to = now
        val a = total(1, from, to, now)
        val b = total(2, from, to, now)
        val card = card()
        card.addView(label("●  " + personName(1), 17f, true, blue))
        gap(card, 5)
        card.addView(label(duration(a), 31f, true))
        gap(card, 16)
        card.addView(label("●  " + personName(2), 17f, true, green))
        gap(card, 5)
        card.addView(label(duration(b), 31f, true))
        gap(card, 18)
        card.addView(label("Łącznie", 17f, false, muted))
        card.addView(label(duration(a + b), 32f, true))
        addCard(card)
        content.addView(label("Okres: " + dateFormat.format(Date(from)) + " – " + dateFormat.format(Date(to)), 14f, false, muted))
        gap(content, 20)
        content.addView(label("Wykresy i porównania dodamy w kolejnej wersji.", 14f, false, muted))
    }

    private fun renderHistory() {
        content.addView(label("Historia", 24f, true))
        gap(content, 10)
        datePickerRow()
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
                    prefs.edit().putString("name_$person", name).apply()
                    renderContent()
                }
            }.show()
    }

    private fun renderSettings() {
        content.addView(label("Ustawienia", 24f, true))
        gap(content, 16)
        val c = card()
        c.addView(label("Nazwy liczników", 19f, true))
        gap(c, 12)
        for (person in 1..2) {
            c.addView(action("Zmień: " + personName(person), Color.rgb(226, 235, 252), blue) { rename(person) })
            gap(c, 10)
        }
        addCard(c)
        content.addView(label("Dane sesji są przechowywane lokalnie na tym telefonie. Nie odinstalowuj aplikacji bez kopii zapasowej.", 14f, false, muted))
    }

    private fun renderContent() {
        content.removeAllViews()
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
