package com.whiteboard.app.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.os.Build
import android.os.Bundle
import android.view.*
import android.widget.*
import com.whiteboard.app.data.BoardRepository
import com.whiteboard.app.data.SettingsStore
import com.whiteboard.app.drawing.DrawingView
import com.whiteboard.app.model.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class BoardActivity : Activity() {
    companion object {
        private const val EXTRA_ID = "id"
        private const val EXTRA_TYPE = "type"
        fun openNew(c: Context, t: BoardType) =
            c.startActivity(Intent(c, BoardActivity::class.java).putExtra(EXTRA_TYPE, t.name))
        fun openSaved(c: Context, id: String) =
            c.startActivity(Intent(c, BoardActivity::class.java).putExtra(EXTRA_ID, id))
    }

    private lateinit var repo: BoardRepository
    private lateinit var settings: SettingsStore
    private lateinit var board: Board
    private lateinit var canvas: DrawingView
    private lateinit var root: FrameLayout
    private lateinit var fab: TextView
    private lateinit var barCard: FrameLayout
    private lateinit var propsBox: LinearLayout
    private lateinit var preview: PreviewView
    private lateinit var undoBtn: TextView
    private lateinit var redoBtn: TextView
    private val toolBtns = EnumMap<Tool, TextView>(Tool::class.java)
    private var tool = Tool.PEN
    private var barOpen = false
    private val tp = Palette(0xF2202228.toInt(), 0, Color.WHITE, 0xFFB4B9C4.toInt(), 0xFF6FA2FF.toInt(), true)

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        repo = BoardRepository(this); settings = SettingsStore(this)
        val id = intent.getStringExtra(EXTRA_ID)
        val loaded = id?.let { repo.load(it) }
        if (id != null && loaded == null) {
            Toast.makeText(this, "Could not open board", Toast.LENGTH_SHORT).show(); finish(); return
        }
        board = loaded ?: Board.create(BoardType.valueOf(intent.getStringExtra(EXTRA_TYPE) ?: "WHITE"))
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= 28) {
            val lp = window.attributes
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = lp
        }

        canvas = DrawingView(this)
        canvas.color = board.type.ink
        for (t in Tool.values()) canvas.styles.getValue(t).apply {
            size = settings.toolSize(t).toFloat()
            opacity = if (t == Tool.ERASER) 1f else settings.toolOpacity(t) / 100f
        }
        canvas.attach(board)
        canvas.onStrokeStart = { if (barOpen) closeBar() }
        canvas.onHistoryChanged = { updateUndoRedo() }

        root = FrameLayout(this).apply { setBackgroundColor(board.backgroundColor) }
        root.addView(canvas, FrameLayout.LayoutParams(-1, -1))
        buildBar(); buildFab()
        root.addView(barCard); root.addView(fab)
        barCard.visibility = View.GONE
        root.addOnLayoutChangeListener { _, l, t, r, bt, _, _, _, _ ->
            fab.x = clampf(fab.x, 0f, (r - l - fab.width).toFloat()); fab.y = clampf(fab.y, 0f, (bt - t - fab.height).toFloat())
            if (barOpen) positionBar()
        }
        setContentView(root)
        selectTool(Tool.PEN)
        updateUndoRedo()
    }

    private fun clampf(v: Float, lo: Float, hi: Float) = max(lo, min(v, hi))

    @Suppress("DEPRECATION")
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
    }

    // ---------- floating button ----------
    private fun buildFab() {
        val s = dp(settings.buttonSizeDp)
        fab = TextView(this).apply {
            textSize = settings.buttonSizeDp * 0.4f; gravity = Gravity.CENTER
            background = rounded(0xE6222428.toInt(), dpf(100f)); elevation = dpf(6f)
            alpha = settings.buttonOpacity / 100f
            layoutParams = FrameLayout.LayoutParams(s, s).apply { leftMargin = dp(16); topMargin = dp(24) }
        }
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        var dx = 0f; var dy = 0f; var sx = 0f; var sy = 0f; var moved = false
        fab.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { dx = e.rawX; dy = e.rawY; sx = v.x; sy = v.y; moved = false }
                MotionEvent.ACTION_MOVE -> {
                    if (!moved && hypot(e.rawX - dx, e.rawY - dy) > slop) moved = true
                    if (moved) {
                        v.x = clampf(sx + e.rawX - dx, 0f, (root.width - v.width).toFloat())
                        v.y = clampf(sy + e.rawY - dy, 0f, (root.height - v.height).toFloat())
                        if (barOpen) positionBar()
                    }
                }
                MotionEvent.ACTION_UP -> if (!moved) { v.performClick(); if (barOpen) closeBar() else openBar() }
            }
            true
        }
    }

    // ---------- toolbar ----------
    private fun cell(emoji: String, desc: String, onClick: () -> Unit) = TextView(this).apply {
        text = emoji; textSize = 22f; gravity = Gravity.CENTER; contentDescription = desc
        background = ripple(rounded(0, dpf(14f))); setOnClickListener { onClick() }
    }

    private fun buildBar() {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(10), dp(10), dp(10), dp(12)) }
        fun row(vararg v: View) = LinearLayout(this).also { r -> v.forEach { r.addView(it, LinearLayout.LayoutParams(0, dp(48), 1f)) } }
        toolBtns[Tool.PEN] = cell("✏️", "Pen") { selectTool(Tool.PEN) }
        toolBtns[Tool.BRUSH] = cell("🖌️", "Brush") { selectTool(Tool.BRUSH) }
        toolBtns[Tool.ERASER] = cell("🧽", "Eraser") { selectTool(Tool.ERASER) }
        undoBtn = cell("↩️", "Undo") { canvas.undo() }
        redoBtn = cell("↪️", "Redo") { canvas.redo() }
        col.addView(row(toolBtns[Tool.PEN]!!, toolBtns[Tool.BRUSH]!!, toolBtns[Tool.ERASER]!!))
        col.addView(row(cell("🎨", "Color") { pickColor() }, undoBtn, redoBtn))
        col.addView(row(cell("🗑️", "Clear") { confirmClear() }, cell("💾", "Save") { save() }, cell("⬅️", "Exit") { exit() }))
        preview = PreviewView(this)
        col.addView(preview, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(10) })
        propsBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(propsBox, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
        barCard = FrameLayout(this).apply {
            background = rounded(tp.bg, dpf(24f)); elevation = dpf(12f)
            addView(ScrollView(context).apply { addView(col) })
            layoutParams = FrameLayout.LayoutParams(dp(264), -2)
        }
    }

    private fun selectTool(t: Tool) {
        tool = t; canvas.tool = t
        for ((k, v) in toolBtns) v.background = ripple(rounded(if (k == t) 0x556FA2FF else 0, dpf(14f)))
        fab.text = when (t) { Tool.PEN -> "✏️"; Tool.BRUSH -> "🖌️"; Tool.ERASER -> "🧽" }
        refreshProps()
    }

    private fun refreshProps() {
        propsBox.removeAllViews()
        val t = tool
        val st = canvas.styles.getValue(t)
        val (mn, mx) = when (t) { Tool.PEN -> 1 to 30; Tool.BRUSH -> 6 to 120; Tool.ERASER -> 8 to 160 }
        propsBox.addView(slider(if (t == Tool.ERASER) "Eraser size" else "Thickness", mn, mx, st.size.roundToInt().coerceIn(mn, mx), tp, { "$it px" }) {
            st.size = it.toFloat(); settings.setToolSize(t, it); preview.invalidate()
        })
        if (t != Tool.ERASER) propsBox.addView(slider("Opacity", 5, 100, (st.opacity * 100).roundToInt(), tp, { "$it%" }) {
            st.opacity = it / 100f; settings.setToolOpacity(t, it); preview.invalidate()
        })
        preview.invalidate()
        if (barOpen) positionBar()
    }

    private fun openBar() {
        barOpen = true
        barCard.animate().cancel()
        positionBar()
        val target = settings.toolbarOpacity / 100f
        barCard.alpha = 0f; barCard.scaleX = 0.92f; barCard.scaleY = 0.92f; barCard.visibility = View.VISIBLE
        barCard.animate().alpha(target).scaleX(1f).scaleY(1f).setDuration(160).start()
    }

    private fun closeBar() {
        barOpen = false
        barCard.animate().cancel()
        barCard.animate().alpha(0f).setDuration(120).withEndAction { if (!barOpen) barCard.visibility = View.GONE }.start()
    }

    private fun positionBar() {
        val w = dp(264)
        val col = (barCard.getChildAt(0) as ScrollView).getChildAt(0)
        col.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
        val h = min(col.measuredHeight, root.height - dp(16))
        val lp = barCard.layoutParams
        if (lp.height != h || lp.width != w) { lp.width = w; lp.height = h; barCard.layoutParams = lp }
        val m = dp(8).toFloat()
        barCard.x = clampf(fab.x + fab.width / 2f - w / 2f, m, root.width - w - m)
        var y = fab.y + fab.height + m
        if (y + h > root.height - m) y = fab.y - h - m
        barCard.y = clampf(y, m, root.height - h - m)
    }

    private fun updateUndoRedo() {
        undoBtn.alpha = if (canvas.canUndo) 1f else 0.35f
        redoBtn.alpha = if (canvas.canRedo) 1f else 0.35f
    }

    // ---------- actions ----------
    private fun pickColor() {
        ColorPickerDialog(this, canvas.color, settings.recentColors()) { c ->
            canvas.color = c; settings.addRecent(c)
            if (tool == Tool.ERASER) selectTool(Tool.PEN) else preview.invalidate()
        }.show()
    }

    private fun confirmClear() {
        AlertDialog.Builder(this).setTitle("Clear board?")
            .setMessage("All drawings will be removed. You can undo this.")
            .setPositiveButton("Clear") { _, _ -> canvas.clearAll() }
            .setNegativeButton("Cancel", null).show()
    }

    private fun save(after: (() -> Unit)? = null) {
        if (board.saved) { persist(after); return }
        val et = EditText(this).apply {
            setText("Board " + SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date()))
            setSelectAllOnFocus(true); setSingleLine()
        }
        val frame = FrameLayout(this).apply { setPadding(dp(20), dp(8), dp(20), 0); addView(et) }
        AlertDialog.Builder(this).setTitle("Save board").setView(frame)
            .setPositiveButton("Save") { _, _ ->
                board.name = et.text.toString().trim().ifEmpty { "Untitled" }
                persist(after)
            }.setNegativeButton("Cancel", null).show()
    }

    private fun persist(after: (() -> Unit)?) {
        board.modifiedAt = System.currentTimeMillis()
        val snap = board.copyForSave()
        val thumb = canvas.snapshot(480)
        Thread {
            val ok = try { repo.save(snap, thumb); true } catch (e: Exception) { false }
            runOnUiThread {
                if (ok) {
                    board.saved = true; canvas.markSaved()
                    Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show(); after?.invoke()
                } else Toast.makeText(this, "Save failed", Toast.LENGTH_LONG).show()
            }
        }.start()
    }

    private fun exit() {
        if (!canvas.dirty || (!board.saved && !canvas.hasStrokes)) { finish(); return }
        AlertDialog.Builder(this).setTitle("Save changes?")
            .setMessage("You have unsaved changes on this board.")
            .setPositiveButton("Save") { _, _ -> save { finish() } }
            .setNegativeButton("Discard") { _, _ -> finish() }
            .setNeutralButton("Cancel", null).show()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { if (barOpen) closeBar() else exit() }

    private inner class PreviewView(c: Context) : View(c) {
        private val pa = Paint(Paint.ANTI_ALIAS_FLAG)
        private val path = Path()
        override fun onDraw(cv: Canvas) {
            val w = width.toFloat(); val h = height.toFloat(); val r = dpf(12f)
            pa.style = Paint.Style.FILL; pa.color = board.backgroundColor
            cv.drawRoundRect(0f, 0f, w, h, r, r, pa)
            val st = canvas.styles.getValue(tool)
            val sz = min(st.size, h * 0.85f)
            pa.style = Paint.Style.STROKE; pa.strokeCap = Paint.Cap.ROUND
            if (tool == Tool.ERASER) {
                pa.strokeWidth = dpf(2f); pa.color = if (board.type == BoardType.WHITE) 0xFF666666.toInt() else 0xFFCCCCCC.toInt()
                cv.drawCircle(w / 2, h / 2, sz / 2f, pa)
            } else {
                pa.strokeWidth = sz; pa.color = canvas.color; pa.alpha = (st.opacity * 255).roundToInt()
                path.reset(); path.moveTo(w * 0.1f, h * 0.6f); path.cubicTo(w * 0.3f, h * 0.0f, w * 0.6f, h * 1.0f, w * 0.9f, h * 0.4f)
                cv.drawPath(path, pa)
            }
        }
    }
}
