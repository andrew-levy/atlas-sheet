package dev.better.sheet.sample

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.AttributeSet
import android.view.View
import android.view.ViewTreeObserver
import kotlin.math.min

/** A rounded glass overlay. The backdrop must not contain this view or another cyclic backdrop.
 * Dimensions are pixels, matching other View drawing APIs. Hardware refraction requires API 33.
 * TextureView/SurfaceView and separate windows are not captured by View.draw().
 */
class GlassView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    var backdrop: View? = null
        set(value) {
            require(value !== this) { "A glass view cannot sample itself" }
            var ancestor = parent
            while (ancestor is View) {
                require(ancestor !== value) { "The backdrop must not contain the glass view" }
                ancestor = ancestor.parent
            }
            stopObservingBackdrop()
            field = value
            observeBackdrop()
            invalidate()
        }
    var cornerRadius = 36f * resources.displayMetrics.density
        set(value) { require(value.isFinite() && value >= 0); field = value; invalidate() }
    var blurRadius = 2f * resources.displayMetrics.density
        set(value) { require(value.isFinite() && value >= 0); field = value; invalidate() }
    var refractionHeight = 24f * resources.displayMetrics.density
        set(value) { require(value.isFinite() && value >= 0); field = value; invalidate() }
    var refractionAmount = 28f * resources.displayMetrics.density
        set(value) { require(value.isFinite() && value >= 0); field = value; invalidate() }
    /** Poll changing/animated sources at up to 20 fps. Scroll and lens movement
     * invalidate on demand, so use live=false for static/scrollable content.
     * Never invalidate from OnPreDraw: that makes the UI permanently non-idle.
     */
    var live = true
        set(value) {
            if (field == value) return
            field = value
            removeCallbacks(refresh)
            if (value && isAttachedToWindow) postDelayed(refresh, 50L)
            invalidate()
        }
    private val sourceMatrix = Matrix()
    private val inverse = Matrix()
    private val localMatrix = Matrix()
    private val bounds = RectF()
    private val clip = Path()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val renderer = if (Build.VERSION.SDK_INT >= 33) LensShader() else null
    private var capturing = false
    private var observedSource: ViewTreeObserver? = null
    private val onSourceScroll = ViewTreeObserver.OnScrollChangedListener {
        // On demand: scrolling updates the sampled region without a render loop.
        postInvalidateOnAnimation()
    }
    private val refresh = object : Runnable {
        override fun run() {
            if (!isAttachedToWindow || !live) return
            if (isShown && windowVisibility == VISIBLE) invalidate()
            // Leave idle gaps for instrumentation and keep capture cost bounded.
            postDelayed(this, 50L)
        }
    }

    private fun observeBackdrop() {
        if (!isAttachedToWindow) return
        backdrop?.viewTreeObserver?.takeIf { it.isAlive }?.let {
            it.addOnScrollChangedListener(onSourceScroll)
            observedSource = it
        }
    }

    private fun stopObservingBackdrop() {
        observedSource?.takeIf { it.isAlive }?.removeOnScrollChangedListener(onSourceScroll)
        observedSource = null
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        observeBackdrop()
        if (live) postDelayed(refresh, 50L)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(refresh)
        stopObservingBackdrop()
        if (Build.VERSION.SDK_INT >= 33) renderer?.release()
        super.onDetachedFromWindow()
    }

    // Equivalent local-to-root mapping using APIs available on API 24 as well.
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
        // Global matrices account for nested scrolling, translation, rotation and scale.
        sourceMatrix.reset()
        matrixToRoot(source, sourceMatrix)
        localMatrix.reset()
        matrixToRoot(this, localMatrix)
        if (!localMatrix.invert(inverse)) return
        sourceMatrix.setConcat(inverse, sourceMatrix)
        sourceMatrix.preTranslate(-source.scrollX.toFloat(), -source.scrollY.toFloat())
        val radius = cornerRadius.coerceAtMost(min(width, height) / 2f)
        bounds.set(0f, 0f, width.toFloat(), height.toFloat())
        clip.reset()
        clip.addRoundRect(bounds, radius, radius, Path.Direction.CW)
        val save = canvas.save()
        canvas.clipPath(clip)
        capturing = true
        try {
            if (Build.VERSION.SDK_INT >= 33 && canvas.isHardwareAccelerated) {
                renderer!!.draw(canvas, source, sourceMatrix, width, height, radius,
                    blurRadius, refractionHeight, refractionAmount, resources.displayMetrics.density)
            } else {
                val captureSave = canvas.save()
                canvas.concat(sourceMatrix)
                source.draw(canvas)
                canvas.restoreToCount(captureSave)
                paint.color = 0x30FFFFFF
                canvas.drawRoundRect(bounds, radius, radius, paint)
            }
        } finally {
            capturing = false
            canvas.restoreToCount(save)
        }
    }
}
