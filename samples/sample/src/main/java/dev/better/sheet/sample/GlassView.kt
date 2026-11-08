package dev.better.sheet.sample

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.AttributeSet
import android.view.Choreographer
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import kotlin.math.min

/**
 * Reusable liquid glass overlay for a normal Android View hierarchy.
 *
 * - The source is another View in the same window (not a parent of this overlay).
 * - Shapes are expressed in this View's local coordinates. A full-screen GlassView
 *   can therefore morph between two independent control rectangles.
 * - Physics/animation is display-synchronised and finishes when settled.
 * - API 33+ draws refracted GPU glass; older versions get a tinted shape fallback.
 */
class GlassView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {
    private val motion = GlassMotion(this)
    private val shader = if (Build.VERSION.SDK_INT >= 33) LensShader() else null
    private val sourceMatrix = Matrix()
    private val inverseMatrix = Matrix()
    private val localMatrix = Matrix()
    private val fallbackPath = Path()
    private val secondPath = Path()
    private val tint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x30FFFFFF }

    private var capturing = false
    private var observedTree: ViewTreeObserver? = null

    var backdrop: View? = null
        set(value) {
            require(value !== this) { "Glass cannot sample itself" }
            // Recheck when the view is attached, after final parentage is known.
            var ancestor = parent
            while (ancestor is View) {
                require(ancestor !== value) { "Backdrop cannot contain the glass overlay" }
                ancestor = ancestor.parent
            }
            stopObserving()
            field = value
            observe()
            invalidate()
        }

    var cornerRadius = 36f * resources.displayMetrics.density
        set(value) {
            require(value.isFinite() && value >= 0f)
            field = value
            if (width > 0 && height > 0) motion.useDefault(width, height, value)
            invalidate()
        }
    var blurRadius = 1.2f * resources.displayMetrics.density
        set(value) { require(value.isFinite() && value >= 0f); field = value; invalidate() }
    var refractionHeight = 26f * resources.displayMetrics.density
        set(value) { require(value.isFinite() && value >= 0f); field = value; invalidate() }
    var refractionAmount = 34f * resources.displayMetrics.density
        set(value) { require(value.isFinite() && value >= 0f); field = value; invalidate() }

    /** Enables springy deformation for interactive glass. Does not consume touches. */
    var jellyEnabled = true

    /**
     * Animated backdrops: continuously sync to vsync, at display refresh rate.
     * Defaults OFF. Static/scrolling content is updated on demand, so it stays idle.
     * A permanently-live source costs capture/GPU time and should be opt-in.
     */
    var live = false
        set(value) {
            if (field == value) return
            field = value
            Choreographer.getInstance().removeFrameCallback(backdropFrame)
            if (value && isAttachedToWindow) {
                Choreographer.getInstance().postFrameCallback(backdropFrame)
            }
            invalidate()
        }

    private val backdropFrame = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!isAttachedToWindow || !live) return
            if (isShown && windowVisibility == VISIBLE) postInvalidateOnAnimation()
            Choreographer.getInstance().postFrameCallback(this)
        }
    }
    private val onScroll = ViewTreeObserver.OnScrollChangedListener {
        postInvalidateOnAnimation()
    }

    private fun observe() {
        if (!isAttachedToWindow) return
        backdrop?.viewTreeObserver?.takeIf { it.isAlive }?.let {
            it.addOnScrollChangedListener(onScroll)
            observedTree = it
        }
    }

    private fun stopObserving() {
        observedTree?.takeIf { it.isAlive }?.removeOnScrollChangedListener(onScroll)
        observedTree = null
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        observe()
        motion.onAttached()
        if (live) Choreographer.getInstance().postFrameCallback(backdropFrame)
    }

    override fun onDetachedFromWindow() {
        Choreographer.getInstance().removeFrameCallback(backdropFrame)
        motion.onDetached()
        stopObserving()
        shader?.release()
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) motion.useDefault(w, h, cornerRadius)
    }

    /** Read-only local geometry, useful for layout integration and deterministic tests. */
    val currentShapeBounds: RectF get() = RectF(motion.primary)
    val mergeProgress: Float get() = motion.mergeAmount

    /** Set a shape immediately, in local pixel coordinates. */
    fun setShape(bounds: RectF, radius: Float = cornerRadius) {
        motion.snapShape(bounds, radius)
    }

    /** Animate a rectangle into another rectangle, including radius and position. */
    fun morphTo(bounds: RectF, radius: Float = cornerRadius) {
        motion.morphTo(bounds, radius)
    }

    /** Grow a second glass lobe and form a smooth neck between both surfaces. */
    fun mergeWith(bounds: RectF, radius: Float = cornerRadius, smoothness: Float = 28f * resources.displayMetrics.density) {
        motion.mergeWith(bounds, radius, smoothness)
    }

    /** Remove the second lobe with a spring animation. */
    fun separate() {
        motion.separate()
    }

    /** Optional explicit gesture forwarding when an owner handles touch externally. */
    fun pressAt(x: Float, y: Float) { if (jellyEnabled) motion.press(x, y) }
    fun dragTo(x: Float, y: Float) { if (jellyEnabled) motion.drag(x, y) }
    fun releaseTouch() { if (jellyEnabled) motion.release() }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        // Full-window non-clickable glass overlays pass touch through to controls.
        if (jellyEnabled && isClickable) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> pressAt(event.x, event.y)
                MotionEvent.ACTION_MOVE -> dragTo(event.x, event.y)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> releaseTouch()
            }
        }
        return super.dispatchTouchEvent(event)
    }

    // Avoid API 29-only transformMatrixToGlobal; use parent offsets on API 24+.
    private fun matrixToRoot(view: View, matrix: Matrix) {
        val parentView = view.parent as? View
        if (parentView != null) {
            matrixToRoot(parentView, matrix)
            matrix.preTranslate(-parentView.scrollX.toFloat(), -parentView.scrollY.toFloat())
        }
        matrix.preTranslate(view.left.toFloat(), view.top.toFloat())
        if (!view.matrix.isIdentity) matrix.preConcat(view.matrix)
    }

    override fun onDraw(canvas: Canvas) {
        val source = backdrop ?: return
        if (capturing || width == 0 || height == 0 || !source.isAttachedToWindow) return
        sourceMatrix.reset()
        matrixToRoot(source, sourceMatrix)
        localMatrix.reset()
        matrixToRoot(this, localMatrix)
        if (!localMatrix.invert(inverseMatrix)) return
        sourceMatrix.setConcat(inverseMatrix, sourceMatrix)
        // source.draw() already applies the source's own internal scroll.
        // Parent scrolling is handled in matrixToRoot; subtracting source
        // scroll here would double-offset a ScrollView backdrop.
        if (motion.primary.isEmpty) motion.useDefault(width, height, cornerRadius)

        capturing = true
        try {
            if (Build.VERSION.SDK_INT >= 33 && canvas.isHardwareAccelerated) {
                shader!!.draw(canvas, source, sourceMatrix, width, height, motion,
                    blurRadius, refractionHeight, refractionAmount,
                    resources.displayMetrics.density)
            } else drawFallback(canvas, source)
        } finally {
            capturing = false
        }
    }

    private fun drawFallback(canvas: Canvas, source: View) {
        fallbackPath.reset()
        val first = motion.primary
        val r = motion.primaryRadius.coerceAtMost(min(first.width(), first.height()) / 2f)
        fallbackPath.addRoundRect(first, r, r, Path.Direction.CW)
        if (motion.mergeAmount > .01f) {
            secondPath.reset()
            val other = motion.secondary
            val radius = motion.secondaryRadius.coerceAtMost(min(other.width(), other.height()) / 2f)
            secondPath.addRoundRect(other, radius, radius, Path.Direction.CW)
            fallbackPath.op(secondPath, Path.Op.UNION)
        }
        val saved = canvas.save()
        canvas.clipPath(fallbackPath)
        canvas.concat(sourceMatrix)
        source.draw(canvas)
        canvas.restoreToCount(saved)
        canvas.drawPath(fallbackPath, tint)
    }
}
