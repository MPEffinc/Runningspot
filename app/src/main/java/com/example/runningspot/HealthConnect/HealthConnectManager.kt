package com.example.runningspot.HealthConnect

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.StepsRecord

class HealthConnectManager(private val context: Context) {
    private val healthConnectClient by lazy { HealthConnectClient.Companion.getOrCreate(context) }

    fun checkAvailability(): Int {
        // sdk 상태 체크
        return HealthConnectClient.Companion.getSdkStatus(context, "com.google.android.apps.healthdata")
    }

    val permissions = setOf(
        // 필요 권한 목록
        HealthPermission.Companion.getReadPermission<HeartRateRecord>(),
        HealthPermission.Companion.getReadPermission<StepsRecord>(),
        HealthPermission.Companion.getReadPermission<DistanceRecord>(),
        HealthPermission.Companion.getReadPermission<ExerciseSessionRecord>()
    )

    suspend fun hasAllPermissions(): Boolean {
        // 권한 확인
        return healthConnectClient.permissionController.getGrantedPermissions().containsAll(permissions)
    }
}