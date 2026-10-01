package com.thamightyboro.loadouts.ocr

import com.thamightyboro.loadouts.data.Part
import com.thamightyboro.loadouts.data.PartType
import com.thamightyboro.loadouts.data.StatLine
import kotlin.math.abs

/** A line of recognised text with its position in the image. */
data class OcrLine(val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val height get() = (bottom - top).coerceAtLeast(1)
    val centerY get() = (top + bottom) / 2
}

/**
 * Turns the text read from an SWG examine window into a draft Part.
 *
 * The examine window puts labels and values in separate columns, so the OCR engine
 * usually returns "Armor:" and "793.0/793.0" as separate pieces. We rebuild the rows
 * by position, then pull "label: value" pairs out of each row.
 */
object ExamineParser {

    private val ignoredLabels = listOf(
        "volume", "condition", "serial", "crafter", "cash", "bank", "complexity", "charges",
    )

    private val valueRegex =
        Regex("""\d[\d,]*\.?\d*(?:\s*[/\-–]\s*\d[\d,]*\.?\d*)?\s*%?""")

    fun parse(lines: List<OcrLine>): Part {
        val rows = buildRows(lines)
        if (rows.isEmpty()) return Part()

        val name = guessName(rows.first())
        val stats = mutableListOf<StatLine>()
        val qualities = mutableListOf<StatLine>()
        var reLevel: Int? = null
        var pendingLabel = ""

        var i = 0
        while (i < rows.size) {
            val row = rows[i]
            i++
            val colon = row.indexOf(':')
            if (colon < 0) {
                // A label that wrapped onto two lines, e.g. "Weapon Shield Effectiveness" / "Quality: 90.06%"
                pendingLabel = if (row.any { it.isLetter() } && row.none { it.isDigit() }) {
                    (pendingLabel + " " + row).trim()
                } else ""
                continue
            }

            val head = row.substring(0, colon).trim()
            // Only glue a wrapped line on when this row is the tail of a quality label,
            // so section headings like "Ship Component" don't leak into the next stat.
            val label = cleanLabel(
                if (head.lowercase().startsWith("quality")) "$pendingLabel $head" else head
            )
            pendingLabel = ""
            var value = valueRegex.find(row.substring(colon + 1))?.value?.trim()

            // Value sometimes drops onto the next line ("Energy Maintenance Quality:" / "83.19%")
            if (value == null && i < rows.size) {
                val next = rows[i].trim()
                val m = valueRegex.find(next)
                if (m != null && m.range.first == 0) {
                    value = m.value.trim()
                    i++
                }
            }
            if (value == null || label.length < 2) continue
            val lower = label.lowercase()
            if (ignoredLabels.any { lower.startsWith(it) }) continue

            when {
                lower.contains("reverse engineering") -> reLevel = value.toDoubleOrNull()?.toInt()
                lower.contains("quality") -> qualities += StatLine(label, value)
                else -> stats += StatLine(label, value)
            }
        }

        return Part(
            name = name,
            type = guessType(stats),
            reLevel = reLevel,
            stats = stats,
            qualities = qualities,
        )
    }

    /** Groups lines into visual rows and keeps only the left-most column cluster of each row. */
    private fun buildRows(all: List<OcrLine>): List<String> {
        if (all.isEmpty()) return emptyList()
        val medianH = all.map { it.height }.sorted()[all.size / 2]

        // Work out where the examine window's text column ends: the right edge of the
        // furthest-reaching "label:" piece that starts in the left half. Anything starting
        // beyond that belongs to whatever is beside the window (inventory, other panels).
        val farRight = all.maxOf { it.right }
        val labelEdge = all.filter { ':' in it.text && it.left < farRight / 2 }.maxOfOrNull { it.right }
        val title = all.minByOrNull { it.top }
        val lines = if (labelEdge == null) all else all.filter { it === title || it.left <= labelEdge + medianH }

        val sorted = lines.sortedBy { it.centerY }
        val rows = mutableListOf<MutableList<OcrLine>>()
        for (line in sorted) {
            val row = rows.lastOrNull()
            if (row != null && abs(row.map { it.centerY }.average() - line.centerY) < medianH * 0.6) {
                row += line
            } else {
                rows += mutableListOf(line)
            }
        }
        // Anything far to the right (e.g. the inventory panel next to the window) is dropped.
        val maxGap = medianH * 8
        return rows.map { row ->
            val ordered = row.sortedBy { it.left }
            val kept = mutableListOf(ordered.first())
            for (l in ordered.drop(1)) {
                if (l.left - kept.last().right > maxGap) break
                kept += l
            }
            kept.joinToString(" ") { it.text.trim() }
        }.filter { it.isNotBlank() }
    }

    private fun cleanLabel(s: String): String =
        s.replace(Regex("""\s+"""), " ")
            .trim()
            .trimStart { !it.isLetter() }          // stray quote marks / cursor debris
            .replace(Regex("""^[¥Y]s\.""", RegexOption.IGNORE_CASE), "Vs.")
            .replace(Regex("""^VWeapon"""), "Weapon")
            .trimEnd('.', ',', ' ')

    private fun guessName(firstRow: String): String {
        val t = firstRow.trim()
        if (t.contains(':') || t.count { it.isLetter() } < 4) return ""
        // Title bar is all caps; make it readable but keep quotes and hyphens.
        return t.lowercase().split(" ").joinToString(" ") { word ->
            word.split("-").joinToString("-") { part ->
                val idx = part.indexOfFirst { it.isLetter() }
                if (idx < 0) part else part.substring(0, idx) + part[idx].uppercaseChar() + part.substring(idx + 1)
            }
        }
    }

    fun guessType(stats: List<StatLine>): PartType {
        val labels = stats.joinToString("|") { it.label.lowercase() }
        return when {
            "refire" in labels || "energy/shot" in labels || "vs. shields" in labels -> PartType.WEAPON
            "generation" in labels -> PartType.REACTOR
            "capacitor energy" in labels -> PartType.CAPACITOR
            "booster" in labels -> PartType.BOOSTER
            "droid command" in labels -> PartType.DROID_INTERFACE
            "shield" in labels && "recharge" in labels -> PartType.SHIELD
            "front shield" in labels || "back shield" in labels -> PartType.SHIELD
            "speed" in labels || "acceleration" in labels || "pitch" in labels || "yaw" in labels -> PartType.ENGINE
            "recharge" in labels -> PartType.CAPACITOR
            "armor" in labels -> PartType.ARMOR
            else -> PartType.UNKNOWN
        }
    }
}
