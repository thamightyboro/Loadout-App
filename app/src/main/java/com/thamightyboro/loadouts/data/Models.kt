package com.thamightyboro.loadouts.data

import java.util.UUID

enum class PartType(val label: String) {
    REACTOR("Reactor"),
    ENGINE("Engine"),
    SHIELD("Shield"),
    ARMOR("Armor"),
    CAPACITOR("Capacitor"),
    DROID_INTERFACE("Droid Interface"),
    BOOSTER("Booster"),
    WEAPON("Weapon"),
    UNKNOWN("Other");

    companion object {
        fun fromName(name: String?): PartType =
            entries.firstOrNull { it.name == name } ?: UNKNOWN
    }
}

/** One "Label: value" row from the examine window, kept as text so nothing is lost. */
data class StatLine(val label: String, val value: String)

data class Part(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val type: PartType = PartType.UNKNOWN,
    val reLevel: Int? = null,
    val stats: List<StatLine> = emptyList(),
    val qualities: List<StatLine> = emptyList(),
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    /** Component template this part was matched to (for deviation ratings); null = auto-match. */
    val refId: String? = null,
) {
    val mass: Double? get() = stats.numberFor("mass")
    val drain: Double? get() = stats.numberFor("energy drain")
    val generation: Double? get() = stats.numberFor("generation")

    /** Short one-line summary of the most useful stats for list rows. */
    fun headline(): String = buildList {
        mass?.let { add("Mass ${fmt(it)}") }
        drain?.let { add("Drain ${fmt(it)}") }
        generation?.let { add("Gen ${fmt(it)}") }
        reLevel?.let { add("RE $it") }
    }.joinToString(" · ")
}

enum class Slot(val label: String, val accepts: PartType) {
    REACTOR("Reactor", PartType.REACTOR),
    ENGINE("Engine", PartType.ENGINE),
    SHIELD("Shield", PartType.SHIELD),
    ARMOR_FRONT("Front Armor", PartType.ARMOR),
    ARMOR_REAR("Rear Armor", PartType.ARMOR),
    CAPACITOR("Capacitor", PartType.CAPACITOR),
    DROID_INTERFACE("Droid Interface", PartType.DROID_INTERFACE),
    BOOSTER("Booster", PartType.BOOSTER),
    WEAPON_1("Weapon 1", PartType.WEAPON),
    WEAPON_2("Weapon 2", PartType.WEAPON),
    WEAPON_3("Weapon 3", PartType.WEAPON),
    WEAPON_4("Weapon 4", PartType.WEAPON),
    WEAPON_5("Weapon 5", PartType.WEAPON),
    WEAPON_6("Weapon 6", PartType.WEAPON),
    WEAPON_7("Weapon 7", PartType.WEAPON),
    WEAPON_8("Weapon 8", PartType.WEAPON);

    companion object {
        val weapons = entries.filter { it.accepts == PartType.WEAPON }
        fun fromName(name: String): Slot? = entries.firstOrNull { it.name == name }
    }
}

data class Loadout(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val chassis: String = "",
    val massLimit: Double? = null,
    val weaponSlots: Int = 2,
    val slots: Map<Slot, String> = emptyMap(), // slot -> part id
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
) {
    fun activeSlots(): List<Slot> =
        Slot.entries.filter { it.accepts != PartType.WEAPON } +
            Slot.weapons.take(weaponSlots.coerceIn(0, Slot.weapons.size))
}

data class LoadoutTotals(
    val mass: Double,
    val drain: Double,
    val generation: Double?,
    val filled: Int,
    val total: Int,
)

fun Loadout.totals(parts: Map<String, Part>): LoadoutTotals {
    val active = activeSlots()
    val equipped = active.mapNotNull { slot -> slots[slot]?.let(parts::get) }
    val reactor = slots[Slot.REACTOR]?.let(parts::get)
    return LoadoutTotals(
        mass = equipped.sumOf { it.mass ?: 0.0 },
        // the reactor generates rather than drains, so leave it out of drain
        drain = equipped.filter { it.type != PartType.REACTOR }.sumOf { it.drain ?: 0.0 },
        generation = reactor?.generation,
        filled = equipped.size,
        total = active.size,
    )
}

/** Finds the first stat whose label contains [key] and pulls the leading number out of it. */
fun List<StatLine>.numberFor(key: String): Double? =
    firstOrNull { it.label.contains(key, ignoreCase = true) }?.value?.let(::firstNumber)

private val numberRegex = Regex("""-?\d[\d,]*\.?\d*""")

fun firstNumber(text: String): Double? =
    numberRegex.find(text)?.value?.replace(",", "")?.toDoubleOrNull()

fun fmt(v: Double): String =
    if (v >= 1000) "%,.1f".format(v) else if (v % 1.0 == 0.0) "%.0f".format(v) else "%.1f".format(v)
