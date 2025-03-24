package com.salmansaleem.i220904

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.io.ByteArrayOutputStream

class NewPost : AppCompatActivity() {

    private lateinit var photoImageView: ImageView
    private lateinit var closeButton: ImageView
    private lateinit var nextButton: TextView
    private lateinit var albumSelector: TextView
    private lateinit var gridLayout1: GridLayout
    private lateinit var gridLayout2: GridLayout
    private lateinit var gridLayout3: GridLayout
    private lateinit var gridLayout4: GridLayout
    private var selectedBitmap: Bitmap? = null
    private var selectedAlbumId: String? = null // Null means "Recents"

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val imageUri = result.data?.data
            imageUri?.let {
                try {
                    val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, it)
                    val squareBitmap = cropToSquare(bitmap)
                    photoImageView.setImageBitmap(squareBitmap)
                    selectedBitmap = squareBitmap
                } catch (e: Exception) {
                    Log.e("NewPost", "Error picking image: ${e.message}", e)
                    Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private val requestStoragePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            loadGalleryPhotos()
        } else {
            Toast.makeText(this, "Storage permission denied. Gallery photos cannot be loaded.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_new_post)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        photoImageView = findViewById(R.id.pic1)
        closeButton = findViewById(R.id.close)
        nextButton = findViewById(R.id.next)
        albumSelector = findViewById(R.id.album_selector)
        gridLayout1 = findViewById(R.id.grid1)
        gridLayout2 = findViewById(R.id.grid2)
        gridLayout3 = findViewById(R.id.grid3)
        gridLayout4 = findViewById(R.id.grid4)

        setupListeners()
        setupAlbumSelector()
        checkStoragePermission() // Check permission instead of direct load
    }

    private fun checkStoragePermission() {
        val permission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            loadGalleryPhotos()
        } else {
            requestStoragePermission.launch(permission)
        }
    }

    private fun setupAlbumSelector() {
        albumSelector.setOnClickListener {
            showAlbumPopup()
        }
    }

    private fun showAlbumPopup() {
        val albums = getAlbums()
        val popup = PopupMenu(this, albumSelector)
        popup.menu.add("Recents")
        albums.forEach { (name, id) ->
            popup.menu.add(name)
        }

        popup.setOnMenuItemClickListener { item ->
            val albumName = item.title.toString()
            albumSelector.text = albumName
            selectedAlbumId = if (albumName == "Recents") null else albums[albumName]
            loadGalleryPhotos()
            true
        }
        popup.show()
    }

    private fun getAlbums(): Map<String, String> {
        val albums = mutableMapOf<String, String>()
        val projection = arrayOf(
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.BUCKET_ID
        )
        val cursor = contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            null
        )

        cursor?.use {
            val nameColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            val idColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
            while (it.moveToNext()) {
                val name = it.getString(nameColumn)
                val id = it.getString(idColumn)
                albums[name] = id
            }
        } ?: Log.w("NewPost", "No albums found")
        return albums
    }

    private fun loadGalleryPhotos() {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.BUCKET_ID
        )
        val selection = if (selectedAlbumId != null) "${MediaStore.Images.Media.BUCKET_ID} = ?" else null
        val selectionArgs = if (selectedAlbumId != null) arrayOf(selectedAlbumId) else null
        val cursor = contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )

        cursor?.use {
            val idColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val dataColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
            val gridLayouts = listOf(gridLayout1, gridLayout2, gridLayout3, gridLayout4)
            var gridIndex = 0
            var imageCount = 0

            if (it.moveToFirst()) {
                val firstUri = Uri.withAppendedPath(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    it.getString(idColumn)
                )
                try {
                    val firstBitmap = MediaStore.Images.Media.getBitmap(contentResolver, firstUri)
                    val squareFirstBitmap = cropToSquare(firstBitmap)
                    photoImageView.setImageBitmap(squareFirstBitmap)
                    selectedBitmap = squareFirstBitmap
                } catch (e: Exception) {
                    Log.e("NewPost", "Error loading first photo: ${e.message}", e)
                }
            }

            while (it.moveToNext() && gridIndex < gridLayouts.size) {
                val imageId = it.getString(idColumn)
                val imageUri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, imageId)
                val bitmap = BitmapFactory.decodeFile(it.getString(dataColumn)) ?: continue
                val squareBitmap = cropToSquare(bitmap)

                try {
                    val imageView = gridLayouts[gridIndex].getChildAt(imageCount % 4) as ImageView
                    imageView.setImageBitmap(squareBitmap)
                    imageView.setOnClickListener {
                        photoImageView.setImageBitmap(squareBitmap)
                        selectedBitmap = squareBitmap
                    }
                } catch (e: Exception) {
                    Log.e("NewPost", "Error setting grid image: ${e.message}", e)
                }

                imageCount++
                if (imageCount % 4 == 0) gridIndex++
            }
        } ?: Log.w("NewPost", "No photos found in gallery")
    }

    private fun setupListeners() {
        closeButton.setOnClickListener {
            finish()
        }

        nextButton.setOnClickListener {
            selectedBitmap?.let { bitmap ->
                val intent = Intent(this, NewPostShare::class.java)
                val baos = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos)
                val byteArray = baos.toByteArray()
                intent.putExtra("selectedPhoto", byteArray)
                startActivity(intent)
            } ?: Toast.makeText(this, "No photo selected", Toast.LENGTH_SHORT).show()
        }

        findViewById<ImageView>(R.id.camera)?.setOnClickListener {
            openGallery()
        } ?: Log.w("NewPost", "Camera button not found")
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        pickImageLauncher.launch(intent)
    }

    private fun cropToSquare(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val size = minOf(width, height)
        val x = (width - size) / 2
        val y = (height - size) / 2
        return Bitmap.createBitmap(bitmap, x, y, size, size)
    }
}