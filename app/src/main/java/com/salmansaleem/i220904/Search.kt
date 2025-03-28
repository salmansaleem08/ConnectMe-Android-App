package com.salmansaleem.i220904

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
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

class Search : AppCompatActivity() {

    private lateinit var searchEditText: EditText
    private lateinit var recentSearchesLayout: LinearLayout
    private lateinit var searchResultsRecyclerView: RecyclerView
    private lateinit var searchAdapter: SearchResultAdapter
    private lateinit var recentTitle: TextView
    private lateinit var filterFollowers: TextView
    private lateinit var filterFollowing: TextView
    private val searchResultsList = mutableListOf<User>()
    private val allUsers = mutableListOf<User>()
    private var followersFilter = false
    private var followingFilter = false
    private var currentUser: User? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_search)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        searchEditText = findViewById(R.id.search_edit_text)
        recentSearchesLayout = findViewById(R.id.recentSearches)
        searchResultsRecyclerView = findViewById(R.id.search_results_recycler_view)
        recentTitle = findViewById(R.id.recent)
        filterFollowers = findViewById(R.id.filter_followers)
        filterFollowing = findViewById(R.id.filter_following)

        searchResultsRecyclerView.layoutManager = LinearLayoutManager(this)
        searchAdapter = SearchResultAdapter(searchResultsList)
        searchResultsRecyclerView.adapter = searchAdapter

        fetchCurrentUser()
        fetchAllUsers()
        setupSearchListener()
        setupFilterListeners()
        loadRecentSearches()


        findViewById<ImageView>(R.id.search).setOnClickListener {
            startActivity(Intent(this, Search::class.java))
        }

        findViewById<ImageView>(R.id.add).setOnClickListener {
            startActivity(Intent(this, StoryActivity::class.java))
        }

        findViewById<ImageView>(R.id.myProfile).setOnClickListener {
            startActivity(Intent(this, MyProfile::class.java))
        }

        findViewById<ImageView>(R.id.home).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }

        findViewById<ImageView>(R.id.contacts).setOnClickListener {
            startActivity(Intent(this, Contacts::class.java))
        }
    }

    private fun fetchCurrentUser() {
        val database = FirebaseDatabase.getInstance().reference.child("Users")
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        database.child(currentUserId).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                currentUser = snapshot.getValue(User::class.java) ?: User()
                Log.d("Search", "Current user: ${currentUser?.username}, Followers: ${currentUser?.followers?.size}, Following: ${currentUser?.following?.size}")
                // Trigger search update after current user is fetched
                searchUsers(searchEditText.text.toString().trim())
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("Search", "Failed to fetch current user: ${error.message}")
            }
        })
    }

    private fun fetchAllUsers() {
        val database = FirebaseDatabase.getInstance().reference.child("Users")
        database.get().addOnSuccessListener { snapshot ->
            allUsers.clear()
            allUsers.addAll(snapshot.children.mapNotNull { it.getValue(User::class.java) })
            Log.d("Search", "All users fetched: ${allUsers.size}")
        }.addOnFailureListener { error ->
            Log.e("Search", "Failed to fetch users: ${error.message}")
        }
    }

    private fun setupFilterListeners() {
        filterFollowers.setOnClickListener {
            followersFilter = !followersFilter
            filterFollowers.isSelected = followersFilter
            filterFollowers.setTextColor(if (followersFilter) android.graphics.Color.WHITE else getColor(R.color.filter_default))
            Log.d("Search", "Followers filter toggled: $followersFilter")
            searchUsers(searchEditText.text.toString().trim())
        }

        filterFollowing.setOnClickListener {
            followingFilter = !followingFilter
            filterFollowing.isSelected = followingFilter
            filterFollowing.setTextColor(if (followingFilter) android.graphics.Color.WHITE else getColor(R.color.filter_default))
            Log.d("Search", "Following filter toggled: $followingFilter")
            searchUsers(searchEditText.text.toString().trim())
        }
    }

    private fun setupSearchListener() {
        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                val query = s.toString().trim()
                if (query.isEmpty()) {
                    searchResultsRecyclerView.visibility = View.GONE
                    recentSearchesLayout.visibility = View.VISIBLE
                    recentTitle.text = "Recent searches"
                } else {
                    searchResultsRecyclerView.visibility = View.VISIBLE
                    recentSearchesLayout.visibility = View.GONE
                    recentTitle.text = "Searching"
                    searchUsers(query)
                }
            }
        })
    }

    private fun searchUsers(query: String) {
        searchResultsList.clear()
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        val filteredUsers = allUsers.filter {
            (it.username.contains(query, ignoreCase = true) || it.name.contains(query, ignoreCase = true)) && it.uid != currentUserId
        }.filter { user ->
            if (currentUser == null) {
                Log.w("Search", "Current user not fetched yet, showing all results")
                true // Show all until currentUser loads
            } else {
                val followersUsernames = currentUser?.followers?.map { it.username } ?: emptyList()
                val followingUsernames = currentUser?.following?.map { it.username } ?: emptyList()
                when {
                    followersFilter && followingFilter -> followersUsernames.contains(user.username) && followingUsernames.contains(user.username)
                    followersFilter -> followersUsernames.contains(user.username)
                    followingFilter -> followingUsernames.contains(user.username)
                    else -> true
                }
            }
        }

        searchResultsList.addAll(filteredUsers)
        Log.d("Search", "Filtered users: ${searchResultsList.size}, Query: $query, Filters: F=$followersFilter, Fg=$followingFilter")
        searchAdapter.notifyDataSetChanged()

        searchResultsRecyclerView.addOnItemTouchListener(
            RecyclerItemClickListener(this, searchResultsRecyclerView, object : RecyclerItemClickListener.OnItemClickListener {
                override fun onItemClick(view: View, position: Int) {
                    val selectedUser = searchResultsList[position]
                    addToRecentSearches(selectedUser.username)
                }
            })
        )
    }

    private fun loadRecentSearches() {
        val database = FirebaseDatabase.getInstance().reference.child("Users")
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        database.child(currentUserId).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                recentSearchesLayout.removeAllViews()
                val currentUser = snapshot.getValue(User::class.java)
                currentUser?.recentSearches?.take(5)?.forEach { search ->
                    val view = LayoutInflater.from(this@Search).inflate(
                        R.layout.item_recent_search, recentSearchesLayout, false
                    )
                    val usernameTextView = view.findViewById<TextView>(R.id.recent_search_text)
                    val removeButton = view.findViewById<ImageView>(R.id.remove_search)

                    usernameTextView.text = search
                    removeButton.setOnClickListener {
                        removeFromRecentSearches(search)
                    }
                    recentSearchesLayout.addView(view)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("Search", "Failed to load recent searches: ${error.message}")
            }
        })
    }

    private fun addToRecentSearches(username: String) {
        val database = FirebaseDatabase.getInstance().reference.child("Users")
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        database.child(currentUserId).get().addOnSuccessListener { snapshot ->
            val currentUser = snapshot.getValue(User::class.java)
            currentUser?.let {
                val updatedSearches = it.recentSearches.toMutableList()
                if (!updatedSearches.contains(username)) {
                    if (updatedSearches.size >= 5) updatedSearches.removeAt(0)
                    updatedSearches.add(username)
                    database.child(currentUserId).child("recentSearches").setValue(updatedSearches)
                }
            }
        }
    }

    private fun removeFromRecentSearches(username: String) {
        val database = FirebaseDatabase.getInstance().reference.child("Users")
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        database.child(currentUserId).get().addOnSuccessListener { snapshot ->
            val currentUser = snapshot.getValue(User::class.java)
            currentUser?.let {
                val updatedSearches = it.recentSearches.toMutableList()
                updatedSearches.remove(username)
                database.child(currentUserId).child("recentSearches").setValue(updatedSearches)
            }
        }
    }
}