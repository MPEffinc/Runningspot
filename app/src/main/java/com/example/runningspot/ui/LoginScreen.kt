package com.example.runningspot.ui

import android.app.Activity
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.example.runningspot.data.remote.ApiClient
import com.example.runningspot.data.remote.KakaoAuthRequest
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.kakao.sdk.auth.model.OAuthToken
import com.kakao.sdk.user.UserApiClient
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Composable
fun LoginScreen(onLoginSuccess: (name: String?, profileUrl: String?, provider: String) -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()

    var nickname by remember { mutableStateOf<String?>(null) }
    var profileUrl by remember { mutableStateOf<String?>(null) }
    var provider by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    // Google SignIn 초기화 (현재는 "Firebase 로그인"까지는 안 함)
    val gso = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .build()
    }
    val googleClient = remember(activity) { activity?.let { GoogleSignIn.getClient(it, gso) } }

    val googleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            nickname = account?.displayName
            profileUrl = account?.photoUrl?.toString()
            provider = "google"
            onLoginSuccess(nickname, profileUrl, provider!!)
        } catch (e: ApiException) {
            Log.e("GOOGLE", "signIn failed code=${e.statusCode}", e)
            Toast.makeText(context, "구글 로그인 실패(${e.statusCode})", Toast.LENGTH_SHORT).show()
        } catch (t: Throwable) {
            Log.e("GOOGLE", "signIn failed", t)
            Toast.makeText(context, "구글 로그인 실패", Toast.LENGTH_SHORT).show()
        }
    }

    // 카카오 로그인 -> 서버에서 Firebase customToken 발급 -> FirebaseAuth 로그인
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
                // 1) 카카오 사용자 정보 조회(닉네임/프로필)
                UserApiClient.instance.me { user, err ->
                    if (err != null || user == null) {
                        Log.e("KAKAO", "user info failed", err)
                        Toast.makeText(context, "카카오 사용자 정보 조회 실패", Toast.LENGTH_SHORT).show()
                        return@me
                    }

                    nickname = user.kakaoAccount?.profile?.nickname
                    profileUrl = user.kakaoAccount?.profile?.thumbnailImageUrl
                    provider = "kakao"

                    // 2) 서버로 accessToken 보내서 Firebase customToken 받기
                    val accessToken = token.accessToken
                    scope.launch {
                        try {
                            loading = true

                            val resp = ApiClient.authApi.kakaoToFirebase(
                                KakaoAuthRequest(kakaoAccessToken = accessToken)
                            )

                            // 3) FirebaseAuth 로그인 (여기서 UID 생성됨)
                            FirebaseAuth.getInstance()
                                .signInWithCustomToken(resp.customToken)
                                .await()

                            // Firebase ID Token 발급 (중요: customToken이 아니라 idToken을 서버에 보냄)
                            val idToken = FirebaseAuth.getInstance()
                                .currentUser
                                ?.getIdToken(true)
                                ?.await()
                                ?.token

                            if (idToken == null) {
                                throw IllegalStateException("Firebase ID Token 발급 실패")
                            }

                            // /me 호출 -> 서버가 MySQL users에 생성/조회
                            val me = ApiClient.authApi.me("Bearer $idToken")
                            Log.d("AUTH", "Server /me ok uid=${me.uid}, userId=${me.userId}")

                            // 이제 Firebase 콘솔 Users에 뜸
                            val uid = FirebaseAuth.getInstance().currentUser?.uid
                            Log.d("AUTH", "Firebase signIn success uid=$uid")

                            onLoginSuccess(nickname, profileUrl, provider!!)
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

    // UI
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {

            if (loading) {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text("로그인 처리 중...")
                return@Column
            }

            if (nickname != null) {
                Image(
                    painter = rememberAsyncImagePainter(profileUrl),
                    contentDescription = "profile",
                    modifier = Modifier.size(80.dp)
                )
                Spacer(Modifier.height(8.dp))
                Text("환영합니다, ${nickname ?: "사용자"}님")
            } else {
                Text("소셜 로그인", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = { kakaoLogin() },
                    colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.primary),
                    enabled = activity != null
                ) {
                    Text("카카오로 로그인")
                }

                Spacer(Modifier.height(8.dp))

                Button(
                    onClick = {
                        val intent = googleClient?.signInIntent
                        if (intent != null) googleLauncher.launch(intent)
                        else Toast.makeText(context, "GoogleSignIn 초기화 실패", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.secondary),
                    enabled = googleClient != null
                ) {
                    Text("Google로 로그인")
                }
            }
        }
    }
}
