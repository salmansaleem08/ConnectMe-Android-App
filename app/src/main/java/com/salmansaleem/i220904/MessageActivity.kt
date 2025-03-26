package com.salmansaleem.i220904

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.view.View
import android.view.animation.AlphaAnimation
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.io.ByteArrayOutputStream

class MessageActivity : AppCompatActivity() {
    private lateinit var messagesRecyclerView: RecyclerView
    private lateinit var messageAdapter: MessageAdapter
    private lateinit var messageEditText: EditText
    private lateinit var sendButton: ImageView
    private lateinit var photoButton: ImageView
    private lateinit var vanishModeButton: ImageView
    private lateinit var backButton: ImageView
    private lateinit var otherUsernameTextView: TextView
    private lateinit var onlineStatusTextView: TextView
    private lateinit var callButton: ImageView
    private lateinit var videoCallButton: ImageView
    private lateinit var otherProfilePic: ImageView
    private lateinit var viewProfileButton: Button

    private lateinit var database: DatabaseReference
    private var currentUserId: String? = null
    private var otherUserId: String? = null
    private var vanishMode = false
    private var vanishModeStartTimestamp: Long? = null

    private val PICK_IMAGE_REQUEST = 1
    private val STORAGE_PERMISSION_CODE = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_message)

        initializeViews()

        currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        otherUserId = intent.getStringExtra("OTHER_USER_ID")

        if (currentUserId == null || otherUserId == null) {
            Log.e("MessageActivity", "User authentication or other user ID missing")
            Toast.makeText(this, "Authentication error", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        database = FirebaseDatabase.getInstance().reference.child("Messages")
        val chatId = getChatId(currentUserId!!, otherUserId!!)

        messageAdapter = MessageAdapter(mutableListOf(), currentUserId!!) { message, position ->
            handleMessageClick(message, position)
        }
        messagesRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MessageActivity).apply {
                stackFromEnd = true
            }
            adapter = messageAdapter
        }

        setupPresence()
        loadOtherUserProfile()
        loadMessages()
        observeOnlineStatus()
        setupClickListeners()
        observeVanishMode(chatId) // Add this to sync vanish mode
    }

    private fun initializeViews() {
        messagesRecyclerView = findViewById(R.id.messagesRecyclerView)
        messageEditText = findViewById(R.id.messageEditText)
        sendButton = findViewById(R.id.sendButton)
        photoButton = findViewById(R.id.photoButton)
        vanishModeButton = findViewById(R.id.vanishModeButton)
        backButton = findViewById(R.id.back)
        otherUsernameTextView = findViewById(R.id.otherUsername)
        onlineStatusTextView = findViewById(R.id.onlineStatus)
        callButton = findViewById(R.id.call)
        videoCallButton = findViewById(R.id.videocall)
        otherProfilePic = findViewById(R.id.otherProfilePic)
        viewProfileButton = findViewById(R.id.viewProfileButton)
    }

    private fun setupPresence() {
        val presenceRef = FirebaseDatabase.getInstance().reference.child(".info/connected")
        val userRef = FirebaseDatabase.getInstance().reference.child("Users").child(currentUserId!!)

        presenceRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val connected = snapshot.getValue(Boolean::class.java) ?: false
                if (connected) {
                    userRef.child("online").setValue(true)
                    userRef.child("online").onDisconnect().setValue(false)
                    userRef.child("lastSeen").onDisconnect().setValue(System.currentTimeMillis())
                }
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e("Presence", "Presence error: ${error.message}")
            }
        })
    }

    private fun setupClickListeners() {
        sendButton.setOnClickListener {
            val text = messageEditText.text.toString().trim()
            if (text.isNotEmpty()) {
                sendMessage(text = text)
                messageEditText.text.clear()
            }
        }

        photoButton.setOnClickListener { openGallery() }
        vanishModeButton.setOnClickListener { toggleVanishMode() }
        backButton.setOnClickListener { finish() }
    }

    private fun observeVanishMode(chatId: String) {
        val vanishModeRef = database.child(chatId).child("vanishMode")
        vanishModeRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val enabled = snapshot.child("enabled").getValue(Boolean::class.java) ?: false
                val timestamp = snapshot.child("timestamp").getValue(Long::class.java)

                if (enabled != vanishMode) {
                    vanishMode = enabled
                    vanishModeStartTimestamp = timestamp
                    if (vanishMode) {
                        enableVanishModeUI()
                    } else {
                        disableVanishModeUI()
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("MessageActivity", "Failed to observe vanish mode: ${error.message}")
            }
        })
    }

    private fun toggleVanishMode() {
        if (!vanishMode) {
            enableVanishMode()
            vanishModeButton.setImageResource(R.drawable.iconn)
        } else {
            disableVanishMode()
            vanishModeButton.setImageResource(R.drawable.iconn)
        }
    }

    private fun enableVanishModeUI() {
        vanishModeButton.setImageResource(R.drawable.iconn) // Update with your ON icon
        messagesRecyclerView.setBackgroundColor(resources.getColor(android.R.color.black))
        Toast.makeText(this, "Vanish Mode Enabled", Toast.LENGTH_SHORT).show()
        val fadeIn = AlphaAnimation(0f, 1f).apply { duration = 500 }
        messagesRecyclerView.startAnimation(fadeIn)
    }


    private fun disableVanishModeUI() {
        vanishModeButton.setImageResource(R.drawable.iconn) // Update with your OFF icon
        messagesRecyclerView.setBackgroundColor(resources.getColor(android.R.color.white))
        Toast.makeText(this, "Back to Normal Mode", Toast.LENGTH_SHORT).show()
        clearVanishModeMessages()
    }

    private fun clearVanishModeMessages() {
        val chatId = getChatId(currentUserId!!, otherUserId!!)
        if (vanishModeStartTimestamp != null) {
            database.child(chatId).child("messages").orderByChild("timestamp")
                .startAt(vanishModeStartTimestamp!!.toDouble())
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        for (data in snapshot.children) {
                            data.ref.removeValue()
                        }
                        vanishModeStartTimestamp = null
                    }
                    override fun onCancelled(error: DatabaseError) {
                        Log.e("MessageActivity", "Error clearing vanish mode messages: ${error.message}")
                    }
                })
        }
    }


    private fun observeOnlineStatus() {
        val userRef = FirebaseDatabase.getInstance().reference.child("Users").child(otherUserId!!)
        userRef.child("online").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val isOnline = snapshot.getValue(Boolean::class.java)
                when (isOnline) {
                    true -> {
                        onlineStatusTextView.text = "Online"
                        onlineStatusTextView.setTextColor(resources.getColor(android.R.color.holo_green_dark))
                    }
                    false -> {
                        onlineStatusTextView.text = "Away"
                        onlineStatusTextView.setTextColor(resources.getColor(android.R.color.darker_gray))
                    }
                    null -> {
                        onlineStatusTextView.text = "Unknown"
                        onlineStatusTextView.setTextColor(resources.getColor(android.R.color.darker_gray))
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e("OnlineStatus", "Failed to observe online status: ${error.message}")
            }
        })

        userRef.child("lastSeen").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val lastSeen = snapshot.getValue(Long::class.java)
                if (lastSeen != null && onlineStatusTextView.text == "Away") {
                    onlineStatusTextView.text = "Last seen: ${getTimeAgo(lastSeen)}"
                }
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e("OnlineStatus", "Failed to get last seen: ${error.message}")
            }
        })
    }

    private fun getTimeAgo(timestamp: Long): String {
        val diff = System.currentTimeMillis() - timestamp
        return when {
            diff < 60_000 -> "just now"
            diff < 3_600_000 -> "${diff / 60_000} minutes ago"
            diff < 24 * 3_600_000 -> "${diff / 3_600_000} hours ago"
            else -> "${diff / (24 * 3_600_000)} days ago"
        }
    }

    private fun openGallery() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), STORAGE_PERMISSION_CODE)
        } else {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            startActivityForResult(intent, PICK_IMAGE_REQUEST)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == STORAGE_PERMISSION_CODE && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            openGallery()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null) {
            val imageUri = data.data ?: return
            try {
                val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, imageUri)
                val byteArrayOutputStream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 70, byteArrayOutputStream)
                val imageBytes = byteArrayOutputStream.toByteArray()
                val imageBase64 = Base64.encodeToString(imageBytes, Base64.DEFAULT)
                sendMessage(imageBase64 = imageBase64)
            } catch (e: Exception) {
                Log.e("MessageActivity", "Error processing image: ${e.message}")
                Toast.makeText(this, "Error loading image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadOtherUserProfile() {
        database.root.child("Users").child(otherUserId!!).get().addOnSuccessListener { snapshot ->
            val user = snapshot.getValue(User::class.java)
            if (user != null) {
                otherUsernameTextView.text = user.username
                if (user.profileImageBase64.isNotEmpty()) {
                    try {
                        val imageBytes = Base64.decode(user.profileImageBase64, Base64.DEFAULT)
                        Glide.with(this).load(imageBytes).circleCrop().into(otherProfilePic)
                    } catch (e: Exception) {
                        Log.e("MessageActivity", "Error loading profile image: ${e.message}")
                    }
                }
            }
        }.addOnFailureListener {
            Log.e("MessageActivity", "Failed to load user profile: ${it.message}")
        }
    }

    private fun loadMessages() {
        val chatId = getChatId(currentUserId!!, otherUserId!!)
        database.child(chatId).orderByChild("timestamp").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val messages = mutableListOf<Message>()
                for (data in snapshot.children) {
                    val message = data.getValue(Message::class.java) ?: continue
                    if (!message.isDeleted) messages.add(message)
                }
                messageAdapter.updateMessages(messages)
                messagesRecyclerView.scrollToPosition(messages.size - 1)
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e("MessageActivity", "Database error: ${error.message}")
            }
        })
    }

    private fun sendMessage(text: String? = null, imageBase64: String? = null, postId: String? = null) {
        val chatId = getChatId(currentUserId!!, otherUserId!!)
        val message = Message(
            senderId = currentUserId!!,
            receiverId = otherUserId!!,
            text = text,
            imageBase64 = imageBase64,
            postId = postId,
            vanishMode = vanishMode
        )
        database.child(chatId).push().setValue(message)
    }

    private fun getChatId(user1: String, user2: String): String {
        return if (user1 < user2) {
            user1 + "_" + user2
        } else {
            user2 + "_" + user1
        }
    }

    private fun enableVanishMode() {
        vanishMode = true
        vanishModeStartTimestamp = System.currentTimeMillis()
        Toast.makeText(this, "Vanish Mode Enabled", Toast.LENGTH_SHORT).show()
        val fadeIn = AlphaAnimation(0f, 1f).apply { duration = 500 }
        messagesRecyclerView.startAnimation(fadeIn)
        messagesRecyclerView.setBackgroundColor(resources.getColor(android.R.color.black))
        sendMessage(text = "VANISH_MODE_ENABLED")
    }

    private fun disableVanishMode() {
        vanishMode = false
        val chatId = getChatId(currentUserId!!, otherUserId!!)
        database.child(chatId).orderByChild("timestamp").startAt(vanishModeStartTimestamp!!.toDouble())
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    for (data in snapshot.children) {
                        data.ref.removeValue()
                    }
                    messagesRecyclerView.setBackgroundColor(resources.getColor(android.R.color.white))
                    vanishModeStartTimestamp = null
                    Toast.makeText(this@MessageActivity, "Back to Normal Mode", Toast.LENGTH_SHORT).show()
                }
                override fun onCancelled(error: DatabaseError) {
                    Log.e("MessageActivity", "Error disabling vanish mode: ${error.message}")
                }
            })
    }

    private fun handleMessageClick(message: Message, position: Int) {
        if (message.senderId != currentUserId || message.text == "VANISH_MODE_ENABLED") return
        val currentTime = System.currentTimeMillis()
        if (currentTime - message.timestamp > 5 * 60 * 1000) {
            Toast.makeText(this, "Cannot edit/delete after 5 minutes", Toast.LENGTH_SHORT).show()
            return
        }

        val dialogView = layoutInflater.inflate(R.layout.dialog_message_options, null)
        val editButton = dialogView.findViewById<Button>(R.id.editButton)
        val deleteButton = dialogView.findViewById<Button>(R.id.deleteButton)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        editButton.setOnClickListener {
            showEditDialog(message, position)
            dialog.dismiss()
        }
        deleteButton.setOnClickListener {
            deleteMessage(message, position)
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun showEditDialog(message: Message, position: Int) {
        val editText = EditText(this).apply { setText(message.text) }
        AlertDialog.Builder(this)
            .setTitle("Edit Message")
            .setView(editText)
            .setPositiveButton("Save") { _, _ ->
                val newText = editText.text.toString().trim()
                if (newText.isNotEmpty()) editMessage(message, newText, position)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun editMessage(message: Message, newText: String, position: Int) {
        val chatId = getChatId(currentUserId!!, otherUserId!!)
        database.child(chatId).orderByChild("timestamp").equalTo(message.timestamp.toDouble())
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    for (data in snapshot.children) {
                        val updatedMessage = message.copy(text = newText, isEdited = true)
                        data.ref.setValue(updatedMessage)
                        messageAdapter.updateMessage(position, updatedMessage)
                    }
                }
                override fun onCancelled(error: DatabaseError) {
                    Log.e("MessageActivity", "Error editing message: ${error.message}")
                }
            })
    }

    private fun deleteMessage(message: Message, position: Int) {
        val chatId = getChatId(currentUserId!!, otherUserId!!)
        database.child(chatId).orderByChild("timestamp").equalTo(message.timestamp.toDouble())
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    for (data in snapshot.children) {
                        data.ref.child("isDeleted").setValue(true)
                        messageAdapter.removeMessage(position)
                    }
                }
                override fun onCancelled(error: DatabaseError) {
                    Log.e("MessageActivity", "Error deleting message: ${error.message}")
                }
            })
    }

    override fun onDestroy() {
        super.onDestroy()
        if (vanishMode) disableVanishMode()
    }
}