package dev.atlas.sheet

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlin.math.abs

/** Expand first if requested; otherwise the child owns motion until its scroll edge. */
internal class SheetNestedScroll(
    private val state: SheetState,
    private val expandFirst: Boolean,
    private val velocityThreshold: Float,
    private val transferFling: Boolean = true,
    private val handoffThreshold: Float = velocityThreshold,
    private val velocityScale: Float = 1f,
) : NestedScrollConnection {
    private var moved = false

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (source != NestedScrollSource.UserInput || !expandFirst || available.y >= 0f) return Offset.Zero
        return consume(available.y)
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        // Child fling frames stay with the child. Transfer its residual velocity once, in post-fling.
        if (source != NestedScrollSource.UserInput) return Offset.Zero
        return consume(available.y)
    }

    private fun consume(delta: Float): Offset {
        val consumed = state.consumeNestedDrag(delta)
        if (consumed != 0f) moved = true
        return Offset(0f, consumed)
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        if (expandFirst && available.y < 0f && state.canConsumeNestedDrag(available.y)) {
            moved = false
            state.finishGesture(available.y, velocityThreshold)
            return Velocity(0f, available.y)
        }
        return Velocity.Zero
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        val handoff = transferFling && velocityScale > 0f &&
            abs(available.y) >= handoffThreshold && state.canConsumeNestedDrag(available.y)
        val shouldSettle = moved
        moved = false
        if (!shouldSettle && !handoff) {
            state.endDrag()
            return Velocity.Zero
        }
        // A drag always settles, even when automatic fling handoff is disabled.
        state.finishGesture(if (handoff) available.y * velocityScale else 0f, velocityThreshold)
        // Once transferred, own the residual fling so ancestors do not also react to it.
        return if (handoff) Velocity(0f, available.y) else Velocity.Zero
    }
}
