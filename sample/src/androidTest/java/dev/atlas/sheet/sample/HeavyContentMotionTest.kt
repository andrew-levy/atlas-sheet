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

    private fun runs(demo: Demo, backgroundPriority: Boolean = true): List<OpenTiming> {
        HeavyLoad.backgroundPriority = backgroundPriority
        return (1..5).map { open(demo) }
    }

    @Test fun backgroundPriorityLoadingDoesNotDelayOrStallOpening() {
        open(Demo.Chat)
        val baseline = runs(Demo.Chat)
        val normalPriority = runs(Demo.Heavy, backgroundPriority = false)
        val heavy = runs(Demo.Heavy)
        fun median(values: List<Long>) = values.sorted()[values.size / 2]
        fun describe(name: String, timings: List<OpenTiming>) = "$name: start=${median(timings.map { it.firstMotionMillis })}ms " +
            "motion=${median(timings.map { it.settledMillis - it.firstMotionMillis })}ms longestFrame=${median(timings.map { it.longestFrameMillis })}ms"
        val summary = listOf(describe("chat", baseline), describe("heavy/normal-priority", normalPriority), describe("heavy/background-priority", heavy)).joinToString("; ")
        Log.i("AtlasHeavy", summary)
        println(summary)
        val baseStart = median(baseline.map { it.firstMotionMillis })
        val baseMotion = median(baseline.map { it.settledMillis - it.firstMotionMillis })
        val baseFrame = median(baseline.map { it.longestFrameMillis })
        // CPU saturation on 2-core CI emulators delays the start by roughly 0.2-0.35 s (docs/VALIDATION.md);
        // this bound catches regressions well beyond that.
        assertTrue("Opening started much later under load: $summary", median(heavy.map { it.firstMotionMillis }) <= baseStart * 2 + 200)
        assertTrue("Opening took longer under load: $summary", median(heavy.map { it.settledMillis - it.firstMotionMillis }) <= baseMotion * 3 / 2 + 150)
        assertTrue("Longest frame grew under load: $summary", median(heavy.map { it.longestFrameMillis }) <= baseFrame * 2 + 50)
    }
}
