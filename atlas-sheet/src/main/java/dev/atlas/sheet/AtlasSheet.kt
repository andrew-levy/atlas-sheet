package dev.atlas.sheet

import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableDefaults
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Constraints
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

/** Modal sheet with window-level input/focus isolation. Remove it from composition on dismissal. */
@Composable
public fun AtlasModalSheet(
    state: SheetState,
    detents: List<SheetDetent>,
    onDismissRequest: () -> Unit,
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
    content: @Composable ColumnScope.() -> Unit,
) {
    SideEffect { state.preparePresentation() }
    val scope = rememberCoroutineScope()
    val dismissCallback by rememberUpdatedState(onDismissRequest)
    Dialog(
        onDismissRequest = { if (behavior.dismissOnBack) scope.launch { state.hide() } },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnBackPress = behavior.dismissOnBack,
            dismissOnClickOutside = false,
        ),
    ) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            // Compose draws the scrim, including status/navigation bar regions, exactly once.
            window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            // DialogProperties selects Resize on API 30 and below and Nothing on API 31+.
            // Preserving that choice is required for legacy IME inset delivery.
        }
        LaunchedEffect(state) {
            try { state.enter() } catch (interrupted: CancellationException) { coroutineContext.ensureActive() }
            snapshotFlow { state.anchored.settledValue }.first { it == HiddenId }
            dismissCallback()
        }
        AtlasSheetHost(
            state, detents, modifier, behavior, bodyMode, title, maxWidth, topMargin,
            containerColor, contentColor, scrimColor, shape, dragHandle, header, footer, content,
        )
    }
}

/** Inline overlay. Put last in a full-size Box. Color.Unspecified removes the scrim and its hit target. */
@Composable
public fun AtlasSheetHost(
    state: SheetState,
    detents: List<SheetDetent>,
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
    content: @Composable ColumnScope.() -> Unit,
) {
    require(detents.isNotEmpty() && detents.map { it.id }.distinct().size == detents.size)
    require(bodyMode != SheetBodyMode.Fill || detents.none { it.requiresContentMeasurement }) {
        "Content detents require SheetBodyMode.Scroll. Use fixed/fraction/custom detents for lazy content."
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
    val bottomInset = if (behavior.imePolicy == SheetImePolicy.Ignore) navInset else maxOf(navInset, imeInset)
    val fling = AnchoredDraggableDefaults.flingBehavior(state.anchored, animationSpec = state.animationSpec)
    val nested = remember(state, behavior.draggable, behavior.expandBeforeScroll, density) {
        SheetNestedScroll(state, behavior.expandBeforeScroll, with(density) { 125.dp.toPx() })
    }
    var beforeIme by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(imeVisible, behavior.imePolicy) {
        if (behavior.imePolicy == SheetImePolicy.Expand) {
            if (imeVisible && beforeIme == null) {
                beforeIme = state.targetDetent ?: state.currentDetent
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
        val detentScope = with(density) {
            SheetDetentScope(availablePx.toDp(), animatedContentHeight.value.toDp(), chrome.toDp())
        }
        val geometry = SheetGeometry.resolve(availablePx, chrome.toFloat(), detents.map { d ->
            d.id to with(density) { d.resolve(detentScope).toPx() }
        })
        val layoutReady = headerMeasured && footerMeasured &&
            (bodyMode == SheetBodyMode.Fill || contentInitialized)
        SideEffect { state.updateGeometry(geometry, behavior.dismissOnDrag, layoutReady) }

        if (scrimColor != Color.Unspecified && isVisible) {
            Box(Modifier.fillMaxSize()
                .drawBehind {
                    val smallestHeight = geometry.availableHeight - geometry.offsets.getValue(geometry.smallest)
                    val alpha = ((geometry.availableHeight - state.offsetOrHidden()) / smallestHeight).coerceIn(0f, 1f)
                    drawRect(scrimColor.copy(alpha = scrimColor.alpha * alpha))
                }
                .clickable(enabled = behavior.dismissOnScrim, onClickLabel = "Dismiss $title") {
                    scope.launch { state.hide() }
                }
                .semantics { if (behavior.dismissOnScrim) contentDescription = "Dismiss $title" })
        }
        val surfaceModifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = with(density) { bottomInset.toDp() })
            .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal))
            .widthIn(max = maxWidth).fillMaxWidth()
            .clip(shape)

        Layout(modifier = surfaceModifier, content = {
            Layout(modifier = Modifier.fillMaxWidth()
                .shadow(16.dp, shape).background(containerColor, shape)
                .then(if (behavior.draggable) Modifier.nestedScroll(nested) else Modifier)
                .semantics {
                    if (!isVisible) hideFromAccessibility()
                    paneTitle = title
                    isTraversalGroup = true
                    stateDescription = state.currentDetent ?: "Hidden"
                    if (behavior.dismissOnDrag) dismiss { scope.launch { state.hide() }; true }
                    expand { scope.launch { state.expand() }; true }
                    collapse { scope.launch { state.collapse() }; true }
                }, content = {
                // Foundation only: no Material theme is imposed on consumers.
                CompositionLocalProvider(LocalSheetContentColor provides contentColor) {
                    Column(Modifier.fillMaxWidth().onSizeChanged { headerHeight = it.height; headerMeasured = true }
                        .anchoredDraggable(state.anchored, Orientation.Vertical, enabled = behavior.draggable, flingBehavior = fling)) {
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
            val layoutHeight = if (chrome == 0) availablePx else visibleHeight.coerceAtLeast(chrome.toFloat().coerceAtMost(availablePx))
            val child = measurables.single().measure(Constraints.fixed(constraints.maxWidth, layoutHeight.roundToInt()))
            layout(constraints.maxWidth, visibleHeight.roundToInt()) { child.place(0, 0) }
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
