package dev.atlas.sheet

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Shared coordination only: each modal owns its SheetState and visibility. */
@Stable
public class SheetStackState internal constructor() {
    internal val entries = mutableStateListOf<SheetState>()
    public val size: Int get() = entries.size

    internal fun register(state: SheetState) {
        check(state !in entries) { "A SheetState cannot be presented by two modals in the same stack" }
        entries.add(state)
    }

    internal fun unregister(state: SheetState) { entries.remove(state) }
}

/** Share this instance between sibling modals. Hidden mounted sheets do not join the stack. */
@Composable
public fun rememberSheetStackState(): SheetStackState = remember { SheetStackState() }

/** Live information for a modal's current presentation, available inside its content and modifiers. */
@Stable
public class AtlasSheetStackEntry internal constructor(
    private val stack: SheetStackState,
    public val sheetState: SheetState,
) {
    public val depth: Int get() {
        val index = stack.entries.indexOf(sheetState)
        return if (index < 0) 0 else stack.entries.lastIndex - index
    }
    public val isTopmost: Boolean get() = stack.entries.lastOrNull() === sheetState
    /** Progress of the immediately covering sheet, from hidden to its smallest detent. */
    public val coveredProgress: Float get() {
        val index = stack.entries.indexOf(sheetState)
        if (index < 0) return 0f
        return stack.entries.getOrNull(index + 1)?.stackPresentationProgress() ?: 0f
    }
    /** Sum of every covering sheet's progress: 0, 1, 2 at rest for a three-sheet stack. */
    public val animatedDepth: Float get() {
        val index = stack.entries.indexOf(sheetState)
        if (index < 0) return 0f
        var total = 0f
        for (i in index + 1 until stack.entries.size) total += stack.entries[i].stackPresentationProgress()
        return total
    }
}

private fun SheetState.stackPresentationProgress(): Float {
    val g = geometry ?: return 0f
    return stackCoverage(g.availableHeight - offsetOrHidden(), g.availableHeight - g.offsets.getValue(g.smallest))
}

internal fun stackCoverage(visibleHeight: Float, restingHeight: Float): Float =
    if (restingHeight <= 0f) 0f else (visibleHeight / restingHeight).coerceIn(0f, 1f)

/** Available inside a stack sheet, including its surface modifier. */
public val LocalAtlasSheetStackEntry: ProvidableCompositionLocal<AtlasSheetStackEntry?> =
    staticCompositionLocalOf { null }

/** Values reset to these defaults on each frame; translations and radii are density independent. */
public class AtlasStackEffectScope internal constructor() {
    public var scaleX: Float = 1f
    public var scaleY: Float = 1f
    public var translationX: Dp = 0.dp
    public var translationY: Dp = 0.dp
    public var alpha: Float = 1f
    public var cornerRadius: Dp? = null
    public var transformOrigin: TransformOrigin = TransformOrigin(0.5f, 1f)
}

/**
 * Transform the sheet surface without transforming its full-window scrim or input boundary.
 * Apply via surfaceModifier. Without a stack this modifier is a no-op. Does not change measurement.
 * Use ordinary padding before this modifier to change layout width instead of scaling text.
 */
public fun Modifier.atlasStackEffect(
    effect: AtlasStackEffectScope.(AtlasSheetStackEntry) -> Unit,
): Modifier = composed {
    val entry = LocalAtlasSheetStackEntry.current ?: return@composed Modifier
    graphicsLayer {
        val values = AtlasStackEffectScope().apply { effect(entry) }
        scaleX = values.scaleX
        scaleY = values.scaleY
        translationX = values.translationX.toPx()
        translationY = values.translationY.toPx()
        alpha = values.alpha
        transformOrigin = values.transformOrigin
        values.cornerRadius?.let {
            shape = RoundedCornerShape(topStart = it, topEnd = it)
            clip = true
        }
    }
}
