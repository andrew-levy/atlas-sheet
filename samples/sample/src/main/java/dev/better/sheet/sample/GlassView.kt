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
            field = value
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
    /** Refresh while the window is rendering. Disable for static scenes and invalidate explicitly. */
    var live = true
        set(value) { field = value; invalidate() }
    private val sourceMatrix = Matrix()
    private val inverse = Matrix()
    private val localMatrix = Matrix()
    private val bounds = RectF()
    private val clip = Path()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val renderer = if (Build.VERSION.SDK_INT >= 33) LensShader() else null
    private var capturing = false
    private var observer: ViewTreeObserver? = null
    private val refresh = ViewTreeObserver.OnPreDrawListener {
        if (live && isShown && windowVisibility == VISIBLE) invalidate()
        true
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        observer = viewTreeObserver.also { it.addOnPreDrawListener(refresh) }
    }

    override fun onDetachedFromWindow() {
        observer?.takeIf { it.isAlive }?.removeOnPreDrawListener(refresh)
        observer = null
        if (Build.VERSION.SDK_INT >= 33) renderer?.release()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val source = backdrop ?: return
        if (capturing || width == 0 || height == 0 || !source.isAttachedToWindow) return
        // Global matrices account for nested scrolling, translation, rotation and scale.
        sourceMatrix.reset()
        source.transformMatrixToGlobal(sourceMatrix)
        localMatrix.reset()
        transformMatrixToGlobal(localMatrix)
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
