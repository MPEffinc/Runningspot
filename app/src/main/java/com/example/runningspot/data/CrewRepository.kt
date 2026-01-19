package com.example.runningspot.data

import androidx.room.util.copy
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import kotlin.jvm.java
import kotlin.reflect.KClass
import com.example.runningspot.data.model.CrewPost

class CrewRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    suspend fun fetchCrews(): List<CrewPost> {
        val snap = db.collection("crews")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get()
            .await()

        return snap.documents.map { doc ->
            doc.toObject(CrewPost::class.java)!!.copy(id = doc.id)
        }
    }

    suspend fun joinCrew(crewId: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
            ?: throw IllegalStateException("로그인이 필요합니다")

        val crewRef = db.collection("crews").document(crewId)
        val memberRef = crewRef.collection("members").document(uid)

        db.runTransaction { tx ->
            val crewSnap = tx.get(crewRef)

            val current = crewSnap.getLong("currentMembers") ?: 0
            val max = crewSnap.getLong("maxMembers") ?: 0

            if (current >= max) {
                throw IllegalStateException("모집이 마감되었습니다")
            }

            if (tx.get(memberRef).exists()) {
                throw IllegalStateException("이미 참여한 크루입니다")
            }

            tx.set(memberRef, mapOf("joinedAt" to FieldValue.serverTimestamp()))
            tx.update(crewRef, "currentMembers", current + 1)
        }.await()
    }
    suspend fun isMember(crewId: String): Boolean {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return false
        val doc = db.collection("crews").document(crewId)
            .collection("members").document(uid)
            .get().await()
        return doc.exists()
    }
    suspend fun fetchCrew(crewId: String): CrewPost? {
        val doc = db.collection("crews").document(crewId).get().await()
        return doc.toObject(CrewPost::class.java)?.copy(id = doc.id)
    }
    suspend fun leaveCrew(crewId: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
            ?: throw IllegalStateException("로그인이 필요합니다")

        val crewRef = db.collection("crews").document(crewId)
        val memberRef = crewRef.collection("members").document(uid)

        db.runTransaction { tx ->
            val crewSnap = tx.get(crewRef)
            val memberSnap = tx.get(memberRef)

            if (!memberSnap.exists()) throw IllegalStateException("참여한 크루가 아닙니다")

            val current = crewSnap.getLong("currentMembers") ?: 0
            val next = (current - 1).coerceAtLeast(0)

            tx.delete(memberRef)
            tx.update(crewRef, "currentMembers", next)
        }.await()
    }
}