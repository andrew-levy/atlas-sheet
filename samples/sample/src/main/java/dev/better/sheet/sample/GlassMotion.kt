package dev.better.sheet.sample

import android.graphics.RectF
import android.view.Choreographer
import android.view.View
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Motion belongs to the glass surface, not to a particular menu or gesture detector.
 * Springs are driven by display vsync only until they settle. No perpetual invalidation.
 *
 * Shape coordinates are local to GlassView. A full-window GlassView can therefore morph
 * and merge pills anywhere in that window, without creating or destroying child Views.
 */
internal class GlassMotion(private val view: View) : Choreographer.FrameCallback {
    private class Spring(initial: Float = 0f) {
        var value: Float = initial
            private set
        private var target = initial
        private var velocity = 0f
        val settled: Boolean
            get() = abs(value - target) < 0.12f && abs(velocity) < 1.2f

        fun snap(next: Float) {
            value = next
            target = next
            velocity = 0f
        }

        fun to(next: Float) {
            if (next.isFinite()) target = next
        }

        fun step(seconds: Float) {
            // Semi-implicit Euler with substeps avoids unstable large-vsync jumps.
            var time = seconds.coerceIn(0f, .05f)
            while (time > 0f) {
                val dt = min(.008f, time)
                val omega = 18.5f // ~0.34s natural period
                val damping = .78f
                velocity += ((target - value) * omega * omega -
                    2f * damping * omega * velocity) * dt
                value += velocity * dt
                time -= dt
            }
            if (settled) snap(target)
        }
    }

    private val left = Spring()
    private val top = Spring()
    private val right = Spring()
    private val bottom = Spring()
    private val radius = Spring()
    private val secondLeft = Spring()
    private val secondTop = Spring()
    private val secondRight = Spring()
    private val secondBottom = Spring()
    private val secondRadius = Spring()
    private val union = Spring()
    private val fingerDown = Spring()
    private val dragX = Spring()
    private val dragY = Spring()
    private val glow = Spring()
    private val channels = listOf(
        left, top, right, bottom, radius,
        secondLeft, secondTop, secondRight, secondBottom, secondRadius,
        union, fingerDown, dragX, dragY, glow
    )

    val primary = RectF()
    val secondary = RectF()
    var primaryRadius = 0f
        private set
    var secondaryRadius = 0f
        private set
    var mergeAmount = 0f
        private set
    var smoothness = 0f
        private set
    var pressAmount = 0f
        private set
    var leanX = 0f
        private set
    var leanY = 0f
        private set
    var glowAmount = 0f
        private set
    var lightX = 0f
        private set
    var lightY = 0f
        private set

    private var initialized = false
    private var customShape = false
    private var pending = false
    private var previousFrame = 0L
    private var downX = 0f
    private var downY = 0f

    fun useDefault(width: Int, height: Int, corner: Float) {
        if (customShape) return
        val bounds = RectF(0f, 0f, width.toFloat(), height.toFloat())
        snapShape(bounds, corner, false)
    }

    fun snapShape(bounds: RectF, corner: Float, explicit: Boolean = true) {
        require(bounds.width() > 0f && bounds.height() > 0f && corner >= 0f)
        customShape = explicit
        initialized = true
        left.snap(bounds.left); top.snap(bounds.top)
        right.snap(bounds.right); bottom.snap(bounds.bottom)
        radius.snap(corner)
        // An explicit new demo/shape must not inherit an unfinished merge or
        // press animation from the previous screen.
        union.snap(0f)
        fingerDown.snap(0f)
        dragX.snap(0f)
        dragY.snap(0f)
        glow.snap(0f)
        publish()
        view.invalidate()
    }

    fun morphTo(bounds: RectF, corner: Float) {
        require(bounds.width() > 0f && bounds.height() > 0f && corner >= 0f)
        if (!initialized) snapShape(bounds, corner)
        customShape = true
        left.to(bounds.left); top.to(bounds.top)
        right.to(bounds.right); bottom.to(bounds.bottom)
        radius.to(corner)
        animate()
    }

    /** Add a second lobe and smoothly join it to the first. */
    fun mergeWith(bounds: RectF, corner: Float, neck: Float) {
        require(bounds.width() > 0f && bounds.height() > 0f && corner >= 0f && neck >= 0f)
        if (!initialized) return
        if (union.value < .01f) {
            val cx = primary.centerX()
            val cy = primary.centerY()
            secondLeft.snap(cx - .5f); secondRight.snap(cx + .5f)
            secondTop.snap(cy - .5f); secondBottom.snap(cy + .5f)
            secondRadius.snap(.5f)
        }
        secondLeft.to(bounds.left); secondTop.to(bounds.top)
        secondRight.to(bounds.right); secondBottom.to(bounds.bottom)
        secondRadius.to(corner)
        smoothness = neck
        union.to(1f)
        animate()
    }

    fun separate() {
        union.to(0f)
        animate()
    }

    fun press(x: Float, y: Float) {
        downX = x; downY = y
        lightX = x; lightY = y
        fingerDown.to(1f)
        glow.to(1f)
        animate()
    }

    fun drag(x: Float, y: Float) {
        lightX = x; lightY = y
        val unit = view.resources.displayMetrics.density
        val reach = 38f * unit
        dragX.to((x - downX).coerceIn(-reach, reach))
        dragY.to((y - downY).coerceIn(-reach, reach))
        animate()
    }

    fun release() {
        fingerDown.to(0f); dragX.to(0f); dragY.to(0f); glow.to(0f)
        animate()
    }

    private fun publish() {
        primary.set(left.value, top.value, right.value, bottom.value)
        secondary.set(secondLeft.value, secondTop.value, secondRight.value, secondBottom.value)
        primaryRadius = radius.value.coerceAtLeast(0f)
        secondaryRadius = secondRadius.value.coerceAtLeast(0f)
        mergeAmount = union.value.coerceIn(0f, 1f)
        pressAmount = fingerDown.value.coerceIn(0f, 1.4f)
        leanX = dragX.value
        leanY = dragY.value
        glowAmount = glow.value.coerceIn(0f, 1f)
    }

    private fun animate() {
        if (!view.isAttachedToWindow) {
            view.invalidate()
            return
        }
        if (pending) return
        pending = true
        previousFrame = 0L
        Choreographer.getInstance().postFrameCallback(this)
    }

    fun onAttached() {
        if (channels.any { !it.settled }) animate()
    }

    fun onDetached() {
        Choreographer.getInstance().removeFrameCallback(this)
        pending = false
        previousFrame = 0L
    }

    override fun doFrame(frameTimeNanos: Long) {
        pending = false
        if (!view.isAttachedToWindow) return
        val dt = if (previousFrame == 0L) 1f / max(view.display?.refreshRate ?: 60f, 30f)
            else ((frameTimeNanos - previousFrame) / 1_000_000_000f).coerceIn(.001f, .05f)
        previousFrame = frameTimeNanos
        channels.forEach { it.step(dt) }
        publish()
        view.invalidate()
        if (channels.any { !it.settled }) {
            pending = true
            Choreographer.getInstance().postFrameCallback(this)
        } else {
            previousFrame = 0L
        }
    }
}
