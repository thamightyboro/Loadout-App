package com.thamightyboro.loadouts.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.thamightyboro.loadouts.AppViewModel

@Composable
fun App(vm: AppViewModel) {
    val nav = rememberNavController()
    val data by vm.data.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route

    LaunchedEffect(vm.message) {
        vm.message?.let {
            snackbar.showSnackbar(it)
            vm.message = null
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (route == "parts" || route == "loadouts" || route == "loot") {
                NavigationBar {
                    listOf(
                        Triple("parts", "Parts", Icons.Filled.Inventory2),
                        Triple("loadouts", "Loadouts", Icons.Filled.RocketLaunch),
                        Triple("loot", "Loot", Icons.Filled.Calculate),
                    ).forEach { (r, label, icon) ->
                        NavigationBarItem(
                            selected = route == r,
                            onClick = {
                                nav.navigate(r) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(icon, null) },
                            label = { Text(label) },
                        )
                    }
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            NavHost(nav, startDestination = "parts") {
                composable("parts") {
                    PartsScreen(
                        vm = vm,
                        parts = data.parts,
                        onOpen = { nav.navigate("part/$it") },
                    )
                }
                composable("part/{id}") { entry ->
                    val id = entry.arguments?.getString("id") ?: "new"
                    PartEditScreen(
                        vm = vm,
                        id = id,
                        existing = data.parts.firstOrNull { it.id == id },
                        onBack = { nav.popBackStack() },
                    )
                }
                composable("loadouts") {
                    LoadoutsScreen(
                        loadouts = data.loadouts,
                        parts = data.partsById,
                        onOpen = { nav.navigate("loadout/$it") },
                    )
                }
                composable("loot") { LootScreen(vm) }
                composable("loadout/{id}") { entry ->
                    val id = entry.arguments?.getString("id") ?: "new"
                    LoadoutEditScreen(
                        vm = vm,
                        existing = data.loadouts.firstOrNull { it.id == id },
                        parts = data.parts,
                        onBack = { nav.popBackStack() },
                    )
                }
            }

            if (vm.scanning) {
                Box(
                    Modifier.fillMaxSize().background(Color(0xAA000000)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Text("Reading stats…", Modifier.padding(top = 12.dp))
                    }
                }
            }
        }
    }
}
