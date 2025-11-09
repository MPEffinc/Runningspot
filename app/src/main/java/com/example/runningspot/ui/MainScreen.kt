package com.example.runningspot.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import coil.compose.rememberAsyncImagePainter
import com.example.runningspot.R
import com.example.runningspot.RunningActivity
import com.example.runningspot.ui.components.BottomNavBar
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.KakaoMapReadyCallback
import com.kakao.vectormap.LatLng
import com.kakao.vectormap.MapLifeCycleCallback
import com.kakao.vectormap.MapView
import com.kakao.vectormap.camera.CameraUpdateFactory
import com.kakao.vectormap.label.LabelOptions
import com.kakao.vectormap.label.LabelStyle
import com.kakao.vectormap.label.LabelStyles
import com.kakao.vectormap.route.RouteLine
import com.kakao.vectormap.route.RouteLineManager
import com.kakao.vectormap.route.RouteLineOptions
import com.kakao.vectormap.route.RouteLineSegment
import com.kakao.vectormap.route.RouteLineStyle
import com.kakao.vectormap.route.RouteLineStyles


// ===== 임시 DB: SharedPreferences + 내부파일(JSON) =====
private const val RUN_SP = "run_pref"
private const val RUN_KEY = "runs_json"

private data class RunSummaryRef(
    val distanceM: Double,
    val durationMs: Long,
    val endAt: Long,
    val fileName: String // 내부 저장소에 저장된 경로 파일명
)

private fun saveRunSummaryRef(ctx: android.content.Context, item: RunSummaryRef, maxKeep: Int = 200) {
    val sp = ctx.getSharedPreferences(RUN_SP, android.content.Context.MODE_PRIVATE)
    val old = org.json.JSONArray(sp.getString(RUN_KEY, "[]"))
    val arr = org.json.JSONArray().apply {
        put(org.json.JSONObject().apply {
            put("distanceM", item.distanceM)
            put("durationMs", item.durationMs)
            put("endAt", item.endAt)
            put("fileName", item.fileName)
        })
        for (i in 0 until kotlin.math.min(old.length(), maxKeep - 1)) put(old.getJSONObject(i))
    }
    sp.edit().putString(RUN_KEY, arr.toString()).apply()
}

private fun loadRunSummaryRefs(ctx: android.content.Context): List<RunSummaryRef> {
    val sp = ctx.getSharedPreferences(RUN_SP, android.content.Context.MODE_PRIVATE)
    val arr = org.json.JSONArray(sp.getString(RUN_KEY, "[]"))
    return buildList {
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            add(
                RunSummaryRef(
                    distanceM = o.optDouble("distanceM", 0.0),
                    durationMs = o.optLong("durationMs", 0L),
                    endAt = o.optLong("endAt", 0L),
                    fileName = o.optString("fileName", "")
                )
            )
        }
    }
}

private fun saveRunPathFile(ctx: android.content.Context, endAt: Long, path: List<Pair<Double, Double>>): String {
    val dir = java.io.File(ctx.filesDir, "runs").apply { mkdirs() }
    val name = "run_${endAt}.json"
    val file = java.io.File(dir, name)
    val arr = org.json.JSONArray()
    path.forEach { (lat, lng) -> arr.put(org.json.JSONObject().apply { put("lat", lat); put("lng", lng) }) }
    file.writeText(arr.toString())
    return name
}

private fun loadRunPathFile(ctx: android.content.Context, fileName: String): List<Pair<Double, Double>> {
    if (fileName.isBlank()) return emptyList()
    val file = java.io.File(java.io.File(ctx.filesDir, "runs"), fileName)
    if (!file.exists()) return emptyList()
    val arr = org.json.JSONArray(file.readText())
    return buildList {
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            add(o.optDouble("lat") to o.optDouble("lng"))
        }
    }
}


@Composable
fun MainScreen(
    userName: String?,
    userProfile: String?,
    provider: String?,
    onLogout: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(2) } // 기본 러닝 탭 선택

    val context = LocalContext.current

// 기록/통계 상태
    var showHistory by rememberSaveable { mutableStateOf(false) }
    var lastDistance by rememberSaveable { mutableStateOf<Double?>(null) }
    var lastDuration by rememberSaveable { mutableStateOf<Long?>(null) }
    var lastPath by remember { mutableStateOf<List<Pair<Double, Double>>>(emptyList()) }
    val runRefs = remember { mutableStateListOf<RunSummaryRef>() }

// 앱 시작 시 저장된 기록 읽어오기
    LaunchedEffect(Unit) {
        runRefs.clear()
        runRefs.addAll(loadRunSummaryRefs(context))
        runRefs.firstOrNull()?.let { r ->
            lastDistance = r.distanceM
            lastDuration = r.durationMs
            lastPath = loadRunPathFile(context, r.fileName)
        }
    }


    Scaffold(
        bottomBar = { BottomNavBar(selectedTab, onTabSelected = { selectedTab = it }) }
    ) { padding ->

        when (selectedTab) {
            0 -> InfoScreen(padding)
            1 -> {
            if (showHistory) {
                HistoryList(
                    padding = padding,
                    runs = runRefs,
                    onBack = { showHistory = false },
                    onSelect = { r ->
                        lastDistance = r.distanceM
                        lastDuration = r.durationMs
                        lastPath = loadRunPathFile(context, r.fileName)
                        showHistory = false
                    }
                )
            } else {
                StatsScreen(
                    padding = padding,
                    distance = lastDistance,
                    duration = lastDuration,
                    route = lastPath,
                    onShowHistory = { showHistory = true }
                )
            }
        }
            2 -> RunningScreen(
                padding = padding,
                onRunResult = { distance, duration, pathPairs ->
                    val endAt = System.currentTimeMillis()
                    // 1) 경로 파일 저장
                    val fileName = saveRunPathFile(context, endAt, pathPairs)
                    // 2) 요약 저장(SharedPreferences)
                    val ref = RunSummaryRef(distance, duration, endAt, fileName)
                    saveRunSummaryRef(context, ref)

                    // 3) 메모리 목록/프리뷰 갱신
                    runRefs.add(0, ref)
                    lastDistance = distance
                    lastDuration = duration
                    lastPath = pathPairs
                }
            )
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
fun RunningScreen(
    padding: PaddingValues,
    onRunResult: (Double, Long, List<Pair<Double, Double>>) -> Unit = { _, _, _ -> }
    ) {
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

            val dist = data.getDoubleExtra("runningDistance", Double.NaN)
            val time = data.getLongExtra("runningTime", -1L)
            val size = data.getIntExtra("pathSize", 0)
            val pathPairs = if (size > 1) {
                (0 until size).map { i ->
                    data.getDoubleExtra("lat_$i", 0.0) to data.getDoubleExtra("lng_$i", 0.0)
                }
            } else emptyList()

            if (!dist.isNaN() && time >= 0) {
                onRunResult(dist, time, pathPairs)
            }

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
fun StatsScreen(
    padding: PaddingValues,
    distance: Double?, duration: Long?,
    route: List<Pair<Double, Double>>,
    onShowHistory: () -> Unit = {}
) {

    val paceText = calcPace(distance ?: 0.0, duration ?: 0L)
        ?.let { formatPace(it) } ?: "-"

    val kcalText = when {
        distance != null && duration != null -> {
            val kcal = calcCalories(distance)
            "%.0f kcal".format(kcal)
        }
        else -> "-"
    }

    Column(
        Modifier.fillMaxSize().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("📊 러닝 통계", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))

        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            MapRoutePreview(
                path = route,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .background(Color(0xFFEFEFEF))
            )
            Spacer(Modifier.height(8.dp))
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("러닝 거리")
            Text((distance?.let { "%.2f km".format(it / 1000.0) } ?: "-"), fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(16.dp))
        Text("목표 수치 달성! 🎯", fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("평균 페이스")
                Text(paceText, fontWeight = FontWeight.Bold)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("러닝 시간")
                Text((duration?.let { formatDuration(it) } ?: "-"), fontWeight = FontWeight.Bold)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("칼로리")
                Text(kcalText, fontWeight = FontWeight.Bold)
            }
        }
    }

    Button(onClick = onShowHistory) { Text("기록 보기") }
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

// 맵 통계창 출력
@Composable
private fun MapRoutePreview(
    path: List<Pair<Double, Double>>,
    modifier: Modifier = Modifier,
    zoomLevel: Int = 15
) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    // Kakao MapView 준비
    val mapView = remember { MapView(context) }
    var kakaoMap by remember { mutableStateOf<KakaoMap?>(null) }

    // 라이프사이클 연동
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

    // 맵 준비 콜백
    val readyCb = remember(path) {
        object : KakaoMapReadyCallback() {
            override fun onMapReady(map: KakaoMap) {
                kakaoMap = map

                // 경로가 있으면 폴리라인 그리기
                if (path.size > 1) {
                    val routePts = path.map { LatLng.from(it.first, it.second) }
                    map.routeLineManager?.let { manager ->
                        val layer = manager.layer
                        val style = RouteLineStyle.from(8f, android.graphics.Color.BLUE)
                        val styles = RouteLineStyles.from(style)
                        val seg = RouteLineSegment.from(routePts).setStyles(styles)
                        val options = RouteLineOptions.from(seg)
                        layer.addRouteLine(options).show()
                    }

                    // 카메라를 경로 중앙으로 이동
                    val avgLat = path.map { it.first }.average()
                    val avgLng = path.map { it.second }.average()
                    val update = CameraUpdateFactory.newCenterPosition(LatLng.from(avgLat, avgLng))
                    map.moveCamera(update)
                    // 필요한 경우 확대/축소 레벨 조정
                    // map.setZoomLevel(zoomLevel) // SDK 버전에 따라 지원
                } else {
                    // 경로 없으면 기본 위치
                    val center = LatLng.from(0.0, 0.0)
                    map.moveCamera(CameraUpdateFactory.newCenterPosition(center))
                }
            }

            override fun getPosition(): LatLng = LatLng.from(0.0, 0.0)
            override fun getZoomLevel(): Int = zoomLevel
        }
    }

    // 맵 시작
    LaunchedEffect(mapView, path) {
        mapView.start(object : MapLifeCycleCallback() {
            override fun onMapDestroy() {}
            override fun onMapError(error: Exception?) { error?.printStackTrace() }
        }, readyCb)
    }

    // 실제 뷰 렌더
    AndroidView(
        modifier = modifier,
        factory = { mapView }
    )
}


// 시간 표시
private fun formatDuration(ms: Long): String {
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

// 페이스 계산
fun calcPace(distanceM: Double, durationMs: Long): Double? {
    if (distanceM < 50.0 || durationMs < 30_000L) return null // 정확도 올리기
    val distKm = distanceM / 1000.0
    val sec = durationMs / 1000.0
    if (distKm <= 0.0) return null
    return sec / distKm
}

fun formatPace(secPerKm: Double): String {
    val total = secPerKm.toInt()
    val m = total / 60
    val s = total % 60
    return "%d’%02d”/km".format(m, s)
}

// 칼로리 계산 (기본 몸무게: 70kg)
fun calcCalories(distanceM: Double, weightKg: Double = 70.0): Double {
    val distKm = distanceM / 1000.0
    return weightKg * distKm * 1.0
}

// 과거 기록 조회
@Composable
private fun HistoryList(
    padding: PaddingValues,
    runs: List<RunSummaryRef>,
    onBack: () -> Unit = {},
    onSelect: (RunSummaryRef) -> Unit = {}
) {
    Column(
        Modifier.fillMaxSize().padding(padding).padding(12.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = onBack) { Text("← 뒤로") }
            Text("러닝 기록", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Spacer(Modifier.width(1.dp))
        }
        Spacer(Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(runs.size) { idx ->
                val r = runs[idx]
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(r) },
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        val distanceKm = r.distanceM / 1000.0
                        val pace = calcPace(r.distanceM, r.durationMs)?.let { formatPace(it) } ?: "--"
                        Text(text = formatDate(r.endAt), fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        Text("거리 ${"%.2f".format(distanceKm)} km · 시간 ${formatDuration(r.durationMs)} · 페이스 $pace")
                    }
                }
            }
        }
    }
}

private fun formatDate(ms: Long): String {
    val sdf = java.text.SimpleDateFormat("yyyy.MM.dd HH:mm", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(ms))
}
