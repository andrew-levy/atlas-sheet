package dev.atlas.sheet

import org.junit.Assert.*
import org.junit.Test

class AtlasSheetStackTest {
    @Test fun coverageTracksDragAndReversalAtAShortDetent() {
        val stack = AtlasSheetStackState(listOf("base"))
        val base = AtlasSheetStackEntry(stack, stack.entries.first())
        stack.push("detail")
        val above = stack.entries.last().state
        above.updateGeometry(SheetGeometry.resolve(1000f, 0f, listOf("expanded" to 400f)), true)
        assertEquals(0f, base.coveredProgress, 0f)
        above.anchored.dispatchRawDelta(-200f)
        assertEquals(.5f, base.coveredProgress, .001f)
        above.anchored.dispatchRawDelta(-200f)
        assertEquals(1f, base.coveredProgress, .001f)
        above.anchored.dispatchRawDelta(100f)
        assertEquals(.75f, base.coveredProgress, .001f)
        above.anchored.dispatchRawDelta(-100f)
        assertEquals(1f, base.coveredProgress, .001f)
    }

    @Test fun threeLevelsUseImmediateCoverAndRestoreAfterRemoval() {
        val stack = AtlasSheetStackState(listOf("base"))
        val base = AtlasSheetStackEntry(stack, stack.entries.first())
        stack.push("detail")
        val detail = stack.entries.last()
        detail.state.updateGeometry(SheetGeometry.resolve(1000f, 0f, listOf("expanded" to 600f)), true)
        detail.state.anchored.dispatchRawDelta(-600f)
        val detailInfo = AtlasSheetStackEntry(stack, detail)
        stack.push("review")
        assertEquals(2, base.depth)
        assertEquals(1f, base.coveredProgress, 0f)
        assertEquals(0f, detailInfo.coveredProgress, 0f)
        assertFalse(detailInfo.isTopmost)
        stack.remove(stack.entries.last())
        assertTrue(detailInfo.isTopmost)
        stack.remove(detail)
        assertEquals(0, base.depth)
        assertEquals(0f, base.coveredProgress, 0f)
        assertTrue(base.isTopmost)
    }

    @Test fun duplicateRoutesHaveIndependentStateAndRemovalIdentity() {
        val stack = AtlasSheetStackState(listOf("profile"))
        val original = stack.entries.first()
        stack.push("profile")
        val new = stack.entries.last()
        assertNotSame(original.state, new.state)
        stack.remove(original)
        stack.remove(original) // A late completion cannot remove the replacement.
        assertEquals(listOf("profile"), stack.keys)
        assertSame(new, stack.entries.single())
    }

    @Test fun coverageIsBoundedAndHandlesZeroHeight() {
        assertEquals(0f, stackCoverage(-10f, 300f), 0f)
        assertEquals(1f, stackCoverage(800f, 300f), 0f)
        assertEquals(0f, stackCoverage(0f, 0f), 0f)
    }
}
