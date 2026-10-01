@file:OptIn(ExperimentalMaterial3Api::class)

package com.thamightyboro.loadouts.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.thamightyboro.loadouts.AppViewModel
import com.thamightyboro.loadouts.data.Part
import com.thamightyboro.loadouts.data.PartType
import java.io.File

@Composable
fun PartsScreen(vm: AppViewModel, parts: List<Part>, onOpen: (String) -> Unit) {
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf<PartType?>(null) }
    var pendingPhoto by rememberSaveable { mutableStateOf<Uri?>(null) }
    var menuOpen by remember { mutableStateOf(false) }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = pendingPhoto
        if (ok && uri != null) vm.scan(uri) { onOpen("new") }
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.scan(uri) { onOpen("new") }
    }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            context.contentResolver.openOutputStream(uri)?.use { it.write(vm.exportJson().toByteArray()) }
            vm.message = "Backup saved."
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            if (text != null) vm.importJson(text)
        }
    }

    fun launchCamera() {
        val dir = File(context.cacheDir, "scans").apply { mkdirs() }
        val file = File(dir, "scan_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        pendingPhoto = uri
        takePicture.launch(uri)
    }

    val shown = parts
        .filter { filter == null || it.type == filter }
        .filter { query.isBlank() || it.name.contains(query, true) || it.notes.contains(query, true) }
        .sortedWith(compareBy<Part> { it.type.ordinal }.thenBy { it.name.lowercase() })

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Parts") },
                actions = {
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, "More") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Export backup") },
                            onClick = { menuOpen = false; exportLauncher.launch("swg-loadouts-backup.json") },
                        )
                        DropdownMenuItem(
                            text = { Text("Import backup") },
                            onClick = { menuOpen = false; importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SmallFloatingActionButton(onClick = { onOpen("blank") }) { Icon(Icons.Filled.Edit, "Add by hand") }
                SmallFloatingActionButton(onClick = {
                    pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) { Icon(Icons.Filled.Image, "Scan a screenshot") }
                ExtendedFloatingActionButton(
                    onClick = { launchCamera() },
                    icon = { Icon(Icons.Filled.CameraAlt, null) },
                    text = { Text("Scan part") },
                )
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                placeholder = { Text("Search parts") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text("All") }) }
                items(PartType.entries.filter { it != PartType.UNKNOWN }) { t ->
                    FilterChip(
                        selected = filter == t,
                        onClick = { filter = if (filter == t) null else t },
                        label = { Text(t.label) },
                    )
                }
            }
            if (shown.isEmpty()) {
                EmptyHint(
                    if (parts.isEmpty()) "No parts yet.\nTap Scan part and photograph a component's examine window, or pick a screenshot."
                    else "Nothing matches."
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 200.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(shown, key = { it.id }) { PartRow(it) { onOpen(it.id) } }
                }
            }
        }
    }
}

@Composable
fun PartRow(part: Part, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = SwgColors.Panel),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    part.type.label.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = SwgColors.Teal,
                )
            }
            Text(
                part.name.ifBlank { "(unnamed)" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = SwgColors.Gold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val h = part.headline()
            if (h.isNotBlank()) Text(h, style = MaterialTheme.typography.bodySmall, color = SwgColors.Muted)
        }
    }
}

@Composable
fun EmptyHint(text: String) {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(48.dp))
        Text(text, color = SwgColors.Muted, style = MaterialTheme.typography.bodyLarge)
    }
}
