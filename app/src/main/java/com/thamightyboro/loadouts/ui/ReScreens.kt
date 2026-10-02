@file:OptIn(ExperimentalMaterial3Api::class)

package com.thamightyboro.loadouts.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.thamightyboro.loadouts.AppViewModel
import com.thamightyboro.loadouts.data.Deviation
import com.thamightyboro.loadouts.data.Part
import com.thamightyboro.loadouts.data.PartType
import com.thamightyboro.loadouts.data.ReCalc
import com.thamightyboro.loadouts.data.ReProject
import com.thamightyboro.loadouts.data.StatLine
import java.io.File

/** RE tab: list of reverse engineering projects. */
@Composable
fun ReProjectsScreen(projects: List<ReProject>, onOpen: (String) -> Unit) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("RE projects") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onOpen("new") },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("New project") },
            )
        },
    ) { pad ->
        if (projects.isEmpty()) {
            Box(Modifier.padding(pad)) {
                EmptyHint("Start a project, then scan or add the parts you'll feed into the analysis tool - the app shows what comes out.")
            }
        } else {
            LazyColumn(
                Modifier.padding(pad),
                contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 120.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(projects.sortedByDescending { it.createdAt }, key = { it.id }) { p ->
                    val st = ReCalc.status(p)
                    Card(
                        Modifier.fillMaxWidth().clickable { onOpen(p.id) },
                        colors = CardDefaults.cardColors(containerColor = SwgColors.Panel),
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(p.name.ifBlank { "Untitled project" }, color = SwgColors.Gold, fontWeight = FontWeight.SemiBold)
                            val what = listOfNotNull(
                                st.type?.label,
                                st.level?.let { "RE $it" },
                                st.level?.let { "${p.items.size} / $it parts" } ?: "${p.items.size} parts",
                            ).joinToString(" · ")
                            Text(what.ifBlank { "No parts yet" }, color = SwgColors.Muted, style = MaterialTheme.typography.bodySmall)
                            Text(
                                if (st.ready) "Ready" else if (p.items.isEmpty()) "Empty" else "In progress",
                                color = if (st.ready) SwgColors.Good else SwgColors.Muted,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** One project: its parts (scanned, typed in or copied from the library) and the projected result. */
@Composable
fun ReProjectScreen(vm: AppViewModel, existing: ReProject?, parts: List<Part>, onBack: () -> Unit) {
    val start = remember { existing ?: ReProject() }
    var name by remember { mutableStateOf(start.name) }
    var level by remember { mutableStateOf(start.level) }
    val items = remember { mutableStateListOf<Part>().apply { addAll(start.items) } }
    var editing by remember { mutableStateOf<Pair<Int, Part>?>(null) }
    var picking by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val project = start.copy(name = name.trim(), level = level, items = items.toList())
    val status = ReCalc.status(project)

    // Saves as you go, so nothing scanned is lost if you just back out.
    LaunchedEffect(project) {
        if (project.items.isNotEmpty() || project.name.isNotBlank()) vm.saveReProject(project)
    }

    val context = LocalContext.current
    var pendingPhoto by rememberSaveable { mutableStateOf<Uri?>(null) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = pendingPhoto
        if (ok && uri != null) vm.scanPart(uri) { items.add(it) }
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.scanPart(uri) { items.add(it) }
    }
    fun launchCamera() {
        val dir = File(context.cacheDir, "scans").apply { mkdirs() }
        val file = File(dir, "re_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        pendingPhoto = uri
        takePicture.launch(uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "New RE project" else "RE project") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    if (existing != null) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Delete") }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp, 12.dp, 16.dp, 120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(name, { name = it }, label = { Text("Project name") }, singleLine = true, modifier = Modifier.fillMaxWidth())

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        listOfNotNull(status.type?.label, status.level?.let { "RE level $it" }).joinToString(" · ").ifBlank { "Add the first part" },
                        color = SwgColors.Teal, fontWeight = FontWeight.SemiBold,
                    )
                    val need = status.level
                    Text(
                        if (need != null) "${items.size} of $need parts" else "${items.size} parts",
                        color = SwgColors.Muted, style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (items.isNotEmpty() && items.first().reLevel == null) {
                    OutlinedTextField(
                        level?.toString() ?: "",
                        { level = it.filter(Char::isDigit).take(2).toIntOrNull()?.coerceIn(1, 10) },
                        label = { Text("RE level") }, singleLine = true, modifier = Modifier.width(110.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { launchCamera() }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Icon(Icons.Filled.CameraAlt, null); Text(" Camera")
                }
                OutlinedButton(
                    onClick = { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 8.dp),
                ) { Icon(Icons.Filled.Image, null); Text(" Screenshot") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        val t = status.type ?: PartType.UNKNOWN
                        editing = -1 to Part(
                            name = items.firstOrNull()?.name ?: "",
                            type = t,
                            reLevel = status.level,
                            stats = templateFor(t).map { StatLine(it, "") },
                        )
                    },
                    modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 8.dp),
                ) { Icon(Icons.Filled.Edit, null); Text(" Type in") }
                OutlinedButton(onClick = { picking = true }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Icon(Icons.Filled.Inventory2, null); Text(" My parts")
                }
            }

            if (items.isNotEmpty()) {
                SectionTitle("Parts going in")
                items.forEachIndexed { i, p ->
                    ReItemRow(vm, i, p, onEdit = { editing = i to p }, onMakeFirst = if (i > 0) {
                        { items.removeAt(i); items.add(0, p) }
                    } else null)
                }
                Text(
                    "The result keeps part 1's name and model - use ↑ to choose which.",
                    color = SwgColors.Muted, style = MaterialTheme.typography.bodySmall,
                )
            }

            ReResult(vm, project, status) { result ->
                vm.savePart(result)
                vm.message = "Saved the RE result to your parts."
            }
        }
    }

    editing?.let { (index, part) ->
        ReItemDialog(
            start = part,
            isNew = index < 0,
            onSave = { p -> if (index < 0) items.add(p) else items[index] = p; editing = null },
            onDelete = { if (index >= 0) items.removeAt(index); editing = null },
            onDismiss = { editing = null },
        )
    }

    if (picking) {
        val type = status.type
        val options = parts.filter { (type == null && it.type in ReCalc.reTypes) || it.type == type }
            .filter { status.level == null || it.reLevel == null || it.reLevel == status.level }
            .sortedBy { it.name.lowercase() }
        AlertDialog(
            onDismissRequest = { picking = false },
            title = { Text("Add from my parts") },
            text = {
                if (options.isEmpty()) {
                    Text("No matching parts in your library.", color = SwgColors.Muted)
                } else {
                    LazyColumn(Modifier.heightIn(max = 420.dp)) {
                        items(options, key = { it.id }) { p ->
                            Column(
                                Modifier.fillMaxWidth().clickable {
                                    // A copy, so editing it here never changes your saved part.
                                    items.add(p.copy(id = java.util.UUID.randomUUID().toString()))
                                    picking = false
                                }.padding(vertical = 10.dp),
                            ) {
                                Text(p.name.ifBlank { "(unnamed)" }, color = SwgColors.Gold)
                                Text(p.headline(), style = MaterialTheme.typography.bodySmall, color = SwgColors.Muted)
                            }
                            HorizontalDivider()
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { picking = false }) { Text("Close") } },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this project?") },
            text = { Text("Parts in your library aren't affected.") },
            confirmButton = { TextButton(onClick = { vm.deleteReProject(start.id); confirmDelete = false; onBack() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ReItemRow(vm: AppViewModel, index: Int, part: Part, onEdit: () -> Unit, onMakeFirst: (() -> Unit)?) {
    var open by remember { mutableStateOf(false) }
    Card(
        Modifier.fillMaxWidth().clickable { open = !open },
        colors = CardDefaults.cardColors(containerColor = SwgColors.Panel),
    ) {
        Row(Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${index + 1}", color = SwgColors.Teal, fontWeight = FontWeight.Bold, modifier = Modifier.width(28.dp))
            Column(Modifier.weight(1f)) {
                Text(part.name.ifBlank { "(unnamed)" }, color = SwgColors.Gold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val h = listOfNotNull(part.type.label, part.headline().ifBlank { null }).joinToString(" \u00b7 ")
                Text(h, color = SwgColors.Muted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, "Edit part") }
            if (onMakeFirst != null) IconButton(onClick = onMakeFirst) { Icon(Icons.Filled.ArrowUpward, "Make this part 1") }
        }
        if (open) {
            // Pre report: this part's own rolls, rated best in class
            val ref = vm.ref
            val item = ref?.let { r -> part.refId?.let(r.byId::get) ?: Deviation.rank(part, r).firstOrNull()?.item }
            Column(Modifier.padding(start = 40.dp, end = 12.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                if (item == null || ref == null) {
                    Text("No reference data to rate this part.", color = SwgColors.Muted, style = MaterialTheme.typography.bodySmall)
                } else {
                    Deviation.evaluate(part, item, ref).forEach { r ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(r.label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            Text(fmtStat(r.value) + "  ", color = SwgColors.Muted, style = MaterialTheme.typography.bodySmall)
                            DeviationChip(r.rating, r.z)
                        }
                    }
                }
            }
        }
    }
}

/** The projected result: best of each stat plus the RE bonus, rated against part 1's template. */
@Composable
private fun ReResult(vm: AppViewModel, project: ReProject, status: ReCalc.Status, onSave: (Part) -> Unit) {
    val level = status.level
    if (project.items.isEmpty() || level == null) return
    val rows = ReCalc.rows(project, level)
    val bonus = ReCalc.bonus(level)

    SectionTitle("Result")
    Card(colors = CardDefaults.cardColors(containerColor = SwgColors.PanelHigh)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "Best of each stat + ${pct(bonus)} (level $level ${pct(ReCalc.levelBonus(level))} + expertise ${pct(ReCalc.EXPERTISE_BONUS)})",
                color = SwgColors.Muted, style = MaterialTheme.typography.bodySmall,
            )
            status.problems.forEach { Text("• $it", color = SwgColors.Bad, style = MaterialTheme.typography.bodySmall) }

            val ref = vm.ref
            val first = project.items.first()
            val item = ref?.let { r -> first.refId?.let(r.byId::get) ?: Deviation.rank(first, r).firstOrNull()?.item }
            if (item != null) {
                Text("Rated against ${item.name}", color = SwgColors.Muted, style = MaterialTheme.typography.bodySmall)
            }
            HorizontalDivider(color = SwgColors.Panel)
            Text(
                "Pre = best roll going in \u00b7 Post = after the RE bonus. Tap a part above to see its own report.",
                color = SwgColors.Muted, style = MaterialTheme.typography.bodySmall,
            )
            rows.forEach { r ->
                val key = r.key
                val st = if (key != null) item?.stats?.get(key) else null
                Column(Modifier.padding(vertical = 4.dp)) {
                    Text(r.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    ReStatLine("Pre", "${fmtStat(r.best)} (part ${r.bestIndex + 1})", r.best, key, st, item, ref, false)
                    ReStatLine("Post", fmtStat(r.result), r.result, key, st, item, ref, true)
                }
            }
            if (rows.isEmpty()) Text("No stats read yet.", color = SwgColors.Muted)
            if (rows.isNotEmpty()) {
                Button(
                    onClick = { onSave(ReCalc.resultPart(project, level, rows)) },
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                ) { Text("Save result to my parts") }
            }
        }
    }
}

/** One "Pre"/"Post" line: label, value and its best-in-class chip. */
@Composable
private fun ReStatLine(
    tag: String, text: String, value: Double, key: String?, st: com.thamightyboro.loadouts.data.RefStat?,
    item: com.thamightyboro.loadouts.data.RefItem?, ref: com.thamightyboro.loadouts.data.RefData?, highlight: Boolean,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(tag, color = SwgColors.Teal, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(40.dp))
        Text(
            text, modifier = Modifier.weight(1f),
            color = if (highlight) SwgColors.Gold else SwgColors.Text,
            fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal,
            style = MaterialTheme.typography.bodyMedium,
        )
        if (key != null && st != null && st.mod > 0) {
            val z = Deviation.z(st, value)
            val rating = item?.let { i -> ref?.let { Deviation.classGoodness(it, i, key, value) } } ?: Deviation.goodness(key, z)
            DeviationChip(rating, z)
        }
    }
}

private fun pct(v: Double): String = "%.0f%%".format(v * 100)

/** Add or edit one part in the project by hand (or fix what a scan read). */
@Composable
private fun ReItemDialog(start: Part, isNew: Boolean, onSave: (Part) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(start.name) }
    var type by remember { mutableStateOf(start.type) }
    var re by remember { mutableStateOf(start.reLevel?.toString() ?: "") }
    val stats = remember { mutableStateListOf<StatLine>().apply { addAll(start.stats) } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "Add part" else "Edit part") },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    re, { re = it.filter(Char::isDigit).take(2) }, label = { Text("RE level") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(PartType.entries.filter { it in ReCalc.reTypes }) { t ->
                        FilterChip(
                            selected = t == type,
                            onClick = {
                                if (t != type && stats.all { it.value.isBlank() }) {
                                    stats.clear(); stats.addAll(templateFor(t).map { StatLine(it, "") })
                                }
                                type = t
                            },
                            label = { Text(t.label) },
                        )
                    }
                }
                StatEditor(stats)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    start.copy(
                        name = name.trim(),
                        type = type,
                        reLevel = re.toIntOrNull(),
                        stats = stats.filter { it.label.isNotBlank() && it.value.isNotBlank() },
                    ),
                )
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (!isNew) TextButton(onClick = onDelete) { Text("Remove", color = SwgColors.Bad) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
