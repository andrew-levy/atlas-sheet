package dev.better.sheet.sample

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.widget.*

class GlassActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.glass_playground)
        val backdrop = findViewById<ScrollView>(R.id.backdrop)
        val glass = findViewById<GlassView>(R.id.glass)
        val places = findViewById<LinearLayout>(R.id.places)
        val density = resources.displayMetrics.density
        val colors = listOf("#234C50", "#456771", "#806566", "#475B68")
        listOf("Lake trail", "North overlook", "Cedar grove", "West ridge", "Summit pass", "South shore", "Meadow walk", "Lookout point").forEachIndexed { index, place ->
            places.addView(TextView(this).apply {
                text = place
                textSize = 29f
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor(colors[index % colors.size]))
            }, -1, (124 * density).toInt())
        }
        glass.backdrop = backdrop
        backdrop.setOnScrollChangeListener { _, _, _, _, _ -> glass.invalidate() }
    }
}
