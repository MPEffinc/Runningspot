package com.example.runningspot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.example.runningspot.ui.LoginScreen
import com.example.runningspot.ui.MainScreen
import com.google.firebase.auth.FirebaseAuth

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            var isLoggedIn by rememberSaveable { mutableStateOf(false) }
            var userName by rememberSaveable { mutableStateOf<String?>(null) }
            var userProfile by rememberSaveable { mutableStateOf<String?>(null) }
            var loginProvider by rememberSaveable { mutableStateOf<String?>(null) }
            val auth = FirebaseAuth.getInstance()
            if (auth.currentUser == null) {
                auth.signInAnonymously()
            }

            if (isLoggedIn) {
                MainScreen(
                    userName = userName,
                    userProfile = userProfile,
                    provider = loginProvider,
                    onLogout = {
                        isLoggedIn = false
                        userName = null
                        userProfile = null
                        loginProvider = null
                    }
                )
            } else {
                LoginScreen { name, profile, provider ->
                    userName = name
                    userProfile = profile
                    loginProvider = provider
                    isLoggedIn = true
                }
            }
        }
    }
}