package com.salmansaleem.i220904

import java.io.Serializable

data class Message(
    val senderId: String = "",           // UID of the sender
    val receiverId: String = "",         // UID of the receiver
    val text: String? = null,            // Text content (null if image/post)
    val imageBase64: String? = null,     // Base64-encoded image (null if text/post)
    val postId: String? = null,          // Timestamp of the post (null if text/image)
    val timestamp: Long = System.currentTimeMillis(), // When the message was sent
    var isEdited: Boolean = false,       // Flag for edited messages
    var isDeleted: Boolean = false,      // Flag for deleted messages
    val vanishMode: Boolean = false      // Whether this message is in vanish mode
) : Serializable

