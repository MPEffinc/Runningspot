package com.example.runningspot.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Looper
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
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.integerArrayResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.rememberAsyncImagePainter
import com.example.runningspot.CommunityActivity
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
import org.json.JSONArray
import org.json.JSONObject
import kotlin.String
import kotlin.jvm.java
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.ui.text.style.TextOverflow
import com.example.runningspot.loadComments

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
            3 -> CommunityScreen(padding,userName)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityScreen(padding: PaddingValues, userName: String?) {

    val context = LocalContext.current
    val prefs = context.getSharedPreferences("community_prefs", Context.MODE_PRIVATE)

    var refreshKey by remember { mutableStateOf(0) }
    val posts by remember(refreshKey) { mutableStateOf(loadPosts(prefs)) }

    var selectedTab by remember { mutableStateOf(0) }
    val tabTitles = listOf("커뮤니티", "크루")

    // 돌아올 때 새로고침
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshKey++
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }
    val crewList = listOf(
        Crew(
            id = 1,
            name = "Crew Momentum",
            location = "Songpa-dong, Incheon",
            description = "Momentum 팀 모임에서 같이 뛰실 분들을 모집합니다! 인천팀 실력이 아니더라도 환영! 저녁에 함께 달리고 싶으신 분들 위주!",
            likes = 3,
            comments = 12,
            profileRes = R.drawable.tom1
        ),
        Crew(
            id = 2,
            name = "주호준",
            location = "Songpa-dong, Incheon",
            description = "Shirokuro 출신! 8시 운동 세트도 바쁘고 같이 뛰실 분도 없나요? 초보 환영! 저도 초보입니다.",
            likes = 1,
            comments = 2,
            profileRes = R.drawable.tom2
        ),
        Crew(
            id = 3,
            name = "Crew UNiverse",
            location = "Songpa-dong, Incheon",
            description = "UNiverse 러닝 좋아하시는 분들 모이세요! 너무 쉬운 샌티드 코스 준비완료!",
            likes = 4,
            comments = 10,
            profileRes = R.drawable.tom3
        )
    )

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

            // 크루 탭
            if (selectedTab == 1) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    items(crewList) { crew ->
                        CrewCard(crew = crew) {
                            val intent = Intent(context, CommunityActivity::class.java)
                            intent.putExtra("crewName", crew.name)
                            context.startActivity(intent)
                        }
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
                                    intent.putExtra("userName", userName)
                                    intent.putExtra("imageUri", post.imageUri)
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
                                            .padding(top = 20.dp, bottom = 20.dp)
                                            .padding(horizontal = 20.dp)
                                            .aspectRatio(16f / 9f)
                                            .clip(RoundedCornerShape(14.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Image(
                                        painter = painterResource(id = post.imageRes),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 20.dp, bottom = 20.dp)
                                            .padding(horizontal = 20.dp)
                                            .aspectRatio(16f / 9f)
                                            .clip(RoundedCornerShape(14.dp)),
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
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFFEDE7F6))
                                        .padding(horizontal = 20.dp, vertical = 14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("거리 7.2km", fontWeight = FontWeight.SemiBold)
                                    Text("페이스 5'10''/km", fontWeight = FontWeight.SemiBold)
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

                                val previewComments =
                                    loadComments(prefs, post.id).take(2)

                                if (previewComments.isNotEmpty()) {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        previewComments.forEach { (_, comment) ->
                                            Text("• $comment", color = Color.DarkGray)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // FAB
        FloatingActionButton(
            onClick = {
                val intent = Intent(context, CommunityActivity::class.java)
                intent.putExtra("userName", userName)
                intent.putExtra("isWriteMode", true)
                context.startActivity(intent)
            },
            containerColor = Color(0xFF6C4CD3),
            shape = CircleShape,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "추가", tint = Color.White)
        }
    }
}
@Composable
fun CrewCard(crew: Crew, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp)) {

            // 프로필 원형 이미지
            Image(
                painter = painterResource(id = crew.profileRes),
                contentDescription = null,
                modifier = Modifier
                    .size(55.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {

                Text(
                    crew.name,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF222222)
                )

                Text(
                    crew.location,
                    fontSize = 13.sp,
                    color = Color.Gray
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    crew.description,
                    fontSize = 14.sp,
                    color = Color.DarkGray,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(10.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Favorite,
                            contentDescription = null,
                            tint = Color(0xFFE57373)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("${crew.likes}")
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.ChatBubbleOutline,
                            contentDescription = null,
                            tint = Color(0xFF7986CB)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("${crew.comments}")
                    }
                }
            }
        }
    }
}
fun loadPosts(prefs: SharedPreferences): List<Post> {
    fun getSavedLikes(postId: Int) = prefs.getInt("likes_$postId", 0)
    fun getSavedComments(postId: Int) = prefs.getInt("comments_$postId", 0)

    val defaultPosts = listOf(
        Post(
            id = 1,
            title = "옛날 생각이 나는 러닝루트",
            authorName = "김민주",
            content = "오늘 4km 뛰었어요! 상쾌한 날씨 🌞",
            likes = getSavedLikes(1),
            comments = getSavedComments(1),
            imageRes = R.drawable.jeju
        ),
        Post(
            id = 2,
            title = "도심 속 러닝 코스 추천",
            authorName = "정민석",
            content = "오늘 5km 뛰었어요! 시원한 바람 🍃",
            likes = getSavedLikes(2),
            comments = getSavedComments(2),
            imageRes = R.drawable.busan
        ),
        Post(
            id = 3,
            title = "겨울 러닝도 즐겁게!",
            authorName = "남가을",
            content = "오늘 6km 뛰었어요! 하늘이 맑아요 🌤",
            likes = getSavedLikes(3),
            comments = getSavedComments(3),
            imageRes = R.drawable.sea
        )
    )

    // 저장된 사용자 게시글 불러오기
    val savedJson = prefs.getString("user_posts", "[]")
    val jsonArray = JSONArray(savedJson)
    val newPosts = List(jsonArray.length()) { i ->
        val obj = jsonArray.getJSONObject(i)
        val hashtagsString = obj.optString("hashtags", "")
        val hashtags = hashtagsString.split(",").map { it.trim().removePrefix("#") }
        Post(
            id = obj.getInt("id"),
            title = obj.optString("title", "제목 없음"),
            authorName = obj.optString("author", "익명"),
            content = obj.optString("content", ""),
            likes = getSavedLikes(obj.getInt("id")),
            comments = getSavedComments(obj.getInt("id")),
            imageRes = R.drawable.sea,
            imageUri = obj.optString("imageUri", null),
        )
    }

    return defaultPosts + newPosts
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
)

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