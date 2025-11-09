package com.example.runningspot

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import org.json.JSONArray
import org.json.JSONObject

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
                    onUpdateStats = { likes, comments ->
                        prefs.edit()
                            .putInt("likes_$postId", likes)
                            .putInt("comments_$postId", comments)
                            .apply()
                    }
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

    //사진 선택 런처
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        selectedImageUri = uri
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

            //이미지 미리보기 선택 버튼
            Button(onClick = { imagePicker.launch("image/*") }) {
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
    onUpdateStats: (Int, Int) -> Unit
) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("community_prefs", Context.MODE_PRIVATE)

    var likes by rememberSaveable { mutableStateOf(initialLikes) }
    var liked by rememberSaveable { mutableStateOf(false) }
    var commentList by remember { mutableStateOf(loadComments(prefs, postId)) }
    var newComment by remember { mutableStateOf("") }

    fun addComment(comment: String) {
        commentList = commentList + (userName to comment)
        saveComments(prefs, postId, commentList)
        onUpdateStats(likes, commentList.size)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                actions = {
                    if (authorName == userName) {
                        IconButton(onClick = {
                            val jsonArray = JSONArray(prefs.getString("user_posts", "[]"))
                            val newArray = JSONArray()
                            for (i in 0 until jsonArray.length()) {
                                val obj = jsonArray.getJSONObject(i)
                                if (obj.getInt("id") != postId) newArray.put(obj)
                            }
                            prefs.edit().putString("user_posts", newArray.toString()).apply()
                            (context as? Activity)?.finish()
                        }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "게시글 삭제",
                                tint = Color.Red
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 해시태그
            if (hashtags.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, bottom = 8.dp), // 왼쪽에 여백 추가
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    hashtags.forEach { tag ->
                        AssistChip(
                            onClick = {},
                            label = { Text("#$tag") },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = Color(0xFFE3F2FD)
                            )
                        )
                    }
                }
            }

            //이미지
            imageUri?.let {
                if (it.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Image(
                        painter = rememberAsyncImagePainter(Uri.parse(it)),
                        contentDescription = "첨부 이미지",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                    )
                }
            } ?: run {
                if (imageRes != 0) {
                    Spacer(Modifier.height(10.dp))
                    Image(
                        painter = painterResource(id = imageRes),
                        contentDescription = "기본 이미지",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Text(content, fontSize = 16.sp)

            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {
                    liked = !liked
                    likes = if (liked) likes + 1 else maxOf(likes - 1, 0)
                    prefs.edit().putInt("likes_$postId", likes).apply()
                    onUpdateStats(likes, commentList.size)
                }) {
                    Icon(
                        imageVector = Icons.Filled.Favorite,
                        contentDescription = "좋아요",
                        tint = if (liked) Color.Red else Color.LightGray
                    )
                }
                Text("$likes   💬 ${commentList.size}")
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = newComment,
                    onValueChange = { newComment = it },
                    placeholder = { Text("댓글을 입력하세요...") },
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = {
                    if (newComment.isNotBlank()) {
                        addComment(newComment)
                        newComment = ""
                    }
                }) {
                    Text("댓글 추가")
                }
            }

            Spacer(Modifier.height(16.dp))

            if (commentList.isNotEmpty()) {
                Text("댓글 (${commentList.size})", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    commentList.forEachIndexed { index, (writer, comment) ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            elevation = CardDefaults.cardElevation(2.dp)
                        ) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(writer, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    Text(comment)
                                }
                                if (writer == userName) {
                                    IconButton(onClick = {
                                        commentList = commentList.toMutableList().also { it.removeAt(index) }
                                        saveComments(prefs, postId, commentList)
                                        onUpdateStats(likes, commentList.size)
                                    }) {
                                        Icon(Icons.Default.Delete, contentDescription = "댓글 삭제", tint = Color.Gray)
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                Text("아직 댓글이 없습니다.")
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