package com.example.runningspot.ui.components

import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

data class BottomNavDestination(
    val route: String,
    val icon: ImageVector,
    val label: String
)

@Composable
fun BottomNavBar(
    destinations: List<BottomNavDestination>,
    currentRoute: String?,
    onTabSelected: (BottomNavDestination) -> Unit
) {
    NavigationBar {
        destinations.forEach { destination ->
            NavigationBarItem(
                icon = { Icon(destination.icon, contentDescription = destination.label) },
                label = { Text(destination.label) },
                selected = currentRoute == destination.route,
                onClick = { onTabSelected(destination) }
            )
        }
    }
}