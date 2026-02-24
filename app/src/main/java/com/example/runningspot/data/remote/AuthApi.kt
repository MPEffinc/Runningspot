package com.example.runningspot.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

interface AuthApi {
    @POST("/auth/kakao")
    suspend fun kakaoToFirebase(@Body body: KakaoAuthRequest): KakaoAuthResponse

    // 로그인 직후 서버에 유저 등록/확인
    @GET("/me")
    suspend fun me(
        @Header("Authorization") auth: String
    ): MeResponse
}
