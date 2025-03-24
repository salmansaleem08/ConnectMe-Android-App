package com.salmansaleem.i220904

import android.content.Intent
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

class FollowersActivity : AppCompatActivity() {

    private lateinit var usernameTextView: TextView
    private lateinit var followersCountTextView: TextView // Updated to followers
    private lateinit var followingCountTextView: TextView // Updated to following
    private lateinit var backButton: ImageView
    private lateinit var searchEditText: EditText
    private lateinit var recyclerViewFollowers: RecyclerView
    private lateinit var followersAdapter: FollowersAdapter
    private val followersList = mutableListOf<User>()
    private val fullFollowersList = mutableListOf<User>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_followers)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Initialize views
        usernameTextView = findViewById(R.id.name)
        followersCountTextView = findViewById(R.id.countfollowers) // New ID
        followingCountTextView = findViewById(R.id.countfollowing) // New ID
        backButton = findViewById(R.id.back)
        searchEditText = findViewById(R.id.search_edit_text) // Updated ID
        recyclerViewFollowers = findViewById(R.id.recyclerViewFollowers)

        // Set up RecyclerView
        recyclerViewFollowers.layoutManager = LinearLayoutManager(this)
        followersAdapter = FollowersAdapter(followersList)
        recyclerViewFollowers.adapter = followersAdapter

        // Back button functionality
        backButton.setOnClickListener { finish() }

        // Set up search functionality
        setupSearch()

        // Fetch user data and followers
        fetchUserData()


        var btn1 = findViewById<TextView>(R.id.countfollowing)
        btn1.setOnClickListener {
            val intent = Intent(this, FollowingActivity::class.java)
            startActivity(intent)
        }

    }

    private fun fetchUserData() {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        if (currentUserId == null) {
            Log.e("Followers", "User not authenticated")
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
                   // followersCountTextView.text = "${user.followers.size} Followers"
                    followingCountTextView.text = "${user.following.size} Following"
                    followersCountTextView.text = getString(R.string.followers_count, user.followers.size)
                    //followingCountTextView.text = getString(R.string.following_count, user.following.size)

                    // Fetch followers
                    fetchFollowersFromDatabase()
                } else {
                    Log.e("Followers", "User data not found")
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("Followers", "Database error: ${error.message}")
            }
        })
    }

    private fun fetchFollowersFromDatabase() {
        val database = FirebaseDatabase.getInstance().reference.child("Users")
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        database.child(currentUserId).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                followersList.clear()
                fullFollowersList.clear()
                val currentUser = snapshot.getValue(User::class.java)

                if (currentUser?.followers.isNullOrEmpty()) {
                    followersAdapter.notifyDataSetChanged()
                    recyclerViewFollowers.visibility = RecyclerView.GONE
                    Log.d("Followers", "No followers found")
                    return
                }

                // Fetch all users once
                database.get().addOnSuccessListener { allUsersSnapshot ->
                    val allUsers = allUsersSnapshot.children.mapNotNull { it.getValue(User::class.java) }
                    currentUser?.followers?.forEach { follower ->
                        allUsers.find { it.username == follower.username }?.let { followerUser ->
                            if (!followersList.contains(followerUser)) {
                                followersList.add(followerUser)
                                fullFollowersList.add(followerUser)
                            }
                        }
                    }
                    followersAdapter.notifyDataSetChanged()
                    recyclerViewFollowers.visibility = RecyclerView.VISIBLE
                    Log.d("Followers", "Loaded ${followersList.size} followers")
                }.addOnFailureListener { error ->
                    Log.e("Followers", "Failed to fetch all users: ${error.message}")
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("Followers", "Database error: ${error.message}")
            }
        })
    }

    private fun setupSearch() {
        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                filterFollowers(s.toString())
            }
        })
    }

    private fun filterFollowers(query: String) {
        followersList.clear()
        val filteredList = if (query.isEmpty()) {
            fullFollowersList
        } else {
            fullFollowersList.filter { user ->
                user.username.lowercase().contains(query.lowercase())
            }
        }

        followersList.addAll(filteredList)
        followersAdapter.notifyDataSetChanged()
        recyclerViewFollowers.visibility = if (followersList.isEmpty()) RecyclerView.GONE else RecyclerView.VISIBLE
        Log.d("Followers", "Filtered to ${followersList.size} followers")
    }
}