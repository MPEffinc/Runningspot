package com.example.runningspot.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.runningspot.data.remote.ApiClient
import com.example.runningspot.data.remote.CreateRouteRequest
import com.example.runningspot.data.remote.NearbyRouteDto
import com.example.runningspot.data.repository.RouteRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class RouteViewModel : ViewModel() {

    private val _nearbyRoutes = MutableStateFlow<List<NearbyRouteDto>>(emptyList())
    val nearbyRoutes: StateFlow<List<NearbyRouteDto>> = _nearbyRoutes

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _createResult = MutableStateFlow<Long?>(null)
    val createResult: StateFlow<Long?> = _createResult

    private val repo = RouteRepository()

    fun loadNearbyRoutes(lat: Double, lng: Double) {
        viewModelScope.launch {
            //
            val mockRoutes = listOf(
                NearbyRouteDto(
                    id = 1001,
                    title = "🔥 테스트: 학교 운동장 한 바퀴",
                    distance_m = 1200.0,
                    start_lat = lat + 0.001, // 내 위치보다 북쪽으로 조금
                    start_lng = lng + 0.001  // 내 위치보다 동쪽으로 조금
                ),
                NearbyRouteDto(
                    id = 1002,
                    title = "🌳 테스트: 호수공원 러닝",
                    distance_m = 3500.0,
                    start_lat = lat - 0.001, // 내 위치보다 남쪽
                    start_lng = lng - 0.001  // 내 위치보다 서쪽
                ),
                NearbyRouteDto(
                    id = 1003,
                    title = "🏃‍♂️ 테스트: 강변 조깅 코스",
                    distance_m = 5000.0,
                    start_lat = lat + 0.002,
                    start_lng = lng - 0.002
                )
            )

            // 0.5초 딜레이 후 데이터 입력
            kotlinx.coroutines.delay(500)
            _nearbyRoutes.value = mockRoutes

            // 테스트 중 서버 코드가 실행 안 되게 리턴
            return@launch
            /*
            try {
                val routes = ApiClient.routeApi.getNearbyRoutes(lat, lng)
                _nearbyRoutes.value = routes
            } catch (e: Exception) {
                e.printStackTrace()
                _error.value = e.message
            }
            */
        }
    }

    fun createRoute(body: CreateRouteRequest) {
        viewModelScope.launch {
            try {
                val id = repo.createRoute(body)
                _createResult.value = id
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }
}
