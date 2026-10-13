package dev.atlas.sheet.sample

import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class FragmentSheetTest {
    @get:Rule val compose = createAndroidComposeRule<FragmentDemoActivity>()

    @Test fun fragmentRootClosesAfterExitAndDisposesItsViewComposition() {
        compose.onNodeWithTag("open-fragment").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithTag("fragment-header").fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        compose.onNodeWithTag("fragment-header").assertIsDisplayed()
        lateinit var oldView: ComposeView
        compose.runOnUiThread {
            oldView = compose.activity.supportFragmentManager.findFragmentByTag("sheet")!!.requireView() as ComposeView
        }
        compose.onNodeWithTag("close-fragment").performClick()
        compose.waitUntil(10000) { !oldView.hasComposition }
        assertFalse(oldView.hasComposition)
        compose.onNodeWithTag("open-fragment").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithTag("fragment-header").fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        compose.onNodeWithTag("fragment-header").assertIsDisplayed()
    }
}
