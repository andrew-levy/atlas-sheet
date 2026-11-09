package dev.better.sheet.sample

import androidx.activity.compose.setContent
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dev.better.sheet.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SheetMotionTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun inlineCanReopenBeforeExitFinishes() = reopenBeforeExitFinishes(modal = false)

    @Test fun modalCanReopenBeforeExitFinishes() = reopenBeforeExitFinishes(modal = true)

    private fun reopenBeforeExitFinishes(modal: Boolean) {
        lateinit var state: SheetState
        lateinit var scope: CoroutineScope
        var opens = 0
        var dismissals = 0
        var tapX = 0
        var tapY = 0
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        compose.runOnUiThread { compose.activity.setContent {
            state = rememberSheetState("medium")
            scope = rememberCoroutineScope()
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxWidth().padding(top = 48.dp).height(64.dp)
                    .onGloballyPositioned {
                        tapX = it.boundsInWindow().center.x.toInt()
                        tapY = it.boundsInWindow().center.y.toInt()
                    }.clickable {
                        opens++
                        scope.launch { state.show() }
                    })
                val points = listOf(SheetSnapPoint.fraction("medium", .4f))
                if (modal) {
                    BetterModalSheet(state, points,
                        exitTransition = slideOutVertically(tween(2000)) { it },
                        onDismissed = { dismissals++ }) { Spacer(Modifier.height(80.dp)) }
                } else {
                    BetterSheet(state, points,
                        exitTransition = slideOutVertically(tween(2000)) { it },
                        onDismissed = { dismissals++ }) { Spacer(Modifier.height(80.dp)) }
                }
            }
        } }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        try {
            // Real window input verifies the modal's FLAG_NOT_TOUCHABLE handoff too.
            device.click(tapX, tapY)
            compose.mainClock.advanceTimeBy(48)
            compose.waitForIdle()
            assertNull(state.targetSnapPoint)
            device.click(tapX, tapY)
            compose.mainClock.advanceTimeBy(208)
            compose.waitForIdle()
            assertEquals("A second scrim tap must not reopen the background yet", 0, opens)
            assertTrue("The long exit is deliberately still running", state.isAnimationRunning)
            device.click(tapX, tapY)
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            assertEquals("Faded scrim must release the reopen button", 1, opens)
            assertEquals("medium", state.targetSnapPoint)
            compose.mainClock.advanceTimeBy(3000)
            compose.waitForIdle()
            assertEquals("medium", state.currentSnapPoint)
            assertTrue(state.isVisible)
            assertEquals("Interrupted exit must not dismiss the reopened sheet", 0, dismissals)
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    @Test fun trayMorphsAtIntermediateFramesAndReversesWithoutRemounting() {
        lateinit var state: SheetState
        lateinit var scope: CoroutineScope
        var mounts = 0
        compose.runOnUiThread { compose.activity.setContent {
            state = rememberSheetState("medium")
            scope = rememberCoroutineScope()
            BetterSheet(state, listOf(SheetSnapPoint.fraction("medium", .6f), SheetSnapPoint.expanded()),
                snapAnimationSpec = tween(400),
                enterTransition = slideInVertically(tween(400)) { it },
                exitTransition = slideOutVertically(tween(400)) { it },
                maxWidth = 2000.dp,
                presentation = SheetPresentation.Tray(matchDeviceCorners = false),
                sheetTransform = Modifier.testTag("tray-surface"),
                header = { Spacer(Modifier.fillMaxWidth().height(48.dp).testTag("tray-header")) },
                footer = { Spacer(Modifier.fillMaxWidth().height(48.dp).testTag("tray-footer")) },
                bodyMode = SheetBodyMode.Fill) {
                DisposableEffect(Unit) { mounts++; onDispose {} }
                Spacer(Modifier.fillMaxSize())
            }
        } }
        compose.waitForIdle()
        fun bounds() = compose.onNodeWithTag("tray-surface").getUnclippedBoundsInRoot()
        val collapsed = bounds()
        assertEquals(12f, collapsed.left.value, 1f)
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread { scope.launch { state.expand() } }
        compose.mainClock.advanceTimeBy(200)
        compose.waitForIdle()
        val halfway = bounds()
        assertTrue((halfway.right - halfway.left) > (collapsed.right - collapsed.left))
        assertTrue(halfway.left > 0.dp)
        compose.runOnUiThread { scope.launch { state.collapse() } }
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()
        assertEquals(collapsed.left.value, bounds().left.value, 1f)
        assertEquals(collapsed.top.value, bounds().top.value, 1f)
        compose.runOnUiThread { scope.launch { state.expand() } }
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()
        val expanded = bounds()
        assertEquals(0f, expanded.left.value, 1f)
        assertTrue((expanded.right - expanded.left) > (halfway.right - halfway.left))
        val footer = compose.onNodeWithTag("tray-footer").getUnclippedBoundsInRoot()
        assertTrue(footer.bottom <= expanded.bottom)
        compose.runOnUiThread { scope.launch { state.hide() } }
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()
        assertFalse("Exit must finish: current=${state.currentSnapPoint}, target=${state.targetSnapPoint}, progress=${state.progress}, running=${state.isAnimationRunning}", state.isVisible)
        assertEquals(1, mounts)
        compose.mainClock.autoAdvance = true
    }

    @Test fun inlineBackgroundTouchesAreIndependentOfScrimColor() {
        var color by mutableStateOf(Color.Unspecified)
        var behavior by mutableStateOf(SheetBehavior())
        var backgroundTaps = 0
        lateinit var state: SheetState
        lateinit var scope: CoroutineScope
        compose.runOnUiThread { compose.activity.setContent {
            state = rememberSheetState()
            scope = rememberCoroutineScope()
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxWidth().height(80.dp)
                    .testTag("background-target").clickable { backgroundTaps++ })
                BetterSheet(
                    state = state,
                    snapPoints = listOf(SheetSnapPoint.fraction("expanded", .4f)),
                    scrimColor = color,
                    behavior = behavior,
                ) { Spacer(Modifier.height(80.dp)) }
            }
        } }
        compose.waitForIdle()
        for (scrim in listOf(Color.Unspecified, Color.Transparent, Color.Black.copy(alpha = .42f))) {
            for ((blocks, dismisses) in listOf(true to true, true to false, false to true, false to false)) {
                compose.runOnUiThread {
                    color = scrim
                    behavior = SheetBehavior(scrimEnabled = blocks, dismissOnScrim = dismisses)
                    scope.launch { state.show() }
                }
                compose.waitForIdle()
                val before = backgroundTaps
                // Inject a real touch at the underlying node's coordinates, so overlay hit testing runs.
                compose.onNodeWithTag("background-target").performTouchInput { click() }
                compose.waitForIdle()
                assertEquals(before + if (blocks) 0 else 1, backgroundTaps)
                assertEquals(!(blocks && dismisses), state.isVisible)
            }
        }
        compose.runOnUiThread { scope.launch { state.hide() } }
        compose.waitForIdle()
        val before = backgroundTaps
        compose.onNodeWithTag("background-target").performTouchInput { click() }
        compose.waitForIdle()
        assertEquals(before + 1, backgroundTaps)
    }

    @Test fun modalOutsideTapDismissesWithEnabledTransparentScrim() {
        lateinit var state: SheetState
        compose.runOnUiThread { compose.activity.setContent {
            state = rememberSheetState()
            BetterModalSheet(
                state = state,
                snapPoints = listOf(SheetSnapPoint.fraction("expanded", .4f)),
                scrimColor = Color.Transparent,
                behavior = SheetBehavior(scrimEnabled = true),
            ) { Spacer(Modifier.height(80.dp)) }
        } }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Dismiss Bottom sheet")
            .performTouchInput { click(androidx.compose.ui.geometry.Offset(center.x, 40f)) }
        compose.waitForIdle()
        assertFalse(state.isVisible)
    }

    @Test fun disabledModalScrimNeitherDismissesNorPassesTouchesToBackground() {
        lateinit var state: SheetState
        var backgroundTaps = 0
        compose.runOnUiThread { compose.activity.setContent {
            state = rememberSheetState()
            Box(Modifier.fillMaxSize().clickable { backgroundTaps++ })
            BetterModalSheet(
                state = state,
                snapPoints = listOf(SheetSnapPoint.fraction("expanded", .4f)),
                behavior = SheetBehavior(scrimEnabled = false),
            ) { Spacer(Modifier.height(80.dp)) }
        } }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Dismiss Bottom sheet").assertDoesNotExist()
        compose.onNode(isDialog()).performTouchInput {
            click(androidx.compose.ui.geometry.Offset(center.x, 40f))
        }
        compose.waitForIdle()
        assertTrue(state.isVisible)
        assertEquals(0, backgroundTaps)
    }

    @Test fun mountedModalsAccumulateDepthInterruptExitAndReopen() {
        lateinit var stackState: SheetStackState
        lateinit var base: SheetStackEntry
        lateinit var middle: SheetStackEntry
        lateinit var detail: SheetState
        lateinit var review: SheetState
        lateinit var scope: CoroutineScope
        var baseDepth = 0f
        var middleDepth = 0f
        var dismissals = 0
        compose.runOnUiThread { compose.activity.setContent {
            stackState = rememberSheetStackState()
            scope = rememberCoroutineScope()
            val first = rememberSheetState()
            detail = rememberSheetState(initiallyVisible = false)
            review = rememberSheetState(initiallyVisible = false)
            BetterModalSheet(first, listOf(SheetSnapPoint.expanded()), stackState = stackState,
                sheetTransform = Modifier.sheetStackEffect {
                    baseDepth = it.animatedDepth
                    scaleX = 1f - .06f * baseDepth
                }) {
                base = requireNotNull(LocalSheetStackEntry.current)
                Spacer(Modifier.height(120.dp).testTag("stack-first"))
            }
            BetterModalSheet(detail, listOf(SheetSnapPoint.fraction("expanded", .6f)), stackState = stackState,
                sheetTransform = Modifier.sheetStackEffect { middleDepth = it.animatedDepth }) {
                middle = requireNotNull(LocalSheetStackEntry.current)
                Spacer(Modifier.height(120.dp).testTag("stack-second"))
            }
            BetterModalSheet(review, listOf(SheetSnapPoint.fraction("expanded", .4f)),
                onDismissed = { dismissals++ }, stackState = stackState) {
                Spacer(Modifier.height(120.dp).testTag("stack-third"))
            }
        } }
        compose.waitForIdle()
        assertEquals(1, stackState.size)
        compose.onNodeWithTag("stack-second").assertDoesNotExist()
        compose.onNodeWithTag("stack-third").assertDoesNotExist()
        compose.runOnUiThread { scope.launch { detail.show() } }
        compose.waitForIdle()
        assertEquals(1f, baseDepth, .01f)
        compose.runOnUiThread { scope.launch { review.show() } }
        compose.waitForIdle()
        assertEquals(3, stackState.size)
        assertEquals(2f, baseDepth, .01f)
        assertEquals(1f, middleDepth, .01f)
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread { scope.launch { review.hide() } }
        compose.mainClock.advanceTimeBy(64)
        compose.waitForIdle()
        assertTrue(baseDepth > 1f && baseDepth < 2f)
        assertEquals(baseDepth - 1f, middleDepth, .01f)
        assertEquals(3, stackState.size)
        compose.runOnUiThread { scope.launch { review.show() } }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertEquals(0, dismissals)
        assertEquals(2f, baseDepth, .01f)
        repeat(2) {
            compose.runOnUiThread { scope.launch { review.hide() } }
            compose.waitForIdle()
            assertEquals(it + 1, dismissals)
            assertEquals(2, stackState.size)
            compose.onNodeWithTag("stack-third").assertDoesNotExist()
            assertEquals(1f, baseDepth, .01f)
            assertTrue(middle.isTopmost)
            compose.runOnUiThread { scope.launch { review.show() } }
            compose.waitForIdle()
            assertEquals(3, stackState.size)
            assertEquals(2f, baseDepth, .01f)
        }
        compose.runOnUiThread { scope.launch { review.hide(); detail.hide() } }
        compose.waitForIdle()
        assertEquals(1, stackState.size)
        assertEquals(0f, baseDepth, .01f)
        assertTrue(base.isTopmost)
    }

    @Test fun reachingListTopNeedsAnotherGestureBeforeSheetCollapses() {
        lateinit var state: SheetState
        lateinit var list: androidx.compose.foundation.lazy.LazyListState
        compose.runOnUiThread { compose.activity.setContent {
            state = rememberSheetState("medium")
            list = androidx.compose.foundation.lazy.rememberLazyListState(2)
            BetterSheet(state, listOf(SheetSnapPoint.fraction("small", .25f),
                SheetSnapPoint.fraction("medium", .6f), SheetSnapPoint.expanded()),
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
        assertEquals("small", state.currentSnapPoint)
    }

    @Test fun midScrolledContentTravelsWithHeaderDuringExpansion() {
        lateinit var state: SheetState
        lateinit var scope: CoroutineScope
        lateinit var list: androidx.compose.foundation.lazy.LazyListState
        compose.runOnUiThread { compose.activity.setContent {
            state = rememberSheetState("medium")
            scope = rememberCoroutineScope()
            list = androidx.compose.foundation.lazy.rememberLazyListState(20)
            BetterSheet(state, listOf(SheetSnapPoint.fraction("medium", .5f), SheetSnapPoint.expanded()),
                bodyMode = SheetBodyMode.Fill,
                snapAnimationSpec = tween(320),
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
            BetterModalSheet(
                state = rememberSheetState(),
                snapPoints = listOf(SheetSnapPoint.expanded()),
                onDismissed = {},
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
                BetterSheet(
                    state = rememberSheetState(),
                    snapPoints = listOf(SheetSnapPoint.fraction("expanded", .5f)),
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
            state = rememberSheetState("medium", initiallyVisible = false)
            scope = rememberCoroutineScope()
            BetterSheet(state, listOf(SheetSnapPoint.fraction("medium", .6f)),
                enterTransition = slideInVertically(tween(320)) { it },
                exitTransition = slideOutVertically(tween(320)) { it },
                onDismissed = { dismissed++ }) {
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
        assertTrue(state.progress > early)
        assertTrue("Entry remains active until its slide finishes", state.isAnimationRunning)
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()
        assertEquals(.6f, state.progress, .01f)
        compose.runOnUiThread { scope.launch { state.hide() } }
        compose.mainClock.advanceTimeBy(160)
        compose.waitForIdle()
        assertEquals("Dismiss callback waits for the exit", 0, dismissed)
        assertTrue(state.isAnimationRunning)
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()
        assertEquals(1, dismissed)
        compose.runOnUiThread { scope.launch { state.hide(); state.show() } }
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()
        assertEquals(1, dismissed)
        assertEquals("medium", state.currentSnapPoint)
        assertEquals("Body remains mounted across hide/show", 1, compositions)
    }

    @Test fun interruptedEntryDoesNotDismissAPartiallyVisibleRoot() {
        lateinit var state: SheetState
        lateinit var scope: CoroutineScope
        lateinit var entry: Job
        var dismissed = 0
        compose.runOnUiThread { compose.activity.setContent {
            state = rememberSheetState("medium", initiallyVisible = false)
            scope = rememberCoroutineScope()
            BetterSheet(state, listOf(SheetSnapPoint.fraction("medium", .6f)),
                enterTransition = slideInVertically(tween(320)) { it },
                exitTransition = slideOutVertically(tween(320)) { it },
                onDismissed = { dismissed++ }) {
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
            state = rememberSheetState("content")
            BetterSheet(state, listOf(SheetSnapPoint.fitContent()),
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

