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
            val fragment = compose.activity.supportFragmentManager.findFragmentByTag("sheet") as RootSheetFragment
            val window = fragment.requireDialog().window!!
            assertEquals(0, window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                assertFalse(window.isNavigationBarContrastEnforced)
                assertFalse(window.isStatusBarContrastEnforced)
            }
        }
        val fragment = compose.activity.supportFragmentManager.findFragmentByTag("sheet") as RootSheetFragment
        val openedProgress = fragment.observedState!!.progress
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("close-fragment").performClick()
        compose.mainClock.advanceTimeBy(80)
        compose.waitForIdle()
        assertTrue("Fragment remains mounted during exit", oldView.hasComposition)
        val closingProgress = fragment.observedState!!.progress
        assertTrue("dismiss() has intermediate exit frames", closingProgress > 0f && closingProgress < openedProgress)
        compose.mainClock.advanceTimeBy(2000)
        compose.mainClock.autoAdvance = true
        compose.waitUntil(10000) { !oldView.hasComposition }
        assertFalse(oldView.hasComposition)
        compose.onNodeWithTag("open-fragment").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithTag("fragment-header").fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        compose.onNodeWithTag("fragment-header").assertIsDisplayed()
    }
}
