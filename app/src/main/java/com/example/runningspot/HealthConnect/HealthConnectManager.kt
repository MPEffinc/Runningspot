package com.example.runningspot.HealthConnect

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord

class HealthConnectManager(private val context: Context) {
    private val healthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }

    fun checkAvailability(): Int {
        // sdk 상태 체크
        return HealthConnectClient.getSdkStatus(context, "com.google.android.apps.healthdata")
    }

    val permissions = setOf(
        // 필요 권한 목록
        HealthPermission.getReadPermission<HeartRateRecord>(),
        HealthPermission.getReadPermission<StepsRecord>(),
        HealthPermission.getReadPermission<DistanceRecord>(),
        HealthPermission.getReadPermission<ExerciseSessionRecord>()
    )

    suspend fun hasAllPermissions(): Boolean {
        // 권한 확인
        return healthConnectClient.permissionController.getGrantedPermissions()
            .containsAll(permissions)
    }

    // 특정 기간을 필터링하는 함수
    private fun getSessionRange(
        startTime: java.time.Instant,
        endTime: java.time.Instant
    ): TimeRangeFilter {
        return TimeRangeFilter.between(startTime, endTime)
    }

    // 걸음 수
    suspend fun readSessionSteps(startTime: java.time.Instant, endTime: java.time.Instant): Long {
        return try {
            val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    recordType = StepsRecord::class,
                    timeRangeFilter = getSessionRange(startTime, endTime)
                )
            )
            response.records.sumOf { it.count }
        } catch (e: Exception) {
            0L
        }
    }

    // 평균 심박수
    suspend fun readSessionAvgHeartRate(
        startTime: java.time.Instant,
        endTime: java.time.Instant
    ): Long {
        return try {
            val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    recordType = HeartRateRecord::class,
                    timeRangeFilter = getSessionRange(startTime, endTime)
                )
            )
            val samples = response.records.flatMap { it.samples }
            if (samples.isEmpty()) 0L else samples.map { it.beatsPerMinute }.average().toLong()
        } catch (e: Exception) {
            0L
        }
    }
    // 칼로리
    suspend fun readSessionCalories(startTime: java.time.Instant, endTime: java.time.Instant): Double {
        return try {
            val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    recordType = TotalCaloriesBurnedRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            )
            response.records.sumOf { it.energy.inKilocalories }
        } catch (e: Exception) {
            0.0
        }
    }
}