package com.thamightyboro.loadouts.export

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.provider.MediaStore
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.FileProvider
import com.thamightyboro.loadouts.data.CommandGroup
import com.thamightyboro.loadouts.data.DroidCommands
import com.thamightyboro.loadouts.data.Loadout
import com.thamightyboro.loadouts.data.power
import com.thamightyboro.loadouts.data.Part
import com.thamightyboro.loadouts.data.Slot
import com.thamightyboro.loadouts.data.StatLine
import com.thamightyboro.loadouts.data.fmt
import com.thamightyboro.loadouts.data.totals
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil

/** Draws a loadout as a 1920x1080 summary card. */
object LoadoutImage {
    const val W = 1920
    const val H = 1080

    private const val BG_TOP = 0xFF0B1517.toInt()
    private const val BG_BOTTOM = 0xFF14282B.toInt()
    private const val PANEL = 0xFF16292C.toInt()
    private const val PANEL_HIGH = 0xFF1F3A3E.toInt()
    private const val GOLD = 0xFFE8C547.toInt()
    private const val TEAL = 0xFF7FD4C1.toInt()
    private const val TEXT = 0xFFE4EEEC.toInt()
    private const val MUTED = 0xFF8FA6A3.toInt()
    private const val BAD = 0xFFE06C5C.toInt()

    private val bold: Typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
    private val regular: Typeface = Typeface.create("sans-serif-condensed", Typeface.NORMAL)

    private fun paint(color: Int, size: Float, face: Typeface = regular, spacing: Float = 0f) =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color; textSize = size; typeface = face; letterSpacing = spacing
        }

    private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }

    private fun fit(text: String, p: TextPaint, width: Float): String =
        TextUtils.ellipsize(text, p, width, TextUtils.TruncateAt.END).toString()

    // Shorter labels so two stat columns fit in each card.
    private val shortLabels = mapOf(
        "reactor energy drain" to "Drain",
        "hitpoints" to "HP",
        "energy generation rate" to "Generation",
        "energy generation" to "Generation",
        "front shield hitpoints" to "Front Shield",
        "back shield hitpoints" to "Back Shield",
        "shield recharge rate" to "Recharge",
        "capacitor energy" to "Cap Energy",
        "booster energy consumption rate" to "Consumption",
        "booster recharge rate" to "Recharge",
        "booster speed maximum" to "Speed",
        "droid command speed" to "Cmd Speed",
        "speed maximum" to "Speed",
        "pitch rate maximum" to "Pitch",
        "yaw rate maximum" to "Yaw",
        "roll rate maximum" to "Roll",
        "booster energy" to "Energy",
        "booster acceleration" to "Accel",
    )

    /** "793.0/793.0" (current/max at full condition) reads better as just "793.0". */
    private fun tidyValue(v: String): String {
        val parts = v.split("/").map { it.trim() }
        return if (parts.size == 2 && parts[0] == parts[1]) parts[0] else v.trim()
    }

    private fun shortLabel(label: String) = shortLabels[label.lowercase().trim()] ?: label

    /** Most useful stats first: mass, drain, generation, then the rest in examine-window order. */
    private fun orderedStats(stats: List<StatLine>): List<StatLine> {
        val priority = listOf("mass", "energy drain", "generation")
        val first = priority.mapNotNull { key -> stats.firstOrNull { it.label.contains(key, true) } }
        return first + stats.filter { it !in first }
    }

    // ---- Card content: every stat, every quality, and notes ----

    private sealed interface Cell
    private class StatCell(val label: String, val value: String, val quality: Boolean) : Cell
    private object SectionCell : Cell
    private class NoteCell(val text: String) : Cell
    private class Placed(val cell: Cell, val row: Int, val col: Int, val span: Int)

    /** "Weapon Shield Effectiveness Quality" -> "Shield Effectiveness" (the section header says Quality). */
    private fun qualityLabel(label: String): String {
        val base = label.replace(Regex("(?i)\\s*quality\\s*$"), "").replace(Regex("(?i)^weapon\\s+"), "").trim()
        return qualityNames[base.lowercase()] ?: shortLabel(base)
    }

    private val qualityNames = mapOf(
        "shield effectiveness" to "Vs. Shields",
        "armor effectiveness" to "Vs. Armor",
        "energy maintenance" to "Drain",
        "energy/shot" to "Energy/Shot",
        "refire rate" to "Refire Rate",
        "min damage" to "Min Damage",
        "max damage" to "Max Damage",
        "energy generation" to "Generation",
    )

    /** [drain]/[generation]: projected values with droid commands running (shown as "before → after"). */
    private fun cellsFor(part: Part, drain: Pair<Double, Double>? = null, generation: Pair<Double, Double>? = null): List<Cell> = buildList {
        orderedStats(part.stats).forEach {
            val l = it.label.lowercase()
            val v = when {
                drain != null && "energy drain" in l -> "${fmt(drain.first)} \u2192 ${fmt(drain.second)}"
                generation != null && "generation" in l -> "${fmt(generation.first)} \u2192 ${fmt(generation.second)}"
                else -> tidyValue(it.value)
            }
            add(StatCell(shortLabel(it.label), v, false))
        }
        if (part.qualities.isNotEmpty()) {
            add(SectionCell)
            part.qualities.forEach { add(StatCell(qualityLabel(it.label), it.value.trim(), true)) }
        }
        if (part.notes.isNotBlank()) add(NoteCell(part.notes.replace('\n', ' ').trim()))
    }

    /** Lays cells into a grid; long values and section rows take a full row. Returns placements and row count. */
    private fun place(cells: List<Cell>, cols: Int, colW: Float, valueP: TextPaint): Pair<List<Placed>, Int> {
        val out = mutableListOf<Placed>()
        var row = 0
        var col = 0
        for (cell in cells) {
            val full = cell !is StatCell || valueP.measureText(cell.value) > colW * 0.55f
            if (full) {
                if (col != 0) { row++; col = 0 }
                out += Placed(cell, row, 0, cols)
                row++
            } else {
                out += Placed(cell, row, col, 1)
                col++
                if (col == cols) { row++; col = 0 }
            }
        }
        return out to (if (col == 0) row else row + 1)
    }

    fun render(l: Loadout, parts: Map<String, Part>): Bitmap {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val pad = 40f

        // Background with a faint grid
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, H.toFloat(), BG_TOP, BG_BOTTOM, Shader.TileMode.CLAMP)
        })
        val grid = Paint().apply { color = 0x0CFFFFFF; strokeWidth = 1f }
        for (x in 0..W step 80) c.drawLine(x.toFloat(), 0f, x.toFloat(), H.toFloat(), grid)
        for (y in 0..H step 80) c.drawLine(0f, y.toFloat(), W.toFloat(), y.toFloat(), grid)

        // Header: name + chassis (compact, so the slot cards get the room)
        val title = paint(GOLD, 46f, bold)
        c.drawText(fit(l.name.ifBlank { "Untitled loadout" }, title, 1100f), pad, 70f, title)
        val sub = paint(TEAL, 22f, bold, 0.12f)
        val chassisText = fit(l.chassis.ifBlank { "No chassis set" }.uppercase(), sub, 1100f)
        c.drawText(chassisText, pad, 104f, sub)
        val cmds = CommandGroup.entries.mapNotNull { g -> DroidCommands.find(l.droidCommands[g])?.label }
        if (cmds.isNotEmpty()) {
            val cp = paint(GOLD, 22f, bold, 0.06f)
            val cx = pad + sub.measureText(chassisText) + 24f
            c.drawText(fit("DROID: " + cmds.joinToString("  \u00b7  ").uppercase(), cp, 1140f - cx), cx, 104f, cp)
        }

        // Header: summary tiles
        val t = l.totals(parts)
        val pw = l.power(parts)
        val drainBySlot = pw.slots.filter { it.drain != it.baseDrain }.associate { it.slot to (it.baseDrain to it.drain) }
        val genChange = pw.baseGeneration?.let { b -> pw.generation?.takeIf { it != b }?.let { b to it } }
        fun cells(slot: Slot, part: Part) = cellsFor(part, drainBySlot[slot], if (slot == Slot.REACTOR) genChange else null)
        val tileW = 210f
        val tileH = 94f
        val tileGap = 16f
        var tx = W - pad - (tileW * 3 + tileGap * 2)
        val limit = l.massLimit
        val massOver = limit != null && t.mass > limit
        tile(
            c, tx, 24f, tileW, tileH, "MASS", fmt(t.mass),
            limit?.let { "of ${fmt(it)}" } ?: "no limit set",
            if (massOver) BAD else TEXT,
            limit?.takeIf { it > 0 }?.let { (t.mass / it).toFloat() },
        )
        tx += tileW + tileGap
        // Projected with droid commands (same as plain drain when none are set)
        val gen = pw.generation
        val drainOver = gen != null && pw.drain > gen
        tile(
            c, tx, 24f, tileW, tileH, if (cmds.isNotEmpty()) "DRAIN (DROID CMDS)" else "REACTOR DRAIN", fmt(pw.drain),
            gen?.let { "of ${fmt(it)} generated" } ?: "no reactor fitted",
            if (drainOver) BAD else TEXT,
            gen?.takeIf { it > 0 }?.let { (pw.drain / it).toFloat() },
        )
        tx += tileW + tileGap
        tile(c, tx, 24f, tileW, tileH, "SLOTS FILLED", "${t.filled} / ${t.total}", "", TEXT, null)

        // Divider
        c.drawRect(pad, 132f, W - pad, 134f, fill(0x55E8C547))

        // Slot cards
        val slots = l.activeSlots()
        val cols = 4
        val rows = ceil(slots.size / cols.toFloat()).toInt().coerceAtLeast(1)
        val gap = 14f
        val top = 150f
        val bottom = H - 40f
        val cardW = (W - 2 * pad - (cols - 1) * gap) / cols
        val cardH = (bottom - top - (rows - 1) * gap) / rows
        // One text size for every card (tidier): the largest at which every card fits in full.
        val cellsBySlot = slots.mapNotNull { s -> l.slots[s]?.let(parts::get)?.let { s to cells(s, it) } }.toMap()
        val innerW = cardW - 32f
        val size = (20 downTo 8).firstOrNull { sz ->
            cellsBySlot.values.all { layoutFor(it, innerW, cardH, sz.toFloat()) != null }
        }?.toFloat() ?: 8f
        slots.forEachIndexed { i, slot ->
            val x = pad + (i % cols) * (cardW + gap)
            val y = top + (i / cols) * (cardH + gap)
            val part = l.slots[slot]?.let(parts::get)
            card(c, RectF(x, y, x + cardW, y + cardH), slot.label, part, cellsBySlot[slot].orEmpty(), size)
        }

        // Footer
        val foot = paint(MUTED, 18f)
        if (l.notes.isNotBlank()) {
            c.drawText(fit(l.notes.replace('\n', ' '), foot, 1400f), pad, H - 14f, foot)
        }
        val stamp = "SWG Loadouts \u00b7 " + SimpleDateFormat("d MMM yyyy", Locale.UK).format(Date())
        c.drawText(stamp, W - pad - foot.measureText(stamp), H - 14f, foot)
        return bmp
    }

    private fun tile(
        c: Canvas, x: Float, y: Float, w: Float, h: Float,
        label: String, value: String, sub: String, valueColor: Int, progress: Float?,
    ) {
        c.drawRoundRect(RectF(x, y, x + w, y + h), 12f, 12f, fill(PANEL_HIGH))
        val lp = paint(TEAL, 15f, bold, 0.1f)
        c.drawText(label, x + 14f, y + 24f, lp)
        val vp = paint(valueColor, 30f, bold)
        c.drawText(fit(value, vp, w - 28f), x + 14f, y + 58f, vp)
        val sp = paint(MUTED, 15f)
        if (sub.isNotBlank()) c.drawText(fit(sub, sp, w - 28f), x + 14f, y + 78f, sp)
        if (progress != null) {
            val barY = y + h - 9f
            c.drawRoundRect(RectF(x + 14f, barY, x + w - 14f, barY + 4f), 2f, 2f, fill(0x33FFFFFF))
            val p = progress.coerceIn(0f, 1f)
            c.drawRoundRect(
                RectF(x + 14f, barY, x + 14f + (w - 28f) * p, barY + 4f), 2f, 2f,
                fill(if (progress > 1f) BAD else TEAL),
            )
        }
    }

    private fun card(c: Canvas, r: RectF, slotLabel: String, part: Part?, cells: List<Cell>, sz: Float) {
        c.drawRoundRect(r, 12f, 12f, fill(PANEL))
        c.drawRoundRect(r, 12f, 12f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 2f
            color = if (part != null) 0x40E8C547 else 0x18FFFFFF
        })
        c.drawRoundRect(RectF(r.left, r.top + 12f, r.left + 4f, r.bottom - 12f), 2f, 2f, fill(if (part != null) TEAL else 0x22FFFFFF))

        val inner = r.left + 16f
        val innerW = r.width() - 32f
        if (part == null) {
            c.drawText(slotLabel.uppercase(), inner, r.top + 26f, paint(TEAL, 15f, bold, 0.1f))
            c.drawText("Empty", inner, r.top + 52f, paint(0x66FFFFFF, 18f))
            return
        }

        val (cols, placedRows) = layoutFor(cells, innerW, r.height(), sz)
            ?: (3 to place(cells, 3, (innerW - 2 * sz) / 3, paint(TEXT, sz, bold)))
        val colGap = sz
        val colW = (innerW - (cols - 1) * colGap) / cols

        // Card header: slot + RE level, then the part name
        val lp = paint(TEAL, (sz * 0.8f).coerceAtLeast(11f), bold, 0.1f)
        val labelBase = r.top + 10f + lp.textSize
        c.drawText(slotLabel.uppercase(), inner, labelBase, lp)
        part.reLevel?.let {
            val rp = paint(MUTED, lp.textSize, bold)
            val s = "RE $it"
            c.drawText(s, r.right - 16f - rp.measureText(s), labelBase, rp)
        }
        val name = part.name.ifBlank { "(unnamed)" }
        val np = paint(GOLD, sz * 1.25f, bold)
        while (np.textSize > sz && np.measureText(name) > innerW) np.textSize -= 1f
        val nameBase = labelBase + 6f + np.textSize
        c.drawText(fit(name, np, innerW), inner, nameBase, np)

        // Stats grid
        val labelP = paint(MUTED, sz)
        val valueP = paint(TEXT, sz, bold)
        val qualP = paint(TEAL, sz, bold)
        val lh = lineHeight(sz)
        val firstBase = r.top + headerHeight(sz) + sz
        for (pl in placedRows.first) {
            val x = inner + pl.col * (colW + colGap)
            val y = firstBase + pl.row * lh
            if (y > r.bottom - 4f) continue // only possible at the very smallest size
            val w = colW * pl.span + colGap * (pl.span - 1)
            when (val cell = pl.cell) {
                is SectionCell -> {
                    val sp = paint(TEAL, (sz * 0.75f).coerceAtLeast(10f), bold, 0.12f)
                    c.drawText("SPACE EVALUATION", x, y - sz * 0.15f, sp)
                    val after = x + sp.measureText("SPACE EVALUATION") + 8f
                    c.drawRect(after, y - sz * 0.4f, x + w, y - sz * 0.4f + 1f, fill(0x337FD4C1))
                }
                is NoteCell -> {
                    val notep = paint(MUTED, sz)
                    notep.textSkewX = -0.2f
                    c.drawText(fit(cell.text, notep, w), x, y, notep)
                }
                is StatCell -> {
                    val vp = if (cell.quality) qualP else valueP
                    val v = fit(cell.value, vp, w * 0.75f)
                    val vw = vp.measureText(v)
                    c.drawText(fit(cell.label, labelP, w - vw - sz * 0.5f), x, y, labelP)
                    c.drawText(v, x + w - vw, y, vp)
                }
            }
        }
    }

    /**
     * A grid layout for this card at text size [sz], or null if it doesn't fit: every row must fit
     * in the card height and every label must show in full (no "…"). Tries 2 columns, then 3.
     */
    private fun layoutFor(cells: List<Cell>, innerW: Float, cardH: Float, sz: Float): Pair<Int, Pair<List<Placed>, Int>>? {
        val labelP = paint(MUTED, sz)
        val valueP = paint(TEXT, sz, bold)
        for (cols in listOf(2, 3)) {
            val colW = (innerW - (cols - 1) * sz) / cols
            val placed = place(cells, cols, colW, valueP)
            if (headerHeight(sz) + placed.second * lineHeight(sz) + 8f > cardH) continue
            val labelsFit = placed.first.all { pl ->
                val cell = pl.cell
                if (cell !is StatCell) true else {
                    val w = colW * pl.span + sz * (pl.span - 1)
                    labelP.measureText(cell.label) + valueP.measureText(cell.value) + sz * 0.5f <= w
                }
            }
            if (labelsFit) return cols to placed
        }
        return null
    }

    private fun lineHeight(sz: Float) = sz * 1.32f
    private fun headerHeight(sz: Float) = 10f + (sz * 0.8f).coerceAtLeast(11f) + 6f + sz * 1.25f + sz * 0.6f

    class Saved(val shareUri: android.net.Uri, val inGallery: Boolean)

    /**
     * Renders and saves the image: to the gallery (Pictures/SWG Loadouts, Android 10+) and to a
     * cache copy for sharing. Heavy work - call off the main thread.
     */
    fun save(context: Context, l: Loadout, parts: Map<String, Part>): Saved {
        val bmp = render(l, parts)
        val safeName = l.name.ifBlank { "loadout" }.replace(Regex("[^A-Za-z0-9_-]+"), "_").take(40)
        val fileName = "${safeName}_${System.currentTimeMillis()}.png"

        var savedToGallery = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SWG Loadouts")
            }
            context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)?.let { uri ->
                context.contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                savedToGallery = true
            }
        }

        // Share copy from the app's cache
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, fileName)
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Saved(uri, savedToGallery)
    }

    /** Opens the Android share sheet for a saved image. Call on the main thread. */
    fun share(context: Context, uri: android.net.Uri) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(send, "Share loadout").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
