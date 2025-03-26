package com.salmansaleem.i220904

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import android.widget.ImageView
import android.widget.TextView

class DM : AppCompatActivity() {
    private lateinit var followersRecyclerView: RecyclerView
    private lateinit var followerAdapter: DMFollower_Adapter
    private lateinit var database: DatabaseReference
    private lateinit var backButton: ImageView
    private lateinit var usernameTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dm)

        // Initialize views
        followersRecyclerView = findViewById(R.id.followersRecyclerView)
        backButton = findViewById(R.id.back)
        usernameTextView = findViewById(R.id.username)

        followerAdapter = DMFollower_Adapter()
        followersRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@DM)
            adapter = followerAdapter
        }

        database = FirebaseDatabase.getInstance().reference.child("Users")

        // Set current username
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        database.child(currentUserId).get().addOnSuccessListener { snapshot ->
            val currentUser = snapshot.getValue(User::class.java)
            currentUser?.let {
                usernameTextView.text = it.username
                fetchFollowers(currentUserId)
            }
        }.addOnFailureListener {
            Log.e("DM", "Failed to fetch current user: ${it.message}")
        }

        // Back button functionality
        backButton.setOnClickListener {
            finish() // Close the activity
        }
    }

    private fun fetchFollowers(currentUserId: String) {
        database.child(currentUserId).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val currentUser = snapshot.getValue(User::class.java) ?: return
                val followersList = mutableListOf<User>()

                if (currentUser.followers.isEmpty()) {
                    followerAdapter.setFollowers(emptyList())
                    return
                }

                // Fetch all users to match followers
                database.get().addOnSuccessListener { allUsersSnapshot ->
                    val allUsers = allUsersSnapshot.children.mapNotNull { it.getValue(User::class.java) }
                    currentUser.followers.forEach { follower ->
                        allUsers.find { it.username == follower.username }?.let { followerUser ->
                            if (!followersList.contains(followerUser)) {
                                followersList.add(followerUser)
                            }
                        }
                    }
                    followerAdapter.setFollowers(followersList)
                }.addOnFailureListener {
                    Log.e("DM", "Failed to fetch all users: ${it.message}")
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("DM", "Database error: ${error.message}")
            }
        })
    }
}