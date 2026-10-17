package dev.atlas.sheet

import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Test

class SheetSnapPointTest {
    @Test fun contentFitHasPositiveProvisionalHeightBeforeMeasurement() {
        val scope = SheetSnapPointScope(600.dp, 0.dp, 0.dp)
        assertTrue(SheetSnapPoint.fitContent().resolve(scope) > 0.dp)
    }
    @Test fun contentFitIncludesMeasuredStickyChrome() {
        val scope = SheetSnapPointScope(600.dp, 200.dp, 100.dp)
        assertEquals(300.dp, SheetSnapPoint.fitContent().resolve(scope))
    }
    @Test fun fractionsUseTheAvailableViewport() {
        val scope = SheetSnapPointScope(600.dp, 200.dp, 100.dp)
        assertEquals(300.dp, SheetSnapPoint.fraction("half", .5f).resolve(scope))
    }
    @Test(expected = IllegalArgumentException::class) fun invalidFractionIsRejected() { SheetSnapPoint.fraction("bad", Float.NaN) }
}
