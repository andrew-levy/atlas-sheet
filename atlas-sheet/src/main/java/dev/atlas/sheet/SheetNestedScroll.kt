package dev.atlas.sheet

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlin.math.abs

/** Decide ownership once per touch gesture, before accepting any edge remainder. */
internal class SheetNestedScroll(
    private val state: SheetState,
    private val velocityThreshold: Float,
    private val bubbleAtTop: Boolean = true,
    private val bubbleAtBottom: Boolean = true,
    private val transferFling: Boolean = true,
    private val handoffThreshold: Float = velocityThreshold,
    private val velocityScale: Float = 1f,
) : NestedScrollConnection {
    private enum class Owner { Undecided, Content, Sheet }
    private var owner = Owner.Content
    private var moved = false

    // Reset on an actual new touch, never on direction changes or fling frames.
    fun beginGesture() {
        owner = Owner.Undecided
        moved = false
    }

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (source != NestedScrollSource.UserInput || owner != Owner.Sheet) return Offset.Zero
        return consume(available.y)
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (source != NestedScrollSource.UserInput) return Offset.Zero
        // Even a partially consumed first delta locks this gesture to content.
        if (consumed.y != 0f) owner = Owner.Content
        if (owner == Owner.Undecided && available.y != 0f) {
            val enabled = if (available.y > 0f) bubbleAtTop else bubbleAtBottom
            owner = if (enabled && state.canConsumeNestedDrag(available.y)) Owner.Sheet else Owner.Content
        }
        return if (owner == Owner.Sheet) consume(available.y) else Offset.Zero
    }

    private fun consume(delta: Float): Offset {
        val consumed = state.consumeNestedDrag(delta)
        if (consumed != 0f) moved = true
        return Offset(0f, consumed)
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        if (!moved) return Velocity.Zero
        moved = false
        val velocity = if (transferFling && abs(available.y) >= handoffThreshold)
            available.y * velocityScale else 0f
        state.finishGesture(velocity, velocityThreshold)
        return Velocity(0f, available.y)
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        // A list fling reaching an edge never starts a sheet gesture.
        if (moved) {
            moved = false
            state.finishGesture(0f, velocityThreshold)
        }
        return Velocity.Zero
    }
}
