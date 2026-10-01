package com.thamightyboro.loadouts.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Loosely after the SWG examine window: dark teal panels, gold labels, light values.
object SwgColors {
    val Background = Color(0xFF0E1A1C)
    val Panel = Color(0xFF16292C)
    val PanelHigh = Color(0xFF1F3A3E)
    val Gold = Color(0xFFE8C547)
    val Teal = Color(0xFF7FD4C1)
    val Text = Color(0xFFE4EEEC)
    val Muted = Color(0xFF8FA6A3)
    val Good = Color(0xFF6BD66B)
    val Bad = Color(0xFFE06C5C)
}

private val scheme = darkColorScheme(
    primary = SwgColors.Gold,
    onPrimary = Color(0xFF1A1500),
    secondary = SwgColors.Teal,
    onSecondary = Color(0xFF00201A),
    background = SwgColors.Background,
    onBackground = SwgColors.Text,
    surface = SwgColors.Background,
    onSurface = SwgColors.Text,
    surfaceVariant = SwgColors.Panel,
    onSurfaceVariant = SwgColors.Muted,
    surfaceContainer = SwgColors.Panel,
    surfaceContainerHigh = SwgColors.PanelHigh,
    surfaceContainerHighest = SwgColors.PanelHigh,
    surfaceContainerLow = SwgColors.Panel,
    secondaryContainer = SwgColors.PanelHigh,
    onSecondaryContainer = SwgColors.Gold,
    error = SwgColors.Bad,
)

@Composable
fun LoadoutsTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
