package dev.better.sheet.sample

import android.animation.ValueAnimator
import android.app.Activity
import android.os.Bundle
import android.graphics.*
import android.view.*
import android.view.animation.OvershootInterpolator
import android.widget.*
import kotlin.math.*

/** Demo interaction belongs here; GlassView is only a backdrop renderer. */
class GlassActivity : Activity() {
    private val dp get() = resources.displayMetrics.density
    private lateinit var root: FrameLayout
    lateinit var scene: ScrollView
        private set
    lateinit var glass: GlassView
        private set
    lateinit var tabBackdrop: FrameLayout
        private set
    lateinit var selection: GlassView
        private set
    private lateinit var tabs: LinearLayout
    private var tabAnimation: ValueAnimator? = null
    private var selected = 0
    private var grabX = 0f
    private var grabY = 0f
    private var tabDown = 0f
    private var tabStart = 0f
    private fun px(value: Int) = (value * dp).roundToInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(5, 9, 28)
        window.navigationBarColor = Color.rgb(5, 9, 28)
        root = FrameLayout(this).apply {
            setBackgroundColor(Color.rgb(5, 9, 28))
            fitsSystemWindows = true
        }
        setContentView(root)
        scene = ScrollView(this).apply {
            id = R.id.backdrop
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        scene.addView(ImageView(this).apply {
            setImageResource(R.drawable.glass_night_sky)
            scaleType = ImageView.ScaleType.CENTER_CROP
            contentDescription = "Blue, violet and coral nebula with stars"
        }, FrameLayout.LayoutParams(-1, (resources.displayMetrics.heightPixels * 1.65f).toInt()))
        root.addView(scene, FrameLayout.LayoutParams(-1, -1))
        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(this@GlassActivity).apply {
                text = "Liquid glass"; textSize = 32f; setTextColor(Color.WHITE)
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            })
            addView(TextView(this@GlassActivity).apply {
                text = "Drag the lens. Scroll the stars."; textSize = 15f
                setTextColor(0xCCDDE6FF.toInt())
            })
        }, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START).apply {
            leftMargin = px(28); topMargin = px(38)
        })
        glass = GlassView(this).apply {
            id = R.id.glass
            backdrop = scene
            contentDescription = "Draggable glass lens"
            cornerRadius = px(42).toFloat()
            refractionHeight = px(32).toFloat()
            refractionAmount = px(40).toFloat()
            blurRadius = dp * 1.2f
        }
        root.addView(glass, FrameLayout.LayoutParams(px(210), px(144), Gravity.CENTER))
        glass.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    view.animate().cancel()
                    grabX = event.rawX - view.x; grabY = event.rawY - view.y
                    view.parent.requestDisallowInterceptTouchEvent(true)
                }
                MotionEvent.ACTION_MOVE -> {
                    view.x = (event.rawX - grabX).coerceIn(0f, max(0f, (root.width - view.width).toFloat()))
                    view.y = (event.rawY - grabY).coerceIn(0f, max(0f, (root.height - view.height).toFloat()))
                    view.invalidate()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    view.parent.requestDisallowInterceptTouchEvent(false)
                    if (event.actionMasked == MotionEvent.ACTION_UP) view.performClick()
                }
            }
            true
        }
        tabBackdrop = FrameLayout(this)
        root.addView(tabBackdrop, FrameLayout.LayoutParams(-1, px(76), Gravity.BOTTOM).apply {
            leftMargin = px(20); rightMargin = px(20); bottomMargin = px(30)
        })
        tabBackdrop.addView(GlassView(this).apply {
            backdrop = scene; cornerRadius = px(38).toFloat()
            blurRadius = px(8).toFloat(); refractionHeight = px(22).toFloat()
            refractionAmount = px(22).toFloat()
        }, FrameLayout.LayoutParams(-1, -1))
        tabs = LinearLayout(this).apply { setPadding(px(4), 0, px(4), 0) }
        tabBackdrop.addView(tabs, FrameLayout.LayoutParams(-1, -1))
        listOf("Explore", "Saved", "Profile").forEachIndexed { index, label ->
            tabs.addView(TabIcon(this, index, label).apply {
                contentDescription = label
                isClickable = true; isFocusable = true
                setOnClickListener { selectTab(index) }
            }, LinearLayout.LayoutParams(0, -1, 1f))
        }
        selection = GlassView(this).apply {
            backdrop = tabBackdrop; cornerRadius = px(32).toFloat()
            blurRadius = 0f; refractionHeight = px(15).toFloat(); refractionAmount = px(20).toFloat()
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        root.addView(selection, FrameLayout.LayoutParams(1, px(64)))
        root.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            val slot = (tabBackdrop.width - px(8)) / 3f
            if (slot > 0 && selection.layoutParams.width != slot.roundToInt()) {
                selection.layoutParams = selection.layoutParams.apply { width = slot.roundToInt() }
            }
            setTabPosition(selected.toFloat())
        }
        selection.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    tabAnimation?.cancel(); tabDown = event.rawX
                    tabStart = currentTabPosition()
                    view.animate().scaleX(1.15f).scaleY(1.12f).setDuration(160).start()
                    view.parent.requestDisallowInterceptTouchEvent(true)
                }
                MotionEvent.ACTION_MOVE -> setTabPosition(tabStart + (event.rawX - tabDown) / selection.width)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    view.animate().scaleX(1f).scaleY(1f).setDuration(220).start()
                    selectTab(currentTabPosition().roundToInt().coerceIn(0, 2))
                    view.parent.requestDisallowInterceptTouchEvent(false)
                    if (event.actionMasked == MotionEvent.ACTION_UP) view.performClick()
                }
            }
            true
        }
    }

    private fun currentTabPosition() =
        (selection.x - tabBackdrop.x - px(4)) / max(1, selection.width)

    private fun selectTab(index: Int) {
        selected = index
        for (i in 0..2) tabs.getChildAt(i).isSelected = i == index
        tabAnimation?.cancel()
        tabAnimation = ValueAnimator.ofFloat(currentTabPosition(), index.toFloat()).apply {
            duration = 420
            interpolator = OvershootInterpolator(.65f)
            addUpdateListener { setTabPosition(it.animatedValue as Float) }
            start()
        }
    }

    /** Used by the frame-stepped recording so emulator speed does not change the clip. */
    fun setTabPosition(position: Float) {
        selection.x = tabBackdrop.x + px(4) + position.coerceIn(0f, 2f) * selection.width
        selection.y = tabBackdrop.y + px(6)
        selection.invalidate()
    }

    override fun onDestroy() { tabAnimation?.cancel(); super.onDestroy() }

    private class TabIcon(context: android.content.Context, val index: Int, val label: String) : View(context) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val path = Path()
        val dp = resources.displayMetrics.density
        override fun onDraw(canvas: Canvas) {
            val save = canvas.save()
            canvas.translate(width / 2f, height * .36f)
            canvas.scale(dp, dp)
            paint.color = Color.WHITE
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.8f
            paint.strokeCap = Paint.Cap.ROUND; paint.strokeJoin = Paint.Join.ROUND
            when (index) {
                0 -> {
                    canvas.drawCircle(0f, 0f, 11f, paint)
                    path.reset(); path.moveTo(5f, -5f); path.lineTo(2f, 3f)
                    path.lineTo(-5f, 5f); path.lineTo(-2f, -3f); path.close()
                    canvas.drawPath(path, paint)
                }
                1 -> {
                    path.reset(); path.moveTo(-8f, -10f); path.lineTo(8f, -10f)
                    path.lineTo(8f, 11f); path.lineTo(0f, 6f); path.lineTo(-8f, 11f); path.close()
                    canvas.drawPath(path, paint)
                }
                else -> {
                    canvas.drawCircle(0f, -5f, 5f, paint)
                    canvas.drawArc(-10f, 3f, 10f, 19f, 180f, 180f, false, paint)
                }
            }
            canvas.restoreToCount(save)
            paint.style = Paint.Style.FILL; paint.textAlign = Paint.Align.CENTER
            paint.textSize = 11f * dp
            canvas.drawText(label, width / 2f, height * .80f, paint)
        }
    }
}
