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

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import java.io.InputStream
import android.content.Context
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.shape.CircleShape
import coil.compose.rememberAsyncImagePainter
import androidx.compose.ui.draw.clip
import androidx.exifinterface.media.ExifInterface
import android.graphics.Matrix
import android.net.Uri

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
// 전역 상태: 프로필 이미지 Uri 저장
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
        tryInitCenter(context,fusedLocationClient, kakaoMap, hasLocationPermission)
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        result ->
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
                        updateCurrentLabel(context,map, lat, lng)
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
    LaunchedEffect(userMarkerImageUri) {
        val map = kakaoMap
        if (map != null && userMarkerImageUri != null) {
            // 위치 권한이 있고 맵이 준비되었으면 현재 위치로 라벨 갱신
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                getSingleFix(fusedLocationClient) { lat, lng ->
                    updateCurrentLabel(context, map, lat, lng)
                }
            } else {
                // 권한 없으면 권한 요청을 유도하거나 무시
            }
        }
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
                    updateCurrentLabel(context,map, latLng.latitude, latLng.longitude)
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
                        updateCurrentLabel(context,map, lat, lng)
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
                val intent = Intent(context, RunningActivity::class.java).apply {
                    putExtra("userMarkerImageUri", userMarkerImageUri)
                }

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
    context : Context,
    fused: FusedLocationProviderClient,
    map: KakaoMap?,
    hasPermission: Boolean
) {
    if (!hasPermission || map == null) return
    getSingleFix(fused) { lat, lng ->
        moveCameraTo(map, lat, lng)
        updateCurrentLabel( context,map, lat, lng)
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
    val context = LocalContext.current
    val pickImageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            userMarkerImageUri = it.toString()   // 전역 상태에 저장
            Toast.makeText(context, "마커 이미지가 선택되었습니다.", Toast.LENGTH_SHORT).show()
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

            if (userMarkerImageUri != null) {
                Image(
                    painter = rememberAsyncImagePainter(userMarkerImageUri),
                    contentDescription = "마커 미리보기",
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = { userMarkerImageUri = null }) {
                    Text("마커 초기화")
                }
            } else {
                Button(onClick = { pickImageLauncher.launch("image/*") }) {
                    Text("마커 이미지 업로드")
                }
            }
            Spacer(Modifier.height(20.dp))
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