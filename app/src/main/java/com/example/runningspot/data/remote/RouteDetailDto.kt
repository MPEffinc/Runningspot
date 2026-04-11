package com.example.runningspot.data.remote

import com.google.gson.annotations.SerializedName

data class RouteDetailDto(
    val id: Long,
    val title: String,
    @SerializedName(value = "distance_m", alternate = ["distanceM"]) val distance_m: Double,
    @SerializedName(value = "start_lat", alternate = ["startLat"]) val start_lat: Double,
    @SerializedName(value = "start_lng", alternate = ["startLng"]) val start_lng: Double,
    @SerializedName(value = "points", alternate = ["routePoints", "path", "coordinates"]) val points: List<RoutePointDto> = emptyList(),
)
