package com.salmansaleem.i220904

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.io.ByteArrayOutputStream

class EditProfile : AppCompatActivity() {

    // UI elements
    private lateinit var imageView: ImageView
    private lateinit var nameEditText: EditText
    private lateinit var usernameEditText: EditText
    private lateinit var phoneEditText: EditText
    private lateinit var bioEditText: EditText
    private lateinit var doneButton: TextView
    private lateinit var name :TextView

    // Firebase references
    private lateinit var mAuth: FirebaseAuth
    private lateinit var mDatabase: FirebaseDatabase

    // Current user data
    private var imageUri: Uri? = null
    private var selectedImageBitmap: Bitmap? = null
    private val PICK_IMAGE_REQUEST = 1
    private val STORAGE_PERMISSION_CODE = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_profile)

        // Initialize Firebase instances
        mAuth = FirebaseAuth.getInstance()
        mDatabase = FirebaseDatabase.getInstance()

        // Check if user is logged in
        if (mAuth.currentUser == null) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, Login::class.java)) // Replace with your Login activity
            finish()
            return
        }

        // Initialize views
        imageView = findViewById(R.id.imageView)
        nameEditText = findViewById(R.id.PersonName)
        usernameEditText = findViewById(R.id.PersonUsername)
        phoneEditText = findViewById(R.id.PersonContactNumber)
        bioEditText = findViewById(R.id.personBio)
        doneButton = findViewById(R.id.done)
        name = findViewById(R.id.olivia)


        // Set scale type for zooming
        imageView.scaleType = ImageView.ScaleType.CENTER_CROP

        // Load current user data
        loadUserData()

        // Image selection with permission check
        imageView.setOnClickListener {
            if (checkStoragePermission()) {
                openImagePicker()
            } else {
                requestStoragePermission()
            }
        }

        // Done button click listener
        doneButton.setOnClickListener {
            saveProfileChanges()
        }
    }

    private fun loadUserData() {
        val currentUser = mAuth.currentUser ?: return
        val userRef = mDatabase.getReference("Users").child(currentUser.uid)
        userRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val user = snapshot.getValue(User::class.java)
                if (user != null) {
                    nameEditText.hint = user.name
                    usernameEditText.hint = user.username
                    name.text = user.username
                    phoneEditText.hint = user.phone
                    bioEditText.hint = user.bio
                    if (user.profileImageBase64.isNotEmpty()) {
                        try {
                            val bitmap = base64ToBitmap(user.profileImageBase64)
                            if (bitmap != null) {
                                val circularBitmap = getCircularBitmap(bitmap)
                                imageView.setImageBitmap(circularBitmap)
                            } else {
                                Log.e("EditProfile", "Failed to decode Base64 image")
                            }
                        } catch (e: Exception) {
                            Log.e("EditProfile", "Error decoding image: ${e.message}", e)
                        }
                    }
                } else {
                    Log.e("EditProfile", "User data is null")
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@EditProfile, "Failed to load profile: ${error.message}", Toast.LENGTH_SHORT).show()
                Log.e("EditProfile", "Database error: ${error.message}")
            }
        })
    }

    private fun saveProfileChanges() {
        val currentUser = mAuth.currentUser ?: return
        val userRef = mDatabase.getReference("Users").child(currentUser.uid)

        val updatedName = nameEditText.text.toString().ifEmpty { nameEditText.hint.toString() }
        val updatedUsername = usernameEditText.text.toString().ifEmpty { usernameEditText.hint.toString() }
        val updatedPhone = phoneEditText.text.toString().ifEmpty { phoneEditText.hint.toString() }
        val updatedBio = bioEditText.text.toString().ifEmpty { bioEditText.hint.toString() }
        val updatedImageBase64 = selectedImageBitmap?.let { bitmapToBase64(getCircularBitmap(it)) } ?: ""

        updateUserData(userRef, updatedName, updatedUsername, updatedPhone, updatedImageBase64, updatedBio)
    }

    private fun updateUserData(
        userRef: com.google.firebase.database.DatabaseReference,
        name: String,
        username: String,
        phone: String,
        profileImageBase64: String,
        bio: String
    ) {
        val updates = mutableMapOf<String, Any>(
            "name" to name,
            "username" to username,
            "phone" to phone,
            "bio" to bio,
            "profileImageBase64" to profileImageBase64
        )

        userRef.updateChildren(updates)
            .addOnSuccessListener {
                Toast.makeText(this, "Profile updated successfully", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to update profile: ${e.message}", Toast.LENGTH_LONG).show()
                Log.e("EditProfile", "Database update error: ${e.message}", e)
            }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK && data != null) {
            imageUri = data.data
            if (imageUri != null) {
                try {
                    selectedImageBitmap = uriToBitmap(this, imageUri!!)
                    if (selectedImageBitmap != null) {
                        val circularBitmap = getCircularBitmap(selectedImageBitmap!!)
                        imageView.setImageBitmap(circularBitmap)
                        Toast.makeText(this, "Image selected", Toast.LENGTH_SHORT).show()
                        Log.d("EditProfile", "Image selected, size: ${selectedImageBitmap?.byteCount} bytes")
                    } else {
                        Toast.makeText(this, "Failed to decode image", Toast.LENGTH_SHORT).show()
                        Log.e("EditProfile", "Bitmap is null after decoding")
                    }
                } catch (e: Exception) {
                    Toast.makeText(this, "Error processing image: ${e.message}", Toast.LENGTH_LONG).show()
                    Log.e("EditProfile", "Image processing error: ${e.message}", e)
                }
            } else {
                Toast.makeText(this, "Failed to select image", Toast.LENGTH_SHORT).show()
                Log.e("EditProfile", "Image URI is null")
            }
        }
    }

    // Check storage permission
    private fun checkStoragePermission(): Boolean {
        val permission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    // Request storage permission
    private fun requestStoragePermission() {
        val permission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {
            Toast.makeText(this, "Storage permission is needed to select images", Toast.LENGTH_LONG).show()
        }
        ActivityCompat.requestPermissions(this, arrayOf(permission), STORAGE_PERMISSION_CODE)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == STORAGE_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Storage permission granted", Toast.LENGTH_SHORT).show()
                openImagePicker()
            } else {
                Toast.makeText(this, "Storage permission denied. Cannot select image.", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Helper function to open image picker
    private fun openImagePicker() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, PICK_IMAGE_REQUEST)
    }

    // Convert Bitmap to Base64
    private fun bitmapToBase64(bitmap: Bitmap): String {
        val byteArrayOutputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 50, byteArrayOutputStream)
        val byteArray = byteArrayOutputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.DEFAULT)
    }

    // Convert Base64 to Bitmap
    private fun base64ToBitmap(base64String: String): Bitmap? {
        return try {
            val decodedBytes = Base64.decode(base64String, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
        } catch (e: Exception) {
            Log.e("EditProfile", "Error decoding Base64: ${e.message}", e)
            null
        }
    }

    // Convert URI to Bitmap
    private fun uriToBitmap(context: Context, uri: Uri): Bitmap? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            BitmapFactory.decodeStream(inputStream).also { inputStream?.close() }
        } catch (e: Exception) {
            Log.e("EditProfile", "Error converting URI to Bitmap: ${e.message}", e)
            null
        }
    }

    private fun getCircularBitmap(bitmap: Bitmap): Bitmap {
        // Convert 200dp to pixels based on device density
        val sizeInDp = 200
        val scale = resources.displayMetrics.density
        val sizeInPixels = (sizeInDp * scale).toInt() // e.g., 600px on a 3x density device

        // Scale bitmap to match ImageView size
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, sizeInPixels, sizeInPixels, true)
        val output = Bitmap.createBitmap(sizeInPixels, sizeInPixels, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint()
        val rect = android.graphics.Rect(0, 0, sizeInPixels, sizeInPixels)

        paint.isAntiAlias = true
        canvas.drawARGB(0, 0, 0, 0)
        paint.color = android.graphics.Color.WHITE

        val radius = sizeInPixels / 2f
        canvas.drawCircle(radius, radius, radius, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(scaledBitmap, rect, rect, paint)

        return output
    }
}