package com.example.runningspot

import android.app.Application
import android.util.Log
import com.kakao.vectormap.KakaoMapSdk
import com.kakao.sdk.common.KakaoSdk

class GlobalApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // 네이티브 앱 키 등록
        runCatching {
            KakaoMapSdk.init(this, "4a0414c61069e75577764b8ea65c26e9")
            KakaoSdk.init(this, "4a0414c61069e75577764b8ea65c26e9")
        }.onFailure { t ->
            Log.e("GlobalApplication", "Kakao SDK init failed", t)
        }
    }
}