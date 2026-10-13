package dev.atlas.sheet.sample

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.atlas.sheet.*
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SheetUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun open(name: String) {
        compose.onNodeWithTag("gallery").performScrollToNode(hasTestTag("open-$name"))
        compose.onNodeWithTag("open-$name").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithTag("footer").fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
    }
    @Test fun footerIsVisibleAtEveryDetent() {
        open("Detents")
        listOf("peek", "expanded", "medium").forEach { id ->
            compose.onNodeWithTag("detent-$id").performClick()
            compose.waitForIdle()
            compose.onNodeWithTag("footer").assertIsDisplayed()
            compose.onNodeWithTag("body-list").assertIsDisplayed()
        }
    }
    @Test fun contentFitRespondsToLiveContentChanges() {
        open("Fit")
        val initialTop = compose.onNodeWithTag("sheet-header").getUnclippedBoundsInRoot().top
        val beforeBounds = compose.onNodeWithTag("fit-body").getUnclippedBoundsInRoot()
        val before = beforeBounds.bottom - beforeBounds.top
        compose.onNodeWithTag("primary-action").performClick()
        compose.waitForIdle()
        val afterBounds = compose.onNodeWithTag("fit-body").getUnclippedBoundsInRoot()
        val after = afterBounds.bottom - afterBounds.top
        assertTrue("Content detent must grow", after > before)
        assertTrue("The visible sheet must grow, not only its content", compose.onNodeWithTag("sheet-header").getUnclippedBoundsInRoot().top < initialTop)
        compose.onNodeWithTag("footer").assertIsDisplayed()
    }
    @Test fun lockedFlowIgnoresBackAndDownwardDrag() {
        open("Locked")
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack()
        compose.onNodeWithTag("footer").assertIsDisplayed()
        compose.onNodeWithTag("detent-peek").performClick()
        compose.onNodeWithTag("sheet-title").performTouchInput { swipeDown() }
        compose.waitForIdle()
        compose.onNodeWithTag("footer").assertIsDisplayed()
        compose.onNodeWithTag("primary-action").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("open-Locked").assertIsDisplayed()
    }
    @Test fun composerSendsMessagesAndRemainsVisibleWithIme() {
        open("Chat")
        val initialFooterBottom = compose.onNodeWithTag("footer").fetchSemanticsNode().boundsInWindow.bottom
        compose.onNodeWithTag("composer").performClick().performTextInput("Hello Atlas")
        compose.waitForIdle()
        compose.waitUntil(20000) {
            compose.onNodeWithTag("footer").fetchSemanticsNode().boundsInWindow.bottom < initialFooterBottom - 100f
        }
        compose.waitUntil(20000) {
            compose.onNodeWithTag("detent-expanded").fetchSemanticsNode().config[SemanticsProperties.Selected]
        }
        compose.onNodeWithTag("detent-expanded").assertIsSelected()
        compose.onNodeWithTag("footer").assertIsDisplayed()
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val footerBottom = compose.onNodeWithTag("footer").fetchSemanticsNode().boundsInWindow.bottom
        assertTrue(footerBottom < device.displayHeight)
        compose.onNodeWithTag("send").performClick()
        compose.onNodeWithText("Hello Atlas").assertIsDisplayed()
        device.pressBack()
        compose.waitForIdle()
        compose.waitUntil(20000) {
            compose.onNodeWithTag("detent-medium").fetchSemanticsNode().config[SemanticsProperties.Selected]
        }
        compose.onNodeWithTag("detent-medium").assertIsSelected()
    }
    @Test fun scrollingHandsOffToSheetAndCanDismiss() {
        open("Detents")
        compose.onNodeWithTag("body-list").performTouchInput { swipeUp() }
        compose.waitForIdle()
        compose.onNodeWithTag("footer").assertIsDisplayed()
        compose.onNodeWithTag("close").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("open-Detents").assertIsDisplayed()
    }
    @Test fun footerKeepsItsSpaceWhenTheHeaderIsTooTallForTheWindow() {
        compose.runOnUiThread {
            compose.activity.setContent {
                Box(Modifier.fillMaxWidth().height(180.dp)) {
                    val state = rememberSheetState()
                    AtlasSheetHost(
                        state = state,
                        detents = listOf(SheetDetent.expanded()),
                        topMargin = 0.dp,
                        scrimColor = Color.Unspecified,
                        header = { Spacer(Modifier.height(240.dp)) },
                        footer = { Box(Modifier.fillMaxWidth().height(56.dp).background(Color.Blue).testTag("small-footer")) },
                    ) { Spacer(Modifier.height(100.dp)) }
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("small-footer").assertIsDisplayed().assertHeightIsEqualTo(56.dp)
    }
}
