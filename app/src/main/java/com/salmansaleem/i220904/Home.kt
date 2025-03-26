package com.salmansaleem.i220904


import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.os.Bundle
import android.util.Base64
import android.util.Log
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
import android.widget.ImageView
import com.google.firebase.database.ChildEventListener

class Home : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: StoryFollowerAdapter
    private val followersList = mutableListOf<User>()
    private lateinit var profilePicImageView: ImageView


    private lateinit var postRecyclerView: RecyclerView
    private lateinit var postAdapter: PostAdapter
    private val postsMap = mutableMapOf<String, Pair<User, Post>>() // To avoid duplicates and sort later


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_home)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }


        postRecyclerView = findViewById(R.id.post_recycler_view)
        postAdapter = PostAdapter()

        postRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@Home)
            adapter = postAdapter
        }

        fetchPostsFromFollowers()



        profilePicImageView = findViewById(R.id.profilepic)
        recyclerView = findViewById(R.id.stories_recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        adapter = StoryFollowerAdapter(followersList) { user ->
            val intent = Intent(this, StoryViewActivity::class.java)
            intent.putExtra("USER_ID", user.uid)
            startActivity(intent)
        }
        recyclerView.adapter = adapter

        fetchCurrentUserProfile()
        fetchFollowersFromDatabase()

        // Current user's stories on profile click
        profilePicImageView.setOnClickListener {
            val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return@setOnClickListener
            val intent = Intent(this, StoryViewActivity::class.java)
            intent.putExtra("USER_ID", currentUserId)
            startActivity(intent)
        }

        findViewById<ImageView>(R.id.search).setOnClickListener {
            startActivity(Intent(this, Search::class.java))
        }

        findViewById<ImageView>(R.id.add).setOnClickListener {
            startActivity(Intent(this, StoryActivity::class.java))
        }

        findViewById<ImageView>(R.id.myProfile).setOnClickListener {
            startActivity(Intent(this, MyProfile::class.java))
        }

        findViewById<ImageView>(R.id.contacts).setOnClickListener {
            startActivity(Intent(this, Contacts::class.java))
        }

        findViewById<ImageView>(R.id.send).setOnClickListener {
            startActivity(Intent(this, DM::class.java))
        }
    }

    private fun fetchCurrentUserProfile() {
        val database = FirebaseDatabase.getInstance().reference.child("Users")
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        database.child(currentUserId).get().addOnSuccessListener { snapshot ->
            val currentUser = snapshot.getValue(User::class.java)
            currentUser?.let {
                if (it.profileImageBase64.isNotEmpty()) {
                    try {
                        val decodedBytes = Base64.decode(it.profileImageBase64, Base64.DEFAULT)
                        val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                        val circularBitmap = getCircularBitmap(bitmap)
                        profilePicImageView.setImageBitmap(circularBitmap)
                    } catch (e: Exception) {
                        Log.e("Home", "Failed to load profile picture: ${e.message}")
                        profilePicImageView.setImageResource(R.drawable.default_profile)
                    }
                } else {
                    profilePicImageView.setImageResource(R.drawable.default_profile)
                }
            }
        }.addOnFailureListener { error ->
            Log.e("Home", "Failed to fetch current user: ${error.message}")
            profilePicImageView.setImageResource(R.drawable.default_profile)
        }
    }

    private fun fetchFollowersFromDatabase() {
        val database = FirebaseDatabase.getInstance().reference.child("Users")
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        database.child(currentUserId).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                followersList.clear()
                val currentUser = snapshot.getValue(User::class.java)

                if (currentUser?.followers.isNullOrEmpty()) {
                    adapter.notifyDataSetChanged()
                    return
                }

                database.get().addOnSuccessListener { allUsersSnapshot ->
                    val allUsers = allUsersSnapshot.children.mapNotNull { it.getValue(User::class.java) }
                    currentUser?.followers?.forEach { follower ->
                        allUsers.find { it.username == follower.username && it.uid != currentUserId }?.let { followerUser ->
                            if (!followersList.contains(followerUser)) {
                                followersList.add(followerUser)
                            }
                        }
                    }
                    adapter.notifyDataSetChanged()
                }.addOnFailureListener { error ->
                    Log.e("Home", "Failed to fetch all users: ${error.message}")
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("Home", "Database error: ${error.message}")
            }
        })
    }

    private fun getCircularBitmap(bitmap: Bitmap): Bitmap {
        val size = Math.min(bitmap.width, bitmap.height)
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint()
        val rect = android.graphics.Rect(0, 0, size, size)

        paint.isAntiAlias = true
        canvas.drawARGB(0, 0, 0, 0)
        paint.color = android.graphics.Color.WHITE

        val radius = size / 2f
        canvas.drawCircle(radius, radius, radius, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(bitmap, rect, rect, paint)

        return output
    }



    private fun fetchPostsFromFollowers() {
        val database = FirebaseDatabase.getInstance().reference.child("Users")
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        // Get the current user's following list first
        database.child(currentUserId).get().addOnSuccessListener { snapshot ->
            val currentUser = snapshot.getValue(User::class.java)
            val followingIds = currentUser?.following?.map { it.username }?.toSet() ?: emptySet()

            // Listen for users and their posts incrementally
            database.addChildEventListener(object : ChildEventListener {
                override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                    val user = snapshot.getValue(User::class.java) ?: return
                    if (user.username in followingIds || user.uid == currentUserId) {
                        user.posts.forEach { post ->
                            postAdapter.addPost(user, post) // Add each post as it loads
                        }
                    }
                }

                override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                    val user = snapshot.getValue(User::class.java) ?: return
                    if (user.username in followingIds || user.uid == currentUserId) {
                        user.posts.forEach { post ->
                            // Only add new posts that aren't already in the list
                            if (posts.none { it.second.timestamp == post.timestamp }) {
                                postAdapter.addPost(user, post)
                            }
                        }
                    }
                }

                override fun onChildRemoved(snapshot: DataSnapshot) {
                    // Handle post removal if needed
                }

                override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {
                    // Not needed since we're not sorting
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("Home", "Database error: ${error.message}")
                }
            })
        }.addOnFailureListener {
            Log.e("Home", "Failed to fetch current user: ${it.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clean up listeners if necessary
    }

    private val posts: List<Pair<User, Post>>
        get() = postAdapter.posts

}