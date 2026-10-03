package com.whiteboard.app.drawing

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import com.whiteboard.app.model.*
import java.util.EnumMap
import java.util.UUID
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

class ToolStyle(var size: Float, var opacity: Float)

/**
 * Low-latency drawing surface. Finished strokes live in an off-screen bitmap (board space);
 * only the stroke in progress is re-rendered per frame. Strokes stay as vector data in the Board.
 */
class DrawingView(context: Context) : View(context) {
    private sealed class Action {
        class Add(val stroke: Stroke) : Action()
        class Clear(val removed: List<Stroke>) : Action()
    }

    var board: Board? = null; private set
    var tool = Tool.PEN
    var color = Color.BLACK
    val styles = EnumMap<Tool, ToolStyle>(Tool::class.java).apply {
        put(Tool.PEN, ToolStyle(4f, 1f)); put(Tool.BRUSH, ToolStyle(28f, 0.7f)); put(Tool.ERASER, ToolStyle(40f, 1f))
    }
    var dirty = false
    var onStrokeStart: (() -> Unit)? = null
    var onHistoryChanged: (() -> Unit)? = null

    val canUndo get() = undoStack.isNotEmpty()
    val canRedo get() = redoStack.isNotEmpty()
    val hasStrokes get() = board?.strokes?.isNotEmpty() == true

    private var bmp: Bitmap? = null
    private var bmpCanvas: Canvas? = null
    private val toView = Matrix()
    private val toBoard = Matrix()
    private val undoStack = ArrayList<Action>()
    private val redoStack = ArrayList<Action>()
    private var current: Stroke? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val path = Path()
    private val clearMode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    private val pt = FloatArray(2)

    fun attach(b: Board) {
        board = b; bmp = null; undoStack.clear(); redoStack.clear(); dirty = false
        ensureReady(); invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) { ensureReady() }

    private fun ensureReady() {
        val b = board ?: return
        if (width == 0 || height == 0) return
        if (b.canvasWidth == 0) { b.canvasWidth = width; b.canvasHeight = height }
        if (bmp == null) {
            bmp = Bitmap.createBitmap(b.canvasWidth, b.canvasHeight, Bitmap.Config.ARGB_8888)
            bmpCanvas = Canvas(bmp!!)
            rebuild()
        }
        val s = min(width.toFloat() / b.canvasWidth, height.toFloat() / b.canvasHeight)
        toView.setScale(s, s)
        toView.postTranslate((width - b.canvasWidth * s) / 2f, (height - b.canvasHeight * s) / 2f)
        toView.invert(toBoard)
    }

    // ---------- drawing ----------
    override fun onDraw(canvas: Canvas) {
        val b = board ?: return
        canvas.drawColor(b.backgroundColor)
        val bm = bmp ?: return
        val layer = canvas.saveLayer(null, null)
        canvas.save()
        canvas.concat(toView)
        canvas.drawBitmap(bm, 0f, 0f, bmpPaint)
        current?.let { renderStroke(canvas, it) }
        canvas.restore()
        canvas.restoreToCount(layer)
    }

    private fun factor(s: Stroke, p: Float): Float = when {
        !s.pressureSensitive -> 1f
        s.tool == Tool.BRUSH -> 0.25f + 0.75f * p
        s.tool == Tool.PEN -> 0.45f + 0.55f * p
        else -> 1f
    }

    private fun renderStroke(c: Canvas, s: Stroke) {
        val pts = s.points
        val n = pts.size
        if (n == 0) return
        val p = paint
        p.reset()
        p.isAntiAlias = true
        p.strokeCap = Paint.Cap.ROUND
        p.strokeJoin = Paint.Join.ROUND
        if (s.tool == Tool.ERASER) {
            p.xfermode = clearMode; p.color = Color.BLACK
        } else {
            p.color = s.color; p.alpha = (s.opacity * 255f).roundToInt().coerceIn(0, 255)
        }
        if (n == 1) {
            p.style = Paint.Style.FILL
            c.drawCircle(pts[0].x, pts[0].y, s.size * factor(s, pts[0].pressure) / 2f, p)
            return
        }
        p.style = Paint.Style.STROKE
        val variable = s.pressureSensitive && s.tool != Tool.ERASER && s.opacity >= 0.999f
        if (!variable) {
            var avg = 0f
            if (s.pressureSensitive) { for (q in pts) avg += q.pressure; avg /= n }
            p.strokeWidth = s.size * factor(s, avg)
            path.reset()
            path.moveTo(pts[0].x, pts[0].y)
            for (i in 1 until n) {
                val a = pts[i - 1]; val b = pts[i]
                path.quadTo(a.x, a.y, (a.x + b.x) / 2f, (a.y + b.y) / 2f)
            }
            path.lineTo(pts[n - 1].x, pts[n - 1].y)
            c.drawPath(path, p)
        } else {
            var px = pts[0].x; var py = pts[0].y
            for (i in 1 until n) {
                val a = pts[i - 1]; val b = pts[i]
                val mx = (a.x + b.x) / 2f; val my = (a.y + b.y) / 2f
                path.reset(); path.moveTo(px, py); path.quadTo(a.x, a.y, mx, my)
                p.strokeWidth = s.size * factor(s, (a.pressure + b.pressure) / 2f)
                c.drawPath(path, p)
                px = mx; py = my
            }
            path.reset(); path.moveTo(px, py); path.lineTo(pts[n - 1].x, pts[n - 1].y)
            c.drawPath(path, p)
        }
    }

    private fun rebuild() {
        val bc = bmpCanvas ?: return
        bmp?.eraseColor(Color.TRANSPARENT)
        board?.strokes?.forEach { renderStroke(bc, it) }
    }

    // ---------- input ----------
    private fun addPoint(s: Stroke, x: Float, y: Float, pressure: Float) {
        pt[0] = x; pt[1] = y
        toBoard.mapPoints(pt)
        val last = s.points.lastOrNull()
        if (last != null && hypot(pt[0] - last.x, pt[1] - last.y) < 0.5f) return
        val pr = if (s.pressureSensitive) pressure.coerceIn(0.05f, 1f) else 1f
        s.points.add(StrokePoint(pt[0], pt[1], pr))
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (board == null || bmp == null) return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                requestUnbufferedDispatch(e)
                parent?.requestDisallowInterceptTouchEvent(true)
                val type = e.getToolType(0)
                val t = if (type == MotionEvent.TOOL_TYPE_ERASER) Tool.ERASER else tool
                val st = styles.getValue(t)
                val s = Stroke(UUID.randomUUID().toString(), t, color, if (t == Tool.ERASER) 1f else st.opacity, st.size, ArrayList())
                s.pressureSensitive = type == MotionEvent.TOOL_TYPE_STYLUS
                current = s
                onStrokeStart?.invoke()
                addPoint(s, e.x, e.y, e.pressure)
            }
            MotionEvent.ACTION_MOVE -> current?.let { s ->
                for (i in 0 until e.historySize) addPoint(s, e.getHistoricalX(i), e.getHistoricalY(i), e.getHistoricalPressure(i))
                addPoint(s, e.x, e.y, e.pressure)
            }
            MotionEvent.ACTION_UP -> current?.let { s ->
                addPoint(s, e.x, e.y, e.pressure)
                commit(s)
            }
            MotionEvent.ACTION_CANCEL -> current = null
            else -> return true
        }
        invalidate()
        return true
    }

    private fun commit(s: Stroke) {
        current = null
        if (s.points.isEmpty()) return
        board?.strokes?.add(s)
        bmpCanvas?.let { renderStroke(it, s) }
        undoStack.add(Action.Add(s)); redoStack.clear()
        changed()
    }

    private fun changed() { dirty = true; onHistoryChanged?.invoke(); invalidate() }

    // ---------- history ----------
    fun undo() {
        val b = board ?: return
        if (undoStack.isEmpty()) return
        val a = undoStack.removeAt(undoStack.size - 1)
        when (a) {
            is Action.Add -> { b.strokes.remove(a.stroke); rebuild() }
            is Action.Clear -> { b.strokes.addAll(a.removed); rebuild() }
        }
        redoStack.add(a)
        changed()
    }

    fun redo() {
        val b = board ?: return
        if (redoStack.isEmpty()) return
        val a = redoStack.removeAt(redoStack.size - 1)
        when (a) {
            is Action.Add -> { b.strokes.add(a.stroke); bmpCanvas?.let { renderStroke(it, a.stroke) } }
            is Action.Clear -> { b.strokes.clear(); rebuild() }
        }
        undoStack.add(a)
        changed()
    }

    fun clearAll() {
        val b = board ?: return
        if (b.strokes.isEmpty()) return
        undoStack.add(Action.Clear(ArrayList(b.strokes))); redoStack.clear()
        b.strokes.clear(); rebuild(); changed()
    }

    fun markSaved() { dirty = false }

    fun snapshot(maxW: Int = 480): Bitmap {
        val b = board!!
        val tw = min(maxW, b.canvasWidth).coerceAtLeast(1)
        val th = (tw * b.canvasHeight.toFloat() / b.canvasWidth).roundToInt().coerceAtLeast(1)
        val out = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(b.backgroundColor)
        bmp?.let { c.drawBitmap(it, null, Rect(0, 0, tw, th), bmpPaint) }
        return out
    }
}
