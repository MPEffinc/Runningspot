package com.example.runningspot.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.example.runningspot.data.remote.RouteDetailDto
import com.example.runningspot.viewmodel.RouteDetailViewModel
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.KakaoMapReadyCallback
import com.kakao.vectormap.LatLng
import com.kakao.vectormap.MapLifeCycleCallback
import com.kakao.vectormap.MapView
import com.kakao.vectormap.camera.CameraUpdateFactory
import com.kakao.vectormap.route.RouteLineOptions
import com.kakao.vectormap.route.RouteLineSegment
import com.kakao.vectormap.route.RouteLineStyle
import com.kakao.vectormap.route.RouteLineStyles
import android.graphics.Color as AColor

@Composable
fun RouteDetailScreen(
    padding: PaddingValues,
    routeId: Long,
    onBack: () -> Unit,
    viewModel: RouteDetailViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val route by viewModel.route.collectAsState()
    val error by viewModel.error.collectAsState()

    LaunchedEffect(routeId) {
        viewModel.loadRouteDetail(routeId)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Button(onClick = onBack) { Text("← 뒤로") }
            Text("루트 상세", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.width(1.dp))
        }

        Spacer(Modifier.height(12.dp))

        when {
            error != null -> {
                Text("서버 에러: $error", color = Color.Red)
            }
            route == null -> {
                Text("불러오는 중...", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> {
                val r = route!!
                Text(r.title, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                Spacer(Modifier.height(6.dp))
                Text("거리: ${"%.1f".format(r.distance_m / 1000.0)} km", fontSize = 13.sp)

                Spacer(Modifier.height(12.dp))

                RoutePolylinePreview(
                    route = r,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )

                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = { /* TODO */ },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("이 루트로 뛰기")
                }
            }
        }
    }
}


@Composable
private fun RoutePolylinePreview(
    route: RouteDetailDto,
    modifier: Modifier = Modifier,
    zoomLevel: Int = 15
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    val mapView = remember { MapView(context) }

    DisposableEffect(lifecycleOwner, mapView) {
        val obs = object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) { mapView.resume() }
            override fun onPause(owner: LifecycleOwner) { mapView.pause() }
            override fun onDestroy(owner: LifecycleOwner) { mapView.finish() }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(obs)
            runCatching { mapView.finish() }
        }
    }

    val readyCb = remember(route.id) {
        object : KakaoMapReadyCallback() {
            override fun onMapReady(map: KakaoMap) {
                val pts = route.points
                if (pts.size > 1) {
                    val latLngs = pts.map { LatLng.from(it.lat, it.lng) }

                    map.routeLineManager?.let { manager ->
                        val layer = manager.layer
                        val style = RouteLineStyle.from(8f, AColor.BLUE)
                        val styles = RouteLineStyles.from(style)
                        val seg = RouteLineSegment.from(latLngs).setStyles(styles)
                        val options = RouteLineOptions.from(seg)
                        layer.addRouteLine(options).show()
                    }

                    val avgLat = pts.map { it.lat }.average()
                    val avgLng = pts.map { it.lng }.average()
                    map.moveCamera(
                        CameraUpdateFactory.newCenterPosition(LatLng.from(avgLat, avgLng))
                    )
                } else {
                    // points가 없으면 start_lat/start_lng로 이동
                    map.moveCamera(
                        CameraUpdateFactory.newCenterPosition(
                            LatLng.from(route.start_lat, route.start_lng)
                        )
                    )
                }
            }

            override fun getPosition(): LatLng =
                LatLng.from(route.start_lat, route.start_lng)

            override fun getZoomLevel(): Int = zoomLevel
        }
    }

    LaunchedEffect(mapView, route.id) {
        mapView.start(object : MapLifeCycleCallback() {
            override fun onMapDestroy() {}
            override fun onMapError(error: Exception?) { error?.printStackTrace() }
        }, readyCb)
    }

    AndroidView(modifier = modifier, factory = { mapView })
}