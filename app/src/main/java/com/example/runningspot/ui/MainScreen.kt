package com.example.runningspot.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.rememberAsyncImagePainter
import com.example.runningspot.CommunityActivity
import com.example.runningspot.R
import com.example.runningspot.RunningActivity
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
import com.kakao.vectormap.label.LabelTextStyle
import com.kakao.vectormap.label.LabelTextBuilder
import com.kakao.vectormap.route.RouteLine
import com.kakao.vectormap.route.RouteLineManager
import com.kakao.vectormap.route.RouteLineOptions
import com.kakao.vectormap.route.RouteLineSegment
import com.kakao.vectormap.route.RouteLineStyle
import com.kakao.vectormap.route.RouteLineStyles

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Matrix
import android.media.ExifInterface
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.TextButton
import androidx.compose.runtime.rememberCoroutineScope
import com.google.firebase.auth.FirebaseAuth
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.kakao.sdk.user.UserApiClient
import com.example.runningspot.data.CrewRepository
import kotlinx.coroutines.launch
import com.example.runningspot.data.repository.CrewPost
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.collectAsState
import com.example.runningspot.viewmodel.RouteViewModel
import com.example.runningspot.data.remote.NearbyRouteDto

import androidx.compose.material3.*
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.DirectionsRun

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

// 기록 삭제
private fun deleteRunSummaryRef(ctx: android.content.Context, target: RunSummaryRef) {
    val sp = ctx.getSharedPreferences(RUN_SP, android.content.Context.MODE_PRIVATE)
    val arr = org.json.JSONArray(sp.getString(RUN_KEY, "[]"))
    val newArr = org.json.JSONArray()

    for (i in 0 until arr.length()) {
        val o = arr.getJSONObject(i)
        // endAt 으로 동일 기록 찾기
        val endAt = o.optLong("endAt", 0L)
        if (endAt != target.endAt) {
            newArr.put(o)
        }
    }

    sp.edit().putString(RUN_KEY, newArr.toString()).apply()

    // 경로 파일도 같이 삭제
    if (target.fileName.isNotBlank()) {
        val dir = java.io.File(ctx.filesDir, "runs")
        val f = java.io.File(dir, target.fileName)
        if (f.exists()) {
            f.delete()
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
    val viewModel: RouteViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    var selectedTab by remember { mutableStateOf(2) } // 기본 러닝 탭 선택

    val context = LocalContext.current

// 기록/통계 상태
    var showHistory by rememberSaveable { mutableStateOf(false) }
    var lastDistance by rememberSaveable { mutableStateOf<Double?>(null) }
    var lastDuration by rememberSaveable { mutableStateOf<Long?>(null) }
    var lastPath by remember { mutableStateOf<List<Pair<Double, Double>>>(emptyList()) }
    val runRefs = remember { mutableStateListOf<RunSummaryRef>() }
    var showInfoOnMyPage by rememberSaveable { mutableStateOf(false) }

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
            0 -> {
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
                        },
                        onDelete = { r ->
                            // 1) 저장소에서 삭제
                            deleteRunSummaryRef(context, r)
                            // 2) 메모리 목록에서 삭제
                            runRefs.remove(r)

                            // 3) 통계 화면에 보여줄 마지막 기록 갱신
                            if (runRefs.isNotEmpty()) {
                                val first = runRefs.first()
                                lastDistance = first.distanceM
                                lastDuration = first.durationMs
                                lastPath = loadRunPathFile(context, first.fileName)
                            } else {
                                lastDistance = null
                                lastDuration = null
                                lastPath = emptyList()
                            }
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
            1 -> WeeklyStatsScreen(
                padding = padding,
                runs = runRefs
            )
            2 -> RunningScreen(
                padding = padding,
                viewModel = viewModel,
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
            3 -> CommunityScreen(padding, userName)
            4 -> {
                if (showInfoOnMyPage) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .padding(16.dp)
                    ) {
                        Column(
                            Modifier.fillMaxSize()
                        ) {
                            Button(onClick = { showInfoOnMyPage = false }) {
                                Text("← 뒤로")
                            }
                            Spacer(Modifier.height(12.dp))

                            InfoScreen(padding = PaddingValues(0.dp))
                        }
                    }
                } else {
                    MyPageScreen(
                        padding = padding,
                        userName = userName,
                        userProfile = userProfile,
                        provider = provider,
                        onLogout = onLogout,
                        onShowInfo = { showInfoOnMyPage = true }
                    )
                }
            }
        }
    }
}

var userMarkerImageUri by mutableStateOf<String?>(null)
fun getCircularBitmap(bitmap: Bitmap): Bitmap {
    val size = minOf(bitmap.width, bitmap.height)
    val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)

    val canvas = Canvas(output)
    val paint = Paint().apply { isAntiAlias = true }

    val path = Path().apply {
        addCircle(size / 2f, size / 2f, size / 2f, Path.Direction.CCW)
    }

    canvas.clipPath(path)
    canvas.drawBitmap(
        Bitmap.createScaledBitmap(bitmap, size, size, false),
        0f,
        0f,
        paint
    )
    return output
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunningScreen(
    padding: PaddingValues,
    viewModel: RouteViewModel,
    onRunResult: (Double, Long, List<Pair<Double, Double>>) -> Unit = { _, _, _ -> }
) {
    // 주변 루트 리스트 관찰
    val nearbyRoutes by viewModel.nearbyRoutes.collectAsState(initial = emptyList())

    val coroutineScope = rememberCoroutineScope() //
    var selectedRouteId by remember { mutableStateOf<Long?>(null) }

    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    val mapView = remember { MapView(context) }
    var kakaoMap by remember { mutableStateOf<KakaoMap?>(null) }
    var hasLocationPermission by remember { mutableStateOf(false) }
    var isRunning by remember { mutableStateOf(false) }
    val runningPath = remember { mutableStateListOf<LatLng>() }
    var currentRoute by remember { mutableStateOf<RouteLine?>(null) }
    var showRunningDialog by remember { mutableStateOf(false) }

    val sheetState = rememberStandardBottomSheetState(
        initialValue = SheetValue.PartiallyExpanded
    )
    val scaffoldState = rememberBottomSheetScaffoldState(sheetState)

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        hasLocationPermission =
            result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                    result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        tryInitCenter(context, fusedLocationClient, kakaoMap, hasLocationPermission)
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

                if (hasLocationPermission) {
                    getSingleFix(fusedLocationClient) { lat, lng ->
                        moveCameraTo(map, lat, lng)
                        updateCurrentLabel(context, map, lat, lng)

                        // 내 위치를 주변 루트를 달라고 요청
                        viewModel.loadNearbyRoutes(lat, lng)
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

    //nearbyRoutes 데이터를 받아오면 지도에 마커
    LaunchedEffect(nearbyRoutes, kakaoMap) {
        if (nearbyRoutes.isNotEmpty() && kakaoMap != null) {
            showNicknameMarkers(kakaoMap!!, nearbyRoutes)
        }
    }

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
                    updateCurrentLabel(context, map, latLng.latitude, latLng.longitude)
                    drawRunningPath(map, routeLineManager, runningPath, currentRoute) {
                        currentRoute = it
                    }
                }
            }
        }
    }
    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = 160.dp, // 하단 시트가 내려가 있을 때 보여줄 높이
        sheetContainerColor = Color.White,
        sheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        sheetShadowElevation = 20.dp,
        sheetContent = {
            // [하단 시트 내부: 추천 루트 리스트]
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .heightIn(min = 400.dp)
            ) {
                Spacer(modifier = Modifier.height(12.dp))
                // 시트 핸들러
                Box(Modifier.width(40.dp).height(4.dp).background(Color(0xFFE0E0E0), CircleShape).align(Alignment.CenterHorizontally))
                Spacer(modifier = Modifier.height(24.dp))

                Text("주변 추천 루트", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = Color.Black)
                Spacer(modifier = Modifier.height(16.dp))

                Box(modifier = Modifier.weight(1f)) {
                    NearbyRoutesSection(
                        viewModel = viewModel,
                        onRouteClick = { id -> selectedRouteId = id

                            coroutineScope.launch {
                                scaffoldState.bottomSheetState.partialExpand()
                            }
                            // TODO:해당 좌표로 지도 이동 로직추가
                        }
                    )
                }
                Spacer(modifier = Modifier.height(100.dp)) // 하단 내비게이션 바 공간 확보
            }
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding) // MainScreen의 Scaffold 패딩 적용
        ) {
            // 카카오맵 배경
            AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())

            // 상단 검색바
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .padding(top = 16.dp)
                    .align(Alignment.TopCenter),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(8.dp)
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("어디서 달리고 싶으신가요?", color = Color.Gray)
                }
            }

            // 우측 하단 플로팅 버튼들 (시트 높이만큼 bottom 여백을 주어 안 가려지게 함)
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 180.dp, end = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.End
            ) {
                // 현재 위치 버튼
                SmallFloatingActionButton(
                    onClick = {
                        val map = kakaoMap ?: return@SmallFloatingActionButton
                        if (hasLocationPermission) {
                            getSingleFix(fusedLocationClient) { lat, lng ->
                                moveCameraTo(map, lat, lng)
                                updateCurrentLabel(context, map, lat, lng)
                                viewModel.loadNearbyRoutes(lat, lng)
                            }
                        }
                    },
                    containerColor = Color.White,
                    contentColor = Color(0xFF6750A4),
                    shape = CircleShape
                ) {
                    Icon(Icons.Default.MyLocation, contentDescription = null)
                }

                // 러닝 시작 버튼
                ExtendedFloatingActionButton(
                    onClick = {
                        val intent = Intent(context, RunningActivity::class.java)
                        launcher.launch(intent)
                    },
                    containerColor = Color(0xFF6750A4),
                    contentColor = Color.White,
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("러닝 시작", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// 주변 루트 마커 띄워주는 함수
private fun showNicknameMarkers(kakaoMap: KakaoMap, routes: List<com.example.runningspot.data.remote.NearbyRouteDto>) {
    val labelManager = kakaoMap.labelManager ?: return
    val layer = labelManager.layer ?: return

    // 기존에 그려진 추천 마커들이 있다면 싹 지우고 새로 그리기
    layer.removeAll()

    kakaoMap.setOnLabelClickListener { _, _, label ->
        val clickedRouteId = label.tag as? Long
        if (clickedRouteId != null) {
            println("클릭된 루트 ID: $clickedRouteId")
        }
        true
    }

    routes.forEach { route ->
        val pos = LatLng.from(route.start_lat, route.start_lng)

        val style = LabelStyle.from(com.example.runningspot.R.drawable.ic_launcher_foreground) // 👈 본인 프로젝트의 아이콘으로 맞춰주세요
            .setTextStyles(LabelTextStyle.from(35, android.graphics.Color.BLUE))

        val nicknameText = route.nickname ?: "이름 없음"

        val options = LabelOptions.from(pos)
            .setStyles(style)
            .setTexts(LabelTextBuilder().setTexts(nicknameText))
            .setTag(route.id)

        layer.addLabel(options)
    }
}

@Composable
fun RecommendedRouteCard(route: Any, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F3F5)),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(60.dp).background(Color.LightGray, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.DirectionsRun, contentDescription = null, tint = Color.White)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text("인기 추천 경로", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("거리 미정 · 약 30분", fontSize = 13.sp, color = Color.Gray)
            }
        }
    }
}

private fun tryInitCenter(
    context : Context,
    fused: FusedLocationProviderClient,
    map: KakaoMap?,
    hasPermission: Boolean
) {
    if (!hasPermission || map == null) return
    getSingleFix(fused) { lat, lng ->
        moveCameraTo(map, lat, lng)
        updateCurrentLabel(context, map, lat, lng)
    }
}

private fun moveCameraTo(map: KakaoMap, lat: Double, lng: Double) {
    val pos = LatLng.from(lat, lng)
    val update = CameraUpdateFactory.newCenterPosition(pos)
    map.moveCamera(update)
}

private fun updateCurrentLabel(context : Context, map: KakaoMap, lat: Double, lng: Double) {
    val pos = LatLng.from(lat, lng)
    val labelManager = map.getLabelManager()
    val layer = labelManager?.layer

    layer?.removeAll()


    val bitmap: Bitmap = try {
        if (userMarkerImageUri.isNullOrBlank()) {
            // 기본 리소스 사용
            BitmapFactory.decodeResource(context.resources, R.drawable.arrow)
        } else {
            val uri = Uri.parse(userMarkerImageUri)
            // 1) 먼저 이미지 크기(메타) 확인
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri).use { ins ->
                BitmapFactory.decodeStream(ins, null, boundsOpts)
            }

            // 2) 적절한 inSampleSize 계산 (긴 변을 maxSize로 맞춤)
            val maxSize = 300 // 마커용 긴 변(px) — 필요시 변경
            var sample = 1
            val (ow, oh) = boundsOpts.outWidth to boundsOpts.outHeight
            if (ow > 0 && oh > 0) {
                var halfW = ow / 2
                var halfH = oh / 2
                while (halfW / sample > maxSize || halfH / sample > maxSize) {
                    sample *= 2
                }
            }

            // 3) 실제 디코딩 (샘플링 적용)
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            var decoded: Bitmap? = null
            context.contentResolver.openInputStream(uri).use { ins ->
                decoded = BitmapFactory.decodeStream(ins, null, opts)
            }
            var bmp = decoded ?: BitmapFactory.decodeResource(context.resources, R.drawable.arrow)

            // 4) EXIF 회전 보정 (안전하게)
            bmp = try {
                context.contentResolver.openInputStream(uri).use { ins ->
                    val exif = ExifInterface(ins!!)
                    val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                    val matrix = Matrix()
                    when (orientation) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                        else -> {}
                    }
                    if (!matrix.isIdentity) {
                        Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
                    } else bmp
                }
            } catch (e: Exception) {
                // EXIF 읽기 실패해도 원본 사용
                bmp
            }

            // 5) 최종 크기 보정(정확히 마커용 사이즈로 스케일)
            val target = 200 // 최종 마커 사이즈(px) — 필요하면 변경
            val scaled = Bitmap.createScaledBitmap(bmp, target, target, true)
            // 6) 원형으로 잘라주기
            getCircularBitmap(scaled)
        }
    } catch (e: Exception) {
        Log.e("UPDATE_LABEL", "bitmap load failed", e)
        BitmapFactory.decodeResource(context.resources, R.drawable.arrow)
    }

    // 라벨 스타일/추가 (기존 방식과 동일)
    val style = LabelStyle.from(bitmap)
    val styles = labelManager?.addLabelStyles(LabelStyles.from(style))
    val options = LabelOptions.from(pos).setStyles(styles)
    layer?.addLabel(options)
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
        Spacer(Modifier.height(30.dp))
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

        Spacer(Modifier.height(30.dp))

        Button(onClick = onShowHistory) { Text("기록 보기") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityScreen(padding: PaddingValues, userName: String?) {

    val context = LocalContext.current
    val prefs = context.getSharedPreferences("community_prefs", Context.MODE_PRIVATE)
    val repo = remember { com.example.runningspot.data.CommunityPostRepository() }
    val scope = rememberCoroutineScope()


    var refreshKey by remember { mutableStateOf(0) }
    var posts by remember { mutableStateOf<List<Post>>(emptyList()) }

    val crewRepo = remember { CrewRepository() }
    var crews by remember { mutableStateOf<List<CrewPost>>(emptyList()) }

    var selectedTab by remember { mutableStateOf(0) }
    val tabTitles = listOf("커뮤니티", "크루")
    LaunchedEffect(refreshKey) {

        // Firestore에서 최신 글 읽기
        val remote = repo.fetchLatestPosts(50)

        // Firestore -> UI Post로 변환 (title이 Firestore에 없어서 임시로 content 앞부분을 title로 사용)
        val remotePostsForUi: List<Post> = remote.map { (docId, p) ->
            Post(
                id = docId.hashCode(),              // ⚠️ 임시: UI가 Int id라서 docId를 hash로 변환
                docId = docId,
                title = p.content.take(18),         // ⚠️ 임시 title(나중에 Firestore에 title 필드 추가 추천)
                authorName = p.userName.ifBlank { "익명" },
                content = p.content,
                likes = p.likeCount.toInt(),
                comments = p.commentCount.toInt(),
                imageRes = R.drawable.sea,          // Firestore는 imageRes가 없으니 임시 기본 이미지
                imageUri = p.imageUrls.firstOrNull() // Firestore imageUrls[0]를 썸네일로
            )
        }

        // “기존 로컬글 + Firestore글” 합치기
        posts = remotePostsForUi
        crews = crewRepo.fetchCrews()
    }

    // 돌아올 때 새로고침
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshKey++
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    // 전체 화면
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
    ) {

        Column(modifier = Modifier.fillMaxSize()) {

            // 탭
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color(0xFFF7F4FF),
                indicator = { tabPos ->
                    TabRowDefaults.Indicator(
                        modifier = Modifier
                            .tabIndicatorOffset(tabPos[selectedTab])
                            .height(3.dp),
                        color = Color(0xFF6C4CD3)
                    )
                }
            ) {
                tabTitles.forEachIndexed { i, title ->
                    Tab(
                        selected = selectedTab == i,
                        onClick = { selectedTab = i },
                        text = {
                            Text(
                                text = title,
                                fontWeight = if (selectedTab == i) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == i) Color(0xFF6C4CD3) else Color.Gray
                            )
                        }
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            fun openChat(crewId: String) {
                val intent = Intent(context, CommunityActivity::class.java)
                intent.putExtra("isChatMode", true)
                intent.putExtra("crewId", crewId)
                context.startActivity(intent)
            }
            // 크루 탭
            if (selectedTab == 1) {

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    items(crews, key = { it.id }) { crew ->

                        CrewPostCard(
                            crew = crew,
                            onClick = {
                                scope.launch {
                                    val ok = crewRepo.isMember(crew.id)
                                    if (ok) openChat(crew.id)
                                }
                            },
                            onJoin = {
                                scope.launch {
                                    try {
                                        crewRepo.joinCrew(crew.id)

                                        // 🔄 다시 불러오기
                                        crews = crewRepo.fetchCrews()

                                    } catch (e: Exception) {
                                        Toast.makeText(
                                            context,
                                            e.message ?: "참여 실패",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            }
                        )
                    }
                }
            } else {

                // 커뮤니티 탭 리스트
                LazyColumn(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    items(posts, key = { it.id }) { post ->

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val intent = Intent(context, CommunityActivity::class.java)
                                    intent.putExtra("postId", post.id)
                                    intent.putExtra("title", post.title)
                                    intent.putExtra("authorName", post.authorName)
                                    intent.putExtra("content", post.content)
                                    intent.putExtra("likes", post.likes)
                                    intent.putExtra("comments", post.comments)
                                    intent.putExtra("imageRes", post.imageRes)
                                    intent.putExtra("distanceKm", post.distanceKm ?: Double.NaN)
                                    intent.putExtra("durationText", post.durationText ?: "")
                                    intent.putExtra("pace", post.pace ?: "")
                                    intent.putExtra("calories", post.calories ?: Double.NaN)
                                    intent.putExtra("userName", userName)
                                    intent.putExtra("imageUri", post.imageUri)
                                    intent.putExtra("docId", post.docId)
                                    context.startActivity(intent)
                                },
                            shape = RoundedCornerShape(18.dp),
                            elevation = CardDefaults.cardElevation(4.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F4FF))
                        ) {

                            Column(modifier = Modifier.padding(16.dp)) {

                                // ----- 이미지 -----
                                if (post.imageUri?.isNotBlank() == true) {
                                    Image(
                                        painter = rememberAsyncImagePainter(Uri.parse(post.imageUri)),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 4.dp, bottom = 4.dp)
                                            .padding(horizontal = 4.dp, vertical = 4.dp)
                                            .aspectRatio(1f)
                                            .clip(RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Image(
                                        painter = painterResource(id = post.imageRes),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 4.dp, bottom = 4.dp)
                                            .padding(horizontal = 4.dp, vertical = 4.dp)
                                            .aspectRatio(1f)
                                            .clip(RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                                //작성자 이름 (작은 폰트)
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    text = post.authorName,
                                    fontSize = 13.sp,
                                    color = Color(0xFF222222),
                                    fontWeight = FontWeight.Medium
                                )

                                Spacer(Modifier.height(4.dp))

                                // 제목
                                Text(
                                    post.title,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF3C2A7D)
                                )

                                Spacer(Modifier.height(6.dp))

                                // 본문
                                Text(
                                    post.content,
                                    fontSize = 15.sp,
                                    color = Color.DarkGray,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Spacer(Modifier.height(12.dp))

                                // ----- 거리 / 페이스 UI -----
                                if (post.distanceKm != null && post.pace != null) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color(0xFFEDE7F6))
                                            .padding(horizontal = 20.dp, vertical = 12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            "거리 ${"%.2f".format(post.distanceKm)}km",
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            "페이스 ${post.pace}",
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    Spacer(Modifier.height(12.dp))
                                }

                                Spacer(Modifier.height(12.dp))

                                // ----- 좋아요 & 댓글 -----
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Favorite,
                                            contentDescription = null,
                                            tint = Color(0xFFE57373)
                                        )
                                        Text("${post.likes}", fontSize = 15.sp)
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.ChatBubbleOutline,
                                            contentDescription = null,
                                            tint = Color(0xFF7986CB)
                                        )
                                        Text("${post.comments}", fontSize = 15.sp)
                                    }
                                }

                                Spacer(Modifier.height(12.dp))
                            }
                        }
                    }
                }
            }
        }

        var showWritePicker by remember { mutableStateOf(false) }

// FAB
        FloatingActionButton(
            onClick = { showWritePicker = true },
            containerColor = Color(0xFF6C4CD3),
            shape = CircleShape,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "추가", tint = Color.White)
        }

// ✅ 선택 다이얼로그
        if (showWritePicker) {
            AlertDialog(
                onDismissRequest = { showWritePicker = false },
                title = { Text("무엇을 작성할까요?") },
                text = {
                    Column {
                        Button(
                            onClick = {
                                showWritePicker = false
                                val intent = Intent(context, CommunityActivity::class.java)
                                intent.putExtra("isWriteMode", true)
                                intent.putExtra("writeType", "post")   // ✅ 추가
                                intent.putExtra("userName", userName)
                                context.startActivity(intent)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("피드 쓰기") }

                        Spacer(Modifier.height(10.dp))

                        Button(
                            onClick = {
                                showWritePicker = false
                                val intent = Intent(context, CommunityActivity::class.java)
                                intent.putExtra("isWriteMode", true)
                                intent.putExtra("writeType", "crew")   // ✅ 추가
                                intent.putExtra("userName", userName)
                                context.startActivity(intent)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("크루 모집글 쓰기") }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showWritePicker = false }) { Text("취소") }
                }
            )
        }
    }
}

@Composable
fun CrewPostCard(
    crew: CrewPost,
    onClick: () -> Unit,
    onJoin: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(crew.title, fontWeight = FontWeight.Bold)
            Text(crew.location, color = Color.Gray)

            Spacer(Modifier.height(8.dp))

            Text("${crew.currentMembers}/${crew.maxMembers}")

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = onJoin,
                enabled = crew.currentMembers < crew.maxMembers
            ) {
                Text(
                    if (crew.currentMembers >= crew.maxMembers)
                        "모집 마감"
                    else
                        "참여하기"
                )
            }
        }
    }
}

data class Crew(
    val id: Int,
    val name: String,
    val location: String,
    val description: String,
    val likes: Int,
    val comments: Int,
    val profileRes: Int
)
data class Post(
    val title: String,
    val id: Int,
    val authorName: String,
    val content: String,
    var likes: Int = 0,
    var comments: Int = 0,
    val imageRes: Int,
    val imageUri: String? = null,
    val distanceKm: Double? = null,
    val pace: String? = null,
    val durationText: String? = null,
    val calories: Double? = null,
    val docId: String? = null
)
fun logoutAll(
    context: Context,
    provider: String?,
    onLoggedOut: () -> Unit
) {
    FirebaseAuth.getInstance().signOut()
    when (provider?.lowercase()) {
        "google" -> {
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestIdToken(context.getString(R.string.default_web_client_id))
                .build()

            GoogleSignIn.getClient(context, gso)
                .signOut()
                .addOnCompleteListener { onLoggedOut() }
        }
        "kakao" -> {
            UserApiClient.instance.logout { _ ->
                onLoggedOut()
            }
        }
        else -> {
            onLoggedOut()
        }
    }
}
@Composable
fun MyPageScreen(
    padding: PaddingValues,
    userName: String?,
    userProfile: String?,
    provider: String?,
    onLogout: () -> Unit,
    onShowInfo: () -> Unit
) {
    val context = LocalContext.current
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedImageUri = uri // 사진을 고르면 여기에 저장됨
            Toast.makeText(context, "사진이 선택되었습니다!", Toast.LENGTH_SHORT).show()
        }
    }
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
                onClick = { logoutAll(context, provider) { onLogout()} },
                colors = ButtonDefaults.buttonColors(Color.Red)
            ) {
                Text("로그아웃", color = Color.White)
            }

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = onShowInfo
            ) {
                Text("앱 정보")
            }

            if (selectedImageUri != null) {
                Text("선택된 이미지 경로: $selectedImageUri", fontSize = 12.sp)
            } else {
                Image(
                    painter = painterResource(id = R.drawable.ic_launcher_foreground),
                    contentDescription = "기본 프로필",
                    modifier = Modifier.size(100.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = {
                photoPickerLauncher.launch("image/*") // "이미지 파일만 보여줘" 라는 뜻
            }) {
                Text("프로필(마커) 사진 변경")
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
    onSelect: (RunSummaryRef) -> Unit = {},
    onDelete: (RunSummaryRef) -> Unit = {}
) {
    val context = LocalContext.current
    val viewModel: com.example.runningspot.viewmodel.RouteViewModel =
        androidx.lifecycle.viewmodel.compose.viewModel()

    var showUploadDialog by remember { mutableStateOf(false) }
    var uploadTarget by remember { mutableStateOf<RunSummaryRef?>(null) }
    var uploadTitle by remember { mutableStateOf("") }
    var uploadVisibility by remember { mutableStateOf("PUBLIC") }

    val createResult by viewModel.createResult.collectAsState()
    val error by viewModel.error.collectAsState()

    LaunchedEffect(createResult) {
        if (createResult != null) {
            Toast.makeText(context, "✅ 루트 업로드 성공! id=${createResult}", Toast.LENGTH_SHORT).show()
            // 필요하면 여기서 createResult 초기화 메서드 만들어서 초기화해도 됨
            showUploadDialog = false
            uploadTarget = null
        }
    }

    LaunchedEffect(error) {
        if (error != null) {
            Toast.makeText(context, "❌ 업로드 실패: $error", Toast.LENGTH_SHORT).show()
        }
    }

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

        val totalDistanceKm = runs.sumOf { it.distanceM } / 1000.0
        val totalDurationMs = runs.sumOf { it.durationMs }
        val totalCalories = runs.sumOf { calcCalories(it.distanceM) }

        if (runs.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    Text("요약", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text("총 러닝 횟수: ${runs.size}회")
                    Text("총 거리: ${"%.1f".format(totalDistanceKm)} km")
                    Text("총 시간: ${formatDuration(totalDurationMs)}")
                    Text("총 소모 칼로리: ${"%.0f".format(totalCalories)} kcal")
                }
            }
        }

        if (runs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "아직 저장된 러닝 기록이 없어요.\n첫 러닝을 시작해 보세요!",
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        } else {
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
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            Column(
                                modifier = Modifier.align(Alignment.TopStart)
                            ) {
                                val distanceKm = r.distanceM / 1000.0
                                val pace =
                                    calcPace(r.distanceM, r.durationMs)?.let { formatPace(it) }
                                        ?: "--"

                                Text(text = formatDate(r.endAt), fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(4.dp))
                                Text("거리 ${"%.2f".format(distanceKm)} km · 시간 ${formatDuration(r.durationMs)} · 페이스 $pace")
                            }
                            Row(
                                modifier = Modifier.align(Alignment.BottomEnd),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "공유",
                                    color = Color(0xFF1E88E5),
                                    modifier = Modifier.clickable {
                                        // 업로드 대상 선택 + 다이얼로그 열기
                                        uploadTarget = r
                                        uploadTitle = r.titleOrDefault()
                                        uploadVisibility = "PUBLIC"
                                        showUploadDialog = true
                                    }
                                )
                                Text(
                                    text = "삭제",
                                    color = Color.Red,
                                    modifier = Modifier.clickable { onDelete(r) }
                                )
                            }

                        }
                    }
                }
            }
        }
    }
    if (showUploadDialog && uploadTarget != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showUploadDialog = false },
            title = { Text("루트 업로드") },
            text = {
                Column {
                    OutlinedTextField(
                        value = uploadTitle,
                        onValueChange = { uploadTitle = it },
                        label = { Text("루트 제목") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))

                    Text("공개 범위", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = if (uploadVisibility == "PUBLIC") "✅ 공개" else "공개",
                            modifier = Modifier.clickable { uploadVisibility = "PUBLIC" }
                        )
                        Text(
                            text = if (uploadVisibility == "PRIVATE") "✅ 비공개" else "비공개",
                            modifier = Modifier.clickable { uploadVisibility = "PRIVATE" }
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val target = uploadTarget ?: return@Button

                    // 1) 로컬 파일에서 경로 읽기
                    val pairs = loadRunPathFile(context, target.fileName)
                    if (pairs.size < 2) {
                        Toast.makeText(context, "경로가 없어서 업로드할 수 없어요.", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    // 2) points 만들기
                    val points = pairs.mapIndexed { idx, p ->
                        com.example.runningspot.data.remote.RoutePointDto(
                            seq = idx,
                            lat = p.first,
                            lng = p.second
                        )
                    }

                    val start = pairs.first()
                    val end = pairs.last()

                    // 3) CreateRouteRequest 구성
                    val body = com.example.runningspot.data.remote.CreateRouteRequest(
                        title = uploadTitle.ifBlank { target.titleOrDefault() },
                        distance_m = target.distanceM,
                        start_lat = start.first,
                        start_lng = start.second,
                        end_lat = end.first,
                        end_lng = end.second,
                        visibility = uploadVisibility,
                        points = points
                    )

                    // 4) 서버 업로드 호출
                    viewModel.createRoute(body)
                }) { Text("업로드") }
            },
            dismissButton = {
                Button(onClick = { showUploadDialog = false }) { Text("취소") }
            }
        )
    }
}
private fun RunSummaryRef.titleOrDefault(): String {
    return "내 러닝 루트 ${formatDate(endAt)}"
}

private fun formatDate(ms: Long): String {
    val sdf = java.text.SimpleDateFormat("yyyy.MM.dd HH:mm", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(ms))
}

@Composable
private fun WeeklyStatsScreen(
    padding: PaddingValues,
    runs: List<RunSummaryRef>,
    viewModel: RouteViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    var selectedRouteId by rememberSaveable { mutableStateOf<Long?>(null) }

    // 상세 화면으로 전환
    if (selectedRouteId != null) {
        RouteDetailScreen(
            padding = padding,
            routeId = selectedRouteId!!,
            onBack = { selectedRouteId = null }
        )
        return
    }

    val now = System.currentTimeMillis()
    val dayMs = 24L * 60L * 60L * 1000L
    val oneWeekAgo = now - 6L * dayMs

    val cal = java.util.Calendar.getInstance()
    // 캘린더: 하루 단위로 자르기
    fun normalizeToDayStart(timeMs: Long): Long {
        cal.timeInMillis = timeMs
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    // 최근 1주일만 필터
    val weekRuns = runs.filter { it.endAt >= oneWeekAgo }

    // 날짜별 총 거리(km)
    val groupedByDayKm: Map<Long, Double> = weekRuns
        .groupBy { normalizeToDayStart(it.endAt) }
        .mapValues { (_, list) -> list.sumOf { it.distanceM } / 1000.0 }

    // 날짜별 총 시간(ms)
    val groupedByDayDuration: Map<Long, Long> = weekRuns
        .groupBy { normalizeToDayStart(it.endAt) }
        .mapValues { (_, list) -> list.sumOf { it.durationMs } }

    val dateFormat = java.text.SimpleDateFormat("MM/dd", java.util.Locale.getDefault())

    data class DayStat(
        val dayStartMs: Long,
        val label: String,
        val valueKm: Double,
        val kcal: Double,
        val durationMs: Long
    )

    // 최근 7일(과거→오늘 순) 리스트
    val days: List<DayStat> = (0..6).map { offset ->
        val dayStart = normalizeToDayStart(oneWeekAgo + offset * dayMs)

        val km = groupedByDayKm[dayStart] ?: 0.0
        val durationMs = groupedByDayDuration[dayStart] ?: 0L
        val kcal = if (km > 0.0) calcCalories(km * 1000.0) else 0.0

        DayStat(
            dayStartMs = dayStart,
            label = dateFormat.format(java.util.Date(dayStart)),
            valueKm = km,
            kcal = kcal,
            durationMs = durationMs
        )
    }

    val maxValueKm = days.maxOfOrNull { it.valueKm } ?: 0.0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(16.dp)
    ) {
        Text("📈 최근 1주일 러닝 기록", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))

        if (maxValueKm <= 0.0) {
            // 최근 1주일 기록 없음
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "최근 1주일 동안 저장된 러닝 기록이 없어요.\n러닝을 시작하고 다시 확인해 보세요!",
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        } else {
            // 막대 그래프 (거리 기준)
            androidx.compose.foundation.Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
            ) {
                val barCount = days.size
                val maxVal = maxValueKm.toFloat()

                val barWidth = size.width / (barCount * 1.7f)
                val barSpace = barWidth * 0.8f
                val chartHeight = size.height * 0.8f

                days.forEachIndexed { index, day ->
                    val value = day.valueKm.toFloat()
                    if (value <= 0f) return@forEachIndexed

                    val ratio = value / maxVal
                    val barHeight = chartHeight * ratio

                    val xCenter = barWidth / 2f +
                            index * (barWidth + barSpace)
                    val top = size.height - barHeight

                    drawRect(
                        color = Color(0xFF4CAF50),
                        topLeft = androidx.compose.ui.geometry.Offset(
                            xCenter - barWidth / 2f,
                            top
                        ),
                        size = androidx.compose.ui.geometry.Size(
                            barWidth,
                            barHeight
                        )
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // x축 라벨 (날짜 + km + kcal + 시간)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                days.forEach { day ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(day.label, fontSize = 11.sp)

                        if (day.valueKm > 0.0) {
                            Text(
                                "%.1f km".format(day.valueKm),
                                fontSize = 10.sp,
                                color = Color.Black
                            )
                            Text(
                                formatDuration(day.durationMs),
                                fontSize = 10.sp,
                                color = Color.Black
                            )
                            Text(
                                "%.0f kcal".format(day.kcal),
                                fontSize = 10.sp,
                                color = Color.Black
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // 요약 정보
        val totalKm = days.sumOf { it.valueKm }
        val totalKcal = days.sumOf { it.kcal }
        val totalRuns = weekRuns.size
        val totalDurationMs = days.sumOf { it.durationMs }

        Text("요약", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
        Spacer(Modifier.height(8.dp))
        Text("최근 1주일 총 러닝 횟수: ${totalRuns}회")
        Text("최근 1주일 총 거리: ${"%.1f".format(totalKm)} km")
        Text("최근 1주일 총 러닝 시간: ${formatDuration(totalDurationMs)}")
        Text("최근 1주일 총 소모 칼로리: ${"%.0f".format(totalKcal)} kcal")

        NearbyRoutesSection(
            viewModel = viewModel,
            onRouteClick = { id -> selectedRouteId = id }
        )
    }
}
