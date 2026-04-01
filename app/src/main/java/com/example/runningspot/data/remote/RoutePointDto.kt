package com.example.runningspot.data.remote

import com.google.gson.annotations.SerializedName

data class RoutePointDto(
    @SerializedName(value = "seq", alternate = ["order", "index"]) val seq: Int,
    @SerializedName(value = "lat", alternate = ["latitude", "y"]) val lat: Double,
    @SerializedName(value = "lng", alternate = ["longitude", "x"]) val lng: Double
)
