package com.whiteboard.app.ui

import android.app.Activity
import android.app.AlertDialog
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import com.whiteboard.app.data.BoardRepository
import java.text.DateFormat
import java.util.Date

class SavedBoardsActivity : Activity() {
    private lateinit var repo: BoardRepository
    private lateinit var list: LinearLayout
    private lateinit var p: Palette

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        p = palette(); applyWindow(p)
        repo = BoardRepository(this)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(p.bg) }
        val head = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(12), dp(16), dp(20), dp(8)) }
        head.addView(label("‹", 34f, p.text).apply { setPadding(dp(12), 0, dp(16), dp(4)); setOnClickListener { finish() } })
        head.addView(label("Saved Boards", 24f, p.text, true))
        root.addView(head)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(8), dp(16), dp(24)) }
        root.addView(ScrollView(this).apply { addView(list) }, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    override fun onResume() { super.onResume(); refresh() }

    private fun refresh() {
        list.removeAllViews()
        val items = repo.list()
        if (items.isEmpty()) {
            list.addView(label("No saved boards yet.\nCreate one from New Board and tap 💾 to save it.", 16f, p.sub).apply {
                gravity = Gravity.CENTER; setPadding(0, dp(80), 0, 0)
            }, LinearLayout.LayoutParams(-1, -2))
            return
        }
        val df = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
        for (s in items) {
            val card = LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL; setPadding(dp(12), dp(12), dp(8), dp(12))
                background = ripple(rounded(p.card, dpf(18f))); elevation = dpf(2f)
                setOnClickListener { BoardActivity.openSaved(this@SavedBoardsActivity, s.id) }
            }
            val thumb = ImageView(this).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                background = rounded(s.type.bg, dpf(10f)); clipToOutline = true
                if (s.thumb.exists()) setImageBitmap(BitmapFactory.decodeFile(s.thumb.absolutePath))
            }
            card.addView(thumb, LinearLayout.LayoutParams(dp(112), dp(76)))
            val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            col.addView(label(s.name, 17f, p.text, true).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
            col.addView(label("${s.type.label}\n${df.format(Date(s.modifiedAt))}", 13f, p.sub))
            card.addView(col, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(14) })
            val more = label("⋮", 26f, p.text).apply { setPadding(dp(14), dp(6), dp(14), dp(6)) }
            more.setOnClickListener { menu(it, s) }
            card.addView(more)
            list.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        }
    }

    private fun menu(anchor: android.view.View, s: BoardRepository.Summary) {
        PopupMenu(this, anchor).apply {
            menu.add("Open"); menu.add("Rename"); menu.add("Duplicate"); menu.add("Delete")
            setOnMenuItemClickListener {
                when (it.title.toString()) {
                    "Open" -> BoardActivity.openSaved(this@SavedBoardsActivity, s.id)
                    "Rename" -> rename(s)
                    "Duplicate" -> { repo.duplicate(s.id); refresh() }
                    "Delete" -> AlertDialog.Builder(this@SavedBoardsActivity).setTitle("Delete \"${s.name}\"?")
                        .setMessage("This cannot be undone.")
                        .setPositiveButton("Delete") { _, _ -> repo.delete(s.id); refresh() }
                        .setNegativeButton("Cancel", null).show()
                }
                true
            }
        }.show()
    }

    private fun rename(s: BoardRepository.Summary) {
        val et = EditText(this).apply { setText(s.name); setSelectAllOnFocus(true); setSingleLine() }
        val frame = FrameLayout(this).apply { setPadding(dp(20), dp(8), dp(20), 0); addView(et) }
        AlertDialog.Builder(this).setTitle("Rename board").setView(frame)
            .setPositiveButton("Rename") { _, _ ->
                val n = et.text.toString().trim()
                if (n.isNotEmpty()) { repo.rename(s.id, n); refresh() }
            }.setNegativeButton("Cancel", null).show()
    }
}
