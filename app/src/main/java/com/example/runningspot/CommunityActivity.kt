package com.example.runningspot

import android.R.attr.duration
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.os.Process.myUid
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.example.runningspot.ui.Post
import com.example.runningspot.ui.calcCalories
import com.example.runningspot.ui.calcPace
import com.example.runningspot.ui.formatPace
import com.example.runningspot.ui.loadPosts
import okhttp3.internal.concurrent.formatDuration
import org.json.JSONArray
import org.json.JSONObject
import kotlin.run
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.example.runningspot.ui.MainScreen
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.example.runningspot.data.model.CrewPost
import com.example.runningspot.ui.CrewChatScreen
import com.example.runningspot.data.CrewRepository
import com.google.firebase.storage.FirebaseStorage
import java.util.UUID

class CommunityActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val isCrewDetailMode = intent.getBooleanExtra("isCrewDetailMode", false)
        if (isCrewDetailMode) {
            val crewId = intent.getStringExtra("crewId") ?: run {
                finish()
                return
            }
            setContent {
                CrewDetailScreen(crewId = crewId)
            }
            return
        }
        val isChatMode = intent.getBooleanExtra("isChatMode", false)
        if (isChatMode) {
            val crewId = intent.getStringExtra("crewId") ?: run {
                finish()
                return
            }
            setContent { CrewChatScreen(crewId = crewId) }
            return
        }

        // 글 작성인지, 기존 게시글 보기인지 구분
        val isWriteMode = intent.getBooleanExtra("isWriteMode", false)
        val prefs = getSharedPreferences("community_prefs", MODE_PRIVATE)

        if (isWriteMode) {
            val userName = intent.getStringExtra("userName") ?: "익명 사용자"
            val writeType = intent.getStringExtra("writeType") ?: "post"

            setContent {
                if (writeType == "crew") {
                    CrewWriteScreen(userName = userName)
                } else {
                    WritePostScreen(userName, prefs) // ✅ 기존 피드 글쓰기 그대로
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
            setContent {
                CommunityDetailScreen(
                    title = title,
                    hashtags = listOf("러닝", "오전", "3Km"),
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrewWriteScreen(userName: String) {
    val context = LocalContext.current

    var title by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var maxMembersText by remember { mutableStateOf("5") }

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
                    val data = hashMapOf(
                        "title" to title.trim(),
                        "location" to location.trim(),
                        "description" to description.trim(),
                        "authorId" to user.uid,
                        "maxMembers" to maxMembers,
                        "currentMembers" to 0L,
                        "createdAt" to FieldValue.serverTimestamp()
                    )

                    db.collection("crews")
                        .add(data)
                        .addOnSuccessListener {
                            Toast.makeText(context, "크루 모집글 등록 완료", Toast.LENGTH_SHORT).show()
                            (context as? Activity)?.finish()
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(context, "등록 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("등록")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WritePostScreen(userName: String, prefs: SharedPreferences) {
    val context = LocalContext.current

    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var hashtags by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var selectedRun by remember { mutableStateOf<RunSummaryRef?>(null) }
    //사진 선택 런처
    val imagePicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                selectedImageUri = uri
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
        topBar = { TopAppBar(title = { Text("게시글 작성") }) }
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
            var showRunPicker by remember { mutableStateOf(false) }
            val runList = remember { loadLatestRun(context) }
            Button(onClick = { showRunPicker = true }) {
                Text("러닝 기록 선택하기")
            }
            if (showRunPicker) {
                AlertDialog(
                    onDismissRequest = { showRunPicker = false },
                    title = { Text("러닝 기록 선택") },
                    text = {
                        Column {
                            runList.forEach { run ->
                                Text(
                                    text = "거리 ${"%.2f".format(run.distanceM / 1000)} km / 시간 ${
                                        formatDuration(
                                            run.durationMs
                                        )
                                    }",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedRun = run
                                            showRunPicker = false
                                        }
                                        .padding(12.dp)
                                )
                            }
                        }
                    },
                    confirmButton = {}
                )
            }
            selectedRun?.let { run ->
                Spacer(Modifier.height(12.dp))
                Text("거리: ${"%.2f".format(run.distanceM / 1000)} km")
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
            Button(onClick = { imagePicker.launch(arrayOf("image/*")) }) {
                Text("사진 첨부하기")
            }

            selectedImageUri?.let {
                Spacer(Modifier.height(8.dp))
                Image(
                    painter = rememberAsyncImagePainter(it),
                    contentDescription = "선택된 이미지",
                    modifier = Modifier
                        .height(200.dp)
                        .fillMaxWidth()
                )
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    if (title.isBlank() || content.isBlank()) {
                        Toast.makeText(context, "제목/내용을 입력해주세요", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    val user = FirebaseAuth.getInstance().currentUser
                    if (user == null) {
                        Toast.makeText(context, "로그인이 필요합니다(익명 로그인 포함)", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    val db = FirebaseFirestore.getInstance()

                    // ✅ 1) 먼저 업로드할 이미지가 있으면 Storage 업로드 → downloadUrl 얻기
                    val localImageUri = selectedImageUri

                    fun savePost(imageDownloadUrl: String?) {
                        val postData = hashMapOf(
                            "title" to title,
                            "authorId" to user.uid,
                            "authorName" to (userName ?: user.displayName ?: "익명"),
                            "content" to content,
                            "imageUrls" to emptyList<String>(),

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

                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp(),
                            "likeCount" to 0,
                            "commentCount" to 0
                        )

                        db.collection("posts")
                            .add(postData)
                            .addOnSuccessListener {
                                Toast.makeText(context, "게시글 등록 완료", Toast.LENGTH_SHORT).show()
                                (context as? Activity)?.finish()
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(context, "등록 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                    }

                    // ✅ 이미지 없으면 그냥 저장
                    if (localImageUri == null) {
                        savePost(null)
                        return@Button
                    }

                    // ✅ 이미지 있으면 Storage 업로드 후 저장
                    val storage = FirebaseStorage.getInstance()
                    val fileName = "${UUID.randomUUID()}.jpg"
                    val ref = storage.reference.child("posts/${user.uid}/$fileName")

                    ref.putFile(localImageUri)
                        .continueWithTask { task ->
                            if (!task.isSuccessful) throw (task.exception ?: Exception("이미지 업로드 실패"))
                            ref.downloadUrl
                        }
                        .addOnSuccessListener { downloadUri ->
                            savePost(downloadUri.toString())
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(context, "이미지 업로드 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("게시글 등록")
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
    docId: String?


) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { com.example.runningspot.data.CommunityPostRepository() }
    val myUid = FirebaseAuth.getInstance().currentUser?.uid
    var likes by rememberSaveable { mutableStateOf(0) }
    var liked by rememberSaveable { mutableStateOf(false) }
    var postAuthorId by remember { mutableStateOf<String?>(null) }
    var comments by remember {
        mutableStateOf<List<Pair<String, com.example.runningspot.data.Comment>>>(
            emptyList()
        )
    }
    var newComment by remember { mutableStateOf("") }
    LaunchedEffect(docId) {
        val safeDocId = docId ?: return@LaunchedEffect
        try {
            // 1) 내가 좋아요 눌렀는지
            liked = repo.isLikedByMe(safeDocId)

            // 2) post 문서에서 최신 카운트 가져오기
            val post = repo.fetchPost(safeDocId)
            if (post != null) {
                likes = post.likeCount.toInt()
                postAuthorId = post.authorId
            }

            // 3) 댓글 목록 로딩
            comments = repo.fetchComments(safeDocId)
        } catch (_: Exception) {
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("커뮤니티", fontSize = 20.sp) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
                actions = {
                    val canDelete = (myUid != null && postAuthorId != null && myUid == postAuthorId)
                    if (canDelete) {
                        Text(
                            "삭제",
                            color = Color.Red,
                            modifier = Modifier
                                .padding(end = 16.dp)
                                .clickable {
                                    val safeDocId = docId
                                    if (safeDocId == null) {
                                        Toast.makeText(context, "삭제할 문서 ID가 없어요", Toast.LENGTH_SHORT).show()
                                        return@clickable
                                    }

                                    scope.launch {
                                        try {
                                            repo.deletePost(safeDocId)
                                            Toast.makeText(context, "삭제 완료", Toast.LENGTH_SHORT).show()
                                            (context as? Activity)?.finish()
                                        } catch (e: Exception) {
                                            Toast.makeText(
                                                context,
                                                "삭제 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
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
        ) {

            // 이미지
            if (imageUri?.isNotBlank() == true) {
                Image(
                    painter = rememberAsyncImagePainter(Uri.parse(imageUri)),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(20.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Image(
                    painter = painterResource(id = imageRes),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(20.dp)),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(Modifier.height(16.dp))

            Text(
                content,
                fontSize = 16.sp,
                lineHeight = 23.sp,
                color = Color(0xFF222222)
            )

            Spacer(Modifier.height(16.dp))

            if (distanceKm != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFFEDE7F6))
                        .padding(18.dp)
                ) {
                    Text("🏃 러닝 정보", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Spacer(Modifier.height(10.dp))
                    Text("거리: ${"%.2f".format(distanceKm)} km", fontSize = 15.sp)
                    Text("시간: $durationText", fontSize = 15.sp)
                    Text("페이스: $pace", fontSize = 15.sp)
                    if (calories != null) {
                        Text("칼로리: ${"%.0f".format(calories)} kcal", fontSize = 15.sp)
                    }
                }
                Spacer(Modifier.height(20.dp))
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
                        tint = Color(0xFF8E7CC3),
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
                    onClick = {
                        val safeDocId = docId ?: run {
                            Toast.makeText(context, "댓글을 달 문서 ID가 없어요", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (newComment.isBlank()) return@Button

                        scope.launch {
                            try {
                                repo.addComment(safeDocId, newComment.trim(),authorName = userName )
                                newComment = ""
                                comments = repo.fetchComments(safeDocId)
                                onUpdateStats(likes, comments.size)
                            } catch (e: Exception) {
                                Toast.makeText(context, "댓글 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6C4CD3)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(54.dp)
                ) {
                    Text("등록", fontSize = 15.sp)
                }
            }
            Spacer(Modifier.height(12.dp))


            // ✅ 댓글 목록 UI (원본 형태 유지)
            if (comments.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    comments.forEach { (commentId, c) ->
                        Column {
                            Text(
                                text = (c.authorName.ifBlank { c.authorId.take(6) }),
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF6C4CD3),
                                fontSize = 15.sp
                            )
                            Text(
                                text = c.text,
                                fontSize = 15.sp,
                                color = Color.DarkGray
                            )

                            // ✅ 내 댓글만 삭제 버튼 노출(원하면 제거 가능)
                            val myUid = FirebaseAuth.getInstance().currentUser?.uid
                            if (myUid != null && c.authorId == myUid) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "삭제",
                                    color = Color.Red,
                                    fontSize = 13.sp,
                                    modifier = Modifier.clickable {
                                        val safeDocId = docId ?: return@clickable
                                        scope.launch {
                                            try {
                                                repo.deleteComment(safeDocId, commentId)
                                                comments = repo.fetchComments(safeDocId)
                                                onUpdateStats(likes, comments.size)
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "댓글 삭제 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrewDetailScreen(
    crewId: String,
    crewRepo: CrewRepository = CrewRepository()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var crew by remember { mutableStateOf<CrewPost?>(null) }
    var isMember by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(crewId) {
        loading = true
        try {
            crew = crewRepo.fetchCrew(crewId)          // ✅ 단일 크루 읽기 함수 필요
            isMember = crewRepo.isMember(crewId)       // ✅ 이미 너가 만든 함수
        } finally {
            loading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(crew?.title ?: "크루") },
                actions = {
                    // ✅ 탈퇴 버튼을 상단에 두고 싶으면 여기
                    if (isMember) {
                        TextButton(
                            onClick = {
                                scope.launch {
                                    try {
                                        crewRepo.leaveCrew(crewId)    // ✅ 탈퇴 함수 필요
                                        isMember = false
                                        crew = crewRepo.fetchCrew(crewId)
                                        Toast.makeText(context, "탈퇴 완료", Toast.LENGTH_SHORT).show()
                                    } catch (e: Exception) {
                                        Toast.makeText(context, e.message ?: "탈퇴 실패", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        ) { Text("탈퇴", color = Color.Red) }
                    }
                }
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
            Text(c.title, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            Spacer(Modifier.height(6.dp))
            Text(c.location, color = Color.Gray)
            Spacer(Modifier.height(12.dp))
            Text(c.description)
            Spacer(Modifier.height(12.dp))
            Text("인원: ${c.currentMembers}/${c.maxMembers}", fontWeight = FontWeight.SemiBold)

            Spacer(Modifier.height(12.dp))

            // ----- 버튼 영역 -----
            val isFull = c.currentMembers >= c.maxMembers

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        scope.launch {
                            try {
                                crewRepo.joinCrew(crewId)
                                isMember = true
                                crew = crewRepo.fetchCrew(crewId)
                                Toast.makeText(context, "참여 완료", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, e.message ?: "참여 실패", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    enabled = !isMember && !isFull,
                    modifier = Modifier.weight(1f)
                ) { Text(if (isFull) "모집 마감" else if (isMember) "참여 중" else "참여하기") }

                OutlinedButton(
                    onClick = {
                        scope.launch {
                            try {
                                crewRepo.leaveCrew(crewId)
                                isMember = false
                                crew = crewRepo.fetchCrew(crewId)
                                Toast.makeText(context, "탈퇴 완료", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, e.message ?: "탈퇴 실패", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    enabled = isMember,
                    modifier = Modifier.weight(1f)
                ) { Text("탈퇴하기") }
            }

            Spacer(Modifier.height(16.dp))
            Divider()
            Spacer(Modifier.height(10.dp))

            // ----- 채팅 영역: 참여한 사람만 -----
        }
    }
}