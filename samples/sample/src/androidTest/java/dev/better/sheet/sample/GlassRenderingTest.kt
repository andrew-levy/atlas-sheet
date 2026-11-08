package dev.better.sheet.sample

import android.graphics.Bitmap
import android.os.Build
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class GlassRenderingTest {
    private val device get() = InstrumentationRegistry.getInstrumentation()
    private fun screenshot(): Bitmap {
        device.waitForIdleSync()
        SystemClock.sleep(180)
        return checkNotNull(device.uiAutomation.takeScreenshot())
    }
    private fun different(a: Bitmap, b: Bitmap, x: Int, y: Int, w: Int, h: Int): Int {
        var changed = 0
        for (j in y until y + h step 3) for (i in x until x + w step 3) {
            if (a.getPixel(i, j) != b.getPixel(i, j)) changed++
        }
        return changed
    }

    @Test fun backdropScrollUpdatesStationaryGlass() {
        ActivityScenario.launch(GlassActivity::class.java).use { scenario ->
            val location = IntArray(2)
            var w = 0; var h = 0
            val before = screenshot()
            scenario.onActivity {
                it.glass.getLocationOnScreen(location)
                w = it.glass.width; h = it.glass.height
                it.scene.scrollTo(0, it.scene.height / 3)
            }
            val after = screenshot()
            assertTrue("A stationary lens must refresh after scrolling", different(before, after,
                location[0] + w / 4, location[1] + h / 4, w / 2, h / 2) > 100)
            before.recycle(); after.recycle()
        }
    }

    @Test fun refractionChangesPixelsAndSurvivesResizeAndReattach() {
        assumeTrue(Build.VERSION.SDK_INT >= 33)
        ActivityScenario.launch(GlassActivity::class.java).use { scenario ->
            val location = IntArray(2)
            var w = 0; var h = 0
            scenario.onActivity { it.glass.refractionAmount = 0f }
            val flat = screenshot()
            scenario.onActivity {
                it.glass.getLocationOnScreen(location)
                w = it.glass.width; h = it.glass.height
                it.glass.refractionAmount = 45f * it.resources.displayMetrics.density
            }
            val curved = screenshot()
            assertTrue("Refraction must alter the captured pixels", different(flat, curved,
                location[0], location[1], w, h) > 100)
            scenario.onActivity {
                val parent = it.glass.parent as FrameLayout
                val params = it.glass.layoutParams
                parent.removeView(it.glass)
                params.width = (w * .8f).toInt()
                parent.addView(it.glass, params)
            }
            screenshot().recycle()
            flat.recycle(); curved.recycle()
        }
    }

    @Test fun dragMovesLensAndTabBackdropIncludesIcons() {
        ActivityScenario.launch(GlassActivity::class.java).use { scenario ->
            screenshot().recycle()
            scenario.onActivity {
                val glass = it.glass
                val x = glass.x
                val y = glass.y
                val now = SystemClock.uptimeMillis()
                for ((action, delta) in listOf(MotionEvent.ACTION_DOWN to 0f,
                    MotionEvent.ACTION_MOVE to 40f, MotionEvent.ACTION_UP to 40f)) {
                    val event = MotionEvent.obtain(now, now + action + 1L, action, 20f + delta, 20f, 0)
                    glass.dispatchTouchEvent(event); event.recycle()
                }
                assertTrue(glass.x > x)
                assertEquals(y, glass.y, 1f)
                assertSame(it.tabBackdrop, it.selection.backdrop)
                val original = it.selection.x
                it.setTabPosition(2f)
                assertTrue(it.selection.x > original)
            }
        }
    }

    @Test fun ancestorBackdropRejectedAndStaticModeCanBeDetached() {
        ActivityScenario.launch(GlassActivity::class.java).use { scenario ->
            scenario.onActivity {
                try {
                    it.glass.backdrop = it.glass.parent as View
                    fail("Recursive backdrop should be rejected")
                } catch (_: IllegalArgumentException) { }
                it.glass.live = false
            }
            scenario.recreate()
            screenshot().recycle()
        }
    }
}
