package com.salmansaleem.i220904

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.salmansaleem.i220904.adapters.FollowersAdapter

class FollowingActivity : AppCompatActivity() {

    private lateinit var usernameTextView: TextView
    private lateinit var followersCountTextView: TextView
    private lateinit var followingCountTextView: TextView
    private lateinit var backButton: ImageView
    private lateinit var searchEditText: EditText
    private lateinit var recyclerViewFollowing: RecyclerView
    private lateinit var followingAdapter: FollowersAdapter
    private val followingList = mutableListOf<User>()
    private val fullFollowingList = mutableListOf<User>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_following)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Initialize views
        usernameTextView = findViewById(R.id.name)
        followersCountTextView = findViewById(R.id.countfollowers)
        followingCountTextView = findViewById(R.id.countfollowing)
        backButton = findViewById(R.id.back)
        searchEditText = findViewById(R.id.search_edit_text)
        recyclerViewFollowing = findViewById(R.id.recyclerViewFollowing)

        // Set up RecyclerView
        recyclerViewFollowing.layoutManager = LinearLayoutManager(this)
        followingAdapter = FollowersAdapter(followingList)
        recyclerViewFollowing.adapter = followingAdapter

        // Back button functionality
        backButton.setOnClickListener { finish() }

        // Set up search functionality
        setupSearch()

        // Fetch user data and following
        fetchUserData()
    }

    private fun fetchUserData() {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        if (currentUserId == null) {
            Log.e("Following", "User not authenticated")
            return
        }

        val databaseRef = FirebaseDatabase.getInstance().reference.child("Users").child(currentUserId)
        databaseRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val user = snapshot.getValue(User::class.java)
                if (user != null) {
                    // Set username
                    usernameTextView.text = user.username

                    // Set followers and following counts
                    followersCountTextView.text = "${user.followers.size} Followers"
                    followingCountTextView.text = "${user.following.size} Following"

                    // Fetch following
                    fetchFollowingFromDatabase()
                } else {
                    Log.e("Following", "User data not found")
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("Following", "Database error: ${error.message}")
            }
        })
    }

    private fun fetchFollowingFromDatabase() {
        val database = FirebaseDatabase.getInstance().reference.child("Users")
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        database.child(currentUserId).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                followingList.clear()
                fullFollowingList.clear()
                val currentUser = snapshot.getValue(User::class.java)

                if (currentUser?.following.isNullOrEmpty()) {
                    followingAdapter.notifyDataSetChanged()
                    recyclerViewFollowing.visibility = RecyclerView.GONE
                    Log.d("Following", "No following found")
                    return
                }

                // Fetch all users once
                database.get().addOnSuccessListener { allUsersSnapshot ->
                    val allUsers = allUsersSnapshot.children.mapNotNull { it.getValue(User::class.java) }
                    currentUser?.following?.forEach { following ->
                        allUsers.find { it.username == following.username }?.let { followingUser ->
                            if (!followingList.contains(followingUser)) {
                                followingList.add(followingUser)
                                fullFollowingList.add(followingUser)
                            }
                        }
                    }
                    followingAdapter.notifyDataSetChanged()
                    recyclerViewFollowing.visibility = RecyclerView.VISIBLE
                    Log.d("Following", "Loaded ${followingList.size} following")
                }.addOnFailureListener { error ->
                    Log.e("Following", "Failed to fetch all users: ${error.message}")
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("Following", "Database error: ${error.message}")
            }
        })
    }

    private fun setupSearch() {
        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                filterFollowing(s.toString())
            }
        })
    }

    private fun filterFollowing(query: String) {
        followingList.clear()
        val filteredList = if (query.isEmpty()) {
            fullFollowingList
        } else {
            fullFollowingList.filter { user ->
                user.username.lowercase().contains(query.lowercase())
            }
        }

        followingList.addAll(filteredList)
        followingAdapter.notifyDataSetChanged()
        recyclerViewFollowing.visibility = if (followingList.isEmpty()) RecyclerView.GONE else RecyclerView.VISIBLE
        Log.d("Following", "Filtered to ${followingList.size} following")
    }
}