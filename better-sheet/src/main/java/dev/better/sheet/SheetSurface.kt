package dev.better.sheet

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape

/** Where Better Sheet is asking a material to paint. */
public enum class SheetSurfaceRegion {
    /** The full sheet, including reserved system-bar space inside its shape. */
    Body,
    /** Separate rectangular continuation below a standard bottom sheet. */
    NavigationBar,
}

/** Shadow policy is independent of paint. Top sheets retain their existing zero elevation. */
public enum class SheetSurfaceShadow { Default, None }

/** Geometry supplied by the host; the app continues to supply header, body and footer UI. */
@Immutable
public class SheetSurfaceScope internal constructor(
    /** Resolved shape, including the current tray morph or mirrored top-sheet corners. */
    public val shape: Shape,
    public val edge: SheetEdge,
    public val presentation: SheetPresentation,
    public val region: SheetSurfaceRegion,
)

/**
 * Optional replacement for the solid [androidx.compose.foundation.background] paint.
 * Use `SheetSurface { Modifier.drawBehind { ... } }` or a renderer's drawing modifier.
 *
 * The modifier must draw its content and must not change measurement, padding, placement,
 * input handling or semantics. Better Sheet owns those, plus clipping to [SheetSurfaceScope.shape].
 * The callback can run for both [SheetSurfaceRegion] values; do not assume a single invocation.
 * Modifier-local coordinates are local to each region. Use region-aware paint if continuity
 * matters (for example a gradient), or an absolute-coordinate backdrop source.
 *
 * Default shadow is preserved. Set [shadow] to [SheetSurfaceShadow.None] when the material
 * supplies its own shadow through the outer `surfaceModifier`. A null sheet `surface` preserves the existing solid rendering.
 * This API does not create backdrop capture or bridge separate windows for renderers.
 */
@Stable
public class SheetSurface(
    public val shadow: SheetSurfaceShadow = SheetSurfaceShadow.Default,
    private val modifier: @Composable SheetSurfaceScope.() -> Modifier,
) {
    @Composable
    internal fun modifier(scope: SheetSurfaceScope): Modifier = modifier.invoke(scope)
}
