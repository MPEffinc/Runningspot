package com.example.runningspot.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun BottomNavBar(selectedIndex: Int, onTabSelected: (Int) -> Unit) {
    val items = listOf(
        Icons.Default.History to "기록",
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