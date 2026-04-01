package com.example.runningspot

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
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
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import androidx.lifecycle.lifecycleScope
import com.example.runningspot.data.remote.ApiClient
import com.google.android.material.button.MaterialButton
import com.example.runningspot.ui.getCircularBitmap
import com.kakao.vectormap.GestureType
import kotlinx.coroutines.launch


class RunningActivity : ComponentActivity() {

    private var followMode = true
    private var lastKnownLocation: android.location.Location? = null
    private lateinit var gpsBtn: com.google.android.material.button.MaterialButton
    // 지도 관련
    private lateinit var mapView: MapView
    private var kakaoMap: KakaoMap? = null
    private var followRouteId: Long = -1L
    private var guideRoute: RouteLine? = null

    // 경로 표시용
    private var currentRoute: RouteLine? = null
    private val runningPath = mutableListOf<LatLng>()

    // 위치 추적용
    private lateinit var fused: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private var isRunning = false

    private var userMarkerBitmap: Bitmap? = null
    private var isPaused = false
    private var autoFollow = true
    // 상단 UI (거리/시간)
    private lateinit var txtPace: TextView
    private lateinit var txtCalories: TextView
    private lateinit var txtTime: TextView
    private lateinit var txtDistance: TextView
    private var startTime = 0L
    private var elapsedTime = 0L
    private var totalDistance = 0.0
    private var pauseStartedAt = 0L
    private var accumulatedPauseMs = 0L
    private val timerHandler = Handler(Looper.getMainLooper())
    private val timerTicker = object : Runnable {
        override fun run() {
            if (isRunning && !isPaused) {
                updateUI()
                timerHandler.postDelayed(this, 1000L)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ✅ 루트 레이아웃 생성
        val root = android.widget.FrameLayout(this)
        mapView = MapView(this)

        root.addView(
            mapView,
            android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        //마커 설
        val userMarkerImageUri = intent.getStringExtra("userMarkerImageUri")

        if (!userMarkerImageUri.isNullOrBlank()) {
            userMarkerBitmap = loadUserRunningMarkerBitmap(this, userMarkerImageUri)
        }

        // ✅ 하단 인디케이터 UI (불투명 카드형 2x2)
        val infoLayout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(0, 0, 0, 0)
        }

        fun makeStatTile(title: String): Pair<android.view.View, TextView> {
            val valueView = TextView(this).apply {
                text = "-"
                setTextColor(Color.parseColor("#1A1A1A"))
                textSize = 26f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }

            val container = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                setPadding(24, 20, 24, 20)
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(Color.parseColor("#F0F0EE"))
                    cornerRadius = 20f
                }
                addView(TextView(this@RunningActivity).apply {
                    text = title
                    setTextColor(Color.parseColor("#2A2A2A"))
                    textSize = 15f
                })
                addView(valueView)
            }
            return container to valueView
        }

        val (paceTile, paceValue) = makeStatTile("평균 페이스")
        val (timeTile, timeValue) = makeStatTile("시간")
        val (kcalTile, kcalValue) = makeStatTile("소모 칼로리")
        val (distTile, distValue) = makeStatTile("러닝 거리")

        txtPace = paceValue
        txtTime = timeValue
        txtCalories = kcalValue
        txtDistance = distValue

        val firstRow = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 14)
            addView(paceTile, android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = 8 })
            addView(timeTile, android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = 8 })
        }
        val secondRow = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            addView(kcalTile, android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = 8 })
            addView(distTile, android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = 8 })
        }

        infoLayout.addView(firstRow)
        infoLayout.addView(secondRow)

        val stopBtn = com.google.android.material.button.MaterialButton(this).apply {
            text = "러닝 종료"
            setBackgroundColor(Color.RED)
            setTextColor(Color.WHITE)
            setOnClickListener { stopRunningAndFinish() }
        }

        gpsBtn = MaterialButton(this).apply {
            text = "현재 위치"
            setBackgroundColor(Color.parseColor("#2196F3"))
            setTextColor(Color.WHITE)
            visibility = View.VISIBLE
            setOnClickListener {
                recenterToCurrentLocation()
            }
        }

        val pauseBtn = com.google.android.material.button.MaterialButton(this).apply {
            text = "일시정지"
            setBackgroundColor(Color.parseColor("#6E7075"))
            setTextColor(Color.WHITE)
            setOnClickListener { togglePause(this) }
        }

        val buttonRow = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(16, 20, 16, 8)
            addView(pauseBtn, android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(gpsBtn, android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(stopBtn, android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }

        val bottomContainer = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(16, 20, 16, 12)
            minimumHeight = (resources.displayMetrics.heightPixels * 0.22f).toInt()
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.parseColor("#FAFAF8"))
                cornerRadii = floatArrayOf(28f, 28f, 28f, 28f, 0f, 0f, 0f, 0f)
            }
            addView(
                infoLayout,
                android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
            addView(
                buttonRow,
                android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }

        root.addView(
            bottomContainer,
            android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = android.view.Gravity.BOTTOM
                bottomMargin = 14
            }
        )

        // ✅ 레이아웃 최종 지정
        setContentView(root)

        // ✅ 뒤로가기 버튼 처리
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    stopRunningAndFinish()
                }
            }
        )

        // ✅ 위치 및 지도 초기화
        fused = LocationServices.getFusedLocationProviderClient(this)
        locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY, 2000L
        ).build()

        // 지도 로드 완료 후 실행
        mapView.start(object : MapLifeCycleCallback() {
            override fun onMapDestroy() {}
            override fun onMapError(error: Exception?) {
                error?.printStackTrace()
            }
        }, object : KakaoMapReadyCallback() {
            override fun onMapReady(map: KakaoMap) {
                kakaoMap = map
                // 지도 로드 완료 후 러닝 시작

                map.setOnCameraMoveStartListener { _, gestureType ->
                    if (gestureType != GestureType.Unknown) {
                        autoFollow = false
                    }
                }
                map.setOnMapWidgetClickListener { kakaoMapObj, mapWidget, guiId ->
                    autoFollow = true
                    // 즉시 현재 위치로 이동 (권한이 있을 때)
                    if (ActivityCompat.checkSelfPermission(
                            this@RunningActivity,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        fused.lastLocation.addOnSuccessListener { loc ->
                            if (loc != null) {
                                kakaoMapObj.moveCamera(
                                    CameraUpdateFactory.newCenterPosition(
                                        LatLng.from(loc.latitude, loc.longitude)
                                    )
                                )
                            }
                        }
                    }
                }


                startRunning()
            }

            override fun getPosition(): LatLng = LatLng.from(37.56, 126.97)
            override fun getZoomLevel(): Int = 15
        })
    }
    private fun drawGuideRouteOnce(map: KakaoMap, routeId: Long) {
        lifecycleScope.launch {
            try {
                val detail = ApiClient.routeApi.getRouteDetail(routeId) // GET /routes/{id}

                val pts = detail.points
                    .sortedBy { it.seq } // seq 필수
                    .map { LatLng.from(it.lat, it.lng) }

                if (pts.size < 2) return@launch

                val manager = map.routeLineManager ?: return@launch
                val layer = manager.layer

                // 이전 가이드 라인 있으면 제거
                runCatching { guideRoute?.let { layer.remove(it) } }

                // 가이드 라인은 빨강(또는 회색) 추천
                val style = RouteLineStyle.from(10f, Color.RED)
                val styles = RouteLineStyles.from(style)
                val seg = RouteLineSegment.from(pts).setStyles(styles)
                val options = RouteLineOptions.from(seg)

                guideRoute = layer.addRouteLine(options).apply { show() }

                // (선택) 시작 지점으로 한번 카메라 이동
                // map.moveCamera(CameraUpdateFactory.newCenterPosition(pts.first()))

            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@RunningActivity, "가이드 루트 불러오기 실패", Toast.LENGTH_SHORT).show()
            }
        }
    }

    //마커 비트맵 조정 함수
    private fun loadUserRunningMarkerBitmap(context: Context, uriStr: String): Bitmap? {
        return try {
            val uri = Uri.parse(uriStr)

            // 1) 이미지 메타(크기) 확인
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri).use { ins ->
                BitmapFactory.decodeStream(ins, null, boundsOpts)
            }

            // 2) inSampleSize 계산
            val maxSize = 300
            var sample = 1
            val (ow, oh) = boundsOpts.outWidth to boundsOpts.outHeight
            if (ow > 0 && oh > 0) {
                var halfW = ow / 2
                var halfH = oh / 2
                while (halfW / sample > maxSize || halfH / sample > maxSize) {
                    sample *= 2
                }
            }

            // 3) 실제 디코딩
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            var bmp: Bitmap? = null
            context.contentResolver.openInputStream(uri).use { ins ->
                bmp = BitmapFactory.decodeStream(ins, null, opts)
            }
            var decoded = bmp ?: return null

            // 4) EXIF 회전 보정
            decoded = try {
                context.contentResolver.openInputStream(uri).use { ins ->
                    val exif = ExifInterface(ins!!)
                    val orientation = exif.getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                    val matrix = Matrix()
                    when (orientation) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                    }
                    if (!matrix.isIdentity)
                        Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
                    else decoded
                }
            } catch (_: Exception) { decoded }

            // 5) 마커 사이즈 보정
            val target = 200
            val scaled = Bitmap.createScaledBitmap(decoded, target, target, true)

            // 6) 원형 변환
            getCircularBitmap(scaled)

        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }


    private fun togglePause(btn: com.google.android.material.button.MaterialButton) {
        if (!isRunning) return

        isPaused = !isPaused
        btn.text = if (isPaused) "다시시작" else "일시정지"
        btn.setBackgroundColor(
            if (isPaused) Color.parseColor("#4CAF50") else Color.parseColor("#6E7075")
        )

        if (isPaused) {
            pauseStartedAt = SystemClock.elapsedRealtime()
            timerHandler.removeCallbacks(timerTicker)
            // 일시정지 → 위치 업데이트 중단
            fused.removeLocationUpdates(locationCallback)
        } else {
            accumulatedPauseMs += (SystemClock.elapsedRealtime() - pauseStartedAt)
            // 재개 → 권한 체크 후 위치 업데이트 재시작
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                fused.requestLocationUpdates(locationRequest, locationCallback, mainLooper)
                timerHandler.post(timerTicker)
            } else {
                Toast.makeText(this, "위치 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun getElapsedDurationMs(): Long {
        if (!isRunning) return 0L
        val now = if (isPaused) pauseStartedAt else SystemClock.elapsedRealtime()
        return (now - startTime - accumulatedPauseMs).coerceAtLeast(0L)
    }

    private fun recenterToCurrentLocation() {
        val map = kakaoMap ?: return

        lastKnownLocation?.let {
            val pos = LatLng.from(it.latitude, it.longitude)
            map.moveCamera(CameraUpdateFactory.newCenterPosition(pos))
        }

        autoFollow = true
    }

    // ✅ 위치 추적 콜백
    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            if (!isRunning) return
            val map = kakaoMap ?: return
            val manager = map.routeLineManager ?: return

            for (loc in result.locations) {
                val p = LatLng.from(loc.latitude, loc.longitude)

                if (runningPath.isNotEmpty()) {
                    totalDistance += distanceBetween(runningPath.last(), p)
                }

                runningPath.add(p)
                if (autoFollow) {
                    moveCameraTo(map, p)
                }
                updateMarker(map, p)
                drawPath(manager)
                updateUI()
            }
        }
    }


    // ✅ 거리 계산 (Haversine formula)
    private fun distanceBetween(a: LatLng, b: LatLng): Double {
        val r = 6371000.0 // Earth radius (m)
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLng = Math.toRadians(b.longitude - a.longitude)
        val sa = sin(dLat / 2).pow(2.0)
        val sb = cos(Math.toRadians(a.latitude)) * cos(Math.toRadians(b.latitude)) *
                sin(dLng / 2).pow(2.0)
        return 2 * r * asin(sqrt(sa + sb))
    }
    fun calcPace(distanceM: Double, durationMs: Long): Double? {
        if (distanceM < 50.0 || durationMs < 30_000L) return null // 정확도 올리기
        val distKm = distanceM / 1000.0
        val sec = durationMs / 1000.0
        if (distKm <= 0.0) return null
        return sec / distKm
    }

    // 칼로리 계산 (기본 몸무게: 70kg)
    fun calcCalories(distanceM: Double, weightKg: Double = 70.0): Double {
        val distKm = distanceM / 1000.0
        return weightKg * distKm * 1.0
    }

    // ✅ 상단 UI 갱신
    private fun updateUI() {
        val durationMs = getElapsedDurationMs()
        elapsedTime = durationMs / 1000
        val minutes = elapsedTime / 60
        val seconds = elapsedTime % 60
        txtTime.text = "%02d:%02d".format(minutes, seconds)
        txtDistance.text = "%.1f km".format(totalDistance / 1000.0)
        val paceSecondsPerKm = calcPace(totalDistance, durationMs)
        if (paceSecondsPerKm != null) {
            val paceMin = (paceSecondsPerKm / 60).toInt()
            val paceSec = (paceSecondsPerKm % 60).toInt()
            txtPace.text = "%d'%02d\"".format(paceMin, paceSec)
        } else {
            txtPace.text = "-'--\"" // 데이터 부족
        }
        val calories = calcCalories(totalDistance)
        txtCalories.text = "%.0f kcal".format(calories)
    }

    // ✅ 러닝 시작 (지도 로드 완료 후 실행)
    @SuppressLint("MissingPermission")
    private fun startRunning() {
        // 위치 권한 확인
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                100
            )
            return
        }

        runningPath.clear()
        totalDistance = 0.0
        startTime = SystemClock.elapsedRealtime()
        pauseStartedAt = 0L
        accumulatedPauseMs = 0L
        isRunning = true
        isPaused = false

        fused.lastLocation.addOnSuccessListener { loc ->
            kakaoMap?.let { map ->
                if (loc != null) {
                    val start = LatLng.from(loc.latitude, loc.longitude)
                    runningPath.add(start)
                    moveCameraTo(map, start)
                    updateMarker(map, start)
                }
            }
        }

        fused.requestLocationUpdates(locationRequest, locationCallback, mainLooper)
        timerHandler.removeCallbacks(timerTicker)
        timerHandler.post(timerTicker)
        updateUI()
        Toast.makeText(this, "러닝 시작!", Toast.LENGTH_SHORT).show()
    }

    // ✅ 러닝 종료 및 결과 반환
    private fun stopRunningAndFinish() {
        val finalDurationMs = getElapsedDurationMs()
        isRunning = false
        timerHandler.removeCallbacks(timerTicker)
        fused.removeLocationUpdates(locationCallback)

        // 결과 경로를 Intent로 반환
        val intent = Intent()
        intent.putExtra("runningDistance", totalDistance)
        intent.putExtra("runningTime", finalDurationMs)
        intent.putExtra("pathSize", runningPath.size)
        runningPath.forEachIndexed { i, latLng ->
            intent.putExtra("lat_$i", latLng.latitude)
            intent.putExtra("lng_$i", latLng.longitude)
        }
        setResult(RESULT_OK, intent)

        Toast.makeText(this, "러닝 종료!", Toast.LENGTH_SHORT).show()
        finish()
    }

    // ✅ 지도 관련 함수
    private fun moveCameraTo(map: KakaoMap, p: LatLng) {
        map.moveCamera(CameraUpdateFactory.newCenterPosition(p))
    }

    private fun updateMarker(map: KakaoMap, p: LatLng) {
        val labelManager = map.labelManager ?: return
        val layer = labelManager.layer ?: return
        layer.removeAll()

        val styles = if (userMarkerBitmap != null) {
            LabelStyle.from(userMarkerBitmap)
        } else {
            LabelStyle.from(R.drawable.loc)
        }
        layer.addLabel(LabelOptions.from(p).setStyles(styles))
    }

    private fun drawPath(manager: RouteLineManager) {
        if (runningPath.size < 2) return
        val layer = manager.layer
        runCatching { currentRoute?.let { layer.remove(it) } }
        val style = RouteLineStyle.from(8f, Color.BLUE)
        val styles = RouteLineStyles.from(style)
        val seg = RouteLineSegment.from(runningPath).setStyles(styles)
        val options = RouteLineOptions.from(seg)
        currentRoute = layer.addRouteLine(options).apply { show() }
    }

    // 수명주기
    override fun onResume() { super.onResume(); mapView.resume() }
    override fun onPause()  { super.onPause();  mapView.pause() }
    override fun onDestroy(){ super.onDestroy(); mapView.finish() }

    // 권한 결과
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            startRunning()
        } else {
            Toast.makeText(this, "위치 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}