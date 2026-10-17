package dev.atlas.sheet

import androidx.compose.animation.core.snap
import org.junit.Assert.*
import org.junit.Test

class AtlasSheetStackTest {
    private fun sheet(height: Float = 400f): SheetState = SheetState("expanded", snap()).apply {
        prepareModalPresentation()
        updateGeometry(SheetGeometry.resolve(1000f, 0f, listOf("expanded" to height)), true)
    }

    @Test fun cumulativeDepthTracksEveryCoveringSheetAndReversal() {
        val stack = SheetStackState()
        val base = sheet()
        val middle = sheet()
        val top = sheet()
        stack.register(base)
        stack.register(middle)
        val baseInfo = AtlasSheetStackEntry(stack, base)
        val middleInfo = AtlasSheetStackEntry(stack, middle)
        middle.anchored.dispatchRawDelta(-400f)
        stack.register(top)
        assertEquals(1f, baseInfo.animatedDepth, 0f)
        top.anchored.dispatchRawDelta(-200f)
        assertEquals(1.5f, baseInfo.animatedDepth, .001f)
        assertEquals(.5f, middleInfo.animatedDepth, .001f)
        top.anchored.dispatchRawDelta(-200f)
        assertEquals(2f, baseInfo.animatedDepth, .001f)
        assertEquals(1f, middleInfo.animatedDepth, .001f)
        top.anchored.dispatchRawDelta(100f)
        assertEquals(1.75f, baseInfo.animatedDepth, .001f)
        top.anchored.dispatchRawDelta(-100f)
        assertEquals(2f, baseInfo.animatedDepth, .001f)
        assertEquals(1f, baseInfo.coveredProgress, 0f)
    }

    @Test fun removalAndReopeningUsePresentationOrder() {
        val stack = SheetStackState()
        val first = sheet()
        val second = sheet()
        stack.register(first)
        stack.register(second)
        val firstInfo = AtlasSheetStackEntry(stack, first)
        val secondInfo = AtlasSheetStackEntry(stack, second)
        assertEquals(1, firstInfo.depth)
        stack.unregister(first)
        assertEquals(0, firstInfo.depth)
        assertFalse(firstInfo.isTopmost)
        assertTrue(secondInfo.isTopmost)
        stack.register(first)
        assertTrue(firstInfo.isTopmost)
        assertEquals(1, secondInfo.depth)
        stack.unregister(first)
        stack.unregister(first)
        assertEquals(1, stack.size)
    }

    @Test fun independentStacksDoNotAffectEachOther() {
        val one = SheetStackState()
        val two = SheetStackState()
        val first = sheet()
        val second = sheet()
        one.register(first)
        two.register(second)
        second.anchored.dispatchRawDelta(-400f)
        assertEquals(0f, AtlasSheetStackEntry(one, first).animatedDepth, 0f)
    }

    @Test(expected = IllegalStateException::class)
    fun sameStateCannotBeRegisteredTwice() {
        val stack = SheetStackState()
        val state = sheet()
        stack.register(state)
        stack.register(state)
    }

    @Test fun coverageIsBoundedAndHandlesZeroHeight() {
        assertEquals(0f, stackCoverage(-10f, 300f), 0f)
        assertEquals(1f, stackCoverage(800f, 300f), 0f)
        assertEquals(0f, stackCoverage(0f, 0f), 0f)
    }
}
