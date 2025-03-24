package com.salmansaleem.i220904

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.io.ByteArrayOutputStream

class StoryActivity : AppCompatActivity() {

    private lateinit var closeButton: ImageView
    private lateinit var nextButton: TextView
    private lateinit var galleryImage: ImageView
    private lateinit var storyCircle: RelativeLayout
    private lateinit var cameraSwitch: ImageView
    private lateinit var cameraPreview: FrameLayout
    private lateinit var fullScreenImage: ImageView
    private var selectedImageBase64: String? = null
    private var photoUri: Uri? = null

    private val requestCameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            capturePhotoWithIntent()
        } else {
            Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    private val requestStoragePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            loadMostRecentGalleryImage()
        } else {
            Log.e("StoryActivity", "Storage permission denied")
            Toast.makeText(this, "Storage permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val data: Intent? = result.data
            val imageUri: Uri? = data?.data
            imageUri?.let {
                try {
                    val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, it)
                    selectedImageBase64 = bitmapToBase64(bitmap)
                    galleryImage.setImageBitmap(bitmap)
                    fullScreenImage.setImageBitmap(bitmap)
                    fullScreenImage.visibility = View.VISIBLE
                    cameraPreview.visibility = View.GONE
                } catch (e: Exception) {
                    Log.e("StoryActivity", "Error selecting gallery image: ${e.message}", e)
                }
            }
        }
    }

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            try {
                val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, photoUri)
                selectedImageBase64 = bitmapToBase64(bitmap)
                galleryImage.setImageBitmap(bitmap)
                fullScreenImage.setImageBitmap(bitmap)
                fullScreenImage.visibility = View.VISIBLE
                cameraPreview.visibility = View.GONE
            } catch (e: Exception) {
                Log.e("StoryActivity", "Error capturing photo: ${e.message}", e)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        try {
            setContentView(R.layout.activity_story)
        } catch (e: Exception) {
            Log.e("StoryActivity", "Error inflating layout: ${e.message}", e)
            Toast.makeText(this, "Error loading UI: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Initialize views
        try {
            closeButton = findViewById(R.id.close)
            nextButton = findViewById(R.id.next)
            galleryImage = findViewById(R.id.gallery)
            storyCircle = findViewById(R.id.storycircle)
            cameraSwitch = findViewById(R.id.cameraSwitch)
            cameraPreview = findViewById(R.id.camera_preview)
            fullScreenImage = findViewById(R.id.full_screen_image)
        } catch (e: Exception) {
            Log.e("StoryActivity", "View initialization failed: ${e.message}", e)
            Toast.makeText(this, "Error initializing views", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Set click listeners
        closeButton.setOnClickListener { resetUI(); finish() }
        nextButton.setOnClickListener { uploadStory() }
        galleryImage.setOnClickListener { openGallery() }
        storyCircle.setOnClickListener { checkCameraPermission() }
        cameraSwitch.setOnClickListener { togglePreview() }

        // Post button
        val postButton = findViewById<TextView>(R.id.post)
        postButton?.setOnClickListener {
            try {
                val intent = Intent(this, NewPost::class.java)
                startActivity(intent)
                finish()
            } catch (e: Exception) {
                Log.e("StoryActivity", "Error starting NewPost: ${e.message}", e)
                Toast.makeText(this, "Failed to open New Post", Toast.LENGTH_SHORT).show()
            }
        } ?: Log.w("StoryActivity", "Post button not found")

        // Load gallery image only after UI is stable
        checkStoragePermission()
    }

    private fun checkStoragePermission() {
        val permission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            loadMostRecentGalleryImage()
        } else {
            requestStoragePermission.launch(permission)
        }
    }

    private fun loadMostRecentGalleryImage() {
        val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DATE_ADDED)
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
        val cursor = contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            sortOrder
        )

        cursor?.use {
            if (it.moveToFirst()) {
                val idColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val id = it.getLong(idColumn)
                val contentUri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())
                try {
                    val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, contentUri)
                    galleryImage.setImageBitmap(bitmap)
                } catch (e: Exception) {
                    Log.e("StoryActivity", "Error loading gallery image: ${e.message}", e)
                }
            } else {
                Log.w("StoryActivity", "No images found in gallery")
            }
        } ?: Log.w("StoryActivity", "Gallery cursor is null")
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        galleryLauncher.launch(intent)
    }

    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            capturePhotoWithIntent()
        } else {
            requestCameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    private fun capturePhotoWithIntent() {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "story_${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        }
        photoUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
        }
        cameraLauncher.launch(intent)
    }

    private fun togglePreview() {
        if (cameraPreview.visibility == View.VISIBLE) {
            cameraPreview.visibility = View.GONE
            fullScreenImage.visibility = View.VISIBLE
            cameraSwitch.setImageResource(R.drawable.camera)
        } else {
            cameraPreview.visibility = View.VISIBLE
            fullScreenImage.visibility = View.GONE
            cameraSwitch.setImageResource(R.drawable.switchcamera)
        }
    }

    private fun uploadStory() {
        if (selectedImageBase64 == null) {
            Toast.makeText(this, "Please select or capture an image first", Toast.LENGTH_SHORT).show()
            return
        }
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val databaseRef = FirebaseDatabase.getInstance().reference.child("Users").child(currentUserId)
        databaseRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val user = snapshot.getValue(User::class.java)
                if (user != null) {
                    val newStory = Story(
                        profileImageBase64 = selectedImageBase64!!,
                        timestamp = System.currentTimeMillis()
                    )
                    val updatedStories = user.stories.toMutableList().apply { add(newStory) }
                    databaseRef.child("stories").setValue(updatedStories)
                        .addOnSuccessListener {
                            Toast.makeText(this@StoryActivity, "Story uploaded!", Toast.LENGTH_SHORT).show()
                            resetUI()
                            finish()
                        }
                        .addOnFailureListener { error ->
                            Toast.makeText(this@StoryActivity, "Upload failed: ${error.message}", Toast.LENGTH_SHORT).show()
                        }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("StoryActivity", "Database error: ${error.message}", error.toException())
            }
        })
    }

    private fun resetUI() {
        fullScreenImage.visibility = View.GONE
        cameraPreview.visibility = View.VISIBLE
        selectedImageBase64 = null
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val byteArrayOutputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream)
        val byteArray = byteArrayOutputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.DEFAULT)
    }
}