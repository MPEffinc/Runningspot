package com.example.runningspot

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.*
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.example.runningspot.ui.calcCalories
import com.example.runningspot.ui.calcPace
import com.example.runningspot.ui.formatPace
import okhttp3.internal.concurrent.formatDuration
import org.json.JSONArray
import kotlin.run
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.example.runningspot.data.repository.CrewPost
import com.example.runningspot.ui.CrewChatScreen
import com.example.runningspot.data.repository.CrewRepository
import com.google.firebase.storage.FirebaseStorage
import java.util.UUID
import com.example.runningspot.data.remote.RouteSummary
import com.example.runningspot.data.repository.fetchMyRoutes
import com.example.runningspot.ui.RouteMapByRouteDetail
import com.example.runningspot.viewmodel.RouteDetailViewModel

import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Dialog
import com.example.runningspot.ui.theme.DialogContainer
import com.example.runningspot.ui.theme.DialogText
import com.example.runningspot.ui.theme.DialogTitle
import com.example.runningspot.ui.theme.RunningSpotTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.core.view.WindowCompat
import com.example.runningspot.data.repository.Comment
import com.example.runningspot.data.repository.CommunityPostRepository
import kotlinx.coroutines.tasks.await

class CommunityActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.parseColor("#FAFAF8")
        WindowCompat.getInsetsController(window, window.decorView)?.isAppearanceLightStatusBars = true

        val isCrewDetailMode = intent.getBooleanExtra("isCrewDetailMode", false)
        if (isCrewDetailMode) {
            val crewId = intent.getStringExtra("crewId") ?: run {
                finish()
                return
            }
            setContent {
                RunningSpotTheme {
                    CrewDetailScreen(crewId = crewId)
                }
            }
            return
        }
        val isChatMode = intent.getBooleanExtra("isChatMode", false)
        if (isChatMode) {
            val crewId = intent.getStringExtra("crewId") ?: run {
                finish()
                return
            }
            setContent {
                RunningSpotTheme {
                    CrewChatScreen(crewId = crewId)
                }
            }
            return
        }

        // 글 작성인지, 기존 게시글 보기인지 구분
        val isWriteMode = intent.getBooleanExtra("isWriteMode", false)
        val prefs = getSharedPreferences("community_prefs", MODE_PRIVATE)

        if (isWriteMode) {
            val userName = intent.getStringExtra("userName") ?: "익명 사용자"
            val writeType = intent.getStringExtra("writeType") ?: "post"
            val editDocId = intent.getStringExtra("editDocId")
            val initialTitle = intent.getStringExtra("initialTitle")
            val initialContent = intent.getStringExtra("initialContent")
            val initialImageUri = intent.getStringExtra("initialImageUri")

            setContent {
                RunningSpotTheme {
                    if (writeType == "crew") {
                        CrewWriteScreen(userName = userName)
                    } else {
                        WritePostScreen(
                            userName = userName,
                            prefs = prefs,
                            editDocId = editDocId,
                            initialTitle = initialTitle,
                            initialContent = initialContent,
                            initialImageUri = initialImageUri
                        )
                    }
                }
            }
        } else {
            //게시글 상세 보기 모드
            val title = intent.getStringExtra("title") ?: "제목 없음"
            val authorName = intent.getStringExtra("authorName") ?: "익명 작성자"
            val userName = intent.getStringExtra("userName") ?: "익명 사용자"
            val content = intent.getStringExtra("content") ?: ""
            val postId = intent.getIntExtra("postId", 0)
            val imageRes = intent.getIntExtra("imageRes", 0)
            val imageUri = intent.getStringExtra("imageUri")
            val savedLikes = prefs.getInt("likes_$postId", 0)
            val savedComments = prefs.getInt("comments_$postId", 0)
            val distanceKm = intent.getDoubleExtra("distanceKm", Double.NaN).takeIf { !it.isNaN() }
            val durationText = intent.getStringExtra("durationText")
            val pace = intent.getStringExtra("pace")
            val calories = intent.getDoubleExtra("calories", Double.NaN).takeIf { !it.isNaN() }
            val docId = intent.getStringExtra("docId")
            val routeId = intent.getLongExtra("routeId", -1L).let { if (it == -1L) null else it }
            setContent {
                RunningSpotTheme {
                    CommunityDetailScreen(
                        title = title,
                        hashtags = listOf("러닝"),
                        authorName = authorName,
                        userName = userName,
                        content = content,
                        postId = postId,
                        imageRes = imageRes,
                        imageUri = imageUri,
                        initialLikes = savedLikes,
                        initialComments = savedComments,
                        distanceKm = distanceKm,
                        pace = pace,
                        durationText = durationText,
                        calories = calories,
                        docId = docId,
                        routeId = routeId,
                        onEditRequest = { editableDocId, editableTitle, editableContent, editableImageUri ->
                            val editIntent = Intent(this@CommunityActivity, CommunityActivity::class.java).apply {
                                putExtra("isWriteMode", true)
                                putExtra("writeType", "post")
                                putExtra("userName", userName)
                                putExtra("editDocId", editableDocId)
                                putExtra("initialTitle", editableTitle)
                                putExtra("initialContent", editableContent)
                                putExtra("initialImageUri", editableImageUri)
                            }
                            startActivity(editIntent)
                        },
                        onUpdateStats = { likes, comments ->
                            prefs.edit()
                                .putInt("likes_$postId", likes)
                                .putInt("comments_$postId", comments)
                                .apply()
                        },
                    )
                }
            }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrewWriteScreen(userName: String) {
    val context = LocalContext.current

    var title by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var maxMembersText by remember { mutableStateOf("5") }
    var isSubmitting by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("크루 모집글 작성") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("모집 제목") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = location,
                onValueChange = { location = it },
                label = { Text("장소") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("설명") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
            )

            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = maxMembersText,
                onValueChange = { maxMembersText = it.filter { ch -> ch.isDigit() } },
                label = { Text("모집 인원수") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    if (isSubmitting) return@Button
                    if (title.isBlank() || location.isBlank() || description.isBlank()) {
                        Toast.makeText(context, "모든 항목을 입력해주세요", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    val maxMembers = maxMembersText.toLongOrNull() ?: 0L
                    if (maxMembers <= 0L) {
                        Toast.makeText(context, "모집 인원수를 올바르게 입력해주세요", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    val user = FirebaseAuth.getInstance().currentUser
                    if (user == null) {
                        Toast.makeText(context, "로그인이 필요합니다", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    val db = FirebaseFirestore.getInstance()
                    isSubmitting = true
                    val crewRef = db.collection("crews").document()   // ✅ ID를 미리 확보
                    val crewId = crewRef.id
                    val memberRef = crewRef.collection("members").document(user.uid)

                    db.runBatch { batch ->
                        batch.set(crewRef, hashMapOf(
                            "title" to title.trim(),
                            "location" to location.trim(),
                            "description" to description.trim(),
                            "userId" to user.uid,
                            "maxMembers" to maxMembers,
                            "currentMembers" to 1L, // ✅ 방장 포함
                            "createdAt" to FieldValue.serverTimestamp(),

                            // (선택) 크루 목록에서 마지막 메시지 보여주기 위한 필드
                            "lastMessage" to "",
                            "lastMessageAt" to FieldValue.serverTimestamp()
                        ))

                        // ✅ 방장 멤버 등록
                        batch.set(memberRef, mapOf(
                            "role" to "owner",
                            "joinedAt" to FieldValue.serverTimestamp()
                        ))
                    }.addOnSuccessListener {
                        isSubmitting = false
                        Toast.makeText(context, "크루 모집글 등록 완료", Toast.LENGTH_SHORT).show()
                        (context as? Activity)?.finish()
                    }.addOnFailureListener { e ->
                        isSubmitting = false
                        Toast.makeText(context, "등록 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                },
                enabled = !isSubmitting,
                colors = ButtonDefaults.buttonColors(
                    disabledContainerColor = Color(0xFF2A2A2A),
                    disabledContentColor = Color(0xFFF0F0EE)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isSubmitting) "등록 중..." else "등록")
            }
            if (isSubmitting) {
                AlertDialog(
                    onDismissRequest = {},
                    containerColor = DialogContainer,
                    titleContentColor = DialogTitle,
                    textContentColor = DialogText,
                    confirmButton = {},
                    title = { Text("처리 중") },
                    text = {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                            Text("크루 모집글을 등록하고 있어요")
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WritePostScreen(
    userName: String,
    prefs: SharedPreferences,
    editDocId: String? = null,
    initialTitle: String? = null,
    initialContent: String? = null,
    initialImageUri: String? = null
) {
    val context = LocalContext.current
    val isEditMode = !editDocId.isNullOrBlank()
    var title by remember { mutableStateOf(initialTitle.orEmpty()) }
    var content by remember { mutableStateOf(initialContent.orEmpty()) }
    var previewImageUrl by remember { mutableStateOf(initialImageUri) }
    var hashtags by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var selectedRun by remember { mutableStateOf<RunSummaryRef?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }
    //사진 선택 런처
    val imagePicker =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) {
                selectedImageUri = uri
                previewImageUrl = null
            }
        }

    fun loadLatestRun(context: Context): List<RunSummaryRef> {
        val sp = context.getSharedPreferences("run_pref", Context.MODE_PRIVATE)
        val json = sp.getString("runs_json", "[]")
        val arr = JSONArray(json)
        return List(arr.length()) { i ->
            val obj = arr.getJSONObject(i)
            RunSummaryRef(
                distanceM = obj.optDouble("distanceM", 0.0),
                durationMs = obj.optLong("durationMs", 0L),
                endAt = obj.optLong("endAt", 0L),
                fileName = obj.optString("fileName", "")
            )
        }
    }
    Scaffold(
        topBar = { TopAppBar(title = { Text(if (isEditMode) "게시글 수정" else "게시글 작성") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("제목") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = hashtags,
                onValueChange = { hashtags = it },
                label = { Text("해시태그 (쉼표로 구분)") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                label = { Text("내용을 입력하세요") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
            )

            Spacer(Modifier.height(16.dp))
            var selectedRoute by remember { mutableStateOf<RouteSummary?>(null) }
            var routeList by remember { mutableStateOf<List<RouteSummary>>(emptyList()) }
            var showPicker by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()
            Button(onClick = {
                scope.launch {
                    try {
                        routeList = fetchMyRoutes("http://3.39.10.184:4000") // 에뮬레이터
                        showPicker = true
                    } catch (e: Exception) {
                        Log.e("POST", "fetch routes fail", e)
                    }
                }
            }) {
                Text("러닝기록(루트) 불러오기")
            }
            if (showPicker) {
                // 간단 Dialog 예시(바텀시트로 바꿔도 됨)
                AlertDialog(
                    onDismissRequest = { showPicker = false },
                    containerColor = DialogContainer,
                    titleContentColor = DialogTitle,
                    textContentColor = DialogText,
                    title = { Text("내 루트 선택") },
                    text = {
                        Column {
                            routeList.forEach { r ->
                                TextButton(onClick = {
                                    selectedRoute = r
                                    showPicker = false
                                }) {
                                    Text("${r.title} • ${r.distance_m}m")
                                }
                            }
                        }
                    },
                    confirmButton = {}
                )
            }
            selectedRun?.let { run ->
                Spacer(Modifier.height(12.dp))
                Text("거리: ${"%.1f".format(run.distanceM / 1000)} km")
                Text("시간: ${formatDuration(run.durationMs)}")
                Text(
                    "페이스: ${
                        formatPace(
                            calcPace(run.distanceM, run.durationMs) ?: 0.0
                        )
                    }"
                )
                Text("칼로리: ${"%.0f".format(calcCalories(run.distanceM))} kcal")
            }

            //이미지 미리보기 선택 버튼
            Button(
                onClick = {
                    imagePicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }
            ) {
                Text("사진 첨부하기")
            }

            if (selectedImageUri != null || !previewImageUrl.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Image(
                    painter = rememberAsyncImagePainter(selectedImageUri ?: previewImageUrl),
                    contentDescription = "선택된 이미지",
                    modifier = Modifier
                        .height(200.dp)
                        .fillMaxWidth()
                )
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    if (isSubmitting) return@Button
                    val trimmedTitle = title.trim()
                    val trimmedContent = content.trim()
                    if (trimmedTitle.isBlank() || trimmedContent.isBlank()) {
                        Toast.makeText(context, "제목/내용을 입력해주세요", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    val user = FirebaseAuth.getInstance().currentUser
                    if (user == null) {
                        Toast.makeText(context, "로그인이 필요합니다(익명 로그인 포함)", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    val db = FirebaseFirestore.getInstance()
                    isSubmitting = true

                    // ✅ 1) 먼저 업로드할 이미지가 있으면 Storage 업로드 → downloadUrl 얻기
                    val localImageUri = selectedImageUri

                    fun savePost(imageDownloadUrl: String?) {
                        val postData = hashMapOf(
                            "title" to trimmedTitle,
                            "userId" to user.uid,
                            "userName" to (userName ?: user.displayName ?: "익명"),
                            "content" to trimmedContent,
                            // ✅ Firestore에는 content:// 말고 downloadUrl을 저장
                            "imageUrls" to listOfNotNull(imageDownloadUrl),

                            "runSummary" to (selectedRun?.let { r ->
                                mapOf(
                                    "distanceKm" to (r.distanceM / 1000.0),
                                    "durationMs" to r.durationMs,
                                    "pace" to formatPace(calcPace(r.distanceM, r.durationMs) ?: 0.0),
                                    "calories" to calcCalories(r.distanceM)
                                )
                            }),
                            "routeId" to selectedRoute?.id,
                            "routeTitle" to selectedRoute?.title,

                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp(),
                            "likeCount" to 0,
                            "commentCount" to 0
                        )

                        db.collection("posts")
                            .add(postData)
                            .addOnSuccessListener {
                                isSubmitting = false
                                Toast.makeText(context, "게시글 등록 완료", Toast.LENGTH_SHORT).show()
                                (context as? Activity)?.finish()
                            }
                            .addOnFailureListener { e ->
                                isSubmitting = false
                                Toast.makeText(context, "등록 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                    }

                    // ✅ 이미지 없으면 그냥 저장
                    fun updatePost(imageDownloadUrl: String?) {
                        val updateData = hashMapOf<String, Any>(
                            "title" to trimmedTitle,
                            "content" to trimmedContent,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                        updateData["imageUrls"] = listOfNotNull(imageDownloadUrl)

                        db.collection("posts")
                            .document(editDocId!!)
                            .update(updateData)
                            .addOnSuccessListener {
                                isSubmitting = false
                                Toast.makeText(context, "게시글 수정 완료", Toast.LENGTH_SHORT).show()
                                (context as? Activity)?.finish()
                            }
                            .addOnFailureListener { e ->
                                isSubmitting = false
                                Toast.makeText(context, "수정 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                    }

                    if (isEditMode && localImageUri == null) {
                        updatePost(previewImageUrl)
                        return@Button
                    }

                    val uploadUri = localImageUri
                    if (uploadUri == null) {
                        savePost(null)
                        return@Button
                    }

                    // ✅ 이미지 있으면 Storage 업로드 후 저장
                    val storage = FirebaseStorage.getInstance()
                    val fileName = "${UUID.randomUUID()}.jpg"
                    val ref = storage.reference.child("posts/${user.uid}/$fileName")

                    ref.putFile(uploadUri)
                        .continueWithTask { task ->
                            if (!task.isSuccessful) throw (task.exception ?: Exception("이미지 업로드 실패"))
                            ref.downloadUrl
                        }
                        .addOnSuccessListener { downloadUri ->
                            if (isEditMode) {
                                updatePost(downloadUri.toString())
                            } else {
                                savePost(downloadUri.toString())
                            }
                        }
                        .addOnFailureListener { e ->
                            isSubmitting = false
                            Toast.makeText(context, "이미지 업로드 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                },
                enabled = !isSubmitting,
                colors = ButtonDefaults.buttonColors(
                    disabledContainerColor = Color(0xFF2A2A2A),
                    disabledContentColor = Color(0xFFF0F0EE)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (isSubmitting) {
                        if (isEditMode) "수정 중..." else "등록 중..."
                    } else {
                        if (isEditMode) "게시글 수정" else "게시글 등록"
                    }
                )
            }

            if (isSubmitting) {
                AlertDialog(
                    onDismissRequest = {},
                    containerColor = DialogContainer,
                    titleContentColor = DialogTitle,
                    textContentColor = DialogText,
                    confirmButton = {},
                    title = { Text("처리 중") },
                    text = {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                            Text("게시글을 등록하고 있어요")
                        }
                    }
                )
            }
        }
    }
}
data class RunSummaryRef(
    val distanceM: Double,
    val durationMs: Long,
    val endAt: Long,
    val fileName: String
)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityDetailScreen(
    title: String,
    hashtags: List<String>,
    authorName: String,
    userName: String,
    content: String,
    postId: Int,
    imageRes: Int,
    imageUri: String?,
    initialLikes: Int,
    initialComments: Int,
    distanceKm: Double?,
    durationText: String?,
    pace: String?,
    calories: Double?,
    onUpdateStats: (Int, Int) -> Unit,
    docId: String?,
    routeId: Long?,
    onEditRequest: (docId: String, title: String, content: String, imageUri: String?) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { CommunityPostRepository() }
    val myUid = FirebaseAuth.getInstance().currentUser?.uid
    var likes by rememberSaveable { mutableStateOf(0) }
    var liked by rememberSaveable { mutableStateOf(false) }
    var postAuthorId by remember { mutableStateOf<String?>(null) }
    var comments by remember {
        mutableStateOf<List<Pair<String, Comment>>>(
            emptyList()
        )
    }
    var showImagePreview by remember { mutableStateOf(false) }
    var showDeletePostConfirm by remember { mutableStateOf(false) }
    var deleteCommentTargetId by remember { mutableStateOf<String?>(null) }
    var isSubmittingComment by remember { mutableStateOf(false) }
    var newComment by remember { mutableStateOf("") }
    val routeVm: RouteDetailViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val routeDetail by routeVm.route.collectAsState()
    val routeError by routeVm.error.collectAsState()

    LaunchedEffect(routeId) {
        routeId?.let { routeVm.loadRouteDetail(it) }
    }
    LaunchedEffect(docId) {
        val safeDocId = docId ?: return@LaunchedEffect
        try {
            // 1) 내가 좋아요 눌렀는지
            liked = repo.isLikedByMe(safeDocId)

            // 2) post 문서에서 최신 카운트 가져오기
            val post = repo.fetchPost(safeDocId)
            if (post != null) {
                likes = post.likeCount.toInt()
                postAuthorId = post.userId
            }

            // 3) 댓글 목록 로딩
            comments = repo.fetchComments(safeDocId)
        } catch (_: Exception) {
        }
    }

    Scaffold(
        containerColor = Color(0xFFFAFAF8),
        topBar = {
            TopAppBar(
                title = { Text("커뮤니티", fontSize = 20.sp) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFFFAFAF8)),
                actions = {
                    val canDelete = (myUid != null && postAuthorId != null && myUid == postAuthorId)
                    if (canDelete) {
                        Text(
                            "수정",
                            color = Color(0xFF204996),
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .clickable {
                                    val safeDocId = docId ?: return@clickable
                                    onEditRequest(safeDocId, title, content, imageUri)
                                }
                        )
                        Text(
                            "삭제",
                            color = Color.Red,
                            modifier = Modifier
                                .padding(end = 16.dp)
                                .clickable { showDeletePostConfirm = true }
                        )
                    }
                }
            )
        }
    ) { padding ->

        // ✅ UI는 원본 그대로 Column 유지
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 16.dp)
                .fillMaxSize()
                .imePadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
        ) {
            val fallbackImageRes = if (imageRes != 0) imageRes else R.drawable.noimage

            // 이미지
            when {
                imageUri?.isNotBlank() == true -> {
                    Image(
                        painter = rememberAsyncImagePainter(Uri.parse(imageUri)),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { showImagePreview = true },
                        contentScale = ContentScale.Crop
                    )
                }
                else -> {
                    Image(
                        painter = painterResource(id = fallbackImageRes),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { showImagePreview = true },
                        contentScale = ContentScale.Crop
                    )
                }
            }

            if (showImagePreview) {
                Dialog(
                    onDismissRequest = { showImagePreview = false },
                    properties = DialogProperties(usePlatformDefaultWidth = false)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black)
                            .clickable { showImagePreview = false }
                    ) {
                        when {
                            imageUri?.isNotBlank() == true -> {
                                Image(
                                    painter = rememberAsyncImagePainter(Uri.parse(imageUri)),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .align(Alignment.Center),
                                    contentScale = ContentScale.Fit
                                )
                            }
                            else -> {
                                Image(
                                    painter = painterResource(id = fallbackImageRes),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .align(Alignment.Center),
                                    contentScale = ContentScale.Fit
                                )
                            }
                        }

                        IconButton(
                            onClick = { showImagePreview = false },
                            modifier = Modifier
                                .statusBarsPadding()
                                .padding(8.dp)
                                .align(Alignment.TopStart)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "닫기",
                                tint = Color.White
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = title,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF204996)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = authorName,
                fontSize = 13.sp,
                color = Color(0xFF2A2A2A),
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(12.dp))

            Text(
                content,
                fontSize = 16.sp,
                lineHeight = 23.sp,
                color = Color(0xFF1A1A1A)
            )

            Spacer(Modifier.height(16.dp))

            if (distanceKm != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0x33F1B243))
                        .padding(18.dp)
                ) {
                    Text("🏃 러닝 정보", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Spacer(Modifier.height(10.dp))
                    Text("거리: ${"%.1f".format(distanceKm)} km", fontSize = 15.sp)
                    Text("시간: $durationText", fontSize = 15.sp)
                    Text("페이스: $pace", fontSize = 15.sp)
                    if (calories != null) {
                        Text("칼로리: ${"%.0f".format(calories)} kcal", fontSize = 15.sp)
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
            if (routeId != null) {
                Spacer(Modifier.height(12.dp))

                RouteMapByRouteDetail(
                    routeDetail = routeDetail,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(RoundedCornerShape(16.dp))
                )

                Spacer(Modifier.height(12.dp))
            }

            // 좋아요/댓글 카운트 UI (원본 유지)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {

                // ✅ 좋아요 Firestore 연동
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = {
                            val safeDocId = docId ?: run {
                                Toast.makeText(context, "좋아요 문서 ID가 없어요", Toast.LENGTH_SHORT).show()
                                return@IconButton
                            }

                            scope.launch {
                                try {
                                    val nowLiked = repo.toggleLike(safeDocId)
                                    liked = nowLiked
                                    repo.fetchPost(safeDocId)?.let { likes = it.likeCount.toInt() }
                                    onUpdateStats(likes, comments.size)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "좋아요 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Favorite,
                            contentDescription = null,
                            tint = if (liked) Color.Red else Color.LightGray,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Text("$likes", fontSize = 15.sp)
                }

                // 댓글 수
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ChatBubbleOutline,
                        contentDescription = null,
                        tint = Color(0xFF204996),
                        modifier = Modifier.size(20.dp)
                    )
                    Text("${comments.size}", fontSize = 15.sp)
                }
            }

            Spacer(Modifier.height(20.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = newComment,
                    onValueChange = { newComment = it },
                    placeholder = { Text("댓글을 입력하세요") },
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(Modifier.width(10.dp))

                Button(
                    enabled = !isSubmittingComment,
                    onClick = {
                        if (isSubmittingComment) return@Button
                        val safeDocId = docId ?: run {
                            Toast.makeText(context, "댓글을 달 문서 ID가 없어요", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (newComment.isBlank()) return@Button
                        isSubmittingComment = true

                        scope.launch {
                            try {
                                repo.addComment(safeDocId, newComment.trim(),userName = userName )
                                newComment = ""
                                comments = repo.fetchComments(safeDocId)
                                onUpdateStats(likes, comments.size)
                            } catch (e: Exception) {
                                Toast.makeText(context, "댓글 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                            }finally {
                                isSubmittingComment = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF204996),
                        disabledContainerColor = Color(0xFF2A2A2A),
                        disabledContentColor = Color(0xFFF0F0EE)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(54.dp)
                ) {
                    Text(if (isSubmittingComment) "등록 중..." else "등록", fontSize = 15.sp)
                }
            }
            Spacer(Modifier.height(12.dp))


            // ✅ 댓글 목록 UI (원본 형태 유지)
            if (comments.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    comments.forEach { (commentId, c) ->
                        Column {
                            Text(
                                text = (c.userName.ifBlank { c.userId.take(6) }),
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF204996),
                                fontSize = 15.sp
                            )
                            Text(
                                text = c.text,
                                fontSize = 15.sp,
                                color = Color.DarkGray
                            )

                            // ✅ 내 댓글만 삭제 버튼 노출(원하면 제거 가능)
                            val myUid = FirebaseAuth.getInstance().currentUser?.uid
                            if (myUid != null && c.userId == myUid) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "삭제",
                                    color = Color.Red,
                                    fontSize = 13.sp,
                                    modifier = Modifier.clickable { deleteCommentTargetId = commentId }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

        }
        if (showDeletePostConfirm) {
            AlertDialog(
                onDismissRequest = { showDeletePostConfirm = false },
                containerColor = DialogContainer,
                titleContentColor = DialogTitle,
                textContentColor = DialogText,
                title = { Text("삭제 확인") },
                text = { Text("삭제하시겠습니까?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeletePostConfirm = false
                            val safeDocId = docId ?: return@TextButton
                            scope.launch {
                                try {
                                    repo.deletePost(safeDocId)
                                    Toast.makeText(context, "삭제 완료", Toast.LENGTH_SHORT).show()
                                    (context as? Activity)?.finish()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "삭제 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    ) { Text("삭제") }
                },
                dismissButton = {
                    TextButton(onClick = { showDeletePostConfirm = false }) { Text("취소") }
                }
            )
        }

        if (deleteCommentTargetId != null) {
            AlertDialog(
                onDismissRequest = { deleteCommentTargetId = null },
                containerColor = DialogContainer,
                titleContentColor = DialogTitle,
                textContentColor = DialogText,
                title = { Text("삭제 확인") },
                text = { Text("삭제하시겠습니까?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val targetId = deleteCommentTargetId ?: return@TextButton
                            deleteCommentTargetId = null
                            val safeDocId = docId ?: return@TextButton
                            scope.launch {
                                try {
                                    repo.deleteComment(safeDocId, targetId)
                                    comments = repo.fetchComments(safeDocId)
                                    onUpdateStats(likes, comments.size)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "댓글 삭제 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    ) { Text("삭제") }
                },
                dismissButton = {
                    TextButton(onClick = { deleteCommentTargetId = null }) { Text("취소") }
                }
            )
        }

        if (isSubmittingComment) {
            AlertDialog(
                onDismissRequest = {},
                containerColor = DialogContainer,
                titleContentColor = DialogTitle,
                textContentColor = DialogText,
                confirmButton = {},
                title = { Text("처리 중") },
                text = {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                        Text("댓글을 등록하고 있어요")
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrewDetailScreen(
    crewId: String,
    crewRepo: CrewRepository = CrewRepository()
) {
    data class CrewMemberUi(
        val uid: String,
        val nickname: String
    )

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val myUid = FirebaseAuth.getInstance().currentUser?.uid
    val db = remember { FirebaseFirestore.getInstance() }
    fun openCrewChat() {
        val chatIntent = Intent(context, CommunityActivity::class.java).apply {
            putExtra("isChatMode", true)
            putExtra("crewId", crewId)
        }
        context.startActivity(chatIntent)
    }

    var crew by remember { mutableStateOf<CrewPost?>(null) }
    var isMember by remember { mutableStateOf(false) }
    var ownerName by remember { mutableStateOf("알 수 없음") }
    var members by remember { mutableStateOf<List<CrewMemberUi>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    suspend fun refreshCrewState() {
        crew = crewRepo.fetchCrew(crewId)
        isMember = crewRepo.isMember(crewId)

        val ownerId = crew?.userId
        ownerName = if (!ownerId.isNullOrBlank()) {
            runCatching {
                db.collection("users")
                    .document(ownerId)
                    .get()
                    .await()
                    .getString("nickname")
            }.getOrNull()?.takeIf { it.isNotBlank() } ?: "알 수 없음"
        } else {
            "알 수 없음"
        }

        val memberDocs = db.collection("crews")
            .document(crewId)
            .collection("members")
            .get()
            .await()
            .documents

        val loadedMembers = memberDocs.map { doc ->
            val uid = doc.id
            val nick = runCatching {
                db.collection("users")
                    .document(uid)
                    .get()
                    .await()
                    .getString("nickname")
            }.getOrNull()?.takeIf { it.isNotBlank() } ?: "사용자"
            CrewMemberUi(uid = uid, nickname = nick)
        }
        val ownerUid = crew?.userId
        members = loadedMembers.sortedWith(
            compareByDescending<CrewMemberUi> { it.uid == ownerUid }
                .thenBy { it.nickname }
        )
    }

    LaunchedEffect(crewId) {
        loading = true
        try {
            refreshCrewState()
        } finally {
            loading = false
        }
    }

    Scaffold(
        containerColor = Color(0xFFFAFAF8),
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = { (context as? Activity)?.finish() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "뒤로가기"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFFFAFAF8))
            )
        }
    ) { padding ->
        if (loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val c = crew ?: run {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("크루 정보를 불러오지 못했어요")
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
        ) {
            // ----- 크루 정보 -----
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(c.title, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, color = Color(0xFF1A1A1A))
                Spacer(Modifier.height(10.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F0EE)),
                    elevation = CardDefaults.cardElevation(0.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("장소: ${c.location}", color = Color(0xFF2A2A2A), fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        Text(c.description, color = Color(0xFF2A2A2A))
                    }
                }

                Spacer(Modifier.height(10.dp))
                Text(
                    text = "(${c.currentMembers}/${c.maxMembers})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color(0xFF2A2A2A)
                )
                Spacer(Modifier.height(4.dp))
                Text("크루장: $ownerName", fontWeight = FontWeight.SemiBold, color = Color(0xFF2A2A2A))
            }

            Spacer(Modifier.height(14.dp))
            Card(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFAFAF8)),
                elevation = CardDefaults.cardElevation(0.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E2DE))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 14.dp)
                ) {
                    Text(
                        text = "크루원 목록",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1A1A1A),
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                    Spacer(Modifier.height(10.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (members.isEmpty()) {
                            Text("표시할 크루원이 없어요.", color = Color(0xFF6A6A66))
                        } else {
                            members.forEach { member ->
                                val isMe = myUid != null && member.uid == myUid
                                val isOwnerMember = member.uid == c.userId
                                Text(
                                    text = buildString {
                                        append(member.nickname)
                                        if (isOwnerMember) append(" (크루장)")
                                        if (isMe) append(" (나)")
                                    },
                                    color = Color(0xFF2A2A2A),
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            // ----- 버튼 영역 -----
            val isFull = c.currentMembers >= c.maxMembers
            val isOwner = myUid != null && myUid == c.userId

            if (isOwner) {
                Button(
                    onClick = {
                        scope.launch {
                            try {
                                val crewRef = db.collection("crews").document(crewId)
                                val members = crewRef.collection("members").get().await()
                                val messages = crewRef.collection("messages").get().await()

                                db.runBatch { batch ->
                                    members.documents.forEach { batch.delete(it.reference) }
                                    messages.documents.forEach { batch.delete(it.reference) }
                                    batch.delete(crewRef)
                                }.await()

                                Toast.makeText(context, "크루 해산 완료", Toast.LENGTH_SHORT).show()
                                (context as? Activity)?.finish()
                            } catch (e: Exception) {
                                Toast.makeText(
                                    context,
                                    e.message ?: "크루 해산 실패",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFC53D3D),
                        contentColor = Color(0xFFFAFAF8),
                    )
                ) { Text("크루 해산") }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            scope.launch {
                                try {
                                    crewRepo.joinCrew(crewId)
                                    refreshCrewState()
                                    Toast.makeText(context, "참여 완료", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, e.message ?: "참여 실패", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        enabled = !isMember && !isFull,
                        modifier = Modifier.weight(1f)
                    ) { Text(if (isFull) "모집 마감" else if (isMember) "참여 중" else "참여하기") }

                    Button(
                        onClick = {
                            scope.launch {
                                try {
                                    crewRepo.leaveCrew(crewId)
                                    refreshCrewState()
                                    Toast.makeText(context, "탈퇴 완료", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, e.message ?: "탈퇴 실패", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        enabled = isMember,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFC53D3D),
                            contentColor = Color(0xFFFAFAF8),
                            disabledContainerColor = Color(0xFFB9B9B5),
                            disabledContentColor = Color(0xFFF0F0EE)
                        )
                    ) { Text("탈퇴하기") }
                }
            }

            Spacer(Modifier.height(16.dp))
            Divider()
            Spacer(Modifier.height(10.dp))

            if (!isMember) {
                Text(
                    text = "크루에 참여한 멤버만 채팅방에 입장할 수 있어요.",
                    color = Color(0xFF6A6A66),
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            Button(
                onClick = { openCrewChat() },
                enabled = isMember,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF204996),
                    contentColor = Color(0xFFFAFAF8),
                    disabledContainerColor = Color(0xFF2A2A2A),
                    disabledContentColor = Color(0xFFF0F0EE)
                )
            ) {
                Text(if (isMember) "크루 채팅방 입장" else "크루 참여 후 채팅 가능")
            }
        }
    }
}
