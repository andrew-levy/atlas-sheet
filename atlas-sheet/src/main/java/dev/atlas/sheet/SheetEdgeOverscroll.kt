package dev.atlas.sheet

import androidx.compose.foundation.OverscrollEffect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity

/** Header drags that pass an edge are resisted here. The sheet draws the resulting translation. */
internal class SheetEdgeOverscroll(
    private val state: SheetState,
    private val velocityThreshold: Float,
) : OverscrollEffect {
    override fun applyToScroll(
        delta: Offset,
        source: NestedScrollSource,
        performScroll: (Offset) -> Offset,
    ): Offset {
        state.beginDrag()
        val afterRubber = state.consumeRubber(delta.y)
        val consumed = performScroll(Offset(0f, afterRubber))
        val edgeConsumed = state.absorbEdge(afterRubber - consumed.y)
        return Offset(0f, (delta.y - afterRubber) + consumed.y + edgeConsumed)
    }

    override suspend fun applyToFling(velocity: Velocity, performFling: suspend (Velocity) -> Velocity) {
        val speed = velocity.y
        if (state.edgeReleaseSettles(speed, velocityThreshold)) {
            state.clearRubber()
            performFling(velocity)
        } else {
            // The anchor is already on the bound. Settle it in place, then spring the visual back.
            performFling(Velocity.Zero)
            state.animateRubberHome(speed)
        }
    }

    override val isInProgress: Boolean
        get() = state.rubberTranslation != 0f
}
