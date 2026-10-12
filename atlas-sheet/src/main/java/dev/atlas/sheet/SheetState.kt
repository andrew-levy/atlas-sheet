package dev.atlas.sheet

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.gestures.snapTo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first

/** Hoist this state. Animations are mutually exclusive through Compose's AnchoredDraggableState. */
@Stable
public class SheetState internal constructor(
    internal val initialDetent: String,
    internal val animationSpec: AnimationSpec<Float>,
) {
    internal val anchored = AnchoredDraggableState(initialDetent)
    internal var geometry: SheetGeometry? by mutableStateOf(null)
    private var dragDismiss = true
    private var programmaticHide = false

    /** The detent actually reached; does not change half way through an animation. */
    public val currentDetent: String? get() = anchored.settledValue.takeUnless { it == HiddenId }
    public val targetDetent: String? get() = anchored.targetValue.takeUnless { it == HiddenId }
    public val isAnimationRunning: Boolean get() = anchored.isAnimationRunning
    public val isVisible: Boolean get() = geometry?.let { anchored.offset.isFinite() && anchored.offset < it.availableHeight - 0.5f } ?: false
    /** Continuous normalized visible height, useful for coordinated background animation. */
    public val progress: Float get() = geometry?.let { ((it.availableHeight - offsetOrHidden()) / it.availableHeight).coerceIn(0f, 1f) } ?: 0f

    internal fun offsetOrHidden(): Float = anchored.offset.takeIf { it.isFinite() } ?: (geometry?.availableHeight ?: 0f)

    internal fun updateGeometry(next: SheetGeometry, dismissOnDrag: Boolean) {
        if (next == geometry && dragDismiss == dismissOnDrag) return
        dragDismiss = dismissOnDrag
        geometry = next
        val desired = next.canonical(anchored.targetValue)
        val target = when {
            desired == HiddenId -> HiddenId
            desired in next.offsets -> desired
            else -> next.smallest
        }
        updateAnchors(target)
    }

    private fun updateAnchors(target: String = anchored.targetValue) {
        val g = geometry ?: return
        val includeHidden = dragDismiss || programmaticHide || target == HiddenId
        anchored.updateAnchors(DraggableAnchors {
            g.offsets.forEach { (id, offset) -> id at offset }
            if (includeHidden) HiddenId at g.availableHeight
        }, newTarget = target)
    }

    private suspend fun ready(): SheetGeometry = snapshotFlow { geometry }.first { it != null }!!

    /** Suspends until layout is ready. Unknown IDs fail fast; interrupted animations propagate cancellation. */
    public suspend fun animateTo(detent: String) {
        val g = ready()
        val target = g.canonical(detent)
        require(target in g.offsets) { "Unknown detent '$detent'. Available: ${g.offsets.keys + g.aliases.keys}" }
        programmaticHide = false
        updateAnchors()
        anchored.animateTo(target, animationSpec)
        updateAnchors()
    }

    public suspend fun snapTo(detent: String) {
        val g = ready()
        val target = g.canonical(detent)
        require(target in g.offsets) { "Unknown detent '$detent'" }
        programmaticHide = false
        updateAnchors()
        anchored.snapTo(target)
        updateAnchors()
    }

    public suspend fun expand() { animateTo(ready().largest) }
    public suspend fun collapse() { animateTo(ready().smallest) }

    /** Programmatic dismissal remains available when drag, scrim or Back dismissal is disabled. */
    public suspend fun hide() {
        ready()
        programmaticHide = true
        updateAnchors()
        try { anchored.animateTo(HiddenId, animationSpec) }
        finally { programmaticHide = false }
    }

    internal suspend fun enter() {
        val g = ready()
        val desired = g.canonical(currentDetent ?: initialDetent).takeIf { it in g.offsets } ?: g.smallest
        programmaticHide = true
        updateAnchors()
        anchored.snapTo(HiddenId)
        animateTo(desired)
    }

    internal fun consumeDrag(delta: Float): Float =
        if (geometry == null || anchored.offset.isNaN()) 0f else anchored.dispatchRawDelta(delta)

    internal suspend fun settle(velocity: Float, velocityThreshold: Float) {
        val g = geometry ?: return
        val anchors = g.offsets.toMutableMap().apply { if (dragDismiss) put(HiddenId, g.availableHeight) }
        val target = settleTarget(offsetOrHidden(), velocity, anchors, velocityThreshold)
        anchored.animateTo(target, animationSpec)
    }
}

@Composable
public fun rememberSheetState(
    initialDetent: String = "expanded",
    animationSpec: AnimationSpec<Float> = spring(dampingRatio = 0.9f, stiffness = 450f),
): SheetState {
    val saver = Saver<SheetState, String>(
        save = { it.currentDetent ?: it.initialDetent },
        restore = { SheetState(it, animationSpec) },
    )
    return rememberSaveable(saver = saver) { SheetState(initialDetent, animationSpec) }
}
