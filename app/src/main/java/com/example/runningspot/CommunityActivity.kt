package com.example.runningspot

import android.R.attr.duration
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
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

class CommunityActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 글 작성인지, 기존 게시글 보기인지 구분
        val isWriteMode = intent.getBooleanExtra("isWriteMode", false)
        val prefs = getSharedPreferences("community_prefs", MODE_PRIVATE)

        if (isWriteMode) {
            // 글 작성 모드
            val userName = intent.getStringExtra("userName") ?: "익명 사용자"
            setContent { WritePostScreen(userName, prefs) }
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
                    if (title.isNotBlank() && content.isNotBlank()) {
                        val jsonArray = JSONArray(prefs.getString("user_posts", "[]"))
                        val newPost = JSONObject().apply {
                            put("id", (0..999999).random())
                            put("title", title)
                            put("author", userName)
                            put("content", content)
                            put("hashtags", hashtags)
                            put("imageUri", selectedImageUri?.toString() ?: "")
                            selectedRun?.let { r ->
                                put("distanceKm", r.distanceM / 1000)
                                put("durationMs", r.durationMs)
                                put(
                                    "pace",
                                    formatPace(calcPace(r.distanceM, r.durationMs) ?: 0.0)
                                )
                                put("calories", calcCalories(r.distanceM))
                            }
                        }
                        jsonArray.put(newPost)
                        prefs.edit().putString("user_posts", jsonArray.toString()).apply()
                        (context as? Activity)?.finish()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("게시글 등록")
            }
        }
    }
}
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
    onUpdateStats: (Int, Int) -> Unit

) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("community_prefs", Context.MODE_PRIVATE)

    var likes by rememberSaveable { mutableStateOf(initialLikes) }
    var liked by rememberSaveable { mutableStateOf(false) }
    var commentList by remember { mutableStateOf(loadComments(prefs, postId)) }
    var newComment by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("커뮤니티", fontSize = 20.sp) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
                actions = {
                    // 🔥🔥 여기 삭제 버튼 추가
                    Text(
                        "삭제",
                        color = Color.Red,
                        modifier = Modifier
                            .padding(end = 16.dp)
                            .clickable {
                                deletePost(prefs, postId)
                                (context as? Activity)?.finish()
                            }
                    )
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 16.dp)
                .fillMaxSize()
        ) {

            //  이미지 (그대로 유지)
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

            //  본문
            Text(
                content,
                fontSize = 16.sp,
                lineHeight = 23.sp,
                color = Color(0xFF222222)
            )

            Spacer(Modifier.height(16.dp))


            if (distanceKm != null && pace != null && durationText != null) {

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
                    Text("시간: ${durationText}", fontSize = 15.sp)
                    Text("페이스: $pace", fontSize = 15.sp)

                    if (calories != null) {
                        Text("칼로리: ${"%.0f".format(calories)} kcal", fontSize = 15.sp)
                    }
                }

                Spacer(Modifier.height(20.dp))
            }

            // 좋아요 / 댓글 아이콘
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = {
                            liked = !liked
                            likes = if (liked) likes + 1 else maxOf(likes - 1, 0)
                            prefs.edit().putInt("likes_$postId", likes).apply()
                            onUpdateStats(likes, commentList.size)
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
                    Text("${commentList.size}", fontSize = 15.sp)
                }
            }

            Spacer(Modifier.height(20.dp))

            //댓글 전체 표시 (작성자 + 내용)
            if (commentList.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    commentList.forEach { (writer, comment) ->
                        Column {
                            Text(
                                text = writer,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF6C4CD3),
                                fontSize = 15.sp
                            )
                            Text(
                                text = comment,
                                fontSize = 15.sp,
                                color = Color.DarkGray
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            //댓글 입력창
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
                        if (newComment.isNotBlank()) {
                            commentList = commentList + (userName to newComment)
                            saveComments(prefs, postId, commentList)
                            newComment = ""
                            onUpdateStats(likes, commentList.size)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6C4CD3)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(54.dp)
                ) {
                    Text("등록", fontSize = 15.sp)
                }
            }
        }
    }
}

    fun saveComments(prefs: SharedPreferences, postId: Int, comments: List<Pair<String, String>>) {
        val json = JSONArray().apply {
            comments.forEach { (writer, text) ->
                put(JSONObject().apply {
                    put("writer", writer)
                    put("text", text)
                })
            }
        }
        prefs.edit().putString("comments_json_$postId", json.toString()).apply()
    }

fun loadComments(prefs: SharedPreferences, postId: Int): List<Pair<String, String>> {
    val jsonString = prefs.getString("comments_json_$postId", null) ?: return emptyList()
    return try {
        val jsonArray = JSONArray(jsonString)
        List(jsonArray.length()) { i ->
            val obj = jsonArray.getJSONObject(i)
            obj.getString("writer") to obj.getString("text")
        }
    } catch (e: Exception) {
        emptyList()
    }
}
fun deletePost(prefs: SharedPreferences, postId: Int) {
    val savedJson = prefs.getString("user_posts", "[]") ?: "[]"
    val arr = JSONArray(savedJson)
    val newArr = JSONArray()

    for (i in 0 until arr.length()) {
        val obj = arr.getJSONObject(i)
        if (obj.getInt("id") != postId) {
            newArr.put(obj)
        }
    }

    prefs.edit().putString("user_posts", newArr.toString()).apply()
}
data class RunSummaryRef(
    val distanceM: Double,
    val durationMs: Long,
    val endAt: Long,
    val fileName: String
)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrewDetailScreen(
    crewName: String,
    crewLocation: String,
    crewDescription: String
) {

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(crewName, fontSize = 20.sp) }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(20.dp)
                .fillMaxSize()
        ) {

            Text(
                text = crewName,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = crewLocation,
                fontSize = 15.sp,
                color = Color.Gray
            )

            Spacer(Modifier.height(20.dp))

            Text(
                text = crewDescription,
                fontSize = 16.sp,
                lineHeight = 22.sp
            )

            Spacer(Modifier.height(40.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFFEFEFEF)),
                contentAlignment = Alignment.Center
            ) {
                Text("크루 상세 화면 준비 중!", color = Color.Gray)
            }
        }
    }
}