package dev.atlas.sheet.sample

import android.graphics.Bitmap
import android.view.Choreographer
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName

/**
 * Captures demo clips frame by frame; excluded from normal CI.
 *
 * Emulators without a host GPU composite only a few frames per second, so screenrecord misses
 * most of a sheet animation. Instead, each step advances the paused Compose clock by one output
 * frame, waits for that frame to reach the display, and screenshots the whole screen (including
 * dialog and IME windows). scripts/record-demos.py assembles the frames at [FRAME_MILLIS] per
 * frame, so Compose motion plays at its real speed. The IME animates on the device clock and may
 * appear abruptly.
 */
class DemoRecordingTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val testName = TestName()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private lateinit var frames: File
    private var frameIndex = 0

    @Before fun prepare() {
        compose.mainClock.autoAdvance = false
        frames = File(instrumentation.targetContext.getExternalFilesDir(null), "demo-frames/${testName.methodName}")
        frames.deleteRecursively()
        check(frames.mkdirs()) { "Cannot create $frames" }
    }

    private fun awaitDisplayedFrame() {
        val drawn = CountDownLatch(1)
        compose.runOnUiThread {
            Choreographer.getInstance().postFrameCallback {
                Choreographer.getInstance().postFrameCallback { drawn.countDown() }
            }
        }
        drawn.await(5, TimeUnit.SECONDS)
    }

    private fun capture() {
        compose.waitForIdle()
        awaitDisplayedFrame()
        val screenshot = instrumentation.uiAutomation.takeScreenshot() ?: error("Screenshot failed")
        File(frames, "%05d.jpg".format(frameIndex++)).outputStream().use {
            screenshot.compress(Bitmap.CompressFormat.JPEG, 92, it)
        }
        screenshot.recycle()
    }

    /** Records [millis] of Compose time; each captured frame is [FRAME_MILLIS] long. */
    private fun pause(millis: Long = 1000) {
        repeat((millis / FRAME_MILLIS).toInt()) {
            repeat((FRAME_MILLIS / CLOCK_FRAME_MILLIS).toInt()) { compose.mainClock.advanceTimeByFrame() }
            capture()
        }
    }

    /**
     * Semantics scroll actions animate, so performScrollTo/performScrollToNode never finish while
     * the clock is paused. The gallery jumps with the clock running; sheet bodies scroll by gesture.
     */
    private fun open(name: String) {
        compose.mainClock.autoAdvance = true
        compose.onNodeWithTag("gallery").performScrollToNode(hasTestTag("open-$name"))
        compose.mainClock.autoAdvance = false
        pause(500)
        compose.onNodeWithTag("open-$name").performClick()
        pause()
    }

    private fun swipeIntoView(tag: String) {
        val viewport = compose.onNode(hasScrollAction() and hasAnyDescendant(hasTestTag(tag)))
        repeat(8) {
            val visible = viewport.fetchSemanticsNode().boundsInRoot
            val target = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
            if (target.top >= visible.top && target.bottom <= visible.bottom) return
            viewport.performTouchInput { swipeUp(startY = bottom * .8f, endY = bottom * .2f, durationMillis = 700) }
            pause(700)
        }
        throw AssertionError("$tag did not scroll into view")
    }

    @Test fun detents() {
        open("Detents")
        listOf("peek", "expanded", "medium").forEach { compose.onNodeWithTag("detent-$it").performClick(); pause() }
        compose.onNodeWithTag("body-list").performTouchInput { swipeUp() }; pause()
        compose.onNodeWithTag("close").performClick(); pause()
    }
    @Test fun chat() {
        open("Chat")
        compose.onNodeWithTag("composer").performClick(); pause()
        compose.onNodeWithTag("composer").performTextInput("A composer that stays with you."); pause()
        compose.onNodeWithTag("send").performClick(); pause()
        UiDevice.getInstance(instrumentation).pressBack(); pause()
        compose.onNodeWithTag("detent-peek").performClick(); pause()
    }
    @Test fun contentFit() {
        open("Fit")
        compose.onNodeWithTag("primary-action").performClick(); pause()
        compose.onNodeWithTag("primary-action").performClick(); pause()
        compose.onNodeWithTag("close").performClick(); pause()
    }
    @Test fun stickyActions() {
        open("Form")
        compose.onNodeWithTag("field-0").performClick().performTextInput("Atlas Enterprise"); pause()
        swipeIntoView("field-8"); pause()
        UiDevice.getInstance(instrumentation).pressBack(); pause()
        compose.onNodeWithTag("detent-expanded").performClick(); pause()
    }
    @Test fun persistent() {
        open("Persistent")
        compose.onNodeWithTag("detent-medium").performClick(); pause()
        compose.onNodeWithTag("detent-peek").performClick(); pause()
        compose.onNodeWithTag("close").performClick(); pause()
    }
    @Test fun protectedFlow() {
        open("Locked")
        UiDevice.getInstance(instrumentation).pressBack(); pause()
        compose.onNodeWithTag("primary-action").performClick(); pause()
    }

    companion object {
        private const val CLOCK_FRAME_MILLIS = 16L
        /** Output frame duration; record-demos.py uses the same value. */
        const val FRAME_MILLIS = 32L
    }
}
