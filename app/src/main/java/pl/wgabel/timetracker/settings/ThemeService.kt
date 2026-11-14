package pl.wgabel.timetracker

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.view.WindowManager

/** Controls the persisted theme selection and system bar appearance. */
internal interface ThemeService {
    val isDark: Boolean
    fun applyThemeToSystem()
    fun selectTheme()
}

internal class PreferencesThemeService(private val activity: Activity) : ThemeService {
    private val prefs get() = activity.getSharedPreferences("preferences", Context.MODE_PRIVATE)
    override val isDark: Boolean get() = prefs.getString("app_theme", "light") == "dark"

    override fun applyThemeToSystem() {
        // System bars visually extend the dark header/footer, with light icons.
        val chrome = if (isDark) Color.rgb(25, 31, 43) else Color.rgb(24, 47, 53)
        activity.window.statusBarColor = chrome
        activity.window.navigationBarColor = chrome
        activity.window.decorView.systemUiVisibility = 0
        if (Build.VERSION.SDK_INT >= 29) {
            activity.window.isStatusBarContrastEnforced = false
            activity.window.isNavigationBarContrastEnforced = false
        }
        activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }

    override fun selectTheme() {
        val choices = arrayOf(activity.getString(R.string.theme_light), activity.getString(R.string.theme_dark))
        val selected = if (isDark) 1 else 0
        AlertDialog.Builder(activity).setTitle(activity.getString(R.string.theme))
            .setSingleChoiceItems(choices, selected) { dialog, which ->
                prefs.edit().putString("app_theme", if (which == 1) "dark" else "light").apply()
                dialog.dismiss()
                activity.recreate()
            }.setNegativeButton(activity.getString(R.string.cancel), null).show()
    }
}
