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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
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
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.runningspot.ui.components.BottomNavBar
import com.example.runningspot.ui.components.BottomNavDestination
import com.google.android.gms.location.*
import com.google.android.gms.tasks.CancellationTokenSource
import com.kakao.vectormap.*
import com.kakao.vectormap.camera.CameraUpdateFactory
import com.kakao.vectormap.label.LabelOptions
import com.kakao.vectormap.label.LabelTextBuilder
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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


object MainDestinations {
    const val Info = "info"
    const val Stats = "stats"
    const val Running = "running"
    const val Community = "community"
    const val MyPage = "mypage"
}

@Composable
fun MainScreen(
    userName: String?,
    userProfile: String?,
    provider: String?,
    onLogout: () -> Unit
) {
    val navController = rememberNavController()
    val navItems = remember {
        listOf(
            BottomNavDestination(MainDestinations.Info, Icons.Default.Info, "정보"),
            BottomNavDestination(MainDestinations.Stats, Icons.Default.Leaderboard, "통계"),
            BottomNavDestination(MainDestinations.Running, Icons.Default.DirectionsRun, "러닝"),
            BottomNavDestination(MainDestinations.Community, Icons.Default.People, "커뮤니티"),
            BottomNavDestination(MainDestinations.MyPage, Icons.Default.Person, "마이")
        )
    }
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: MainDestinations.Running

    Scaffold(
        bottomBar = {
            BottomNavBar(
                destinations = navItems,
                currentRoute = currentRoute,
                onTabSelected = { destination ->
                    navController.navigate(destination.route) {
                        launchSingleTop = true
                        restoreState = true
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                    }
                }
            )
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = MainDestinations.Running,
            modifier = Modifier.fillMaxSize()
        ) {
            composable(MainDestinations.Info) { InfoScreen(padding) }
            composable(MainDestinations.Stats) { StatsScreen(padding) }
            composable(MainDestinations.Running) { RunningScreen(padding) }
            composable(MainDestinations.Community) { CommunityScreen(padding) }
            composable(MainDestinations.MyPage) {
                MyPageScreen(
                    padding = padding,
                    userName = userName,
                    userProfile = userProfile,
                    provider = provider,
                    onLogout = onLogout
                )
            }
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
    var selectedTab by remember { mutableStateOf(CommunityCategory.General) }
    var generalPosts by remember { mutableStateOf(sampleGeneralPosts()) }
    var crewPosts by remember { mutableStateOf(sampleCrewPosts()) }
    var showPostDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
    ) {
        Column(Modifier.fillMaxSize()) {
            Text(
                text = "커뮤니티",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
            )

            CommunityCategoryTabs(
                selected = selectedTab,
                onTabSelected = { selectedTab = it }
            )

            when (selectedTab) {
                CommunityCategory.General -> {
                    GeneralFeed(
                        posts = generalPosts,
                        modifier = Modifier.weight(1f),
                        onToggleLike = { postId ->
                            generalPosts = generalPosts.map { post ->
                                if (post.id == postId) {
                                    val liked = !post.isLiked
                                    post.copy(
                                        isLiked = liked,
                                        likes = if (liked) post.likes + 1 else post.likes - 1
                                    )
                                } else {
                                    post
                                }
                            }
                        },
                        onAddComment = { postId, comment ->
                            if (comment.isBlank()) return@GeneralFeed
                            generalPosts = generalPosts.map { post ->
                                if (post.id == postId) {
                                    post.copy(comments = post.comments + comment)
                                } else {
                                    post
                                }
                            }
                        }
                    )
                }

                CommunityCategory.Crew -> {
                    CrewFeed(
                        posts = crewPosts,
                        modifier = Modifier.weight(1f),
                        onToggleLike = { postId ->
                            crewPosts = crewPosts.map { post ->
                                if (post.id == postId) {
                                    val liked = !post.isLiked
                                    post.copy(
                                        isLiked = liked,
                                        likes = if (liked) post.likes + 1 else post.likes - 1
                                    )
                                } else {
                                    post
                                }
                            }
                        }
                    )
                }
            }
        }

        if (selectedTab == CommunityCategory.General) {
            FloatingActionButton(
                onClick = { showPostDialog = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp),
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "새 게시글")
            }
        }

        if (showPostDialog) {
            WritePostDialog(
                onDismiss = { showPostDialog = false },
                onPost = { title, content ->
                    val newPost = CommunityPost(
                        id = (generalPosts.maxOfOrNull { it.id } ?: 0) + 1,
                        author = "나",
                        location = title.ifBlank { "추천 러닝 루트" },
                        content = content,
                        distance = "5.4km",
                        pace = "5'20\"/km",
                        likes = 0,
                        isLiked = false,
                        comments = emptyList(),
                        routeColor = nextRouteColor(generalPosts.size + 1)
                    )
                    generalPosts = listOf(newPost) + generalPosts
                    showPostDialog = false
                }
            )
        }
    }
}

private enum class CommunityCategory { General, Crew }

private data class CommunityPost(
    val id: Int,
    val author: String,
    val location: String,
    val content: String,
    val distance: String,
    val pace: String,
    val likes: Int,
    val isLiked: Boolean,
    val comments: List<String>,
    val routeColor: Color
)

private data class CrewPost(
    val id: Int,
    val crewName: String,
    val description: String,
    val meetupInfo: String,
    val likes: Int,
    val isLiked: Boolean,
    val tags: List<String>
)

@Composable
private fun CommunityCategoryTabs(selected: CommunityCategory, onTabSelected: (CommunityCategory) -> Unit) {
    val tabs = remember { CommunityCategory.values() }
    TabRow(selectedTabIndex = selected.ordinal) {
        tabs.forEach { tab ->
            Tab(
                selected = tab == selected,
                onClick = { onTabSelected(tab) },
                text = {
                    Text(
                        text = when (tab) {
                            CommunityCategory.General -> "일반"
                            CommunityCategory.Crew -> "크루"
                        },
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            )
        }
    }
}

@Composable
private fun GeneralFeed(
    posts: List<CommunityPost>,
    modifier: Modifier = Modifier,
    onToggleLike: (Int) -> Unit,
    onAddComment: (Int, String) -> Unit
) {
    if (posts.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("첫 러닝 루트를 공유해보세요!", color = MaterialTheme.colorScheme.outline)
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(posts, key = { it.id }) { post ->
            GeneralPostCard(
                post = post,
                onToggleLike = { onToggleLike(post.id) },
                onAddComment = { comment -> onAddComment(post.id, comment) }
            )
        }
    }
}

@Composable
private fun GeneralPostCard(
    post: CommunityPost,
    onToggleLike: () -> Unit,
    onAddComment: (String) -> Unit
) {
    var showComments by remember(post.id) { mutableStateOf(false) }
    var commentText by remember(post.id) { mutableStateOf("") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProfileBadge(name = post.author)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(post.author, style = MaterialTheme.typography.titleMedium)
                    Text(post.location, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
            }

            Spacer(Modifier.height(16.dp))

            RoutePreview(routeColor = post.routeColor)

            Spacer(Modifier.height(12.dp))

            Text(post.content, style = MaterialTheme.typography.bodyMedium)

            Spacer(Modifier.height(12.dp))

            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("거리 ${post.distance}", style = MaterialTheme.typography.labelMedium)
                    Text("페이스 ${post.pace}", style = MaterialTheme.typography.labelMedium)
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick = onToggleLike) {
                        Icon(
                            imageVector = if (post.isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = null,
                            tint = if (post.isLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("${post.likes}")
                    }
                    TextButton(onClick = { showComments = !showComments }) {
                        Icon(
                            imageVector = Icons.Outlined.ChatBubbleOutline,
                            contentDescription = null
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("${post.comments.size}")
                    }
                }
            }

            if (showComments) {
                Spacer(Modifier.height(8.dp))
                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                Spacer(Modifier.height(8.dp))

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    post.comments.forEach { comment ->
                        Text("• $comment", style = MaterialTheme.typography.bodySmall)
                    }
                }

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = commentText,
                    onValueChange = { commentText = it },
                    placeholder = { Text("댓글을 입력하세요") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))

                Button(
                    onClick = {
                        if (commentText.isNotBlank()) {
                            onAddComment(commentText)
                            commentText = ""
                        }
                    },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("등록")
                }
            }
        }
    }
}

@Composable
private fun CrewFeed(
    posts: List<CrewPost>,
    modifier: Modifier = Modifier,
    onToggleLike: (Int) -> Unit
) {
    if (posts.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("첫 크루 모집글을 작성해보세요!", color = MaterialTheme.colorScheme.outline)
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(posts, key = { it.id }) { post ->
            CrewPostCard(post = post, onToggleLike = { onToggleLike(post.id) })
        }
    }
}

@Composable
private fun CrewPostCard(post: CrewPost, onToggleLike: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProfileBadge(name = post.crewName)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(post.crewName, style = MaterialTheme.typography.titleMedium)
                    Text(post.meetupInfo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
            }

            Spacer(Modifier.height(12.dp))

            Text(post.description, style = MaterialTheme.typography.bodyMedium)

            Spacer(Modifier.height(12.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(post.tags) { tag ->
                    AssistChip(onClick = { }, label = { Text(tag) })
                }
            }

            Spacer(Modifier.height(12.dp))

            TextButton(onClick = onToggleLike) {
                Icon(
                    imageVector = if (post.isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = null,
                    tint = if (post.isLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.width(4.dp))
                Text("${post.likes}")
            }
        }
    }
}

@Composable
private fun ProfileBadge(name: String) {
    val initials = remember(name) {
        name.split(" ", limit = 2)
            .mapNotNull { it.firstOrNull()?.uppercaseChar() }
            .joinToString("")
            .takeIf { it.isNotBlank() }
            ?: name.take(2)
    }

    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.size(44.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(initials, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun RoutePreview(routeColor: Color) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = routeColor.copy(alpha = 0.2f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .background(routeColor.copy(alpha = 0.05f)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.DirectionsRun,
                    contentDescription = null,
                    tint = routeColor
                )
                Text("러닝 루트 미리보기", color = routeColor)
            }
        }
    }
}

@Composable
private fun WritePostDialog(onDismiss: () -> Unit, onPost: (String, String) -> Unit) {
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    if (content.isNotBlank()) {
                        onPost(title, content)
                    }
                }
            ) { Text("공유") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        },
        title = { Text("러닝 루트 공유") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("루트 이름 또는 위치") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("러닝 후기") },
                    minLines = 3
                )
            }
        }
    )
}

private fun sampleGeneralPosts() = listOf(
    CommunityPost(
        id = 1,
        author = "홍길동",
        location = "인천대공원 러닝 루트",
        content = "주말 아침 숲길을 달렸는데 공기가 정말 상쾌했어요. 벚꽃 시즌에 꼭 다시 오고 싶네요!",
        distance = "7.2km",
        pace = "5'10\"/km",
        likes = 12,
        isLiked = false,
        comments = listOf("저도 여기 자주 가요!", "주차는 괜찮았나요?"),
        routeColor = nextRouteColor(1)
    ),
    CommunityPost(
        id = 2,
        author = "김민수",
        location = "한강 잠원지구",
        content = "해 질 무렵 달리기 좋은 코스 추천합니다. 노을이 진짜 예술이에요!",
        distance = "5.8km",
        pace = "4'58\"/km",
        likes = 23,
        isLiked = false,
        comments = listOf("노을사진 기대해도 될까요?", "오늘 저녁에 달려봐야겠네요"),
        routeColor = nextRouteColor(2)
    )
)

private fun sampleCrewPosts() = listOf(
    CrewPost(
        id = 1,
        crewName = "Crew Momentum",
        description = "매주 화/목 저녁 8시에 뚝섬 한강공원에서 달려요. 초보 러너 환영합니다!",
        meetupInfo = "서울 · 화/목 20:00",
        likes = 18,
        isLiked = false,
        tags = listOf("#야간러닝", "#초보환영", "#한강")
    ),
    CrewPost(
        id = 2,
        crewName = "Crew Universe",
        description = "토요일 아침마다 성수동-서울숲 루트를 함께 달리고 브런치 즐겨요!",
        meetupInfo = "서울 · 토 08:30",
        likes = 9,
        isLiked = false,
        tags = listOf("#브런치", "#주말러닝")
    )
)

private fun nextRouteColor(index: Int): Color {
    val colors = listOf(
        Color(0xFF4CAF50),
        Color(0xFF2196F3),
        Color(0xFFFF9800),
        Color(0xFF9C27B0)
    )
    return colors[(index - 1) % colors.size]
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