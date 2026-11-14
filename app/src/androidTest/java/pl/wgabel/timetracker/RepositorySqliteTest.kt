package pl.wgabel.timetracker

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Real SQLite integration tests. Run on an Android device or emulator. */
@RunWith(AndroidJUnit4::class)
class RepositorySqliteTest {
    private lateinit var db: SQLiteDatabase
    private lateinit var sessions: SessionRepository
    private lateinit var events: EventRepository

    @Before fun setUp() {
        db = SQLiteDatabase.create(null)
        db.execSQL("CREATE TABLE sessions (id INTEGER PRIMARY KEY AUTOINCREMENT, project_id INTEGER NOT NULL, person INTEGER NOT NULL, started_at INTEGER NOT NULL, ended_at INTEGER)")
        db.execSQL("CREATE UNIQUE INDEX one_active_per_person ON sessions(person) WHERE ended_at IS NULL")
        db.execSQL("CREATE TABLE events (id INTEGER PRIMARY KEY AUTOINCREMENT, project_id INTEGER NOT NULL, session_id INTEGER NOT NULL, person INTEGER NOT NULL, happened_at INTEGER NOT NULL, type TEXT NOT NULL, note TEXT NOT NULL)")
        sessions = SessionRepository(db)
        events = EventRepository(db, sessions)
    }

    @After fun tearDown() { db.close() }

    private fun sessionId(): Long =
        db.rawQuery("SELECT id FROM sessions ORDER BY id DESC LIMIT 1", null).use {
            assertTrue(it.moveToFirst())
            it.getLong(0)
        }

    private fun eventCount(): Int =
        db.rawQuery("SELECT COUNT(*) FROM events", null).use {
            assertTrue(it.moveToFirst())
            it.getInt(0)
        }

    @Test fun overlappingSessionIsRejectedWithoutWriting() {
        assertTrue(sessions.insert(1, 1, 100, 200))
        assertFalse(sessions.insert(1, 1, 199, 250))
        assertTrue(sessions.insert(1, 1, 200, 250))
    }

    @Test fun differentParticipantsMayOverlap() {
        assertTrue(sessions.insert(1, 1, 100, 200))
        assertTrue(sessions.insert(1, 2, 100, 200))
    }

    @Test fun eventOutsideSessionIsRejected() {
        assertTrue(sessions.insert(1, 1, 100, 200))
        val id = sessionId()
        assertFalse(events.save(1, id, 1, null, 201, "Inne", "outside"))
        assertEquals(0, eventCount())
    }

    @Test fun eventOnSessionBoundaryIsAllowed() {
        assertTrue(sessions.insert(1, 1, 100, 200))
        val id = sessionId()
        assertTrue(events.save(1, id, 1, null, 100, "Inne", "start"))
        assertTrue(events.save(1, id, 1, null, 200, "Inne", "end"))
        assertEquals(2, eventCount())
    }

    @Test fun shorteningSessionCannotOrphanEvent() {
        assertTrue(sessions.insert(1, 1, 100, 200))
        val id = sessionId()
        assertTrue(events.save(1, id, 1, null, 190, "Inne", "keep"))
        assertFalse(sessions.update(1, 1, id, 100, 180))
        assertTrue(sessions.eventWithinSession(1, id, 1, 190))
    }

    @Test fun eventCannotBeWrittenForDifferentParticipant() {
        assertTrue(sessions.insert(1, 1, 100, 200))
        assertFalse(events.save(1, sessionId(), 2, null, 150, "Inne", "wrong owner"))
        assertEquals(0, eventCount())
    }

    @Test fun editingEventKeepsRowCount() {
        assertTrue(sessions.insert(1, 1, 100, 200))
        val id = sessionId()
        assertTrue(events.save(1, id, 1, null, 150, "Inne", "original"))
        val eventId = db.rawQuery("SELECT id FROM events", null).use {
            assertTrue(it.moveToFirst())
            it.getLong(0)
        }
        assertTrue(events.save(1, id, 1, eventId, 160, "Spacer", "edited"))
        assertEquals(1, eventCount())
        db.rawQuery("SELECT happened_at, type, note FROM events WHERE id=?", arrayOf(eventId.toString())).use {
            assertTrue(it.moveToFirst())
            assertEquals(160L, it.getLong(0))
            assertEquals("Spacer", it.getString(1))
            assertEquals("edited", it.getString(2))
        }
    }
}
