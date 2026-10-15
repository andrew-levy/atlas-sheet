package dev.atlas.sheet

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Always-mounted, in-place sheet. Suitable as a Fragment's ComposeView root.
 * Opens on mount by default; use initiallyVisible = false and state.show() for a hidden host.
 * hide() animates out without requiring callers to remove this composable.
 * onDismissed runs after a completed exit. A fragment may pop/remove itself there.
 * The host fills its parent: mount in a full-window fragment container for a modal-sized scrim.
 */
@Composable
public fun AtlasSheet(
    state: SheetState = rememberSheetState(),
    detents: List<SheetDetent>,
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
    content: @Composable ColumnScope.() -> Unit,
) {
    SideEffect { state.preparePresentation() }
    val dismissed by rememberUpdatedState(onDismissed)
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(state.targetDetent, state.hasPresented) {
        if (state.targetDetent == null && state.hasPresented) {
            focus.clearFocus(force = true)
            keyboard?.hide()
        }
    }
    val dismissSlop = with(LocalDensity.current) { 24.dp.toPx() }
    LaunchedEffect(state) {
        if (state.initiallyVisible) launch { state.enter() }
        snapshotFlow { state.releaseDismissed(dismissSlop) }.collect { hidden -> if (hidden) dismissed() }
    }
    AtlasSheetHost(
        state, detents, modifier, behavior, bodyMode, title, maxWidth, topMargin,
        containerColor, contentColor, scrimColor, shape, dragHandle, header, footer, content,
    )
}
