package com.example.runningspot.data.remote

data class NearbyRouteDto(
    val id: Long,           // 루트 고유 ID (DB의 routes.id)
    val title: String,      // 루트 이름/제목
    val distance_m: Double, // 루트 총 거리(미터)
    val start_lat: Double,  // 시작 지점 위도
    val start_lng: Double   // 시작 지점 경도
)
