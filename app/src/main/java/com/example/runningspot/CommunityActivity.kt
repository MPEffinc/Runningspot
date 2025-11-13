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
import androidx.compose.foundation.background
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
import com.example.runningspot.ui.loadPosts
import org.json.JSONArray
import org.json.JSONObject
import kotlin.run

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("커뮤니티", fontSize = 20.sp) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
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


            // 거리 + 페이스
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFEDE7F6)) // 연보라
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("거리 7.2km", fontWeight = FontWeight.SemiBold, color = Color(0xFF4A3C96))
                Text("페이스 5'10''/km", fontWeight = FontWeight.SemiBold, color = Color(0xFF4A3C96))
            }

            Spacer(Modifier.height(20.dp))

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