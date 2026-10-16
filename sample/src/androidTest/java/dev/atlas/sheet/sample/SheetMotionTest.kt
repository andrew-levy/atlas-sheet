package dev.atlas.sheet.sample

import androidx.activity.compose.setContent
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dev.atlas.sheet.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SheetMotionTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun stackEffectFollowsInterruptedExitAndRestoresUnderlyingSheet() {
        lateinit var stack: AtlasSheetStackState
        lateinit var base: AtlasSheetStackEntry
        lateinit var top: AtlasSheetStackEntry
        lateinit var scope: CoroutineScope
        var effectProgress = 0f
        compose.runOnUiThread { compose.activity.setContent {
            stack = rememberAtlasSheetStackState("base")
            scope = rememberCoroutineScope()
            AtlasSheetStack(stack) {
                sheet("base", modifier = Modifier.atlasStackEffect { entry ->
                    effectProgress = entry.coveredProgress
                    scaleX = 1f - .1f * effectProgress
                }) {
                    base = LocalAtlasSheetStackEntry.current
                    Spacer(Modifier.height(120.dp))
                }
                sheet("top", detents = listOf(SheetDetent.fraction("expanded", .6f))) {
                    top = LocalAtlasSheetStackEntry.current
                    Spacer(Modifier.height(120.dp))
                }
            }
        } }
        compose.waitForIdle()
        compose.runOnUiThread { stack.push("top") }
        compose.waitForIdle()
        assertEquals(1f, effectProgress, .01f)
        assertEquals(1, base.depth)
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread { scope.launch { top.sheetState.hide() } }
        compose.mainClock.advanceTimeBy(64)
        compose.waitForIdle()
        assertTrue("Effect must follow the exit before removal", effectProgress > 0f && effectProgress < 1f)
        assertEquals(2, stack.keys.size)
        compose.runOnUiThread { scope.launch { top.sheetState.show() } }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertEquals("Interrupted exit keeps both entries", 2, stack.keys.size)
        assertEquals(1f, effectProgress, .01f)
        compose.runOnUiThread { scope.launch { stack.pop() } }
        compose.waitForIdle()
        assertEquals(listOf("base"), stack.keys)
        assertEquals(0f, effectProgress, .01f)
        assertTrue(base.isTopmost)
    }

    @Test fun reachingListTopNeedsAnotherGestureBeforeSheetCollapses() {
        lateinit var state: SheetState
        lateinit var list: androidx.compose.foundation.lazy.LazyListState
        compose.runOnUiThread { compose.activity.setContent {
            state = rememberSheetState("medium")
            list = androidx.compose.foundation.lazy.rememberLazyListState(2)
            AtlasSheet(state, listOf(SheetDetent.fraction("small", .25f),
                SheetDetent.fraction("medium", .6f), SheetDetent.expanded()),
                bodyMode = SheetBodyMode.Fill,
                behavior = SheetBehavior(dismissOnDrag = false)) {
                androidx.compose.foundation.lazy.LazyColumn(state = list,
                    modifier = Modifier.fillMaxSize().testTag("gesture-list")) {
                    items(100) { Spacer(Modifier.fillMaxWidth().height(32.dp)) }
                }
            }
        } }
        compose.waitForIdle()
        val before = state.progress
        compose.onNodeWithTag("gesture-list").performTouchInput {
            swipeDown(startY = height * .1f, endY = height * .9f, durationMillis = 500)
        }
        compose.waitForIdle()
        assertEquals(0, list.firstVisibleItemIndex)
        assertEquals(0, list.firstVisibleItemScrollOffset)
        assertEquals("Reaching the edge must not drag or fling the sheet", before, state.progress, .001f)
        compose.onNodeWithTag("gesture-list").performTouchInput {
            swipeDown(startY = height * .1f, endY = height * .9f, durationMillis = 500)
        }
        compose.waitForIdle()
        assertEquals("small", state.currentDetent)
    }

    @Test fun midScrolledContentTravelsWithHeaderDuringExpansion() {
        lateinit var state: SheetState
        lateinit var scope: CoroutineScope
        lateinit var list: androidx.compose.foundation.lazy.LazyListState
        compose.runOnUiThread { compose.activity.setContent {
            state = rememberSheetState("medium", tween(320))
            scope = rememberCoroutineScope()
            list = androidx.compose.foundation.lazy.rememberLazyListState(20)
            AtlasSheet(state, listOf(SheetDetent.fraction("medium", .5f), SheetDetent.expanded()),
                bodyMode = SheetBodyMode.Fill,
                header = { Spacer(Modifier.fillMaxWidth().height(48.dp).testTag("moving-header")) }) {
                androidx.compose.foundation.lazy.LazyColumn(state = list, modifier = Modifier.fillMaxSize()) {
                    items(100) { index -> Spacer(Modifier.fillMaxWidth().height(56.dp).testTag("row-$index")) }
                }
            }
        } }
        compose.waitForIdle()
        val headerBefore = compose.onNodeWithTag("moving-header").getUnclippedBoundsInRoot().top.value
        val rowBefore = compose.onNodeWithTag("row-21").getUnclippedBoundsInRoot().top.value
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread { scope.launch { state.expand() } }
        compose.mainClock.advanceTimeBy(128)
        compose.waitForIdle()
        val headerDelta = compose.onNodeWithTag("moving-header").getUnclippedBoundsInRoot().top.value - headerBefore
        val rowDelta = compose.onNodeWithTag("row-21").getUnclippedBoundsInRoot().top.value - rowBefore
        assertTrue("Sheet must be expanding", headerDelta < -1f)
        assertEquals("Content stays attached to the sheet top", headerDelta, rowDelta, 1f)
        assertEquals(20, list.firstVisibleItemIndex)
        compose.mainClock.autoAdvance = true
    }

    @Test fun modalPreservesHostSystemBarIconAppearance() {
        var dialogWindow: android.view.Window? = null
        compose.runOnUiThread { compose.activity.setContent {
            AtlasModalSheet(
                state = rememberSheetState(),
                detents = listOf(SheetDetent.expanded()),
                onDismissRequest = {},
            ) {
                val view = androidx.compose.ui.platform.LocalView.current
                SideEffect {
                    dialogWindow = (view.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window
                }
                Spacer(Modifier.height(120.dp))
            }
        } }
        compose.waitForIdle()
        compose.runOnUiThread {
            val activityWindow = compose.activity.window
            val modalWindow = checkNotNull(dialogWindow)
            val host = androidx.core.view.WindowCompat.getInsetsController(activityWindow, activityWindow.decorView)
            val modal = androidx.core.view.WindowCompat.getInsetsController(modalWindow, modalWindow.decorView)
            assertEquals(host.isAppearanceLightStatusBars, modal.isAppearanceLightStatusBars)
            assertEquals(host.isAppearanceLightNavigationBars, modal.isAppearanceLightNavigationBars)
            assertEquals(0, modalWindow.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            @Suppress("DEPRECATION")
            assertEquals(android.graphics.Color.TRANSPARENT, modalWindow.statusBarColor)
            @Suppress("DEPRECATION")
            assertEquals(android.graphics.Color.TRANSPARENT, modalWindow.navigationBarColor)
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                assertFalse(modalWindow.isNavigationBarContrastEnforced)
                assertFalse(modalWindow.isStatusBarContrastEnforced)
            }
        }
    }

    @Test fun scrimCoversSystemBars() {
        var top = 0
        var bottom = 0
        compose.runOnUiThread { compose.activity.setContent {
            val density = LocalDensity.current
            val insets = WindowInsets.systemBars.union(WindowInsets.displayCutout)
            top = insets.getTop(density)
            bottom = insets.getBottom(density)
            Box(Modifier.fillMaxSize().testTag("inset-host")) {
                AtlasSheet(
                    state = rememberSheetState(),
                    detents = listOf(SheetDetent.fraction("expanded", .5f)),
                    title = "inset test",
                ) { Spacer(Modifier.height(120.dp)) }
            }
        } }
        compose.waitForIdle()
        val host = compose.onNodeWithTag("inset-host").fetchSemanticsNode().boundsInRoot
        val scrim = compose.onNodeWithContentDescription("Dismiss inset test").fetchSemanticsNode().boundsInRoot
        assertTrue("Device supplies a system-bar inset", top > 0 || bottom > 0)
        assertEquals(host.top, scrim.top, 1f)
        assertEquals(host.bottom, scrim.bottom, 1f)
        assertEquals(host.left, scrim.left, 1f)
        assertEquals(host.right, scrim.right, 1f)
    }

    @Test fun mountedRootAnimatesAndReopensWithoutRecreatingContent() {
        lateinit var state: SheetState
        lateinit var scope: CoroutineScope
        var dismissed = 0
        var compositions = 0
        compose.runOnUiThread { compose.activity.setContent {
            state = rememberSheetState("medium", tween(320), initiallyVisible = false)
            scope = rememberCoroutineScope()
            AtlasSheet(state, listOf(SheetDetent.fraction("medium", .6f)), onDismissed = { dismissed++ }) {
                DisposableEffect(Unit) { compositions++; onDispose {} }
                Spacer(Modifier.height(120.dp))
            }
        } }
        compose.waitForIdle()
        assertEquals(0f, state.progress, .001f)
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread { scope.launch { state.show() } }
        compose.mainClock.advanceTimeBy(80)
        compose.waitForIdle()
        val early = state.progress
        assertTrue("Entry must have intermediate frames", early > 0f && early < .6f)
        compose.mainClock.advanceTimeBy(120)
        compose.waitForIdle()
        assertTrue(state.progress > early && state.progress < .6f)
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()
        assertEquals(.6f, state.progress, .01f)
        compose.runOnUiThread { scope.launch { state.hide() } }
        compose.mainClock.advanceTimeBy(160)
        compose.waitForIdle()
        assertEquals("Dismiss callback waits for the exit", 0, dismissed)
        assertTrue(state.progress > 0f && state.progress < .6f)
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()
        assertEquals(1, dismissed)
        compose.runOnUiThread { scope.launch { state.hide(); state.show() } }
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()
        assertEquals(1, dismissed)
        assertEquals("medium", state.currentDetent)
        assertEquals("Body remains mounted across hide/show", 1, compositions)
    }

    @Test fun interruptedEntryDoesNotDismissAPartiallyVisibleRoot() {
        lateinit var state: SheetState
        lateinit var scope: CoroutineScope
        lateinit var entry: Job
        var dismissed = 0
        compose.runOnUiThread { compose.activity.setContent {
            state = rememberSheetState("medium", tween(320), initiallyVisible = false)
            scope = rememberCoroutineScope()
            AtlasSheet(state, listOf(SheetDetent.fraction("medium", .6f)), onDismissed = { dismissed++ }) {
                Spacer(Modifier.height(120.dp))
            }
        } }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread { entry = scope.launch { state.show() } }
        compose.mainClock.advanceTimeBy(128)
        compose.waitForIdle()
        assertTrue(state.progress > 0f)
        compose.runOnUiThread { entry.cancel() }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        assertEquals("Cancellation isn't dismissal while pixels are visible", 0, dismissed)
        compose.runOnUiThread { scope.launch { state.hide() } }
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()
        assertEquals(0f, state.progress, .001f)
        assertEquals(1, dismissed)
    }

    @Test fun liveContentFitResizesAcrossIntermediateFrames() {
        lateinit var state: SheetState
        var extra by mutableStateOf(false)
        compose.runOnUiThread { compose.activity.setContent {
            state = rememberSheetState("content", tween(320))
            AtlasSheet(state, listOf(SheetDetent.content()),
                header = { Spacer(Modifier.fillMaxWidth().height(48.dp).testTag("motion-header")) }) {
                Spacer(Modifier.height(if (extra) 320.dp else 80.dp))
            }
        } }
        compose.waitForIdle()
        val before = compose.onNodeWithTag("motion-header").getUnclippedBoundsInRoot().top
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread { extra = true }
        compose.mainClock.advanceTimeBy(128)
        compose.waitForIdle()
        val middle = compose.onNodeWithTag("motion-header").getUnclippedBoundsInRoot().top
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()
        val after = compose.onNodeWithTag("motion-header").getUnclippedBoundsInRoot().top
        assertTrue("Resize is gradual", middle < before && middle > after)
        assertTrue(kotlin.math.abs((before - after).value - 240f) < 1f)
    }
}
