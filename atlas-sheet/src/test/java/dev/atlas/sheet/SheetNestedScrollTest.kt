package dev.atlas.sheet

import androidx.compose.animation.core.snap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class SheetNestedScrollTest {
    private val touch = NestedScrollSource.UserInput
    private fun state() = SheetState("medium", snap()).also {
        it.updateGeometry(SheetGeometry.resolve(1000f, 100f,
            listOf("small" to 300f, "medium" to 600f, "full" to 1000f)), true)
    }

    @Test fun reachingTopDuringDragCannotCollapseUntilNewTouch() {
        val state = state()
        val nested = SheetNestedScroll(state, 125f)
        nested.beginGesture()
        assertEquals(Offset.Zero, nested.onPostScroll(Offset(0f, 50f), Offset.Zero, touch))
        assertEquals(Offset.Zero, nested.onPostScroll(Offset(0f, 10f), Offset(0f, 40f), touch))
        assertEquals(Offset.Zero, nested.onPostScroll(Offset.Zero, Offset(0f, 90f), touch))
        assertEquals(400f, state.offsetOrHidden(), 0f)
        nested.beginGesture()
        assertEquals(Offset(0f, 90f), nested.onPostScroll(Offset.Zero, Offset(0f, 90f), touch))
        assertEquals(490f, state.offsetOrHidden(), 0f)
    }

    @Test fun firstDeltaPartiallyConsumedByContentLocksBothDirections() {
        for (direction in listOf(-1f, 1f)) {
            val state = state()
            val nested = SheetNestedScroll(state, 125f)
            nested.beginGesture()
            assertEquals(Offset.Zero, nested.onPostScroll(Offset(0f, direction), Offset(0f, 80f * direction), touch))
            assertEquals(Offset.Zero, nested.onPostScroll(Offset.Zero, Offset(0f, 80f * direction), touch))
            // Reversing direction is not a new gesture either.
            assertEquals(Offset.Zero, nested.onPostScroll(Offset.Zero, Offset(0f, -80f * direction), touch))
            assertEquals(400f, state.offsetOrHidden(), 0f)
        }
    }

    @Test fun topAndBottomSwitchesAreIndependent() {
        for (top in listOf(false, true)) for (bottom in listOf(false, true)) {
            for (direction in listOf(-1f, 1f)) {
                val state = state()
                val nested = SheetNestedScroll(state, 125f, bubbleAtTop = top, bubbleAtBottom = bottom)
                nested.beginGesture()
                val expected = if (if (direction > 0f) top else bottom) direction * 50f else 0f
                assertEquals(Offset(0f, expected), nested.onPostScroll(Offset.Zero, Offset(0f, direction * 50f), touch))
                assertEquals(400f + expected, state.offsetOrHidden(), 0f)
            }
        }
    }

    @Test fun inertiaAtEitherEdgeNeverStartsSheetMovement() = runTest {
        for (direction in listOf(-1f, 1f)) {
            val state = state()
            val nested = SheetNestedScroll(state, 125f)
            nested.beginGesture()
            nested.onPostScroll(Offset(0f, 100f * direction), Offset.Zero, touch)
            assertEquals(Velocity.Zero, nested.onPreFling(Velocity(0f, 5000f * direction)))
            assertEquals(Offset.Zero, nested.onPostScroll(Offset.Zero, Offset(0f, 100f * direction), NestedScrollSource.SideEffect))
            assertEquals(Velocity.Zero, nested.onPostFling(Velocity.Zero, Velocity(0f, 5000f * direction)))
            assertEquals(400f, state.offsetOrHidden(), 0f)
            assertFalse(state.dragInProgress)
        }
    }

    @Test fun newTouchResetsOwnershipEvenWithoutPreviousFlingCallback() {
        val state = state()
        val nested = SheetNestedScroll(state, 125f)
        nested.beginGesture()
        nested.onPostScroll(Offset(0f, -20f), Offset.Zero, touch)
        nested.beginGesture()
        assertEquals(Offset(0f, -50f), nested.onPostScroll(Offset.Zero, Offset(0f, -50f), touch))
    }

    @Test fun sheetAtFullHeightHandsRemainingDistanceBackToListWithoutRubber() {
        val state = state()
        val nested = SheetNestedScroll(state, 125f)
        nested.beginGesture()
        assertEquals(Offset(0f, -400f), nested.onPostScroll(Offset.Zero, Offset(0f, -450f), touch))
        assertEquals(Offset.Zero, nested.onPreScroll(Offset(0f, -80f), touch))
        assertEquals(0f, state.rubberTranslation, 0f)
    }
}
