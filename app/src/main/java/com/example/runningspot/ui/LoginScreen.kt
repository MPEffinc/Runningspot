package com.example.runningspot.ui

import android.app.Activity
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.auth.api.R as GoogleAuthR
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.kakao.sdk.auth.model.OAuthToken
import com.kakao.sdk.user.UserApiClient
import com.example.runningspot.R
import com.example.runningspot.data.remote.ApiClient
import com.example.runningspot.data.remote.KakaoAuthRequest
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Composable
fun LoginScreen(onLoginSuccess: (name: String?, profileUrl: String?, provider: String) -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    var nickname by remember { mutableStateOf<String?>(null) }
    var profileUrl by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    var showWelcome by remember { mutableStateOf(false) }

    fun completeLogin(provider: String, name: String?, photoUrl: String?) {
        nickname = name
        profileUrl = photoUrl
        showWelcome = true
        loading = false

        scope.launch {
            // Both providers briefly show the same welcome state before navigation.
            delay(650)
            onLoginSuccess(name, photoUrl, provider)
        }
    }

    // Compose lint 대응: LocalContext로 직접 문자열 조회하지 않고 stringResource를 사용
    val webClientId = stringResource(id = R.string.default_web_client_id)
    val gso = remember(webClientId) {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestIdToken(webClientId)
            .build()
    }
    val googleClient = remember(activity, gso) {
        activity?.let { GoogleSignIn.getClient(it, gso) }
    }

    val googleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val idToken = account.idToken

            if (idToken == null) {
                Toast.makeText(context, "ID Token 없음", Toast.LENGTH_SHORT).show()
                return@rememberLauncherForActivityResult
            }

            val credential = GoogleAuthProvider.getCredential(idToken, null)
            FirebaseAuth.getInstance()
                .signInWithCredential(credential)
                .addOnSuccessListener { authResult ->
                    val user = authResult.user
                    scope.launch {
                        try {
                            val firebaseUser = FirebaseAuth.getInstance().currentUser
                                ?: throw IllegalStateException("Firebase currentUser가 null")

                            val userData = mutableMapOf<String, Any>(
                                "provider" to "google",
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                            user?.displayName?.takeIf { it.isNotBlank() }?.let { userData["nickname"] = it }
                            user?.photoUrl?.toString()?.takeIf { it.isNotBlank() }?.let { userData["profileUrl"] = it }

                            FirebaseFirestore.getInstance()
                                .collection("users")
                                .document(firebaseUser.uid)
                                .set(userData, SetOptions.merge())
                                .await()
                        } catch (e: Exception) {
                            Log.w("GOOGLE", "users 문서 저장 실패", e)
                        }

                        completeLogin(
                            provider = "google",
                            name = user?.displayName,
                            photoUrl = user?.photoUrl?.toString()
                        )
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(context, "Firebase 로그인 실패", Toast.LENGTH_SHORT).show()
                    Log.e("GOOGLE", "firebase signIn failed", e)
                }

        } catch (e: ApiException) {
            Log.e("GOOGLE", "signIn failed code=${e.statusCode}", e)
            Toast.makeText(context, "구글 로그인 실패(${e.statusCode})", Toast.LENGTH_SHORT).show()
        }
    }

    fun kakaoLogin() {
        val act = activity ?: run {
            Toast.makeText(context, "Activity 컨텍스트를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        val callback: (OAuthToken?, Throwable?) -> Unit = { token, error ->
            if (error != null) {
                Log.e("KAKAO", "login failed", error)
                Toast.makeText(context, "카카오 로그인 실패", Toast.LENGTH_SHORT).show()
            } else if (token != null) {
                UserApiClient.instance.me { user, err ->
                    if (err != null || user == null) {
                        Log.e("KAKAO", "user info failed", err)
                        Toast.makeText(context, "카카오 사용자 정보 조회 실패", Toast.LENGTH_SHORT).show()
                        return@me
                    }

                    nickname = user.kakaoAccount?.profile?.nickname
                    profileUrl = user.kakaoAccount?.profile?.thumbnailImageUrl

                    val accessToken = token.accessToken
                    scope.launch {
                        try {
                            loading = true

                            val resp = ApiClient.authApi.kakaoToFirebase(
                                KakaoAuthRequest(kakaoAccessToken = accessToken)
                            )

                            FirebaseAuth.getInstance()
                                .signInWithCustomToken(resp.customToken)
                                .await()

                            val firebaseUser = FirebaseAuth.getInstance().currentUser
                                ?: throw IllegalStateException("Firebase currentUser가 null")

                            val userData = mutableMapOf<String, Any>(
                                "provider" to "kakao",
                                "updatedAt" to FieldValue.serverTimestamp()
                            )

                            if (!nickname.isNullOrBlank()) {
                                userData["nickname"] = nickname as String
                            }

                            if (!profileUrl.isNullOrBlank()) {
                                userData["profileUrl"] = profileUrl as String
                            }

                            FirebaseFirestore.getInstance()
                                .collection("users")
                                .document(firebaseUser.uid)
                                .set(userData, SetOptions.merge())
                                .await()

                            val idToken = FirebaseAuth.getInstance()
                                .currentUser
                                ?.getIdToken(true)
                                ?.await()
                                ?.token
                                ?: throw IllegalStateException("Firebase ID Token 발급 실패")

                            val me = ApiClient.authApi.me("Bearer $idToken")
                            Log.d("AUTH", "Server /me ok uid=${me.uid}, userId=${me.userId}")

                            val uid = FirebaseAuth.getInstance().currentUser?.uid
                            Log.d("AUTH", "Firebase signIn success uid=$uid")

                            completeLogin(
                                provider = "kakao",
                                name = nickname,
                                photoUrl = profileUrl
                            )
                        } catch (e: Exception) {
                            Log.e("AUTH", "Firebase custom token login failed", e)
                            Toast.makeText(
                                context,
                                "Firebase 연동 로그인 실패: ${e.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        } finally {
                            loading = false
                        }
                    }
                }
            }
        }

        val api = UserApiClient.instance
        if (api.isKakaoTalkLoginAvailable(context)) {
            api.loginWithKakaoTalk(act, callback = callback)
        } else {
            api.loginWithKakaoAccount(act, callback = callback)
        }
    }

    @Composable
    fun KakaoBrandButton(enabled: Boolean, onClick: () -> Unit) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFFEE500),
                contentColor = Color(0xFF191919)
            )
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(id = R.drawable.ic_kakaotalk_logo),
                    contentDescription = "Kakao bubble",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (loading) "처리 중..." else "카카오 로그인",
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }

    @Composable
    fun GoogleBrandButton(enabled: Boolean, onClick: () -> Unit) {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFDADCE0)),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(id = GoogleAuthR.drawable.googleg_standard_color_18),
                    contentDescription = "Google logo",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text("구글 계정으로 로그인", fontWeight = FontWeight.Medium, fontFamily = FontFamily.SansSerif)
            }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val logoSize = maxHeight * 0.30f

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(this@BoxWithConstraints.maxHeight * 0.34f),
                contentAlignment = Alignment.TopCenter
            ) {
                AppTopLogo(
                    modifier = Modifier.padding(top = 12.dp),
                    size = logoSize
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            if (showWelcome) {
                Image(
                    painter = rememberAsyncImagePainter(profileUrl),
                    contentDescription = "profile",
                    modifier = Modifier.size(80.dp)
                )
                Spacer(Modifier.height(8.dp))
                Text("환영합니다, ${nickname ?: "사용자"}님")
                Spacer(Modifier.height(24.dp))
            } else {
                Text(
                    "소셜 로그인",
                    fontSize = 12.sp,
                    color = Color(0xFF8A8A88),
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(16.dp))

                KakaoBrandButton(
                    enabled = activity != null && !loading,
                    onClick = { kakaoLogin() }
                )

                Spacer(modifier = Modifier.height(12.dp))

                GoogleBrandButton(
                    enabled = googleClient != null && !loading,
                    onClick = {
                        val intent = googleClient?.signInIntent
                        if (intent != null) {
                            googleLauncher.launch(intent)
                        } else {
                            Toast.makeText(context, "GoogleSignIn 초기화 실패", Toast.LENGTH_SHORT).show()
                        }
                    }
                )

                if (loading) {
                    Spacer(modifier = Modifier.height(14.dp))
                    CircularProgressIndicator()
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}