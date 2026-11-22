package com.friday.ai

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(5, 10, 22)
        window.navigationBarColor = Color.rgb(5, 10, 22)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(28, 32, 28, 32)
            setBackgroundColor(Color.rgb(5, 10, 22))
        }
        val title = TextView(this).apply {
            text = "FRIDAY"
            textSize = 32f
            setTextColor(Color.rgb(90, 190, 255))
            gravity = Gravity.CENTER
        }
        val orb = TextView(this).apply {
            text = "◉"
            textSize = 110f
            setTextColor(Color.rgb(45, 155, 255))
            gravity = Gravity.CENTER
        }
        val subtitle = TextView(this).apply {
            text = "Personal AI Assistant\n\nNamaste! Main FRIDAY hoon.\nAbhi mera starter interface tayyar hai."
            textSize = 18f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        root.addView(title)
        root.addView(orb)
        root.addView(subtitle)
        setContentView(root)
    }
}
