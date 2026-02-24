package com.example.runningspot.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface RouteApi {

    @GET("/routes/nearby")
    suspend fun getNearbyRoutes(
        @Query("lat") lat: Double,
        @Query("lng") lng: Double,
        @Query("radiusKm") radiusKm: Double = 5.0
    ): List<NearbyRouteDto>

    @GET("/routes/{id}")
    suspend fun getRouteDetail(
        @Path("id") id: Long
    ): RouteDetailDto

    @POST("/routes")
    suspend fun createRoute(
        @Header("Authorization") auth: String,
        @Body body: CreateRouteRequest
    ): CreateRouteResponse
}
