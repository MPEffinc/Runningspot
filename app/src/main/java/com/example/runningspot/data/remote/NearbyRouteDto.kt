package com.example.runningspot.data.remote

import com.google.gson.annotations.SerializedName

data class NearbyRouteDto(
    val id: Long,           // 루트 고유 ID (DB의 routes.id)
    val title: String,      // 루트 이름/제목
    @SerializedName(value = "distance_m", alternate = ["distanceM"]) val distance_m: Double, // 루트 총 거리(미터)
    @SerializedName(value = "start_lat", alternate = ["startLat"]) val start_lat: Double,  // 시작 지점 위도
    @SerializedName(value = "start_lng", alternate = ["startLng"]) val start_lng: Double,   // 시작 지점 경도
    @SerializedName(value = "profile_image_url", alternate = ["profileImageUrl"]) val profile_image_url: String?,
    @SerializedName(value = "nickname", alternate = ["userName", "authorName", "name"]) val nickname: String? = null,
    @SerializedName(value = "points", alternate = ["routePoints", "path", "coordinates"]) val points: List<RoutePointDto>? = null
)
