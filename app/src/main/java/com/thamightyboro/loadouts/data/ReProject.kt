package com.thamightyboro.loadouts.data

import java.util.UUID

/** A reverse engineering job: the parts going into the analysis tool together. */
data class ReProject(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val items: List<Part> = emptyList(),
    /** RE level set by hand when the scans didn't pick it up; otherwise taken from the parts. */
    val level: Int? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

/**
 * Ship component reverse engineering, as script/space/crafting/analysis_tool.java does it:
 *  - you need exactly as many parts as their RE level, all the same type and RE level;
 *  - the result takes the BEST value of every stat across the parts (not the average);
 *  - then adds the level bonus (1% at L1 up to 6% at L10) plus the expertise bonus,
 *    which we always count at its +1% maximum;
 *  - the result keeps the first part's template (its name/model).
 */
object ReCalc {
    const val EXPERTISE_BONUS = 0.01

    fun levelBonus(level: Int): Double = when (level) {
        1 -> 0.01
        2, 3 -> 0.02
        4, 5 -> 0.03
        6, 7 -> 0.04
        8, 9 -> 0.05
        10 -> 0.06
        else -> 0.0
    }

    fun bonus(level: Int) = levelBonus(level) + EXPERTISE_BONUS

    val reTypes = setOf(
        PartType.ARMOR, PartType.BOOSTER, PartType.CAPACITOR, PartType.DROID_INTERFACE,
        PartType.ENGINE, PartType.REACTOR, PartType.SHIELD, PartType.WEAPON,
    )

    fun lowerIsBetter(label: String): Boolean {
        val l = label.lowercase()
        return "mass" in l || "drain" in l || "maintenance" in l || "energy/shot" in l || "per shot" in l ||
            "refire" in l || "consumption" in l || "command speed" in l
    }

    data class Status(val type: PartType?, val level: Int?, val problems: List<String>) {
        val ready: Boolean get() = type != null && level != null && problems.isEmpty()
    }

    fun status(p: ReProject): Status {
        val first = p.items.firstOrNull() ?: return Status(null, p.level, emptyList())
        val type = first.type
        val level = p.level ?: first.reLevel
        val problems = buildList {
            if (type !in reTypes) add("${type.label} parts can't be reverse engineered.")
            if (p.items.any { it.type != type }) add("Every part must be the same type (${type.label}).")
            if (level == null) {
                add("Set the RE level.")
            } else {
                if (p.items.any { it.reLevel != null && it.reLevel != level }) add("Every part must be RE level $level.")
                if (p.items.size < level) add("Needs exactly $level parts - add ${level - p.items.size} more.")
                if (p.items.size > level) add("Needs exactly $level parts - remove ${p.items.size - level}.")
            }
        }
        return Status(type, level, problems)
    }

    data class Row(
        val label: String,
        val key: String?,
        val values: List<Double?>,
        val bestIndex: Int,
        val best: Double,
        val result: Double,
        val lowerIsBetter: Boolean,
    )

    /** The stats RE works on, read from one part's examine lines. "a-b" damage becomes min and max. */
    private fun readings(part: Part): LinkedHashMap<String, Double> {
        val out = LinkedHashMap<String, Double>()
        for (line in part.stats) {
            val label = line.label.trim()
            val l = label.lowercase()
            if (label.isEmpty() || "reverse engineering" in l) continue
            // Reactors don't get their drain re-rolled, it stays the template's.
            if (part.type == PartType.REACTOR && ("drain" in l || "maintenance" in l)) continue
            val n = Deviation.numbers(line.value)
            if (n.isEmpty()) continue
            if ("damage" in l && n.size >= 2) {
                out["Min Damage"] = n[0]
                out["Max Damage"] = n[1]
            } else {
                out[label] = n.last() // "793.0/793.0" is current/max
            }
        }
        return out
    }

    fun rows(p: ReProject, level: Int): List<Row> {
        val type = p.items.firstOrNull()?.type ?: return emptyList()
        val b = bonus(level)
        val perItem = p.items.map { readings(it) }
        val labels = LinkedHashMap<String, String>() // lowercase -> display label, first appearance order
        perItem.forEach { m -> m.keys.forEach { labels.putIfAbsent(it.lowercase(), it) } }
        return labels.map { (lower, label) ->
            val values = perItem.map { m -> m.entries.firstOrNull { it.key.lowercase() == lower }?.value }
            val lowerBetter = lowerIsBetter(label)
            var bestIndex = -1
            values.forEachIndexed { i, v ->
                if (v == null) return@forEachIndexed
                if ("mass" in lower && v <= 0.0) return@forEachIndexed
                val cur = if (bestIndex < 0) null else values[bestIndex]
                if (cur == null || (lowerBetter && v < cur) || (!lowerBetter && v > cur)) bestIndex = i
            }
            if (bestIndex < 0) return@map null
            val best = values[bestIndex]!!
            val key = when (lower) {
                "min damage" -> "minDamage"
                "max damage" -> "maxDamage"
                else -> Deviation.keyForLabel(label, type)
            }
            Row(label, key, values, bestIndex, best, if (lowerBetter) best * (1 - b) else best * (1 + b), lowerBetter)
        }.filterNotNull()
    }

    private fun fmt(v: Double) = if (kotlin.math.abs(v) < 10) "%.3f".format(v) else "%.1f".format(v)

    /** The finished part, ready to go in the parts library. */
    fun resultPart(p: ReProject, level: Int, rows: List<Row>): Part {
        val first = p.items.first()
        val stats = mutableListOf<StatLine>()
        val min = rows.firstOrNull { it.key == "minDamage" }
        val max = rows.firstOrNull { it.key == "maxDamage" }
        for (r in rows) {
            when (r.key) {
                "minDamage" -> stats += StatLine("Damage", if (max != null) "${fmt(r.result)}-${fmt(max.result)}" else fmt(r.result))
                "maxDamage" -> if (min == null) stats += StatLine("Damage", fmt(r.result))
                else -> stats += StatLine(r.label, fmt(r.result))
            }
        }
        val pct = "%.0f".format(bonus(level) * 100)
        return Part(
            name = first.name,
            type = first.type,
            reLevel = level,
            stats = stats,
            notes = "Reverse engineered from ${p.items.size} parts (+$pct%)" + if (p.name.isNotBlank()) " - ${p.name}" else "",
            refId = first.refId,
        )
    }
}
