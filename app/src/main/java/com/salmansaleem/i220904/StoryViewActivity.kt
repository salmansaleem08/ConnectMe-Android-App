package com.salmansaleem.i220904

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class StoryViewActivity : AppCompatActivity() {

    private lateinit var storyImageView: ImageView
    private lateinit var closeButton: ImageView
    private lateinit var progressBar: ProgressBar
    private val handler = Handler(Looper.getMainLooper())
    private var storiesList: List<Story> = emptyList()
    private var currentStoryIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_story_view)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Initialize views
        storyImageView = findViewById(R.id.story_image)
        closeButton = findViewById(R.id.close_button)
        progressBar = findViewById(R.id.progress_bar)

        // Get user ID from intent
        val userId = intent.getStringExtra("USER_ID") ?: run {
            Toast.makeText(this, "No user selected", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Close button listener
        closeButton.setOnClickListener {
            handler.removeCallbacksAndMessages(null)
            finish()
        }

        // Fetch and display stories
        fetchUserStories(userId)
    }

    private fun fetchUserStories(userId: String) {
        val databaseRef = FirebaseDatabase.getInstance().reference.child("Users").child(userId)
        databaseRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val user = snapshot.getValue(User::class.java)
                storiesList = user?.stories ?: emptyList()
                if (storiesList.isEmpty()) {
                    Toast.makeText(this@StoryViewActivity, "No stories available", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    startStoryPlayback()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("StoryViewActivity", "Failed to fetch stories: ${error.message}")
                Toast.makeText(this@StoryViewActivity, "Error loading stories", Toast.LENGTH_SHORT).show()
                finish()
            }
        })
    }

    private fun startStoryPlayback() {
        currentStoryIndex = 0
        progressBar.max = 2000 // 2 seconds in milliseconds
        showNextStory()
    }

    private fun showNextStory() {
        if (currentStoryIndex >= storiesList.size) {
            // All stories viewed, return to Home
            handler.removeCallbacksAndMessages(null)
            val intent = Intent(this, Home::class.java)
            startActivity(intent)
            finish()
            return
        }

        val story = storiesList[currentStoryIndex]
        try {
            val decodedBytes = Base64.decode(story.profileImageBase64, Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
            storyImageView.setImageBitmap(bitmap)
        } catch (e: Exception) {
            Log.e("StoryViewActivity", "Error decoding story image: ${e.message}")
            storyImageView.setImageResource(R.drawable.default_profile) // Fallback image
        }

        // Reset and start progress
        progressBar.progress = 0
        val startTime = System.currentTimeMillis()
        handler.post(object : Runnable {
            override fun run() {
                val elapsed = System.currentTimeMillis() - startTime
                progressBar.progress = elapsed.toInt()
                if (elapsed >= 2000) {
                    currentStoryIndex++
                    showNextStory()
                } else {
                    handler.postDelayed(this, 16) // Update every ~16ms for smooth progress
                }
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null) // Clean up to avoid memory leaks
    }
}