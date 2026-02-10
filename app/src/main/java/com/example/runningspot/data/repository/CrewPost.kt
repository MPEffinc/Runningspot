package com.example.runningspot.data.repository


data class CrewPost(
    val id: String = "",           // Firestore 문서 ID
    val title: String = "",
    val description: String = "",
    val location: String = "",
    val userId: String = "",
    val maxMembers: Long = 0,
    val currentMembers: Long = 0,
    val createdAt: com.google.firebase.Timestamp? = null
)