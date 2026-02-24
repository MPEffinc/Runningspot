package com.example.runningspot.data.remote

data class RouteSummary(
    val id: Long,
    val title: String,
    val distance_m: Double,
    val start_lat: Double,
    val start_lng: Double,
    val visibility: String?
)

data class RouteDetail(
    val id: Long,
    val title: String,
    val distance_m: Double,
    val start_lat: Double,
    val start_lng: Double,
    val end_lat: Double,
    val end_lng: Double,
    val points: List<LatLngDto>
)

data class LatLngDto(val lat: Double, val lng: Double)