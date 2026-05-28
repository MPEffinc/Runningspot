package com.example.runningspot.data.remote

data class CreateRunRecordRequest(
    val distance_m: Double,
    val duration_ms: Long,
    val ended_at: Long,
    val started_at: Long ?= null,
    val points: List<RoutePointDto> = emptyList()
)