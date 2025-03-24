package com.salmansaleem.i220904

data class Post(
    val photoBase64: String = "",
    val caption: String = "",
    var likeCount: Int = 0,
    val comments: MutableList<Comment> = mutableListOf(),
    val timestamp: Long = System.currentTimeMillis(),
    val likedBy: MutableList<String> = mutableListOf() // List of user IDs who liked the post
)