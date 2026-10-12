package dev.atlas.sheet

import org.junit.Assert.*
import org.junit.Test

class SheetGeometryTest {
    @Test fun resolvesOffsetsAndOrderWithoutRequiringSortedInput() {
        val g = SheetGeometry.resolve(1000f, 100f, listOf("full" to 1000f, "peek" to 250f, "medium" to 600f))
        assertEquals(0f, g.offsets.getValue("full"), 0f)
        assertEquals(750f, g.offsets.getValue("peek"), 0f)
        assertEquals("full", g.largest)
        assertEquals("peek", g.smallest)
    }
    @Test fun smallDetentsCannotHideStickyChrome() {
        val g = SheetGeometry.resolve(500f, 180f, listOf("small" to 20f))
        assertEquals(319f, g.offsets.getValue("small"), 0f)
    }
    @Test fun oversizedChromeIsClampedToViewport() {
        val g = SheetGeometry.resolve(100f, 200f, listOf("small" to 20f))
        assertEquals(0f, g.offsets.getValue("small"), 0f)
    }
    @Test fun equalClampedHeightsBecomeDeterministicAliases() {
        val g = SheetGeometry.resolve(500f, 50f, listOf("content" to 900f, "full" to 500f))
        assertEquals(1, g.offsets.size)
        assertEquals("content", g.canonical("full"))
    }
    @Test fun viewportShrinkRecomputesOffsets() {
        val full = SheetGeometry.resolve(1000f, 100f, listOf("fixed" to 400f))
        val ime = SheetGeometry.resolve(450f, 100f, listOf("fixed" to 400f))
        assertEquals(600f, full.offsets.getValue("fixed"), 0f)
        assertEquals(50f, ime.offsets.getValue("fixed"), 0f)
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsDuplicateIds() { SheetGeometry.resolve(500f, 10f, listOf("a" to 100f, "a" to 200f)) }
    @Test(expected = IllegalArgumentException::class) fun rejectsNaN() { SheetGeometry.resolve(500f, 10f, listOf("a" to Float.NaN)) }
    @Test(expected = IllegalArgumentException::class) fun rejectsZero() { SheetGeometry.resolve(500f, 10f, listOf("a" to 0f)) }
    @Test(expected = IllegalArgumentException::class) fun rejectsInvalidViewport() { SheetGeometry.resolve(0f, 10f, listOf("a" to 100f)) }
    private val anchors = mapOf("full" to 0f, "medium" to 400f, "peek" to 750f, HiddenId to 1000f)
    @Test fun slowReleaseUsesNearest() { assertEquals("medium", settleTarget(510f, 100f, anchors, 125f)) }
    @Test fun fastDownwardReleaseAdvancesToNext() { assertEquals("peek", settleTarget(410f, 1000f, anchors, 125f)) }
    @Test fun fastUpwardReleaseAdvancesToNext() { assertEquals("medium", settleTarget(700f, -1000f, anchors, 125f)) }
    @Test fun releaseAtBoundsDoesNotOvershoot() {
        assertEquals("full", settleTarget(0f, -500f, anchors, 125f))
        assertEquals(HiddenId, settleTarget(1000f, 500f, anchors, 125f))
    }
    @Test fun nondismissibleFlingCannotSelectHidden() {
        assertEquals("peek", settleTarget(750f, 500f, anchors.filterKeys { it != HiddenId }, 125f))
    }
}
