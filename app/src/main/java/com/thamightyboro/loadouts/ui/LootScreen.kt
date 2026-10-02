@file:OptIn(ExperimentalMaterial3Api::class)

package com.thamightyboro.loadouts.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.thamightyboro.loadouts.AppViewModel
import com.thamightyboro.loadouts.data.Deviation
import com.thamightyboro.loadouts.data.PartType
import com.thamightyboro.loadouts.data.RefData
import com.thamightyboro.loadouts.data.RefItem
import kotlin.math.abs
import kotlin.math.ceil

/** Number formatting that keeps small stats (0.446, 13.9) readable. */
fun fmtStat(v: Double): String = when {
    abs(v) >= 1000 -> "%,.1f".format(v)
    abs(v) >= 10 -> "%.1f".format(v)
    abs(v) >= 1 -> "%.2f".format(v)
    else -> "%.3f".format(v)
}

fun fmtChance(p: Double): String = when {
    p <= 0.0 -> "0%"
    p >= 0.9999 -> "100%"
    p >= 0.01 -> "%.2f%%".format(p * 100)
    else -> "%.3g%%".format(p * 100)
}

fun fmtTokens(t: Double): String = if (t.isInfinite() || t.isNaN()) "never" else "%,.0f".format(t)

private class Condition(key: String, below: Boolean, text: String) {
    var key by mutableStateOf(key)
    var below by mutableStateOf(below)
    var text by mutableStateOf(text)
}

@Composable
fun LootScreen(vm: AppViewModel) {
    val ref = vm.ref
    var tab by rememberSaveable { mutableStateOf(0) }
    Scaffold(topBar = { TopAppBar(title = { Text("Loot") }) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (ref == null) {
                EmptyHint("Component reference data is missing from this build.")
                return@Column
            }
            TabRow(selectedTabIndex = tab, containerColor = SwgColors.Background) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Odds & tokens") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Lookup") })
            }
            if (tab == 0) OddsTab(ref) else LookupTab(ref)
        }
    }
}

@Composable
private fun OddsTab(ref: RefData) {
    val types = ref.vendor.keys.sortedBy { it.ordinal }
    var type by rememberSaveable { mutableStateOf(PartType.SHIELD) }
    var level by rememberSaveable { mutableStateOf(7) }
    var mode by rememberSaveable { mutableStateOf(Deviation.Mode.ORIGINAL) }
    val conditions = remember { mutableStateListOf(Condition("mass", true, "13000")) }
    var detail by remember { mutableStateOf<RefItem?>(null) }

    val statKeys = remember(type) {
        val present = ref.items.filter { it.type == type }.flatMap { it.stats.keys }.toSet()
        Deviation.statDefs.filter { it.key in present }
    }

    val items = ref.vendorItems(type, level)
    val price = ref.price(level)
    val parsed = conditions.mapNotNull { c -> c.text.replace(",", "").toDoubleOrNull()?.let { Triple(c.key, c.below, it) } }
    val perItem = items.map { item ->
        item to parsed.fold(1.0) { acc, (key, below, thr) ->
            acc * (item.stats[key]?.let { Deviation.chance(it, below, thr, mode) } ?: 0.0)
        }
    }.sortedByDescending { it.second }
    val p = if (perItem.isEmpty()) 0.0 else perItem.sumOf { it.second } / perItem.size

    LazyColumn(
        contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 120.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text("Space Duty vendor purchase", color = SwgColors.Muted, style = MaterialTheme.typography.labelMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 6.dp)) {
                items(types) { t ->
                    FilterChip(
                        selected = t == type,
                        onClick = {
                            if (t != type) {
                                type = t
                                conditions.clear()
                                conditions.add(Condition("mass", true, ""))
                            }
                        },
                        label = { Text(t.label) },
                    )
                }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items((1..10).toList()) { lv -> FilterChip(selected = lv == level, onClick = { level = lv }, label = { Text("L$lv") }) }
            }
        }
        item {
            Text("I want a roll where…", color = SwgColors.Gold, style = MaterialTheme.typography.titleSmall)
        }
        items(conditions.size) { i ->
            val c = conditions[i]
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StatPicker(statKeys, c.key, Modifier.weight(1.4f)) { c.key = it }
                OutlinedButton(onClick = { c.below = !c.below }, contentPadding = PaddingValues(horizontal = 10.dp)) {
                    Text(if (c.below) "below" else "above")
                }
                OutlinedTextField(
                    c.text, { c.text = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' } },
                    singleLine = true, modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                if (conditions.size > 1) IconButton(onClick = { conditions.removeAt(i) }) { Icon(Icons.Filled.Close, "Remove") }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (conditions.size < 4) TextButton(onClick = { conditions.add(Condition(statKeys.first().key, false, "")) }) {
                    Icon(Icons.Filled.Add, null); Text("Add condition")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Deviation.Mode.entries.forEach { m ->
                    FilterChip(selected = mode == m, onClick = { mode = m }, label = { Text(m.label) })
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = SwgColors.PanelHigh)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Level $level ${type.label.lowercase()} · $price tokens per buy · ${items.size} possible items",
                        color = SwgColors.Muted, style = MaterialTheme.typography.bodySmall,
                    )
                    if (items.isEmpty()) {
                        Text("The vendor sells nothing at this level.", color = SwgColors.Muted)
                    } else if (p <= 0.0) {
                        Text("Impossible", color = SwgColors.Bad, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(
                            mode.cap?.let { "No item here can roll that within ${it.toInt()} deviations. Try the original (uncapped) rules." }
                                ?: "No item here can roll that.",
                            color = SwgColors.Muted, style = MaterialTheme.typography.bodySmall,
                        )
                    } else {
                        Text("Chance per buy", color = SwgColors.Gold)
                        Text(
                            fmtChance(p) + "   (1 in ${"%,.0f".format(ceil(1 / p))})",
                            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                        )
                        Spacer6()
                        ResultLine("Average cost", "${fmtTokens(price / p)} tokens")
                        ResultLine("50% sure", "${fmtTokens(ceil(Deviation.buysFor(p, 0.5)) * price)} tokens")
                        ResultLine("90% sure", "${fmtTokens(ceil(Deviation.buysFor(p, 0.9)) * price)} tokens")
                        ResultLine("99% sure", "${fmtTokens(ceil(Deviation.buysFor(p, 0.99)) * price)} tokens")
                    }
                }
            }
        }
        if (items.isNotEmpty()) {
            item { Text("Each item (equal odds of getting any one)", color = SwgColors.Gold, style = MaterialTheme.typography.titleSmall) }
            items(perItem, key = { it.first.id }) { (item, chance) ->
                Row(
                    Modifier.fillMaxWidth().clickable { detail = item }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(item.name, color = SwgColors.Gold)
                        val avgs = parsed.mapNotNull { (k, _, _) ->
                            item.stats[k]?.let { "${Deviation.defsByKey[k]?.label ?: k} avg ${fmtStat(it.avg)}" }
                        }.distinct().joinToString(" · ")
                        Text("RE ${item.re}" + if (avgs.isNotBlank()) " · $avgs" else "", color = SwgColors.Muted, style = MaterialTheme.typography.bodySmall)
                    }
                    Text(fmtChance(chance), color = if (chance > 0) SwgColors.Text else SwgColors.Muted, fontWeight = FontWeight.SemiBold)
                }
                HorizontalDivider(color = Color(0x22FFFFFF))
            }
        }
        item {
            Text(
                "Rolls: value = average × (1 + deviation × modifier ÷ 2), deviation on a bell curve. " +
                    "Original rules let deviation run past 3 (very rarely 4–5); current SWG-Source clamps it to ±3. " +
                    "Averages come from the SWG-Source tables, so a server that changed them will differ.",
                color = SwgColors.Muted, style = MaterialTheme.typography.bodySmall,
            )
        }
    }
    detail?.let { ItemDetailDialog(it, ref) { detail = null } }
}

@Composable
private fun Spacer6() = Box(Modifier.padding(top = 6.dp))

@Composable
private fun ResultLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, color = SwgColors.Muted, modifier = Modifier.weight(1f))
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun StatPicker(defs: List<Deviation.StatDef>, key: String, modifier: Modifier, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 10.dp)) {
            Text(Deviation.defsByKey[key]?.label ?: key, maxLines = 1, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.ArrowDropDown, null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            defs.forEach { d -> DropdownMenuItem(text = { Text(d.label) }, onClick = { onPick(d.key); open = false }) }
        }
    }
}

@Composable
private fun LookupTab(ref: RefData) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf<PartType?>(null) }
    var detail by remember { mutableStateOf<RefItem?>(null) }
    val types = ref.items.map { it.type }.distinct().sortedBy { it.ordinal }
    val words = query.lowercase().split(' ').filter { it.isNotBlank() }
    val shown = ref.items
        .filter { filter == null || it.type == filter }
        .filter { item -> words.all { w -> item.name.lowercase().contains(w) || item.id.contains(w) } }
        .sortedWith(compareBy<RefItem> { it.type.ordinal }.thenBy { it.re }.thenBy { it.name })

    Column {
        OutlinedTextField(
            query, { query = it }, singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, null) }, placeholder = { Text("Search components") },
            modifier = Modifier.fillMaxWidth().padding(16.dp, 12.dp, 16.dp, 0.dp),
        )
        LazyRow(contentPadding = PaddingValues(16.dp, 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text("All") }) }
            items(types) { t -> FilterChip(selected = filter == t, onClick = { filter = if (filter == t) null else t }, label = { Text(t.label) }) }
        }
        LazyColumn(contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 120.dp)) {
            items(shown, key = { it.id }) { item ->
                Column(Modifier.fillMaxWidth().clickable { detail = item }.padding(vertical = 8.dp)) {
                    Text(item.name, color = SwgColors.Gold)
                    val lv = ref.vendorLevels(item)
                    Text(
                        "${item.type.label} · RE ${item.re}" + (item.stats["mass"]?.let { " · mass avg ${fmtStat(it.avg)}" } ?: "") +
                            if (lv.isNotEmpty()) " · vendor L${lv.joinToString("/")}" else "",
                        color = SwgColors.Muted, style = MaterialTheme.typography.bodySmall,
                    )
                }
                HorizontalDivider(color = Color(0x22FFFFFF))
            }
        }
    }
    detail?.let { ItemDetailDialog(it, ref) { detail = null } }
}

/** Average plus the worst/best values at 3 deviations (and best at 5, original rules). */
@Composable
fun ItemDetailDialog(item: RefItem, ref: RefData, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.name) },
        text = {
            Column(Modifier.heightIn(max = 480.dp)) {
                val lv = ref.vendorLevels(item)
                Text(
                    "${item.type.label} · RE ${item.re}" + if (lv.isNotEmpty()) " · Space Duty vendor level ${lv.joinToString(", ")}" else " · not on the duty vendor",
                    color = SwgColors.Muted, style = MaterialTheme.typography.bodySmall,
                )
                Row(Modifier.padding(top = 10.dp, bottom = 4.dp)) {
                    Text("Stat", color = SwgColors.Muted, modifier = Modifier.weight(1.3f), style = MaterialTheme.typography.labelSmall)
                    listOf("Worst (3)", "Average", "Best (3)", "Best (5)").forEach {
                        Text(it, color = SwgColors.Muted, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
                    }
                }
                LazyColumn {
                    items(Deviation.statDefs.filter { it.key in item.stats }) { def ->
                        val st = item.stats.getValue(def.key)
                        val sign = if (def.lowerIsBetter) -1.0 else 1.0
                        Row(Modifier.padding(vertical = 3.dp)) {
                            Text(def.label, modifier = Modifier.weight(1.3f), style = MaterialTheme.typography.bodySmall)
                            Text(fmtStat(Deviation.valueAt(st, -3 * sign)), color = Color(Deviation.band(-3.0).color), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            Text(fmtStat(st.avg), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            Text(fmtStat(Deviation.valueAt(st, 3 * sign)), color = Color(Deviation.band(2.5).color), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            Text(
                                if (st.uniform) "—" else fmtStat(Deviation.valueAt(st, 5 * sign)),
                                color = Color(Deviation.band(3.0).color), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
                Text(
                    "Modifier sets the spread: each deviation moves a stat by average × modifier ÷ 2.",
                    color = SwgColors.Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

/** Coloured chip showing a deviation (positive = better) and its band name. */
@Composable
fun DeviationChip(goodness: Double, rawZ: Double, modifier: Modifier = Modifier) {
    val suspicious = abs(rawZ) > 6.0
    val band = if (suspicious) Deviation.Band("Check", 0xFF8FA6A3) else Deviation.band(goodness)
    val c = Color(band.color)
    Row(
        modifier
            .background(c.copy(alpha = 0.16f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("%+.2f".format(goodness), color = c, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
        Text("  ${band.label}", color = c, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(70.dp))
    }
}
