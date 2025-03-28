package com.salmansaleem.i220904

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import io.agora.rtc2.ChannelMediaOptions
import io.agora.rtc2.Constants
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.RtcEngineConfig

class AudioChatActivity : AppCompatActivity() {

    private lateinit var usernameTextView: TextView
    private lateinit var profilePicImageView: ImageView
    private lateinit var callTimeTextView: TextView
    private lateinit var videoCallButton: ImageView
    private lateinit var endCallButton: ImageView
    private lateinit var muteButton: ImageView
    private lateinit var speakerButton: ImageView

    private var rtcEngine: RtcEngine? = null
    private var callStartTime: Long = 0
    private val handler = Handler(Looper.getMainLooper())
    private var isMuted = false
    private var isSpeakerOn = false // Default to earpiece (speaker off)

    private val APP_ID = "a264f8409de44945ab9609f4cd4a219f" // Your Agora App ID
    private lateinit var channelName: String
    private lateinit var username: String
    private lateinit var callerId: String
    private lateinit var receiverId: String
    private var isCaller = false
    private val AUDIO_PERMISSION_CODE = 200

    private val updateCallTimeRunnable = object : Runnable {
        override fun run() {
            val elapsedTime = (System.currentTimeMillis() - callStartTime) / 1000
            val minutes = elapsedTime / 60
            val seconds = elapsedTime % 60
            callTimeTextView.text = String.format("%02d:%02d", minutes, seconds)
            handler.postDelayed(this, 1000)
        }
    }

    private val rtcEventHandler = object : IRtcEngineEventHandler() {
        override fun onJoinChannelSuccess(channel: String?, uid: Int, elapsed: Int) {
            Log.d("AudioChatActivity", "Joined channel: $channel with UID: $uid")
            callStartTime = System.currentTimeMillis()
            handler.post(updateCallTimeRunnable)
        }

        override fun onUserOffline(uid: Int, reason: Int) {
            Log.d("AudioChatActivity", "User offline: $uid, reason: $reason")
            endCall()
        }

        override fun onError(err: Int) {
            Log.e("AudioChatActivity", "Agora error code: $err")
            Toast.makeText(this@AudioChatActivity, "Call error: $err", Toast.LENGTH_SHORT).show()
        }
    }

    private val notificationManager by lazy { getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_audio_chat)

        // Initialize views
        usernameTextView = findViewById(R.id.name)
        profilePicImageView = findViewById(R.id.person)
        callTimeTextView = findViewById(R.id.time)
        videoCallButton = findViewById(R.id.videocall)
        endCallButton = findViewById(R.id.endcall)
        muteButton = findViewById(R.id.mc)
        speakerButton = findViewById(R.id.speaker)

        // Get data from intent
        channelName = intent.getStringExtra("CHANNEL_NAME") ?: "default_channel"
        callerId = intent.getStringExtra("CALLER_ID") ?: ""
        receiverId = intent.getStringExtra("RECEIVER_ID") ?: ""
        isCaller = intent.getBooleanExtra("IS_CALLER", false)


        loadUserData()

        // Handle Accept/Reject actions
        when (intent.action) {
            "ACCEPT_CALL" -> {
                if (checkAudioPermission()) {
                    initializeAgoraEngine()
                    joinChannel()
                    updateCallStatus("accepted")
                    // Clear notification when call is accepted
                    notificationManager.cancel(1)
                }
            }
            "REJECT_CALL" -> {
                updateCallStatus("rejected")
                // Only show toast to caller via Firebase listener, not here
                finish()
            }
            else -> {
                if (isCaller && checkAudioPermission()) {
                    initializeAgoraEngine()
                    joinChannel()
                    listenForCallResponse()
                }
            }
        }

        // Setup button listeners
        setupClickListeners()
    }


    private fun loadUserData() {
        val otherUserId = if (isCaller) receiverId else callerId
        FirebaseDatabase.getInstance().reference.child("Users").child(otherUserId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val username = snapshot.child("username").getValue(String::class.java) ?: "Unknown"
                    val profilePicBase64 = snapshot.child("profileImageBase64").getValue(String::class.java) ?: ""

                    usernameTextView.text = username

                    if (profilePicBase64.isNotEmpty()) {
                        try {
                            val bitmap = base64ToBitmap(profilePicBase64)
                            if (bitmap != null) {
                                val circularBitmap = getCircularBitmap(bitmap)
                                profilePicImageView.setImageBitmap(circularBitmap)
                            } else {
                                profilePicImageView.setImageResource(R.drawable.img5)
                            }
                        } catch (e: Exception) {
                            profilePicImageView.setImageResource(R.drawable.img5)
                        }
                    } else {
                        profilePicImageView.setImageResource(R.drawable.img5)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    usernameTextView.text = "Unknown"
                    profilePicImageView.setImageResource(R.drawable.img5)
                }
            })
    }


    private fun checkAudioPermission(): Boolean {
        val permissions = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.INTERNET
        )
        val notGranted = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (notGranted.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, notGranted.toTypedArray(), AUDIO_PERMISSION_CODE)
            return false
        }
        return true
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == AUDIO_PERMISSION_CODE && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            initializeAgoraEngine()
            joinChannel()
        } else {
            Toast.makeText(this, "Audio permission denied", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun initializeAgoraEngine() {
        try {
            Log.d("AudioChatActivity", "Initializing Agora with App ID: $APP_ID")
            val config = RtcEngineConfig().apply {
                mAppId = APP_ID
                mContext = this@AudioChatActivity
                mEventHandler = rtcEventHandler
            }
            rtcEngine = RtcEngine.create(config)
            rtcEngine?.enableAudio()
            rtcEngine?.setEnableSpeakerphone(isSpeakerOn) // Set initial speaker state
            Log.d("AudioChatActivity", "Agora engine initialized successfully")
        } catch (e: Exception) {
            Log.e("AudioChatActivity", "Error initializing Agora: ${e.message}", e)
            e.printStackTrace()
            Toast.makeText(this, "Error initializing call: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun joinChannel() {
        val options = ChannelMediaOptions().apply {
            autoSubscribeAudio = true
            publishMicrophoneTrack = true
            clientRoleType = Constants.CLIENT_ROLE_BROADCASTER
        }
        rtcEngine?.joinChannel(null, channelName, 0, options)
        Log.d("AudioChatActivity", "Attempting to join channel: $channelName")
    }

    private fun updateCallStatus(status: String) {
        val callRef = FirebaseDatabase.getInstance().reference.child("CallInvites").child(receiverId)
        callRef.child("status").setValue(status)
            .addOnSuccessListener { Log.d("AudioChatActivity", "Call status updated to $status") }
            .addOnFailureListener { Log.e("AudioChatActivity", "Failed to update call status: ${it.message}") }
    }

    private fun listenForCallResponse() {
        val callRef = FirebaseDatabase.getInstance().reference.child("CallInvites").child(receiverId)
        callRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val status = snapshot.child("status").getValue(String::class.java)
                val username = snapshot.child("username").getValue(String::class.java) ?: "Unknown"
                when (status) {
                    "rejected" -> {
                        runOnUiThread {
                            Toast.makeText(this@AudioChatActivity, "$username is busy", Toast.LENGTH_SHORT).show()
                        }
                        endCall()
                        callRef.removeEventListener(this)
                    }
                    "accepted" -> {
                        // Call continues, no action needed
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("AudioChatActivity", "Error listening for call response: ${error.message}")
            }
        })
    }

    private fun setupClickListeners() {
        muteButton.setOnClickListener {
            isMuted = !isMuted
            rtcEngine?.muteLocalAudioStream(isMuted)
            muteButton.setImageResource(if (isMuted) R.drawable.mc_muted else R.drawable.mc)
            Log.d("AudioChatActivity", "Mute toggled: $isMuted")
        }

        speakerButton.setOnClickListener {
            isSpeakerOn = !isSpeakerOn
            rtcEngine?.setEnableSpeakerphone(isSpeakerOn)
            speakerButton.setImageResource(if (isSpeakerOn) R.drawable.volume else R.drawable.volume1)
            // Verify speaker state
            val actualState = rtcEngine?.isSpeakerphoneEnabled() ?: false
            if (actualState != isSpeakerOn) {
                Log.w("AudioChatActivity", "Speaker state mismatch: expected $isSpeakerOn, actual $actualState")
                rtcEngine?.setEnableSpeakerphone(isSpeakerOn) // Force correct state
            }
        }

        endCallButton.setOnClickListener {
            endCall()
        }

        videoCallButton.setOnClickListener {
            Toast.makeText(this, "Video call feature not implemented yet", Toast.LENGTH_SHORT).show()
        }
    }

    private fun endCall() {
        handler.removeCallbacks(updateCallTimeRunnable)
        rtcEngine?.leaveChannel()
        RtcEngine.destroy()
        rtcEngine = null
        if (isCaller) {
            FirebaseDatabase.getInstance().reference.child("CallInvites").child(receiverId).removeValue()
        }
        notificationManager.cancel(1) // Clear notification on call end
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (rtcEngine != null) {
            endCall()
        }
    }

    // Helper method to decode Base64 string to Bitmap
    private fun base64ToBitmap(base64String: String): Bitmap? {
        return try {
            val decodedBytes = Base64.decode(base64String, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
        } catch (e: Exception) {
            Log.e("AudioChatActivity", "Error decoding Base64 to Bitmap: ${e.message}", e)
            null
        }
    }

    // Helper method to create a circular Bitmap
    private fun getCircularBitmap(bitmap: Bitmap): Bitmap {
        val sizeInDp = 200
        val scale = resources.displayMetrics.density
        val sizeInPixels = (sizeInDp * scale).toInt()

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