package com.salmansaleem.i220904

import android.os.Bundle
import android.util.Log
import android.widget.EditText
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class CommentActivity : AppCompatActivity() {
    private lateinit var commentsRecyclerView: RecyclerView
    private lateinit var commentEditText: EditText
    private lateinit var sendCommentButton: ImageView
    private lateinit var commentAdapter: CommentAdapter
    private lateinit var database: DatabaseReference
    private lateinit var currentUser: User

    private var userId: String? = null
    private var postTimestamp: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_comment)

        // Get post details from intent
        userId = intent.getStringExtra("USER_ID")
        postTimestamp = intent.getLongExtra("POST_TIMESTAMP", 0)

        if (userId == null || postTimestamp == 0L) {
            finish() // Invalid data, close activity
            return
        }

        // Initialize views
        commentsRecyclerView = findViewById(R.id.commentsRecyclerView)
        commentEditText = findViewById(R.id.commentEditText)
        sendCommentButton = findViewById(R.id.sendCommentButton)

        commentAdapter = CommentAdapter()
        commentsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@CommentActivity)
            adapter = commentAdapter
        }

        database = FirebaseDatabase.getInstance().reference.child("Users")

        // Fetch current user data
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        database.child(currentUserId).get().addOnSuccessListener { snapshot ->
            currentUser = snapshot.getValue(User::class.java) ?: return@addOnSuccessListener
            loadComments()
        }.addOnFailureListener {
            Log.e("CommentActivity", "Failed to fetch current user: ${it.message}")
        }

        // Send comment button click listener
        sendCommentButton.setOnClickListener {
            val commentText = commentEditText.text.toString().trim()
            if (commentText.isNotEmpty()) {
                addComment(commentText)
                commentEditText.text.clear()
            }
        }
    }

    private fun loadComments() {
        database.child(userId!!).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val user = snapshot.getValue(User::class.java) ?: return
                val post = user.posts.find { it.timestamp == postTimestamp } ?: return
                val commentsWithUsers = mutableListOf<Pair<User, Comment>>()

                // Fetch user details for each comment
                post.comments.forEach { comment ->
                    database.child(comment.userId).get().addOnSuccessListener { userSnapshot ->
                        val commenter = userSnapshot.getValue(User::class.java)
                        if (commenter != null) {
                            commentsWithUsers.add(Pair(commenter, comment))
                            commentAdapter.setComments(commentsWithUsers.sortedBy { it.second.timestamp })
                        }
                    }.addOnFailureListener {
                        Log.e("CommentActivity", "Failed to fetch commenter: ${it.message}")
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("CommentActivity", "Database error: ${error.message}")
            }
        })
    }

    private fun addComment(commentText: String) {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val newComment = Comment(
            userId = currentUserId,
            text = commentText,
            timestamp = System.currentTimeMillis()
        )

        // Update the post in the database
        database.child(userId!!).get().addOnSuccessListener { snapshot ->
            val user = snapshot.getValue(User::class.java) ?: return@addOnSuccessListener
            val postIndex = user.posts.indexOfFirst { it.timestamp == postTimestamp }
            if (postIndex != -1) {
                user.posts[postIndex].comments.add(newComment)
                database.child(userId!!).setValue(user)
                    .addOnSuccessListener {
                        commentAdapter.addComment(currentUser, newComment)
                    }
                    .addOnFailureListener {
                        Log.e("CommentActivity", "Failed to save comment: ${it.message}")
                    }
            }
        }.addOnFailureListener {
            Log.e("CommentActivity", "Failed to fetch user: ${it.message}")
        }
    }
}