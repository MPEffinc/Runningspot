package com.example.runningspot.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*

@Composable
fun BottomNavBar(selectedIndex: Int, onTabSelected: (Int) -> Unit) {
    val items = listOf(
        Icons.Default.Info to "정보",
        Icons.Default.Leaderboard to "통계",
        Icons.Default.DirectionsRun to "러닝",
        Icons.Default.People to "커뮤니티",
        Icons.Default.Person to "마이"
    )

    NavigationBar {
        items.forEachIndexed { index, pair ->
            NavigationBarItem(
                icon = { Icon(pair.first, contentDescription = pair.second) },
                label = { Text(pair.second) },
                selected = selectedIndex == index,
                onClick = { onTabSelected(index) }
            )
        }
    }
}