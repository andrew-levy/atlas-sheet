package dev.atlas.sheet

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sign

/** Apple's system spring: perceptual duration 0.5s, bounce 0 (critically damped). */
internal const val IosSheetSpringDurationSeconds = 0.5
internal const val IosSheetSpringDampingRatio = 1f

/** stiffness = (2π / duration)^2, mass = 1. */
internal val IosSheetSpringStiffness: Float =
    (2.0 * PI / IosSheetSpringDurationSeconds).let { (it * it).toFloat() }

/** UIScrollView rubber-band coefficient used by iOS sheets. */
internal const val RubberBandCoefficient = 0.55f

/**
 * Resisted distance for a raw finger [overscroll] past a bound.
 * `dimension` is the sheet's available height, matching UIScrollView.
 */
internal fun rubberBand(
    overscroll: Float,
    dimension: Float,
    coefficient: Float = RubberBandCoefficient,
): Float {
    if (overscroll == 0f || dimension <= 0f) return 0f
    val distance = abs(overscroll)
    val resisted = (1f - 1f / (distance * coefficient / dimension + 1f)) * dimension
    return sign(overscroll) * resisted
}

/** Inverse of [rubberBand], so a spring on the visual offset can be dragged again. */
internal fun rubberRawFromVisual(
    visual: Float,
    dimension: Float,
    coefficient: Float = RubberBandCoefficient,
): Float {
    if (visual == 0f || dimension <= 0f) return 0f
    val y = abs(visual).coerceAtMost(dimension * 0.999f)
    val raw = dimension / coefficient * (1f / (1f - y / dimension) - 1f)
    return sign(visual) * raw
}

/**
 * An inward flick past [threshold] leaves the edge and settles to the next detent.
 * An outward or gentle release stays on the edge and springs the rubber band home.
 */
internal fun rubberReleaseSettlesAnchor(translation: Float, velocity: Float, threshold: Float): Boolean {
    if (translation == 0f) return true
    return velocity != 0f && sign(velocity) != sign(translation) && abs(velocity) >= threshold
}

/** 0 at hidden, 1 at the lowest detent, held at 1 above that. Ignored when [followsDrag] is false. */
internal fun scrimDragFraction(visibleHeight: Float, smallestDetentHeight: Float, followsDrag: Boolean): Float {
    if (!followsDrag) return 1f
    if (smallestDetentHeight <= 0f) return 1f
    return (visibleHeight / smallestDetentHeight).coerceIn(0f, 1f)
}
