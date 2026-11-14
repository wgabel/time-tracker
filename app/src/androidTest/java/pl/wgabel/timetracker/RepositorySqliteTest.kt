package pl.wgabel.timetracker

import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import pl.wgabel.timetracker.data.EventRepository
import pl.wgabel.timetracker.data.SessionRepository
import pl.wgabel.timetracker.data.SQLiteDatabaseService
import pl.wgabel.timetracker.data.ProjectRepository
import pl.wgabel.timetracker.data.ParticipantRepository
import pl.wgabel.timetracker.domain.SessionService
import pl.wgabel.timetracker.domain.EventService
import pl.wgabel.timetracker.features.timers.TimersController
import pl.wgabel.timetracker.domain.StatisticsCalculator

/** Real SQLite integration tests. Run on an Android device or emulator. */
@RunWith(AndroidJUnit4::class)
class RepositorySqliteTest {
    private lateinit var db: SQLiteDatabase
    private lateinit var sessions: SessionRepository
    private lateinit var events: EventRepository
    private lateinit var isolatedContext: IsolatedDatabaseContext
    private lateinit var databaseService: SQLiteDatabaseService
    private lateinit var projects: ProjectRepository
    private lateinit var participants: ParticipantRepository
    private lateinit var sessionService: SessionService
    private lateinit var eventService: EventService
    private var sessionChangeCount = 0

    @Before fun setUp() {
        isolatedContext = IsolatedDatabaseContext(InstrumentationRegistry.getInstrumentation().targetContext)
        databaseService = SQLiteDatabaseService(isolatedContext)
        db = databaseService.database
        sessions = SessionRepository(db)
        events = EventRepository(db, sessions)
        projects = ProjectRepository(db)
        participants = ParticipantRepository(db)
        assertEquals(1L, projects.create("First project"))
        assertEquals(2L, projects.create("Second project"))
        assertTrue(participants.createParticipant(1, "First person", 0xff228866.toInt()))
        assertTrue(participants.createParticipant(1, "Second person", 0xff6644aa.toInt()))
        assertTrue(participants.createParticipant(2, "Other project person", 0xffbb8822.toInt()))
        sessionService = SessionService(sessions, projects, participants) { sessionChangeCount++ }
        eventService = EventService(events, projects)
    }

    @After fun tearDown() {
        databaseService.close()
        isolatedContext.close()
    }

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

    @Test fun completedSessionDeletionRemovesOnlyItsOwnEvents() {
        assertTrue(sessions.insert(1, 1, 100, 200))
        val first = sessionId()
        assertTrue(events.save(1, first, 1, null, 150, "Inne", "first"))
        assertTrue(sessions.insert(2, 3, 100, 200))
        val second = sessionId()
        assertTrue(events.save(2, second, 3, null, 150, "Inne", "second"))
        assertTrue(sessions.deleteCompleted(1, first))
        assertNull(sessions.findSession(1, first, 1))
        assertNotNull(sessions.findSession(2, second, 3))
        assertEquals("second", events.sessionEvents(2, second).single().note)
        assertEquals(1, eventCount())
    }

    @Test fun failedSessionDeleteRollsBackDependentEventDeletion() {
        assertTrue(sessions.insert(1, 1, 100, 200))
        val id = sessionId()
        assertTrue(events.save(1, id, 1, null, 150, "Inne", "keep"))
        db.execSQL("CREATE TRIGGER reject_session_delete BEFORE DELETE ON sessions BEGIN SELECT RAISE(ABORT, 'test failure'); END")
        try {
            sessions.deleteCompleted(1, id)
            fail("Expected the injected storage failure")
        } catch (_: SQLiteException) {
            assertNotNull(sessions.findSession(1, id, 1))
            assertEquals("keep", events.sessionEvents(1, id).single().note)
        }
    }

    @Test fun activeSessionCannotBeDeletedOrStoppedBeforeAnExistingEvent() {
        assertTrue(sessions.insert(1, 1, 100, null))
        val id = sessionId()
        assertTrue(events.save(1, id, 1, null, 150, "Inne", "keep"))
        assertFalse(sessions.deleteCompleted(1, id))
        assertFalse(sessions.toggleTimer(1, 1, 120))
        assertEquals(100L, sessions.findActiveSession(1, 1)?.startedAt)
        assertEquals("keep", events.sessionEvents(1, id).single().note)
    }

    @Test fun rapidToggleRejectsZeroLengthStopWithoutDuplicatingTheTimer() {
        assertTrue(sessionService.toggleTimer(1, 1, 1_000))
        val id = sessionId()
        assertFalse(sessionService.toggleTimer(1, 1, 1_000))
        assertEquals(id, sessions.findActiveSession(1, 1)?.id)
        assertEquals(1, sessionChangeCount)
        assertTrue(sessionService.toggleTimer(1, 1, 2_000))
        assertNull(sessions.findActiveSession(1, 1))
        assertEquals(2_000L, sessions.findSession(1, id, 1)?.endedAt)
        assertEquals(2, sessionChangeCount)
    }

    @Test fun archivedProjectRejectsSessionAndEventMutations() {
        assertTrue(sessionService.createSession(1, 1, 100, 200))
        val id = sessionId()
        assertTrue(eventService.saveEvent(1, id, 1, null, 150, "Inne", "keep"))
        val eventId = events.sessionEvents(1, id).single().id
        assertTrue(projects.archiveIfNoActiveTimers(1))
        assertFalse(sessionService.toggleTimer(1, 1, 300))
        assertFalse(sessionService.createSession(1, 2, 300, 400))
        assertFalse(sessionService.updateSession(1, 1, id, 100, 250))
        assertFalse(sessionService.deleteCompletedSession(1, id))
        assertFalse(eventService.saveEvent(1, id, 1, eventId, 160, "Inne", "changed"))
        assertFalse(eventService.deleteEvent(1, eventId))
        assertEquals(200L, sessions.findSession(1, id, 1)?.endedAt)
        assertEquals("keep", events.sessionEvents(1, id).single().note)
        assertEquals(1, sessionChangeCount)
    }

    @Test fun projectArchivalIsBlockedUntilAllTimersStop() {
        assertTrue(sessionService.toggleTimer(1, 1, 1_000))
        assertTrue(sessionService.toggleTimer(1, 2, 1_000))
        assertFalse(projects.archiveIfNoActiveTimers(1))
        assertTrue(sessionService.toggleTimer(1, 1, 2_000))
        assertFalse(projects.archiveIfNoActiveTimers(1))
        assertTrue(sessionService.toggleTimer(1, 2, 2_000))
        assertTrue(projects.archiveIfNoActiveTimers(1))
    }

    @Test fun domainRejectsParticipantFromAnotherProjectWithoutNotifications() {
        assertFalse(sessionService.createSession(1, 3, 100, 200))
        assertFalse(sessionService.toggleTimer(1, 3, 1_000))
        assertEquals(0, sessionChangeCount)
        assertTrue(sessions.sessionsInRange(1, 0, 2_000).isEmpty())
    }

    @Test fun crossProjectEditsAndDeletesCannotChangeTheOriginalSession() {
        assertTrue(sessions.insert(1, 1, 100, 200))
        val id = sessionId()
        assertFalse(sessions.update(2, 3, id, 110, 190))
        assertFalse(sessions.deleteCompleted(2, id))
        assertFalse(events.save(2, id, 3, null, 150, "Inne", "wrong project"))
        assertEquals(100L, sessions.findSession(1, id, 1)?.startedAt)
        assertEquals(200L, sessions.findSession(1, id, 1)?.endedAt)
        assertEquals(0, eventCount())
    }

    @Test fun medicationRequiresANoteAndRejectedChangesDoNotNotify() {
        assertTrue(sessionService.createSession(1, 1, 100, 200))
        val id = sessionId()
        assertFalse(eventService.saveEvent(1, id, 1, null, 150, "Lek", "   "))
        assertTrue(eventService.saveEvent(1, id, 1, null, 150, "Lek", "recorded administration"))
        assertFalse(sessionService.updateSession(1, 1, id, 160, 200))
        assertEquals(1, sessionChangeCount)
        assertEquals(1, eventCount())
    }

    @Test fun participantWithLegacyOrphanEventCannotBeDeleted() {
        db.execSQL("INSERT INTO events(project_id,session_id,person,happened_at,type,note) VALUES(1,999,1,150,'Inne','legacy')")
        assertFalse(participants.deleteIfNoSessions(1, 1))
        assertTrue(participants.belongsToProject(1, 1))
        assertEquals(1, eventCount())
    }

    @Test fun timerSnapshotRetainsStoredColorsAndEventDoesNotChangeElapsedTime() {
        val now = System.currentTimeMillis()
        assertTrue(sessionService.createSession(1, 1, now - 1_000, null))
        val id = sessionId()
        val controller = TimersController(projects, participants, sessions, sessionService)
        val before = controller.loadState(1, "Person ", now)
        assertTrue(eventService.saveEvent(1, id, 1, null, now - 500, "Inne", "annotation"))
        val after = controller.loadState(1, "Person ", now)
        assertEquals(before.cards, after.cards)
        assertEquals(0xff228866.toInt(), after.cards.first().participant.participantColor)
        assertEquals(now - 1_000, after.cards.first().participant.activeStartedAt)
        assertTrue(after.recentActivity.any { it.description == "event:Inne:annotation" })
        assertEquals(1_000L, StatisticsCalculator.totalDuration(after.cards.first().intervals, now - 1_000, now, now))
        assertEquals(1, sessionChangeCount)
    }
}
