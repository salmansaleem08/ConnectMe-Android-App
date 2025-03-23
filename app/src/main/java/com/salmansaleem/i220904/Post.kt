package com.salmansaleem.i220904

data class Post(
    val photoBase64: String = "", // Changed from photoUrl to photoBase64
    val caption: String = "",
    val likeCount: Int = 0,
    val comments: MutableList<Comment> = mutableListOf(),
    val timestamp: Long = System.currentTimeMillis()
)
