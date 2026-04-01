package com.example.runningspot

import android.app.Application
import android.util.Log
import com.kakao.vectormap.KakaoMapSdk
import com.kakao.sdk.common.KakaoSdk

class GlobalApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // SDK init 예외로 앱이 시작 단계에서 종료되지 않도록 보호
        runCatching {
            KakaoMapSdk.init(this, "761f8d0c71257bbbcebf7f4b89082f9f")
            KakaoSdk.init(this, "761f8d0c71257bbbcebf7f4b89082f9f")
        }.onFailure { t ->
            Log.e("GlobalApplication", "Kakao SDK init failed", t)
        }
    }
}