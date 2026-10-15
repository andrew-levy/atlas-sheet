package dev.atlas.sheet

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity

/** Upward motion expands first. Downward motion goes to the child until it reaches its edge. */
internal class SheetNestedScroll(
    private val state: SheetState,
    private val expandFirst: Boolean,
    private val velocityThreshold: Float,
) : NestedScrollConnection {
    private var moved = false

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (source != NestedScrollSource.UserInput || !expandFirst || available.y >= 0f) return Offset.Zero
        return consume(available.y)
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (source != NestedScrollSource.UserInput) return Offset.Zero
        return consume(available.y)
    }

    private fun consume(delta: Float): Offset {
        val consumed = state.consumeDrag(delta)
        if (consumed != 0f) moved = true
        return Offset(0f, consumed)
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        val min = state.geometry?.offsets?.values?.minOrNull() ?: return Velocity.Zero
        if (expandFirst && available.y < 0f && state.offsetOrHidden() > min + 0.5f) {
            state.finishGesture(available.y, velocityThreshold)
            moved = false
            return Velocity(0f, available.y)
        }
        return Velocity.Zero
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        if (!moved && available.y == 0f) {
            state.endDrag()
            return Velocity.Zero
        }
        state.finishGesture(available.y, velocityThreshold)
        moved = false
        return Velocity(0f, available.y)
    }
}
