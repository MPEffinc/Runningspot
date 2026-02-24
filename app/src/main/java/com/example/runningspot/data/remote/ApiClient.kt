package com.example.runningspot.data.remote

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ApiClient {
    private const val BASE_URL = /*"http://192.168.123.128:4000/"*/"http://10.0.2.2:4000/"/*"http://192.168.219.129:4000/"*/

    private val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor(logging)
        .build()

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val routeApi: RouteApi by lazy { retrofit.create(RouteApi::class.java) }

    val authApi: com.example.runningspot.data.remote.AuthApi by lazy {
        retrofit.create(com.example.runningspot.data.remote.AuthApi::class.java)
    }
}
