package pl.wgabel.timetracker

import android.content.Intent
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises vertical swipe on the actual widget list layout on an emulator.
 * This verifies ListView touch scrolling; a launcher-hosted end-to-end test is separate.
 */
@RunWith(AndroidJUnit4::class)
class WidgetScrollingInstrumentedTest {
    @Test
    fun swipeUpRevealsParticipantsBeyondVisibleRows() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val activity = instrumentation.startActivitySync(
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ) as MainActivity
        try {
            instrumentation.runOnMainSync {
                activity.setContentView(R.layout.widget_timers)
                val list = activity.findViewById<ListView>(R.id.widget_participants)
                list.adapter = ArrayAdapter(
                    activity, android.R.layout.simple_list_item_1,
                    (1..25).map { "Participant $it" }
                )
            }
            instrumentation.waitForIdleSync()
            val list = activity.findViewById<ListView>(R.id.widget_participants)
            val device = UiDevice.getInstance(instrumentation)
            val position = IntArray(2)
            instrumentation.runOnMainSync { list.getLocationOnScreen(position) }
            val x = position[0] + list.width / 2
            val bottom = position[1] + list.height * 4 / 5
            val top = position[1] + list.height / 5
            device.swipe(x, bottom, x, top, 30)
            instrumentation.waitForIdleSync()
            assertTrue("Swipe should reveal later participants", list.firstVisiblePosition > 0)
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }
}
