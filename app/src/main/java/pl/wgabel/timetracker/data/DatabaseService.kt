package pl.wgabel.timetracker

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** Database lifecycle and schema, isolated from the Activity. */
internal interface DatabaseService : AutoCloseable {
    val database: SQLiteDatabase
}

internal class SQLiteDatabaseService(context: Context) : DatabaseService {
    private val appContext = context.applicationContext
    private val helper = object : SQLiteOpenHelper(appContext, "sessions.db", null, 6) {
            override fun onCreate(database: SQLiteDatabase) {
                database.execSQL("CREATE TABLE projects (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, archived INTEGER NOT NULL DEFAULT 0, icon_variant INTEGER NOT NULL DEFAULT 0)")
                database.execSQL("CREATE TABLE participants (id INTEGER PRIMARY KEY AUTOINCREMENT, project_id INTEGER NOT NULL, name TEXT NOT NULL, color INTEGER NOT NULL DEFAULT 0)")
                database.execSQL("CREATE TABLE sessions (id INTEGER PRIMARY KEY AUTOINCREMENT, project_id INTEGER NOT NULL, person INTEGER NOT NULL, started_at INTEGER NOT NULL, ended_at INTEGER)")
                database.execSQL("CREATE UNIQUE INDEX one_active_per_person ON sessions(person) WHERE ended_at IS NULL")
                database.execSQL("CREATE TABLE events (id INTEGER PRIMARY KEY AUTOINCREMENT, project_id INTEGER NOT NULL, session_id INTEGER NOT NULL, person INTEGER NOT NULL, happened_at INTEGER NOT NULL, type TEXT NOT NULL, note TEXT NOT NULL)")
            }
            override fun onUpgrade(database: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
                if (oldVersion < 2) {
                    database.execSQL("CREATE TABLE projects (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL)")
                    database.execSQL("CREATE TABLE participants (id INTEGER PRIMARY KEY AUTOINCREMENT, project_id INTEGER NOT NULL, name TEXT NOT NULL)")
                    database.execSQL("INSERT INTO projects(name) VALUES ('Poprzednie pomiary')")
                    database.execSQL("INSERT INTO participants(id,project_id,name) VALUES (1,1,?)", arrayOf(appContext.getSharedPreferences("preferences", Context.MODE_PRIVATE).getString("name_1", appContext.getString(R.string.legacy_person_1))))
                    database.execSQL("INSERT INTO participants(id,project_id,name) VALUES (2,1,?)", arrayOf(appContext.getSharedPreferences("preferences", Context.MODE_PRIVATE).getString("name_2", appContext.getString(R.string.legacy_person_2))))
                    database.execSQL("ALTER TABLE sessions ADD COLUMN project_id INTEGER NOT NULL DEFAULT 1")
                }
                if (oldVersion < 3) database.execSQL("ALTER TABLE projects ADD COLUMN archived INTEGER NOT NULL DEFAULT 0")
                if (oldVersion < 4) database.execSQL("ALTER TABLE projects ADD COLUMN icon_variant INTEGER NOT NULL DEFAULT 0")
                if (oldVersion < 5) database.execSQL("CREATE TABLE events (id INTEGER PRIMARY KEY AUTOINCREMENT, project_id INTEGER NOT NULL, session_id INTEGER NOT NULL, person INTEGER NOT NULL, happened_at INTEGER NOT NULL, type TEXT NOT NULL, note TEXT NOT NULL)")
                if (oldVersion < 6) {
                    database.execSQL("ALTER TABLE participants ADD COLUMN color INTEGER NOT NULL DEFAULT 0")
                    database.rawQuery("SELECT id FROM participants", null).use { cursor ->
                        while (cursor.moveToNext()) {
                            val id = cursor.getInt(0)
                            database.execSQL(
                                "UPDATE participants SET color=? WHERE id=?",
                                arrayOf(ParticipantColors.legacyColor(id), id)
                            )
                        }
                    }
                }
            }
    }
    override val database: SQLiteDatabase by lazy { helper.writableDatabase }
    override fun close() { helper.close() }
}
