package pl.wgabel.timetracker

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import pl.wgabel.timetracker.design.AppUi
import pl.wgabel.timetracker.settings.PreferencesLanguageService
import pl.wgabel.timetracker.settings.PreferencesThemeService
import pl.wgabel.timetracker.ui.AnimatedSplashScreen

/** Android lifecycle and system integration only; application navigation lives in AppCoordinator. */
class MainActivity : Activity() {
    private lateinit var coordinator: AppCoordinator
    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            if (!isFinishing) {
                coordinator.updateTimers(System.currentTimeMillis())
                handler.postDelayed(this, 1_000)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val language = PreferencesLanguageService(this)
        val theme = PreferencesThemeService(this)
        language.applyLanguage()
        theme.applyThemeToSystem()
        val container = (application as TimeTrackerApplication).container
        coordinator = AppCoordinator(this, container, language, theme) { startActivityForResult(it, 301) }
        setContentView(coordinator.root)
        coordinator.restoreState(savedInstanceState)
        val components = AppUi(this, container.designTokens) { theme.isDark }
        AnimatedSplashScreen(this, { theme.isDark }, { components.color("blue") },
            { components.color("muted") }, { components.color("ink") }).show(coordinator.root as android.widget.LinearLayout)
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
        }
        container.notifications.synchronizeTimerNotifications()
        handler.post(tick)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 301) coordinator.completeExport(resultCode, data)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        coordinator.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        if (::coordinator.isInitialized) coordinator.updateActiveAnimations(coordinator.root, true)
    }

    override fun onPause() {
        if (::coordinator.isInitialized) coordinator.updateActiveAnimations(coordinator.root, false)
        super.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (::coordinator.isInitialized) coordinator.dispose()
        super.onDestroy()
    }
}
