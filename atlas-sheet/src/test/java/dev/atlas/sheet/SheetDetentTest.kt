package dev.atlas.sheet

import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Test

class SheetDetentTest {
    @Test fun contentFitHasPositiveProvisionalHeightBeforeMeasurement() {
        val scope = SheetDetentScope(600.dp, 0.dp, 0.dp)
        assertTrue(SheetDetent.content().resolve(scope) > 0.dp)
    }
    @Test fun contentFitIncludesMeasuredStickyChrome() {
        val scope = SheetDetentScope(600.dp, 200.dp, 100.dp)
        assertEquals(300.dp, SheetDetent.content().resolve(scope))
    }
    @Test fun fractionsUseTheAvailableViewport() {
        val scope = SheetDetentScope(600.dp, 200.dp, 100.dp)
        assertEquals(300.dp, SheetDetent.fraction("half", .5f).resolve(scope))
    }
    @Test(expected = IllegalArgumentException::class) fun invalidFractionIsRejected() { SheetDetent.fraction("bad", Float.NaN) }
}
