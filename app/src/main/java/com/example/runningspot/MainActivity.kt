package com.example.runningspot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.example.runningspot.ui.LoginScreen
import com.example.runningspot.ui.MainScreen
import com.google.firebase.auth.FirebaseAuth
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowInsetsControllerCompat
import com.example.runningspot.ui.SplashScreen
import com.example.runningspot.ui.theme.RunningSpotTheme
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

private enum class AppEntryState {
    Splash,
    Login,
    Main
}
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        )
        super.onCreate(savedInstanceState)

        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }

        setContent {
            RunningSpotTheme {
                var appEntryState by rememberSaveable { mutableStateOf(AppEntryState.Splash) }
                var userName by rememberSaveable { mutableStateOf<String?>(null) }
                var userProfile by rememberSaveable { mutableStateOf<String?>(null) }
                var loginProvider by rememberSaveable { mutableStateOf<String?>(null) }
                var shouldResolveSession by rememberSaveable { mutableStateOf(false) }

                LaunchedEffect(shouldResolveSession) {
                    if (!shouldResolveSession) return@LaunchedEffect
                    shouldResolveSession = false

                    val currentUser = FirebaseAuth.getInstance().currentUser
                    if (currentUser == null) {
                        appEntryState = AppEntryState.Login
                        return@LaunchedEffect
                    }

                    runCatching {
                        val doc = FirebaseFirestore.getInstance()
                            .collection("users")
                            .document(currentUser.uid)
                            .get()
                            .await()

                        userName = doc.getString("nickname") ?: currentUser.displayName
                        userProfile =
                            doc.getString("profileUrl") ?: currentUser.photoUrl?.toString()
                        loginProvider = doc.getString("provider")
                            ?: currentUser.providerData.firstOrNull { it.providerId != "firebase" }?.providerId
                                ?.let { providerId ->
                                    if (providerId.contains("google")) "google" else if (providerId.contains(
                                            "kakao"
                                        )
                                    ) "kakao" else providerId
                                }
                    }.onFailure {
                        userName = currentUser.displayName
                        userProfile = currentUser.photoUrl?.toString()
                    }

                    appEntryState = AppEntryState.Main
                }

                when (appEntryState) {
                    AppEntryState.Splash -> {
                        SplashScreen(onFinished = { shouldResolveSession = true })
                    }

                    AppEntryState.Login -> {
                        LoginScreen { name, profile, provider ->
                            userName = name
                            userProfile = profile
                            loginProvider = provider
                            appEntryState = AppEntryState.Main
                        }
                    }

                    AppEntryState.Main -> {
                        MainScreen(
                            userName = userName,
                            userProfile = userProfile,
                            provider = loginProvider,
                            onLogout = {
                                userName = null
                                userProfile = null
                                loginProvider = null
                                appEntryState = AppEntryState.Login
                            }
                        )
                    }
                }
            }
        }
    }
}

