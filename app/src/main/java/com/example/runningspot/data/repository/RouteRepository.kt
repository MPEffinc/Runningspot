package com.example.runningspot.data.repository

import com.example.runningspot.data.remote.ApiClient
import com.example.runningspot.data.remote.CreateRouteRequest
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await

class RouteRepository {

    suspend fun createRoute(body: CreateRouteRequest): Long {
        val user = FirebaseAuth.getInstance().currentUser
        val token = user?.getIdToken(true)?.await()?.token
            ?: throw IllegalStateException("로그인이 필요합니다.")

        val res = ApiClient.routeApi.createRoute("Bearer $token", body)
        return res.id
    }
}
