package com.example.runningspot.data.remote

data class RouteDetailDto(
    val id: Long,
    val title: String,
    val distance_m: Double,
    val start_lat: Double,
    val start_lng: Double,
    val points: List<RoutePointDto>
)
