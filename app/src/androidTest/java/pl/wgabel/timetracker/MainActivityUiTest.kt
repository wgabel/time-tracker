package pl.wgabel.timetracker

import android.content.ContentValues
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.os.SystemClock
import android.app.Dialog
import android.widget.SeekBar
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import pl.wgabel.timetracker.data.SQLiteDatabaseService
import pl.wgabel.timetracker.design.AppUi
import pl.wgabel.timetracker.ui.sessions.DateTimeEditor
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

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
        instrumentation.runOnMainSync {
            (context.applicationContext as TimeTrackerApplication).releaseContainer()
        }
        context.deleteDatabase(databaseName)
        context.getSharedPreferences("preferences", 0).edit()
            .putString("app_language", "en").putString("app_theme", "light").commit()
        SQLiteDatabaseService(context).use { service ->
            val db = service.database
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
        instrumentation.runOnMainSync {
            (context.applicationContext as TimeTrackerApplication).releaseContainer()
        }
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
        openCompletedSessionEditor()
        tapDescription("Shortcuts")
        tapText("30 min")
        tapSessionBoundary("time_session_end")
        tapText("15 min")
        tapSessionBoundary("time_session_start")
        // Returning to Start must retain its draft; neither boundary is persisted by a tab change.
        assertPersistedSessionTimes(now - 40 * 60_000L, now - 20 * 60_000L)
        tapSessionBoundary("time_session_end")
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

    @Test fun editOnlyCompletedSessionEndAndCancelFurtherChanges() {
        val now = System.currentTimeMillis()
        val started = now - 40 * 60_000L
        val ended = now - 20 * 60_000L
        database().use { db ->
            db.insertOrThrow("sessions", null, ContentValues().apply {
                put("project_id", 1); put("person", 1)
                put("started_at", started); put("ended_at", ended)
            })
        }
        openCompletedSessionEditor()
        tapSessionBoundary("time_session_end")
        tapDescription("Increase minute")
        takePickerScreenshot("session-end-selected.png")
        assertPersistedSessionTimes(started, ended)
        tapSave()
        assertPersistedSessionTimes(started, ended + 60_000L)

        openCompletedSessionEditor()
        tapSessionBoundary("time_session_end")
        tapDescription("Increase minute")
        tapText("Cancel")
        assertPersistedSessionTimes(started, ended + 60_000L)
    }

    private fun openCompletedSessionEditor() {
        // Reload project so history reads the inserted or updated session.
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
        instrumentation.waitForIdleSync()
    }

    private fun tapSessionBoundary(resource: String) {
        val boundary = device.wait(Until.findObject(By.res(context.packageName, resource)), 8000)
        requireNotNull(boundary) { "Session boundary must be available before saving: $resource" }.click()
        instrumentation.waitForIdleSync()
    }

    private fun assertPersistedSessionTimes(started: Long, ended: Long) {
        database().use { db ->
            db.rawQuery("SELECT started_at,ended_at FROM sessions", null).use {
                assertTrue(it.moveToFirst())
                assertEquals(started, it.getLong(0))
                assertEquals(ended, it.getLong(1))
                assertFalse(it.moveToNext())
            }
        }
    }

    @Test fun timePickerSynchronizesRealSwipesArrowsAndDayScale() {
        val date = LocalDate.of(2026, 11, 12)
        val initial = date.atTime(13, 59, 37, 123_000_000).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        var saved: Long? = null
        val dialog = showTimePicker(DateTimeEditor.Config("Session start", initial, quickOffsets = false)) {
            saved = it; true
        }
        try {
            // Drag only the minute field. Its hour must carry and the rail must follow exactly.
            val minute = requireNotNull(device.findObject(By.res(context.packageName, "time_minute")))
            val bounds = minute.visibleBounds
            val distance = (18 * context.resources.displayMetrics.density * 1.2f).toInt()
            device.swipe(bounds.centerX(), bounds.centerY() + distance / 2,
                bounds.centerX(), bounds.centerY() - distance / 2, 20)
            instrumentation.waitForIdleSync()
            assertPickerTime(dialog, 14, 0)
            val hour = requireNotNull(device.findObject(By.res(context.packageName, "time_hour")))
            val hourBounds = hour.visibleBounds
            device.swipe(hourBounds.centerX(), hourBounds.centerY() + distance / 2,
                hourBounds.centerX(), hourBounds.centerY() - distance / 2, 20)
            instrumentation.waitForIdleSync()
            assertPickerTime(dialog, 15, 0)
            tapDescription("Increase minute")
            assertPickerTime(dialog, 15, 1)

            // Now drag the actual SeekBar, then compare both fields with its final progress.
            val scale = requireNotNull(device.findObject(By.res(context.packageName, "time_scale"))).visibleBounds
            device.swipe(scale.centerX(), scale.centerY(), scale.left + scale.width() / 4, scale.centerY(), 20)
            instrumentation.waitForIdleSync()
            var selectedMinute = -1
            instrumentation.runOnMainSync {
                selectedMinute = dialog.findViewById<SeekBar>(R.id.time_scale).progress
                assertEquals(0, selectedMinute % 5)
            }
            assertPickerTime(dialog, selectedMinute / 60, selectedMinute % 60)
            assertTrue(selectedMinute in 1..700)
            takePickerScreenshot("time-picker-light.png")
            tapSave()
            val result = requireNotNull(saved).let { java.time.Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()) }
            assertEquals(date, result.toLocalDate())
            assertEquals(selectedMinute, result.hour * 60 + result.minute)
            assertEquals(37, result.second)
            assertEquals(123_000_000, result.nano)
        } finally {
            instrumentation.runOnMainSync { dialog.dismiss() }
        }
    }

    @Test fun timePickerKeepsExactSecondsAndRejectsOutOfSessionBounds() {
        val initial = LocalDate.of(2026, 11, 12).atTime(13, 59, 37, 123_000_000)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        var saved: Long? = null
        val dialog = showTimePicker(DateTimeEditor.Config("Event time", initial,
            min = initial, max = initial, seconds = true, quickOffsets = false), dark = true) {
            saved = it; true
        }
        try {
            takePickerScreenshot("time-picker-dark.png")
            tapText("Seconds: 37")
            tapDescription("Increase second")
            instrumentation.runOnMainSync {
                assertFalse(dialog.findViewById<android.widget.Button>(R.id.time_picker_save).isEnabled)
                assertEquals("38", dialog.findViewById<TextView>(R.id.time_second).text.toString())
                assertTrue(dialog.findViewById<TextView>(R.id.time_picker_error).text.isNotBlank())
            }
            assertNull(saved)
            takePickerScreenshot("time-picker-dark-bounds.png")
        } finally {
            instrumentation.runOnMainSync { dialog.dismiss() }
        }
        assertNull("Dismiss must not save an invalid selection", saved)
        val unchanged = showTimePicker(DateTimeEditor.Config("Event time", initial,
            min = initial, max = initial, seconds = true, quickOffsets = false)) { saved = it; true }
        try {
            tapSave()
            assertEquals(initial, saved)
        } finally {
            instrumentation.runOnMainSync { unchanged.dismiss() }
        }
    }

    private fun showTimePicker(config: DateTimeEditor.Config, dark: Boolean = false,
                               onSave: (Long) -> Boolean): Dialog {
        var dialog: Dialog? = null
        instrumentation.runOnMainSync {
            val host = requireNotNull(activity)
            val tokens = (context.applicationContext as TimeTrackerApplication).container.designTokens
            dialog = DateTimeEditor(host, AppUi(host, tokens) { dark }).show(config, onSave)
        }
        instrumentation.waitForIdleSync()
        return requireNotNull(dialog)
    }

    private fun assertPickerTime(dialog: Dialog, hour: Int, minute: Int) {
        instrumentation.runOnMainSync {
            assertEquals(String.format(java.util.Locale.ENGLISH, "%02d", hour),
                dialog.findViewById<TextView>(R.id.time_hour).text.toString())
            assertEquals(String.format(java.util.Locale.ENGLISH, "%02d", minute),
                dialog.findViewById<TextView>(R.id.time_minute).text.toString())
            assertEquals(hour * 60 + minute, dialog.findViewById<SeekBar>(R.id.time_scale).progress)
        }
    }

    private fun takePickerScreenshot(name: String) {
        // AGP pulls this directory before uninstalling the tested APK and clearing its app files.
        val output = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
            ?.let(::File) ?: requireNotNull(context.getExternalFilesDir(null))
        val folder = File(output, "time-picker-screenshots").apply { mkdirs() }
        assertTrue(device.takeScreenshot(File(folder, name)))
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

    @Test fun activityRecreationRestoresSelectedProjectAndDoesNotDuplicateTimer() {
        tapText("▶  START")
        val startedAt = activeStart()
        instrumentation.runOnMainSync { requireNotNull(activity).recreate() }
        assertNotNull(device.wait(Until.findObject(By.text("■  STOP")), 8_000))
        instrumentation.waitForIdleSync()
        instrumentation.runOnMainSync {
            activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .filterIsInstance<MainActivity>().single()
        }
        SystemClock.sleep(2_200) // Existing startup overlay also runs after recreation.
        assertEquals(startedAt, activeStart())
        assertEquals(1, count("sessions"))
        tapText("■  STOP")
        assertEquals(1, count("sessions"))
    }

    private fun activeStart(): Long = database().use { db ->
        db.rawQuery("SELECT started_at FROM sessions WHERE person=1 AND ended_at IS NULL", null).use {
            assertTrue("Expected an active session", it.moveToFirst())
            val start = it.getLong(0)
            assertFalse("Only one active session should exist", it.moveToNext())
            start
        }
    }

    @Test fun runningTimerSurvivesBackgroundAndReturn() {
        tapText("▶  START")
        val startedAt = activeStart()
        device.pressHome()
        assertFalse("App should be in background", device.hasObject(By.text("■  STOP")))
        SystemClock.sleep(1500)
        assertEquals("Backgrounding must preserve the session", startedAt, activeStart())
        device.executeShellCommand("am start -n ${context.packageName}/.MainActivity")
        assertNotNull(device.wait(Until.findObject(By.text("■  STOP")), 8000))
        assertEquals(startedAt, activeStart())
        tapText("■  STOP")
        assertEquals(1, count("sessions"))
    }

    @Test fun notificationOpensAppWithTimerStillRunning() {
        tapText("▶  START")
        val startedAt = activeStart()
        device.pressHome()
        SystemClock.sleep(1200)
        device.openNotification()
        val notification = device.wait(Until.findObject(By.text("UI project")), 8000)
        requireNotNull(notification) { "Expected foreground timer notification" }.click()
        instrumentation.waitForIdleSync()
        assertNotNull(device.wait(Until.findObject(By.text("UI project")), 8000))
        // Notification opens MainActivity at the project list; select our project.
        if (device.hasObject(By.text("▶  START"))) {
            fail("Notification returned to project but lost the running session")
        }
        if (!device.hasObject(By.text("■  STOP"))) {
            tapText("UI project")
        }
        assertNotNull(device.wait(Until.findObject(By.text("■  STOP")), 8000))
        assertEquals(startedAt, activeStart())
        tapText("■  STOP")
        assertEquals(1, count("sessions"))
    }

}
