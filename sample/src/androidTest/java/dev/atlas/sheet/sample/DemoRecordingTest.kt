package dev.atlas.sheet.sample

import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Deliberate pauses only for human-readable screen recordings; excluded from normal CI. */
class DemoRecordingTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Before fun realTimeClock() { compose.mainClock.autoAdvance = false }

    /**
     * Advances the paused Compose clock by the wall-clock time that actually elapsed, so recorded
     * motion plays at real speed even when the emulator renders fewer than 60 frames per second.
     */
    private fun pause(millis: Long = 1000) {
        val start = SystemClock.uptimeMillis()
        var advanced = 0L
        while (advanced < millis) {
            Thread.sleep(FRAME_MILLIS)
            val step = (SystemClock.uptimeMillis() - start - advanced).coerceAtLeast(FRAME_MILLIS)
            compose.mainClock.advanceTimeBy(step)
            advanced += step
        }
        compose.waitForIdle()
    }

    /**
     * Semantics scroll actions animate, so performScrollTo/performScrollToNode never finish while
     * the clock is paused. The gallery jumps with the clock running; sheet bodies scroll by gesture.
     */
    private fun open(name: String) {
        pause(500)
        Log.i(MARKER_TAG, MARKER_READY)
        compose.mainClock.autoAdvance = true
        compose.onNodeWithTag("gallery").performScrollToNode(hasTestTag("open-$name"))
        compose.mainClock.autoAdvance = false
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
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack(); pause()
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
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack(); pause()
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
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack(); pause()
        compose.onNodeWithTag("primary-action").performClick(); pause()
    }

    companion object {
        private const val FRAME_MILLIS = 16L
        /** scripts/record-demos.py trims each clip to start at this logcat marker. */
        const val MARKER_TAG = "AtlasDemo"
        const val MARKER_READY = "ready"
    }
}
