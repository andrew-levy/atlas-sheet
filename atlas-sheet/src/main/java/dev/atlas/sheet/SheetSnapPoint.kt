package dev.atlas.sheet

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Inputs are density-independent, after safe-area and optional IME exclusion. */
@Immutable
public data class SheetSnapPointScope(
    val availableHeight: Dp,
    val contentHeight: Dp,
    val chromeHeight: Dp,
)

/** Stable IDs survive configuration changes. Custom resolvers must be pure and inexpensive. */
public class SheetSnapPoint private constructor(
    public val id: String,
    internal val resolve: SheetSnapPointScope.() -> Dp,
    internal val requiresContentMeasurement: Boolean = false,
) {
    init { require(id.isNotBlank() && id != HiddenId) { "Detent IDs must be nonblank and not reserved" } }
    public companion object {
        public fun fraction(id: String, fraction: Float): SheetSnapPoint {
            require(fraction.isFinite() && fraction > 0f && fraction <= 1f)
            return SheetSnapPoint(id, { availableHeight * fraction })
        }
        public fun fixed(id: String, height: Dp): SheetSnapPoint {
            require(height.value.isFinite() && height > 0.dp)
            return SheetSnapPoint(id, { height })
        }
        public fun fitContent(id: String = "content"): SheetSnapPoint =
            SheetSnapPoint(id, { maxOf(1.dp, contentHeight + chromeHeight) }, requiresContentMeasurement = true)
        public fun expanded(id: String = "expanded"): SheetSnapPoint = fraction(id, 1f)
        public fun custom(id: String, resolve: SheetSnapPointScope.() -> Dp): SheetSnapPoint = SheetSnapPoint(id, resolve)
    }
}

internal const val HiddenId = "__atlas_hidden"
