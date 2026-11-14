package pl.wgabel.timetracker

import android.content.ContentValues
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real UI smoke tests: tap the same controls as a user, then verify persisted data.
 * Each test starts with its own isolated project and SQLite database.
 */
@RunWith(AndroidJUnit4::class)
class MainActivityUiTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val databaseName = "sessions.db"
    private var activity: android.app.Activity? = null

    @Before fun setUp() {
        context.stopService(Intent(context, TimerNotificationService::class.java))
        context.deleteDatabase(databaseName)
        context.getSharedPreferences("preferences", 0).edit()
            .putString("app_language", "en").putString("app_theme", "light").commit()
        database().use { db ->
            db.execSQL("CREATE TABLE projects (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, archived INTEGER NOT NULL DEFAULT 0, icon_variant INTEGER NOT NULL DEFAULT 0)")
            db.execSQL("CREATE TABLE participants (id INTEGER PRIMARY KEY AUTOINCREMENT, project_id INTEGER NOT NULL, name TEXT NOT NULL, color INTEGER NOT NULL DEFAULT 0)")
            db.execSQL("CREATE TABLE sessions (id INTEGER PRIMARY KEY AUTOINCREMENT, project_id INTEGER NOT NULL, person INTEGER NOT NULL, started_at INTEGER NOT NULL, ended_at INTEGER)")
            db.execSQL("CREATE UNIQUE INDEX one_active_per_person ON sessions(person) WHERE ended_at IS NULL")
            db.execSQL("CREATE TABLE events (id INTEGER PRIMARY KEY AUTOINCREMENT, project_id INTEGER NOT NULL, session_id INTEGER NOT NULL, person INTEGER NOT NULL, happened_at INTEGER NOT NULL, type TEXT NOT NULL, note TEXT NOT NULL)")
            db.version = 6 // Match the app SQLiteOpenHelper schema version.
            db.insertOrThrow("projects", null, ContentValues().apply {
                put("id", 1); put("name", "UI project")
            })
            db.insertOrThrow("participants", null, ContentValues().apply {
                put("id", 1); put("project_id", 1); put("name", "UI participant"); put("color", 0xff228866.toInt())
            })
        }
        // Avoid Android 13's notification permission prompt obscuring the UI.
        device.executeShellCommand("pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS")
        activity = instrumentation.startActivitySync(
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        instrumentation.waitForIdleSync()
        // The splash overlay intentionally blocks taps during its 2-second animation.
        SystemClock.sleep(2200)
        tapText("UI project")
    }

    @After fun tearDown() {
        activity?.let { current ->
            instrumentation.runOnMainSync { current.finish() }
        }
        instrumentation.waitForIdleSync()
        context.stopService(Intent(context, TimerNotificationService::class.java))
        context.deleteDatabase(databaseName)
    }

    private fun database(): SQLiteDatabase =
        context.openOrCreateDatabase(databaseName, 0, null)

    private fun findText(value: String): UiObject2 =
        requireNotNull(device.wait(Until.findObject(By.text(value)), 8000)) {
            "Could not find visible text: $value"
        }

    private fun tapText(value: String) {
        findText(value).click()
        instrumentation.waitForIdleSync()
    }

    private fun tapSave() {
        val button = device.wait(Until.findObject(By.text(java.util.regex.Pattern.compile("(?i)save|zapisz"))), 8000)
        requireNotNull(button) { "Could not find Save button in dialog" }.click()
        instrumentation.waitForIdleSync()
    }

    private fun tapDescription(value: String) {
        val target = device.wait(Until.findObject(By.descContains(value)), 8000)
        requireNotNull(target) { "Could not find accessible control: $value" }
        target.click()
        instrumentation.waitForIdleSync()
    }

    private fun count(table: String): Int = database().use { db ->
        db.rawQuery("SELECT COUNT(*) FROM $table", null).use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getInt(0)
        }
    }

    @Test fun startAndStopTimerFromParticipantCard() {
        tapText("▶  START")
        assertNotNull(device.wait(Until.findObject(By.text("■  STOP")), 8000))
        database().use { db ->
            db.rawQuery("SELECT started_at, ended_at FROM sessions WHERE person=1", null).use {
                assertTrue(it.moveToFirst())
                assertTrue(it.getLong(0) > 0)
                assertTrue(it.isNull(1))
            }
        }
        tapText("■  STOP")
        database().use { db ->
            db.rawQuery("SELECT started_at, ended_at FROM sessions WHERE person=1", null).use {
                assertTrue(it.moveToFirst())
                assertTrue(it.getLong(1) >= it.getLong(0))
            }
        }
        assertEquals(1, count("sessions"))
    }

    @Test fun addEventThroughRunningTimerForm() {
        tapText("▶  START")
        // A short delay ensures the event time is after the session start.
        SystemClock.sleep(1000)
        tapText("EVENT")
        assertNotNull(device.wait(Until.findObject(By.text("Add event")), 8000)
            ?: device.wait(Until.findObject(By.text("Dodaj wydarzenie")), 1000))
        val note = device.wait(Until.findObject(By.clazz("android.widget.EditText")), 8000)
        requireNotNull(note).text = "UI event note"
        tapSave()
        assertEquals(1, count("events"))
        database().use { db ->
            db.rawQuery("SELECT note,session_id FROM events", null).use {
                assertTrue(it.moveToFirst())
                assertEquals("UI event note", it.getString(0))
                assertTrue(it.getLong(1) > 0)
            }
        }
    }

    @Test fun editCompletedSessionFromHistory() {
        val now = System.currentTimeMillis()
        database().use { db ->
            db.insertOrThrow("sessions", null, ContentValues().apply {
                put("project_id", 1); put("person", 1)
                put("started_at", now - 40 * 60_000L)
                put("ended_at", now - 20 * 60_000L)
            })
        }
        // Reload project so the history screen reads the inserted session.
        tapDescription("History")
        var row = device.findObject(By.descContains("Dotknij"))
        repeat(5) {
            if (row == null) {
                device.swipe(device.displayWidth / 2, device.displayHeight * 3 / 4,
                    device.displayWidth / 2, device.displayHeight / 3, 20)
                row = device.findObject(By.descContains("Dotknij"))
            }
        }
        requireNotNull(row) { "History session row was not found" }.click()
        val edit = device.wait(Until.findObject(By.desc("Edit entry")), 8000)
        requireNotNull(edit) { "Edit action was not exposed after expanding session" }.click()
        tapText("30 min")
        tapSave()
        tapText("15 min")
        tapSave()
        database().use { db ->
            db.rawQuery("SELECT started_at,ended_at FROM sessions", null).use {
                assertTrue(it.moveToFirst())
                val start = it.getLong(0)
                val end = it.getLong(1)
                assertTrue("Start must have changed", start > now - 35 * 60_000L)
                assertTrue("End must have changed", end > now - 18 * 60_000L)
                assertTrue(end > start)
            }
        }
    }
    @Test fun runningTimerSurvivesActivityRestart() {
        tapText("▶  START")
        val originalStart = database().use { db ->
            db.rawQuery("SELECT started_at FROM sessions WHERE person=1 AND ended_at IS NULL", null).use {
                assertTrue(it.moveToFirst())
                it.getLong(0)
            }
        }
        activity?.let { old ->
            instrumentation.runOnMainSync { old.finish() }
        }
        instrumentation.waitForIdleSync()
        activity = instrumentation.startActivitySync(
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        instrumentation.waitForIdleSync()
        SystemClock.sleep(2200)
        tapText("UI project")
        assertNotNull(device.wait(Until.findObject(By.text("■  STOP")), 8000))
        database().use { db ->
            db.rawQuery("SELECT started_at, ended_at FROM sessions WHERE person=1", null).use {
                assertTrue(it.moveToFirst())
                assertEquals("Restart must not reset the timer", originalStart, it.getLong(0))
                assertTrue("Timer must still be running", it.isNull(1))
                assertFalse("Restart must not duplicate sessions", it.moveToNext())
            }
        }
        tapText("■  STOP")
        database().use { db ->
            db.rawQuery("SELECT started_at, ended_at FROM sessions WHERE person=1", null).use {
                assertTrue(it.moveToFirst())
                assertEquals(originalStart, it.getLong(0))
                assertTrue(it.getLong(1) >= originalStart)
                assertFalse(it.moveToNext())
            }
        }
    }

    @Test fun runningTimerSurvivesSwitchingTabs() {
        tapText("▶  START")
        val originalStart = database().use { db ->
            db.rawQuery("SELECT started_at FROM sessions WHERE person=1 AND ended_at IS NULL", null).use {
                assertTrue(it.moveToFirst())
                it.getLong(0)
            }
        }
        tapDescription("Statistics")
        assertNotNull(device.wait(Until.findObject(By.descContains("History")), 8000))
        tapDescription("History")
        assertNotNull(device.wait(Until.findObject(By.descContains("Timers")), 8000))
        tapDescription("Timers")
        assertNotNull(device.wait(Until.findObject(By.text("■  STOP")), 8000))
        database().use { db ->
            db.rawQuery("SELECT started_at, ended_at FROM sessions WHERE person=1", null).use {
                assertTrue(it.moveToFirst())
                assertEquals(originalStart, it.getLong(0))
                assertTrue("Changing tabs must not stop the timer", it.isNull(1))
                assertFalse("Changing tabs must not create sessions", it.moveToNext())
            }
        }
        tapText("■  STOP")
        assertEquals(1, count("sessions"))
    }

}
