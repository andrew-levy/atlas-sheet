package dev.atlas.sheet

import androidx.compose.animation.core.snap
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class SheetStateTest {
    private fun state(dismiss: Boolean = true): SheetState = SheetState("medium", snap()).also {
        it.updateGeometry(SheetGeometry.resolve(1000f, 100f, listOf("medium" to 600f, "full" to 1000f)), dismiss)
    }
    @Test fun managedRootInitializesHiddenBeforeItsFirstFrame() {
        val state = SheetState("medium", snap())
        state.preparePresentation()
        state.updateGeometry(SheetGeometry.resolve(1000f, 100f, listOf("medium" to 600f)), true)
        assertNull(state.currentSnapPoint)
        assertEquals(0f, state.progress, 0f)
    }
    @Test fun explicitlyHiddenStateDoesNotOpenDuringMeasurement() {
        val state = SheetState("medium", snap(), initiallyVisible = false)
        state.updateGeometry(SheetGeometry.resolve(1000f, 100f, listOf("medium" to 600f)), true)
        assertNull(state.currentSnapPoint)
        assertEquals(0f, state.progress, 0f)
    }
    @Test fun initialAnchorIsFiniteAndSelected() {
        val state = state()
        assertEquals("medium", state.currentSnapPoint)
        assertEquals(0.6f, state.progress, 0.0001f)
    }
    @Test fun rawDragIsBounded() {
        val state = state()
        assertEquals(-5000f, state.consumeDrag(-5000f), 0f)
        assertEquals(0f, state.offsetOrHidden(), 0f)
        assertEquals(rubberBand(-4600f, 1000f), state.rubberTranslation, 0.01f)
    }
    @Test fun nondismissibleDragStopsAtSmallestDetent() {
        val state = state(false)
        assertEquals(5000f, state.consumeDrag(5000f), 0f)
        assertEquals(400f, state.offsetOrHidden(), 0f)
        assertEquals(rubberBand(5000f, 1000f), state.rubberTranslation, 0.01f)
    }
    @Test fun dismissibleDragPastHiddenDoesNotRubberBand() {
        val state = state()
        assertEquals(600f, state.consumeDrag(600f), 0f)
        assertEquals(1000f, state.offsetOrHidden(), 0f)
        assertEquals(0f, state.consumeDrag(80f), 0f)
        assertEquals(0f, state.rubberTranslation, 0f)
    }
    @Test fun rubberBandUnwindsBeforeTheAnchorMoves() {
        val state = state()
        state.consumeDrag(-400f)
        state.consumeDrag(-120f)
        assertEquals(rubberBand(-120f, 1000f), state.rubberTranslation, 0.01f)
        assertEquals(0f, state.offsetOrHidden(), 0f)
        state.consumeDrag(150f)
        assertEquals(0f, state.rubberTranslation, 0.01f)
        assertEquals(30f, state.offsetOrHidden(), 0.01f)
    }
    @Test fun snapUpdatesSettledValue() = runTest {
        val state = state()
        state.snapTo("full")
        assertEquals("full", state.currentSnapPoint)
        assertEquals(1f, state.progress, 0f)
    }
    @Test fun removedSelectedDetentFallsBackSafely() {
        val state = state()
        state.updateGeometry(SheetGeometry.resolve(800f, 100f, listOf("new" to 300f)), false)
        assertEquals("new", state.currentSnapPoint)
        assertEquals(500f, state.offsetOrHidden(), 0f)
    }
    @Test fun aliasesAreAcceptedByProgrammaticApi() = runTest {
        val state = state()
        state.updateGeometry(SheetGeometry.resolve(500f, 100f, listOf("medium" to 500f, "full" to 900f)), true)
        state.snapTo("full")
        assertEquals("medium", state.currentSnapPoint)
    }
    @Test fun unknownIdsFailWithUsefulError() = runTest {
        val state = state()
        try { state.snapTo("missing"); fail("Expected unknown ID failure") }
        catch (e: IllegalArgumentException) { assertTrue(e.message!!.contains("missing")) }
    }
}
