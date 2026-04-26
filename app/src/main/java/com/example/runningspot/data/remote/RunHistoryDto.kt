package com.example.runningspot.data.remote

data class RunHistoryDto(
    val id: Long,
    val distance_m: Double,
    val duration_ms: Long,
    val ended_at: Long,
    val started_at: Long? = null,
    val points: List<RoutePointDto> = emptyList(),
    val wearable_steps: Long = 0L,
    val wearable_heart_rate: Long = 0L,
    val wearable_calories: Double = 0.0
)