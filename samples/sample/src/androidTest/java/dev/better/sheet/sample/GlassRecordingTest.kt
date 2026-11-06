package dev.better.sheet.sample

import android.graphics.Bitmap
import android.os.SystemClock
import android.view.MotionEvent
import android.widget.ScrollView
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
            device.waitForIdleSync()
            val start = SystemClock.uptimeMillis()
            var initialX = 0f
            var initialY = 0f
            for (frame in 0 until 146) {
                scenario.onActivity { activity ->
                    val glass = activity.findViewById<GlassView>(R.id.glass)
                    val scene = activity.findViewById<ScrollView>(R.id.backdrop)
                    if (frame == 0) {
                        check(glass.width > 0)
                        initialX = glass.x + glass.width / 2
                        initialY = glass.y + glass.height / 2
                    }
                    if (frame in 18..64) {
                        val angle = (frame - 18) * Math.PI / 46
                        val px = initialX + sin(angle).toFloat() * (scene.width - glass.width) * .44f
                        val py = initialY - sin(angle).toFloat() * glass.height * .95f
                        val action = when (frame) {
                            18 -> MotionEvent.ACTION_DOWN
                            64 -> MotionEvent.ACTION_UP
                            else -> MotionEvent.ACTION_MOVE
                        }
                        val event = MotionEvent.obtain(start, SystemClock.uptimeMillis(), action, px, py, 0)
                        glass.dispatchTouchEvent(event)
                        event.recycle()
                    }
                    if (frame in 70..132) {
                        val progress = (1 - cos((frame - 70) * Math.PI / 62)) / 2
                        scene.scrollTo(0, (progress * scene.height * .96).toInt())
                    }
                    if (frame == 133) check(scene.scrollY > glass.height)
                }
                device.waitForIdleSync()
                SystemClock.sleep(48)
                val screenshot = checkNotNull(device.uiAutomation.takeScreenshot())
                File(frames, "%05d.jpg".format(frame)).outputStream().use {
                    check(screenshot.compress(Bitmap.CompressFormat.JPEG, 92, it))
                }
                screenshot.recycle()
            }
        }
    }
}
