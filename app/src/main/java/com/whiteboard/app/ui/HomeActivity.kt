package com.whiteboard.app.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import com.whiteboard.app.model.BoardType
import kotlin.math.min

class HomeActivity : Activity() {
    private lateinit var home: View
    private lateinit var chooser: View

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val p = palette(); applyWindow(p)
        val root = FrameLayout(this).apply { setBackgroundColor(p.bg) }
        home = buildHome(p)
        chooser = buildChooser(p).also { it.visibility = View.GONE }
        root.addView(home); root.addView(chooser)
        setContentView(root)
    }

    private fun cw() = min(resources.displayMetrics.widthPixels - dp(48), dp(440))

    private fun centered(c: LinearLayout): ScrollView {
        c.gravity = Gravity.CENTER; c.orientation = LinearLayout.VERTICAL
        c.setPadding(dp(24), dp(32), dp(24), dp(32))
        return ScrollView(this).apply { isFillViewport = true; addView(c, ViewGroup.LayoutParams(-1, -2)) }
    }

    private fun lp(h: Int, top: Int = 14) = LinearLayout.LayoutParams(cw(), dp(h)).apply { topMargin = dp(top) }

    private fun buildHome(p: Palette): View {
        val c = LinearLayout(this)
        c.addView(label("WHITEBOARD", 32f, p.text, true).apply { letterSpacing = 0.15f; gravity = Gravity.CENTER })
        c.addView(label("Your Digital Canvas", 16f, p.sub).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(6); bottomMargin = dp(30) })
        c.addView(bigButton("+   New Board", p, true) { swap(chooser, home) }, lp(72, 0))
        c.addView(bigButton("📁   Saved Boards", p) { startActivity(Intent(this, SavedBoardsActivity::class.java)) }, lp(72))
        c.addView(bigButton("⚙   Settings", p) { startActivity(Intent(this, SettingsActivity::class.java)) }, lp(72))
        return centered(c)
    }

    private fun buildChooser(p: Palette): View {
        val c = LinearLayout(this)
        c.addView(label("NEW BOARD", 26f, p.text, true).apply { letterSpacing = 0.12f }, LinearLayout.LayoutParams(-2, -2).apply { bottomMargin = dp(20) })
        for (t in BoardType.values()) {
            val row = LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL; setPadding(dp(14), dp(14), dp(14), dp(14))
                background = ripple(rounded(p.card, dpf(20f))); elevation = dpf(3f)
                setOnClickListener { BoardActivity.openNew(this@HomeActivity, t) }
            }
            row.addView(BoardPreview(this, t), LinearLayout.LayoutParams(dp(110), dp(70)))
            row.addView(label(t.label, 18f, p.text, true), LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(16) })
            c.addView(row, LinearLayout.LayoutParams(cw(), -2).apply { topMargin = dp(14) })
        }
        c.addView(bigButton("‹  Back", p) { swap(home, chooser) }, lp(56, 24))
        return centered(c)
    }

    private fun swap(show: View, hide: View) {
        hide.visibility = View.GONE
        show.alpha = 0f; show.visibility = View.VISIBLE
        show.animate().alpha(1f).setDuration(180).start()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (chooser.visibility == View.VISIBLE) swap(home, chooser) else super.onBackPressed()
    }
}

class BoardPreview(ctx: Context, private val type: BoardType) : View(ctx) {
    private val pa = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    override fun onDraw(c: Canvas) {
        val r = context.dpf(12f)
        pa.style = Paint.Style.FILL; pa.color = type.bg
        c.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), r, r, pa)
        pa.style = Paint.Style.STROKE; pa.color = 0x33808080; pa.strokeWidth = context.dpf(1f)
        c.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), r, r, pa)
        pa.color = type.ink; pa.strokeWidth = context.dpf(3f); pa.strokeCap = Paint.Cap.ROUND
        val w = width.toFloat(); val h = height.toFloat()
        path.reset(); path.moveTo(w * 0.15f, h * 0.65f)
        path.cubicTo(w * 0.3f, h * 0.1f, w * 0.5f, h * 0.95f, w * 0.85f, h * 0.35f)
        c.drawPath(path, pa)
    }
}
