package com.example.runningspot

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
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
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.telecom.VideoProfile.isPaused
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import androidx.lifecycle.lifecycleScope
import com.example.runningspot.data.remote.ApiClient
import com.google.android.material.button.MaterialButton
import com.example.runningspot.ui.getCircularBitmap
import com.kakao.vectormap.GestureType
import kotlinx.coroutines.launch
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.compose.ui.graphics.toArgb
import com.example.runningspot.ui.theme.AppWhite
import com.example.runningspot.ui.theme.BrandBlue
import kotlin.jvm.java


class RunningActivity : ComponentActivity() {
    private var expectedRouteMarker: com.kakao.vectormap.label.Label? = null
    private var userLocationMarker: com.kakao.vectormap.label.Label? = null
    private val offRouteMarkers = mutableListOf<com.kakao.vectormap.label.Label>()
    private lateinit var navIcon: android.widget.ImageView

    private lateinit var txtNavInstruction: TextView
    private lateinit var txtNavDistance: TextView
    private lateinit var navBanner: android.widget.LinearLayout

    private var isOffRouteNow = false
    private val offRouteThresholdM = 25.0
    private val turnDetectAngleDeg = 28.0
    private val minTurnDistanceM = 8.0
    private val lookAheadPoints = 1
    private val maxAcceptedAccuracyM = 35f
    private val minRecordGapM = 4.0
    private val progressBacktrackToleranceM = 25.0
    private val finishDistanceThresholdM = 12.0
    private fun calculateRemainingDistance(
        projectedResult: ProjectedPointResult,
        route: List<LatLng>
    ): Double {
        val total = calculatePathDistance(route)
        return (total - projectedResult.progressDistanceM).coerceAtLeast(0.0)
    }

    private var maxReachedRouteIndex = 0
    private var followMode = false
    private var followRouteTitle: String = ""
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

    private var absoluteStartTimeMs = 0L // 헬스 커넥트용 실제 시각
    private lateinit var healthConnectManager: com.example.runningspot.HealthConnect.HealthConnectManager
    private var userMarkerBitmap: Bitmap? = null
    private var isPaused = false
    private var autoFollow = true

    // 상단 UI (거리/시간)
    private lateinit var txtPace: TextView
    private lateinit var txtCalories: TextView
    private lateinit var txtTime: TextView
    private lateinit var txtDistance: TextView

    private lateinit var txtHeartRate: TextView
    private lateinit var txtSteps: TextView
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
    private lateinit var txtFollowTitle: TextView
    private lateinit var txtRemain: TextView
    private lateinit var txtDeviation: TextView

    private var guidePoints: List<LatLng> = emptyList()
    private var guideTotalDistance = 0.0
    private var offRouteCount = 0
    private var wasOffRoute = false
    private var maxGuideProgressDistanceM = 0.0
    private var hasFinishTriggered = false

    private lateinit var pauseBtn: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        healthConnectManager = com.example.runningspot.HealthConnect.HealthConnectManager(this)
        // ✅ 루트 레이아웃 생성
        val root = android.widget.FrameLayout(this)
        mapView = MapView(this)
        followMode = (intent.getStringExtra("run_mode") == "follow")
        followRouteId = intent.getLongExtra("follow_route_id", -1L)
        followRouteTitle = intent.getStringExtra("follow_route_title").orEmpty()
        navBanner = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(28, 50, 28, 80)
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.parseColor("#F0F0EE"))
                cornerRadius = 24f
            }
            elevation = 10f
            visibility = if (followMode) View.VISIBLE else View.GONE
        }

        txtNavInstruction = TextView(this).apply {
            text = "다음 안내: -"
            setTextColor(Color.parseColor("#2A2A2A"))
            textSize = 20f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }

        txtNavDistance = TextView(this).apply {
            text = "다음 꺾임까지 -"
            setTextColor(Color.parseColor("#2A2A2A"))
            textSize = 16f
        }
        val textContainer = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            layoutParams = android.widget.LinearLayout.LayoutParams(
                0,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }

        textContainer.addView(txtNavInstruction)
        textContainer.addView(txtNavDistance)
        navIcon = android.widget.ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(500, 500).apply {
                marginStart = 16
            }
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageResource(R.drawable.go)
        }

        val navRow = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(textContainer)
            addView(navIcon)
        }
        navBanner.addView(navRow)

        root.addView(
            mapView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        root.addView(
            navBanner,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP
                topMargin = 90
                marginStart = 24
                marginEnd = 24
            }
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
        val (remainTile, remainValue) = makeStatTile("남은 거리")
        val (deviationTile, deviationValue) = makeStatTile("경로 이탈 횟수")
        val (hrTile, hrValue) = makeStatTile("심박수")
        val (stepsTile, stepsValue) = makeStatTile("걸음 수")
        txtRemain = remainValue
        txtDeviation = deviationValue
        txtPace = paceValue
        txtTime = timeValue
        txtCalories = kcalValue
        txtDistance = distValue
        txtHeartRate = hrValue
        txtSteps = stepsValue
        //첫째줄에 시간, 거리 표시
        val firstRow = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 14)
            addView(
                paceTile,
                android.widget.LinearLayout.LayoutParams(
                    0,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                ).apply { marginEnd = 8 })
            addView(
                timeTile,
                android.widget.LinearLayout.LayoutParams(
                    0,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                ).apply { marginStart = 8 })
        }

        val secondRow = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 14)
            addView(
                kcalTile,
                android.widget.LinearLayout.LayoutParams(
                    0,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                ).apply { marginEnd = 8 })
            addView(
                distTile,
                android.widget.LinearLayout.LayoutParams(
                    0,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                ).apply { marginStart = 8 })
        }
        val threeRow = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            visibility = if (followMode) View.VISIBLE else View.GONE
            setPadding(0, 0, 0, 14)

            addView(
                remainTile,
                android.widget.LinearLayout.LayoutParams(
                    0,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                ).apply { marginEnd = 8 }
            )
            addView(
                deviationTile,
                android.widget.LinearLayout.LayoutParams(
                    0,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                ).apply { marginStart = 8 }
            )
        }
        val thirdRow = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            addView(
                hrTile,
                android.widget.LinearLayout.LayoutParams(
                    0,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                ).apply { marginEnd = 8 })
            addView(
                stepsTile,
                android.widget.LinearLayout.LayoutParams(
                    0,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                ).apply { marginStart = 8 })
        }

        infoLayout.addView(threeRow)
        infoLayout.addView(firstRow)
        infoLayout.addView(secondRow)
        infoLayout.addView(thirdRow)

        val stopBtn = MaterialButton(this).apply {
            text = if (followMode) "따라뛰기 종료" else "러닝 종료"
            setBackgroundColor(Color.RED)
            setTextColor(Color.WHITE)
            setOnClickListener {
                stopRunningAndFinish()
            }
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
        // 하단 왼쪽 “일시정지/재개” 버튼
        pauseBtn = MaterialButton(this).apply {
            text = "일시정지"

            setBackgroundColor(Color.parseColor("#6E7075"))
            setTextColor(Color.WHITE)
            setOnClickListener { togglePause(this) }
        }
        val buttonRow = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(16, 20, 16, 8)
            addView(
                pauseBtn,
                android.widget.LinearLayout.LayoutParams(
                    0,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            )
            addView(
                gpsBtn,
                android.widget.LinearLayout.LayoutParams(
                    0,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            )
            addView(
                stopBtn,
                android.widget.LinearLayout.LayoutParams(
                    0,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            )
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

                if (followMode && followRouteId != -1L) {
                    drawGuideRouteOnce(map, followRouteId)
                }
                startRunning()
            }

            override fun getPosition(): LatLng = LatLng.from(37.56, 126.97)
            override fun getZoomLevel(): Int = 15
        })
        handleIndicatorAction(intent.getStringExtra("indicator_action"))
    }

    private fun simplifyRoutePoints(
        points: List<LatLng>,
        minGapM: Double = 8.0
    ): List<LatLng> {
        if (points.size < 2) return points
        val result = mutableListOf(points.first())
        for (i in 1 until points.size) {
            if (distanceBetween(result.last(), points[i]) >= minGapM) {
                result.add(points[i])
            }
        }
        if (result.last() != points.last()) {
            result.add(points.last())
        }
        return result
    }

    private fun drawGuideRouteOnce(map: KakaoMap, routeId: Long) {
        lifecycleScope.launch {
            try {
                val detail = ApiClient.routeApi.getRouteDetail(routeId) // GET /routes/{id}
                val rawPts = detail.points
                    .sortedBy { it.seq ?: Int.MAX_VALUE }
                    .distinctBy { "${it.lat},${it.lng}" }
                    .map { LatLng.from(it.lat, it.lng) }

                val pts = simplifyRoutePoints(rawPts)
                if (pts.size < 2) return@launch

                guidePoints = pts
                guideTotalDistance = calculatePathDistance(pts)
                val manager = map.routeLineManager ?: return@launch
                val layer = manager.layer
                runCatching { guideRoute?.let { layer.remove(it) } }

                val style = RouteLineStyle.from(10f, Color.parseColor("#2196F3"))
                val styles = RouteLineStyles.from(style)
                val seg = RouteLineSegment.from(pts).setStyles(styles)
                val options = RouteLineOptions.from(seg)
                guideRoute = layer.addRouteLine(options).apply { show() }

                updateExpectedRouteMarker(map, pts.first())

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
                        Bitmap.createBitmap(
                            decoded,
                            0,
                            0,
                            decoded.width,
                            decoded.height,
                            matrix,
                            true
                        )
                    else decoded
                }
            } catch (_: Exception) {
                decoded
            }

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
            val manager = map.routeLineManager

            for (loc in result.locations) {
                val p = LatLng.from(loc.latitude, loc.longitude)
                lastKnownLocation = loc

                val projectedResult = if (followMode && guidePoints.size >= 2) {
                    findProjectedPointOnRoute(
                        current = p,
                        route = guidePoints,
                        minProgressDistanceM = maxGuideProgressDistanceM
                    )
                } else {
                    null
                }

                val recordPoint = if (followMode && projectedResult != null) {
                    projectedResult.projected
                } else {
                    p
                }

                val shouldRecordPoint = shouldRecordPathPoint(
                    loc = loc,
                    currentPoint = recordPoint,
                    projectedResult = projectedResult
                )

                if (shouldRecordPoint) {
                    if (runningPath.isNotEmpty()) {
                        totalDistance += distanceBetween(runningPath.last(), recordPoint)
                    }
                    runningPath.add(recordPoint)
                    if (!followMode) {
                        manager?.let { drawPath(it) }
                    }
                    updateUI()
                }

                if (!followMode) {
                    if (autoFollow) {
                        moveCameraTo(map, p)
                    }
                    updateMarker(map, p)
                }

                if (projectedResult != null) {
                    maxGuideProgressDistanceM = max(
                        maxGuideProgressDistanceM,
                        projectedResult.progressDistanceM
                    )

                    val offRouteNow = projectedResult.distanceFromRouteM > offRouteThresholdM

                    val enteredOffRoute = !wasOffRoute && offRouteNow
                    if (enteredOffRoute) {
                        offRouteCount += 1
                        addOffRouteMarker(map, projectedResult.projected)
                    }
                    wasOffRoute = offRouteNow
                    isOffRouteNow = offRouteNow

                    updateExpectedRouteMarker(map, projectedResult.projected)
                    if (autoFollow) {
                        moveCameraTo(map, projectedResult.projected)
                    }

                    val remainingM = calculateRemainingDistance(projectedResult, guidePoints)
                    val nextTurn = findNextTurn(guidePoints, projectedResult)

                    updateNavigationUi(
                        remainingM = remainingM,
                        offRoute = offRouteNow,
                        nextTurn = nextTurn
                    )

                    if (shouldAutoFinishFollowRun(remainingM, offRouteNow, p)) {
                        stopRunningAndFinish(autoCompleted = true)
                        return
                    }
                }
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

    private fun isBacktrackingOnGuide(projectedResult: ProjectedPointResult): Boolean {
        return projectedResult.progressDistanceM + progressBacktrackToleranceM < maxGuideProgressDistanceM
    }

    private fun shouldRecordPathPoint(
        loc: android.location.Location,
        currentPoint: LatLng,
        projectedResult: ProjectedPointResult?
    ): Boolean {
        if (loc.hasAccuracy() && loc.accuracy > maxAcceptedAccuracyM) {
            return false
        }

        val lastPoint = runningPath.lastOrNull() ?: return true
        if (distanceBetween(lastPoint, currentPoint) < minRecordGapM) {
            return false
        }

        if (projectedResult != null && isBacktrackingOnGuide(projectedResult)) {
            return false
        }

        return true
    }

    private fun shouldAutoFinishFollowRun(
        remainingM: Double,
        offRoute: Boolean,
        currentPoint: LatLng
    ): Boolean {
        if (!followMode || hasFinishTriggered || guidePoints.isEmpty() || offRoute) {
            return false
        }

        val endDistance = distanceBetween(currentPoint, guidePoints.last())
        return remainingM <= finishDistanceThresholdM || endDistance <= finishDistanceThresholdM
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
        val timeText = "%02d:%02d".format(minutes, seconds)
        txtTime.text = timeText

        val distanceText = "%.1f km".format(totalDistance / 1000.0)
        txtDistance.text = distanceText
        val paceSecondsPerKm = calcPace(totalDistance, durationMs)
        val paceText = if (paceSecondsPerKm != null) {
            val paceMin = (paceSecondsPerKm / 60).toInt()
            val paceSec = (paceSecondsPerKm % 60).toInt()
            "%d'%02d\"".format(paceMin, paceSec)
        } else {
            "-'--\""
        }
        txtPace.text = paceText
        updateRunningIndicator(
            timeText = timeText,
            distanceText = distanceText,
            paceText = paceText
        )
        if (absoluteStartTimeMs == 0L) {
            txtCalories.text = "%.0f kcal".format(calcCalories(totalDistance))
            return
        }
        if (elapsedTime % 5L == 0L) {
            lifecycleScope.launch {
                // 러닝 중일 때만 헬스 데이터를 호출합니다.
                if (isRunning && healthConnectManager.checkAvailability() == androidx.health.connect.client.HealthConnectClient.SDK_AVAILABLE) {

                    val startInst = java.time.Instant.ofEpochMilli(absoluteStartTimeMs)
                    val currentInst = java.time.Instant.ofEpochMilli(System.currentTimeMillis())

                    // 워치 데이터 읽어오기
                    val hr = healthConnectManager.readSessionAvgHeartRate(startInst, currentInst)
                    val steps = healthConnectManager.readSessionSteps(startInst, currentInst)
                    val wearableKcal =
                        healthConnectManager.readSessionCalories(startInst, currentInst)

                    // 심박수 & 걸음수 텍스트 업데이트
                    txtHeartRate.text = if (hr > 0) "$hr bpm" else "-"
                    txtSteps.text = if (steps > 0) "${steps}보" else "-"

                    // 칼로리는 워치 데이터가 있으면 그것을, 없으면 기존 공식 적용
                    if (wearableKcal > 0) {
                        txtCalories.text = "%.0f kcal".format(wearableKcal)
                    } else {
                        val fallbackCalories = calcCalories(totalDistance)
                        txtCalories.text = "%.0f kcal".format(fallbackCalories)
                    }
                } else {
                    val calories = calcCalories(totalDistance)
                    txtCalories.text = "%.0f kcal".format(calories)
                }
            }
        }
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
        clearRunMarkers()
        totalDistance = 0.0
        maxReachedRouteIndex = 0
        maxGuideProgressDistanceM = 0.0
        offRouteCount = 0
        wasOffRoute = false
        hasFinishTriggered = false
        startTime = SystemClock.elapsedRealtime()
        pauseStartedAt = 0L
        accumulatedPauseMs = 0L
        isRunning = true
        isPaused = false
        startRunningIndicator()

        absoluteStartTimeMs = System.currentTimeMillis()
        fused.lastLocation.addOnSuccessListener { loc ->
            kakaoMap?.let { map ->
                if (loc != null) {
                    val start = LatLng.from(loc.latitude, loc.longitude)
                    if (followMode && guidePoints.size >= 2) {
                        val projectedStart = findProjectedPointOnRoute(start, guidePoints)?.projected
                        if (projectedStart != null) {
                            runningPath.add(projectedStart)
                            moveCameraTo(map, projectedStart)
                            updateExpectedRouteMarker(map, projectedStart)
                        }
                    } else {
                        runningPath.add(start)
                        moveCameraTo(map, start)
                        updateMarker(map, start)
                    }
                }
            }
        }

        fused.requestLocationUpdates(locationRequest, locationCallback, mainLooper)
        timerHandler.removeCallbacks(timerTicker)
        timerHandler.post(timerTicker)
        updateUI()
        Toast.makeText(this, "러닝 시작!", Toast.LENGTH_SHORT).show()
    }

    private fun clearRunMarkers() {
        val layer = kakaoMap?.labelManager?.layer ?: return
        runCatching { userLocationMarker?.let { layer.remove(it) } }
        runCatching { expectedRouteMarker?.let { layer.remove(it) } }
        offRouteMarkers.forEach { marker ->
            runCatching { layer.remove(marker) }
        }
        userLocationMarker = null
        expectedRouteMarker = null
        offRouteMarkers.clear()
    }

    // ✅ 러닝 종료 및 결과 반환
    private fun stopRunningAndFinish(autoCompleted: Boolean = false) {
        if (hasFinishTriggered) return
        hasFinishTriggered = true

        val finalDurationMs = getElapsedDurationMs()
        val absoluteEndTimeMs = System.currentTimeMillis() // 헬스 커넥트용 종료 시각
        val resultIntent = Intent()
        isRunning = false
        timerHandler.removeCallbacks(timerTicker)
        fused.removeLocationUpdates(locationCallback)
        Toast.makeText(this, "러닝 기록을 정리 중입니다...", Toast.LENGTH_SHORT).show()

        if (followMode && guidePoints.size >= 2) {
            val current = lastKnownLocation?.let { LatLng.from(it.latitude, it.longitude) }
            val projectedFinish = current?.let {
                findProjectedPointOnRoute(
                    current = it,
                    route = guidePoints,
                    minProgressDistanceM = maxGuideProgressDistanceM
                )
            }?.projected

            if (projectedFinish != null) {
                val last = runningPath.lastOrNull()
                if (last == null || distanceBetween(last, projectedFinish) >= 1.0) {
                    if (last != null) {
                        totalDistance += distanceBetween(last, projectedFinish)
                    }
                    runningPath.add(projectedFinish)
                }
            }
        }

        lifecycleScope.launch {
            // 웨어러블 기기 데이터 동기화 시간 딜레이
            kotlinx.coroutines.delay(1500)
            var wearableSteps = 0L
            var wearableHeartRate = 0L
            var wearableCalories = 0.0
            if (healthConnectManager.checkAvailability() == androidx.health.connect.client.HealthConnectClient.SDK_AVAILABLE &&
                healthConnectManager.hasAllPermissions()
            ) {
                val startInst = java.time.Instant.ofEpochMilli(absoluteStartTimeMs)
                val endInst = java.time.Instant.ofEpochMilli(absoluteEndTimeMs)

                wearableSteps = healthConnectManager.readSessionSteps(startInst, endInst)
                wearableHeartRate = healthConnectManager.readSessionAvgHeartRate(startInst, endInst)
                wearableCalories = healthConnectManager.readSessionCalories(startInst, endInst)
            }

            resultIntent.putExtra("runningDistance", totalDistance)
            resultIntent.putExtra("runningTime", finalDurationMs)
            resultIntent.putExtra("pathSize", runningPath.size)
            runningPath.forEachIndexed { i, latLng ->
                resultIntent.putExtra("lat_$i", latLng.latitude)
                resultIntent.putExtra("lng_$i", latLng.longitude)
            }
            resultIntent.putExtra("startTimeMs", absoluteStartTimeMs)
            resultIntent.putExtra("endTimeMs", absoluteEndTimeMs)
            resultIntent.putExtra("wearableSteps", wearableSteps)
            resultIntent.putExtra("wearableHeartRate", wearableHeartRate)
            resultIntent.putExtra("wearableCalories", wearableCalories)

            val followResultPath = if (followMode && autoCompleted && guidePoints.isNotEmpty()) {
                guidePoints
            } else {
                runningPath
            }
            resultIntent.putExtra("followPathSize", followResultPath.size)
            followResultPath.forEachIndexed { i, latLng ->
                resultIntent.putExtra("follow_lat_$i", latLng.latitude)
                resultIntent.putExtra("follow_lng_$i", latLng.longitude)
            }

            resultIntent.putExtra("followMode", followMode)
            resultIntent.putExtra("offRouteCount", offRouteCount)
            resultIntent.putExtra("followRouteTitle", followRouteTitle)
            resultIntent.putExtra("autoCompleted", autoCompleted)
            resultIntent.putExtra("followCompleted", autoCompleted)
            resultIntent.putExtra("completionStatus", if (autoCompleted) "completed" else "stopped")

            if (followMode && guidePoints.isNotEmpty()) {
                val current = runningPath.lastOrNull()
                val result = current?.let { calculateFollowProgress(it, guidePoints) }
                resultIntent.putExtra("followProgressPercent", result?.progressPercent ?: 0)
            }

            setResult(RESULT_OK, resultIntent)

            Toast.makeText(
                this@RunningActivity,
                when {
                    followMode && autoCompleted -> "따라뛰기 완료!"
                    followMode -> "따라뛰기 종료!"
                    else -> "러닝 종료!"
                },
                Toast.LENGTH_SHORT
            ).show()

            finish()
        }
    }

    // ✅ 지도 관련 함수
    private fun moveCameraTo(map: KakaoMap, p: LatLng) {
        map.moveCamera(CameraUpdateFactory.newCenterPosition(p))
    }

    private fun updateMarker(map: KakaoMap, p: LatLng) {
        val labelManager = map.labelManager ?: return
        val layer = labelManager.layer ?: return
        runCatching { userLocationMarker?.let { layer.remove(it) } }

        val styles = if (userMarkerBitmap != null) {
            LabelStyle.from(userMarkerBitmap)
        } else {
            LabelStyle.from(R.drawable.loc)
        }
        userLocationMarker = layer.addLabel(LabelOptions.from(p).setStyles(styles))
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
    override fun onDestroy() {
        stopRunningIndicator()
        mapView.finish()
        super.onDestroy()
    }

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
    private fun distanceMeters(
        lat1: Double,
        lng1: Double,
        lat2: Double,
        lng2: Double
    ): Double {
        val result = FloatArray(1)
        android.location.Location.distanceBetween(lat1, lng1, lat2, lng2, result)
        return result[0].toDouble()
    }
    private fun calculatePathDistance(points: List<LatLng>): Double {
        if (points.size < 2) return 0.0
        var total = 0.0
        for (i in 0 until points.lastIndex) {
            total += distanceBetween(points[i], points[i + 1])
        }
        return total
    }
    private data class FollowProgressResult(
        val progressPercent: Int,
        val completedM: Double,
        val remainingM: Double,
        val distanceFromRouteM: Double
    )
    private fun calculateFollowProgress(
        current: LatLng,
        route: List<LatLng>
    ): FollowProgressResult? {
        if (route.size < 2) return null

        var nearestIndex = -1
        var nearestDistance = Double.MAX_VALUE

        route.forEachIndexed { index, point ->
            val d = distanceBetween(current, point)
            if (d < nearestDistance) {
                nearestDistance = d
                nearestIndex = index
            }
        }

        if (nearestIndex == -1) return null

        val safeIndex = nearestIndex.coerceAtLeast(maxReachedRouteIndex)
        maxReachedRouteIndex = safeIndex

        var completed = 0.0
        for (i in 0 until safeIndex) {
            completed += distanceBetween(route[i], route[i + 1])
        }

        val total = calculatePathDistance(route)
        val remaining = (total - completed).coerceAtLeast(0.0)
        val progress = if (total > 0) {
            ((completed / total) * 100).toInt().coerceIn(0, 100)
        } else 0

        return FollowProgressResult(
            progressPercent = progress,
            completedM = completed,
            remainingM = remaining,
            distanceFromRouteM = nearestDistance
        )
    }
    private fun newdistanceBetween(a: LatLng, b: LatLng): Double {
        return distanceMeters(a.latitude, a.longitude, b.latitude, b.longitude)
    }
    private fun updateFollowUi(
        progressPercent: Int,
        remainingM: Double,
        isOffRoute: Boolean
    ) {
        txtRemain.text = "남은 거리 ${"%.1f".format(remainingM / 1000.0)} km"
        txtDeviation.text =
            if (isOffRoute) "경로 이탈 ${offRouteCount}회"
            else "경로 유지 중 ${offRouteCount}회"
    }
    private fun projectPointToSegment(
        p: LatLng,
        a: LatLng,
        b: LatLng
    ): Pair<LatLng, Double> {
        val ax = a.longitude
        val ay = a.latitude
        val bx = b.longitude
        val by = b.latitude
        val px = p.longitude
        val py = p.latitude

        val abx = bx - ax
        val aby = by - ay
        val apx = px - ax
        val apy = py - ay

        val abLenSq = abx * abx + aby * aby
        val t = if (abLenSq == 0.0) 0.0 else ((apx * abx + apy * aby) / abLenSq).coerceIn(0.0, 1.0)

        val projX = ax + abx * t
        val projY = ay + aby * t
        val projected = LatLng.from(projY, projX)

        return projected to t
    }
    private fun findProjectedPointOnRoute(
        current: LatLng,
        route: List<LatLng>,
        minProgressDistanceM: Double = 0.0
    ): ProjectedPointResult? {
        if (route.size < 2) return null

        var bestSegmentIndex = -1
        var bestProjected: LatLng? = null
        var bestDistance = Double.MAX_VALUE
        var progressDistance = 0.0
        var bestProgressDistance = 0.0

        for (i in 0 until route.lastIndex) {
            val a = route[i]
            val b = route[i + 1]

            val (projected, t) = projectPointToSegment(current, a, b)
            val dist = distanceBetween(current, projected)

            val segmentLength = distanceBetween(a, b)
            val currentProgress = progressDistance + segmentLength * t

            if (currentProgress + progressBacktrackToleranceM < minProgressDistanceM) {
                progressDistance += segmentLength
                continue
            }

            if (dist < bestDistance) {
                bestDistance = dist
                bestProjected = projected
                bestSegmentIndex = i
                bestProgressDistance = currentProgress
            }

            progressDistance += segmentLength
        }

        val proj = bestProjected ?: return null

        return ProjectedPointResult(
            segmentIndex = bestSegmentIndex,
            projected = proj,
            distanceFromRouteM = bestDistance,
            progressDistanceM = bestProgressDistance
        )
    }
    private fun bearing(from: LatLng, to: LatLng): Double {
        val lat1 = Math.toRadians(from.latitude)
        val lon1 = Math.toRadians(from.longitude)
        val lat2 = Math.toRadians(to.latitude)
        val lon2 = Math.toRadians(to.longitude)

        val dLon = lon2 - lon1
        val y = kotlin.math.sin(dLon) * kotlin.math.cos(lat2)
        val x = kotlin.math.cos(lat1) * kotlin.math.sin(lat2) -
                kotlin.math.sin(lat1) * kotlin.math.cos(lat2) * kotlin.math.cos(dLon)

        var brng = Math.toDegrees(kotlin.math.atan2(y, x))
        brng = (brng + 360.0) % 360.0
        return brng
    }
    private fun normalizedAngleDiff(a: Double, b: Double): Double {
        return (b - a + 540.0) % 360.0 - 180.0
    }
    private fun smoothedBearing(
        route: List<LatLng>,
        fromIndex: Int,
        toIndex: Int
    ): Double? {
        if (route.size < 2) return null
        if (fromIndex < 0 || toIndex >= route.size || fromIndex >= toIndex) return null

        val from = route[fromIndex]
        val to = route[toIndex]
        val dist = distanceBetween(from, to)
        if (dist < minTurnDistanceM) return null

        return bearing(from, to)
    }

    private fun routeDistance(points: List<LatLng>, start: Int, end: Int): Double {
        if (points.size < 2 || start >= end) return 0.0
        var sum = 0.0
        for (i in start until end) {
            sum += distanceBetween(points[i], points[i + 1])
        }
        return sum
    }

    private fun angleDiffDeg(a: Double, b: Double): Double {
        var diff = (b - a + 540.0) % 360.0 - 180.0
        return diff
    }

    private fun directionTextByAngle(angle: Double): String {
        val absAngle = kotlin.math.abs(angle)
        return when {
            absAngle < 20 -> "직진"
            angle in 20.0..45.0 -> "완만한 우회전"
            angle in 45.0..120.0 -> "우회전"
            angle > 120.0 -> "급우회전"
            angle in -45.0..-20.0 -> "완만한 좌회전"
            angle in -120.0..-45.0 -> "좌회전"
            angle < -120.0 -> "급좌회전"
            else -> "직진"
        }
    }
    private fun findNextTurn(
        route: List<LatLng>,
        projectedResult: ProjectedPointResult
    ): NextTurnResult? {
        if (route.size < 2) return null

        val currentSegment = projectedResult.segmentIndex
        var accumulated = distanceBetween(projectedResult.projected, route[currentSegment + 1])

        for (i in (currentSegment + 1) until route.size - 2) {
            val backStart = (i - lookAheadPoints).coerceAtLeast(0)
            val backEnd = i
            val frontStart = i
            val frontEnd = (i + lookAheadPoints).coerceAtMost(route.lastIndex)

            val beforeBearing = smoothedBearing(route, backStart, backEnd)
            val afterBearing = smoothedBearing(route, frontStart, frontEnd)

            if (beforeBearing == null || afterBearing == null) {
                if (i < route.size - 1) {
                    accumulated += distanceBetween(route[i], route[i + 1])
                }
                continue
            }

            val angle = normalizedAngleDiff(beforeBearing, afterBearing)

            if (kotlin.math.abs(angle) >= turnDetectAngleDeg) {
                return NextTurnResult(
                    directionText = directionTextByAngle(angle),
                    distanceToTurnM = accumulated
                )
            }

            if (i < route.size - 1) {
                accumulated += distanceBetween(route[i], route[i + 1])
            }
        }

        return NextTurnResult(
            directionText = "직진",
            distanceToTurnM = accumulated
        )
    }
    private fun updateExpectedRouteMarker(map: KakaoMap, pos: LatLng) {
        val labelManager = map.labelManager ?: return
        val layer = labelManager.layer ?: return

        runCatching { expectedRouteMarker?.let { layer.remove(it) } }

        val styles = labelManager.addLabelStyles(
            LabelStyles.from(
                LabelStyle.from(createCircleMarkerBitmap(Color.parseColor("#204996"), 28))
            )
        )

        expectedRouteMarker = layer.addLabel(
            LabelOptions.from(pos).setStyles(styles)
        )
    }

    private fun addOffRouteMarker(map: KakaoMap, pos: LatLng) {
        val labelManager = map.labelManager ?: return
        val layer = labelManager.layer ?: return
        val styles = labelManager.addLabelStyles(
            LabelStyles.from(
                LabelStyle.from(createCircleMarkerBitmap(Color.RED, 34))
            )
        )

        val marker = layer.addLabel(LabelOptions.from(pos).setStyles(styles))
        offRouteMarkers.add(marker)
    }

    private fun createCircleMarkerBitmap(color: Int, sizePx: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.FILL
        }

        val radius = sizePx / 2f
        canvas.drawCircle(radius, radius, radius, paint)

        paint.apply {
            this.color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = (sizePx * 0.16f).coerceAtLeast(2f)
        }
        canvas.drawCircle(radius, radius, radius - paint.strokeWidth / 2f, paint)
        return bitmap
    }
    private fun updateNavigationUi(
        remainingM: Double,
        offRoute: Boolean,
        nextTurn: NextTurnResult?
    ) {
        txtRemain.text = "%.1f km".format(remainingM / 1000.0)
        txtDeviation.text = "${offRouteCount}회"

        if (offRoute) {
            txtNavInstruction.text = "경로 이탈: 루트로 복귀하세요"
            txtNavDistance.text = "루트에서 벗어났습니다"
            updateDirectionIcon(directionText = "", offRoute = true)
            return
        }

        if (nextTurn == null) {
            txtNavInstruction.text = "다음 안내: 직진"
            txtNavDistance.text = "직진 구간"
            updateDirectionIcon("직진", offRoute = false)
            return
        }

        txtNavInstruction.text = when {
            nextTurn.distanceToTurnM < 15 -> "지금 ${nextTurn.directionText}"
            nextTurn.distanceToTurnM < 50 -> "곧 ${nextTurn.directionText}"
            else -> "다음 안내: ${nextTurn.directionText}"
        }
        updateDirectionIcon(nextTurn.directionText,offRoute = false)

        txtNavDistance.text = "다음 꺾임까지 ${nextTurn.distanceToTurnM.toInt()}m"
    }
    private data class ProjectedPointResult(
        val segmentIndex: Int,
        val projected: LatLng,
        val distanceFromRouteM: Double,
        val progressDistanceM: Double
    )

    private data class NextTurnResult(
        val directionText: String,
        val distanceToTurnM: Double
    )
    private fun updateDirectionIcon(directionText: String,offRoute: Boolean) {
        if (!::navIcon.isInitialized) return

        val resId = when {
            offRoute -> R.drawable.x
            "좌회전" in directionText -> R.drawable.left
            "우회전" in directionText -> R.drawable.right
            else -> R.drawable.go
        }
        navIcon.setImageResource(resId)
    }
    private fun startRunningIndicator() {
        val intent = Intent(this, RunningIndicatorService::class.java).apply {
            action = RunningIndicatorService.ACTION_START
            putExtra(RunningIndicatorService.EXTRA_TIME, "00:00")
            putExtra(RunningIndicatorService.EXTRA_DISTANCE_KM, "0.0 km")
            putExtra(RunningIndicatorService.EXTRA_PACE, "-'--\"")
            putExtra(RunningIndicatorService.EXTRA_IS_PAUSED, false)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun updateRunningIndicator(timeText: String, distanceText: String, paceText: String) {
        val intent = Intent(this, RunningIndicatorService::class.java).apply {
            action = RunningIndicatorService.ACTION_UPDATE
            putExtra(RunningIndicatorService.EXTRA_TIME, timeText)
            putExtra(RunningIndicatorService.EXTRA_DISTANCE_KM, distanceText)
            putExtra(RunningIndicatorService.EXTRA_PACE, paceText)
            putExtra(RunningIndicatorService.EXTRA_IS_PAUSED, isPaused)
        }
        startService(intent)
    }

    private fun stopRunningIndicator() {
        val intent = Intent(this, RunningIndicatorService::class.java).apply {
            action = RunningIndicatorService.ACTION_STOP
        }
        startService(intent)

        val nm = getSystemService(NotificationManager::class.java)
        nm.cancel(RunningIndicatorService.NOTIFICATION_ID)
    }

    private fun handleIndicatorAction(action: String?) {
        when (action) {
            RunningIndicatorActionReceiver.ACTION_PAUSE_RESUME -> {
                if (::pauseBtn.isInitialized) {
                    togglePause(pauseBtn)
                }
            }

            RunningIndicatorActionReceiver.ACTION_STOP -> {
                stopRunningAndFinish()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIndicatorAction(intent.getStringExtra("indicator_action"))
    }
}
