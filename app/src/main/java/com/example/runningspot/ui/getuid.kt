package com.example.runningspot.ui

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request


suspend fun testUidFromServer(baseUrl: String): String = withContext(Dispatchers.IO) {
    val user = FirebaseAuth.getInstance().currentUser
        ?: throw IllegalStateException("Firebase 로그인 상태가 아님")

    val token = user.getIdToken(false).await().token
        ?: throw IllegalStateException("ID Token 없음")

    Log.d("UID_TEST", "➡️ calling $baseUrl/test/uid token_prefix=${token.take(20)}")

    val client = OkHttpClient()
    val req = Request.Builder()
        .url("$baseUrl/test/uid")
        .addHeader("Authorization", "Bearer $token")
        .get()
        .build()

    client.newCall(req).execute().use { res ->
        val body = res.body?.string().orEmpty()
        Log.d("UID_TEST", "⬅️ status=${res.code} body=$body")
        if (!res.isSuccessful) throw IllegalStateException("HTTP ${res.code}: $body")
        body
    }
}