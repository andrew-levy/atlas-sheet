package dev.better.sheet.sample

import androidx.activity.compose.setContent
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import dev.better.sheet.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Freeze animation time while Android draws and UiAutomation captures real window pixels. */
class EntranceRenderingTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test fun inlineFirstOpenAndReopenSlideFromBottom() = verifyEntrance(false, false)
    @Test fun modalFirstOpenAndReopenSlideFromBottom() = verifyEntrance(true, false)
    @Test fun initiallyVisibleInlineSlidesFromBottom() = verifyEntrance(false, true)
    @Test fun initiallyVisibleModalSlidesFromBottom() = verifyEntrance(true, true)

    private fun verifyEntrance(modal: Boolean, initiallyVisible: Boolean) {
        lateinit var state: SheetState
        lateinit var scope: CoroutineScope
        var hostBottom = 0f
        var height = 0f
        // Layout/draw run on Android's real clock; animations run on the test clock.
        // A slow screenshot must not consume the early part of the entrance animation.
        compose.mainClock.autoAdvance = false
        fun advanceFrames(count: Int) {
            repeat(count) {
                compose.mainClock.advanceTimeByFrame()
                compose.waitForIdle()
            }
        }
        fun renderedTop(): Float? {
            val shot = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
            try {
                for (y in 0 until shot.height) {
                    val pixel = shot.getPixel(shot.width / 2, y)
                    val red = android.graphics.Color.red(pixel)
                    val green = android.graphics.Color.green(pixel)
                    val blue = android.graphics.Color.blue(pixel)
                    // Include the same blue under legacy navigation-bar contrast tint.
                    if (red < 15 && blue > 100 && green in (blue * .16f).toInt()..(blue * .25f).toInt()) {
                        return y.toFloat()
                    }
                }
                return null
            } finally { shot.recycle() }
        }
        try {
            compose.runOnUiThread { compose.activity.setContent {
                state = rememberSheetState("half", initiallyVisible = initiallyVisible)
                scope = rememberCoroutineScope()
                Box(Modifier.fillMaxSize().background(Color.White)
                    .onGloballyPositioned { hostBottom = it.boundsInWindow().bottom }) {
                    val points = listOf(SheetSnapPoint.fraction("half", .5f))
                    val enter = slideInVertically(tween(2000, easing = LinearEasing)) { it }
                    val surface = Modifier.onGloballyPositioned { height = it.size.height.toFloat() }
                    if (modal) {
                        BetterModalSheet(state, points, enterTransition = enter, exitTransition = ExitTransition.None,
                            containerColor = Color(0xFF0033FF), scrimColor = Color.Transparent,
                            sheetTransform = surface, bodyMode = SheetBodyMode.Fill) { Spacer(Modifier.fillMaxSize()) }
                    } else {
                        BetterSheet(state, points, enterTransition = enter, exitTransition = ExitTransition.None,
                            containerColor = Color(0xFF0033FF), scrimColor = Color.Transparent,
                            sheetTransform = surface, bodyMode = SheetBodyMode.Fill) { Spacer(Modifier.fillMaxSize()) }
                    }
                }
            } }
            advanceFrames(2)
            repeat(2) { opening ->
                if (!initiallyVisible || opening > 0) compose.runOnUiThread { scope.launch { state.show() } }
                // Allow composition, measurement, and the transition's first frame to run.
                // Even on a slow emulator this advances animation time by only 160 ms.
                advanceFrames(10)
                fun drawnTop(): Float? {
                    compose.waitForIdle()
                    // waitForIdle covers layout/draw scheduling; wait for RenderThread to
                    // present the frame before sampling pixels (also works on API 24).
                    val drawn = java.util.concurrent.CountDownLatch(1)
                    compose.runOnUiThread {
                        android.view.Choreographer.getInstance().postFrameCallback {
                            android.view.Choreographer.getInstance().postFrameCallback { drawn.countDown() }
                        }
                    }
                    check(drawn.await(5, java.util.concurrent.TimeUnit.SECONDS)) { "Android did not draw a frame" }
                    return renderedTop()
                }
                fun awaitDrawnTop(label: String, ready: (Float) -> Boolean): Float {
                    var top: Float? = null
                    // Animation time is frozen, but Android/RenderThread can still be
                    // presenting the preceding frame under load. Wait for the requested
                    // pixels rather than assuming two Choreographer callbacks suffice.
                    try {
                        compose.waitUntil(10000) { drawnTop().also { top = it }?.let(ready) == true }
                    } catch (failure: androidx.compose.ui.test.ComposeTimeoutException) {
                        throw AssertionError("Opening $opening: $label; last rendered top=$top, height=$height", failure)
                    }
                    return checkNotNull(top)
                }
                val firstTop = awaitDrawnTop("no early sheet pixels") { true }
                var bottom = 0f
                var fullHeight = 0f
                compose.runOnUiThread { bottom = hostBottom; fullHeight = height }
                assertTrue("Opening $opening appeared too high: top=$firstTop, bottom=$bottom, height=$fullHeight",
                    fullHeight > 0f && firstTop >= bottom - fullHeight * .25f)
                compose.mainClock.advanceTimeBy(800)
                compose.waitForIdle()
                val middleTop = awaitDrawnTop("entrance did not move upward") {
                    firstTop - it > fullHeight * .25f
                }
                assertTrue("Opening $opening did not move upward: first=$firstTop, middle=$middleTop",
                    firstTop - middleTop > fullHeight * .25f)
                compose.mainClock.advanceTimeBy(2200)
                compose.waitForIdle()
                compose.runOnUiThread {
                    assertEquals("half", state.currentSnapPoint)
                    assertFalse("Entrance never settled", state.isAnimationRunning)
                }
                val finalTop = awaitDrawnTop("settled frame skipped entrance travel") {
                    firstTop - it > fullHeight * .65f && middleTop - it > fullHeight * .25f
                }
                assertTrue("Opening $opening skipped most of its travel: first=$firstTop, final=$finalTop, height=$fullHeight",
                    firstTop - finalTop > fullHeight * .65f)
                assertTrue("Opening $opening settled before the middle frame: middle=$middleTop, final=$finalTop",
                    middleTop - finalTop > fullHeight * .25f)
                compose.runOnUiThread { scope.launch { state.hide() } }
                advanceFrames(2)
                // The sheet also animates its scrim/stack progress for 150 ms.
                compose.mainClock.advanceTimeBy(500)
                compose.waitForIdle()
                compose.runOnUiThread {
                    assertFalse("Exit never settled", state.isVisible || state.isAnimationRunning)
                }
                compose.waitUntil(5000) { drawnTop() == null }
            }
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }
}
