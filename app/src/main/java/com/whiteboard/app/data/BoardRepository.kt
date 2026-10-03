package com.whiteboard.app.data

import android.content.Context
import android.graphics.Bitmap
import android.util.JsonReader
import android.util.JsonWriter
import com.whiteboard.app.model.*
import org.json.JSONObject
import java.io.*
import java.util.UUID

/**
 * Persists boards as stroke data (never as screenshots).
 *   boards/<id>.json  full board (strokes + points)
 *   boards/<id>.meta  tiny summary used by the gallery
 *   boards/<id>.png   thumbnail
 */
class BoardRepository(ctx: Context) {
    private val dir = File(ctx.filesDir, "boards").apply { mkdirs() }

    class Summary(
        val id: String, val name: String, val type: BoardType,
        val createdAt: Long, val modifiedAt: Long, val thumb: File
    )

    private fun boardFile(id: String) = File(dir, "$id.json")
    private fun metaFile(id: String) = File(dir, "$id.meta")
    private fun thumbFile(id: String) = File(dir, "$id.png")

    @Synchronized
    fun save(b: Board, thumb: Bitmap?) {
        val tmp = File(dir, "${b.id}.json.tmp")
        JsonWriter(BufferedWriter(OutputStreamWriter(FileOutputStream(tmp), Charsets.UTF_8))).use { w -> write(w, b) }
        if (!tmp.renameTo(boardFile(b.id))) {
            boardFile(b.id).delete(); tmp.renameTo(boardFile(b.id))
        }
        metaFile(b.id).writeText(
            JSONObject().put("id", b.id).put("name", b.name).put("type", b.type.name)
                .put("createdAt", b.createdAt).put("modifiedAt", b.modifiedAt).toString()
        )
        thumb?.let { t -> FileOutputStream(thumbFile(b.id)).use { t.compress(Bitmap.CompressFormat.PNG, 100, it) } }
    }

    fun load(id: String): Board? = try {
        JsonReader(BufferedReader(InputStreamReader(FileInputStream(boardFile(id)), Charsets.UTF_8))).use { read(it) }
    } catch (e: Exception) { null }

    fun list(): List<Summary> = (dir.listFiles { f -> f.name.endsWith(".meta") } ?: emptyArray())
        .mapNotNull {
            try {
                val j = JSONObject(it.readText())
                Summary(
                    j.getString("id"), j.getString("name"), BoardType.valueOf(j.getString("type")),
                    j.getLong("createdAt"), j.getLong("modifiedAt"), thumbFile(j.getString("id"))
                )
            } catch (e: Exception) { null }
        }.sortedByDescending { it.modifiedAt }

    fun delete(id: String) { boardFile(id).delete(); metaFile(id).delete(); thumbFile(id).delete() }

    fun rename(id: String, name: String) {
        val b = load(id) ?: return
        b.name = name; b.modifiedAt = System.currentTimeMillis()
        save(b, null)
    }

    fun duplicate(id: String): String? {
        val b = load(id) ?: return null
        val now = System.currentTimeMillis()
        val copy = Board(UUID.randomUUID().toString(), b.name + " (copy)", b.type, b.backgroundColor,
            b.canvasWidth, b.canvasHeight, now, now, b.strokes, true)
        save(copy, null)
        if (thumbFile(id).exists()) thumbFile(id).copyTo(thumbFile(copy.id), true)
        return copy.id
    }

    // ---------- JSON ----------
    private fun write(w: JsonWriter, b: Board) {
        w.beginObject()
        w.name("version").value(1)
        w.name("id").value(b.id)
        w.name("name").value(b.name)
        w.name("type").value(b.type.name)
        w.name("background").beginObject().name("type").value("SOLID").name("color").value(b.backgroundColor).endObject()
        w.name("canvas").beginObject().name("width").value(b.canvasWidth).name("height").value(b.canvasHeight).endObject()
        w.name("createdAt").value(b.createdAt)
        w.name("modifiedAt").value(b.modifiedAt)
        w.name("strokes").beginArray()
        for (s in b.strokes) {
            w.beginObject()
            w.name("id").value(s.id)
            w.name("tool").value(s.tool.name)
            w.name("color").value(s.color)
            w.name("opacity").value(s.opacity)
            w.name("size").value(s.size)
            w.name("points").beginArray()
            for (p in s.points) {
                w.beginArray().value(p.x).value(p.y).value(p.pressure).endArray()
            }
            w.endArray()
            w.endObject()
        }
        w.endArray()
        w.endObject()
    }

    private fun read(r: JsonReader): Board {
        var id = ""; var name = ""; var type = BoardType.WHITE; var bg = 0; var cw = 0; var ch = 0
        var created = 0L; var modified = 0L
        val strokes = ArrayList<Stroke>()
        r.beginObject()
        while (r.hasNext()) {
            when (r.nextName()) {
                "id" -> id = r.nextString()
                "name" -> name = r.nextString()
                "type" -> type = BoardType.valueOf(r.nextString())
                "background" -> { r.beginObject(); while (r.hasNext()) { if (r.nextName() == "color") bg = r.nextInt() else r.skipValue() }; r.endObject() }
                "canvas" -> { r.beginObject(); while (r.hasNext()) { when (r.nextName()) { "width" -> cw = r.nextInt(); "height" -> ch = r.nextInt(); else -> r.skipValue() } }; r.endObject() }
                "createdAt" -> created = r.nextLong()
                "modifiedAt" -> modified = r.nextLong()
                "strokes" -> { r.beginArray(); while (r.hasNext()) strokes.add(readStroke(r)); r.endArray() }
                else -> r.skipValue()
            }
        }
        r.endObject()
        return Board(id, name, type, bg, cw, ch, created, modified, strokes, true)
    }

    private fun readStroke(r: JsonReader): Stroke {
        var id = ""; var tool = Tool.PEN; var color = 0; var opacity = 1f; var size = 4f
        val pts = ArrayList<StrokePoint>()
        r.beginObject()
        while (r.hasNext()) {
            when (r.nextName()) {
                "id" -> id = r.nextString()
                "tool" -> tool = Tool.valueOf(r.nextString())
                "color" -> color = r.nextInt()
                "opacity" -> opacity = r.nextDouble().toFloat()
                "size" -> size = r.nextDouble().toFloat()
                "points" -> {
                    r.beginArray()
                    while (r.hasNext()) {
                        r.beginArray()
                        val x = r.nextDouble().toFloat(); val y = r.nextDouble().toFloat(); val p = r.nextDouble().toFloat()
                        r.endArray()
                        pts.add(StrokePoint(x, y, p))
                    }
                    r.endArray()
                }
                else -> r.skipValue()
            }
        }
        r.endObject()
        return Stroke(id, tool, color, opacity, size, pts).also { s -> s.pressureSensitive = pts.any { it.pressure != 1f } }
    }
}
