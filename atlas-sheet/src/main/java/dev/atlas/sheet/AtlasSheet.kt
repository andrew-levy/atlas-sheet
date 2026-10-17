package dev.atlas.sheet

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.view.Window
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.compose.animation.core.Animatable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableDefaults
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import kotlin.math.roundToInt

/**
 * State-driven modal. Keep mounted and use state.show()/hide(); the Dialog exists only while
 * presenting. onDismissed runs after the exit completes. Share stack for coordinated effects.
 */
@Composable
public fun AtlasModalSheet(
    state: SheetState,
    snapPoints: List<SheetSnapPoint>,
    onDismissed: () -> Unit = {},
    modifier: Modifier = Modifier,
    behavior: SheetBehavior = SheetBehavior(),
    bodyMode: SheetBodyMode = SheetBodyMode.Scroll,
    title: String = "Bottom sheet",
    maxWidth: Dp = 640.dp,
    topMargin: Dp = 24.dp,
    containerColor: Color = Color.White,
    contentColor: Color = Color(0xFF172033),
    scrimColor: Color = Color.Black.copy(alpha = 0.42f),
    shape: Shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    dragHandle: @Composable (() -> Unit)? = { SheetDragHandle() },
    header: @Composable (() -> Unit)? = null,
    footer: @Composable (() -> Unit)? = null,
    surfaceModifier: Modifier = Modifier,
    stackState: SheetStackState? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    SideEffect { state.prepareModalPresentation() }
    if (!state.modalPresentationRequested) return
    val entry = remember(stackState, state) { stackState?.let { SheetStackEntry(it, state) } }
    DisposableEffect(stackState, state) {
        stackState?.register(state)
        onDispose { stackState?.unregister(state) }
    }
    val scope = rememberCoroutineScope()
    val dismissCallback by rememberUpdatedState(onDismissed)
    Dialog(
        onDismissRequest = { if (behavior.dismissOnBack) scope.launch { state.hide() } },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnBackPress = behavior.dismissOnBack,
            dismissOnClickOutside = false,
        ),
    ) {
        val view = LocalView.current
        val window = (view.parent as? DialogWindowProvider)?.window
        val activity = remember(view) { view.context.sheetHostActivity() }
        SideEffect {
            // Compose owns the scrim, including the system-bar areas. A second window dim
            // or contrast overlay would make those bars darker than the content behind them.
            window?.clearSheetSystemBarChrome()
            if (window != null && activity != null) {
                val host = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = host.isAppearanceLightStatusBars
                    isAppearanceLightNavigationBars = host.isAppearanceLightNavigationBars
                }
            }
            // DialogProperties selects Resize on API 30 and below and Nothing on API 31+.
            // Preserving that choice is required for legacy IME inset delivery.
        }
        LaunchedEffect(state) {
            if (state.initialPresentationPending) {
                state.initialPresentationPending = false
                try { state.enter() } catch (interrupted: CancellationException) { coroutineContext.ensureActive() }
            }
        }
        LaunchedEffect(state) {
            snapshotFlow {
                state.hasPresented && !state.modalAwaitingPresentation && state.targetSnapPoint == null && !state.isVisible &&
                    !state.isAnimationRunning && !state.dragInProgress
            }.first { it }
            state.finishModalPresentation()
            dismissCallback()
        }
        CompositionLocalProvider(LocalSheetStackEntry provides entry) {
            AtlasSheetHost(
                state, snapPoints, modifier, behavior, bodyMode, title, maxWidth, topMargin,
                containerColor, contentColor, scrimColor, shape, dragHandle, header, footer, surfaceModifier, content,
            )
        }
    }
}

/** Inline overlay. Put last in a full-size Box. Scrim color controls drawing only; SheetBehavior controls outside touches. */
@Composable
public fun AtlasSheetHost(
    state: SheetState,
    snapPoints: List<SheetSnapPoint>,
    modifier: Modifier = Modifier,
    behavior: SheetBehavior = SheetBehavior(),
    bodyMode: SheetBodyMode = SheetBodyMode.Scroll,
    title: String = "Bottom sheet",
    maxWidth: Dp = 640.dp,
    topMargin: Dp = 24.dp,
    containerColor: Color = Color.White,
    contentColor: Color = Color(0xFF172033),
    scrimColor: Color = Color.Black.copy(alpha = 0.42f),
    shape: Shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    dragHandle: @Composable (() -> Unit)? = { SheetDragHandle() },
    header: @Composable (() -> Unit)? = null,
    footer: @Composable (() -> Unit)? = null,
    surfaceModifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    require(snapPoints.isNotEmpty() && snapPoints.map { it.id }.distinct().size == snapPoints.size)
    require(bodyMode != SheetBodyMode.Fill || snapPoints.none { it.requiresContentMeasurement }) {
        "Content snap points require SheetBodyMode.Scroll. Use fixed/fraction/custom snap points for lazy content."
    }
    require(maxWidth.value.isFinite() && maxWidth > 0.dp)
    require(topMargin.value.isFinite() && topMargin >= 0.dp)
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    var headerHeight by remember { mutableIntStateOf(0) }
    var footerHeight by remember { mutableIntStateOf(0) }
    var naturalBodyHeight by remember { mutableIntStateOf(0) }
    var viewportHeight by remember { mutableIntStateOf(0) }
    var headerMeasured by remember { mutableStateOf(false) }
    var footerMeasured by remember { mutableStateOf(false) }
    var bodyMeasured by remember { mutableStateOf(false) }
    val animatedContentHeight = remember { Animatable(0f) }
    var contentInitialized by remember { mutableStateOf(false) }
    LaunchedEffect(naturalBodyHeight, bodyMeasured) {
        if (bodyMeasured) {
            if (!contentInitialized || !state.hasPresented) {
                animatedContentHeight.snapTo(naturalBodyHeight.toFloat())
                contentInitialized = true
            } else animatedContentHeight.animateTo(naturalBodyHeight.toFloat(), state.animationSpec)
        }
    }
    val isVisible by remember(state) { derivedStateOf { state.isVisible } }
    val topInset = maxOf(WindowInsets.systemBars.getTop(density), WindowInsets.displayCutout.getTop(density))
    val navInset = WindowInsets.systemBars.getBottom(density)
    val imeInset = WindowInsets.ime.getBottom(density)
    val imeVisible = imeInset > navInset
    val bottomInset = if (behavior.keyboardBehavior == SheetKeyboardBehavior.Ignore) navInset else maxOf(navInset, imeInset)
    val fling = AnchoredDraggableDefaults.flingBehavior(state.anchored, animationSpec = state.animationSpec)
    val velocityThreshold = with(density) { 125.dp.toPx() }
    val backgroundTouchSlop = with(density) { 24.dp.toPx() }
    val releaseBackground by remember(state, backgroundTouchSlop) {
        derivedStateOf {
            val g = state.geometry ?: return@derivedStateOf false
            releasesBackgroundPointer(
                targetHidden = state.hasPresented && state.targetSnapPoint == null,
                dragInProgress = state.dragInProgress,
                visibleHeight = (g.availableHeight - state.offsetOrHidden()).coerceAtLeast(0f),
                slop = backgroundTouchSlop,
            )
        }
    }
    val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
    SideEffect {
        val window = dialogWindow ?: return@SideEffect
        if (releaseBackground) window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
    }
    val handoffThreshold = with(density) { behavior.scrollFlingThreshold.dp.toPx() }
    val nested = remember(state, behavior, velocityThreshold, handoffThreshold) {
        SheetNestedScroll(state, velocityThreshold, behavior.bubbleAtTop, behavior.bubbleAtBottom,
            behavior.transferScrollFling, handoffThreshold, behavior.scrollFlingVelocityScale)
    }
    val edgeOverscroll = remember(state, velocityThreshold) { SheetEdgeOverscroll(state, velocityThreshold) }
    var beforeIme by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(imeVisible, behavior.keyboardBehavior) {
        if (behavior.keyboardBehavior == SheetKeyboardBehavior.Expand) {
            if (imeVisible && beforeIme == null) {
                beforeIme = state.targetSnapPoint ?: state.currentSnapPoint
                state.expand()
            } else if (!imeVisible) {
                beforeIme?.let { id ->
                    state.geometry?.let { g -> if (g.canonical(id) in g.offsets && state.isVisible) state.animateTo(id) }
                }
                beforeIme = null
            }
        } else beforeIme = null
    }
    BackHandler(enabled = isVisible && behavior.dismissOnBack) {
        if (imeVisible) keyboard?.hide() else scope.launch { state.hide() }
    }

    Box(modifier.fillMaxSize().onSizeChanged { viewportHeight = it.height }) {
        val availablePx = viewportHeight - with(density) { topMargin.toPx() } - topInset - bottomInset
        if (availablePx <= 0f) return@Box
        val chrome = headerHeight + footerHeight
        val snapPointScope = with(density) {
            SheetSnapPointScope(availablePx.toDp(), animatedContentHeight.value.toDp(), chrome.toDp())
        }
        val geometry = SheetGeometry.resolve(availablePx, chrome.toFloat(), snapPoints.map { d ->
            d.id to with(density) { d.resolve(snapPointScope).toPx() }
        })
        val layoutReady = headerMeasured && footerMeasured &&
            (bodyMode == SheetBodyMode.Fill || contentInitialized)
        SideEffect { state.updateGeometry(geometry, behavior.dismissOnDrag, layoutReady) }

        if (isVisible && behavior.scrimEnabled) {
            // Enabled scrims intercept touches even when their color draws nothing.
            val blocksBackground = !releaseBackground
            Box(Modifier.fillMaxSize()
                .drawBehind {
                    if (scrimColor != Color.Unspecified) {
                        val visible = geometry.availableHeight - state.offsetOrHidden()
                        val smallestHeight = geometry.availableHeight - geometry.offsets.getValue(geometry.smallest)
                        val fraction = scrimDragFraction(visible, smallestHeight, behavior.scrimFollowsDrag)
                        drawRect(scrimColor.copy(alpha = scrimColor.alpha * fraction))
                    }
                }
                .then(when {
                    blocksBackground && behavior.dismissOnScrim ->
                        Modifier.clickable(onClickLabel = "Dismiss $title") { scope.launch { state.hide() } }
                    blocksBackground -> Modifier.pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                awaitPointerEvent().changes.forEach { it.consume() }
                            }
                        }
                    }
                    else -> Modifier
                })
                .semantics {
                    if (blocksBackground && behavior.dismissOnScrim) contentDescription = "Dismiss $title"
                })
        }
        val keyboardCoversNav = behavior.keyboardBehavior != SheetKeyboardBehavior.Ignore && imeInset > navInset
        if (isVisible && navInset > 0 && !keyboardCoversNav) {
            // The sheet sits on the bottom edge. Continue its color under the navigation
            // bar so the bar does not pick up a separate, darker system scrim.
            Box(Modifier.align(Alignment.BottomCenter)
                .downwardRubber(state)
                .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal))
                .widthIn(max = maxWidth).fillMaxWidth()
                .height(with(density) { navInset.toDp() })
                .background(containerColor))
        }
        val sheetSurfaceModifier = Modifier
            .align(Alignment.BottomCenter)
            .downwardRubber(state)
            .padding(bottom = with(density) { bottomInset.toDp() })
            .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal))
            .widthIn(max = maxWidth).fillMaxWidth()
            .then(surfaceModifier)
            .clip(shape)

        Layout(modifier = sheetSurfaceModifier, content = {
            Layout(modifier = Modifier.fillMaxWidth()
                .shadow(16.dp, shape).background(containerColor, shape)
                .then(if (behavior.draggable) Modifier.nestedScroll(nested).pointerInput(nested) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        nested.beginGesture()
                    }
                } else Modifier)
                .semantics {
                    if (!isVisible) hideFromAccessibility()
                    paneTitle = title
                    isTraversalGroup = true
                    stateDescription = state.currentSnapPoint ?: "Hidden"
                    if (behavior.dismissOnDrag) dismiss { scope.launch { state.hide() }; true }
                    expand { scope.launch { state.expand() }; true }
                    collapse { scope.launch { state.collapse() }; true }
                }, content = {
                // Foundation only: no Material theme is imposed on consumers.
                CompositionLocalProvider(LocalSheetContentColor provides contentColor) {
                    Column(Modifier.fillMaxWidth().onSizeChanged { headerHeight = it.height; headerMeasured = true }
                        .anchoredDraggable(
                            state.anchored,
                            Orientation.Vertical,
                            enabled = behavior.draggable,
                            overscrollEffect = edgeOverscroll,
                            flingBehavior = fling,
                        )) {
                        dragHandle?.invoke()
                        header?.invoke()
                    }
                    if (bodyMode == SheetBodyMode.Scroll) {
                        val scroll = rememberScrollState()
                        Box(Modifier.fillMaxWidth().clipToBoundsForSheet().verticalScroll(scroll)) {
                            Column(Modifier.fillMaxWidth().onSizeChanged { naturalBodyHeight = it.height; bodyMeasured = true }, content = content)
                        }
                    } else {
                        Column(Modifier.fillMaxWidth().clipToBoundsForSheet(), content = content)
                    }
                    Box(Modifier.fillMaxWidth().onSizeChanged { footerHeight = it.height; footerMeasured = true }) { footer?.invoke() }
                }
            }) { parts, bounds ->
                // A small window or tall header must not consume a fitting footer's space.
                // Upward stretch grows this height from the top. The footer stays on the bottom edge.
                val width = bounds.maxWidth
                val height = bounds.maxHeight
                val footerPart = parts[2].measure(Constraints(minWidth = width, maxWidth = width, maxHeight = height))
                val headerPart = parts[0].measure(Constraints(minWidth = width, maxWidth = width, maxHeight = height - footerPart.height))
                val bodyHeight = (height - footerPart.height - headerPart.height).coerceAtLeast(0)
                val bodyPart = parts[1].measure(Constraints.fixed(width, bodyHeight))
                layout(width, height) {
                    headerPart.place(0, 0)
                    bodyPart.place(0, headerPart.height)
                    footerPart.place(0, height - footerPart.height)
                }
            }
        }) { measurables, constraints ->
            // Offset reads happen in measurement, not composition, on each drag/animation frame.
            val visibleHeight = (availablePx - state.offsetOrHidden()).coerceIn(0f, availablePx)
            val stretch = upwardStretch(state.rubberTranslation)
            val base = if (chrome == 0) availablePx else visibleHeight.coerceAtLeast(chrome.toFloat().coerceAtMost(availablePx))
            val child = measurables.single().measure(Constraints.fixed(constraints.maxWidth, (base + stretch).roundToInt()))
            layout(constraints.maxWidth, (visibleHeight + stretch).roundToInt()) { child.place(0, 0) }
        }
    }
}

/** Optional color for Foundation text; Material content can use its own LocalContentColor. */
public val LocalSheetContentColor: ProvidableCompositionLocal<Color> = staticCompositionLocalOf { Color(0xFF172033) }

@Composable
public fun SheetDragHandle(modifier: Modifier = Modifier, color: Color = Color(0xFFB8BFCC)) {
    Box(modifier.fillMaxWidth().height(36.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(36.dp, 4.dp).background(color, RoundedCornerShape(2.dp)))
    }
}

private fun Modifier.clipToBoundsForSheet(): Modifier = this.clip(androidx.compose.ui.graphics.RectangleShape)

private fun Modifier.downwardRubber(state: SheetState): Modifier =
    offset { IntOffset(0, downwardShift(state.rubberTranslation).roundToInt()) }

private tailrec fun Context.sheetHostActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.sheetHostActivity()
    else -> null
}

@Suppress("DEPRECATION")
private fun Window.clearSheetSystemBarChrome() {
    clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
    WindowCompat.setDecorFitsSystemWindows(this, false)
    statusBarColor = android.graphics.Color.TRANSPARENT
    navigationBarColor = android.graphics.Color.TRANSPARENT
    if (Build.VERSION.SDK_INT >= 28) {
        attributes = attributes.apply {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
    }
    if (Build.VERSION.SDK_INT >= 29) {
        isStatusBarContrastEnforced = false
        isNavigationBarContrastEnforced = false
    }
}
