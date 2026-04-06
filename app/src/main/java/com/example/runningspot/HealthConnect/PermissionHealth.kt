package com.example.runningspot.HealthConnect

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.activity.compose.setContent

class PermissionHealth : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Text("웨어러블 기기의 데이터를 통해 AI 분석 러닝 가이드를 제공하기 위해 권한이 필요합니다.")
        }
    }
}