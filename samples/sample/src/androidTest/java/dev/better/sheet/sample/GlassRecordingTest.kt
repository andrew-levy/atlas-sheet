package dev.better.sheet.sample

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import java.io.File
import kotlin.math.*

class GlassRecordingTest {
    @Test fun glass() {
        val device = InstrumentationRegistry.getInstrumentation()
        val frames = File(device.targetContext.getExternalFilesDir(null), "demo-frames/glass")
        frames.deleteRecursively()
        check(frames.mkdirs())
        ActivityScenario.launch(GlassActivity::class.java).use { scenario ->
            // A live GlassView continuously invalidates on pre-draw, so the UI is
            // intentionally never idle. Wait for layout with a bounded poll instead.
            val start = SystemClock.uptimeMillis()
            var ready = false
            while (!ready && SystemClock.uptimeMillis() - start < 10_000L) {
                scenario.onActivity { activity ->
                    ready = activity.glass.width > 0 && activity.selection.width > 1
                }
                if (!ready) SystemClock.sleep(40)
            }
            check(ready) { "Glass scene did not lay out within 10 seconds" }
            var baseX = 0f
            var baseY = 0f
            for (frame in 0 until 330) {
                scenario.onActivity { activity ->
                    val glass = activity.glass
                    val scene = activity.scene
                    if (frame == 0) {
                        check(glass.width > 0 && activity.selection.width > 1)
                        baseX = glass.x; baseY = glass.y
                    }
                    // Move a clear lens across bright/dark nebula and fine star detail.
                    if (frame in 20..140) {
                        val t = (frame - 20) / 120f
                        glass.x = baseX + sin(t * 2 * PI).toFloat() * baseX * .85f
                        glass.y = baseY - sin(t * PI).toFloat() * scene.height * .25f
                    }
                    // Hold the lens stationary while the real ScrollView moves beneath it.
                    if (frame in 150..220) {
                        val t = (frame - 150) / 70f
                        scene.scrollTo(0, ((1 - cos(t * PI)) * .5 * scene.height * .45).toInt())
                    }
                    // Scrub the selection across the actual icon backdrop, then return.
                    if (frame in 230..310) {
                        val t = (frame - 230) / 80f
                        activity.setTabPosition((1 - cos(t * 2 * PI)).toFloat())
                        activity.selection.scaleX = 1f + .15f * sin(t * PI).toFloat()
                        activity.selection.scaleY = 1f + .12f * sin(t * PI).toFloat()
                    }
                    if (frame == 320) check(scene.scrollY > 0)
                }
                // Allow the UI thread to render the updated scene without waiting
                // for an idle state that the live backdrop can never reach.
                SystemClock.sleep(32)
                val screenshot = checkNotNull(device.uiAutomation.takeScreenshot())
                File(frames, "%05d.jpg".format(frame)).outputStream().use {
                    check(screenshot.compress(Bitmap.CompressFormat.JPEG, 100, it))
                }
                screenshot.recycle()
            }
        }
    }
}
