package com.example.runningspot.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.runningspot.viewmodel.RouteViewModel

@Composable
fun NearbyRoutesSection(
    viewModel: RouteViewModel,
    onRouteClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val nearbyRoutes by viewModel.nearbyRoutes.collectAsState()
    val error by viewModel.error.collectAsState()

    // TODO: 나중에는 실제 현재 위치로 교체
    LaunchedEffect(Unit) {
        viewModel.loadNearbyRoutes(lat = 37.4, lng = 126.6)
    }

    Column(modifier = modifier) {
        Spacer(Modifier.height(28.dp))
        Text("📍 주변 공유 러닝 루트", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        when {
            error != null -> {
                Text("서버 에러: $error", color = Color.Red, fontSize = 13.sp)
            }
            nearbyRoutes.isEmpty() -> {
                Text("주변에 표시할 러닝 루트가 없습니다.", fontSize = 13.sp, color = Color.Gray)
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp), // 화면 전체를 잡아먹지 않게 제한(원하면 조절)
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(nearbyRoutes.size) { i ->
                        val r = nearbyRoutes[i]
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onRouteClick(r.id) },
                            elevation = CardDefaults.cardElevation(2.dp)
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(r.title, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "거리: ${"%.2f".format(r.distance_m / 1000.0)} km",
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
