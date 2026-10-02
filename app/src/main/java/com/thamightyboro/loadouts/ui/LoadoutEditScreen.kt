@file:OptIn(ExperimentalMaterial3Api::class)

package com.thamightyboro.loadouts.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.thamightyboro.loadouts.AppViewModel
import com.thamightyboro.loadouts.data.CommandGroup
import com.thamightyboro.loadouts.data.DroidCommand
import com.thamightyboro.loadouts.data.DroidCommands
import com.thamightyboro.loadouts.data.Loadout
import com.thamightyboro.loadouts.data.PowerReport
import com.thamightyboro.loadouts.data.power
import com.thamightyboro.loadouts.data.Part
import com.thamightyboro.loadouts.data.PartType
import com.thamightyboro.loadouts.data.Slot
import com.thamightyboro.loadouts.data.fmt
import com.thamightyboro.loadouts.data.totals

@Composable
fun LoadoutEditScreen(vm: AppViewModel, existing: Loadout?, parts: List<Part>, onBack: () -> Unit) {
    val start = remember { existing ?: Loadout() }
    var name by remember { mutableStateOf(start.name) }
    var chassis by remember { mutableStateOf(start.chassis) }
    var massLimit by remember { mutableStateOf(start.massLimit?.let { fmtPlain(it) } ?: "") }
    var weaponSlots by remember { mutableStateOf(start.weaponSlots) }
    var notes by remember { mutableStateOf(start.notes) }
    var slots by remember { mutableStateOf(start.slots) }
    var commands by remember { mutableStateOf(start.droidCommands) }
    var picking by remember { mutableStateOf<Slot?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    val byId = parts.associateBy { it.id }
    val current = start.copy(
        name = name.trim(),
        chassis = chassis.trim(),
        massLimit = massLimit.replace(",", "").toDoubleOrNull(),
        weaponSlots = weaponSlots,
        slots = slots,
        notes = notes.trim(),
        droidCommands = commands,
    )
    val totals = current.totals(byId)
    val power = current.power(byId)

    fun save() {
        vm.saveLoadout(current.copy(slots = slots.filterKeys { it in current.activeSlots() }))
        onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "New loadout" else "Edit loadout") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(
                        onClick = { vm.exportImage(current.copy(slots = slots.filterKeys { it in current.activeSlots() }), byId) },
                        enabled = !vm.exporting,
                    ) { Icon(Icons.Filled.Image, "Export as image") }
                    if (existing != null) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Delete") }
                    TextButton(onClick = { save() }) { Text("Save") }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Totals at the top so they're visible while slotting parts
            Card(colors = CardDefaults.cardColors(containerColor = SwgColors.PanelHigh)) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val limit = current.massLimit
                    val over = limit != null && totals.mass > limit
                    TotalLine("Mass", fmt(totals.mass) + (limit?.let { " / ${fmt(it)}" } ?: ""), if (over) SwgColors.Bad else SwgColors.Text)
                    if (limit != null && limit > 0) {
                        LinearProgressIndicator(
                            progress = { (totals.mass / limit).toFloat().coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                            color = if (over) SwgColors.Bad else SwgColors.Teal,
                        )
                        if (!over) TotalLine("Mass left", fmt(limit - totals.mass), SwgColors.Muted)
                    }
                    val gen = totals.generation
                    val drainOver = gen != null && totals.drain > gen
                    TotalLine(
                        "Reactor drain",
                        fmt(totals.drain) + (gen?.let { " / ${fmt(it)} gen" } ?: ""),
                        if (drainOver) SwgColors.Bad else SwgColors.Text,
                    )
                    if (commands.isNotEmpty()) {
                        val pg = power.generation
                        val pOver = pg != null && power.drain > pg
                        TotalLine(
                            "With droid commands",
                            fmt(power.drain) + (pg?.let { " / ${fmt(it)} gen" } ?: ""),
                            if (pOver) SwgColors.Bad else SwgColors.Teal,
                        )
                    }
                    TotalLine("Slots filled", "${totals.filled} / ${totals.total}", SwgColors.Muted)
                }
            }

            OutlinedTextField(name, { name = it }, label = { Text("Loadout name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(chassis, { chassis = it }, label = { Text("Chassis (e.g. TIE Aggressor)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    massLimit, { massLimit = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Chassis mass limit") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Weapons", style = MaterialTheme.typography.labelSmall, color = SwgColors.Muted)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { if (weaponSlots > 0) weaponSlots-- }) { Icon(Icons.Filled.Remove, "Fewer") }
                        Text("$weaponSlots", style = MaterialTheme.typography.titleMedium)
                        IconButton(onClick = { if (weaponSlots < Slot.weapons.size) weaponSlots++ }) { Icon(Icons.Filled.Add, "More") }
                    }
                }
            }

            PowerSection(power, commands) { g, key -> commands = if (key == null) commands - g else commands + (g to key) }

            SectionTitle("Slots")
            current.activeSlots().forEach { slot ->
                SlotRow(slot, slots[slot]?.let(byId::get)) { picking = slot }
            }

            OutlinedTextField(notes, { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            OutlinedButton(
                onClick = { vm.exportImage(current.copy(slots = slots.filterKeys { it in current.activeSlots() }), byId) },
                enabled = !vm.exporting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Image, null)
                Text(if (vm.exporting) "  Creating image…" else "  Export as image (1920×1080)")
            }
            Button(onClick = { save() }, modifier = Modifier.fillMaxWidth()) { Text("Save loadout") }
        }
    }

    picking?.let { slot ->
        // Parts already used in another slot of this loadout are hidden (you only own one of each).
        val usedElsewhere = slots.filterKeys { it != slot }.values.toSet()
        val options = parts.filter { it.type == slot.accepts && it.id !in usedElsewhere }
            .sortedBy { it.name.lowercase() }
        AlertDialog(
            onDismissRequest = { picking = null },
            title = { Text("Choose ${slot.label.lowercase()}") },
            text = {
                if (options.isEmpty()) {
                    Text("No ${slot.accepts.label.lowercase()} parts in your library yet.", color = SwgColors.Muted)
                } else {
                    LazyColumn(Modifier.heightIn(max = 420.dp)) {
                        items(options, key = { it.id }) { p ->
                            Column(
                                Modifier.fillMaxWidth().clickable {
                                    slots = slots + (slot to p.id); picking = null
                                }.padding(vertical = 10.dp)
                            ) {
                                Text(p.name.ifBlank { "(unnamed)" }, color = SwgColors.Gold)
                                Text(p.headline(), style = MaterialTheme.typography.bodySmall, color = SwgColors.Muted)
                            }
                            HorizontalDivider()
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { picking = null }) { Text("Close") } },
            dismissButton = {
                if (slots.containsKey(slot)) {
                    TextButton(onClick = { slots = slots - slot; picking = null }) { Text("Empty slot") }
                }
            },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this loadout?") },
            text = { Text("Your parts stay in the library.") },
            confirmButton = { TextButton(onClick = { vm.deleteLoadout(start.id); confirmDelete = false; onBack() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun TotalLine(label: String, value: String, color: Color) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, color = SwgColors.Gold, modifier = Modifier.weight(1f))
        Text(value, color = color, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SlotRow(slot: Slot, part: Part?, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = SwgColors.Panel),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(slot.label, color = SwgColors.Teal, style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(110.dp))
            Column(Modifier.weight(1f)) {
                if (part == null) {
                    Text("Empty - tap to choose", color = SwgColors.Muted)
                } else {
                    Text(part.name.ifBlank { "(unnamed)" }, color = SwgColors.Gold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val h = part.headline()
                    if (h.isNotBlank()) Text(h, style = MaterialTheme.typography.bodySmall, color = SwgColors.Muted)
                }
            }
        }
    }
}

private fun fmtPlain(v: Double): String = if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()

/** Projected power: reactor output vs every part's drain, with the chosen droid commands applied. */
@Composable
private fun PowerSection(
    power: PowerReport,
    commands: Map<CommandGroup, String>,
    onPick: (CommandGroup, String?) -> Unit,
) {
    SectionTitle("Power & droid commands")
    Card(colors = CardDefaults.cardColors(containerColor = SwgColors.Panel)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CommandGroup.entries.forEach { g ->
                CommandPicker(g, commands[g]) { onPick(g, it) }
            }
            HorizontalDivider(color = SwgColors.PanelHigh)
            val baseGen = power.baseGeneration
            val gen = power.generation
            if (gen == null) {
                Text("Add a reactor to see generation.", color = SwgColors.Muted, style = MaterialTheme.typography.bodySmall)
            } else {
                TotalLine("Reactor output", if (baseGen != null && baseGen != gen) "${fmt(baseGen)} \u2192 ${fmt(gen)}" else fmt(gen), SwgColors.Text)
            }
            TotalLine(
                "Total drain",
                if (power.baseDrain != power.drain) "${fmt(power.baseDrain)} \u2192 ${fmt(power.drain)}" else fmt(power.drain),
                SwgColors.Text,
            )
            power.surplus?.let { s ->
                TotalLine(
                    if (s >= 0) "Spare power" else "Short by",
                    fmt(kotlin.math.abs(s)),
                    if (s >= 0) SwgColors.Good else SwgColors.Bad,
                )
                if (gen != null && gen > 0) {
                    LinearProgressIndicator(
                        progress = { (power.drain / gen).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        color = if (s >= 0) SwgColors.Teal else SwgColors.Bad,
                    )
                }
                if (s < 0) Text(
                    "Not enough power: parts lowest in the chassis power priority run under-powered and lose performance.",
                    color = SwgColors.Bad, style = MaterialTheme.typography.bodySmall,
                )
            }
            val changed = power.slots.filter { it.drain != it.baseDrain }
            if (changed.isNotEmpty()) {
                changed.forEach { r ->
                    Row(Modifier.fillMaxWidth()) {
                        Text(r.slot.label, color = SwgColors.Muted, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        Text("${fmt(r.baseDrain)} \u2192 ${fmt(r.drain)}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (power.shotCostFactor != 1.0) {
                Text(
                    "Weapon shots cost \u00d7${"%.2f".format(power.shotCostFactor)} capacitor energy.",
                    color = SwgColors.Muted, style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun CommandPicker(group: CommandGroup, key: String?, onPick: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val current = DroidCommands.find(key)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(group.label, color = SwgColors.Teal, style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(90.dp))
        Box(Modifier.weight(1f)) {
            OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
                Text(current?.label ?: "None", maxLines = 1, modifier = Modifier.weight(1f))
                Icon(Icons.Filled.ArrowDropDown, null)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                DropdownMenuItem(text = { Text("None") }, onClick = { onPick(null); open = false })
                DroidCommands.byGroup[group].orEmpty().forEach { c ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(c.label)
                                Text(commandEffect(group, c), color = SwgColors.Muted, style = MaterialTheme.typography.bodySmall)
                            }
                        },
                        onClick = { onPick(c.key); open = false },
                    )
                }
            }
        }
    }
}

private fun commandEffect(group: CommandGroup, c: DroidCommand): String {
    val perf = "%.2f".format(c.general).trimEnd('0').trimEnd('.')
    val drain = "%.2f".format(1.0 / c.energy).trimEnd('0').trimEnd('.')
    return when (group) {
        CommandGroup.REACTOR -> "Output \u00d7$perf"
        else -> "Performance \u00d7$perf \u00b7 drain \u00d7$drain"
    }
}
