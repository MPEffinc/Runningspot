package com.example.runningspot.HealthConnect

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.StepsRecord

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
        return healthConnectClient.permissionController.getGrantedPermissions().containsAll(permissions)
    }
}