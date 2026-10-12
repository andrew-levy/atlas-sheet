package dev.atlas.sheet

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Inputs are density-independent, after safe-area and optional IME exclusion. */
@Immutable
public data class SheetDetentScope(
    val availableHeight: Dp,
    val contentHeight: Dp,
    val chromeHeight: Dp,
)

/** Stable IDs survive configuration changes. Custom resolvers must be pure and inexpensive. */
public class SheetDetent private constructor(
    public val id: String,
    internal val resolve: SheetDetentScope.() -> Dp,
    internal val requiresContentMeasurement: Boolean = false,
) {
    init { require(id.isNotBlank() && id != HiddenId) { "Detent IDs must be nonblank and not reserved" } }
    public companion object {
        public fun fraction(id: String, fraction: Float): SheetDetent {
            require(fraction.isFinite() && fraction > 0f && fraction <= 1f)
            return SheetDetent(id, { availableHeight * fraction })
        }
        public fun fixed(id: String, height: Dp): SheetDetent {
            require(height.value.isFinite() && height > 0.dp)
            return SheetDetent(id, { height })
        }
        public fun content(id: String = "content"): SheetDetent =
            SheetDetent(id, { contentHeight + chromeHeight }, requiresContentMeasurement = true)
        public fun expanded(id: String = "expanded"): SheetDetent = fraction(id, 1f)
        public fun custom(id: String, resolve: SheetDetentScope.() -> Dp): SheetDetent = SheetDetent(id, resolve)
    }
}

internal const val HiddenId = "__atlas_hidden"
