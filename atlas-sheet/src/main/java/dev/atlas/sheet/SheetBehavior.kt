package dev.atlas.sheet

import androidx.compose.runtime.Immutable

public enum class SheetKeyboardBehavior {
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
    @Deprecated("Content now scrolls first. Use bubbleAtTop and bubbleAtBottom for new edge gestures.")
    val expandBeforeScroll: Boolean = false,
    val keyboardBehavior: SheetKeyboardBehavior = SheetKeyboardBehavior.Resize,
    /** Fade the scrim with the drag until the lowest detent. Full strength above that. */
    val scrimFollowsDrag: Boolean = true,
    /** Preserve release velocity only for a gesture that already dragged the sheet. */
    val transferScrollFling: Boolean = true,
    /** Minimum release velocity (dp/s) applied when settling a sheet-owned gesture. */
    val scrollFlingThreshold: Float = 125f,
    /** Fraction of release velocity applied to a sheet-owned gesture. */
    val scrollFlingVelocityScale: Float = 1f,
    /** A new downward drag at the content's physical top may collapse/dismiss the sheet. */
    val bubbleAtTop: Boolean = true,
    /** A new upward drag at the content's physical bottom may expand the sheet. */
    val bubbleAtBottom: Boolean = true,
) {
    init {
        require(scrollFlingThreshold.isFinite() && scrollFlingThreshold >= 0f)
        require(scrollFlingVelocityScale.isFinite() && scrollFlingVelocityScale in 0f..1f)
    }
}
