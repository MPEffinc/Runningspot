package com.example.runningspot.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

interface RunApi {
    @GET("/runs")
    suspend fun getMyRuns(
        @Header("Authorization") auth: String
    ): List<RunHistoryDto>

    @POST("/runs")
    suspend fun createRun(
        @Header("Authorization") auth: String,
        @Body body: CreateRunRecordRequest
    ): CreateRunResponse

    @DELETE("/runs/{id}")
    suspend fun deleteRun(
        @Header("Authorization") auth: String,
        @Path("id") id: Long
    ): Response<Unit>
}