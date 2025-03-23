package com.salmansaleem.i220904

data class Comment(
    val userId: String = "",
    val text: String = "",
    val timestamp: Long = System.currentTimeMillis()
)