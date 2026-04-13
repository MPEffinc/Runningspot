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
import java.io.IOException
import retrofit2.HttpException

class RouteViewModel : ViewModel() {

    private val _nearbyRoutes = MutableStateFlow<List<NearbyRouteDto>>(emptyList())
    val nearbyRoutes: StateFlow<List<NearbyRouteDto>> = _nearbyRoutes

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error
    private val _createRouteError = MutableStateFlow<String?>(null)
    val createRouteError: StateFlow<String?> = _createRouteError

    private val _createResult = MutableStateFlow<Long?>(null)
    val createResult: StateFlow<Long?> = _createResult

    private val repo = RouteRepository()

    fun loadNearbyRoutes(lat: Double, lng: Double) {
        viewModelScope.launch {
            try {
                val routes = ApiClient.routeApi.getNearbyRoutes(lat, lng)
                _nearbyRoutes.value = routes
                _error.value = null
            } catch (e: Exception) {
                e.printStackTrace()
                _error.value = mapToUserMessage(e)
            }
        }
    }

    fun createRoute(body: CreateRouteRequest) {
        viewModelScope.launch {
            _createResult.value = null
            _createRouteError.value = null
            try {
                val id = repo.createRoute(body)
                _createResult.value = id
                _createRouteError.value = null
            } catch (e: Exception) {
                _createRouteError.value = mapToUserMessage(e)
            }
        }
    }
    fun consumeCreateResult() {
        _createResult.value = null
    }

    fun consumeCreateRouteError() {
        _createRouteError.value = null
    }

    private fun mapToUserMessage(error: Throwable): String {
        return when (error) {
            is IOException -> "인터넷 연결을 확인해주세요."
            is HttpException -> "서버 연결이 원활하지 않습니다. 잠시 후 다시 시도해주세요."
            else -> error.message ?: "문제가 발생했습니다. 잠시 후 다시 시도해주세요."
        }
    }
}