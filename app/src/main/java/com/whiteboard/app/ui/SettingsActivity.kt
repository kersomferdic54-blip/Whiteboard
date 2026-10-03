package com.whiteboard.app.ui

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import com.whiteboard.app.data.SettingsStore

class SettingsActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val p = palette(); applyWindow(p)
        val st = SettingsStore(this)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(p.bg) }
        val head = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(12), dp(16), dp(20), dp(8)) }
        head.addView(label("‹", 34f, p.text).apply { setPadding(dp(12), 0, dp(16), dp(4)); setOnClickListener { finish() } })
        head.addView(label("Settings", 24f, p.text, true))
        root.addView(head)

        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(8), dp(16), dp(24)) }
        root.addView(ScrollView(this).apply { addView(body) }, LinearLayout.LayoutParams(-1, 0, 1f))

        // ---- live preview of the floating UI ----
        val stage = FrameLayout(this).apply { background = rounded(0xFFFFFFFF.toInt(), dpf(18f)) }
        val fab = TextView(this).apply { text = "✏️"; textSize = 22f; gravity = Gravity.CENTER; background = rounded(0xE6222428.toInt(), dpf(100f)) }
        val bar = LinearLayout(this).apply {
            gravity = Gravity.CENTER; setPadding(dp(8), dp(8), dp(8), dp(8)); background = rounded(0xF2202228.toInt(), dpf(18f))
            for (e in listOf("✏️", "🖌️", "🧽", "🎨", "↩️")) addView(TextView(context).apply { text = e; textSize = 20f; setPadding(dp(8), 0, dp(8), 0) })
        }
        stage.addView(fab, FrameLayout.LayoutParams(dp(56), dp(56), Gravity.CENTER_VERTICAL or Gravity.START).apply { leftMargin = dp(24) })
        stage.addView(bar, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER_VERTICAL or Gravity.END).apply { rightMargin = dp(16) })
        fun refresh() {
            fab.alpha = st.buttonOpacity / 100f; bar.alpha = st.toolbarOpacity / 100f
            val lp = fab.layoutParams; lp.width = dp(st.buttonSizeDp); lp.height = dp(st.buttonSizeDp); fab.layoutParams = lp
        }
        refresh()

        body.addView(label("FLOATING CONTROLS", 12f, p.sub, true).apply { letterSpacing = 0.1f }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(8); bottomMargin = dp(8) })
        body.addView(stage, LinearLayout.LayoutParams(-1, dp(110)))
        val card = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(10), dp(16), dp(10)); background = rounded(p.card, dpf(18f)) }
        card.addView(slider("Floating button opacity", 10, 100, st.buttonOpacity, p, { "$it%" }) { st.buttonOpacity = it; refresh() })
        card.addView(slider("Floating toolbar opacity", 10, 100, st.toolbarOpacity, p, { "$it%" }) { st.toolbarOpacity = it; refresh() })
        card.addView(slider("Floating button size", 36, 96, st.buttonSizeDp, p, { "$it dp" }) { st.buttonSizeDp = it; refresh() })
        body.addView(card, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        body.addView(label("Opacity only affects the floating button and toolbar, never your drawing.", 12f, p.sub),
            LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(8) })

        // ---- account (placeholder) ----
        body.addView(label("ACCOUNT", 12f, p.sub, true).apply { letterSpacing = 0.1f }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(28); bottomMargin = dp(8) })
        val row = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL; setPadding(dp(16), dp(14), dp(16), dp(14))
            background = ripple(rounded(p.card, dpf(18f)))
            setOnClickListener { Toast.makeText(context, "Google Sign-In coming soon.", Toast.LENGTH_SHORT).show() }
        }
        val g = TextView(this).apply {
            text = "G"; textSize = 20f; gravity = Gravity.CENTER; setTextColor(0xFF4285F4.toInt()); typeface = android.graphics.Typeface.DEFAULT_BOLD
            background = rounded(Color.WHITE, dpf(40f)).apply { setStroke(dp(1), 0x33000000) }
        }
        row.addView(g, LinearLayout.LayoutParams(dp(44), dp(44)))
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(label("Sign in with Google", 16f, p.text, true))
        col.addView(label("Not connected • coming soon", 13f, p.sub))
        row.addView(col, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(14) })
        body.addView(row, LinearLayout.LayoutParams(-1, -2))
        setContentView(root)
    }
}
