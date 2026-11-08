package dev.better.sheet

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect

/**
 * Clip pixels in the draw pass, not the RenderNode layer. Animated layer outlines can leave
 * a sheet's display list empty on some renderers (see PR #16). Keep transforms on layers,
 * but use this one clipping path for surface, stack, and viewport boundaries.
 * The provider is read inside the cache so animated shapes invalidate drawing directly.
 */
internal fun Modifier.sheetDrawClip(shape: () -> Shape?): Modifier = drawWithCache {
    val path = shape()?.let { value ->
        Path().apply { addOutline(value.createOutline(size, layoutDirection, this@drawWithCache)) }
    }
    onDrawWithContent {
        if (path == null) drawContent() else clipPath(path) { this@onDrawWithContent.drawContent() }
    }
}

/** Reserved navigation pixels belong to the surface, never to a child's elevation shadow. */
internal fun Modifier.sheetNavigationCover(color: Color, heightPx: Float): Modifier = drawWithContent {
    drawContent()
    val height = heightPx.coerceIn(0f, size.height)
    if (height > 0f) drawRect(color, Offset(0f, size.height - height), Size(size.width, height))
}

/** Paint a custom tray once over its full height; keep child effects above its navigation area. */
internal fun Modifier.sheetContentAboveNavigation(heightPx: Float): Modifier = drawWithContent {
    clipRect(bottom = (size.height - heightPx.coerceIn(0f, size.height))) {
        this@drawWithContent.drawContent()
    }
}

/**
 * A tray's depth must not change when its height changes. Android elevation projects a spot
 * shadow from the window light source, which can look like a second offset tray while morphing.
 * A fixed drop shadow follows the same animated outline without changing blur, opacity or offset.
 * Standard sheets still keep their elevation shadow out of the navigation area.
 */
internal fun Modifier.sheetSurfaceShadow(elevation: Dp, shape: Shape, floating: Boolean): Modifier =
    if (floating) dropShadow(
        shape = shape,
        shadow = Shadow(radius = elevation, color = Color.Black.copy(alpha = 0.2f), offset = DpOffset(0.dp, 2.dp)),
    ) else drawWithContent {
        val spread = elevation.toPx() * 3f
        clipRect(left = -spread, top = -spread, right = size.width + spread, bottom = size.height) {
            this@drawWithContent.drawContent()
        }
    }.shadow(elevation, shape, clip = false)
