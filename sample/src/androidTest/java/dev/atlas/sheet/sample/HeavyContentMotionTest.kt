package dev.atlas.sheet.sample

import android.content.Intent
import android.os.SystemClock
import android.util.Log
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test

/**
 * Real-clock comparison: no Compose test rule, so the sheet animates on the device's frame clock
 * while the Heavy demo decodes 3,000 messages on every core and waits on a slow database read.
 */
class HeavyContentMotionTest {
    private fun open(demo: Demo): OpenTiming {
        OpenTimings.results.remove(demo.name)
        val intent = Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_DEMO, demo.name)
        ActivityScenario.launch<MainActivity>(intent).use {
            val deadline = SystemClock.uptimeMillis() + 30_000
            while (OpenTimings.results[demo.name] == null) {
                check(SystemClock.uptimeMillis() < deadline) { "${demo.name} never settled" }
                Thread.sleep(50)
            }
        }
        return OpenTimings.results.getValue(demo.name).also { Log.i("AtlasHeavy", "${demo.name}: $it") }
    }

    @Test fun heavyLoadingDoesNotDelayOrStallOpening() {
        open(Demo.Chat)
        val baseline = (1..3).map { open(Demo.Chat) }
        val heavy = (1..3).map { open(Demo.Heavy) }
        val summary = "baseline=$baseline heavy=$heavy"
        println(summary)
        fun median(values: List<Long>) = values.sorted()[values.size / 2]
        val baseStart = median(baseline.map { it.firstMotionMillis })
        val heavyStart = median(heavy.map { it.firstMotionMillis })
        val baseSettle = median(baseline.map { it.settledMillis - it.firstMotionMillis })
        val heavySettle = median(heavy.map { it.settledMillis - it.firstMotionMillis })
        val baseFrame = median(baseline.map { it.longestFrameMillis })
        val heavyFrame = median(heavy.map { it.longestFrameMillis })
        assertTrue("Opening started later under load: $summary", heavyStart <= baseStart + 150)
        assertTrue("Opening took longer under load: $summary", heavySettle <= baseSettle * 3 / 2 + 150)
        assertTrue("Longest frame grew under load: $summary", heavyFrame <= baseFrame * 2 + 50)
    }
}
