package pl.wgabel.timetracker

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.ListView
import android.widget.RemoteViews
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import pl.wgabel.timetracker.widgets.TimerWidgetProvider
import pl.wgabel.timetracker.widgets.WidgetParticipantListService

/** Regression checks for the widget collection layout and service registration. */
@RunWith(AndroidJUnit4::class)
class WidgetListRenderingInstrumentedTest {
    @Test
    fun participantContainerIsAListView() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val layout = android.view.LayoutInflater.from(context).inflate(R.layout.widget_timers, null)
        assertNotNull(layout.findViewById<ListView>(R.id.widget_participants))
    }

    @Test
    fun remoteCollectionServiceIsRegistered() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val component = ComponentName(context, WidgetParticipantListService::class.java)
        val serviceInfo = context.packageManager.getServiceInfo(component, 0)
        assertEquals("android.permission.BIND_REMOTEVIEWS", serviceInfo.permission)
    }
}
