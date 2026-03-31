package com.example.runningspot.ui

import android.app.Activity
import android.app.DownloadManager
import android.util.Log
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
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.GoogleAuthProvider
import com.kakao.sdk.auth.model.OAuthToken
import com.kakao.sdk.user.UserApiClient
import com.example.runningspot.R
import com.example.runningspot.data.remote.ApiClient
import com.example.runningspot.data.remote.KakaoAuthRequest
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import okhttp3.OkHttpClient
import okhttp3.Request
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@Composable
fun LoginScreen(onLoginSuccess: (name: String?, profileUrl: String?, provider: String) -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    var nickname by remember { mutableStateOf<String?>(null) }
    var profileUrl by remember { mutableStateOf<String?>(null) }
    var provider by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    // ✅ Google SignIn 초기화
    val gso = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestIdToken(
                context.getString(R.string.default_web_client_id)
            )
            .build()
    }
    val googleClient = remember(activity) {
        // activity null일 수 있으니 널 세이프 처리
        activity?.let { GoogleSignIn.getClient(it, gso) }
    }

    val client = remember { OkHttpClient() }

    // ✅ Google 로그인 Launcher
    /*val googleLauncher = rememberLauncherForActivityResult(
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
                            val result = testUidFromServer("http://192.168.123.128:4000"/*"http://10.0.2.2:4000/"*/)
                            Log.d("UID_TEST", "server response=$result")

                            Toast.makeText(context, "서버 UID 테스트 성공", Toast.LENGTH_SHORT).show()

                            onLoginSuccess(
                                user?.displayName,
                                user?.photoUrl?.toString(),
                                "google"
                            )
                        } catch (e: Exception) {
                            Log.e("UID_TEST", "UID test failed", e)
                            Toast.makeText(
                                context,
                                "UID 테스트 실패: ${e.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
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
    }*/
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
                    onLoginSuccess(
                        user?.displayName,
                        user?.photoUrl?.toString(),
                        "google"
                    )
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

    // ✅ Kakao 로그인 함수
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

                            val firebaseUser = FirebaseAuth.getInstance().currentUser
                                ?: throw IllegalStateException("Firebase currentUser가 null")

                            val currentNickname = nickname
                            val currentProfileUrl = profileUrl

                            val userData = mutableMapOf<String, Any>(
                                "provider" to "kakao",
                                "updatedAt" to FieldValue.serverTimestamp()
                            )

                            if (!currentNickname.isNullOrBlank()) {
                                userData["nickname"] = currentNickname
                            }

                            if (!currentProfileUrl.isNullOrBlank()) {
                                userData["profileUrl"] = currentProfileUrl
                            }

                            FirebaseFirestore.getInstance()
                                .collection("users")
                                .document(firebaseUser.uid)
                                .set(userData, SetOptions.merge())
                                .await()
                            // Firebase ID Token 발급 (중요: customToken이 아니라 idToken을 서버에 보냄)
                            val idToken = FirebaseAuth.getInstance()
                                .currentUser
                                ?.getIdToken(true)
                                ?.await()
                                ?.token
                                ?: throw IllegalStateException("Firebase ID Token 발급 실패")

                            // /me 호출 -> 서버가 MySQL users에 생성/조회
                            val me = ApiClient.authApi.me("Bearer $idToken")
                            Log.d("AUTH", "Server /me ok uid=${me.uid}, userId=${me.userId}")

                            // 이제 Firebase 콘솔 Users에 뜸
                            val uid = FirebaseAuth.getInstance().currentUser?.uid
                            Log.d("AUTH", "Firebase signIn success uid=$uid")

                            onLoginSuccess(nickname, currentProfileUrl, provider!!)
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
    fun LoginButtonKakao(
        text: String,
        enabled: Boolean,
        onClick: () -> Unit
    ) {
        val kakaoYellow = Color(0xFFFEE500)
        val kakaoText = Color(0xDE000000)

        Image(
            painter = painterResource(id = R.drawable.ic_kakao_symbol), // 👉 공식 버튼 이미지
            contentDescription = "Kakao Login",
            modifier = Modifier
                .width(210.dp)
                .height(56.dp)
                .clickable { kakaoLogin() },
            contentScale = ContentScale.FillBounds
        )
    }

    @Composable
    fun LoginButtonGoogle(
        text: String,
        enabled: Boolean,
        onClick: () -> Unit
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_google_logo), // 👉 공식 버튼 이미지
            contentDescription = "Google Login",
            modifier = Modifier
                .width(210.dp)        // ⭐ 원하는 크기
                .height(56.dp)        // 공식 비율 유지
                .clickable {
                    val intent = googleClient?.signInIntent
                    if (intent != null) {
                        googleLauncher.launch(intent)
                    } else {
                        Toast.makeText(context, "GoogleSignIn 초기화 실패", Toast.LENGTH_SHORT).show()
                    }
                },
            contentScale = ContentScale.FillBounds
        )
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
                // ✅ Kakao 로그인 버튼
                LoginButtonKakao(
                    text = "카카오 로그인",
                    enabled = activity != null,
                    onClick = { kakaoLogin() }
                )

                Spacer(modifier = Modifier.height(12.dp))

// ✅ Google 로그인 버튼
                LoginButtonGoogle(
                    text = "Google로 로그인",
                    enabled = googleClient != null,
                    onClick = {
                        val intent = googleClient?.signInIntent
                        if (intent != null) {
                            googleLauncher.launch(intent)
                        } else {
                            Toast.makeText(context, "GoogleSignIn 초기화 실패", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }
    }
}