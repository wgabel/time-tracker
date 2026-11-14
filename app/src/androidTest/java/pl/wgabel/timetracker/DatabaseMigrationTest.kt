package pl.wgabel.timetracker

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import pl.wgabel.timetracker.data.SQLiteDatabaseService
import pl.wgabel.timetracker.design.ParticipantColors

/** Exercises the actual SQLiteOpenHelper, including files created by every supported legacy schema. */
@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun freshDatabaseHasVersionSixAndTheActiveTimerConstraint() {
        IsolatedDatabaseContext(context).use { isolated ->
            SQLiteDatabaseService(isolated).use { service ->
                val database = service.database
                assertEquals(6, database.version)
                val tables = database.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null).use { cursor ->
                    buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) }
                }
                assertTrue(tables.containsAll(listOf("projects", "participants", "sessions", "events")))
                database.execSQL("INSERT INTO sessions(project_id,person,started_at) VALUES(1,1,100)")
                assertActiveSessionUniqueness(database)
                assertDatabaseIntegrity(database)
            }
        }
    }

    @Test fun upgradesFromEverySupportedVersionPreserveExistingRowsAndCanBeReopened() {
        for (version in 1..6) {
            IsolatedDatabaseContext(context).use { isolated ->
                isolated.getSharedPreferences("preferences", Context.MODE_PRIVATE).edit()
                    .putString("name_1", "Legacy first").putString("name_2", "Legacy second").commit()
                createLegacyDatabase(isolated, version)
                SQLiteDatabaseService(isolated).use { service -> assertPreservedData(service.database, version) }
                // Reopening must not repeat migrations, reset colors, or rewrite active sessions/events.
                SQLiteDatabaseService(isolated).use { service -> assertPreservedData(service.database, version) }
            }
        }
    }

    private fun createLegacyDatabase(context: Context, version: Int) {
        context.openOrCreateDatabase("sessions.db", Context.MODE_PRIVATE, null).use { database ->
            val projectColumn = if (version >= 2) ", project_id INTEGER NOT NULL" else ""
            database.execSQL("CREATE TABLE sessions(id INTEGER PRIMARY KEY AUTOINCREMENT, person INTEGER NOT NULL, started_at INTEGER NOT NULL, ended_at INTEGER$projectColumn)")
            database.execSQL("CREATE UNIQUE INDEX one_active_per_person ON sessions(person) WHERE ended_at IS NULL")
            if (version >= 2) {
                val archiveColumn = if (version >= 3) ", archived INTEGER NOT NULL DEFAULT 0" else ""
                val iconColumn = if (version >= 4) ", icon_variant INTEGER NOT NULL DEFAULT 0" else ""
                database.execSQL("CREATE TABLE projects(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL$archiveColumn$iconColumn)")
                val colorColumn = if (version >= 6) ", color INTEGER NOT NULL DEFAULT 0" else ""
                database.execSQL("CREATE TABLE participants(id INTEGER PRIMARY KEY AUTOINCREMENT, project_id INTEGER NOT NULL, name TEXT NOT NULL$colorColumn)")
                database.execSQL("INSERT INTO projects(id,name) VALUES(1,'Existing project')")
                database.execSQL("INSERT INTO participants(id,project_id,name) VALUES(1,1,'Legacy first'),(2,1,'Legacy second')")
                if (version >= 3) database.execSQL("UPDATE projects SET archived=1 WHERE id=1")
                if (version >= 4) database.execSQL("UPDATE projects SET icon_variant=7 WHERE id=1")
                if (version >= 6) database.execSQL("UPDATE participants SET color=? WHERE id=1", arrayOf(0xff123456.toInt()))
            }
            val projectInsertColumn = if (version >= 2) ",project_id" else ""
            val projectInsertValue = if (version >= 2) ",1" else ""
            database.execSQL("INSERT INTO sessions(id,person,started_at,ended_at$projectInsertColumn) VALUES(77,1,100,NULL$projectInsertValue),(88,2,50,90$projectInsertValue)")
            if (version >= 5) {
                database.execSQL("CREATE TABLE events(id INTEGER PRIMARY KEY AUTOINCREMENT,project_id INTEGER NOT NULL,session_id INTEGER NOT NULL,person INTEGER NOT NULL,happened_at INTEGER NOT NULL,type TEXT NOT NULL,note TEXT NOT NULL)")
                database.execSQL("INSERT INTO events(id,project_id,session_id,person,happened_at,type,note) VALUES(99,1,88,2,70,?,?)",
                    arrayOf("Inne", "Existing note: żółć; \"quote\""))
            }
            database.version = version
        }
    }

    private fun assertPreservedData(database: SQLiteDatabase, originalVersion: Int) {
        assertEquals("Schema from v$originalVersion", 6, database.version)
        database.rawQuery("SELECT id,person,started_at,ended_at,project_id FROM sessions ORDER BY id", null).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(77L, cursor.getLong(0)); assertEquals(1, cursor.getInt(1))
            assertEquals(100L, cursor.getLong(2)); assertTrue(cursor.isNull(3)); assertEquals(1L, cursor.getLong(4))
            assertTrue(cursor.moveToNext())
            assertEquals(88L, cursor.getLong(0)); assertEquals(2, cursor.getInt(1))
            assertEquals(50L, cursor.getLong(2)); assertEquals(90L, cursor.getLong(3)); assertEquals(1L, cursor.getLong(4))
            assertFalse(cursor.moveToNext())
        }
        database.rawQuery("SELECT id,name,color FROM participants ORDER BY id", null).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0)); assertEquals("Legacy first", cursor.getString(1))
            assertEquals(if (originalVersion >= 6) 0xff123456.toInt() else ParticipantColors.legacyColor(1), cursor.getInt(2))
            assertTrue(cursor.moveToNext())
            assertEquals(2, cursor.getInt(0)); assertEquals("Legacy second", cursor.getString(1))
            assertEquals(if (originalVersion >= 6) 0 else ParticipantColors.legacyColor(2), cursor.getInt(2))
            assertFalse(cursor.moveToNext())
        }
        database.rawQuery("SELECT archived,icon_variant FROM projects WHERE id=1", null).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(if (originalVersion >= 3) 1 else 0, cursor.getInt(0))
            assertEquals(if (originalVersion >= 4) 7L else 0L, cursor.getLong(1))
        }
        database.rawQuery("SELECT id,session_id,person,happened_at,type,note FROM events", null).use { cursor ->
            if (originalVersion >= 5) {
                assertTrue(cursor.moveToFirst())
                assertEquals(99L, cursor.getLong(0)); assertEquals(88L, cursor.getLong(1)); assertEquals(2, cursor.getInt(2))
                assertEquals(70L, cursor.getLong(3)); assertEquals("Inne", cursor.getString(4))
                assertEquals("Existing note: żółć; \"quote\"", cursor.getString(5))
            }
            assertFalse(cursor.moveToNext())
        }
        assertActiveSessionUniqueness(database)
        assertDatabaseIntegrity(database)
    }

    private fun assertActiveSessionUniqueness(database: SQLiteDatabase) {
        try {
            database.execSQL("INSERT INTO sessions(project_id,person,started_at) VALUES(1,1,200)")
            fail("The active-session unique index must survive creation/upgrades")
        } catch (_: SQLiteConstraintException) { /* Expected; original active row remains unchanged. */ }
    }

    private fun assertDatabaseIntegrity(database: SQLiteDatabase) {
        database.rawQuery("PRAGMA integrity_check", null).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("ok", cursor.getString(0))
        }
    }
}
