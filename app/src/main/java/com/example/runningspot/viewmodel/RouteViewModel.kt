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
            try {
                val routes = ApiClient.routeApi.getNearbyRoutes(lat, lng)
                _nearbyRoutes.value = routes
            } catch (e: Exception) {
                e.printStackTrace()
                _error.value = e.message
            }
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