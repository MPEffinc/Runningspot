package com.example.runningspot.data.repository

import android.util.Log
import com.example.runningspot.data.remote.RouteDetail
import com.example.runningspot.data.remote.RouteSummary
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

private val client = OkHttpClient()
private val gson = Gson()

suspend fun fetchMyRoutes(baseUrl: String): List<RouteSummary> = withContext(Dispatchers.IO) {
    val user = FirebaseAuth.getInstance().currentUser
        ?: throw IllegalStateException("로그인 필요")

    val token = user.getIdToken(false).await().token
        ?: throw IllegalStateException("ID Token 없음")

    Log.d("ROUTE_API", "GET $baseUrl/routes/mine")

    val req = Request.Builder()
        .url("$baseUrl/routes/mine")
        .addHeader("Authorization", "Bearer $token")
        .get()
        .build()

    client.newCall(req).execute().use { res ->
        val body = res.body?.string().orEmpty()
        if (!res.isSuccessful) throw IllegalStateException("HTTP ${res.code}: $body")

        val type = object : TypeToken<List<RouteSummary>>() {}.type
        gson.fromJson(body, type)
    }
}

suspend fun fetchRouteDetail(baseUrl: String, routeId: Long): RouteDetail = withContext(Dispatchers.IO) {
    Log.d("ROUTE_API", "GET $baseUrl/routes/$routeId")

    val req = Request.Builder()
        .url("$baseUrl/routes/$routeId")
        .get()
        .build()

    client.newCall(req).execute().use { res ->
        val body = res.body?.string().orEmpty()
        if (!res.isSuccessful) throw IllegalStateException("HTTP ${res.code}: $body")
        gson.fromJson(body, RouteDetail::class.java)
    }
}