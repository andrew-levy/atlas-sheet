package dev.better.sheet.sample

import android.animation.ValueAnimator
import android.app.Activity
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.*
import android.view.animation.OvershootInterpolator
import android.widget.*
import kotlin.math.*

/**
 * An interactive material gallery rather than a scripted recording screen:
 * 1) an optical lens over high-contrast artwork,
 * 2) an icon-refracting liquid dock,
 * 3) a trigger that joins another lobe and morphs into a real action menu.
 *
 * The glass renderer does not know about menus, tabs or this activity.
 */
class GlassActivity : Activity() {
    private val dp get() = resources.displayMetrics.density
    private fun px(n: Float) = n * dp
    private fun px(n: Int) = (n * dp).roundToInt()

    private lateinit var root: FrameLayout
    lateinit var scene: ScrollView
        private set
    lateinit var glass: GlassView
        private set
    lateinit var tabBackdrop: FrameLayout
        private set
    lateinit var selection: GlassView
        private set
    lateinit var morphGlass: GlassView
        private set

    private lateinit var headline: TextView
    private lateinit var description: TextView
    private lateinit var hint: TextView
    private lateinit var demoControls: LinearLayout
    private lateinit var morphTrigger: TextView
    private lateinit var morphMenu: LinearLayout
    private lateinit var morphMergeButton: TextView
    private lateinit var tabs: LinearLayout
    private val choices = ArrayList<TextView>()

    private var tabAnimation: ValueAnimator? = null
    private var selected = 0
    private var lastTabWidth = 0
    private var currentMode = 0
    private var morphStage = 0
    private var grabX = 0f
    private var grabY = 0f
    private var downX = 0f
    private var downY = 0f
    private var tabDown = 0f
    private var tabStart = 0f
    private var pendingMorph: Runnable? = null

    private val white = Color.WHITE
    private val muted = 0xFFDCE7FF.toInt()
    private val ink = 0xFF101D3B.toInt()

    private fun rounded(fill: Int, radius: Float, line: Int = 0, lineWidth: Float = 1f): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = px(radius)
            setColor(fill)
            if (line != 0) setStroke(px(lineWidth).roundToInt().coerceAtLeast(1), line)
        }

    private fun label(
        text: String, sp: Float, color: Int = white,
        bold: Boolean = false, tracking: Float = 0f
    ) = TextView(this).apply {
        this.text = text
        textSize = sp
        setTextColor(color)
        letterSpacing = tracking
        typeface = Typeface.create(if (bold) "sans-serif-medium" else "sans-serif", Typeface.NORMAL)
        includeFontPadding = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        window.statusBarColor = 0xFF091129.toInt()
        @Suppress("DEPRECATION")
        window.navigationBarColor = 0xFF091129.toInt()
        root = FrameLayout(this).apply {
            setBackgroundColor(0xFF091129.toInt())
            fitsSystemWindows = true
            clipChildren = false
            clipToPadding = false
        }
        setContentView(root)
        makeBackground()
        makeLens()
        makeDock()
        makeMorph()
        makeHeader()

        root.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            val w = tabBackdrop.width
            if (w > 0 && w != lastTabWidth) {
                lastTabWidth = w
                val slot = ((w - px(8)) / 3f).roundToInt()
                selection.layoutParams = selection.layoutParams.apply { width = slot }
                setTabPosition(selected.toFloat())
            }
            placeMorph()
        }
        root.post { showMode(0) }
    }

    private fun makeBackground() {
        scene = ScrollView(this).apply {
            id = R.id.backdrop
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            fillViewport = true
        }
        val canvasHeight = (resources.displayMetrics.heightPixels * 1.55f).toInt()
        val field = FrameLayout(this).apply {
            setBackgroundColor(0xFF06142E.toInt())
        }
        field.addView(ImageView(this).apply {
            setImageResource(R.drawable.glass_night_sky)
            scaleType = ImageView.ScaleType.CENTER_CROP
            contentDescription = "Deep violet, blue and pink galactic clouds"
        }, FrameLayout.LayoutParams(-1, -1))
        // Crisp shapes make refraction immediately visible, even on smooth stars.
        field.addView(OrbitalArtwork(this), FrameLayout.LayoutParams(-1, -1))
        field.addView(label("REFRACTION", 41f, 0xB8FFFFFF.toInt(), true, -.025f).apply {
            gravity = Gravity.CENTER
        }, FrameLayout.LayoutParams(-1, px(84), Gravity.TOP).apply {
            topMargin = (resources.displayMetrics.heightPixels * .39f).roundToInt()
        })
        field.addView(label("move through light  /  watch the edges", 12f,
            0xD3E8E7FF.toInt(), false, .1f).apply {
            gravity = Gravity.CENTER
        }, FrameLayout.LayoutParams(-1, px(38), Gravity.TOP).apply {
            topMargin = (resources.displayMetrics.heightPixels * .52f).roundToInt()
        })
        scene.addView(field, FrameLayout.LayoutParams(-1, canvasHeight))
        root.addView(scene, FrameLayout.LayoutParams(-1, -1))
        // Darken the top and bottom slightly for legible control labels.
        root.addView(View(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0xD9000717.toInt(), 0x42000921, 0x00000000)
            )
            isClickable = false
        }, FrameLayout.LayoutParams(-1, px(252), Gravity.TOP))
    }

    private fun makeLens() {
        glass = GlassView(this).apply {
            id = R.id.glass
            backdrop = scene
            live = false
            jellyEnabled = true
            isClickable = true
            contentDescription = "Drag this lens to stretch and bend the sky"
            cornerRadius = px(50).toFloat()
            refractionHeight = px(35f)
            refractionAmount = px(52f)
            blurRadius = px(1.1f)
        }
        root.addView(glass, FrameLayout.LayoutParams(px(238), px(158), Gravity.CENTER))
        glass.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    view.animate().cancel()
                    grabX = event.rawX - view.x
                    grabY = event.rawY - view.y
                    downX = event.rawX
                    downY = event.rawY
                    glass.pressAt(event.x, event.y)
                    view.parent.requestDisallowInterceptTouchEvent(true)
                }
                MotionEvent.ACTION_MOVE -> {
                    view.x = (event.rawX - grabX).coerceIn(0f,
                        max(0f, root.width - view.width.toFloat()))
                    view.y = (event.rawY - grabY).coerceIn(0f,
                        max(0f, root.height - view.height.toFloat()))
                    glass.dragTo(event.x + (event.rawX - downX) * .45f,
                        event.y + (event.rawY - downY) * .45f)
                    view.invalidate()
                }
                MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> {
                    glass.releaseTouch()
                    view.parent.requestDisallowInterceptTouchEvent(false)
                    if (event.actionMasked == MotionEvent.ACTION_UP) view.performClick()
                }
            }
            true
        }
    }

    private fun makeDock() {
        tabBackdrop = FrameLayout(this).apply {
            clipChildren = false
            clipToPadding = false
        }
        root.addView(tabBackdrop, FrameLayout.LayoutParams(-1, px(82), Gravity.BOTTOM).apply {
            leftMargin = px(22); rightMargin = px(22); bottomMargin = px(54)
        })
        tabBackdrop.addView(GlassView(this).apply {
            backdrop = scene
            live = false
            cornerRadius = px(41).toFloat()
            blurRadius = px(8f)
            refractionHeight = px(24f)
            refractionAmount = px(29f)
        }, FrameLayout.LayoutParams(-1, -1))
        tabs = LinearLayout(this).apply {
            setPadding(px(4), 0, px(4), 0)
            gravity = Gravity.CENTER
        }
        tabBackdrop.addView(tabs, FrameLayout.LayoutParams(-1, -1))
        listOf("Explore", "Saved", "Profile").forEachIndexed { index, title ->
            tabs.addView(TabIcon(this, index, title).apply {
                contentDescription = title
                isClickable = true
                isFocusable = true
                setOnClickListener { selectTab(index) }
            }, LinearLayout.LayoutParams(0, -1, 1f))
        }

        selection = GlassView(this).apply {
            backdrop = tabBackdrop
            live = false
            jellyEnabled = true
            isClickable = true
            cornerRadius = px(34).toFloat()
            blurRadius = 0f
            refractionHeight = px(20f)
            refractionAmount = px(36f)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        root.addView(selection, FrameLayout.LayoutParams(1, px(70)))
        selection.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    tabAnimation?.cancel()
                    tabDown = event.rawX
                    tabStart = currentTabPosition()
                    selection.pressAt(event.x, event.y)
                    view.parent.requestDisallowInterceptTouchEvent(true)
                }
                MotionEvent.ACTION_MOVE -> {
                    setTabPosition(tabStart +
                        (event.rawX - tabDown) / selection.width.coerceAtLeast(1))
                    selection.dragTo(event.x + (event.rawX - tabDown) * .5f, event.y)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    selection.releaseTouch()
                    selectTab(currentTabPosition().roundToInt().coerceIn(0, 2))
                    view.parent.requestDisallowInterceptTouchEvent(false)
                    if (event.actionMasked == MotionEvent.ACTION_UP) view.performClick()
                }
            }
            true
        }
    }

    private fun makeMorph() {
        morphGlass = GlassView(this).apply {
            backdrop = scene
            live = false
            jellyEnabled = false
            cornerRadius = px(31).toFloat()
            blurRadius = px(2.4f)
            refractionHeight = px(29f)
            refractionAmount = px(38f)
            contentDescription = "Liquid trigger morphs and merges into an action menu"
        }
        // Full-window glass only draws its own small shape; outside remains clear.
        root.addView(morphGlass, FrameLayout.LayoutParams(-1, -1))

        morphTrigger = label("OPEN MENU   ↗", 15f, white, true, .035f).apply {
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            background = rounded(0x1FFFFFFF, 31f, 0x60FFFFFF)
            setOnClickListener { if (morphStage == 2) collapseMorph() else openMorph() }
            contentDescription = "Open morphing glass action menu"
        }
        root.addView(morphTrigger, FrameLayout.LayoutParams(px(168), px(60)))

        morphMenu = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            alpha = 0f
            setPadding(px(18), px(15), px(18), px(15))
            background = rounded(0x10000000, 34f, 0x30FFFFFF)
        }
        val section = label("QUICK ACTIONS", 11f, muted, true, .17f)
        morphMenu.addView(section, LinearLayout.LayoutParams(-1, px(32)))
        val options = listOf(
            Triple("↗", "Share", "Send to someone"),
            Triple("⎘", "Copy link", "Save to clipboard"),
            Triple("♡", "Save", "Keep for later"),
            Triple("⋯", "More", "Other actions")
        )
        options.forEachIndexed { index, (icon, title, subtitle) ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                isClickable = true
                isFocusable = true
                background = rounded(0x13FFFFFF, 15f)
                setOnClickListener { collapseMorph() }
            }
            row.addView(label(icon, 23f, white).apply { gravity = Gravity.CENTER },
                LinearLayout.LayoutParams(px(45), -1))
            row.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                addView(label(title, 15f, white, true))
                addView(label(subtitle, 11f, muted))
            }, LinearLayout.LayoutParams(0, -1, 1f))
            morphMenu.addView(row, LinearLayout.LayoutParams(-1, px(56)).apply {
                bottomMargin = if (index < 3) px(6) else 0
            })
        }
        root.addView(morphMenu, FrameLayout.LayoutParams(px(288), px(330)))

        morphMergeButton = label("MERGE DROPLETS", 13f, white, true, .09f).apply {
            gravity = Gravity.CENTER
            background = rounded(0x53303F7C, 24f, 0x88DDE6FF.toInt())
            isClickable = true; isFocusable = true
            setOnClickListener {
                if (morphStage == 2) collapseMorph()
                else if (morphStage == 1) resetMorph()
                else mergeMorph()
            }
        }
        root.addView(morphMergeButton, FrameLayout.LayoutParams(px(188), px(48), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            bottomMargin = px(74)
        })
        morphGlass.visibility = View.GONE
        morphTrigger.visibility = View.GONE
        morphMenu.visibility = View.GONE
        morphMergeButton.visibility = View.GONE
    }

    private fun triggerBounds(): RectF {
        val w = px(168).toFloat()
        val h = px(60).toFloat()
        val left = (root.width - w) / 2f
        val top = max(px(245f), root.height * .39f)
        return RectF(left, top, left + w, top + h)
    }

    private fun menuBounds(): RectF {
        val w = px(288).toFloat().coerceAtMost(root.width - px(42f))
        val h = px(330).toFloat().coerceAtMost(root.height * .52f)
        val left = (root.width - w) / 2f
        val top = max(px(205f), root.height * .31f)
        return RectF(left, top, left + w, top + h)
    }

    private fun placeMorph() {
        if (!::morphTrigger.isInitialized || root.width == 0) return
        val small = triggerBounds()
        morphTrigger.x = small.left
        morphTrigger.y = small.top
        val large = menuBounds()
        morphMenu.x = large.left
        morphMenu.y = large.top
    }

    private fun secondDrop() = triggerBounds().let { first ->
        val w = px(95f)
        val h = px(57f)
        RectF(first.right - px(12f), first.top + px(11f),
            first.right - px(12f) + w, first.top + px(11f) + h)
    }

    private fun mergeMorph() {
        if (currentMode != 2 || morphStage == 2) return
        morphGlass.mergeWith(secondDrop(), px(28f), px(46f))
        morphStage = 1
        morphMergeButton.text = "SEPARATE"
        hint.text = "Two glass lobes form a single fluid surface. Tap Open Menu to morph."
    }

    private fun openMorph() {
        if (currentMode != 2 || morphStage == 2) return
        pendingMorph?.let { root.removeCallbacks(it) }
        // Briefly form a neck, then expand the same liquid surface into menu size.
        if (morphStage == 0) morphGlass.mergeWith(secondDrop(), px(28f), px(48f))
        morphStage = 2
        morphTrigger.animate().alpha(0f).setDuration(110).start()
        val action = Runnable {
            morphGlass.morphTo(menuBounds(), px(34f))
            // Reduce the second lobe as the larger material takes over.
            morphGlass.separate()
            morphMenu.visibility = View.VISIBLE
            morphMenu.scaleX = .88f
            morphMenu.scaleY = .88f
            morphMenu.alpha = 0f
            morphMenu.animate().alpha(1f).scaleX(1f).scaleY(1f)
                .setStartDelay(85L).setDuration(290L).start()
        }
        pendingMorph = action
        root.postDelayed(action, 145L)
        morphMergeButton.text = "CLOSE MENU"
        hint.text = "The trigger expands into a real action menu. Tap any row to close."
    }

    private fun collapseMorph() {
        if (currentMode != 2) return
        pendingMorph?.let { root.removeCallbacks(it) }
        pendingMorph = null
        morphMenu.animate().alpha(0f).scaleX(.90f).scaleY(.90f)
            .setDuration(150L).withEndAction { morphMenu.visibility = View.GONE }.start()
        morphGlass.separate()
        morphGlass.morphTo(triggerBounds(), px(31f))
        morphTrigger.animate().alpha(1f).setStartDelay(110L).setDuration(180L).start()
        morphStage = 0
        morphMergeButton.text = "MERGE DROPLETS"
        hint.text = "Tap Open Menu to morph the glass, or merge two droplets first."
    }

    private fun resetMorph() {
        morphGlass.separate()
        morphGlass.morphTo(triggerBounds(), px(31f))
        morphStage = 0
        morphMergeButton.text = "MERGE DROPLETS"
        hint.text = "Tap Open Menu to morph the glass, or merge two droplets first."
    }

    private fun makeHeader() {
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(24), 0, px(24), 0)
        }
        header.addView(label("BETTER SHEET     /     GLASS LAB", 11f, 0xFFB8DBFF.toInt(), true, .14f))
        headline = label("Liquid, not plastic.", 33f, white, true, -.035f)
        header.addView(headline, LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(10) })
        description = label("A responsive, refracting surface.", 14f, muted)
        header.addView(description, LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(7) })
        root.addView(header, FrameLayout.LayoutParams(-1, -2, Gravity.TOP).apply {
            topMargin = px(25)
        })

        demoControls = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            setPadding(px(4), px(4), px(4), px(4))
            background = rounded(0x50242A4E, 27f, 0x60FFFFFF)
        }
        listOf("01  LENS", "02  DOCK", "03  MORPH").forEachIndexed { idx, title ->
            val button = label(title, 12f, white, true, .06f).apply {
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = true
                setOnClickListener { showMode(idx) }
            }
            choices.add(button)
            demoControls.addView(button, LinearLayout.LayoutParams(0, px(42), 1f))
        }
        root.addView(demoControls, FrameLayout.LayoutParams(-1, px(50), Gravity.TOP).apply {
            leftMargin = px(22); rightMargin = px(22); topMargin = px(147)
        })

        hint = label("", 13f, white).apply {
            gravity = Gravity.CENTER
            setPadding(px(24), px(6), px(24), px(6))
            background = rounded(0x5E121A39, 20f)
        }
        root.addView(hint, FrameLayout.LayoutParams(-1, px(58), Gravity.BOTTOM).apply {
            leftMargin = px(30); rightMargin = px(30); bottomMargin = px(185)
        })
    }

    /** Selecting a demo only changes presentation, not the underlying renderer. */
    fun showMode(index: Int) {
        if (index !in 0..2) return
        currentMode = index
        if (index != 2) {
            pendingMorph?.let { root.removeCallbacks(it) }
            pendingMorph = null
            if (::morphMenu.isInitialized) morphMenu.animate().cancel()
            if (::morphTrigger.isInitialized) morphTrigger.animate().cancel()
        }
        choices.forEachIndexed { i, view ->
            view.background = if (i == index) rounded(0xF3E8F0FF.toInt(), 22f)
                else rounded(Color.TRANSPARENT, 22f)
            view.setTextColor(if (i == index) ink else white)
            view.isSelected = i == index
        }
        glass.visibility = if (index == 0) View.VISIBLE else View.GONE
        tabBackdrop.visibility = if (index == 1) View.VISIBLE else View.GONE
        selection.visibility = if (index == 1) View.VISIBLE else View.GONE
        morphGlass.visibility = if (index == 2) View.VISIBLE else View.GONE
        morphTrigger.visibility = if (index == 2) View.VISIBLE else View.GONE
        morphMergeButton.visibility = if (index == 2) View.VISIBLE else View.GONE
        if (index != 2) morphMenu.visibility = View.GONE
        when (index) {
            0 -> {
                headline.text = "Bend the light."
                description.text = "One lens, real pixels, responsive edges."
                hint.text = "Drag the lens through the stars and lettering. Watch the edges wobble."
            }
            1 -> {
                headline.text = "Liquid navigation."
                description.text = "Glass slides over real icons and text."
                hint.text = "Drag the glass selector or tap a destination in the dock."
                setTabPosition(selected.toFloat())
            }
            else -> {
                headline.text = "From drop to menu."
                description.text = "A material that merges, stretches and transforms."
                hint.text = "Tap Open Menu to morph, or merge two glass droplets first."
                morphTrigger.alpha = 1f
                morphMenu.visibility = View.GONE
                morphGlass.setShape(triggerBounds(), px(31f))
                morphStage = 0
                morphMergeButton.text = "MERGE DROPLETS"
                placeMorph()
            }
        }
    }

    private fun currentTabPosition(): Float =
        (selection.x - tabBackdrop.x - px(4)) / max(1f, selection.width.toFloat())

    private fun selectTab(index: Int) {
        selected = index
        for (i in 0..2) tabs.getChildAt(i).isSelected = i == index
        tabAnimation?.cancel()
        tabAnimation = ValueAnimator.ofFloat(currentTabPosition(), index.toFloat()).apply {
            duration = 470
            interpolator = OvershootInterpolator(.44f)
            addUpdateListener { setTabPosition(it.animatedValue as Float) }
            start()
        }
    }

    fun setTabPosition(position: Float) {
        selection.x = tabBackdrop.x + px(4) +
            position.coerceIn(0f, 2f) * selection.width.coerceAtLeast(1)
        selection.y = tabBackdrop.y + px(6)
        selection.invalidate()
    }

    override fun onDestroy() {
        tabAnimation?.cancel()
        pendingMorph?.let { root.removeCallbacks(it) }
        super.onDestroy()
    }

    /** High-contrast strokes make actual edge distortion obvious, not just blur. */
    private class OrbitalArtwork(context: android.content.Context) : View(context) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private val d = resources.displayMetrics.density
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width * .53f
            val cy = height * .37f
            val maxR = min(width.toFloat() * .6f, 260f * d)
            p.style = Paint.Style.STROKE
            for (i in 0..5) {
                val r = maxR * (.43f + i * .13f)
                p.strokeWidth = if (i % 2 == 0) 1.1f * d else .6f * d
                p.color = if (i % 2 == 0) 0x7CE3F6FF else 0x45FFFFFF
                canvas.drawCircle(cx, cy, r, p)
            }
            p.strokeWidth = 1.3f * d
            p.color = 0x86E3F6FF.toInt()
            canvas.drawLine(0f, cy, width.toFloat(), cy, p)
            canvas.drawLine(cx, cy - maxR, cx, cy + maxR, p)
            for (i in 0..16) {
                val x = (i + 1) * width / 18f
                p.color = if (i % 2 == 0) 0x60FFFFFF else 0x30FFFFFF
                canvas.drawLine(x, cy - 48f * d, x, cy + 48f * d, p)
            }
            p.style = Paint.Style.FILL
        }
    }

    private class TabIcon(
        context: android.content.Context, private val index: Int, private val caption: String
    ) : View(context) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private val path = Path()
        private val d = resources.displayMetrics.density
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val save = canvas.save()
            canvas.translate(width / 2f, height * .37f)
            canvas.scale(d, d)
            p.color = Color.WHITE
            p.style = Paint.Style.STROKE
            p.strokeWidth = 1.9f
            p.strokeCap = Paint.Cap.ROUND
            p.strokeJoin = Paint.Join.ROUND
            when (index) {
                0 -> {
                    canvas.drawCircle(0f, 0f, 11f, p)
                    path.reset(); path.moveTo(5f, -5f); path.lineTo(2f, 4f)
                    path.lineTo(-5f, 5f); path.lineTo(-2f, -3f); path.close()
                    canvas.drawPath(path, p)
                }
                1 -> {
                    path.reset(); path.moveTo(-9f, -11f); path.lineTo(9f, -11f)
                    path.lineTo(9f, 11f); path.lineTo(0f, 6f)
                    path.lineTo(-9f, 11f); path.close()
                    canvas.drawPath(path, p)
                }
                else -> {
                    canvas.drawCircle(0f, -5f, 5f, p)
                    canvas.drawArc(-11f, 3f, 11f, 20f, 180f, 180f, false, p)
                }
            }
            canvas.restoreToCount(save)
            p.style = Paint.Style.FILL
            p.textAlign = Paint.Align.CENTER
            p.textSize = 12f * d
            p.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            p.color = if (isSelected) Color.WHITE else 0xD5E7EDFF.toInt()
            canvas.drawText(caption, width / 2f, height * .82f, p)
        }
    }
}
