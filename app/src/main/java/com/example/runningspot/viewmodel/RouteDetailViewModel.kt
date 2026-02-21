package com.example.runningspot.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.runningspot.data.remote.ApiClient
import com.example.runningspot.data.remote.RouteDetailDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class RouteDetailViewModel : ViewModel() {

    private val _route = MutableStateFlow<RouteDetailDto?>(null)
    val route: StateFlow<RouteDetailDto?> = _route

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun loadRouteDetail(routeId: Long) {
        viewModelScope.launch {
            try {
                _error.value = null
                _route.value = ApiClient.routeApi.getRouteDetail(routeId)
            } catch (e: Exception) {
                e.printStackTrace()
                _error.value = e.message
            }
        }
    }
}
