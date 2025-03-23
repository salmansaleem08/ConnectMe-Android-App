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
import com.salmansaleem.i220904.StoryFollowerAdapter
import android.widget.ImageView

class Home : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: StoryFollowerAdapter
    private val followersList = mutableListOf<User>()
    private lateinit var profilePicImageView: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_home)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        profilePicImageView = findViewById(R.id.profilepic)

        recyclerView = findViewById(R.id.stories_recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        adapter = StoryFollowerAdapter(followersList)
        recyclerView.adapter = adapter

        fetchCurrentUserProfile()
        fetchFollowersFromDatabase()

        var btn1 = findViewById<ImageView>(R.id.search)
        btn1.setOnClickListener {
            val intent = Intent(this, Search::class.java)
            startActivity(intent)
        }

        var btn2 = findViewById<ImageView>(R.id.add)
        btn2.setOnClickListener {
            val intent = Intent(this, NewPost::class.java)
            startActivity(intent)
        }

        var btn3 = findViewById<ImageView>(R.id.myProfile)
        btn3.setOnClickListener {
            val intent = Intent(this, MyProfile::class.java)
            startActivity(intent)
        }

        var btn4 = findViewById<ImageView>(R.id.contacts)
        btn4.setOnClickListener {
            val intent = Intent(this, Contacts::class.java)
            startActivity(intent)
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
                    profilePicImageView.setImageResource(R.drawable.default_profile) // Set default if no image
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
}