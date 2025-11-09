package com.example.runningspot

import android.Manifest
import android.annotation.SuppressLint
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
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class RunningActivity : ComponentActivity() {

    // 지도 관련
    private lateinit var mapView: MapView
    private var kakaoMap: KakaoMap? = null

    // 경로 표시용
    private var currentRoute: RouteLine? = null
    private val runningPath = mutableListOf<LatLng>()

    // 위치 추적용
    private lateinit var fused: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private var isRunning = false

    // 상단 UI (거리/시간)
    private lateinit var txtTime: TextView
    private lateinit var txtDistance: TextView
    private var startTime = 0L
    private var elapsedTime = 0L
    private var totalDistance = 0.0

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

        val infoParams = android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = android.view.Gravity.TOP or android.view.Gravity.CENTER_HORIZONTAL
        }
        root.addView(infoLayout, infoParams)

        // ✅ 하단 “러닝 종료” 버튼
        val stopBtn = com.google.android.material.button.MaterialButton(this).apply {
            text = "러닝 종료"
            setBackgroundColor(Color.RED)
            setTextColor(Color.WHITE)
            setOnClickListener { stopRunningAndFinish() }
        }
        val btnParams = android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = android.view.Gravity.END or android.view.Gravity.BOTTOM
            marginEnd = 48
            bottomMargin = 96
        }
        root.addView(stopBtn, btnParams)

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
                startRunning()
            }

            override fun getPosition(): LatLng = LatLng.from(37.56, 126.97)
            override fun getZoomLevel(): Int = 15
        })
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
                moveCameraTo(map, p)
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

    // ✅ 상단 UI 갱신
    private fun updateUI() {
        elapsedTime = (SystemClock.elapsedRealtime() - startTime) / 1000
        val minutes = elapsedTime / 60
        val seconds = elapsedTime % 60
        txtTime.text = "⏱ %02d:%02d".format(minutes, seconds)
        txtDistance.text = "📍 %.2f km".format(totalDistance / 1000.0)
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
        isRunning = true

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
        Toast.makeText(this, "러닝 시작!", Toast.LENGTH_SHORT).show()
    }

    // ✅ 러닝 종료 및 결과 반환
    private fun stopRunningAndFinish() {
        isRunning = false
        fused.removeLocationUpdates(locationCallback)

        // 결과 경로를 Intent로 반환
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

    // ✅ 지도 관련 함수
    private fun moveCameraTo(map: KakaoMap, p: LatLng) {
        map.moveCamera(CameraUpdateFactory.newCenterPosition(p))
    }

    private fun updateMarker(map: KakaoMap, p: LatLng) {
        val labelManager = map.labelManager ?: return
        val layer = labelManager.layer ?: return
        layer.removeAll()
        val styles = labelManager.addLabelStyles(
            LabelStyles.from(LabelStyle.from(R.drawable.arrow))
        )
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