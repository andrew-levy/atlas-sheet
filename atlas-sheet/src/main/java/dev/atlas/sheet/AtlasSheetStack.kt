package dev.atlas.sheet

import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Navigation for one modal stack. Call push on the UI thread; pop animates before removal. */
@Stable
public class AtlasSheetStackState internal constructor(keys: List<String>) {
    internal val entries = mutableStateListOf<StackEntry>().apply {
        keys.forEach { add(StackEntry(it, "expanded")) }
    }
    public val keys: List<String> get() = entries.map { it.key }

    /** Keys may repeat: each push creates an independent sheet instance. */
    public fun push(key: String, initialDetent: String = "expanded") {
        entries.add(StackEntry(key, initialDetent))
    }

    /** Only the captured top entry is removed, even if another entry is pushed during dismissal. */
    public suspend fun pop() {
        val entry = entries.lastOrNull() ?: return
        entry.state.hide()
        remove(entry)
    }

    internal fun remove(entry: StackEntry) { entries.remove(entry) }
}

internal class StackEntry(val key: String, initialDetent: String) {
    val state = SheetState(initialDetent, spring(
        dampingRatio = IosSheetSpringDampingRatio,
        stiffness = IosSheetSpringStiffness,
    )).apply { preparePresentation() }
}

/** Restores navigation keys and the last visible detents after Activity recreation. */
@Composable
public fun rememberAtlasSheetStackState(initialKey: String? = null): AtlasSheetStackState {
    val saver = Saver<AtlasSheetStackState, List<String>>(
        save = { stack -> stack.entries.flatMap { listOf(it.key, it.state.targetDetent ?: it.state.lastVisibleDetent) } },
        restore = { saved -> AtlasSheetStackState(emptyList()).apply {
            saved.chunked(2).forEach { (key, detent) -> push(key, detent) }
        } },
    )
    return rememberSaveable(saver = saver) { AtlasSheetStackState(listOfNotNull(initialKey)) }
}

/** Live stack information. Read progress in drawing/layout lambdas when possible. */
@Stable
public class AtlasSheetStackEntry internal constructor(
    private val stack: AtlasSheetStackState,
    private val entry: StackEntry,
) {
    public val key: String get() = entry.key
    public val sheetState: SheetState get() = entry.state
    public val depth: Int get() = (stack.entries.lastIndex - stack.entries.indexOf(entry)).coerceAtLeast(0)
    public val isTopmost: Boolean get() = stack.entries.lastOrNull() === entry
    /** 0 when uncovered; 1 once the immediately covering sheet reaches its smallest detent. */
    public val coveredProgress: Float get() {
        val index = stack.entries.indexOf(entry)
        if (index < 0) return 0f
        val above = stack.entries.getOrNull(index + 1)?.state ?: return 0f
        val geometry = above.geometry ?: return 0f
        val height = geometry.availableHeight - geometry.offsets.getValue(geometry.smallest)
        return stackCoverage(geometry.availableHeight - above.offsetOrHidden(), height)
    }
}

internal fun stackCoverage(visibleHeight: Float, restingHeight: Float): Float =
    if (restingHeight <= 0f) 0f else (visibleHeight / restingHeight).coerceIn(0f, 1f)

/** Available inside a stack sheet, including its surface modifier. */
public val LocalAtlasSheetStackEntry: ProvidableCompositionLocal<AtlasSheetStackEntry> =
    staticCompositionLocalOf { error("No AtlasSheetStack entry in this composition") }

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
 * Applied via AtlasSheetStackScope.sheet's modifier. Does not change content measurement.
 * Use ordinary padding before this modifier to change layout width instead of scaling text.
 */
public fun Modifier.atlasStackEffect(
    effect: AtlasStackEffectScope.(AtlasSheetStackEntry) -> Unit,
): Modifier = composed {
    val entry = LocalAtlasSheetStackEntry.current
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

/** Definitions are rebuilt with composition, so callbacks and captured UI state stay current. */
public class AtlasSheetStackScope internal constructor() {
    internal val definitions = linkedMapOf<String, StackDefinition>()

    /** [modifier] affects the surface only. No stack transformation is imposed by default. */
    public fun sheet(
        key: String,
        detents: List<SheetDetent> = listOf(SheetDetent.expanded()),
        modifier: Modifier = Modifier,
        behavior: SheetBehavior = SheetBehavior(),
        title: String = key,
        bodyMode: SheetBodyMode = SheetBodyMode.Scroll,
        header: @Composable (() -> Unit)? = null,
        footer: @Composable (() -> Unit)? = null,
        content: @Composable ColumnScope.() -> Unit,
    ) {
        require(key !in definitions) { "Duplicate stack sheet definition '$key'" }
        definitions[key] = StackDefinition(detents, modifier, behavior, title, bodyMode, header, footer, content)
    }
}

internal class StackDefinition(
    val detents: List<SheetDetent>,
    val modifier: Modifier,
    val behavior: SheetBehavior,
    val title: String,
    val bodyMode: SheetBodyMode,
    val header: @Composable (() -> Unit)?,
    val footer: @Composable (() -> Unit)?,
    val content: @Composable ColumnScope.() -> Unit,
)

/**
 * Independent modal windows preserve focus, Back handling and touch isolation for the top sheet.
 * Keep this host mounted while empty. Each entry remains composed until its dismissal completes.
 */
@Composable
public fun AtlasSheetStack(
    state: AtlasSheetStackState,
    content: AtlasSheetStackScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    val definitions = AtlasSheetStackScope().apply(content).definitions
    state.entries.forEach { entry ->
        val definition = requireNotNull(definitions[entry.key]) { "No sheet registered for '${entry.key}'" }
        key(entry) {
            val info = remember(state, entry) { AtlasSheetStackEntry(state, entry) }
            CompositionLocalProvider(LocalAtlasSheetStackEntry provides info) {
                AtlasModalSheet(
                    state = entry.state,
                    detents = definition.detents,
                    onDismissRequest = { scope.launch {
                        // Modal hosts release background touches near the end of an exit.
                        // Retain this entry until zero height so its underlying effect cannot jump.
                        snapshotFlow { !entry.state.isVisible && !entry.state.isAnimationRunning &&
                            !entry.state.dragInProgress }.first { it }
                        state.remove(entry)
                    } },
                    behavior = definition.behavior,
                    title = definition.title,
                    bodyMode = definition.bodyMode,
                    surfaceModifier = definition.modifier,
                    header = definition.header,
                    footer = definition.footer,
                    content = definition.content,
                )
            }
        }
    }
}
