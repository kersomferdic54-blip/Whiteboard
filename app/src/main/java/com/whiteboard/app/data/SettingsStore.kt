package com.whiteboard.app.data

import android.content.Context
import com.whiteboard.app.model.Tool

class SettingsStore(ctx: Context) {
    private val p = ctx.getSharedPreferences("whiteboard_settings", Context.MODE_PRIVATE)

    var buttonOpacity: Int
        get() = p.getInt("btn_opacity", 85)
        set(v) { p.edit().putInt("btn_opacity", v).apply() }
    var toolbarOpacity: Int
        get() = p.getInt("bar_opacity", 95)
        set(v) { p.edit().putInt("bar_opacity", v).apply() }
    var buttonSizeDp: Int
        get() = p.getInt("btn_size", 56)
        set(v) { p.edit().putInt("btn_size", v).apply() }

    fun toolSize(t: Tool): Int = p.getInt("size_${t.name}", when (t) { Tool.PEN -> 4; Tool.BRUSH -> 28; Tool.ERASER -> 40 })
    fun toolOpacity(t: Tool): Int = p.getInt("op_${t.name}", when (t) { Tool.PEN -> 100; Tool.BRUSH -> 70; Tool.ERASER -> 100 })
    fun setToolSize(t: Tool, v: Int) { p.edit().putInt("size_${t.name}", v).apply() }
    fun setToolOpacity(t: Tool, v: Int) { p.edit().putInt("op_${t.name}", v).apply() }

    fun recentColors(): List<Int> =
        p.getString("recent", null)?.split(",")?.mapNotNull { it.toIntOrNull() }?.takeIf { it.isNotEmpty() } ?: DEFAULTS

    fun addRecent(c: Int) {
        val list = (listOf(c) + recentColors().filter { it != c }).take(12)
        p.edit().putString("recent", list.joinToString(",")).apply()
    }

    companion object {
        val DEFAULTS = listOf(0xFF000000, 0xFFFFFFFF, 0xFFE53935, 0xFFFB8C00, 0xFFFDD835, 0xFF43A047, 0xFF1E88E5, 0xFF8E24AA)
            .map { it.toInt() }
    }
}
