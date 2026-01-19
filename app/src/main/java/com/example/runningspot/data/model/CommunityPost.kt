package com.example.runningspot.data.model

import com.google.firebase.Timestamp

data class CommunityPost(
    val authorId: String = "",
    val authorName: String = "",
    val content: String = "",
    val imageUrls: List<String> = emptyList(),
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null,
    val likeCount: Long = 0,
    val commentCount: Long = 0,
    val docId: String = ""
)