package com.example.runningspot.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.example.runningspot.ui.components.BottomNavBar
import com.google.android.gms.location.*
import com.google.android.gms.tasks.CancellationTokenSource
import com.kakao.vectormap.*
import com.kakao.vectormap.camera.CameraUpdateFactory
import com.kakao.vectormap.label.LabelOptions
import com.kakao.vectormap.label.LabelTextBuilder
import kotlinx.coroutines.launch
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.kakao.vectormap.label.LabelStyle
import com.kakao.vectormap.label.LabelStyles
import com.example.runningspot.R
import com.example.runningspot.RunningActivity
import com.kakao.vectormap.route.RouteLine
import com.kakao.vectormap.route.RouteLineManager
import com.kakao.vectormap.route.RouteLineOptions
import com.kakao.vectormap.route.RouteLineSegment
import com.kakao.vectormap.route.RouteLineStyle
import com.kakao.vectormap.route.RouteLineStyles


@Composable
fun MainScreen(
    userName: String?,
    userProfile: String?,
    provider: String?,
    onLogout: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(2) } // 기본 러닝 탭 선택

    val runningScreen = remember { mutableStateOf<(@Composable (PaddingValues) -> Unit)>({ RunningScreen(it) }) }

    Scaffold(
        bottomBar = { BottomNavBar(selectedTab, onTabSelected = { selectedTab = it }) }
    ) { padding ->

        when (selectedTab) {
            0 -> InfoScreen(padding)
            1 -> StatsScreen(padding)
            2 -> runningScreen.value.invoke(padding)
            3 -> CommunityScreen(padding)
            4 -> MyPageScreen(
                padding = padding,
                userName = userName,
                userProfile = userProfile,
                provider = provider,
                onLogout = onLogout)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunningScreen(padding: PaddingValues) {
    val context = LocalContext.current
    val activity = context as ComponentActivity
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    val mapView = remember { MapView(context) }
    var kakaoMap by remember { mutableStateOf<KakaoMap?>(null) }

    var hasLocationPermission by remember { mutableStateOf(false) }
    var isRunning by remember { mutableStateOf(false) }
    val runningPath = remember { mutableStateListOf<LatLng>() }
    var currentRoute by remember { mutableStateOf<RouteLine?>(null) }
    var showRunningDialog by remember { mutableStateOf(false) }


    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        hasLocationPermission =
            result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                    result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        // 권한 승인 후 바로 초기 중심 설정
        tryInitCenter(fusedLocationClient, kakaoMap, hasLocationPermission)
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data ?: return@rememberLauncherForActivityResult
            val size = data.getIntExtra("pathSize", 0)
            if (size > 1) {
                val path = (0 until size).map { i ->
                    LatLng.from(
                        data.getDoubleExtra("lat_$i", 0.0),
                        data.getDoubleExtra("lng_$i", 0.0)
                    )
                }
                // ✅ 지도 위에 다시 그리기
                kakaoMap?.routeLineManager?.let { manager ->
                    val layer = manager.layer
                    val style = RouteLineStyle.from(8f, android.graphics.Color.BLUE)
                    val styles = RouteLineStyles.from(style)
                    val seg = RouteLineSegment.from(path).setStyles(styles)
                    val options = RouteLineOptions.from(seg)
                    layer.addRouteLine(options).show()
                }
            }
        }
    }


    // 최초 권한 요청
    LaunchedEffect(Unit) {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED) {
            hasLocationPermission = true
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

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


    val readyCb = remember {
        object : KakaoMapReadyCallback() {
            override fun onMapReady(map: KakaoMap) {
                Log.d("RUNNINGSPOTDEBUG", "KakaoMap Ready.")
                kakaoMap = map

                // 권한이 있으면 즉시 현재 위치로 이동
                if (hasLocationPermission) {
                    getSingleFix(fusedLocationClient) { lat, lng ->
                        moveCameraTo(map, lat, lng)
                        updateCurrentLabel(map, lat, lng)
                    }
                }
            }

            override fun getPosition(): LatLng = LatLng.from(37.406960, 127.115587)
            override fun getZoomLevel(): Int = 15
        }
    }

    LaunchedEffect(mapView) {
        mapView.start(object : MapLifeCycleCallback() {
            override fun onMapDestroy() {}
            override fun onMapError(error: Exception?) {
                error?.printStackTrace()
            }
        }, readyCb)
    }

    // ✅ 위치 콜백 (러닝 중 이동경로 추적)
    val locationCallback = remember {
        object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                if (!isRunning) return
                val map = kakaoMap ?: return
                val routeLineManager = map.routeLineManager
                for (loc in result.locations) {
                    val latLng = LatLng.from(loc.latitude, loc.longitude)
                    runningPath.add(latLng)
                    moveCameraTo(map, latLng.latitude, latLng.longitude)
                    updateCurrentLabel(map, latLng.latitude, latLng.longitude)
                    drawRunningPath(map, routeLineManager, runningPath, currentRoute) {
                        currentRoute = it
                    }
                }
            }
        }
    }

    // ✅ 러닝 시작/종료 함수
    fun startRunning() {
        if (!hasLocationPermission) {
            Toast.makeText(context, "위치 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
            return
        }
        runningPath.clear()
        isRunning = true
        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY, 2000L
        ).build()
        fusedLocationClient.requestLocationUpdates(request, locationCallback, android.os.Looper.getMainLooper())
        Toast.makeText(context, "러닝 시작!", Toast.LENGTH_SHORT).show()
    }

    fun stopRunning() {
        isRunning = false
        fusedLocationClient.removeLocationUpdates(locationCallback)
        Toast.makeText(context, "러닝 종료!", Toast.LENGTH_SHORT).show()
    }


    // ===== UI =====
    Box(
        Modifier
            .fillMaxSize()
            .padding(padding)
    ) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())

        OutlinedTextField(
            value = "",
            onValueChange = {},
            placeholder = { Text("장소 검색") },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(12.dp)
                .fillMaxWidth(0.9f)
        )

        FloatingActionButton(
            onClick = {
                Toast.makeText(context, "현재 위치 불러오는 중...", Toast.LENGTH_SHORT).show()
                val map = kakaoMap ?: return@FloatingActionButton
                Log.d("RUNNINGSPOTDEBUG", "Change View: Current Location")
                if (hasLocationPermission) {
                    getSingleFix(fusedLocationClient) { lat, lng ->
                        Log.d("RUNNINGSPOTDEBUG", "SingleFix: $lat, $lng")
                        Toast.makeText(context, "현재 위치: $lat, $lng", Toast.LENGTH_SHORT).show()
                        moveCameraTo(map, lat, lng)
                        updateCurrentLabel(map, lat, lng)
                    }
                } else {
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }
            },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primary
        ) { Text("현재 위치") }

        // 러닝 시작 버튼
        FloatingActionButton(
            onClick = {
                val intent = Intent(context, RunningActivity::class.java)
                launcher.launch(intent)
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primary
        ) { Text("러닝 시작") }
    }
}

private fun tryInitCenter(
    fused: FusedLocationProviderClient,
    map: KakaoMap?,
    hasPermission: Boolean
) {
    if (!hasPermission || map == null) return
    getSingleFix(fused) { lat, lng ->
        moveCameraTo(map, lat, lng)
        updateCurrentLabel(map, lat, lng)
    }
}

private fun moveCameraTo(map: KakaoMap, lat: Double, lng: Double) {
    val pos = LatLng.from(lat, lng)
    val update = CameraUpdateFactory.newCenterPosition(pos)
    map.moveCamera(update)
}

private fun updateCurrentLabel(map: KakaoMap, lat: Double, lng: Double) {
    val pos = LatLng.from(lat, lng)
    val labelManager = map.getLabelManager()
    val layer = labelManager?.layer

    layer?.removeAll()


    val styles = labelManager?.addLabelStyles( // 공식 가이드 패턴
        LabelStyles.from(LabelStyle.from(R.drawable.arrow)) // drawable에 marker 아이콘 추가 필요
    )

    val options = LabelOptions.from(pos)
        .setStyles(styles)

    layer?.addLabel(options)

    Log.d("RUNNINGSPOTDEBUG", "Label added at $lat, $lng")
}

private fun drawRunningPath(
    map: KakaoMap,
    manager: RouteLineManager?,
    path: List<LatLng>,
    currentRoute: RouteLine?,
    onUpdate: (RouteLine) -> Unit
) {
    if (manager == null || path.size < 2) return
    val layer = manager.layer
    currentRoute?.let { layer.remove(it) }
    val style = RouteLineStyle.from(8f, android.graphics.Color.BLUE)
    val styles = RouteLineStyles.from(style)
    val segment = RouteLineSegment.from(path).setStyles(styles)
    val options = RouteLineOptions.from(segment)
    val newRoute = layer.addRouteLine(options)
    newRoute.show()
    onUpdate(newRoute)
}



@SuppressLint("MissingPermission")
private fun getSingleFix(
    fused: FusedLocationProviderClient,
    onFix: (Double, Double) -> Unit
) {
    val cts = CancellationTokenSource()
    fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
        .addOnSuccessListener { loc ->
            if (loc != null) onFix(loc.latitude, loc.longitude)
        }
}


@Composable
private fun InfoScreen(padding: PaddingValues) {
    Box(
        Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(20.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("러닝 스팟 (Running Spot)", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(16.dp))
            Text("버전: 1.0.0")
            Text("개발자: INU 컴퓨터공학부 팀 모멘텀")
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                "이 앱은 사용자의 러닝 코스를 추적하고, 커뮤니티를 통해\n" +
                        "다른 사용자와 운동 정보를 공유할 수 있도록 합니다.",
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
fun StatsScreen(padding: PaddingValues) {
    Column(
        Modifier.fillMaxSize().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("📊 러닝 통계", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(Color(0xFFE0E0E0)),
            contentAlignment = Alignment.Center
        ) {
            Text("그래프 예시 영역")
        }

        Spacer(Modifier.height(16.dp))
        Text("목표 수치 달성! 🎯", fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("평균 페이스")
                Text("5’22”/km", fontWeight = FontWeight.Bold)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("시간")
                Text("27:00", fontWeight = FontWeight.Bold)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("칼로리")
                Text("410 kcal", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun CommunityScreen(padding: PaddingValues) {
    LazyColumn(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(3) { i ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text("사용자 ${i + 1}의 러닝 후기", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("오늘 ${4 + i}km 뛰었어요! 상쾌한 날씨 ☀️")
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("❤️ ${10 + i}")
                        Text("💬 ${2 + i}")
                    }
                }
            }
        }
    }
}


@Composable
fun MyPageScreen(
    padding: PaddingValues,
    userName: String?,
    userProfile: String?,
    provider: String?,
    onLogout: () -> Unit
) {
    Box(
        Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(20.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            if (userProfile != null) {
                Image(
                    painter = rememberAsyncImagePainter(userProfile),
                    contentDescription = "Profile",
                    modifier = Modifier
                        .size(100.dp)
                        .background(Color.Gray, shape = MaterialTheme.shapes.large)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .background(Color.Gray, shape = MaterialTheme.shapes.large),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🙂", fontSize = MaterialTheme.typography.headlineMedium.fontSize)
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(userName ?: "로그인 정보 없음", style = MaterialTheme.typography.titleMedium)
            Text(provider?.uppercase() ?: "", color = Color.Gray)

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onLogout,
                colors = ButtonDefaults.buttonColors(Color.Red)
            ) {
                Text("로그아웃", color = Color.White)
            }
        }
    }

}

@Composable
private fun MenuItem(text: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}