package com.salmansaleem.i220904

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.io.ByteArrayOutputStream

class NewPostShare : AppCompatActivity() {

    private lateinit var originalImageView: ImageView
    private lateinit var filter1ImageView: ImageView
    private lateinit var filter2ImageView: ImageView
    private lateinit var closeButton: ImageView
    private lateinit var captionEditText: EditText
    private lateinit var shareButton: TextView


    private var selectedBitmap: Bitmap? = null // Now tracks the currently selected Bitmap
    private lateinit var originalBitmap: Bitmap
    private lateinit var sepiaBitmap: Bitmap
    private lateinit var grayBitmap: Bitmap

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_new_post_share)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        originalImageView = findViewById(R.id.pic6)
        filter1ImageView = findViewById(R.id.pic8)
        filter2ImageView = findViewById(R.id.pic9)
        closeButton = findViewById(R.id.close)
        captionEditText = findViewById(R.id.caption_edit_text)
        shareButton = findViewById(R.id.share_button)

        // Get the selected photo from intent
        val byteArray = intent.getByteArrayExtra("selectedPhoto")
        selectedBitmap = byteArray?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }

        setupImageViews()
        setupListeners()
    }

    private fun setupImageViews() {
        selectedBitmap?.let { bitmap ->
            // Store original bitmap
            originalBitmap = bitmap
            originalImageView.setImageBitmap(originalBitmap)

            // Filter 1: Sepia
            val sepiaMatrix = ColorMatrix().apply {
                setSaturation(0f)
                val r = floatArrayOf(0.393f, 0.769f, 0.189f, 0f, 0f)
                val g = floatArrayOf(0.349f, 0.686f, 0.168f, 0f, 0f)
                val b = floatArrayOf(0.272f, 0.534f, 0.131f, 0f, 0f)
                set(floatArrayOf(
                    r[0], g[0], b[0], 0f, 0f,
                    r[1], g[1], b[1], 0f, 0f,
                    r[2], g[2], b[2], 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                ))
            }
            sepiaBitmap = Bitmap.createBitmap(bitmap)
            filter1ImageView.setImageBitmap(sepiaBitmap)
            filter1ImageView.colorFilter = ColorMatrixColorFilter(sepiaMatrix)

            // Filter 2: Grayscale
            val grayMatrix = ColorMatrix().apply { setSaturation(0f) }
            grayBitmap = Bitmap.createBitmap(bitmap)
            filter2ImageView.setImageBitmap(grayBitmap)
            filter2ImageView.colorFilter = ColorMatrixColorFilter(grayMatrix)

            // Set initial selection to original
            selectedBitmap = originalBitmap

            // Add click listeners
            originalImageView.setOnClickListener { reorderImages(R.id.pic6) }
            filter1ImageView.setOnClickListener { reorderImages(R.id.pic8) }
            filter2ImageView.setOnClickListener { reorderImages(R.id.pic9) }
        }
    }


    private fun reorderImages(selectedViewId: Int) {
        when (selectedViewId) {
            R.id.pic6 -> {
                selectedBitmap = originalBitmap
                originalImageView.setImageBitmap(originalBitmap)
                filter1ImageView.setImageBitmap(sepiaBitmap)
                filter1ImageView.colorFilter = ColorMatrixColorFilter(ColorMatrix().apply {
                    setSaturation(0f)
                    val r = floatArrayOf(0.393f, 0.769f, 0.189f, 0f, 0f)
                    val g = floatArrayOf(0.349f, 0.686f, 0.168f, 0f, 0f)
                    val b = floatArrayOf(0.272f, 0.534f, 0.131f, 0f, 0f)
                    set(floatArrayOf(r[0], g[0], b[0], 0f, 0f, r[1], g[1], b[1], 0f, 0f, r[2], g[2], b[2], 0f, 0f, 0f, 0f, 0f, 1f, 0f))
                })
                filter2ImageView.setImageBitmap(grayBitmap)
                filter2ImageView.colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
            }
            R.id.pic8 -> {
                selectedBitmap = sepiaBitmap
                originalImageView.setImageBitmap(sepiaBitmap)
                originalImageView.colorFilter = ColorMatrixColorFilter(ColorMatrix().apply {
                    setSaturation(0f)
                    val r = floatArrayOf(0.393f, 0.769f, 0.189f, 0f, 0f)
                    val g = floatArrayOf(0.349f, 0.686f, 0.168f, 0f, 0f)
                    val b = floatArrayOf(0.272f, 0.534f, 0.131f, 0f, 0f)
                    set(floatArrayOf(r[0], g[0], b[0], 0f, 0f, r[1], g[1], b[1], 0f, 0f, r[2], g[2], b[2], 0f, 0f, 0f, 0f, 0f, 1f, 0f))
                })
                filter1ImageView.setImageBitmap(originalBitmap)
                filter1ImageView.colorFilter = null // Clear filter for original
                filter2ImageView.setImageBitmap(grayBitmap)
                filter2ImageView.colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
            }
            R.id.pic9 -> {
                selectedBitmap = grayBitmap
                originalImageView.setImageBitmap(grayBitmap)
                originalImageView.colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
                filter1ImageView.setImageBitmap(sepiaBitmap)
                filter1ImageView.colorFilter = ColorMatrixColorFilter(ColorMatrix().apply {
                    setSaturation(0f)
                    val r = floatArrayOf(0.393f, 0.769f, 0.189f, 0f, 0f)
                    val g = floatArrayOf(0.349f, 0.686f, 0.168f, 0f, 0f)
                    val b = floatArrayOf(0.272f, 0.534f, 0.131f, 0f, 0f)
                    set(floatArrayOf(r[0], g[0], b[0], 0f, 0f, r[1], g[1], b[1], 0f, 0f, r[2], g[2], b[2], 0f, 0f, 0f, 0f, 0f, 1f, 0f))
                })
                filter2ImageView.setImageBitmap(originalBitmap)
                filter2ImageView.colorFilter = null // Clear filter for original
            }
        }
    }
    private fun setupListeners() {
        closeButton.setOnClickListener {
            finish()
        }

        shareButton.setOnClickListener {
            selectedBitmap?.let { bitmap ->
                val caption = captionEditText.text.toString()
                uploadPost(bitmap, caption)




            } ?: Log.e("NewPost", "No bitmap selected to upload")
        }
    }

    private fun uploadPost(bitmap: Bitmap, caption: String) {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        if (currentUserId == null) {
            Log.e("NewPost", "User not authenticated")
            return
        }

        val databaseRef = FirebaseDatabase.getInstance().reference.child("Users").child(currentUserId)

        // Convert selected Bitmap to Base64
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos)
        val byteArray = baos.toByteArray()
        val base64Image = Base64.encodeToString(byteArray, Base64.DEFAULT)

        val post = Post(
            photoBase64 = base64Image,
            caption = caption
        )

        databaseRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val user = snapshot.getValue(User::class.java)
                if (user != null) {
                    val updatedPosts = user.posts.toMutableList()
                    updatedPosts.add(post)
                    databaseRef.child("posts").setValue(updatedPosts)
                        .addOnSuccessListener {
                            Log.d("NewPost", "Post added to database successfully: $post")
                            val intent = Intent(this@NewPostShare, Home::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                            startActivity(intent)
                            finish()
                        }
                        .addOnFailureListener { e ->
                            Log.e("NewPost", "Failed to save post to database: ${e.message}")
                        }
                } else {
                    Log.e("NewPost", "User data not found in database")
                    val newUser = User(uid = currentUserId, posts = mutableListOf(post))
                    databaseRef.setValue(newUser)
                        .addOnSuccessListener {
                            Log.d("NewPost", "New user created with post: $post")
                            val intent = Intent(this@NewPostShare, Home::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                            startActivity(intent)
                            finish()
                        }
                        .addOnFailureListener { e ->
                            Log.e("NewPost", "Failed to create user with post: ${e.message}")
                        }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("NewPost", "Database error: ${error.message}")
            }
        })
    }
}