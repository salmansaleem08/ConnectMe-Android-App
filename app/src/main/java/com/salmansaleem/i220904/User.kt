package com.salmansaleem.i220904

import com.bumptech.glide.request.Request

data class User(
    val uid: String = "",
    val name: String = "",
    val username: String = "",
    val phone: String = "",
    val email: String = "",
    val profileImageBase64: String = "",
    val posts: MutableList<Post> = mutableListOf(),
    val stories: List<Story> = emptyList(),
    val followers: MutableList<Follower> = mutableListOf(),
    val following: MutableList<Follower> = mutableListOf(),
    var requests: MutableList<User> = mutableListOf(),
    val bio: String = "",
    val recentSearches: MutableList<String> = mutableListOf(),
    val online: Boolean = false,         // Add this
    val lastSeen: Long = 0L, // Optional: for "Last seen" info
    val fcmToken: String = "" // Add this field for FCM token
)