package com.thamightyboro.loadouts.data

/**
 * Droid commands that change power use, from datatables/space_combat/droid_commands.tab.
 *
 * Engine maths (ShipObject_Components.cpp):
 *  - each component draws  maintenance / energyEfficiency  every second
 *  - the reactor puts out  generation x generalEfficiency
 *  - weapon shots cost the capacitor  energyPerShot / energyEfficiency
 * Only one command per group is active at a time (each new one replaces the last).
 */
data class DroidCommand(val key: String, val label: String, val energy: Double, val general: Double)

enum class CommandGroup(val label: String) {
    WEAPONS("Weapons"),
    ENGINE("Engine"),
    REACTOR("Reactor"),
    CAPACITOR("Capacitor");

    companion object {
        fun fromName(name: String): CommandGroup? = entries.firstOrNull { it.name == name }
    }
}

object DroidCommands {
    val byGroup: Map<CommandGroup, List<DroidCommand>> = mapOf(
        CommandGroup.WEAPONS to listOf(
            DroidCommand("weapons_overload_one", "Weapon Overload 1", 0.7, 1.25),
            DroidCommand("weapons_overload_two", "Weapon Overload 2", 0.5, 1.5),
            DroidCommand("weapons_overload_three", "Weapon Overload 3", 0.35, 2.0),
            DroidCommand("weapons_overload_four", "Weapon Overload 4", 0.2, 3.0),
            DroidCommand("weapons_efficiency_one", "Weapon Efficiency 1", 1.25, 0.75),
            DroidCommand("weapons_efficiency_two", "Weapon Efficiency 2", 1.5, 0.5),
            DroidCommand("weapons_efficiency_three", "Weapon Efficiency 3", 2.0, 0.25),
            DroidCommand("weapons_efficiency_four", "Weapon Efficiency 4", 3.0, 0.1),
        ),
        CommandGroup.ENGINE to listOf(
            DroidCommand("engine_overload_one", "Engine Overload 1", 0.8, 1.1),
            DroidCommand("engine_overload_two", "Engine Overload 2", 0.6, 1.2),
            DroidCommand("engine_overload_three", "Engine Overload 3", 0.3, 1.3),
            DroidCommand("engine_overload_four", "Engine Overload 4", 0.1, 1.4),
            DroidCommand("engine_efficiency_one", "Engine Efficiency 1", 1.25, 0.75),
            DroidCommand("engine_efficiency_two", "Engine Efficiency 2", 1.5, 0.5),
            DroidCommand("engine_efficiency_three", "Engine Efficiency 3", 2.0, 0.25),
            DroidCommand("engine_efficiency_four", "Engine Efficiency 4", 3.0, 0.1),
        ),
        CommandGroup.REACTOR to listOf(
            DroidCommand("reactor_overload_one", "Reactor Overload 1", 1.0, 1.1),
            DroidCommand("reactor_overload_two", "Reactor Overload 2", 1.0, 1.3),
            DroidCommand("reactor_overload_three", "Reactor Overload 3", 1.0, 1.6),
            DroidCommand("reactor_overload_four", "Reactor Overload 4", 1.0, 1.9),
        ),
        CommandGroup.CAPACITOR to listOf(
            DroidCommand("weapcap_powerup_one", "Capacitor Power Up 1", 1.25, 1.25),
            DroidCommand("weapcap_powerup_two", "Capacitor Power Up 2", 1.5, 1.5),
            DroidCommand("weapcap_powerup_three", "Capacitor Power Up 3", 1.75, 1.75),
            DroidCommand("weapcap_powerup_four", "Capacitor Power Up 4", 2.0, 2.0),
        ),
    )
    private val byKey = byGroup.values.flatten().associateBy { it.key }
    fun find(key: String?): DroidCommand? = key?.let(byKey::get)

    fun groupFor(slot: Slot): CommandGroup? = when {
        slot.accepts == PartType.WEAPON -> CommandGroup.WEAPONS
        slot == Slot.ENGINE -> CommandGroup.ENGINE
        slot == Slot.CAPACITOR -> CommandGroup.CAPACITOR
        else -> null
    }
}

data class PowerSlot(val slot: Slot, val baseDrain: Double, val drain: Double)

data class PowerReport(
    val baseGeneration: Double?,
    val generation: Double?,
    val baseDrain: Double,
    val drain: Double,
    val slots: List<PowerSlot>,
    /** Capacitor energy per weapon shot is multiplied by this (1 = unchanged). */
    val shotCostFactor: Double,
) {
    val surplus: Double? get() = generation?.let { it - drain }
}

/** Projected reactor output and drain with the loadout's droid commands running. */
fun Loadout.power(parts: Map<String, Part>): PowerReport {
    val cmd = { g: CommandGroup -> DroidCommands.find(droidCommands[g]) }
    val rows = activeSlots().filter { it != Slot.REACTOR }.mapNotNull { slot ->
        val d = slots[slot]?.let(parts::get)?.drain ?: return@mapNotNull null
        val eff = DroidCommands.groupFor(slot)?.let(cmd)?.energy ?: 1.0
        PowerSlot(slot, d, d / eff.coerceIn(0.1, 10.0))
    }
    val baseGen = slots[Slot.REACTOR]?.let(parts::get)?.generation
    val gen = baseGen?.let { it * (cmd(CommandGroup.REACTOR)?.general ?: 1.0) }
    val weaponEff = cmd(CommandGroup.WEAPONS)?.energy ?: 1.0
    return PowerReport(
        baseGeneration = baseGen,
        generation = gen,
        baseDrain = rows.sumOf { it.baseDrain },
        drain = rows.sumOf { it.drain },
        slots = rows,
        shotCostFactor = 1.0 / weaponEff.coerceAtLeast(0.1),
    )
}
