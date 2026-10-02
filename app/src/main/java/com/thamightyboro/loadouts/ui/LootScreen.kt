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
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Image
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File
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
import com.thamightyboro.loadouts.data.Part
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
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Scan") })
            }
            if (tab == 0) OddsTab(ref) else ScanTab(vm, ref)
        }
    }
}

@Composable
private fun OddsTab(ref: RefData) {
    val types = ref.vendor.keys.sortedBy { it.ordinal }
    var type by rememberSaveable { mutableStateOf(PartType.SHIELD) }
    var level by rememberSaveable { mutableStateOf(7) }
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
            acc * (item.stats[key]?.let { Deviation.chance(it, below, thr) } ?: 0.0)
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
        }
        item { OddsCard(type, level, price, items.size, p) }
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
                "Rolls: value = average × (1 + deviation × modifier ÷ 2), deviation on an uncapped bell curve " +
                    "(past 3 is rare, 4–5 very rare). Averages come from the SWG-Source tables, so a server that changed them will differ.",
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

/** Scan an examine window (camera or screenshot) and see straight away how good each roll is. */
@Composable
private fun ScanTab(vm: AppViewModel, ref: RefData) {
    val context = LocalContext.current
    var pendingPhoto by rememberSaveable { mutableStateOf<Uri?>(null) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = pendingPhoto
        if (ok && uri != null) vm.scanToCheck(uri)
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.scanToCheck(uri)
    }
    fun launchCamera() {
        val dir = File(context.cacheDir, "scans").apply { mkdirs() }
        val file = File(dir, "check_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        pendingPhoto = uri
        takePicture.launch(uri)
    }

    val checked = vm.checked
    var refId by remember(checked) { mutableStateOf<String?>(null) }
    var type by remember(checked) { mutableStateOf(checked?.type ?: PartType.UNKNOWN) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp, 12.dp, 16.dp, 120.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { launchCamera() }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.CameraAlt, null); Text("  Camera")
            }
            OutlinedButton(
                onClick = { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                modifier = Modifier.weight(1f),
            ) { Icon(Icons.Filled.Image, null); Text("  Screenshot") }
        }
        if (checked == null) {
            Text(
                "Scan a component's examine window to see how far each stat rolled from average - nothing is saved unless you choose to.",
                color = SwgColors.Muted, style = MaterialTheme.typography.bodyMedium,
            )
            return@Column
        }
        val part = checked.copy(type = type)
        Text(part.name.ifBlank { "(name not read)" }, color = SwgColors.Gold, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        part.reLevel?.let { Text("RE level $it", color = SwgColors.Muted, style = MaterialTheme.typography.bodySmall) }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(PartType.entries.filter { it != PartType.UNKNOWN }) { t ->
                FilterChip(selected = t == type, onClick = { type = t; refId = null }, label = { Text(t.label) })
            }
        }
        DeviationSection(ref, part, refId) { refId = it }
        val matched = remember(part, refId) { refId?.let(ref.byId::get) ?: Deviation.rank(part, ref).firstOrNull()?.item }
        if (matched != null) {
            HorizontalDivider(color = SwgColors.PanelHigh)
            BestStatOdds(ref, part, matched)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = {
                    vm.savePart(part.copy(refId = refId))
                    vm.message = "Saved to your parts."
                    vm.clearChecked()
                },
                modifier = Modifier.weight(1f),
            ) { Text("Save to my parts") }
            OutlinedButton(onClick = { vm.clearChecked() }, modifier = Modifier.weight(1f)) { Text("Clear") }
        }
    }
}

/** Chance per buy and token costs for one Space Duty vendor purchase. */
@Composable
private fun OddsCard(type: PartType, level: Int, price: Int, itemCount: Int, p: Double) {
    Card(colors = CardDefaults.cardColors(containerColor = SwgColors.PanelHigh)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "Level $level ${type.label.lowercase()} \u00b7 $price tokens per buy \u00b7 $itemCount possible items",
                color = SwgColors.Muted, style = MaterialTheme.typography.bodySmall,
            )
            if (itemCount == 0) {
                Text("The vendor sells nothing at this level.", color = SwgColors.Muted)
            } else if (p <= 0.0) {
                Text("Impossible", color = SwgColors.Bad, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("No item here can roll that.", color = SwgColors.Muted, style = MaterialTheme.typography.bodySmall)
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

/**
 * Token odds of rolling a stat at least as good as the scanned one, from the Space Duty vendor.
 * Defaults to the part's best stat; tap another stat to switch.
 */
@Composable
private fun BestStatOdds(ref: RefData, part: Part, item: RefItem) {
    val rows = remember(part, item) {
        Deviation.evaluate(part, item).filter { r -> item.stats[r.key]?.let { it.mod > 0 } == true && abs(r.z) <= 6.0 }
    }
    if (rows.isEmpty()) return
    val best = rows.maxBy { it.goodness }
    var key by remember(part, item) { mutableStateOf(best.key) }
    val row = rows.firstOrNull { it.key == key } ?: best
    val vendorLv = ref.vendorLevels(item)
    var level by remember(part, item) {
        mutableStateOf(vendorLv.firstOrNull() ?: (part.reLevel ?: item.re).coerceIn(1, 10))
    }
    val below = Deviation.defsByKey[row.key]?.lowerIsBetter == true
    val items = ref.vendorItems(item.type, level)
    val p = if (items.isEmpty()) 0.0 else items.sumOf { it.stats[row.key]?.let { st -> Deviation.chance(st, below, row.value) } ?: 0.0 } / items.size
    val pSame = item.stats[row.key]?.let { Deviation.chance(it, below, row.value) } ?: 0.0

    Text("Token roll for this stat", color = SwgColors.Gold, style = MaterialTheme.typography.titleSmall)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(rows, key = { it.key }) { r ->
            FilterChip(
                selected = r.key == row.key, onClick = { key = r.key },
                label = { Text(r.label + if (r.key == best.key) " \u2605" else "") },
            )
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                "${row.label} ${if (below) "\u2264" else "\u2265"} ${fmtStat(row.value)}",
                style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold,
            )
            Text("avg ${fmtStat(row.avg)}", color = SwgColors.Muted, style = MaterialTheme.typography.bodySmall)
        }
        DeviationChip(row.goodness, row.z)
    }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items((1..10).toList()) { lv ->
            FilterChip(
                selected = lv == level, onClick = { level = lv },
                label = { Text(if (lv in vendorLv) "L$lv \u2022" else "L$lv") },
            )
        }
    }
    OddsCard(item.type, level, ref.price(level), items.size, p)
    Text(
        if (vendorLv.isEmpty()) "${item.name} isn't sold by the duty vendor - odds above are for any level $level ${item.type.label.lowercase()} matching this stat."
        else "Odds cover every item the level $level vendor can give (\u2022 = levels that sell ${item.name}). " +
            "Rolling this on ${item.name} itself: ${fmtChance(pSame)} per copy.",
        color = SwgColors.Muted, style = MaterialTheme.typography.bodySmall,
    )
}

/** Average plus the worst/best values at 3 deviations, and the best at 5. */
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
                    listOf("−3 dev", "Average", "+3 dev", "+5 dev").forEach {
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
                            Text(fmtStat(Deviation.valueAt(st, 3 * sign)), color = Color(Deviation.band(3.0).color), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            Text(
                                if (st.uniform) "—" else fmtStat(Deviation.valueAt(st, 5 * sign)),
                                color = Color(Deviation.band(5.0).color), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
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
        Text("  ${band.label}", color = c, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(96.dp))
    }
}
