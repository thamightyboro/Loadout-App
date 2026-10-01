@file:OptIn(ExperimentalMaterial3Api::class)

package com.thamightyboro.loadouts.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.thamightyboro.loadouts.data.Loadout
import com.thamightyboro.loadouts.data.Part
import com.thamightyboro.loadouts.data.fmt
import com.thamightyboro.loadouts.data.totals

@Composable
fun LoadoutsScreen(loadouts: List<Loadout>, parts: Map<String, Part>, onOpen: (String) -> Unit) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Loadouts") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onOpen("new") },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("New loadout") },
            )
        },
    ) { pad ->
        if (loadouts.isEmpty()) {
            Column(Modifier.padding(pad)) {
                EmptyHint("No loadouts yet.\nCreate one, pick a chassis and slot parts from your library.")
            }
        } else {
            LazyColumn(
                Modifier.padding(pad).fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(loadouts.sortedBy { it.name.lowercase() }, key = { it.id }) { l ->
                    val t = l.totals(parts)
                    Card(
                        Modifier.fillMaxWidth().clickable { onOpen(l.id) },
                        colors = CardDefaults.cardColors(containerColor = SwgColors.Panel),
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(l.chassis.ifBlank { "No chassis" }.uppercase(), style = MaterialTheme.typography.labelSmall, color = SwgColors.Teal)
                            Text(l.name.ifBlank { "(unnamed)" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = SwgColors.Gold)
                            val over = l.massLimit != null && t.mass > l.massLimit
                            Text(
                                buildString {
                                    append("Mass ${fmt(t.mass)}")
                                    l.massLimit?.let { append(" / ${fmt(it)}") }
                                    append(" · ${t.filled}/${t.total} slots")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (over) SwgColors.Bad else SwgColors.Muted,
                            )
                        }
                    }
                }
            }
        }
    }
}
