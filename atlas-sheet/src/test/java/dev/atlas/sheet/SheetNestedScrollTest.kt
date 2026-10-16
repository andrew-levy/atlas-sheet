package dev.atlas.sheet

import androidx.compose.animation.core.snap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class SheetNestedScrollTest {
    private fun state() = SheetState("medium", snap()).also {
        it.updateGeometry(SheetGeometry.resolve(1000f, 100f,
            listOf("medium" to 600f, "full" to 1000f)), false)
    }
    @Test fun upwardDragHandsExactRemainderToChildAtFullHeight() {
        val state = state()
        val nested = SheetNestedScroll(state, true, 125f)
        assertEquals(Offset(0f, -400f), nested.onPreScroll(Offset(0f, -450f), NestedScrollSource.UserInput))
        assertEquals(Offset.Zero, nested.onPreScroll(Offset(0f, -80f), NestedScrollSource.UserInput))
        assertEquals(0f, state.rubberTranslation, 0f)
    }
    @Test fun downwardMotionWaitsForChildEdge() {
        val state = state()
        state.consumeNestedDrag(-300f)
        val nested = SheetNestedScroll(state, true, 125f)
        assertEquals(Offset.Zero, nested.onPreScroll(Offset(0f, 90f), NestedScrollSource.UserInput))
        assertEquals(Offset(0f, 30f), nested.onPostScroll(Offset(0f, 60f), Offset(0f, 30f), NestedScrollSource.UserInput))
        assertEquals(130f, state.offsetOrHidden(), 0f)
    }
    @Test fun contentFirstDoesNotInterceptUpwardDrag() {
        val nested = SheetNestedScroll(state(), false, 125f)
        assertEquals(Offset.Zero, nested.onPreScroll(Offset(0f, -50f), NestedScrollSource.UserInput))
        assertEquals(Offset(0f, -20f), nested.onPostScroll(Offset(0f, -30f), Offset(0f, -20f), NestedScrollSource.UserInput))
    }
    @Test fun childFlingFramesDoNotStretchOrDragSheet() {
        val state = state()
        val nested = SheetNestedScroll(state, true, 125f)
        assertEquals(Offset.Zero, nested.onPostScroll(Offset.Zero, Offset(0f, -100f), NestedScrollSource.SideEffect))
        assertEquals(400f, state.offsetOrHidden(), 0f)
        assertFalse(state.dragInProgress)
    }
    @Test fun disabledAndBelowThresholdFlingsLeaveSheetAlone() = runTest {
        val state = state()
        val disabled = SheetNestedScroll(state, false, 125f, transferFling = false)
        assertEquals(Velocity.Zero, disabled.onPostFling(Velocity.Zero, Velocity(0f, -2000f)))
        val guarded = SheetNestedScroll(state, false, 125f, handoffThreshold = 500f)
        assertEquals(Velocity.Zero, guarded.onPostFling(Velocity.Zero, Velocity(0f, -499f)))
        assertEquals(400f, state.offsetOrHidden(), 0f)
    }
    @Test fun sheetBoundsNeverConsumeHorizontalOrCreateRubber() {
        val state = state()
        val nested = SheetNestedScroll(state, true, 125f)
        assertEquals(Offset.Zero, nested.onPostScroll(Offset.Zero, Offset(70f, 50f), NestedScrollSource.UserInput))
        assertEquals(0f, state.rubberTranslation, 0f)
        assertFalse(state.dragInProgress)
    }
}
