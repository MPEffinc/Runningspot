package com.example.runningspot.data.repository

import com.example.runningspot.data.remote.ApiClient
import com.example.runningspot.data.remote.CreateRunRecordRequest
import com.example.runningspot.data.remote.RoutePointDto
import com.example.runningspot.data.remote.RunHistoryDto
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await

class RunRepository {

    private suspend fun getBearerToken(): String {
        val user = FirebaseAuth.getInstance().currentUser
            ?: throw IllegalStateException("로그인이 필요합니다.")

        val token = user.getIdToken(true).await().token
            ?: throw IllegalStateException("인증 토큰을 가져오지 못했습니다.")

        return "Bearer $token"
    }

    suspend fun getMyRuns(): List<RunHistoryDto> {
        return ApiClient.runApi.getMyRuns(getBearerToken())
    }

    suspend fun createRun(
        distanceM: Double,
        durationMs: Long,
        endedAt: Long,
        startedAt: Long?,
        pathPairs: List<Pair<Double, Double>>,
        wearableSteps: Long,
        wearableHeartRate: Long,
        wearableCalories: Double
    ): Long {
        val body = CreateRunRecordRequest(
            distance_m = distanceM,
            duration_ms = durationMs,
            started_at= startedAt,
            ended_at = endedAt,
            points = pathPairs.mapIndexed { index, (lat, lng) ->
                RoutePointDto(
                    seq = index,
                    lat = lat,
                    lng = lng
                )
            },
            wearable_steps = wearableSteps,
            wearable_heart_rate = wearableHeartRate,
            wearable_calories = wearableCalories
        )

        return ApiClient.runApi.createRun(getBearerToken(), body).id
    }

    suspend fun deleteRun(runId: Long) {
        val response = ApiClient.runApi.deleteRun(getBearerToken(), runId)
        if (!response.isSuccessful) {
            throw IllegalStateException("러닝 기록 삭제 실패: ${response.code()}")
        }
    }
}