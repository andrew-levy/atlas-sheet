package dev.atlas.sheet.sample

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Rule
import org.junit.Test

/** Deliberate pauses only for human-readable screen recordings; excluded from normal CI. */
class DemoRecordingTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun pause() { compose.waitForIdle(); Thread.sleep(1400) }
    private fun open(name: String) {
        pause()
        compose.onNodeWithTag("open-$name").performScrollTo().performClick()
        pause()
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
        compose.onNodeWithTag("field-8").performScrollTo(); pause()
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
}
