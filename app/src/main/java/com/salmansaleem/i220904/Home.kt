package com.salmansaleem.i220904

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

class Home : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: StoryFollowerAdapter
    private val FollowersList = mutableListOf<User>()
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
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = StoryFollowerAdapter(FollowersList)
        recyclerView.adapter = adapter


        fetchCurrentUserProfile()
        fetchFollowersFromDatabase()
    }


    private fun fetchFollowersFromDatabase() {
        val database = FirebaseDatabase.getInstance().reference.child("Users")
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        database.child(currentUserId).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                FollowersList.clear()
                val currentUser = snapshot.getValue(User::class.java)

                if (currentUser?.followers.isNullOrEmpty()) {
                    adapter.notifyDataSetChanged()
                    return
                }

                // Fetch all users once
                database.get().addOnSuccessListener { allUsersSnapshot ->
                    val allUsers = allUsersSnapshot.children.mapNotNull { it.getValue(User::class.java) }
                    currentUser?.followers?.forEach { follower ->
                        allUsers.find { it.username == follower.username }?.let { followerUser ->
                            if (!FollowersList.contains(followerUser)) {
                                FollowersList.add(followerUser)
                            }
                        }
                    }
                    adapter.notifyDataSetChanged()
                }.addOnFailureListener { error ->
                    Log.e("Contacts", "Failed to fetch all users: ${error.message}")
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("Contacts", "Database error: ${error.message}")
            }
        })
    }

    private fun fetchCurrentUserProfile() {
        val database = FirebaseDatabase.getInstance().reference.child("Users")
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        database.child(currentUserId).get().addOnSuccessListener { snapshot ->
            val currentUser = snapshot.getValue(User::class.java)
            currentUser?.let {
                try {
                    if (it.profileImageBase64.isNotEmpty()) {
                        val decodedBytes = Base64.decode(it.profileImageBase64, Base64.DEFAULT)
                        val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                        val circularBitmap = getCircularBitmap(bitmap)
                        profilePicImageView.setImageBitmap(circularBitmap)
                    }
                } catch (e: Exception) {
                    Log.e("Home", "Failed to load profile picture: ${e.message}")
                    profilePicImageView.setImageResource(R.drawable.default_profile)
                }
            }
        }.addOnFailureListener { error ->
            Log.e("Home", "Failed to fetch current user: ${error.message}")
            profilePicImageView.setImageResource(R.drawable.default_profile)
        }
    }

    private fun getCircularBitmap(bitmap: Bitmap): Bitmap {
        val size = Math.max(bitmap.width, bitmap.height)
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