package dev.better.sheet.sample

import android.graphics.Bitmap
import android.os.SystemClock
import android.view.MotionEvent
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import dev.better.sheet.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Test

/** No paused Compose clock, forced layout, or captureToImage: inspect the actual window pixels. */
class TrayRenderingTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test fun realClockSurfaceSurvivesRepeatedMorphsAndNavigationHasNoFooterShadow() {
        lateinit var state: SheetState
        lateinit var scope: CoroutineScope
        val initialized = java.util.concurrent.atomic.AtomicBoolean(false)
        var presentation by mutableStateOf<SheetPresentation>(SheetPresentation.Tray())
        var surfaceBottom = 0f
        var surfaceLeft = 0f
        var surfaceRight = 0f
        var footerBottom = 0f
        var footerCenter = 0f
        ActivityScenario.launch(MainActivity::class.java).use { activity ->
            activity.onActivity { it.setContent {
                state = rememberSheetState("medium")
                scope = rememberCoroutineScope()
                SideEffect { initialized.set(true) }
                Box(Modifier.fillMaxSize().background(Color.Green))
                BetterModalSheet(state,
                    listOf(SheetSnapPoint.fraction("medium", .5f), SheetSnapPoint.expanded()),
                    presentation = presentation,
                    containerColor = Color.Cyan,
                    sheetTransform = Modifier.onGloballyPositioned {
                        val bounds = it.boundsInWindow()
                        surfaceBottom = bounds.bottom
                        surfaceLeft = bounds.left
                        surfaceRight = bounds.right
                    },
                    bodyMode = SheetBodyMode.Fill,
                    header = { Box(Modifier.fillMaxWidth().height(48.dp).background(Color.Magenta)) },
                    footer = {
                        Surface(shadowElevation = 12.dp) {
                            Box(Modifier.fillMaxWidth().height(64.dp).onGloballyPositioned { coordinates ->
                                val bounds = coordinates.boundsInWindow()
                                footerBottom = bounds.bottom
                                footerCenter = bounds.center.x
                            })
                        }
                    }) { Spacer(Modifier.fillMaxSize()) }
            } }
            fun awaitSettled(target: String) {
                val deadline = SystemClock.uptimeMillis() + 15000
                while (true) {
                    var ready = false
                    instrumentation.runOnMainSync {
                        ready = initialized.get() && state.currentSnapPoint == target && !state.isAnimationRunning
                    }
                    if (ready) break
                    check(SystemClock.uptimeMillis() < deadline) { "Tray never settled at $target" }
                    SystemClock.sleep(30)
                }
                // Let RenderThread display it; do not force another layout to repair a missing layer.
                SystemClock.sleep(100)
            }
            fun screenshot(): Bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
            awaitSettled("medium")
            for (style in listOf(SheetPresentation.Tray(), SheetPresentation.Standard)) {
                instrumentation.runOnMainSync { presentation = style }
                awaitSettled("medium")
                repeat(3) {
                    for (target in listOf("expanded", "medium")) {
                        instrumentation.runOnMainSync { scope.launch { state.snapTo(target) } }
                        awaitSettled(target)
                        repeat(3) {
                            val shot = screenshot()
                            try {
                                var magenta = 0
                                for (y in 0 until shot.height step 4) for (x in 0 until shot.width step 4) {
                                    val pixel = shot.getPixel(x, y)
                                    if (android.graphics.Color.red(pixel) > 240 && android.graphics.Color.blue(pixel) > 240 &&
                                        android.graphics.Color.green(pixel) < 15) magenta++
                                }
                                assertTrue("Visible tray header disappeared at $target", magenta > 100)
                                if (target == "medium" && style is SheetPresentation.Tray) {
                                    val y = surfaceBottom.toInt() + 2
                                    if (y < shot.height) {
                                        // A floating tray's shadow must fade below its bottom edge.
                                        // Cutting it off here creates straight seams beside the corners.
                                        val below = shot.getPixel(footerCenter.toInt(), y)
                                        val outside = shot.getPixel(2, y)
                                        assertTrue("Floating tray shadow was cut off at the bottom edge",
                                            android.graphics.Color.green(below) < android.graphics.Color.green(outside) - 2)
                                        for (edge in listOf(surfaceLeft, surfaceRight - 1)) {
                                            val x = edge.toInt().coerceIn(0, shot.width - 1)
                                            val above = shot.getPixel(x, surfaceBottom.toInt() - 1)
                                            val under = shot.getPixel(x, surfaceBottom.toInt() + 1)
                                            assertEquals("Shadow has a hard seam beside a rounded tray corner",
                                                android.graphics.Color.green(above).toFloat(),
                                                android.graphics.Color.green(under).toFloat(), 3f)
                                        }
                                    }
                                }
                                if (target == "expanded" || style == SheetPresentation.Standard) {
                                    val x = footerCenter.toInt().coerceIn(0, shot.width - 1)
                                    val y = footerBottom.toInt() + 2
                                    if (y < shot.height - 2) {
                                        val pixel = shot.getPixel(x, y)
                                        assertTrue("Footer shadow leaked into navigation area",
                                            android.graphics.Color.red(pixel) < 15 &&
                                            android.graphics.Color.green(pixel) > 240 && android.graphics.Color.blue(pixel) > 240)
                                    }
                                }
                            } finally { shot.recycle() }
                            SystemClock.sleep(50)
                        }
                    }
                }
            }
        }
    }

    @Test fun heldDragAndReversalKeepTheSameSoftShadowAtEveryHeight() {
        lateinit var state: SheetState
        var headerTop = 0f
        var surfaceBottom = 0f
        var surfaceCenter = 0f
        ActivityScenario.launch(MainActivity::class.java).use { activity ->
            activity.onActivity { it.setContent {
                state = rememberSheetState("medium")
                Box(Modifier.fillMaxSize().background(Color.Green))
                BetterModalSheet(
                    state = state,
                    snapPoints = listOf(SheetSnapPoint.fraction("medium", .5f), SheetSnapPoint.expanded()),
                    presentation = SheetPresentation.Tray(),
                    behavior = SheetBehavior(scrimEnabled = false),
                    bodyMode = SheetBodyMode.Fill,
                    containerColor = Color.Cyan,
                    sheetTransform = Modifier.onGloballyPositioned {
                        val bounds = it.boundsInWindow()
                        surfaceBottom = bounds.bottom
                        surfaceCenter = bounds.center.x
                    },
                    dragHandle = null,
                    header = {
                        Box(Modifier.fillMaxWidth().height(48.dp).background(Color.Magenta).onGloballyPositioned {
                            headerTop = it.boundsInWindow().top
                        })
                    },
                    footer = { Box(Modifier.fillMaxWidth().height(64.dp)) },
                ) { Spacer(Modifier.fillMaxSize()) }
            } }
            val deadline = SystemClock.uptimeMillis() + 15000
            while (true) {
                var ready = false
                instrumentation.runOnMainSync {
                    ready = headerTop > 0f && state.isVisible && !state.isAnimationRunning && headerTop > 0f
                }
                if (ready) break
                check(SystemClock.uptimeMillis() < deadline) { "Tray did not open" }
                SystemClock.sleep(30)
            }
            val initial = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
            val distance = initial.height * .15f
            initial.recycle()
            val downTime = SystemClock.uptimeMillis()
            var y = headerTop + 30f
            fun pointer(action: Int) {
                val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, surfaceCenter, y, 0)
                try { instrumentation.sendPointerSync(event) } finally { event.recycle() }
            }
            pointer(MotionEvent.ACTION_DOWN)
            var previousDarkening: Float? = null
            try {
                for (delta in listOf(-distance, -distance / 2f, distance / 2f)) {
                    repeat(12) {
                        y += delta / 12f
                        pointer(MotionEvent.ACTION_MOVE)
                        SystemClock.sleep(24)
                    }
                    // Keep the finger down: settled-only screenshots missed this regression.
                    SystemClock.sleep(120)
                    var progress = 0f
                    instrumentation.runOnMainSync { progress = state.progress }
                    assertTrue("Expected an intermediate morph while dragging, got $progress", progress > .6f && progress < .98f)
                    val shot = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
                    try {
                        val row = (surfaceBottom.toInt() + 2).coerceAtMost(shot.height - 2)
                        val outside = shot.getPixel(2, row)
                        val beneath = shot.getPixel(shot.width / 3, row)
                        val darkening = (android.graphics.Color.green(outside) - android.graphics.Color.green(beneath)).toFloat()
                        assertTrue("The floating shadow disappeared while dragging at $progress", darkening > 2f)
                        previousDarkening?.let { previous ->
                            assertEquals("Shadow strength changed with tray height or drag direction at $progress",
                                previous, darkening, 4f)
                        }
                        previousDarkening = darkening
                        // A diffuse shadow must fade away from the edge, not form an offset slab.
                        val furtherY = (row + 6).coerceAtMost(shot.height - 2)
                        val outsideFurther = shot.getPixel(2, furtherY)
                        val further = shot.getPixel(shot.width / 3, furtherY)
                        val furtherDarkening = android.graphics.Color.green(outsideFurther) - android.graphics.Color.green(further)
                        assertTrue("Shadow has a hard offset shelf at $progress", furtherDarkening <= darkening + 2f)
                    } finally { shot.recycle() }
                }
            } finally { pointer(MotionEvent.ACTION_UP) }
        }
    }

}

