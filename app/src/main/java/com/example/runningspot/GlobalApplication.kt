package com.example.runningspot

import android.app.Application
import com.kakao.vectormap.KakaoMapSdk
import com.kakao.sdk.common.KakaoSdk

class GlobalApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // 네이티브 앱 키 등록
        KakaoMapSdk.init(this, "761f8d0c71257bbbcebf7f4b89082f9f")
        KakaoSdk.init(this, "761f8d0c71257bbbcebf7f4b89082f9f")
    }
}