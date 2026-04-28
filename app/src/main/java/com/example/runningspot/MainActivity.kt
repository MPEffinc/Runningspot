package com.example.runningspot

import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowInsetsControllerCompat
import com.example.runningspot.ui.LoginScreen
import com.example.runningspot.ui.MainScreen
import com.example.runningspot.ui.SplashScreen
import com.example.runningspot.ui.theme.RunningSpotTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import com.example.runningspot.data.remote.PrefetchedLocation

import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import android.Manifest
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

private enum class AppEntryState {
    Splash,
    Login,
    Main
}

private val PrefetchedLocationSaver = listSaver<PrefetchedLocation, Double>(
    save = { listOf(it.lat, it.lng) },
    restore = { restored -> PrefetchedLocation(restored[0], restored[1]) }
)
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Keep status bar icons readable while using edge-to-edge layouts.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        )
        super.onCreate(savedInstanceState)
        val requestNotificationPermission = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            // 필요하면 로그 추가
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        GoalReminderScheduler.schedule(this)

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

                val context = LocalContext.current
                var pendingEntryState by rememberSaveable { mutableStateOf(AppEntryState.Login) }
                var prefetchedLocation by remember {
                    mutableStateOf<PrefetchedLocation?>(null)
                }

                var splashReady by rememberSaveable { mutableStateOf(false) }
                var initVersion by rememberSaveable { mutableIntStateOf(0) }

                LaunchedEffect(initVersion) {
                    splashReady = false

                    prefetchedLocation = preloadLocation(context)

                    val currentUser = FirebaseAuth.getInstance().currentUser
                    if (currentUser == null) {
                        pendingEntryState = AppEntryState.Login
                        splashReady = true
                        return@LaunchedEffect
                    }

                    runCatching {
                        val doc = FirebaseFirestore.getInstance()
                            .collection("users")
                            .document(currentUser.uid)
                            .get()
                            .await()

                        userName = doc.getString("nickname") ?: currentUser.displayName
                        userProfile = doc.getString("profileUrl") ?: currentUser.photoUrl?.toString()
                        loginProvider = doc.getString("provider")
                            ?: currentUser.providerData.firstOrNull { it.providerId != "firebase" }?.providerId
                                ?.let { providerId ->
                                    when {
                                        providerId.contains("google") -> "google"
                                        providerId.contains("kakao") -> "kakao"
                                        else -> providerId
                                    }
                                }
                    }.onFailure {
                        userName = currentUser.displayName
                        userProfile = currentUser.photoUrl?.toString()
                    }

                    pendingEntryState = AppEntryState.Main
                    splashReady = true
                }

                when (appEntryState) {
                    AppEntryState.Splash -> {
                        SplashScreen(
                            isReady = splashReady,
                            onFinished = {
                                appEntryState = pendingEntryState
                            }
                        )
                    }

                    AppEntryState.Login -> {
                        LoginScreen { name, profile, provider ->
                            userName = name
                            userProfile = profile
                            loginProvider = provider
                            appEntryState = AppEntryState.Main
                            pendingEntryState = AppEntryState.Main
                            initVersion += 1
                        }
                    }

                    AppEntryState.Main -> {
                        MainScreen(
                            userName = userName,
                            userProfile = userProfile,
                            provider = loginProvider,
                            initialLocation = prefetchedLocation,
                            onLogout = {
                                userName = null
                                userProfile = null
                                loginProvider = null
                                prefetchedLocation = null
                                pendingEntryState = AppEntryState.Login
                                appEntryState = AppEntryState.Login
                            }
                        )
                    }
                }
            }
        }
    }
}
private suspend fun preloadLocation(context: Context): PrefetchedLocation? =
    withTimeoutOrNull(2500) {
        suspendCancellableCoroutine { cont ->
            val fine = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
            val coarse = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )

            if (fine != PackageManager.PERMISSION_GRANTED &&
                coarse != PackageManager.PERMISSION_GRANTED
            ) {
                cont.resume(null)
                return@suspendCancellableCoroutine
            }

            val fused = LocationServices.getFusedLocationProviderClient(context)

            fused.lastLocation
                .addOnSuccessListener { lastLoc ->
                    if (cont.isCompleted) return@addOnSuccessListener

                    if (lastLoc != null) {
                        cont.resume(
                            PrefetchedLocation(
                                lat = lastLoc.latitude,
                                lng = lastLoc.longitude
                            )
                        )
                    } else {
                        val cts = CancellationTokenSource()
                        fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                            .addOnSuccessListener { loc ->
                                if (cont.isCompleted) return@addOnSuccessListener
                                if (loc != null) {
                                    cont.resume(
                                        PrefetchedLocation(
                                            lat = loc.latitude,
                                            lng = loc.longitude
                                        )
                                    )
                                } else {
                                    cont.resume(null)
                                }
                            }
                            .addOnFailureListener {
                                if (!cont.isCompleted) cont.resume(null)
                            }
                            .addOnCanceledListener {
                                if (!cont.isCompleted) cont.resume(null)
                            }
                    }
                }
                .addOnFailureListener {
                    if (!cont.isCompleted) cont.resume(null)
                }
                .addOnCanceledListener {
                    if (!cont.isCompleted) cont.resume(null)
                }
        }
    }
