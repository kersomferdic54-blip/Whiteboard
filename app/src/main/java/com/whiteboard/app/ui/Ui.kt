package com.whiteboard.app.ui

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import kotlin.math.roundToInt

class Palette(val bg: Int, val card: Int, val text: Int, val sub: Int, val accent: Int, val dark: Boolean)

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).roundToInt()
fun Context.dpf(v: Float): Float = v * resources.displayMetrics.density

fun Context.palette(): Palette {
    val dark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    return if (dark) Palette(0xFF121316.toInt(), 0xFF1E2025.toInt(), 0xFFF2F3F5.toInt(), 0xFF9AA0AA.toInt(), 0xFF4C8DFF.toInt(), true)
    else Palette(0xFFF4F5F7.toInt(), 0xFFFFFFFF.toInt(), 0xFF16181D.toInt(), 0xFF6B7280.toInt(), 0xFF2F6BFF.toInt(), false)
}

fun rounded(color: Int, radius: Float): GradientDrawable =
    GradientDrawable().apply { setColor(color); cornerRadius = radius }

fun ripple(base: Drawable): Drawable = RippleDrawable(ColorStateList.valueOf(0x33808080), base, null)

@Suppress("DEPRECATION")
fun Activity.applyWindow(p: Palette) {
    window.statusBarColor = p.bg
    window.navigationBarColor = p.bg
    if (!p.dark) window.decorView.systemUiVisibility =
        View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
}

fun Context.label(t: String, sp: Float, color: Int, bold: Boolean = false): TextView =
    TextView(this).apply {
        text = t; textSize = sp; setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

fun Context.bigButton(t: String, p: Palette, accent: Boolean = false, onClick: () -> Unit): TextView =
    TextView(this).apply {
        text = t; textSize = 18f; gravity = Gravity.CENTER; typeface = Typeface.DEFAULT_BOLD
        setTextColor(if (accent) Color.WHITE else p.text)
        background = ripple(rounded(if (accent) p.accent else p.card, dpf(20f)))
        elevation = dpf(3f)
        setOnClickListener { onClick() }
    }

fun Context.slider(
    title: String, min: Int, max: Int, value: Int, p: Palette,
    fmt: (Int) -> String, onChange: (Int) -> Unit
): LinearLayout {
    val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(6), 0, dp(6)) }
    val head = LinearLayout(this)
    val v = label(fmt(value), 14f, p.sub)
    head.addView(label(title, 14f, p.text), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
    head.addView(v)
    val sb = SeekBar(this)
    sb.max = max; sb.min = min; sb.progress = value
    sb.progressTintList = ColorStateList.valueOf(p.accent)
    sb.thumbTintList = ColorStateList.valueOf(p.accent)
    sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(s: SeekBar, pr: Int, user: Boolean) { v.text = fmt(pr); if (user) onChange(pr) }
        override fun onStartTrackingTouch(s: SeekBar) {}
        override fun onStopTrackingTouch(s: SeekBar) {}
    })
    box.addView(head); box.addView(sb)
    return box
}
