package com.example.runningspot.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.runningspot.data.remote.ApiClient
import com.example.runningspot.data.remote.RoutePointDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class PostDetailViewModel : ViewModel() {

    private val _routePoints = MutableStateFlow<List<RoutePointDto>>(emptyList())
    val routePoints: StateFlow<List<RoutePointDto>> = _routePoints

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun loadRoute(routeId: Long) = viewModelScope.launch {
        try {
            _loading.value = true
            _error.value = null

            val detail = ApiClient.routeApi.getRouteDetail(routeId)
            _routePoints.value = detail.points

        } catch (e: Exception) {
            _routePoints.value = emptyList()
            _error.value = e.message ?: "route load failed"
        } finally {
            _loading.value = false
        }
    }
}