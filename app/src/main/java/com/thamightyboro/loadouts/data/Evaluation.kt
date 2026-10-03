package com.thamightyboro.loadouts.data

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.ln1p
import kotlin.math.sqrt

/**
 * Deviation maths for ship component rolls.
 *
 * The server rolls each stat as  value = avg * (1 + z * mod / 2)  where z is a standard
 * bell-curve roll (space_crafting.randBell). z is the "deviation". The original code doesn't
 * clamp it, so rolls of 4-5 deviations are possible, just very rare.
 */
object Deviation {

    data class StatDef(val key: String, val label: String, val lowerIsBetter: Boolean)

    val statDefs = listOf(
        StatDef("mass", "Mass", true),
        StatDef("drain", "Reactor Drain", true),
        StatDef("armor", "Armor / HP", false),
        StatDef("minDamage", "Min Damage", false),
        StatDef("maxDamage", "Max Damage", false),
        StatDef("vsShields", "Vs. Shields", false),
        StatDef("vsArmor", "Vs. Armor", false),
        StatDef("energyPerShot", "Energy/Shot", true),
        StatDef("refireRate", "Refire Rate", true),
        StatDef("shieldFront", "Front Shield HP", false),
        StatDef("shieldBack", "Back Shield HP", false),
        StatDef("shieldRecharge", "Shield Recharge", false),
        StatDef("generation", "Energy Generation", false),
        StatDef("speed", "Speed", false),
        StatDef("pitch", "Pitch", false),
        StatDef("yaw", "Yaw", false),
        StatDef("roll", "Roll", false),
        StatDef("capEnergy", "Capacitor Energy", false),
        StatDef("capRecharge", "Capacitor Recharge", false),
        StatDef("boosterEnergy", "Booster Energy", false),
        StatDef("boosterRecharge", "Booster Recharge", false),
        StatDef("consumption", "Consumption", true),
        StatDef("acceleration", "Acceleration", false),
        StatDef("boosterSpeed", "Booster Speed", false),
        StatDef("commandSpeed", "Command Speed", true),
    )
    val defsByKey = statDefs.associateBy { it.key }

    /** Maps an examine-window label to a stat key, given the part type. */
    fun keyForLabel(label: String, type: PartType): String? {
        // Tolerate OCR misreads: "Vs. Arnmor", "EnergyfShot", "Energy /Shot"
        val l = label.lowercase().replace(Regex("ar[nm]{1,2}or"), "armor")
        return when {
            "mass" in l -> "mass"
            "drain" in l || "maintenance" in l -> "drain"
            l.startsWith("vs") && "shield" in l -> "vsShields"
            l.startsWith("vs") -> "vsArmor"
            "shot" in l -> "energyPerShot"
            "refire" in l -> "refireRate"
            "front" in l && "shield" in l -> "shieldFront"
            "back" in l && "shield" in l -> "shieldBack"
            "generation" in l -> "generation"
            "armor" in l || l == "hp" || l.startsWith("hitpoints") -> "armor"
            type == PartType.SHIELD && "recharge" in l -> "shieldRecharge"
            type == PartType.CAPACITOR && "recharge" in l -> "capRecharge"
            type == PartType.CAPACITOR && "energy" in l -> "capEnergy"
            type == PartType.BOOSTER && "consumption" in l -> "consumption"
            type == PartType.BOOSTER && "accel" in l -> "acceleration"
            type == PartType.BOOSTER && "recharge" in l -> "boosterRecharge"
            type == PartType.BOOSTER && "speed" in l -> "boosterSpeed"
            type == PartType.BOOSTER && "energy" in l -> "boosterEnergy"
            type == PartType.ENGINE && "pitch" in l -> "pitch"
            type == PartType.ENGINE && "yaw" in l -> "yaw"
            type == PartType.ENGINE && "roll" in l -> "roll"
            type == PartType.ENGINE && "speed" in l -> "speed"
            "command" in l -> "commandSpeed"
            else -> null
        }
    }

    data class Reading(val key: String, val label: String, val value: Double)

    // Unsigned on purpose: in "3612.4-4450.1" the dash separates min and max, it isn't a minus sign.
    private val num = Regex("""\d+(?:[.,]\d+)*""")

    /**
     * Reads one OCR'd number, coping with thousands separators that come through as either
     * "," or "." ("4,450.1", "4.450.1", "4450.1" all give 4450.1).
     */
    internal fun parseNumber(token: String): Double? {
        val parts = token.split(',', '.')
        if (parts.size == 1) return token.toDoubleOrNull()
        val last = parts.last()
        val seps = token.filter { it == ',' || it == '.' }
        return if (parts.size == 2 && last.length == 3 && seps == ",") {
            (parts[0] + last).toDoubleOrNull() // "4,450" = thousands
        } else if (parts.size == 2) {
            "${parts[0]}.$last".toDoubleOrNull() // "0.446", "4450.1"
        } else if (last.length == 3) {
            parts.joinToString("").toDoubleOrNull() // "1,234,567"
        } else {
            (parts.dropLast(1).joinToString("") + "." + last).toDoubleOrNull() // "4.450.1" / "4,450.1"
        }
    }

    private fun nums(s: String) = num.findAll(s).mapNotNull { parseNumber(it.value) }.toList()

    /** Every number in an examine value, e.g. "3612.4-4450.1" -> [3612.4, 4450.1]. */
    fun numbers(s: String): List<Double> = nums(s)

    /** Pulls rollable stat values out of a part's examine lines. "a-b" damage gives min and max. */
    fun readings(part: Part): List<Reading> {
        val out = mutableListOf<Reading>()
        for (line in part.stats) {
            val l = line.label.lowercase()
            val n = nums(line.value)
            if (n.isEmpty()) continue
            if ("damage" in l) {
                out += Reading("minDamage", "Min Damage", n[0])
                if (n.size > 1) out += Reading("maxDamage", "Max Damage", n[1])
                continue
            }
            val key = keyForLabel(line.label, part.type) ?: continue
            if (out.any { it.key == key }) continue // "Armor" and "Hitpoints" are the same roll
            // "793.0/793.0" is current/max: use max
            out += Reading(key, defsByKey[key]?.label ?: line.label, n.last())
        }
        return out
    }

    /** Deviation (z) of a value: bell stats directly; uniform stats mapped so the range ends sit at +/-3. */
    fun z(stat: RefStat, value: Double): Double {
        if (stat.mod <= 0.0 || stat.avg == 0.0) return 0.0
        return if (stat.uniform) (value - stat.avg) / stat.mod * 3.0
        else (value / stat.avg - 1.0) / (stat.mod / 2.0)
    }

    /** z flipped so positive is always better for the player. */
    fun goodness(key: String, z: Double) = if (defsByKey[key]?.lowerIsBetter == true) -z else z

    fun valueAt(stat: RefStat, z: Double): Double =
        if (stat.uniform) stat.avg + stat.mod * (z / 3.0).coerceIn(-1.0, 1.0)
        else stat.avg * (1.0 + z * stat.mod / 2.0)

    // ---- Matching a scanned part to its template ----

    data class Match(val item: RefItem, val score: Double)

    /** Ranks templates by how well they explain the part's stats (sum of squared deviations). */
    fun rank(part: Part, ref: RefData, limit: Int = 12): List<Match> {
        val rs = readings(part)
        if (rs.isEmpty()) return emptyList()
        val words = part.name.lowercase().split(Regex("[^a-z0-9]+")).filter { it.length > 1 }.toSet()
        return ref.items.asSequence()
            .filter { part.type == PartType.UNKNOWN || it.type == part.type }
            .mapNotNull { item ->
                var score = 0.0
                var used = 0
                for (r in rs) {
                    val st = item.stats[r.key] ?: continue
                    // A fixed (no-spread) stat only fits if it matches almost exactly.
                    val zz = if (st.mod <= 0.0 || st.avg == 0.0) {
                        if (st.avg != 0.0 && abs(r.value / st.avg - 1.0) < 0.002) 0.0 else 10.0
                    } else z(st, r.value)
                    score += minOf(zz * zz, 100.0)
                    used++
                }
                if (used == 0) return@mapNotNull null
                score /= used
                if (part.reLevel != null && part.reLevel != item.re) score += 3.0
                val idWords = item.id.lowercase().split('_').toSet()
                score -= 0.6 * words.count { it in idWords }
                Match(item, score)
            }
            .sortedBy { it.score }
            .take(limit)
            .toList()
    }

    /**
     * [goodness] is the deviation on this part's own item; [classGoodness] is the best-in-class
     * score (see [classGoodness]). [rating] is what the colours use.
     */
    data class Row(
        val key: String, val label: String, val value: Double, val avg: Double, val z: Double, val goodness: Double,
        val classGoodness: Double? = null,
    ) {
        val rating: Double get() = classGoodness ?: goodness
    }

    fun evaluate(part: Part, item: RefItem, ref: RefData? = null): List<Row> = readings(part).mapNotNull { r ->
        val st = item.stats[r.key] ?: return@mapNotNull null
        val zz = z(st, r.value)
        Row(r.key, r.label, r.value, st.avg, zz, goodness(r.key, zz), ref?.let { classGoodness(it, item, r.key, r.value) })
    }

    // ---- Best in class ----

    /** Chance one roll of [stat] comes out better than [value]. */
    fun chanceBetter(stat: RefStat, key: String, value: Double): Double {
        val lower = defsByKey[key]?.lowerIsBetter == true
        if (stat.mod <= 0.0 || stat.avg == 0.0) {
            return if ((lower && stat.avg < value) || (!lower && stat.avg > value)) 1.0 else 0.0
        }
        if (stat.uniform) {
            val below = ((value - (stat.avg - stat.mod)) / (2 * stat.mod)).coerceIn(0.0, 1.0)
            return if (lower) below else 1 - below
        }
        val zt = z(stat, value)
        return if (lower) phi(zt) else phi(-zt)
    }

    /** Every item of the same type and RE level - the "class" a part is rated against. */
    fun classItems(ref: RefData, item: RefItem): List<RefItem> =
        ref.items.filter { it.type == item.type && it.re == item.re }

    /**
     * Best-in-class score: how [value] ranks against every possible roll of every item of the
     * same type and RE level (each item counted equally), turned back into an equivalent
     * deviation so the usual colour bands apply. 4 = better than ~99.997% of the class.
     */
    fun classGoodness(ref: RefData, item: RefItem, key: String, value: Double): Double? {
        val pool = classItems(ref, item).mapNotNull { it.stats[key] }
        if (pool.isEmpty() || pool.all { it.mod <= 0.0 }) return null
        val p = pool.sumOf { chanceBetter(it, key, value) } / pool.size
        return -invPhi(p.coerceIn(1e-300, 1 - 1e-16))
    }

    /** Inverse standard normal CDF (Acklam's approximation, ~1e-9 relative error). */
    fun invPhi(p: Double): Double {
        val a = doubleArrayOf(-3.969683028665376e+01, 2.209460984245205e+02, -2.759285104469687e+02, 1.383577518672690e+02, -3.066479806614716e+01, 2.506628277459239e+00)
        val b = doubleArrayOf(-5.447609879822406e+01, 1.615858368580409e+02, -1.556989798598866e+02, 6.680131188771972e+01, -1.328068155288572e+01)
        val c = doubleArrayOf(-7.784894002430293e-03, -3.223964580411365e-01, -2.400758277161838e+00, -2.549732539343734e+00, 4.374664141464968e+00, 2.938163982698783e+00)
        val d = doubleArrayOf(7.784695709041462e-03, 3.224671290700398e-01, 2.445134137142996e+00, 3.754408661907416e+00)
        val low = 0.02425
        return when {
            p < low -> {
                val q = sqrt(-2 * ln(p))
                (((((c[0] * q + c[1]) * q + c[2]) * q + c[3]) * q + c[4]) * q + c[5]) / ((((d[0] * q + d[1]) * q + d[2]) * q + d[3]) * q + 1)
            }
            p <= 1 - low -> {
                val q = p - 0.5
                val r = q * q
                (((((a[0] * r + a[1]) * r + a[2]) * r + a[3]) * r + a[4]) * r + a[5]) * q / (((((b[0] * r + b[1]) * r + b[2]) * r + b[3]) * r + b[4]) * r + 1)
            }
            else -> {
                val q = sqrt(-2 * ln(1 - p))
                -(((((c[0] * q + c[1]) * q + c[2]) * q + c[3]) * q + c[4]) * q + c[5]) / ((((d[0] * q + d[1]) * q + d[2]) * q + d[3]) * q + 1)
            }
        }
    }

    // ---- Colour bands ----

    data class Band(val label: String, val color: Long)

    fun band(g: Double): Band = when {
        g >= 4.0 -> Band("Unicorn", 0xFFFF6FD8)
        g >= 3.5 -> Band("Elite", 0xFFFF9A3C)
        g >= 3.0 -> Band("Great", 0xFFB57BFF)
        g >= 2.5 -> Band("Good", 0xFF5CD65C)
        g >= 2.0 -> Band("OK", 0xFF4FA3FF)
        else -> Band("Chassis dealer", 0xFFE0524A)
    }

    // ---- Probability ----

    /** Complementary error function (Numerical Recipes erfcc, ~1e-7 relative accuracy, good in the tails). */
    private fun erfc(x: Double): Double {
        val z = abs(x)
        val t = 1.0 / (1.0 + 0.5 * z)
        val r = t * exp(-z * z - 1.26551223 + t * (1.00002368 + t * (0.37409196 + t * (0.09678418 +
            t * (-0.18628806 + t * (0.27886807 + t * (-1.13520398 + t * (1.48851587 +
            t * (-0.82215223 + t * 0.17087277)))))))))
        return if (x >= 0) r else 2.0 - r
    }

    /** Standard normal CDF. */
    fun phi(z: Double): Double = 0.5 * erfc(-z / sqrt(2.0))

    /** Chance this stat rolls below (or above) [threshold] (original, unclamped rolls). */
    fun chance(stat: RefStat, below: Boolean, threshold: Double): Double {
        if (stat.mod <= 0.0) return if ((stat.avg < threshold) == below) 1.0 else 0.0
        if (stat.uniform) {
            val lo = stat.avg - stat.mod
            val p = ((threshold - lo) / (2 * stat.mod)).coerceIn(0.0, 1.0)
            return if (below) p else 1 - p
        }
        val zt = (threshold / stat.avg - 1.0) / (stat.mod / 2.0)
        return if (below) phi(zt) else phi(-zt)
    }

    /** Purchases needed to have [confidence] chance of at least one success. */
    fun buysFor(p: Double, confidence: Double): Double =
        if (p <= 0.0) Double.POSITIVE_INFINITY else if (p >= 1.0) 1.0 else ln(1 - confidence) / ln1p(-p) // ln1p keeps tiny chances from rounding to "never"
}
