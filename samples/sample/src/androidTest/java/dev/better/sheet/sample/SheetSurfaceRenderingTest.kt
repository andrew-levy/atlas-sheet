package dev.better.sheet.sample

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import dev.better.sheet.*
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Test

/** Actual window pixels verify clipping and system-area paint, including a deliberately overflowing footer. */
class SheetSurfaceRenderingTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var state: SheetState
    private lateinit var scope: CoroutineScope
    private var bounds = Rect.Zero
    private var body = Rect.Zero
    private var header = Rect.Zero
    private var footer = Rect.Zero
    private var materialInstances = 0
    private var materialBounds = Rect.Zero

    private fun mount(
        activity: ActivityScenario<MainActivity>, name: String,
        edge: SheetEdge = SheetEdge.Bottom,
        presentation: SheetPresentation = SheetPresentation.Standard,
        modal: Boolean = false, root: Boolean = false,
        custom: Boolean = true, stacked: Boolean = false, gradient: Boolean = false,
    ) {
        instrumentation.runOnMainSync { bounds = Rect.Zero; body = Rect.Zero; header = Rect.Zero; footer = Rect.Zero; materialInstances = 0; materialBounds = Rect.Zero }
        activity.onActivity { it.setContent { key(name) {
            state = rememberSheetState("medium")
            scope = rememberCoroutineScope()
            val surface: @Composable (Shape) -> Modifier = { outline ->
                check(outline != RectangleShape)
                remember { materialInstances++; true }
                val paint = if (gradient) Brush.verticalGradient(listOf(Color.Black, Color.White))
                    else Brush.verticalGradient(listOf(Color.Red, Color.Red))
                Modifier.background(paint).onGloballyPositioned { materialBounds = it.boundsInWindow() }
            }
            val stack = rememberSheetStackState()
            Box(Modifier.fillMaxSize().background(Color.Green)) {
                val onSurface = Modifier.onGloballyPositioned { bounds = it.boundsInWindow() }
                    .then(if (stacked) Modifier.sheetStackEffect { scaleX = .8f } else Modifier)
                val h: @Composable () -> Unit = {
                    Box(Modifier.fillMaxWidth().height(48.dp).background(Color.Blue).onGloballyPositioned { header = it.boundsInWindow() })
                }
                val f: @Composable () -> Unit = {
                    Box(Modifier.fillMaxWidth().height(64.dp)
                        .onGloballyPositioned { footer = it.boundsInWindow() }
                        .drawWithContent {
                            drawContent()
                            // Model an arbitrary footer effect spilling into the reserved navigation area.
                            drawRect(Color.Magenta, Offset(0f, size.height), Size(size.width, 200f))
                        }.background(Color.Yellow))
                }
                val content: @Composable ColumnScope.() -> Unit = {
                    Box(Modifier.fillMaxSize().onGloballyPositioned { body = it.boundsInWindow() })
                }
                val points = listOf(SheetSnapPoint.fraction("medium", .5f), SheetSnapPoint.expanded())
                if (modal) BetterModalSheet(state, points, stackState = stack,
                    containerColor = Color.Cyan, behavior = SheetBehavior(scrimEnabled = false), bodyMode = SheetBodyMode.Fill, edge = edge,
                    presentation = presentation, header = h, footer = f, sheetTransform = onSurface,
                    sheetSurface = if (custom) surface else null, content = content)
                else if (root) BetterSheet(state, points,
                    containerColor = Color.Cyan, behavior = SheetBehavior(scrimEnabled = false), bodyMode = SheetBodyMode.Fill,
                    presentation = presentation, header = h, footer = f, sheetTransform = onSurface,
                    sheetSurface = if (custom) surface else null, content = content)
                else BetterSheetHost(state, points,
                    containerColor = Color.Cyan, behavior = SheetBehavior(scrimEnabled = false), bodyMode = SheetBodyMode.Fill, edge = edge,
                    presentation = presentation, header = h, footer = f, sheetTransform = onSurface,
                    sheetSurface = if (custom) surface else null, content = content)
            }
        } } }
        await("medium")
        if (!gradient) assertPaint(name, custom, edge)
        if (custom) instrumentation.runOnMainSync { assertEquals("Material must be composed once", 1, materialInstances) }
    }

    private fun await(target: String) {
        val deadline = SystemClock.uptimeMillis() + 15000
        while (true) {
            var ready = false
            instrumentation.runOnMainSync {
                ready = ::state.isInitialized && state.currentSnapPoint == target && !state.isAnimationRunning && body.height > 0 && bounds.height > 0
            }
            if (ready) break
            check(SystemClock.uptimeMillis() < deadline) { "Surface did not settle at $target" }
            SystemClock.sleep(30)
        }
        SystemClock.sleep(200)
    }

    private fun shot(name: String): Bitmap {
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "surface-review").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return bitmap
    }

    private fun assertColor(message: String, pixel: Int, expected: Int) {
        for (shift in listOf(0, 8, 16)) assertTrue("$message: pixel=$pixel expected=$expected", kotlin.math.abs(((expected shr shift) and 255) - ((pixel shr shift) and 255)) <= 3)
    }

    private fun assertPaint(name: String, custom: Boolean, edge: SheetEdge) {
        val image = shot(name)
        try {
            val expected = if (custom) android.graphics.Color.RED else android.graphics.Color.CYAN
            assertColor("$name body paint", image.getPixel(body.center.x.toInt(), body.center.y.toInt()), expected)
            assertColor("$name caller header retained", image.getPixel(header.center.x.toInt(), header.center.y.toInt()), android.graphics.Color.BLUE)
            assertColor("$name caller footer retained", image.getPixel(footer.center.x.toInt(), footer.center.y.toInt()), android.graphics.Color.YELLOW)
            if (edge == SheetEdge.Bottom) {
                // Away from navigation gestures/icons; no magenta footer spill or cyan fallback is allowed.
                assertColor("$name navigation continuation", image.getPixel(image.width / 4, image.height - 8), expected)
            } else {
                assertColor("$name status-area paint", image.getPixel(image.width / 2, 8), expected)
                // The lower-left rounded corner lies outside the custom paint, even for a plain background modifier.
                val corner = image.getPixel((bounds.left + 2).toInt(), (bounds.bottom - 2).toInt())
                assertTrue("$name custom material ignored host shape", android.graphics.Color.green(corner) > 150)
            }
        } finally { image.recycle() }
    }

    @Test fun standardAndTopMaterialsKeepChromeAndNavigationPaint() {
        ActivityScenario.launch(MainActivity::class.java).use { activity ->
            for (custom in listOf(false, true)) {
                mount(activity, "host-bottom-$custom", custom = custom)
                instrumentation.runOnMainSync { scope.launch { state.expand() } }
                await("expanded"); assertPaint("host-bottom-expanded-$custom", custom, SheetEdge.Bottom)
                mount(activity, "host-top-$custom", edge = SheetEdge.Top, custom = custom)
            }
            mount(activity, "root-custom", root = true)
        }
    }

    @Test fun trayMaterialKeepsDefaultShadowAndProtectsNavigationFromFooterEffects() {
        ActivityScenario.launch(MainActivity::class.java).use { activity ->
            val tray = SheetPresentation.Tray(matchDeviceCorners = false)
            // At a floating detent the navigation bar belongs to the green app, not the sheet.
            // Use dedicated probes rather than assertPaint's standard-sheet navigation assertion.
            fun floating(custom: Boolean): Int {
                // Mount at expanded first so the shared assertions apply, then compare floating edges.
                instrumentation.runOnMainSync { body = Rect.Zero; bounds = Rect.Zero }
                activity.onActivity { it.setContent { key(custom) {
                    state = rememberSheetState("expanded"); scope = rememberCoroutineScope()
                    Box(Modifier.fillMaxSize().background(Color.Green)) {
                        BetterSheetHost(state, listOf(SheetSnapPoint.fraction("medium", .5f), SheetSnapPoint.expanded()),
                            bodyMode = SheetBodyMode.Fill, behavior = SheetBehavior(scrimEnabled = false), presentation = tray,
                            sheetTransform = Modifier.onGloballyPositioned { bounds = it.boundsInWindow() },
                            containerColor = Color.Red,
                            sheetSurface = if (custom) ({ _ -> Modifier.background(Color.Red) }) else null,
                            footer = { Box(Modifier.fillMaxWidth().height(64.dp).drawWithContent {
                                drawContent(); drawRect(Color.Magenta, Offset(0f, size.height), Size(size.width, 200f))
                            }) }) { Spacer(Modifier.fillMaxSize().onGloballyPositioned { body = it.boundsInWindow() }) }
                    }
                } } }
                await("expanded")
                val expanded = shot("tray-expanded-$custom")
                try { assertColor("Tray child effect entered navigation area", expanded.getPixel(expanded.width / 4, expanded.height - 8), android.graphics.Color.RED) }
                finally { expanded.recycle() }
                instrumentation.runOnMainSync { scope.launch { state.collapse() } }; await("medium")
                val image = shot("tray-floating-$custom")
                try {
                    val x = bounds.center.x.toInt(); val y = bounds.bottom.toInt() + 4
                    assertColor("Floating tray painted the app navigation bar", image.getPixel(image.width / 4, image.height - 8), android.graphics.Color.GREEN)
                    assertTrue("Square material escaped the rounded tray", android.graphics.Color.red(image.getPixel(bounds.left.toInt() + 2, bounds.top.toInt() + 2)) < 20)
                    return android.graphics.Color.green(image.getPixel(x, y))
                } finally { image.recycle() }
            }
            val solid = floating(false)
            val custom = floating(true)
            assertTrue("Custom material lost its default shadow: $custom", custom < 250)
            assertTrue("Custom and solid shadows differ: $solid vs $custom", kotlin.math.abs(solid - custom) <= 3)
        }
    }

    @Test fun oneMaterialContinuesBehindNavigationWithoutMovingCallerContent() {
        ActivityScenario.launch(MainActivity::class.java).use { activity ->
            mount(activity, "gradient-baseline", custom = false)
            val originalHeader = header; val originalBody = body; val originalFooter = footer
            mount(activity, "gradient-continuous", gradient = true)
            assertEquals(originalHeader, header)
            assertEquals(originalBody, body)
            assertEquals(originalFooter, footer)
            val image = shot("gradient-continuous")
            try {
                assertEquals("Material should reach the window bottom", image.height.toFloat(), materialBounds.bottom, 1f)
                val nearNavTop = android.graphics.Color.green(image.getPixel(image.width / 4, footer.bottom.toInt() + 4))
                val nearNavBottom = android.graphics.Color.green(image.getPixel(image.width / 4, image.height - 8))
                assertTrue("Material restarted at navigation boundary: $nearNavTop", nearNavTop > 220)
                assertTrue("Gradient did not continue to its endpoint: $nearNavBottom", nearNavBottom > 245)
                assertTrue("Navigation gradient has a seam", kotlin.math.abs(nearNavTop - nearNavBottom) < 25)
            } finally { image.recycle() }
        }
    }

    @Test fun materialKeepsKeyboardResizeRestorationAndHeaderDrag() {
        var imeBottom = 0
        lateinit var showKeyboard: () -> Unit
        lateinit var hideKeyboard: () -> Unit
        ActivityScenario.launch(MainActivity::class.java).use { activity ->
            activity.onActivity { it.setContent {
                state = rememberSheetState("medium"); scope = rememberCoroutineScope()
                val requester = remember { FocusRequester() }
                val keyboard = LocalSoftwareKeyboardController.current
                val focus = LocalFocusManager.current
                val ime = WindowInsets.ime.getBottom(LocalDensity.current)
                SideEffect {
                    imeBottom = ime
                    showKeyboard = { requester.requestFocus(); keyboard?.show() }
                    hideKeyboard = { focus.clearFocus(); keyboard?.hide() }
                }
                Box(Modifier.fillMaxSize().background(Color.Green)) {
                    BetterSheetHost(state, listOf(SheetSnapPoint.fraction("medium", .5f), SheetSnapPoint.expanded()),
                        bodyMode = SheetBodyMode.Fill,
                        behavior = SheetBehavior(scrimEnabled = false, keyboardBehavior = SheetKeyboardBehavior.Expand),
                        sheetSurface = { _ ->
                            remember { materialInstances++; true }
                            Modifier.background(Color.Red).onGloballyPositioned { materialBounds = it.boundsInWindow() }
                        },
                        sheetTransform = Modifier.onGloballyPositioned { bounds = it.boundsInWindow() },
                        header = { Box(Modifier.fillMaxWidth().height(48.dp).onGloballyPositioned { header = it.boundsInWindow() }) },
                        footer = { Box(Modifier.fillMaxWidth().height(64.dp).onGloballyPositioned { footer = it.boundsInWindow() }) },
                    ) {
                        Box(Modifier.fillMaxSize().onGloballyPositioned { body = it.boundsInWindow() }) {
                            BasicTextField("Note", {}, Modifier.focusRequester(requester))
                        }
                    }
                }
            } }
            fun until(message: String, ready: () -> Boolean) {
                val deadline = SystemClock.uptimeMillis() + 15000
                while (true) {
                    var done = false
                    instrumentation.runOnMainSync { done = ready() }
                    if (done) break
                    check(SystemClock.uptimeMillis() < deadline) { message }
                    SystemClock.sleep(30)
                }
                SystemClock.sleep(150)
            }
            await("medium")
            val originalFooter = footer
            instrumentation.runOnMainSync { showKeyboard() }
            until("Material did not resize with keyboard") { imeBottom > 100 && state.currentSnapPoint == "expanded" && !state.isAnimationRunning }
            val keyboardShot = shot("material-keyboard")
            try {
                assertEquals("Material extends under IME", (keyboardShot.height - imeBottom).toFloat(), materialBounds.bottom, 2f)
                assertTrue("Footer covered by IME", footer.bottom <= materialBounds.bottom + 1f)
            } finally { keyboardShot.recycle() }
            instrumentation.runOnMainSync { hideKeyboard() }
            until("Keyboard did not restore the detent") { imeBottom < 100 && state.currentSnapPoint == "medium" && !state.isAnimationRunning }
            assertEquals(originalFooter, footer)
            val x = header.center.x.toInt(); val start = header.center.y.toInt()
            // Use Android's input dispatcher with a full-height swipe, avoiding a density-
            // dependent release between detents on small emulator windows.
            instrumentation.uiAutomation.executeShellCommand("input swipe $x $start $x 80 650").use { descriptor ->
                java.io.FileInputStream(descriptor.fileDescriptor).use { it.readBytes() }
            }
            await("expanded")
            assertEquals("Material remounted across keyboard/drag", 1, materialInstances)
        }
    }

    @Test fun modalForwardsTheSurfaceForBothEdges() {
        ActivityScenario.launch(MainActivity::class.java).use { activity ->
            mount(activity, "modal-bottom", modal = true, stacked = true)
            assertTrue("Stack modifier no longer scales custom material", body.left > 50f)
            mount(activity, "modal-top", modal = true, edge = SheetEdge.Top)
        }
    }
}
