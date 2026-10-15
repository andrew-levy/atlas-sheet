package dev.atlas.sheet

import kotlin.math.PI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SheetMotionTest {
    @Test fun defaultSpringMatchesIosSystemSpring() {
        val stiffness = (2.0 * PI / IosSheetSpringDurationSeconds).let { (it * it).toFloat() }
        assertEquals(stiffness, IosSheetSpringStiffness, 0f)
        assertEquals(1f, IosSheetSpringDampingRatio, 0f)
        assertEquals(0.5, IosSheetSpringDurationSeconds, 0.0)
    }

    @Test fun rubberBandMatchesUiScrollView() {
        // 150px past the edge on a 1000px sheet: (1 - 1 / (150 * 0.55 / 1000 + 1)) * 1000
        assertEquals(76.21247f, rubberBand(150f, 1000f), 0.01f)
        assertEquals(-76.21247f, rubberBand(-150f, 1000f), 0.01f)
        assertEquals(0f, rubberBand(0f, 1000f), 0f)
        assertTrue(kotlin.math.abs(rubberBand(400f, 1000f)) < 400f)
    }

    @Test fun rubberBandInverseRoundTrips() {
        val visual = rubberBand(-180f, 900f)
        assertEquals(-180f, rubberRawFromVisual(visual, 900f), 0.05f)
    }

    @Test fun outwardReleaseStaysOnTheEdge() {
        assertFalse(rubberReleaseSettlesAnchor(-24f, -900f, 125f))
        assertFalse(rubberReleaseSettlesAnchor(24f, 40f, 125f))
        assertFalse(rubberReleaseSettlesAnchor(-24f, 0f, 125f))
    }

    @Test fun inwardFlickSettlesToTheNextDetent() {
        assertTrue(rubberReleaseSettlesAnchor(-24f, 400f, 125f))
        assertTrue(rubberReleaseSettlesAnchor(24f, -400f, 125f))
        assertTrue(rubberReleaseSettlesAnchor(0f, -400f, 125f))
    }

    @Test fun scrimFadesUntilTheLowestDetent() {
        assertEquals(0f, scrimDragFraction(0f, 400f, followsDrag = true), 0f)
        assertEquals(0.5f, scrimDragFraction(200f, 400f, followsDrag = true), 0f)
        assertEquals(1f, scrimDragFraction(800f, 400f, followsDrag = true), 0f)
        assertEquals(1f, scrimDragFraction(20f, 400f, followsDrag = false), 0f)
    }
}
