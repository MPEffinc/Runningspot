package com.example.runningspot.data

import android.app.DownloadManager
import android.net.Uri
import com.example.runningspot.data.model.CommunityPost
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.storage.FirebaseStorage
import java.util.UUID

data class Comment(
        val userId: String = "",
        val userName: String = "",
        val text: String = "",
        val createdAt: com.google.firebase.Timestamp? = null
    )
class CommunityPostRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
){
    suspend fun fetchLatestPosts(limit: Long = 20): List<Pair<String, CommunityPost>> {
        val snap = db.collection("posts")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(limit)
            .get()
            .await()

        return snap.documents.mapNotNull { doc ->
            doc.toObject(CommunityPost::class.java)?.let { post ->
                doc.id to post   // ✅ 여기!
            }
        }
    }
    suspend fun toggleLike(postDocId: String): Boolean {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
            ?: throw IllegalStateException("로그인이 필요합니다")

        val postRef = db.collection("posts").document(postDocId)
        val likeRef = postRef.collection("likes").document(uid)

        return db.runTransaction { tx ->
            val likeSnap = tx.get(likeRef)

            if (likeSnap.exists()) {
                // unlike
                tx.delete(likeRef)
                tx.update(postRef, "likeCount", FieldValue.increment(-1))
                false
            } else {
                // like
                tx.set(likeRef, mapOf("createdAt" to FieldValue.serverTimestamp()))
                tx.update(postRef, "likeCount", FieldValue.increment(1))
                true
            }
        }.await()
    }
    suspend fun isLikedByMe(postDocId: String): Boolean {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return false
        val snap = db.collection("posts")
            .document(postDocId)
            .collection("likes")
            .document(uid)
            .get()
            .await()
        return snap.exists()
    }
    suspend fun fetchPost(postDocId: String): CommunityPost? {
        val doc = db.collection("posts").document(postDocId).get().await()
        return doc.toObject(CommunityPost::class.java)
    }

    suspend fun fetchComments(postDocId: String, limit: Long = 50): List<Pair<String, Comment>> {
        val snap = db.collection("posts")
            .document(postDocId)
            .collection("comments")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .limit(limit)
            .get()
            .await()

        return snap.documents.map { it.id to (it.toObject(Comment::class.java) ?: Comment()) }
    }

    suspend fun addComment(postDocId: String, text: String,userName: String?) {
        val user = FirebaseAuth.getInstance().currentUser
            ?: throw IllegalStateException("로그인이 필요합니다")

        val postRef = db.collection("posts").document(postDocId)
        val commentsRef = postRef.collection("comments")

        db.runTransaction { tx ->
            tx.set(commentsRef.document(), mapOf(
                "userId" to user.uid,   // ✅ 댓글 작성자 UID
                "userName" to (userName ?: user.displayName ?: "익명"),
                "text" to text,
                "createdAt" to FieldValue.serverTimestamp()
            ))
            tx.update(postRef, "commentCount", FieldValue.increment(1))
        }.await()
    }
    suspend fun deleteComment(postDocId: String, commentId: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
            ?: throw IllegalStateException("로그인이 필요합니다")

        val postRef = db.collection("posts").document(postDocId)
        val commentRef = postRef.collection("comments").document(commentId)

        db.runTransaction { tx ->
            val snap = tx.get(commentRef)
            if (!snap.exists()) return@runTransaction

            val userId = snap.getString("userId")
            if (userId != uid) {
                throw IllegalStateException("작성자만 삭제할 수 있습니다")
            }

            tx.delete(commentRef)
            tx.update(postRef, "commentCount", FieldValue.increment(-1))
        }.await()
    }
    suspend fun deletePost(docId: String) {
        val postRef = db.collection("posts").document(docId)

        // ✅ 0) 먼저 게시글 문서에서 imageUrls 가져오기 (삭제 전에!)
        val postSnap = postRef.get().await()
        val imageUrls = (postSnap.get("imageUrls") as? List<*>)  // Any list
            ?.mapNotNull { it as? String }
            ?.filter { it.isNotBlank() }
            ?: emptyList()

        // ✅ 1) 댓글 삭제
        val commentsSnap = postRef.collection("comments").get().await()
        for (doc in commentsSnap.documents) {
            doc.reference.delete().await()
        }

        // ✅ 2) 좋아요 삭제 (너 구조에 맞게 /likes 서브컬렉션이면 이것도 같이)
        val likesSnap = postRef.collection("likes").get().await()
        for (doc in likesSnap.documents) {
            doc.reference.delete().await()
        }

        // ✅ 3) 게시글 문서 삭제
        postRef.delete().await()

        // ✅ 4) Storage 이미지 삭제 (문서 삭제 후 해도 됨)
        //    - URL이 이미 지워졌어도 스토리지는 따로 존재하니까 여기서 지워줘야 함
        val storage = FirebaseStorage.getInstance()
        for (url in imageUrls) {
            try {
                storage.getReferenceFromUrl(url).delete().await()
            } catch (_: Exception) {
                // 토큰 만료/이미 삭제됨/권한 문제 등으로 실패할 수 있음
                // 앱이 죽지 않게 무시(원하면 로그 찍어도 됨)
            }
        }
    }
    suspend fun uploadPostImageToStorage(imageUri: Uri): String {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
            ?: throw IllegalStateException("로그인이 필요합니다")

        val storage = FirebaseStorage.getInstance()
        val fileName = "${UUID.randomUUID()}.jpg"

        // 경로는 취향인데 보통 이런 식
        val ref = storage.reference.child("posts/$uid/$fileName")

        // 업로드
        ref.putFile(imageUri).await()

        // 다운로드 URL 얻기 (이걸 Firestore에 저장)
        return ref.downloadUrl.await().toString()
    }
}