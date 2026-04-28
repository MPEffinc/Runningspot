package com.example.runningspot.data.remote

data class CreateRouteRequest(
    val title: String,
    val distance_m: Double,
    val start_lat: Double,
    val start_lng: Double,
    val end_lat: Double,
    val end_lng: Double,
    val visibility: String = "PUBLIC",
    val points: List<RoutePointDto> = emptyList(),
    val profile_image_url: String? = null,
    val wearable_steps: Long,
    val wearable_heart_rate: Long,
    val wearable_calories: Double
)
