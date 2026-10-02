@file:OptIn(ExperimentalMaterial3Api::class)

package com.thamightyboro.loadouts.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.thamightyboro.loadouts.AppViewModel
import com.thamightyboro.loadouts.data.Part
import com.thamightyboro.loadouts.data.PartType
import com.thamightyboro.loadouts.data.StatLine
import com.thamightyboro.loadouts.data.Deviation
import com.thamightyboro.loadouts.data.RefData
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import com.thamightyboro.loadouts.ocr.ExamineParser

/** Default stat rows offered when adding a part by hand, so you don't have to type labels. */
private fun templateFor(type: PartType): List<String> = when (type) {
    PartType.WEAPON -> listOf("Armor", "Hitpoints", "Reactor Energy Drain", "Mass", "Damage", "Vs. Shields", "Vs. Armor", "Energy/Shot", "Refire Rate")
    PartType.REACTOR -> listOf("Armor", "Hitpoints", "Mass", "Energy Generation Rate")
    PartType.ENGINE -> listOf("Armor", "Hitpoints", "Reactor Energy Drain", "Mass", "Pitch Rate Maximum", "Yaw Rate Maximum", "Roll Rate Maximum", "Speed Maximum")
    PartType.SHIELD -> listOf("Armor", "Hitpoints", "Reactor Energy Drain", "Mass", "Front Shield Hitpoints", "Back Shield Hitpoints", "Shield Recharge Rate")
    PartType.ARMOR -> listOf("Armor", "Hitpoints", "Mass")
    PartType.CAPACITOR -> listOf("Armor", "Hitpoints", "Reactor Energy Drain", "Mass", "Capacitor Energy", "Recharge Rate")
    PartType.DROID_INTERFACE -> listOf("Armor", "Hitpoints", "Reactor Energy Drain", "Mass", "Droid Command Speed")
    PartType.BOOSTER -> listOf("Armor", "Hitpoints", "Reactor Energy Drain", "Mass", "Booster Energy", "Booster Recharge Rate", "Booster Energy Consumption Rate", "Booster Acceleration", "Booster Speed Maximum")
    PartType.UNKNOWN -> listOf("Armor", "Hitpoints", "Mass")
}

@Composable
fun PartEditScreen(vm: AppViewModel, id: String, existing: Part?, onBack: () -> Unit) {
    val start = remember(id) {
        existing ?: (if (id == "new") vm.takeDraft() else null) ?: Part()
    }
    val rawText = remember(id) { if (id == "new") vm.draftRawText.also { vm.clearRaw() } else null }

    var name by remember(id) { mutableStateOf(start.name) }
    var type by remember(id) { mutableStateOf(start.type) }
    var reLevel by remember(id) { mutableStateOf(start.reLevel?.toString() ?: "") }
    var notes by remember(id) { mutableStateOf(start.notes) }
    val stats = remember(id) { mutableStateListOf<StatLine>().apply { addAll(start.stats) } }
    val qualities = remember(id) { mutableStateListOf<StatLine>().apply { addAll(start.qualities) } }
    var refId by remember(id) { mutableStateOf(start.refId) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showRaw by remember { mutableStateOf(false) }

    fun save() {
        vm.savePart(
            start.copy(
                name = name.trim(),
                type = type,
                reLevel = reLevel.trim().toIntOrNull(),
                notes = notes.trim(),
                stats = stats.filter { it.label.isNotBlank() || it.value.isNotBlank() },
                qualities = qualities.filter { it.label.isNotBlank() || it.value.isNotBlank() },
                refId = refId,
            )
        )
        onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "New part" else "Edit part") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    if (existing != null) {
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Delete") }
                    }
                    TextButton(onClick = { save() }) { Text("Save") }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (id == "new") {
                Text(
                    "Check what was read before saving - fix anything the scan got wrong.",
                    color = SwgColors.Teal,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("Name") }, modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                TypePicker(type) { newType ->
                    type = newType
                    if (stats.isEmpty()) stats.addAll(templateFor(newType).map { StatLine(it, "") })
                }
                OutlinedTextField(
                    value = reLevel, onValueChange = { reLevel = it.filter(Char::isDigit) },
                    label = { Text("RE level") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(110.dp),
                )
            }
            if (type == PartType.UNKNOWN && stats.isNotEmpty()) {
                TextButton(onClick = { type = ExamineParser.guessType(stats) }) { Text("Guess type from stats") }
            }

            SectionTitle("Ship Component")
            StatEditor(stats)

            SectionTitle("Space Evaluation")
            StatEditor(qualities)

            vm.ref?.let { ref ->
                DeviationSection(
                    ref = ref,
                    part = Part(name = name, type = type, reLevel = reLevel.trim().toIntOrNull(), stats = stats.toList()),
                    refId = refId,
                    onPick = { refId = it },
                )
            }

            OutlinedTextField(
                value = notes, onValueChange = { notes = it },
                label = { Text("Notes") }, modifier = Modifier.fillMaxWidth(), minLines = 2,
            )

            if (rawText != null) {
                TextButton(onClick = { showRaw = !showRaw }) {
                    Text(if (showRaw) "Hide raw scan text" else "Show raw scan text")
                }
                if (showRaw) {
                    Text(rawText, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, color = SwgColors.Muted)
                }
            }
            Button(onClick = { save() }, modifier = Modifier.fillMaxWidth()) { Text("Save part") }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this part?") },
            text = { Text("It will also be removed from any loadouts using it.") },
            confirmButton = {
                TextButton(onClick = { vm.deletePart(start.id); confirmDelete = false; onBack() }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

/** Matches the part to its component template and rates each stat by how far it rolled from average. */
@Composable
fun DeviationSection(ref: RefData, part: Part, refId: String?, onPick: (String?) -> Unit) {
    val ranked = remember(part.stats, part.type, part.name, part.reLevel) { Deviation.rank(part, ref) }
    val item = refId?.let(ref.byId::get) ?: ranked.firstOrNull()?.item
    var picking by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf(false) }

    SectionTitle("Deviation rating")
    if (item == null) {
        Text("Add stats (mass, drain, armor…) and the app will match the part and rate each roll.", color = SwgColors.Muted, style = MaterialTheme.typography.bodySmall)
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).clickable { detail = true }) {
            Text(item.name, color = SwgColors.Gold)
            Text(
                "${item.type.label} · RE ${item.re} · " + if (refId == null) "best match" else "chosen by you",
                color = SwgColors.Muted, style = MaterialTheme.typography.bodySmall,
            )
        }
        TextButton(onClick = { picking = true }) { Text("Change") }
    }
    val rows = Deviation.evaluate(part, item)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        rows.forEach { r ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(r.label, style = MaterialTheme.typography.bodyMedium)
                    Text("${fmtStat(r.value)}  (avg ${fmtStat(r.avg)})", color = SwgColors.Muted, style = MaterialTheme.typography.bodySmall)
                }
                DeviationChip(r.goodness, r.z)
            }
        }
    }
    Text(
        "0 = average roll; past ±3 is rare. Positive is always better for you (lower mass/drain counts as positive). " +
            "\"Check\" means the value is far outside this template's range - try Change.",
        color = SwgColors.Muted, style = MaterialTheme.typography.bodySmall,
    )

    if (picking) {
        AlertDialog(
            onDismissRequest = { picking = false },
            title = { Text("Which component is this?") },
            text = {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    item {
                        Text(
                            "Auto (best match)",
                            color = SwgColors.Teal,
                            modifier = Modifier.fillMaxWidth().clickable { onPick(null); picking = false }.padding(vertical = 10.dp),
                        )
                        HorizontalDivider()
                    }
                    items(ranked, key = { it.item.id }) { m ->
                        Column(Modifier.fillMaxWidth().clickable { onPick(m.item.id); picking = false }.padding(vertical = 8.dp)) {
                            Text(m.item.name, color = SwgColors.Gold)
                            Text("RE ${m.item.re} · fit %.2f (lower is closer)".format(m.score), color = SwgColors.Muted, style = MaterialTheme.typography.bodySmall)
                        }
                        HorizontalDivider()
                    }
                }
            },
            confirmButton = { TextButton(onClick = { picking = false }) { Text("Close") } },
        )
    }
    if (detail) ItemDetailDialog(item, ref) { detail = false }
}

@Composable
private fun TypePicker(type: PartType, onPick: (PartType) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }) {
            Text(type.label)
            Icon(Icons.Filled.ArrowDropDown, null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            PartType.entries.forEach { t ->
                DropdownMenuItem(text = { Text(t.label) }, onClick = { onPick(t); open = false })
            }
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(text, color = SwgColors.Gold, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun StatEditor(list: SnapshotStateList<StatLine>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        list.forEachIndexed { i, line ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = line.label, onValueChange = { list[i] = line.copy(label = it) },
                    singleLine = true, modifier = Modifier.weight(1.3f),
                    textStyle = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = line.value, onValueChange = { list[i] = line.copy(value = it) },
                    singleLine = true, modifier = Modifier.weight(1f),
                    textStyle = MaterialTheme.typography.bodyMedium,
                )
                IconButton(onClick = { list.removeAt(i) }) { Icon(Icons.Filled.Close, "Remove") }
            }
        }
        TextButton(onClick = { list.add(StatLine("", "")) }) {
            Icon(Icons.Filled.Add, null)
            Text("Add line")
        }
    }
}
