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
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class MyProfile : AppCompatActivity() {

    private lateinit var profileImageView: ImageView
    private lateinit var usernameTextView: TextView
    private lateinit var bioTextView: TextView
    private lateinit var postCountTextView: TextView
    private lateinit var followersTextView: TextView
    private lateinit var followingTextView: TextView
    private lateinit var postsRecyclerView: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_my_profile)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Initialize views
        profileImageView = findViewById(R.id.imageView)
        usernameTextView = findViewById(R.id.olivia)
        bioTextView = findViewById(R.id.textbio)
        postCountTextView = findViewById(R.id.countnumberofpost)
        followersTextView = findViewById(R.id.followers)
        followingTextView = findViewById(R.id.following)
        postsRecyclerView = findViewById(R.id.postsRecyclerView)

        // Set up RecyclerView
        postsRecyclerView.layoutManager = GridLayoutManager(this, 3) // 3 columns
        postsRecyclerView.setHasFixedSize(true)

        // Load user data and posts
        loadUserData()
        loadUserPosts()


        var btn1 = findViewById<ImageView>(R.id.edit)
        btn1.setOnClickListener {
            val intent = Intent(this, EditProfile::class.java)
            startActivity(intent)
        }

    }

    private fun loadUserData() {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        if (currentUserId == null) {
            Log.e("MyProfile", "User not authenticated")
            return
        }

        val databaseRef = FirebaseDatabase.getInstance().reference.child("Users").child(currentUserId)
        databaseRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val user = snapshot.getValue(User::class.java)
                if (user != null) {
                    // Profile photo with circular crop and default fallback
                    if (user.profileImageBase64.isNotEmpty()) {
                        try {
                            val decodedBytes = Base64.decode(user.profileImageBase64, Base64.DEFAULT)
                            val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                            val circularBitmap = cropToCircle(bitmap)
                            profileImageView.setImageBitmap(circularBitmap)
                        } catch (e: Exception) {
                            Log.e("MyProfile", "Error decoding profile image: ${e.message}")
                            profileImageView.setImageResource(R.drawable.default_profile)
                        }
                    } else {
                        profileImageView.setImageResource(R.drawable.default_profile)
                    }

                    // Username
                    usernameTextView.text = user.username

                    // Bio
                    bioTextView.text = user.bio.ifEmpty { "No bio available" }

                    // Post count
                    postCountTextView.text = user.posts.size.toString()

                    // Followers and Following
                    followersTextView.text = user.followers.size.toString()
                    followingTextView.text = user.following.size.toString()
                } else {
                    Log.e("MyProfile", "User data not found")
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("MyProfile", "Database error: ${error.message}")
            }
        })
    }

    private fun loadUserPosts() {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        if (currentUserId == null) {
            Log.e("MyProfile", "User not authenticated")
            return
        }

        val databaseRef = FirebaseDatabase.getInstance().reference.child("Users").child(currentUserId)
        databaseRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val user = snapshot.getValue(User::class.java)
                if (user != null) {
                    Log.d("MyProfile", "User posts count: ${user.posts.size}")
                    if (user.posts.isNotEmpty()) {
                        val adapter = PostOnProfileAdapter(user.posts)
                        postsRecyclerView.adapter = adapter
                        postsRecyclerView.visibility = RecyclerView.VISIBLE
                        adapter.notifyDataSetChanged()
                    } else {
                        Log.d("MyProfile", "No posts found for user")
                        postsRecyclerView.visibility = RecyclerView.GONE
                    }
                } else {
                    Log.e("MyProfile", "User data not found")
                    postsRecyclerView.visibility = RecyclerView.GONE
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("MyProfile", "Database error: ${error.message}")
                postsRecyclerView.visibility = RecyclerView.GONE
            }
        })
    }

    // Function to crop bitmap into a circle
    private fun cropToCircle(bitmap: Bitmap): Bitmap {
        val size = minOf(bitmap.width, bitmap.height)
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint()
        val halfWidth = size / 2f
        val halfHeight = size / 2f

        paint.isAntiAlias = true
        canvas.drawCircle(halfWidth, halfHeight, halfWidth, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        val x = (size - bitmap.width) / 2
        val y = (size - bitmap.height) / 2
        canvas.drawBitmap(bitmap, x.toFloat(), y.toFloat(), paint)

        return output
    }
}