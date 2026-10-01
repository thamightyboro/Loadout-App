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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import com.thamightyboro.loadouts.data.Loadout
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
    )
    val totals = current.totals(byId)

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

            SectionTitle("Slots")
            current.activeSlots().forEach { slot ->
                SlotRow(slot, slots[slot]?.let(byId::get)) { picking = slot }
            }

            OutlinedTextField(notes, { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
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
