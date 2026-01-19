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
import com.google.firebase.firestore.Query

data class CrewMessage(
    val id: String = "",
    val uid: String = "",
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

    // ✅ 실시간 구독 (messages 컬렉션)
    DisposableEffect(crewId) {
        val reg = db.collection("crews").document(crewId)
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("크루 채팅") }
            )
        }
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
                                        text = msg.uid.take(6),
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
                    singleLine = true
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        val uid = FirebaseAuth.getInstance().currentUser?.uid
                        if (uid == null) {
                            Toast.makeText(context, "로그인이 필요합니다", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        val text = input.trim()
                        if (text.isBlank()) return@Button

                        input = ""

                        db.collection("crews").document(crewId)
                            .collection("messages")
                            .add(
                                mapOf(
                                    "uid" to uid,
                                    "text" to text,
                                    "createdAt" to FieldValue.serverTimestamp()
                                )
                            )
                            .addOnFailureListener {
                                Toast.makeText(context, "전송 실패: ${it.message}", Toast.LENGTH_SHORT).show()
                            }
                    }
                ) {
                    Text("전송")
                }
            }
        }
    }
}