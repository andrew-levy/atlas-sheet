package dev.atlas.sheet

import androidx.compose.runtime.Immutable

public enum class SheetImePolicy {
    /** Leave sizing unchanged; intended for sheets without text entry. */
    Ignore,
    /** Recompute available height above the IME; retain the selected detent. */
    Resize,
    /** Resize and temporarily choose the largest detent; restore it when the IME closes. */
    Expand,
}

public enum class SheetBodyMode {
    /** Library-owned vertical scrolling. Use finite content; do not nest LazyColumn here. */
    Scroll,
    /** Caller owns scrolling, e.g. LazyColumn, reverse-layout chat or AndroidView interop. */
    Fill,
}

@Immutable
public data class SheetBehavior(
    val dismissOnDrag: Boolean = true,
    val dismissOnScrim: Boolean = true,
    val dismissOnBack: Boolean = true,
    val draggable: Boolean = true,
    val expandBeforeScroll: Boolean = true,
    val imePolicy: SheetImePolicy = SheetImePolicy.Resize,
    /** Fade the scrim with the drag until the lowest detent. Full strength above that. */
    val scrimFollowsDrag: Boolean = true,
)
