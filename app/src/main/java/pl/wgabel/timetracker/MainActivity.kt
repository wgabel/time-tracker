package pl.wgabel.timetracker

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Calendar

class MainActivity : Activity() {
    private lateinit var db: SQLiteDatabase
    private lateinit var root: LinearLayout
    private val handler = Handler(Looper.getMainLooper())
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = object : SQLiteOpenHelper(this, "sessions.db", null, 1) {
            override fun onCreate(database: SQLiteDatabase) {
                database.execSQL("CREATE TABLE sessions (id INTEGER PRIMARY KEY AUTOINCREMENT, person INTEGER NOT NULL, started_at INTEGER NOT NULL, ended_at INTEGER)")
                database.execSQL("CREATE UNIQUE INDEX one_active_per_person ON sessions(person) WHERE ended_at IS NULL")
            }
            override fun onUpgrade(database: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}
        }.writableDatabase
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
            setBackgroundColor(Color.rgb(250,250,250))
        }
        val scroll = ScrollView(this)
        scroll.addView(root)
        setContentView(scroll)
        refresh()
        handler.post(object : Runnable {
            override fun run() {
                if (!isFinishing) { refresh(); handler.postDelayed(this, 1000) }
            }
        })
    }

    private fun text(value: String, size: Float = 18f): TextView = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(Color.rgb(30,30,30))
        setPadding(0, 12, 0, 12)
    }

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
                db.execSQL("INSERT INTO sessions(person,started_at) VALUES (?,?)", arrayOf(person,now))
            } else {
                db.execSQL("UPDATE sessions SET ended_at=? WHERE person=? AND ended_at IS NULL", arrayOf(now,person))
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        refresh()
    }

    private fun duration(ms: Long): String {
        val s = (ms.coerceAtLeast(0) / 1000)
        return "%02d:%02d:%02d".format(Locale.ROOT, s / 3600, (s / 60) % 60, s % 60)
    }

    private fun startOfDay(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY,0); set(Calendar.MINUTE,0); set(Calendar.SECOND,0); set(Calendar.MILLISECOND,0)
    }.timeInMillis

    private fun refresh() {
        val now = System.currentTimeMillis()
        val today = startOfDay()
        root.removeAllViews()
        root.addView(text("Dwa Liczniki", 28f))
        root.addView(text("Dzisiaj: " + dateFormat.format(Date(now)), 15f))
        for (person in 1..2) {
            val active = activeStart(person)
            var sum = 0L
            db.rawQuery("SELECT started_at,ended_at FROM sessions WHERE person=? AND started_at<? AND (ended_at IS NULL OR ended_at>?)", arrayOf(person.toString(), now.toString(), today.toString())).use { cursor ->
                while (cursor.moveToNext()) {
                    val start = cursor.getLong(0).coerceAtLeast(today)
                    val end = if (cursor.isNull(1)) now else cursor.getLong(1).coerceAtMost(now)
                    sum += (end-start).coerceAtLeast(0)
                }
            }
            root.addView(text("Osoba $person", 22f))
            root.addView(text("Suma dzisiaj: " + duration(sum), 20f))
            if (active != null) root.addView(text("Trwa: " + duration(now-active), 16f))
            val button = Button(this).apply {
                text = if (active == null) "START" else "STOP"
                setOnClickListener { toggle(person) }
            }
            root.addView(button, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        root.addView(text("Dzisiejsze sesje", 22f))
        db.rawQuery("SELECT person,started_at,ended_at FROM sessions WHERE started_at<? AND (ended_at IS NULL OR ended_at>?) ORDER BY started_at DESC", arrayOf(now.toString(),today.toString())).use { cursor ->
            while (cursor.moveToNext()) {
                val person = cursor.getInt(0)
                val start = cursor.getLong(1)
                val end = if (cursor.isNull(2)) now else cursor.getLong(2)
                val visibleStart = start.coerceAtLeast(today)
                val visibleEnd = end.coerceAtMost(now)
                val suffix = if (cursor.isNull(2)) " (trwa)" else ""
                root.addView(text("Osoba $person: ${timeFormat.format(Date(visibleStart))} – ${timeFormat.format(Date(visibleEnd))} | ${duration(visibleEnd-visibleStart)}$suffix", 15f))
            }
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        db.close()
        super.onDestroy()
    }
}
