package com.salmansaleem.i220904

import com.bumptech.glide.request.Request

data class User(
    val uid: String = "",
    val name: String = "",
    val username: String = "",
    val phone: String = "",
    val email: String = "",
    val profileImageBase64: String = "",
    val posts: List<String> = emptyList(), // List of post image URLs
    val stories: List<Story> = emptyList(), // List of active stories
    val followers: MutableList<Follower> = mutableListOf(), // List of followers
    val following: MutableList<Follower> = mutableListOf(), // List of following
    var requests: MutableList<User> = mutableListOf(), // List of pending requests
    val bio : String = ""
)