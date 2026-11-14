package pl.wgabel.timetracker

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.res.Configuration
import java.util.Locale

internal interface LanguageService {
    fun applyLanguage()
    fun selectLanguage()
}

internal class PreferencesLanguageService(private val activity: Activity) : LanguageService {
    private val prefs get() = activity.getSharedPreferences("preferences", Context.MODE_PRIVATE)
    override fun selectLanguage() {
        val choices = arrayOf(activity.getString(R.string.system_language), activity.getString(R.string.polish), activity.getString(R.string.english))
        val codes = arrayOf("", "pl", "en")
        val selected = codes.indexOf(prefs.getString("app_language", "") ?: "").coerceAtLeast(0)
        AlertDialog.Builder(activity).setTitle(activity.getString(R.string.language))
            .setSingleChoiceItems(choices, selected) { dialog, which ->
                prefs.edit().putString("app_language", codes[which]).apply()
                dialog.dismiss()
                activity.recreate()
            }.setNegativeButton(activity.getString(R.string.cancel), null).show()
    }

    override fun applyLanguage() {
        val code = prefs.getString("app_language", "") ?: ""
        val locale = if (code.isEmpty()) android.content.res.Resources.getSystem().configuration.locales[0] else Locale(code)
        Locale.setDefault(locale)
        val config = Configuration(activity.resources.configuration)
        config.setLocale(locale)
        activity.resources.updateConfiguration(config, activity.resources.displayMetrics)
    }
}
