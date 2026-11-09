package dev.better.sheet

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.AnimationSpec
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
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
import kotlin.math.roundToInt

/** The window edge a standard sheet opens from. */
public enum class SheetEdge { Bottom, Top }

/**
 * State-driven modal. Keep mounted and use state.show()/hide(); the Dialog exists only while
 * presenting. onDismissed runs after the exit completes. Share stack for coordinated effects.
 */
@Composable
public fun BetterModalSheet(
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
    sheetTransform: Modifier = Modifier,
    stackState: SheetStackState? = null,
    presentation: SheetPresentation = SheetPresentation.Standard,
    edge: SheetEdge = SheetEdge.Bottom,
    enterTransition: EnterTransition = slideInVertically(defaultSheetSpring(IntOffset(1, 1))) { if (edge == SheetEdge.Top) -it else it },
    exitTransition: ExitTransition = slideOutVertically(defaultSheetSpring(IntOffset(1, 1))) { if (edge == SheetEdge.Top) -it else it },
    snapAnimationSpec: AnimationSpec<Float> = defaultSheetSpring(),
    sheetMaterial: (@Composable (outline: Shape) -> Unit)? = null,
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
                    isAppearanceLightStatusBars = if (edge == SheetEdge.Top) containerColor.luminance() > 0.5f
                    else host.isAppearanceLightStatusBars
                    isAppearanceLightNavigationBars = host.isAppearanceLightNavigationBars
                }
            }
            // DialogProperties selects Resize on API 30 and below and Nothing on API 31+.
            // Preserving that choice is required for legacy IME inset delivery.
        }
        LaunchedEffect(state) {
            snapshotFlow {
                state.hasPresented && !state.modalAwaitingPresentation && state.targetSnapPoint == null && state.currentSnapPoint == null && !state.isVisible &&
                    !state.isAnimationRunning && !state.dragInProgress
            }.first { it }
            state.finishModalPresentation()
            dismissCallback()
        }
        CompositionLocalProvider(LocalSheetStackEntry provides entry) {
            BetterSheetHost(
                state, snapPoints, modifier, behavior, bodyMode, title, maxWidth, topMargin,
                containerColor, contentColor, scrimColor, shape, dragHandle, header, footer, sheetTransform, presentation, edge, enterTransition, exitTransition, snapAnimationSpec, sheetMaterial, content,
            )
        }
    }
}

/**
 * Inline overlay. Put last in a full-size Box. Scrim color controls drawing only;
 * SheetBehavior controls outside touches. [sheetMaterial] is a decorative composable layer
 * behind caller content, receiving the resolved sheet shape. The host owns its size, clipping,
 * default shadow and navigation-area extension; see docs/SURFACES.md for the contract.
 * [sheetTransform] decorates/transforms the whole sheet without changing its full-window scrim.
 */
@Composable
public fun BetterSheetHost(
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
    sheetTransform: Modifier = Modifier,
    presentation: SheetPresentation = SheetPresentation.Standard,
    edge: SheetEdge = SheetEdge.Bottom,
    enterTransition: EnterTransition = slideInVertically(defaultSheetSpring(IntOffset(1, 1))) { if (edge == SheetEdge.Top) -it else it },
    exitTransition: ExitTransition = slideOutVertically(defaultSheetSpring(IntOffset(1, 1))) { if (edge == SheetEdge.Top) -it else it },
    snapAnimationSpec: AnimationSpec<Float> = defaultSheetSpring(),
    sheetMaterial: (@Composable (outline: Shape) -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    require(snapPoints.isNotEmpty() && snapPoints.map { it.id }.distinct().size == snapPoints.size)
    require(bodyMode != SheetBodyMode.Fill || snapPoints.none { it.requiresContentMeasurement }) {
        "Content snap points require SheetBodyMode.Scroll. Use fixed/fraction/custom snap points for lazy content."
    }
    val opensFromTop = edge == SheetEdge.Top
    require(!opensFromTop || presentation == SheetPresentation.Standard) {
        "Top sheets support standard presentation."
    }
    val edgeAlignment = if (opensFromTop) Alignment.TopCenter else Alignment.BottomCenter
    require(maxWidth.value.isFinite() && maxWidth > 0.dp)
    require(topMargin.value.isFinite() && topMargin >= 0.dp)
    SideEffect {
        state.preparePresentation()
        state.configureAnimations(snapAnimationSpec)
    }
    LaunchedEffect(state) {
        if (state.initialPresentationPending) {
            state.initialPresentationPending = false
            state.enter()
        }
    }
    val visibilityScope = rememberSheetVisibilityScope(state)
    val deviceCornerRadius = rememberSheetDeviceCornerRadius()
    val hostView = LocalView.current
    var hostReachesWindowEdges by remember { mutableStateOf(false) }
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
            } else animatedContentHeight.animateTo(naturalBodyHeight.toFloat(), defaultSheetSpring())
        }
    }
    val isVisible by remember(state) { derivedStateOf { state.isVisible } }
    val topInset = maxOf(WindowInsets.systemBars.getTop(density), WindowInsets.displayCutout.getTop(density))
    val navInset = WindowInsets.systemBars.getBottom(density)
    val imeInset = WindowInsets.ime.getBottom(density)
    val imeVisible = imeInset > navInset
    val bottomInset = if (behavior.keyboardBehavior == SheetKeyboardBehavior.Ignore) navInset else maxOf(navInset, imeInset)
    val fling = AnchoredDraggableDefaults.flingBehavior(state.anchored, animationSpec = snapAnimationSpec)
    val velocityThreshold = with(density) { 125.dp.toPx() }
    val backgroundTouchSlop = with(density) { 24.dp.toPx() }
    val releaseBackground by remember(state, backgroundTouchSlop) {
        derivedStateOf {
            val g = state.geometry ?: return@derivedStateOf false
            // The anchor stays at its snap point during an exit transition. Waiting for
            // that anchor (or a spring's long tail) leaves an invisible touch blocker.
            !state.presentationActive || (state.presentationExiting &&
                !state.dragInProgress && state.presentationFraction <= 0.01f) ||
                (!state.presentationExiting && releasesBackgroundPointer(
                    targetHidden = state.hasPresented && state.targetSnapPoint == null,
                    dragInProgress = state.dragInProgress,
                    visibleHeight = (g.availableHeight - state.offsetOrHidden()).coerceAtLeast(0f),
                    slop = backgroundTouchSlop,
                ))
        }
    }
    val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
    SideEffect {
        val window = dialogWindow ?: return@SideEffect
        if (releaseBackground) window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
    }
    val handoffThreshold = with(density) { behavior.scrollFlingThreshold.dp.toPx() }
    val nested = remember(state, behavior, velocityThreshold, handoffThreshold, opensFromTop) {
        SheetNestedScroll(state, velocityThreshold, behavior.bubbleAtTop, behavior.bubbleAtBottom,
            behavior.transferScrollFling, handoffThreshold, behavior.scrollFlingVelocityScale, reverseDirection = opensFromTop)
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
                    state.geometry?.let { g -> if (g.canonical(id) in g.offsets && state.isVisible) state.snapTo(id) }
                }
                beforeIme = null
            }
        } else beforeIme = null
    }
    BackHandler(enabled = isVisible && behavior.dismissOnBack) {
        if (imeVisible) keyboard?.hide() else scope.launch { state.hide() }
    }

    Box(modifier.fillMaxSize().onSizeChanged { viewportHeight = it.height }.onGloballyPositioned {
        val bounds = it.boundsInWindow()
        val root = hostView.rootView
        hostReachesWindowEdges = bounds.left <= 1f && bounds.right >= root.width - 1f &&
            bounds.bottom >= root.height - 1f && bounds.width <= with(density) { maxWidth.toPx() } + 1f
    }) {
        val availablePx = viewportHeight - with(density) { topMargin.toPx() } - topInset - bottomInset
        if (availablePx <= 0f) return@Box
        val tray = presentation as? SheetPresentation.Tray
        val chrome = headerHeight + footerHeight
        val trayChrome = chrome + with(density) { (tray?.bottomInset ?: 0.dp).roundToPx() }
        val snapPointScope = with(density) {
            SheetSnapPointScope(availablePx.toDp(), animatedContentHeight.value.toDp(), trayChrome.toDp())
        }
        val geometry = SheetGeometry.resolve(availablePx, trayChrome.toFloat(), snapPoints.map { d ->
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
                        drawRect(scrimColor.copy(alpha = scrimColor.alpha * fraction * state.presentationFraction))
                    }
                }
                .then(when {
                    blocksBackground && behavior.dismissOnScrim && !state.presentationExiting ->
                        Modifier.clickable(
                            interactionSource = null,
                            indication = null,
                            onClickLabel = "Dismiss $title",
                        ) { scope.launch { state.hide() } }
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
                    if (blocksBackground && behavior.dismissOnScrim && !state.presentationExiting) {
                        contentDescription = "Dismiss $title"
                    }
                })
        }
        val keyboardCoversNav = behavior.keyboardBehavior != SheetKeyboardBehavior.Ignore && imeInset > navInset
        val morph by remember(state, tray, availablePx) {
            derivedStateOf {
                if (tray == null) 0f else trayMorphFraction(
                    availablePx - state.layoutOffset(), availablePx, tray.morphStartFraction,
                )
            }
        }
        val traySide = (tray?.horizontalInset ?: 0.dp) * (1f - morph)
        val trayGap = (tray?.bottomInset ?: 0.dp) * (1f - morph)
        // Move the surface into the navigation area while reserving the same space inside it.
        val navExtension = if (tray != null && !keyboardCoversNav) navInset * morph else 0f
        val radius = tray?.let {
            val reported = with(density) { deviceCornerRadius.toDp() } - it.horizontalInset
            if (it.matchDeviceCorners && hostReachesWindowEdges && deviceCornerRadius > 0f) reported.coerceAtLeast(0.dp) else it.cornerRadius
        } ?: 0.dp
        // Preserve the existing default bottom-sheet shape for callers. Only the
        // implicit/default shape mirrors to the lower corners for top sheets.
        val defaultBottomShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        val resolvedShape = if (opensFromTop && shape == defaultBottomShape) {
            RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)
        } else shape
        val surfaceShape = if (tray == null) resolvedShape else RoundedCornerShape(
            topStart = radius * (1f - morph) + tray.expandedCornerRadius * morph,
            topEnd = radius * (1f - morph) + tray.expandedCornerRadius * morph,
            bottomStart = radius * (1f - morph), bottomEnd = radius * (1f - morph),
        )
        // Custom materials are composed once, including the standard bottom navigation area.
        // Compensate this extra paint height in outer padding so content/detents do not move.
        val materialNavigation = if (sheetMaterial != null && !opensFromTop && tray == null &&
            (isVisible || state.presentationExiting) && !keyboardCoversNav) navInset else 0
        // Animate the surface and its system bar background together.
        // The body remains composed and measured while hidden, preserving content state.
        Box(Modifier.align(edgeAlignment).then(with(visibilityScope) {
            Modifier.animateEnterExit(enterTransition, exitTransition, "Sheet presentation")
        }).sheetPresentationPlacement(state)) {
            val barInset = if (opensFromTop) topInset else navInset
            if (sheetMaterial == null && !opensFromTop && tray == null && (isVisible || state.presentationExiting) && barInset > 0 &&
                !keyboardCoversNav
            ) {
                // Continue the surface color behind the system bar at the opening edge.
                Box(Modifier.align(edgeAlignment)
                    .edgeRubber(state, opensFromTop)
                    .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal))
                    .widthIn(max = maxWidth).fillMaxWidth()
                    .height(with(density) { barInset.toDp() })
                    .background(containerColor))
            }
            val sheetSurfaceModifier = Modifier
                .align(edgeAlignment)
                .edgeRubber(state, opensFromTop)
                .padding(
                    top = 0.dp,
                    bottom = with(density) { if (opensFromTop) 0.dp else (bottomInset - navExtension - materialNavigation).toDp() } + trayGap,
                )
                .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal))
                .widthIn(max = maxWidth).fillMaxWidth()
                .padding(horizontal = traySide)
                .then(sheetTransform)
                .sheetSurfaceShadow(
                    if (opensFromTop) 0.dp else 16.dp,
                    surfaceShape,
                    floating = tray != null,
                )
                .sheetDrawClip { surfaceShape }
                .then(if (sheetMaterial == null) Modifier.sheetNavigationCover(containerColor, navExtension) else Modifier)

            Layout(modifier = sheetSurfaceModifier, content = {
                if (sheetMaterial != null) {
                    // A slot may emit any number of nodes; this layer keeps it to one measurable
                    // and gives its children the host's fixed painting bounds.
                    Box(Modifier.fillMaxSize(), propagateMinConstraints = true) { sheetMaterial(surfaceShape) }
                }
                Layout(modifier = Modifier.fillMaxWidth()
                    .then(if (sheetMaterial == null) Modifier.background(containerColor)
                        else Modifier.sheetContentAboveNavigation(navExtension))
                    // The status bar is inside the same clipped sheet surface, not a separate cap.
                    .padding(
                        top = with(density) { if (opensFromTop) topInset.toDp() else 0.dp },
                        bottom = with(density) { navExtension.toDp() },
                    )
                    .then(if (behavior.draggable) Modifier.nestedScroll(nested).pointerInput(nested) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            state.catchExitingPresentation()
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
                        val dragModifier = Modifier.anchoredDraggable(
                            state.anchored,
                            orientation = Orientation.Vertical,
                            reverseDirection = opensFromTop,
                            enabled = behavior.draggable,
                            overscrollEffect = edgeOverscroll,
                            flingBehavior = fling,
                        )
                        Column(Modifier.fillMaxWidth().onSizeChanged { headerHeight = it.height; headerMeasured = true }
                            .then(if (opensFromTop) Modifier else dragModifier)) {
                            if (!opensFromTop) dragHandle?.invoke()
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
                        Column(Modifier.fillMaxWidth().onSizeChanged { footerHeight = it.height; footerMeasured = true }) {
                            footer?.invoke()
                            if (opensFromTop && dragHandle != null) Box(dragModifier) { dragHandle() }
                        }
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
                val visibleHeight = (availablePx - state.layoutOffset()).coerceIn(0f, availablePx)
                val stretch = upwardStretch(state.rubberTranslation)
                val base = if (chrome == 0) availablePx else visibleHeight.coerceAtLeast(chrome.toFloat().coerceAtMost(availablePx))
                // Compensate the external gap/extension so the top edge stays on its detent.
                val adjustment = navExtension - trayGap.toPx()
                val topExtension = if (opensFromTop) topInset else 0
                val childHeight = (base + stretch + adjustment + topExtension).roundToInt().coerceAtLeast(0)
                val visible = (visibleHeight + stretch + adjustment + topExtension).roundToInt().coerceAtLeast(0)
                val child = measurables.last().measure(Constraints.fixed(constraints.maxWidth, childHeight))
                val material = if (sheetMaterial != null) measurables.first().measure(
                    Constraints.fixed(constraints.maxWidth, childHeight + materialNavigation),
                ) else null
                layout(constraints.maxWidth, visible + materialNavigation) {
                    material?.place(0, 0)
                    child.place(0, 0)
                }
            }
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

private fun Modifier.clipToBoundsForSheet(): Modifier = this.sheetDrawClip { androidx.compose.ui.graphics.RectangleShape }

private fun Modifier.edgeRubber(state: SheetState, opensFromTop: Boolean): Modifier =
    offset {
        val shift = downwardShift(state.rubberTranslation).roundToInt()
        IntOffset(0, if (opensFromTop) -shift else shift)
    }

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

