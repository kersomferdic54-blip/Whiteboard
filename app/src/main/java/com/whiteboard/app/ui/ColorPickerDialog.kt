package com.whiteboard.app.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.*
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.*

/** Saturation/value square + hue bar. Covers the full RGB gamut. */
class ColorPickerView(ctx: Context) : View(ctx) {
    var onChange: ((Int) -> Unit)? = null
    private val hsv = floatArrayOf(0f, 1f, 1f)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sv = RectF()
    private val hue = RectF()
    private var drag = 0
    private val hueColors = IntArray(7) { Color.HSVToColor(floatArrayOf(it * 60f, 1f, 1f)) }

    val color: Int get() = Color.HSVToColor(hsv)
    fun setColor(c: Int) { Color.colorToHSV(c, hsv); invalidate() }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        val hh = context.dp(30).toFloat(); val gap = context.dp(14).toFloat()
        sv.set(0f, 0f, w.toFloat(), h - hh - gap)
        hue.set(0f, h - hh, w.toFloat(), h.toFloat())
    }

    override fun onDraw(c: Canvas) {
        val r = context.dpf(12f)
        val pure = Color.HSVToColor(floatArrayOf(hsv[0], 1f, 1f))
        paint.shader = LinearGradient(sv.left, 0f, sv.right, 0f, Color.WHITE, pure, Shader.TileMode.CLAMP)
        c.drawRoundRect(sv, r, r, paint)
        paint.shader = LinearGradient(0f, sv.top, 0f, sv.bottom, Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP)
        c.drawRoundRect(sv, r, r, paint)
        paint.shader = LinearGradient(hue.left, 0f, hue.right, 0f, hueColors, null, Shader.TileMode.CLAMP)
        c.drawRoundRect(hue, hue.height() / 2, hue.height() / 2, paint)
        paint.shader = null
        ring(c, sv.left + hsv[1] * sv.width(), sv.top + (1f - hsv[2]) * sv.height(), context.dpf(11f), color)
        ring(c, hue.left + hsv[0] / 360f * hue.width(), hue.centerY(), hue.height() / 2f, pure)
    }

    private fun ring(c: Canvas, x: Float, y: Float, r: Float, fill: Int) {
        paint.style = Paint.Style.FILL; paint.color = fill; c.drawCircle(x, y, r, paint)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = context.dpf(3f); paint.color = Color.WHITE; c.drawCircle(x, y, r, paint)
        paint.strokeWidth = context.dpf(1f); paint.color = 0x66000000; c.drawCircle(x, y, r + context.dpf(1.5f), paint)
        paint.style = Paint.Style.FILL
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_DOWN) {
            parent?.requestDisallowInterceptTouchEvent(true)
            drag = if (e.y <= sv.bottom + context.dp(7)) 1 else 2
        }
        if (e.actionMasked == MotionEvent.ACTION_UP || e.actionMasked == MotionEvent.ACTION_CANCEL) drag = 0
        if (drag == 1) {
            hsv[1] = ((e.x - sv.left) / sv.width()).coerceIn(0f, 1f)
            hsv[2] = 1f - ((e.y - sv.top) / sv.height()).coerceIn(0f, 1f)
        } else if (drag == 2) {
            hsv[0] = ((e.x - hue.left) / hue.width()).coerceIn(0f, 0.9999f) * 360f
        } else return true
        invalidate(); onChange?.invoke(color)
        return true
    }
}

class ColorPickerDialog(
    private val act: Activity, private val initial: Int,
    private val recents: List<Int>, private val onPick: (Int) -> Unit
) {
    fun show() {
        val p = act.palette()
        val box = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; setPadding(act.dp(20), act.dp(16), act.dp(20), act.dp(8)) }
        val picker = ColorPickerView(act)
        box.addView(picker, LinearLayout.LayoutParams(-1, act.dp(240)))

        val row = LinearLayout(act).apply { gravity = Gravity.CENTER_VERTICAL }
        val swatch = View(act)
        val hex = EditText(act).apply {
            filters = arrayOf(InputFilter.AllCaps(), InputFilter.LengthFilter(7))
            setSingleLine(); textSize = 16f; setTextColor(p.text)
        }
        val rgb = act.label("", 13f, p.sub)
        row.addView(swatch, LinearLayout.LayoutParams(act.dp(44), act.dp(44)))
        row.addView(hex, LinearLayout.LayoutParams(act.dp(110), -2).apply { leftMargin = act.dp(12) })
        row.addView(rgb, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = act.dp(8) })
        box.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = act.dp(14) })

        var chosen = initial
        var fromHex = false
        fun update(c: Int, setHex: Boolean) {
            chosen = c or 0xFF000000.toInt()
            swatch.background = rounded(chosen, act.dpf(22f))
            rgb.text = "RGB ${Color.red(c)}, ${Color.green(c)}, ${Color.blue(c)}"
            if (setHex) { fromHex = true; hex.setText(String.format("#%06X", c and 0xFFFFFF)); fromHex = false }
        }
        picker.onChange = { update(it, true) }
        picker.setColor(initial); update(initial, true)
        hex.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                if (fromHex) return
                val t = s.toString().removePrefix("#")
                if (t.length == 6) t.toIntOrNull(16)?.let { picker.setColor(it or 0xFF000000.toInt()); update(it, false) }
            }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })

        box.addView(act.label("Recent colors", 13f, p.sub), LinearLayout.LayoutParams(-2, -2).apply { topMargin = act.dp(16) })
        val rec = LinearLayout(act)
        for (c in recents) {
            val d = View(act)
            d.background = rounded(c, act.dpf(18f)).apply { setStroke(act.dp(1), 0x55808080) }
            d.setOnClickListener { picker.setColor(c); update(c, true) }
            rec.addView(d, LinearLayout.LayoutParams(act.dp(36), act.dp(36)).apply { rightMargin = act.dp(8) })
        }
        box.addView(HorizontalScrollView(act).apply { addView(rec); isHorizontalScrollBarEnabled = false },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = act.dp(8) })

        AlertDialog.Builder(act).setView(ScrollView(act).apply { addView(box) })
            .setPositiveButton("Select") { _, _ -> onPick(chosen) }
            .setNegativeButton("Cancel", null).show()
    }
}
