package pl.wgabel.timetracker

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.util.UUID

/** Runs the production helper against a disposable database, never the app's sessions.db. */
internal class IsolatedDatabaseContext(context: Context) : ContextWrapper(context), AutoCloseable {
    private val identifier = UUID.randomUUID().toString()
    private val directory = File(context.cacheDir, "sqlite-tests/$identifier").apply { mkdirs() }
    private val preferenceNames = mutableSetOf<String>()

    override fun getApplicationContext(): Context = this
    override fun getDatabasePath(name: String): File = File(directory, name)

    override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?): SQLiteDatabase =
        SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name), factory)

    override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?,
                                      errorHandler: DatabaseErrorHandler?): SQLiteDatabase =
        SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name).path, factory, errorHandler)

    override fun deleteDatabase(name: String): Boolean = SQLiteDatabase.deleteDatabase(getDatabasePath(name))

    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
        val isolatedName = "sqlite-test-$identifier-$name"
        preferenceNames.add(isolatedName)
        return baseContext.getSharedPreferences(isolatedName, mode)
    }

    override fun close() {
        directory.deleteRecursively()
        preferenceNames.forEach(baseContext::deleteSharedPreferences)
    }
}
