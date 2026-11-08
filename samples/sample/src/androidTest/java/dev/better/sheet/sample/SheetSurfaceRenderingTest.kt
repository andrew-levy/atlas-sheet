package dev.better.sheet.sample

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
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
    private var regions = mutableSetOf<SheetSurfaceRegion>()

    private fun mount(
        activity: ActivityScenario<MainActivity>, name: String,
        edge: SheetEdge = SheetEdge.Bottom,
        presentation: SheetPresentation = SheetPresentation.Standard,
        modal: Boolean = false, root: Boolean = false,
        custom: Boolean = true, shadow: SheetSurfaceShadow = SheetSurfaceShadow.Default,
    ) {
        instrumentation.runOnMainSync { bounds = Rect.Zero; body = Rect.Zero; header = Rect.Zero; footer = Rect.Zero; regions.clear() }
        activity.onActivity { it.setContent { key(name) {
            state = rememberSheetState("medium")
            scope = rememberCoroutineScope()
            val material = remember {
                SheetSurface(shadow) {
                    val expectedEdge = edge
                    check(this.edge == expectedEdge)
                    SideEffect { regions.add(region) }
                    Modifier.background(Color.Red)
                }
            }
            Box(Modifier.fillMaxSize().background(Color.Green)) {
                val onSurface = Modifier.onGloballyPositioned { bounds = it.boundsInWindow() }
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
                if (modal) BetterModalSheet(state, points,
                    containerColor = Color.Cyan, behavior = SheetBehavior(scrimEnabled = false), bodyMode = SheetBodyMode.Fill, edge = edge,
                    presentation = presentation, header = h, footer = f, surfaceModifier = onSurface,
                    surface = if (custom) material else null, content = content)
                else if (root) BetterSheet(state, points,
                    containerColor = Color.Cyan, behavior = SheetBehavior(scrimEnabled = false), bodyMode = SheetBodyMode.Fill,
                    presentation = presentation, header = h, footer = f, surfaceModifier = onSurface,
                    surface = if (custom) material else null, content = content)
                else BetterSheetHost(state, points,
                    containerColor = Color.Cyan, behavior = SheetBehavior(scrimEnabled = false), bodyMode = SheetBodyMode.Fill, edge = edge,
                    presentation = presentation, header = h, footer = f, surfaceModifier = onSurface,
                    surface = if (custom) material else null, content = content)
            }
        } } }
        await("medium")
        assertPaint(name, custom, edge)
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
                if (custom) instrumentation.runOnMainSync { assertTrue(regions.contains(SheetSurfaceRegion.NavigationBar)) }
                instrumentation.runOnMainSync { scope.launch { state.expand() } }
                await("expanded"); assertPaint("host-bottom-expanded-$custom", custom, SheetEdge.Bottom)
                mount(activity, "host-top-$custom", edge = SheetEdge.Top, custom = custom)
                if (custom) instrumentation.runOnMainSync { assertEquals(setOf(SheetSurfaceRegion.Body), regions) }
            }
            mount(activity, "root-custom", root = true)
        }
    }

    @Test fun trayMaterialKeepsDefaultShadowAndProtectsNavigationFromFooterEffects() {
        ActivityScenario.launch(MainActivity::class.java).use { activity ->
            val tray = SheetPresentation.Tray(matchDeviceCorners = false)
            // At a floating detent the navigation bar belongs to the green app, not the sheet.
            // Use dedicated probes rather than assertPaint's standard-sheet navigation assertion.
            fun floating(shadow: SheetSurfaceShadow): Int {
                // Mount at expanded first so the shared assertions apply, then compare floating edges.
                instrumentation.runOnMainSync { body = Rect.Zero; bounds = Rect.Zero }
                activity.onActivity { it.setContent { key(shadow) {
                    state = rememberSheetState("expanded"); scope = rememberCoroutineScope()
                    Box(Modifier.fillMaxSize().background(Color.Green)) {
                        BetterSheetHost(state, listOf(SheetSnapPoint.fraction("medium", .5f), SheetSnapPoint.expanded()),
                            bodyMode = SheetBodyMode.Fill, behavior = SheetBehavior(scrimEnabled = false), presentation = tray,
                            surfaceModifier = Modifier.onGloballyPositioned { bounds = it.boundsInWindow() },
                            surface = SheetSurface(shadow) { Modifier.background(Color.Red) },
                            footer = { Box(Modifier.fillMaxWidth().height(64.dp).drawWithContent {
                                drawContent(); drawRect(Color.Magenta, Offset(0f, size.height), Size(size.width, 200f))
                            }) }) { Spacer(Modifier.fillMaxSize().onGloballyPositioned { body = it.boundsInWindow() }) }
                    }
                } } }
                await("expanded")
                val expanded = shot("tray-expanded-$shadow")
                try { assertColor("Tray child effect entered navigation area", expanded.getPixel(expanded.width / 4, expanded.height - 8), android.graphics.Color.RED) }
                finally { expanded.recycle() }
                instrumentation.runOnMainSync { scope.launch { state.collapse() } }; await("medium")
                val image = shot("tray-floating-$shadow")
                try {
                    val x = bounds.center.x.toInt(); val y = bounds.bottom.toInt() + 4
                    assertColor("Floating tray painted the app navigation bar", image.getPixel(image.width / 4, image.height - 8), android.graphics.Color.GREEN)
                    assertTrue("Square material escaped the rounded tray", android.graphics.Color.red(image.getPixel(bounds.left.toInt() + 2, bounds.top.toInt() + 2)) < 20)
                    return android.graphics.Color.green(image.getPixel(x, y))
                } finally { image.recycle() }
            }
            val withShadow = floating(SheetSurfaceShadow.Default)
            val withoutShadow = floating(SheetSurfaceShadow.None)
            assertTrue("Custom material silently lost its default shadow: $withShadow vs $withoutShadow", withShadow + 2 < withoutShadow)
        }
    }

    @Test fun modalForwardsTheSurfaceForBothEdges() {
        ActivityScenario.launch(MainActivity::class.java).use { activity ->
            mount(activity, "modal-bottom", modal = true)
            mount(activity, "modal-top", modal = true, edge = SheetEdge.Top)
        }
    }
}
