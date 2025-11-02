package com.example.runningspot.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.google.android.gms.auth.api.signin.*
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.common.api.ApiException
import com.kakao.sdk.auth.model.OAuthToken
import com.kakao.sdk.user.UserApiClient

@Composable
fun LoginScreen(onLoginSuccess: (name: String?, profileUrl: String?, provider: String) -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    var nickname by remember { mutableStateOf<String?>(null) }
    var profileUrl by remember { mutableStateOf<String?>(null) }
    var provider by remember { mutableStateOf<String?>(null) }

    // ✅ Google SignIn 초기화
    val gso = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            // .requestIdToken("웹 클라이언트 ID")  // 서버 검증 필요하면 사용
            .build()
    }
    val googleClient = remember(activity) {
        // activity null일 수 있으니 널 세이프 처리
        activity?.let { GoogleSignIn.getClient(it, gso) }
    }

    /*
    LaunchedEffect(Unit) {
        val googleAccount = GoogleSignIn.getLastSignedInAccount(context)
        if (googleAccount != null) {
            onLoginSuccess(googleAccount.displayName, googleAccount.photoUrl?.toString(), "google")
            return@LaunchedEffect
        }
        // Kakao 토큰 체크
        UserApiClient.instance.accessTokenInfo { token, error ->
            if (error == null && token != null) {
                UserApiClient.instance.me { user, err ->
                    if (err == null && user != null) {
                        onLoginSuccess(
                            user.kakaoAccount?.profile?.nickname,
                            user.kakaoAccount?.profile?.thumbnailImageUrl,
                            "kakao"
                        )
                    }
                }
            }
        }
    } */

    // ✅ Google 로그인 Launcher
    val googleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            nickname = account?.displayName
            profileUrl = account?.photoUrl?.toString()
            provider = "google"
            onLoginSuccess(nickname, profileUrl, provider!!)      // ✅ 메인으로 전환
        } catch (e: ApiException) {
            // 상태코드로 원인 파악: 10(DEVELOPER_ERROR/SHA1 미등록), 12500(설정 이슈), 7(네트워크)
            android.util.Log.e("GOOGLE", "signIn failed code=${e.statusCode}", e)
            Toast.makeText(context, "구글 로그인 실패(${e.statusCode})", Toast.LENGTH_SHORT).show()
        } catch (t: Throwable) {
            android.util.Log.e("GOOGLE", "signIn failed", t)
            Toast.makeText(context, "구글 로그인 실패", Toast.LENGTH_SHORT).show()
        }
    }

    // ✅ Kakao 로그인 함수
    fun kakaoLogin() {
        val act = activity ?: run {
            Toast.makeText(context, "Activity 컨텍스트를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }
        val callback: (OAuthToken?, Throwable?) -> Unit = { token, error ->
            if (error != null) {
                android.util.Log.e("KAKAO", "login failed", error)
                Toast.makeText(context, "카카오 로그인 실패", Toast.LENGTH_SHORT).show()
            } else if (token != null) {
                UserApiClient.instance.me { user, err ->
                    if (err != null || user == null) {
                        android.util.Log.e("KAKAO", "user info failed", err)
                        Toast.makeText(context, "카카오 사용자 정보 조회 실패", Toast.LENGTH_SHORT).show()
                    } else {
                        nickname = user.kakaoAccount?.profile?.nickname
                        profileUrl = user.kakaoAccount?.profile?.thumbnailImageUrl
                        provider = "kakao"
                        onLoginSuccess(nickname, profileUrl, provider!!) // ✅ 메인으로 전환
                    }
                }
            }
        }

        val api = UserApiClient.instance
        if (api.isKakaoTalkLoginAvailable(context)) {
            api.loginWithKakaoTalk(act, callback = callback)      // ✅ Activity 필수
        } else {
            api.loginWithKakaoAccount(act, callback = callback)    // ✅ Activity 필수
        }
    }

    // ✅ UI
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {

            if (nickname != null) {
                // 로그인 성공 시 프로필 미리보기
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

                // ✅ Kakao 로그인 버튼
                Button(
                    onClick = { kakaoLogin() },
                    colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.primary),
                    enabled = activity != null
                ) {
                    Text("카카오로 로그인")
                }

                Spacer(Modifier.height(8.dp))

                // ✅ Google 로그인 버튼
                Button(
                    onClick = {
                        val intent = googleClient?.signInIntent
                        if (intent != null) googleLauncher.launch(intent)
                        else Toast.makeText(context, "GoogleSignIn 초기화 실패", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.secondary),
                    enabled = googleClient != null                               // ✅ 클라이언트 준비 체크
                ) {
                    Text("Google로 로그인")
                }
            }
        }
    }
}