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
import com.thamightyboro.loadouts.data.Loadout
import com.thamightyboro.loadouts.data.Part
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

    fun render(l: Loadout, parts: Map<String, Part>): Bitmap {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val pad = 60f

        // Background with a faint grid
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, H.toFloat(), BG_TOP, BG_BOTTOM, Shader.TileMode.CLAMP)
        })
        val grid = Paint().apply { color = 0x0CFFFFFF; strokeWidth = 1f }
        for (x in 0..W step 80) c.drawLine(x.toFloat(), 0f, x.toFloat(), H.toFloat(), grid)
        for (y in 0..H step 80) c.drawLine(0f, y.toFloat(), W.toFloat(), y.toFloat(), grid)

        // Header: name + chassis
        val title = paint(GOLD, 66f, bold)
        c.drawText(fit(l.name.ifBlank { "Untitled loadout" }, title, 1080f), pad, 118f, title)
        val sub = paint(TEAL, 28f, bold, 0.12f)
        c.drawText(fit(l.chassis.ifBlank { "No chassis set" }.uppercase(), sub, 1080f), pad, 166f, sub)

        // Header: summary tiles
        val t = l.totals(parts)
        val tileW = 220f
        val tileGap = 20f
        var tx = W - pad - (tileW * 3 + tileGap * 2)
        val limit = l.massLimit
        val massOver = limit != null && t.mass > limit
        tile(
            c, tx, 52f, tileW, 130f, "MASS", fmt(t.mass),
            limit?.let { "of ${fmt(it)}" } ?: "no limit set",
            if (massOver) BAD else TEXT,
            limit?.takeIf { it > 0 }?.let { (t.mass / it).toFloat() },
        )
        tx += tileW + tileGap
        val gen = t.generation
        val drainOver = gen != null && t.drain > gen
        tile(
            c, tx, 52f, tileW, 130f, "REACTOR DRAIN", fmt(t.drain),
            gen?.let { "of ${fmt(it)} generated" } ?: "no reactor fitted",
            if (drainOver) BAD else TEXT,
            gen?.takeIf { it > 0 }?.let { (t.drain / it).toFloat() },
        )
        tx += tileW + tileGap
        tile(c, tx, 52f, tileW, 130f, "SLOTS FILLED", "${t.filled} / ${t.total}", "", TEXT, null)

        // Divider
        c.drawRect(pad, 212f, W - pad, 214f, fill(0x55E8C547))

        // Slot cards
        val slots = l.activeSlots()
        val cols = 4
        val rows = ceil(slots.size / cols.toFloat()).toInt().coerceAtLeast(1)
        val gap = 20f
        val top = 240f
        val bottom = H - 72f
        val cardW = (W - 2 * pad - (cols - 1) * gap) / cols
        val cardH = (bottom - top - (rows - 1) * gap) / rows
        slots.forEachIndexed { i, slot ->
            val x = pad + (i % cols) * (cardW + gap)
            val y = top + (i / cols) * (cardH + gap)
            card(c, RectF(x, y, x + cardW, y + cardH), slot.label, l.slots[slot]?.let(parts::get))
        }

        // Footer
        val foot = paint(MUTED, 22f)
        if (l.notes.isNotBlank()) {
            c.drawText(fit(l.notes.replace('\n', ' '), foot, 1300f), pad, H - 30f, foot)
        }
        val stamp = "SWG Loadouts · " + SimpleDateFormat("d MMM yyyy", Locale.UK).format(Date())
        c.drawText(stamp, W - pad - foot.measureText(stamp), H - 30f, foot)
        return bmp
    }

    private fun tile(
        c: Canvas, x: Float, y: Float, w: Float, h: Float,
        label: String, value: String, sub: String, valueColor: Int, progress: Float?,
    ) {
        c.drawRoundRect(RectF(x, y, x + w, y + h), 14f, 14f, fill(PANEL_HIGH))
        val lp = paint(TEAL, 18f, bold, 0.1f)
        c.drawText(label, x + 18f, y + 32f, lp)
        val vp = paint(valueColor, 40f, bold)
        c.drawText(fit(value, vp, w - 36f), x + 18f, y + 78f, vp)
        val sp = paint(MUTED, 18f)
        if (sub.isNotBlank()) c.drawText(fit(sub, sp, w - 36f), x + 18f, y + 104f, sp)
        if (progress != null) {
            val barY = y + h - 14f
            c.drawRoundRect(RectF(x + 18f, barY, x + w - 18f, barY + 6f), 3f, 3f, fill(0x33FFFFFF))
            val p = progress.coerceIn(0f, 1f)
            c.drawRoundRect(
                RectF(x + 18f, barY, x + 18f + (w - 36f) * p, barY + 6f), 3f, 3f,
                fill(if (progress > 1f) BAD else TEAL),
            )
        }
    }

    private fun card(c: Canvas, r: RectF, slotLabel: String, part: Part?) {
        c.drawRoundRect(r, 14f, 14f, fill(PANEL))
        c.drawRoundRect(r, 14f, 14f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 2f
            color = if (part != null) 0x40E8C547 else 0x18FFFFFF
        })
        // accent bar on the left edge
        c.drawRoundRect(RectF(r.left, r.top + 14f, r.left + 5f, r.bottom - 14f), 3f, 3f, fill(if (part != null) TEAL else 0x22FFFFFF))

        val inner = r.left + 24f
        val innerW = r.width() - 48f
        val lp = paint(TEAL, 19f, bold, 0.1f)
        c.drawText(slotLabel.uppercase(), inner, r.top + 36f, lp)

        if (part == null) {
            val ep = paint(0x66FFFFFF, 26f)
            c.drawText("Empty", inner, r.top + 76f, ep)
            return
        }
        part.reLevel?.let {
            val rp = paint(MUTED, 19f, bold)
            val s = "RE $it"
            c.drawText(s, r.right - 24f - rp.measureText(s), r.top + 36f, rp)
        }
        // Shrink long names a little before resorting to "…"
        val name = part.name.ifBlank { "(unnamed)" }
        val np = paint(GOLD, 28f, bold)
        while (np.textSize > 21f && np.measureText(name) > innerW) np.textSize -= 1f
        c.drawText(fit(name, np, innerW), inner, r.top + 74f, np)

        // Stats in two columns
        val lineH = 28f
        val firstY = r.top + 112f
        val lines = ((r.bottom - 14f - firstY) / lineH).toInt() + 1
        if (lines <= 0) return
        val colGap = 28f
        val colW = (innerW - colGap) / 2
        val labelP = paint(MUTED, 20f)
        val valueP = paint(TEXT, 20f, bold)
        // Fill a two-column grid; a value too long for half a card (e.g. a damage range)
        // gets the whole row to itself.
        var row = 0
        var col = 0
        for (s in orderedStats(part.stats)) {
            val value = tidyValue(s.value)
            val wide = valueP.measureText(value) > colW * 0.6f
            if (wide && col == 1) { row++; col = 0 }
            if (row >= lines) break
            val x = inner + col * (colW + colGap)
            val y = firstY + row * lineH
            val w = if (wide) innerW else colW
            val v = fit(value, valueP, w * 0.72f)
            val vw = valueP.measureText(v)
            c.drawText(fit(shortLabel(s.label), labelP, w - vw - 10f), x, y, labelP)
            c.drawText(v, x + w - vw, y, valueP)
            if (wide || col == 1) { row++; col = 0 } else col = 1
        }
    }

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
