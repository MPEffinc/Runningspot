package com.example.runningspot

import android.app.Application
import com.kakao.vectormap.KakaoMapSdk
import com.kakao.sdk.common.KakaoSdk

class GlobalApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // 네이티브 앱 키 등록
        KakaoMapSdk.init(this, "a79a645b5b5c9b0707a7ebe4316ef1ad")
        KakaoSdk.init(this, "a79a645b5b5c9b0707a7ebe4316ef1ad")
    }
}

