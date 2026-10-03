package com.whiteboard.app.model

import java.util.UUID

enum class BoardType(val label: String, val bg: Int, val ink: Int) {
    WHITE("White Board", 0xFFFFFFFF.toInt(), 0xFF1A1A1A.toInt()),
    BLACK("Black Board", 0xFF0A0A0A.toInt(), 0xFFF5F5F5.toInt()),
    GREEN("Green Board", 0xFF21573D.toInt(), 0xFFF5F5F5.toInt())
}

enum class Tool { PEN, BRUSH, ERASER }

class StrokePoint(val x: Float, val y: Float, val pressure: Float)

class Stroke(
    val id: String,
    val tool: Tool,
    val color: Int,
    val opacity: Float,
    val size: Float,
    val points: MutableList<StrokePoint>
) {
    /** True when the stroke came from a stylus, so pressure modulates width. */
    var pressureSensitive: Boolean = false
}

class Board(
    val id: String,
    var name: String,
    val type: BoardType,
    val backgroundColor: Int,
    var canvasWidth: Int,
    var canvasHeight: Int,
    val createdAt: Long,
    var modifiedAt: Long,
    val strokes: MutableList<Stroke>,
    var saved: Boolean
) {
    /** Shallow snapshot that is safe to serialise on a background thread. */
    fun copyForSave() = Board(
        id, name, type, backgroundColor, canvasWidth, canvasHeight,
        createdAt, modifiedAt, ArrayList(strokes), true
    )

    companion object {
        fun create(type: BoardType): Board {
            val now = System.currentTimeMillis()
            return Board(UUID.randomUUID().toString(), "Untitled", type, type.bg, 0, 0, now, now, ArrayList(), false)
        }
    }
}
