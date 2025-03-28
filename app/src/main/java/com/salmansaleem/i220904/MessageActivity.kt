package com.salmansaleem.i220904

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
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
import com.google.firebase.messaging.FirebaseMessaging
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.KeyFactory

import android.util.Log
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm

import java.security.spec.PKCS8EncodedKeySpec


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


        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                Log.d("FCM", "Token refreshed: $token")
                val userId = FirebaseAuth.getInstance().currentUser?.uid
                if (userId != null) {
                    FirebaseDatabase.getInstance().reference
                        .child("Users")
                        .child(userId)
                        .child("fcmToken")
                        .setValue(token)
                }
            } else {
                Log.e("FCM", "Token refresh failed: ${task.exception?.message}")
            }
        }


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
        observeVanishMode(chatId)
    }




    private fun checkNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_PERMISSION_CODE)
                return false
            }
        }
        return true
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == NOTIFICATION_PERMISSION_CODE && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            // Retry sending call invite if permission granted
            callButton.performClick()
        } else {
            Toast.makeText(this, "Notification permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private const val NOTIFICATION_PERMISSION_CODE = 101
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



        videoCallButton.setOnClickListener {
            if (checkNotificationPermission()) {
                val callerId = currentUserId!!
                val receiverId = otherUserId!!
                val channelName = getChatId(callerId, receiverId)
                val username = otherUsernameTextView.text.toString()
                sendVideoCallInvite(callerId, receiverId, channelName, username)
            }
        }

        callButton.setOnClickListener {
            if (checkNotificationPermission()) {
                val callerId = currentUserId!!
                val receiverId = otherUserId!!
                val channelName = getChatId(callerId, receiverId)
                val username = otherUsernameTextView.text.toString()
                sendCallInvite(callerId, receiverId, channelName, username)
            }
        }

        photoButton.setOnClickListener { openGallery() }
        vanishModeButton.setOnClickListener { toggleVanishMode() }
        backButton.setOnClickListener { finish() }
    }



    private fun sendVideoCallInvite(callerId: String, receiverId: String, channelName: String, username: String) {
        val callData = mapOf(
            "callerId" to callerId,
            "receiverId" to receiverId,
            "channelName" to channelName,
            "username" to username,
            "status" to "pending",
            "type" to "video_call_invite", // Differentiate from audio call
            "timestamp" to System.currentTimeMillis()
        )

        FirebaseDatabase.getInstance().reference.child("CallInvites").child(receiverId)
            .setValue(callData)
            .addOnSuccessListener {
                Log.d("MessageActivity", "Video call invite stored for $receiverId")
                sendFCMNotification(receiverId, callerId, channelName, username, "video_call_invite")
                listenForVideoCallResponse(callerId, receiverId, channelName, username)
            }
            .addOnFailureListener { e ->
                Log.e("MessageActivity", "Failed to send video call invite: ${e.message}")
                Toast.makeText(this, "Failed to initiate video call", Toast.LENGTH_SHORT).show()
            }
    }

    private fun sendCallInvite(callerId: String, receiverId: String, channelName: String, username: String) {
        val callData = mapOf(
            "callerId" to callerId,
            "receiverId" to receiverId,
            "channelName" to channelName,
            "username" to username,
            "status" to "pending",
            "type" to "call_invite",
            "timestamp" to System.currentTimeMillis()
        )

        // Store call invite in Firebase
        FirebaseDatabase.getInstance().reference.child("CallInvites").child(receiverId)
            .setValue(callData)
            .addOnSuccessListener {
                Log.d("MessageActivity", "Call invite stored for $receiverId")
                // Fetch receiver's FCM token and send notification
                sendFCMNotification(receiverId, callerId, channelName, username)
                listenForCallResponse(callerId, receiverId, channelName, username)
            }
            .addOnFailureListener { e ->
                Log.e("MessageActivity", "Failed to send call invite: ${e.message}")
                Toast.makeText(this, "Failed to initiate call", Toast.LENGTH_SHORT).show()
            }
    }


    private val client = OkHttpClient()


    private fun sendFCMNotification(
        receiverId: String,
        callerId: String,
        channelName: String,
        username: String,
        callType: String = "call_invite",
        messageText: String? = null // Add parameter for message content
    ) {
        FirebaseDatabase.getInstance().reference.child("Users").child(receiverId).child("fcmToken")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val receiverToken = snapshot.getValue(String::class.java)
                    if (receiverToken.isNullOrEmpty()) {
                        Log.e("FCM", "No valid FCM token for $receiverId")
                        Toast.makeText(this@MessageActivity, "$username is unavailable - no token", Toast.LENGTH_SHORT).show()
                        return
                    }
                    Thread {
                        try {
                            val accessToken = getAccessToken()
                            if (accessToken.isEmpty()) {
                                Log.e("FCM", "Failed to obtain access token")
                                Toast.makeText(this@MessageActivity, "Authentication failed", Toast.LENGTH_SHORT).show()
                                return@Thread
                            }

                            val projectId = "assignment2db1"
                            val url = "https://fcm.googleapis.com/v1/projects/$projectId/messages:send"
                            val json = JSONObject().apply {
                                put("message", JSONObject().apply {
                                    put("token", receiverToken)
                                    put("data", JSONObject().apply {
                                        put("type", callType)
                                        put("callerId", callerId)
                                        put("receiverId", receiverId)
                                        put("channelName", channelName)
                                        put("username", username)
                                        if (callType == "message" && messageText != null) {
                                            put("messageText", messageText) // Use the passed messageText
                                        }
                                    })
                                    put("android", JSONObject().apply {
                                        put("priority", "high")
                                    })
                                })
                            }.toString()

                            val request = Request.Builder()
                                .url(url)
                                .post(json.toRequestBody("application/json; charset=utf-8".toMediaType()))
                                .header("Authorization", "Bearer $accessToken")
                                .build()

                            client.newCall(request).execute().use { response ->
                                if (response.isSuccessful) {
                                    Log.d("FCM", "Notification sent successfully to $receiverId")
                                } else {
                                    Log.e("FCM", "FCM v1 failed: ${response.code} - ${response.body?.string()}")
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("FCM", "Error sending FCM v1: ${e.message}", e)
                            Toast.makeText(this@MessageActivity, "Notification failed: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }.start()
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("FCM", "Token fetch failed: ${error.message}")
                    Toast.makeText(this@MessageActivity, "Error fetching token: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            })
    }



    private fun getAccessToken(): String {
        try {
            val inputStream: InputStream = resources.openRawResource(R.raw.service_account)
            val jsonString = inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(jsonString)
            val clientEmail = json.getString("client_email")
            val privateKeyPem = json.getString("private_key")
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("\\n", "")
                .trim()
            val tokenUri = json.getString("token_uri")

            Log.d("FCM", "Raw private key (first 50 chars): ${privateKeyPem.take(50)}...")

            // Decode using android.util.Base64
            val keyBytes = try {
                Base64.decode(privateKeyPem, Base64.DEFAULT) // Use DEFAULT flag
            } catch (e: IllegalArgumentException) {
                Log.e("FCM", "Base64 decode failed: ${e.message}", e)
                return ""
            }
            Log.d("FCM", "Decoded key length: ${keyBytes.size} bytes")

            val keySpec = PKCS8EncodedKeySpec(keyBytes)
            val keyFactory = KeyFactory.getInstance("RSA")
            val privateKey = try {
                keyFactory.generatePrivate(keySpec)
            } catch (e: Exception) {
                Log.e("FCM", "Private key parsing failed: ${e.message}", e)
                return ""
            }

            val now = System.currentTimeMillis() / 1000
            val jwt = Jwts.builder()
                .setHeaderParam("alg", "RS256")
                .setHeaderParam("typ", "JWT")
                .setIssuer(clientEmail)
                .setAudience(tokenUri)
                .setIssuedAt(java.util.Date(now * 1000))
                .setExpiration(java.util.Date((now + 3600) * 1000))
                .claim("scope", "https://www.googleapis.com/auth/firebase.messaging")
                .signWith(privateKey, SignatureAlgorithm.RS256)
                .compact()
            Log.d("FCM", "Generated JWT: $jwt")

            val requestBody = "grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=$jwt"
                .toRequestBody("application/x-www-form-urlencoded".toMediaType())
            val request = Request.Builder()
                .url(tokenUri)
                .post(requestBody)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .build()

            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: "{}"
                if (response.isSuccessful) {
                    val responseJson = JSONObject(responseBody)
                    val accessToken = responseJson.getString("access_token")
                    Log.d("FCM", "Access token obtained: $accessToken")
                    return accessToken
                } else {
                    Log.e("FCM", "Token request failed: ${response.code} - $responseBody")
                    return ""
                }
            }
        } catch (e: Exception) {
            Log.e("FCM", "Error getting access token: ${e.message}", e)
            return ""
        }
    }




    private fun listenForVideoCallResponse(callerId: String, receiverId: String, channelName: String, username: String) {
        val callRef = FirebaseDatabase.getInstance().reference.child("CallInvites").child(receiverId)
        callRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val status = snapshot.child("status").getValue(String::class.java)
                when (status) {
                    "accepted" -> {
                        Log.d("MessageActivity", "Video call accepted by $receiverId")
                        startVideoChat(callerId, receiverId, channelName, username, true)
                        callRef.removeEventListener(this)
                    }
                    "rejected" -> {
                        Log.d("MessageActivity", "Video call rejected by $receiverId")
                        Toast.makeText(this@MessageActivity, "$username is busy", Toast.LENGTH_SHORT).show()
                        callRef.removeValue()
                        callRef.removeEventListener(this)
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("MessageActivity", "Error listening for video call response: ${error.message}")
            }
        })
    }

    private fun startVideoChat(callerId: String, receiverId: String, channelName: String, username: String, isCaller: Boolean) {
        FirebaseDatabase.getInstance().reference.child("Users").child(if (isCaller) receiverId else callerId)
            .child("profileImageBase64")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val profilePicBase64 = snapshot.getValue(String::class.java) ?: ""
                    val intent = Intent(this@MessageActivity, VideoChatActivity::class.java).apply {
                        putExtra("USERNAME", username)
                        putExtra("CHANNEL_NAME", channelName)
                        putExtra("CALLER_ID", callerId)
                        putExtra("RECEIVER_ID", receiverId)
                        putExtra("PROFILE_PIC_BASE64", profilePicBase64)
                        putExtra("IS_CALLER", isCaller)
                    }
                    startActivity(intent)
                }
                override fun onCancelled(error: DatabaseError) {
                    val intent = Intent(this@MessageActivity, VideoChatActivity::class.java).apply {
                        putExtra("USERNAME", username)
                        putExtra("CHANNEL_NAME", channelName)
                        putExtra("CALLER_ID", callerId)
                        putExtra("RECEIVER_ID", receiverId)
                        putExtra("PROFILE_PIC_BASE64", "")
                        putExtra("IS_CALLER", isCaller)
                    }
                    startActivity(intent)
                }
            })
    }

    private fun listenForCallResponse(callerId: String, receiverId: String, channelName: String, username: String) {
        val callRef = FirebaseDatabase.getInstance().reference.child("CallInvites").child(receiverId)
        callRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val status = snapshot.child("status").getValue(String::class.java)
                when (status) {
                    "accepted" -> {
                        Log.d("MessageActivity", "Call accepted by $receiverId")
                        startAudioChat(callerId, receiverId, channelName, username, true)
                        callRef.removeEventListener(this) // Stop listening after acceptance
                    }
                    "rejected" -> {
                        Log.d("MessageActivity", "Call rejected by $receiverId")
                        Toast.makeText(this@MessageActivity, "$username is busy", Toast.LENGTH_SHORT).show()
                        callRef.removeValue() // Clean up
                        callRef.removeEventListener(this)
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("MessageActivity", "Error listening for call response: ${error.message}")
            }
        })
    }

    private fun startAudioChat(callerId: String, receiverId: String, channelName: String, username: String, isCaller: Boolean) {
        FirebaseDatabase.getInstance().reference.child("Users").child(if (isCaller) receiverId else callerId)
            .child("profileImageBase64")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val profilePicBase64 = snapshot.getValue(String::class.java) ?: ""
                    val intent = Intent(this@MessageActivity, AudioChatActivity::class.java).apply {
                        putExtra("USERNAME", username)
                        putExtra("CHANNEL_NAME", channelName)
                        putExtra("CALLER_ID", callerId)
                        putExtra("RECEIVER_ID", receiverId)
                        putExtra("PROFILE_PIC_BASE64", profilePicBase64)
                        putExtra("IS_CALLER", isCaller)
                    }
                    startActivity(intent)
                }
                override fun onCancelled(error: DatabaseError) {
                    // Fallback with empty profile pic
                    val intent = Intent(this@MessageActivity, AudioChatActivity::class.java).apply {
                        putExtra("USERNAME", username)
                        putExtra("CHANNEL_NAME", channelName)
                        putExtra("CALLER_ID", callerId)
                        putExtra("RECEIVER_ID", receiverId)
                        putExtra("PROFILE_PIC_BASE64", "")
                        putExtra("IS_CALLER", isCaller)
                    }
                    startActivity(intent)
                }
            })
    }

    private fun sendCallNotification(callerId: String, receiverId: String, channelName: String, username: String) {
        val callData = mapOf(
            "callerId" to callerId,
            "receiverId" to receiverId,
            "channelName" to channelName,
            "username" to username,
            "type" to "call_invite",
            "timestamp" to System.currentTimeMillis()
        )

        // Store call invite in Firebase for status tracking
        FirebaseDatabase.getInstance().reference.child("CallInvites").child(receiverId).setValue(callData)
            .addOnSuccessListener {
                Log.d("MessageActivity", "Call notification sent to $receiverId")
            }
            .addOnFailureListener { e ->
                Log.e("MessageActivity", "Failed to send call notification: ${e.message}")
            }
    }




    private fun observeVanishMode(chatId: String) {
        val vanishModeRef = database.child(chatId).child("vanishMode")
        vanishModeRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val vanishModeData = snapshot.getValue(VanishMode::class.java)
                val enabled = vanishModeData?.enabled ?: false
                val timestamp = vanishModeData?.timestamp

                if (enabled != vanishMode) {
                    vanishMode = enabled
                    vanishModeStartTimestamp = timestamp
                    if (vanishMode) {
                        enableVanishModeUI()
                    } else {
                        disableVanishModeUI()
                    }
                    messageAdapter.setVanishMode(vanishMode)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("MessageActivity", "Failed to observe vanish mode: ${error.message}")
            }
        })
    }

    private fun toggleVanishMode() {
        val chatId = getChatId(currentUserId!!, otherUserId!!)
        if (!vanishMode) {
            enableVanishMode(chatId)
        } else {
            disableVanishMode(chatId)
        }
    }

    private fun enableVanishMode(chatId: String) {
        vanishMode = true
        vanishModeStartTimestamp = System.currentTimeMillis()
        val vanishModeData = VanishMode(enabled = true, timestamp = vanishModeStartTimestamp!!)
        database.child(chatId).child("vanishMode").setValue(vanishModeData)
            .addOnSuccessListener {
                enableVanishModeUI()
                messageAdapter.setVanishMode(true)
            }
            .addOnFailureListener {
                Log.e("MessageActivity", "Failed to enable vanish mode: ${it.message}")
                vanishMode = false
            }
    }

    private fun disableVanishMode(chatId: String) {
        vanishMode = false
        val vanishModeData = VanishMode(enabled = false, timestamp = System.currentTimeMillis())
        database.child(chatId).child("vanishMode").setValue(vanishModeData)
            .addOnSuccessListener {
                disableVanishModeUI()
                clearVanishModeMessages(chatId)
                messageAdapter.setVanishMode(false)
            }
            .addOnFailureListener {
                Log.e("MessageActivity", "Failed to disable vanish mode: ${it.message}")
                vanishMode = true
            }
    }

    private fun enableVanishModeUI() {
        vanishModeButton.setImageResource(R.drawable.iconn) // Replace with your ON icon
        messagesRecyclerView.setBackgroundColor(resources.getColor(android.R.color.black))
        Toast.makeText(this, "Vanish Mode Enabled", Toast.LENGTH_SHORT).show()
        val fadeIn = AlphaAnimation(0f, 1f).apply { duration = 500 }
        messagesRecyclerView.startAnimation(fadeIn)
    }

    private fun disableVanishModeUI() {
        vanishModeButton.setImageResource(R.drawable.iconn) // Replace with your OFF icon
        messagesRecyclerView.setBackgroundColor(resources.getColor(android.R.color.white))
        Toast.makeText(this, "Back to Normal Mode", Toast.LENGTH_SHORT).show()
    }

    private fun clearVanishModeMessages(chatId: String) {
        if (vanishModeStartTimestamp != null) {
            database.child(chatId).child("messages").orderByChild("timestamp")
                .startAt(vanishModeStartTimestamp!!.toDouble())
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        for (data in snapshot.children) {
                            val message = data.getValue(Message::class.java)
                            if (message?.vanishMode == true) {
                                data.ref.removeValue()
                            }
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
        val permission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(permission), STORAGE_PERMISSION_CODE)
        } else {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            startActivityForResult(intent, PICK_IMAGE_REQUEST)
        }
    }

//    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
//        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
//        if (requestCode == STORAGE_PERMISSION_CODE && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
//            openGallery()
//        }
//    }

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
        database.child(chatId).child("messages").orderByChild("timestamp").addValueEventListener(object : ValueEventListener {
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
        database.child(chatId).child("messages").push().setValue(message)
            .addOnSuccessListener {
                // Fetch sender's username from Firebase Database
                FirebaseDatabase.getInstance().reference.child("Users").child(currentUserId!!)
                    .child("username")
                    .addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            val senderUsername = snapshot.getValue(String::class.java) ?: "Unknown"
                            val messageContent = text ?: if (imageBase64 != null) "Image" else "Message"
                            sendFCMNotification(
                                receiverId = otherUserId!!,
                                callerId = currentUserId!!,
                                channelName = chatId,
                                username = senderUsername, // Use the fetched username
                                callType = "message",
                                messageText = messageContent
                            )
                        }

                        override fun onCancelled(error: DatabaseError) {
                            Log.e("MessageActivity", "Failed to fetch sender username: ${error.message}")
                            // Fallback to "Unknown" if username fetch fails
                            val messageContent = text ?: if (imageBase64 != null) "Image" else "Message"
                            sendFCMNotification(
                                receiverId = otherUserId!!,
                                callerId = currentUserId!!,
                                channelName = chatId,
                                username = "Unknown",
                                callType = "message",
                                messageText = messageContent
                            )
                        }
                    })
            }
            .addOnFailureListener {
                Log.e("MessageActivity", "Failed to send message: ${it.message}")
                Toast.makeText(this, "Failed to send message", Toast.LENGTH_SHORT).show()
            }
    }



    private fun getChatId(user1: String, user2: String): String {
        return if (user1 < user2) {
            user1 + "_" + user2
        } else {
            user2 + "_" + user1
        }
    }

    private fun handleMessageClick(message: Message, position: Int) {
        if (message.senderId != currentUserId || message.vanishMode || message.text == "This message was deleted") return
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
        database.child(chatId).child("messages").orderByChild("timestamp").equalTo(message.timestamp.toDouble())
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
        database.child(chatId).child("messages").orderByChild("timestamp").equalTo(message.timestamp.toDouble())
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    for (data in snapshot.children) {
                        data.ref.removeValue() // Completely remove the original message
                        val deletedMessage = Message(
                            senderId = message.senderId,
                            receiverId = message.receiverId,
                            text = "This message was deleted",
                            timestamp = message.timestamp,
                            isEdited = false,
                            isDeleted = true // Mark as deleted to prevent further actions
                        )
                        database.child(chatId).child("messages").push().setValue(deletedMessage)
                        messageAdapter.updateMessage(position, deletedMessage)
                    }
                }
                override fun onCancelled(error: DatabaseError) {
                    Log.e("MessageActivity", "Error deleting message: ${error.message}")
                }
            })
    }

    override fun onDestroy() {
        super.onDestroy()
        if (vanishMode) {
            val chatId = getChatId(currentUserId!!, otherUserId!!)
            disableVanishMode(chatId)
        }
    }
}