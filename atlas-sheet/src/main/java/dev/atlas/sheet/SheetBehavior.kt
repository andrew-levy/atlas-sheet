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
    /** Allow remaining child fling momentum to move the sheet when content reaches its edge. */
    val transferScrollFling: Boolean = true,
    /** Minimum remaining child velocity (dp/s) required to initiate an edge handoff. */
    val scrollFlingThreshold: Float = 125f,
    /** Fraction of remaining velocity transferred to the sheet. Zero disables momentum transfer. */
    val scrollFlingVelocityScale: Float = 1f,
) {
    init {
        require(scrollFlingThreshold.isFinite() && scrollFlingThreshold >= 0f)
        require(scrollFlingVelocityScale.isFinite() && scrollFlingVelocityScale in 0f..1f)
    }
}
