package com.example.runningspot

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import com.example.runningspot.data.remote.ApiClient
import com.example.runningspot.ui.getCircularBitmap
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.material.button.MaterialButton
import com.kakao.vectormap.GestureType
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.KakaoMapReadyCallback
import com.kakao.vectormap.LatLng
import com.kakao.vectormap.MapLifeCycleCallback
import com.kakao.vectormap.MapView
import com.kakao.vectormap.camera.CameraUpdateFactory
import com.kakao.vectormap.label.LabelOptions
import com.kakao.vectormap.label.LabelStyle
import com.kakao.vectormap.route.RouteLine
import com.kakao.vectormap.route.RouteLineManager
import com.kakao.vectormap.route.RouteLineOptions
import com.kakao.vectormap.route.RouteLineSegment
import com.kakao.vectormap.route.RouteLineStyle
import com.kakao.vectormap.route.RouteLineStyles
import kotlinx.coroutines.launch
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class RunningActivity : ComponentActivity() {

    // 지도 관련
    private lateinit var mapView: MapView
    private var kakaoMap: KakaoMap? = null

    // 경로 표시용
    private var currentRoute: RouteLine? = null           // ✅ 내 러닝 경로(파랑)
    private var guideRoute: RouteLine? = null             // ✅ 따라뛰기 가이드 라인(빨강/회색 등)
    private val runningPath = mutableListOf<LatLng>()     // 내 실제 이동 경로

    // 따라뛰기 선택된 루트 id
    private var followRouteId: Long = -1L                 // ✅ 추가

    // 위치 추적용
    private lateinit var fused: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private var isRunning = false
    private var lastKnownLocation: android.location.Location? = null

    private var userMarkerBitmap: Bitmap? = null
    private var isPaused = false
    private var autoFollow = true

    // 상단 UI (거리/시간)
    private lateinit var txtTime: TextView
    private lateinit var txtDistance: TextView
    private var startTime = 0L
    private var elapsedTime = 0L
    private var totalDistance = 0.0

    private lateinit var gpsBtn: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ✅ 따라뛰기 루트 id 받기 (RunningScreen에서 putExtra한 값)
        followRouteId = intent.getLongExtra("follow_route_id", -1L)

        // ✅ 루트 레이아웃 생성
        val root = FrameLayout(this)
        mapView = MapView(this)

        root.addView(
            mapView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        // 마커 설정 (사용자 커스텀 이미지)
        val userMarkerImageUri = intent.getStringExtra("userMarkerImageUri")
        if (!userMarkerImageUri.isNullOrBlank()) {
            userMarkerBitmap = loadUserRunningMarkerBitmap(this, userMarkerImageUri)
        }

        // ✅ 상단 UI (거리 & 시간)
        val infoLayout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            setPadding(32, 64, 32, 0)
            setBackgroundColor(Color.parseColor("#66000000"))
        }
        txtTime = TextView(this).apply {
            text = "⏱ 00:00"
            setTextColor(Color.WHITE)
            textSize = 18f
        }
        txtDistance = TextView(this).apply {
            text = "📍 0.00 km"
            setTextColor(Color.WHITE)
            textSize = 18f
            setPadding(48, 0, 0, 0)
        }
        infoLayout.addView(txtTime)
        infoLayout.addView(txtDistance)

        val infoParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        }
        root.addView(infoLayout, infoParams)

        // ✅ 하단 “러닝 종료” 버튼
        val stopBtn = MaterialButton(this).apply {
            text = "러닝 종료"
            setBackgroundColor(Color.RED)
            setTextColor(Color.WHITE)
            setOnClickListener { stopRunningAndFinish() }
        }
        val btnParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.END or Gravity.BOTTOM
            marginEnd = 48
            bottomMargin = 96
        }
        root.addView(stopBtn, btnParams)

        // ✅ “현재 위치” 버튼 (누르면 autoFollow ON + 현재 위치로)
        gpsBtn = MaterialButton(this).apply {
            text = "현재 위치"
            setBackgroundColor(Color.parseColor("#2196F3"))
            setTextColor(Color.WHITE)

            val sizeW = (130 * resources.displayMetrics.density).toInt()
            val sizeH = (50 * resources.displayMetrics.density).toInt()

            layoutParams = FrameLayout.LayoutParams(sizeW, sizeH).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                bottomMargin = (24 * resources.displayMetrics.density).toInt()
            }
            visibility = View.VISIBLE

            setOnClickListener {
                recenterToCurrentLocation()
            }
        }
        root.addView(gpsBtn)

        // 하단 왼쪽 “일시정지/재개” 버튼
        val pauseBtn = MaterialButton(this).apply {
            text = "일시정지"

            val sizeW = (120 * resources.displayMetrics.density).toInt()
            val sizeH = (50 * resources.displayMetrics.density).toInt()

            layoutParams = FrameLayout.LayoutParams(sizeW, sizeH).apply {
                gravity = Gravity.BOTTOM or Gravity.START
                bottomMargin = (24 * resources.displayMetrics.density).toInt()
                leftMargin = (24 * resources.displayMetrics.density).toInt()
            }

            setBackgroundColor(Color.DKGRAY)
            setTextColor(Color.WHITE)
            setOnClickListener { togglePause(this) }
        }
        val pauseParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.START or Gravity.BOTTOM
            marginStart = 48
            bottomMargin = 96
        }
        root.addView(pauseBtn, pauseParams)

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
            override fun onMapError(error: Exception?) { error?.printStackTrace() }
        }, object : KakaoMapReadyCallback() {
            override fun onMapReady(map: KakaoMap) {
                kakaoMap = map

                // ✅ 드래그/핀치하면 자동으로 autoFollow OFF
                map.setOnCameraMoveStartListener { _, gestureType ->
                    if (gestureType != GestureType.Unknown) {
                        autoFollow = false
                    }
                }

                // ✅ (선택) 위젯 클릭 시 autoFollow ON
                map.setOnMapWidgetClickListener { kakaoMapObj, _, _ ->
                    autoFollow = true
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

                // ✅ 따라뛰기 루트가 있으면: 지도에 "가이드 폴리라인" 한번만 그리기
                if (followRouteId != -1L) {
                    drawGuideRouteOnce(map, followRouteId)
                }

                // ✅ 지도 로드 완료 후 러닝 시작
                startRunning()
            }

            override fun getPosition(): LatLng = LatLng.from(37.56, 126.97)
            override fun getZoomLevel(): Int = 15
        })
    }

    // ✅ 서버에서 루트 상세 받아서 "가이드 라인" 한 번만 그리기
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

    // 마커 비트맵 조정 함수
    private fun loadUserRunningMarkerBitmap(context: Context, uriStr: String): Bitmap? {
        return try {
            val uri = Uri.parse(uriStr)

            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri).use { ins ->
                BitmapFactory.decodeStream(ins, null, boundsOpts)
            }

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

            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            var bmp: Bitmap? = null
            context.contentResolver.openInputStream(uri).use { ins ->
                bmp = BitmapFactory.decodeStream(ins, null, opts)
            }
            var decoded = bmp ?: return null

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

            val target = 200
            val scaled = Bitmap.createScaledBitmap(decoded, target, target, true)
            getCircularBitmap(scaled)

        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun togglePause(btn: MaterialButton) {
        if (!isRunning) return

        isPaused = !isPaused
        btn.text = if (isPaused) "다시시작" else "일시정지"

        if (isPaused) {
            fused.removeLocationUpdates(locationCallback)
        } else {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                fused.requestLocationUpdates(locationRequest, locationCallback, mainLooper)
            } else {
                Toast.makeText(this, "위치 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
            }
        }
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
            if (!isRunning || isPaused) return
            val map = kakaoMap ?: return
            val manager = map.routeLineManager ?: return

            for (loc in result.locations) {
                lastKnownLocation = loc // ✅ 현재 위치 저장

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
        val r = 6371000.0
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLng = Math.toRadians(b.longitude - a.longitude)
        val sa = sin(dLat / 2).pow(2.0)
        val sb = cos(Math.toRadians(a.latitude)) * cos(Math.toRadians(b.latitude)) *
                sin(dLng / 2).pow(2.0)
        return 2 * r * asin(sqrt(sa + sb))
    }

    // ✅ 상단 UI 갱신
    private fun updateUI() {
        elapsedTime = (SystemClock.elapsedRealtime() - startTime) / 1000
        val minutes = elapsedTime / 60
        val seconds = elapsedTime % 60
        txtTime.text = "⏱ %02d:%02d".format(minutes, seconds)
        txtDistance.text = "📍 %.2f km".format(totalDistance / 1000.0)
    }

    // ✅ 러닝 시작
    @SuppressLint("MissingPermission")
    private fun startRunning() {
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
        isRunning = true
        isPaused = false

        fused.lastLocation.addOnSuccessListener { loc ->
            kakaoMap?.let { map ->
                if (loc != null) {
                    lastKnownLocation = loc
                    val start = LatLng.from(loc.latitude, loc.longitude)
                    runningPath.add(start)
                    moveCameraTo(map, start)
                    updateMarker(map, start)
                }
            }
        }

        fused.requestLocationUpdates(locationRequest, locationCallback, mainLooper)
        Toast.makeText(this, "러닝 시작!", Toast.LENGTH_SHORT).show()
    }

    private fun stopRunningAndFinish() {
        isRunning = false
        fused.removeLocationUpdates(locationCallback)

        val intent = Intent()
        intent.putExtra("runningDistance", totalDistance)
        intent.putExtra("runningTime", SystemClock.elapsedRealtime() - startTime)
        intent.putExtra("pathSize", runningPath.size)
        runningPath.forEachIndexed { i, latLng ->
            intent.putExtra("lat_$i", latLng.latitude)
            intent.putExtra("lng_$i", latLng.longitude)
        }
        setResult(RESULT_OK, intent)

        Toast.makeText(this, "러닝 종료!", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun moveCameraTo(map: KakaoMap, p: LatLng) {
        map.moveCamera(CameraUpdateFactory.newCenterPosition(p))
    }

    private fun updateMarker(map: KakaoMap, p: LatLng) {
        val labelManager = map.labelManager ?: return
        val layer = labelManager.layer ?: return
        layer.removeAll()

        val style = if (userMarkerBitmap != null) {
            LabelStyle.from(userMarkerBitmap)
        } else {
            LabelStyle.from(R.drawable.arrow)
        }
        layer.addLabel(LabelOptions.from(p).setStyles(style))
    }

    // ✅ 내 러닝 경로(파란색)만 계속 갱신
    private fun drawPath(manager: RouteLineManager) {
        if (runningPath.size < 2) return
        val layer = manager.layer

        // ✅ currentRoute만 지움 (guideRoute는 안 건드림)
        runCatching { currentRoute?.let { layer.remove(it) } }

        val style = RouteLineStyle.from(8f, Color.BLUE)
        val styles = RouteLineStyles.from(style)
        val seg = RouteLineSegment.from(runningPath).setStyles(styles)
        val options = RouteLineOptions.from(seg)

        currentRoute = layer.addRouteLine(options).apply { show() }
    }

    override fun onResume() { super.onResume(); mapView.resume() }
    override fun onPause()  { super.onPause();  mapView.pause() }
    override fun onDestroy(){ super.onDestroy(); mapView.finish() }

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
