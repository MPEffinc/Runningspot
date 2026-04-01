package com.example.runningspot.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Leaderboard
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

private data class BottomNavItem(
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val label: String
)

@Composable
fun BottomNavBar(selectedIndex: Int, onTabSelected: (Int) -> Unit) {
    val items = listOf(
        BottomNavItem(Icons.Filled.Info, Icons.Outlined.Info, "기록"),
        BottomNavItem(Icons.Filled.Leaderboard, Icons.Outlined.Leaderboard, "통계"),
        BottomNavItem(Icons.Filled.DirectionsRun, Icons.Outlined.DirectionsRun, "러닝"),
        BottomNavItem(Icons.Filled.People, Icons.Outlined.People, "커뮤니티"),
        BottomNavItem(Icons.Filled.Person, Icons.Outlined.Person, "프로필")
    )

    NavigationBar(containerColor = Color(0xFFFAFAF8)) {
        items.forEachIndexed { index, item ->
            val isSelected = selectedIndex == index
            NavigationBarItem(
                icon = {
                    Icon(
                        imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                        contentDescription = item.label
                    )
                },
                label = { Text(item.label) },
                selected = isSelected,
                onClick = { onTabSelected(index) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color(0xFF204996),
                    selectedTextColor = Color(0xFF204996),
                    indicatorColor = Color(0x33F1B243),
                    unselectedIconColor = Color(0xFF2A2A2A),
                    unselectedTextColor = Color(0xFF2A2A2A)
                )
            )
        }
    }
}