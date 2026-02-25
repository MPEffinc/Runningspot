package com.example.runningspot.ui

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.launch
data class CrewMessage(
    val id: String = "",
    val uid: String = "",
    val username: String = "",
    val text: String = "",
    val createdAt: Timestamp? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrewChatScreen(crewId: String) {
    val context = LocalContext.current
    val db = remember { FirebaseFirestore.getInstance() }
    val myUid = FirebaseAuth.getInstance().currentUser?.uid

    var input by remember { mutableStateOf("") }
    var messages by remember { mutableStateOf<List<CrewMessage>>(emptyList()) }

    // ✅ 멤버 여부 (멤버만 채팅 가능)
    var isMember by remember { mutableStateOf(false) }

    // ✅ 내 닉네임(1회 로딩해서 캐시)
    var myNickname by remember { mutableStateOf<String?>(null) }

    // ✅ 내 닉네임 로딩: users/{myUid}.nickname (1회)
    LaunchedEffect(myUid) {
        if (myUid == null) {
            myNickname = null
            return@LaunchedEffect
        }
        try {
            val doc = db.collection("users").document(myUid).get().await()
            myNickname = doc.getString("nickname")
        } catch (_: Exception) {
            myNickname = null
        }
    }

    // ✅ 실시간 구독 (messages 컬렉션)
    DisposableEffect(crewId) {
        val reg: ListenerRegistration = db.collection("crews").document(crewId)
            .collection("messages")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, e ->
                if (e != null) return@addSnapshotListener
                if (snap == null) return@addSnapshotListener

                messages = snap.documents.mapNotNull { doc ->
                    val m = doc.toObject(CrewMessage::class.java) ?: return@mapNotNull null
                    m.copy(id = doc.id)
                }
            }

        onDispose { reg.remove() }
    }
    val scope = rememberCoroutineScope()

    // ✅ 멤버 문서 실시간 구독: crews/{crewId}/members/{uid} 존재 여부
    DisposableEffect(crewId, myUid) {
        if (myUid == null) {
            isMember = false
            onDispose { }
        } else {
            val reg = db.collection("crews").document(crewId)
                .collection("members").document(myUid)
                .addSnapshotListener { snap, _ ->
                    isMember = (snap != null && snap.exists())
                }
            onDispose { reg.remove() }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("크루 채팅") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {

            // 메시지 리스트
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    val isMe = (myUid != null && msg.uid == myUid)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
                    ) {
                        Surface(
                            tonalElevation = 2.dp,
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                if (!isMe) {
                                    Text(
                                        text = msg.username.ifBlank { "사용자" },
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                    Spacer(Modifier.height(2.dp))
                                }
                                Text(msg.text)
                            }
                        }
                    }
                }
            }

            // 입력창 + 전송
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("메시지 입력") },
                    singleLine = true,
                    enabled = isMember
                )
                Spacer(Modifier.width(8.dp))

                Button(
                    enabled = isMember,
                    onClick = {
                        val user = FirebaseAuth.getInstance().currentUser
                        val uid = user?.uid
                        if (uid == null) {
                            Toast.makeText(context, "로그인이 필요합니다", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (!isMember) {
                            Toast.makeText(context, "크루 멤버만 채팅할 수 있어요", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        val text = input.trim()
                        if (text.isBlank()) return@Button
                        scope.launch {
                            try {
                                // 전송 전에 nickname을 "확실히" 얻는다
                                val nickFromUsers = try {
                                    val doc = db.collection("users").document(uid).get().await()
                                    doc.getString("nickname")
                                } catch (_: Exception) {
                                    null
                                }

                                val username = nickFromUsers
                                    ?: myNickname
                                    ?: user.displayName
                                    ?: user.email?.substringBefore("@")
                                    ?: "사용자"

                                // 여기서도 "사용자"면 users 저장이 안 된 거라서 바로 알 수 있게 안내
                                if (username == "사용자") {
                                    Toast.makeText(
                                        context,
                                        "닉네임을 불러오지 못했어요. users/{uid}.nickname 저장을 확인해줘요.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }

                                input = ""

                                val crewDoc = db.collection("crews").document(crewId)
                                val msgDoc = crewDoc.collection("messages").document()

                                db.runBatch { batch ->
                                    batch.set(msgDoc, mapOf(
                                        "uid" to uid,
                                        "username" to username,
                                        "text" to text,
                                        "createdAt" to FieldValue.serverTimestamp()
                                    ))

                                    batch.set(
                                        crewDoc,
                                        mapOf(
                                            "lastMessage" to text,
                                            "lastMessageAt" to FieldValue.serverTimestamp()
                                        ),
                                        SetOptions.merge()
                                    )
                                }.await()

                            } catch (e: Exception) {
                                Toast.makeText(context, "전송 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                ) {
                    Text("전송")
                }
            }

            if (!isMember) {
                Text(
                    text = "크루에 참여한 멤버만 채팅할 수 있어요.",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}