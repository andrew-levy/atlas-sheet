package dev.better.sheet.sample

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.*

/** Both views must be siblings. Only the part of the backdrop under the lens is sampled. */
class GlassView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    var backdrop: View? = null
    private val dp = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val clip = Path()
    private val bounds = RectF()
    private lateinit var sample: Bitmap
    private lateinit var capture: Canvas
    private val lens = if (android.os.Build.VERSION.SDK_INT >= 33) LensShader() else null
    private val cells = 12
    private val mesh = FloatArray((cells + 1) * (cells + 1) * 2)
    private var grabX = 0f
    private var grabY = 0f
    private var tilt = 0f

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (w == 0 || h == 0) return
        sample = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        capture = Canvas(sample)
        lens?.setBackdrop(sample)
    }

    override fun onDraw(canvas: Canvas) {
        val source = backdrop ?: return
        val w = width.toFloat()
        val h = height.toFloat()
        sample.eraseColor(Color.TRANSPARENT)
        capture.save()
        capture.scale(sample.width / w, sample.height / h)
        capture.translate(pivotX, pivotY)
        capture.scale(1 / scaleX, 1 / scaleY)
        capture.translate(source.x - x - pivotX, source.y - y - pivotY)
        capture.translate(-source.scrollX.toFloat(), -source.scrollY.toFloat())
        source.draw(capture)
        capture.restore()

        bounds.set(dp, dp, w - dp, h - dp)
        val corner = (34 + 12 * abs(tilt)) * dp
        clip.reset()
        clip.addRoundRect(bounds, corner, corner, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(clip)
        if (lens != null) {
            paint.color = Color.WHITE
            paint.shader = lens.frame(w, h, corner, dp, tilt)
            canvas.drawRoundRect(bounds, corner, corner, paint)
            paint.shader = null
        } else {
            var i = 0
            for (row in 0..cells) for (col in 0..cells) {
                val u = 2f * col / cells - 1
                val v = 2f * row / cells - 1
                val bend = (1 - u * u) * (1 - v * v)
                mesh[i++] = (1 + u + .20f * u * bend) * w / 2
                mesh[i++] = (1 + v + .27f * v * bend) * h / 2
            }
            paint.color = Color.WHITE
            canvas.drawBitmapMesh(sample, cells, cells, mesh, 0, null, 0, paint)
            paint.shader = LinearGradient(w * (.15f + tilt * .12f), 0f, w * .78f, h, 0x68FFFFFF, 0x22214452, Shader.TileMode.CLAMP)
            canvas.drawPath(clip, paint)
            paint.shader = null
        }
        canvas.restore()
        if (lens == null) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.6f * dp
            paint.shader = LinearGradient(w * (.2f + tilt * .2f), 0f, w * .8f, h, intArrayOf(0xEFFFFFFF.toInt(), 0x40FFFFFF, 0xA0FFFFFF.toInt()), null, Shader.TileMode.CLAMP)
            canvas.drawPath(clip, paint)
            paint.shader = null
            paint.style = Paint.Style.FILL
        }
        paint.color = Color.WHITE
        paint.textAlign = Paint.Align.CENTER
        paint.setShadowLayer(2 * dp, 0f, dp, 0x880D2930.toInt())
        paint.textSize = 21 * dp
        canvas.drawText("Alpine lake", w / 2, h * .48f, paint)
        paint.textSize = 13 * dp
        canvas.drawText("Drag glass · Scroll the scene", w / 2, h * .72f, paint)
        paint.clearShadowLayer()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                animate().cancel()
                grabX = event.rawX - x
                grabY = event.rawY - y
            }
            MotionEvent.ACTION_MOVE -> {
                val host = parent as View
                val nextX = (event.rawX - grabX).coerceIn(0f, max(0f, (host.width - width).toFloat()))
                tilt = ((nextX - x) / (8 * dp)).coerceIn(-1f, 1f)
                x = nextX
                y = (event.rawY - grabY).coerceIn(0f, max(0f, (host.height - height).toFloat()))
                scaleX = 1 + .045f * abs(tilt)
                scaleY = 1 - .035f * abs(tilt)
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                tilt = 0f
                animate().scaleX(1f).scaleY(1f).setDuration(180).setUpdateListener { invalidate() }.start()
                invalidate()
                if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
