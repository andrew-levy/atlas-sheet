package dev.better.sheet.sample

import android.graphics.Bitmap
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** Real device input; avoids the older Espresso input reflection path on Android 16. */
class MaterialExamplesTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)

    private fun find(text: String, scroll: Int = 0): UiObject2 {
        repeat(if (scroll == 0) 1 else 6) {
            device.wait(Until.findObject(By.text(text)), 1500)?.let { return it }
            if (scroll != 0) {
                val x = device.displayWidth / 2
                val low = device.displayHeight * 4 / 5
                val high = device.displayHeight * 2 / 5
                device.swipe(x, if (scroll > 0) low else high, x, if (scroll > 0) high else low, 24)
            }
        }
        error("Could not find $text")
    }

    private fun click(text: String, scroll: Int = 0) {
        find(text, scroll).click()
        device.waitForIdle()
    }

    private fun capture(name: String) {
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "material-examples").apply { mkdirs() }
        val image = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        try { File(directory, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        finally { image.recycle() }
    }

    private fun open(example: MaterialExample, suffix: String = "") {
        click(example.title, scroll = 1)
        click("Expanded")
        find("expanded")
        capture("${example.name}$suffix-expanded")
    }

    @Test fun fourModesSnapAndStackInTheExistingApp() {
        ActivityScenario.launch(MainActivity::class.java).use {
            click("Material & transforms", scroll = 1)
            find("Same content. Four ways to present it.")
            capture("examples-page")
            for (example in MaterialExample.entries) {
                open(example)
                if (example == MaterialExample.Transform || example == MaterialExample.Both) {
                    click("Cover this sheet")
                    find("Stack preview")
                    capture("${example.name}-stack")
                    click("Close top sheet")
                    assertTrue(device.wait(Until.gone(By.text("Stack preview")), 10000))
                } else {
                    click("Save selection")
                    find("✓ Saved")
                }
                click("Peek")
                find("peek")
                find(if (example == MaterialExample.Transform || example == MaterialExample.Both) "Cover this sheet" else "✓ Saved")
                click("Close")
                assertTrue(device.wait(Until.gone(By.text("Close")), 10000))
            }
            click("Floating tray", scroll = -1)
            open(MaterialExample.Both, "-tray")
            click("Close")
            assertTrue(device.wait(Until.gone(By.text("Close")), 10000))
        }
    }
}
