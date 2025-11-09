package com.example.runningspot

import android.app.Application
import com.kakao.vectormap.KakaoMapSdk
import com.kakao.sdk.common.KakaoSdk

class GlobalApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // 네이티브 앱 키 등록
        KakaoMapSdk.init(this, "8a6b18ca6d8ac2a7680c2b6f860815fc")
        KakaoSdk.init(this, "8a6b18ca6d8ac2a7680c2b6f860815fc")
    }
}