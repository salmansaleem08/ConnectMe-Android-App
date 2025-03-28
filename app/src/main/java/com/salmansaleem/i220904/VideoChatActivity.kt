package com.salmansaleem.i220904

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.SurfaceView
import android.view.View
import android.widget.FrameLayout
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
import io.agora.rtc2.video.VideoCanvas

class VideoChatActivity : AppCompatActivity() {

    private lateinit var usernameTextView: TextView
    private lateinit var callTimeTextView: TextView
    private lateinit var endCallButton: ImageView
    private lateinit var muteButton: ImageView
    private lateinit var speakerButton: ImageView
    private lateinit var localVideoContainer: FrameLayout
    private lateinit var remoteVideoContainer: FrameLayout

    private var rtcEngine: RtcEngine? = null
    private var callStartTime: Long = 0
    private val handler = Handler(Looper.getMainLooper())
    private var isMuted = false
    private var isSpeakerOn = true

    private val APP_ID = "a264f8409de44945ab9609f4cd4a219f"
    private lateinit var channelName: String
    private lateinit var callerId: String
    private lateinit var receiverId: String
    private var isCaller = false
    private val VIDEO_PERMISSION_CODE = 201

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
            Log.d("VideoChatActivity", "Joined channel: $channel with UID: $uid")
            callStartTime = System.currentTimeMillis()
            handler.post(updateCallTimeRunnable)
        }

        override fun onUserJoined(uid: Int, elapsed: Int) {
            Log.d("VideoChatActivity", "Remote user joined with UID: $uid, elapsed: $elapsed")
            runOnUiThread {
                setupRemoteVideo(uid)
            }
        }

        override fun onUserOffline(uid: Int, reason: Int) {
            Log.d("VideoChatActivity", "User offline: $uid, reason: $reason")
            endCall()
        }

        override fun onError(err: Int) {
            Log.e("VideoChatActivity", "Agora error code: $err")
            Toast.makeText(this@VideoChatActivity, "Call error: $err", Toast.LENGTH_SHORT).show()
        }
    }

    private val notificationManager by lazy { getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_chat)

        usernameTextView = findViewById(R.id.name)
        callTimeTextView = findViewById(R.id.time)
        endCallButton = findViewById(R.id.endcall)
        muteButton = findViewById(R.id.mc)
        speakerButton = findViewById(R.id.speaker)
        localVideoContainer = findViewById(R.id.local_video_container)
        remoteVideoContainer = findViewById(R.id.remote_video_container)

        channelName = intent.getStringExtra("CHANNEL_NAME") ?: "default_channel"
        callerId = intent.getStringExtra("CALLER_ID") ?: ""
        receiverId = intent.getStringExtra("RECEIVER_ID") ?: ""
        isCaller = intent.getBooleanExtra("IS_CALLER", false)

        // Fetch the other user's username
        loadOtherUserUsername()

        when (intent.action) {
            "ACCEPT_CALL" -> {
                if (checkVideoPermissions()) {
                    initializeAgoraEngine()
                    joinChannel()
                    updateCallStatus("accepted")
                    notificationManager.cancel(1)
                }
            }
            "REJECT_CALL" -> {
                updateCallStatus("rejected")
                finish()
            }
            else -> {
                if (isCaller && checkVideoPermissions()) {
                    initializeAgoraEngine()
                    joinChannel()
                    listenForCallResponse()
                }
            }
        }

        setupClickListeners()
    }

    private fun loadOtherUserUsername() {
        val otherUserId = if (isCaller) receiverId else callerId
        FirebaseDatabase.getInstance().reference.child("Users").child(otherUserId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val username = snapshot.child("username").getValue(String::class.java) ?: "Unknown"
                    usernameTextView.text = username
                    Log.d("VideoChatActivity", "Displaying username: $username for user $otherUserId")
                }

                override fun onCancelled(error: DatabaseError) {
                    usernameTextView.text = "Unknown"
                    Log.e("VideoChatActivity", "Failed to load username: ${error.message}")
                }
            })
    }

    private fun checkVideoPermissions(): Boolean {
        val permissions = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA,
            Manifest.permission.INTERNET
        )
        val notGranted = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (notGranted.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, notGranted.toTypedArray(), VIDEO_PERMISSION_CODE)
            return false
        }
        return true
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == VIDEO_PERMISSION_CODE && grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            initializeAgoraEngine()
            joinChannel()
        } else {
            Toast.makeText(this, "Required permissions denied", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun initializeAgoraEngine() {
        try {
            val config = RtcEngineConfig().apply {
                mAppId = APP_ID
                mContext = this@VideoChatActivity
                mEventHandler = rtcEventHandler
            }
            rtcEngine = RtcEngine.create(config)
            rtcEngine?.enableVideo()
            rtcEngine?.enableAudio()
            rtcEngine?.setEnableSpeakerphone(isSpeakerOn)
            setupLocalVideo()
            Log.d("VideoChatActivity", "Agora engine initialized with video enabled")
        } catch (e: Exception) {
            Log.e("VideoChatActivity", "Error initializing Agora: ${e.message}", e)
            Toast.makeText(this, "Error initializing call: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun setupLocalVideo() {
        val localView = RtcEngine.CreateRendererView(this)
        localView.setZOrderMediaOverlay(true)
        localVideoContainer.addView(localView)
        rtcEngine?.setupLocalVideo(VideoCanvas(localView, VideoCanvas.RENDER_MODE_FIT, 0))
    }

    private fun setupRemoteVideo(uid: Int) {
        Log.d("VideoChatActivity", "Setting up remote video for UID: $uid")
        val remoteView = RtcEngine.CreateRendererView(this)
        remoteView.setZOrderMediaOverlay(false)
        remoteVideoContainer.removeAllViews()
        remoteVideoContainer.addView(remoteView)
        rtcEngine?.setupRemoteVideo(VideoCanvas(remoteView, VideoCanvas.RENDER_MODE_FIT, uid))
        remoteView.visibility = View.VISIBLE
    }

    private fun joinChannel() {
        val options = ChannelMediaOptions().apply {
            autoSubscribeAudio = true
            autoSubscribeVideo = true
            publishMicrophoneTrack = true
            publishCameraTrack = true
            clientRoleType = Constants.CLIENT_ROLE_BROADCASTER
        }
        rtcEngine?.joinChannel(null, channelName, 0, options)
        Log.d("VideoChatActivity", "Joining channel: $channelName")
    }

    private fun updateCallStatus(status: String) {
        val callRef = FirebaseDatabase.getInstance().reference.child("CallInvites").child(receiverId)
        callRef.child("status").setValue(status)
            .addOnSuccessListener { Log.d("VideoChatActivity", "Call status updated to $status") }
            .addOnFailureListener { Log.e("VideoChatActivity", "Failed to update call status: ${it.message}") }
    }

    private fun listenForCallResponse() {
        val callRef = FirebaseDatabase.getInstance().reference.child("CallInvites").child(receiverId)
        callRef.addValueEventListener(object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                val status = snapshot.child("status").getValue(String::class.java)
                when (status) {
                    "rejected" -> {
                        runOnUiThread {
                            Toast.makeText(this@VideoChatActivity, "$usernameTextView.text is busy", Toast.LENGTH_SHORT).show()
                        }
                        endCall()
                        callRef.removeEventListener(this)
                    }
                    "accepted" -> {
                        // Call continues
                    }
                }
            }

            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                Log.e("VideoChatActivity", "Error listening for call response: ${error.message}")
            }
        })
    }

    private fun setupClickListeners() {
        muteButton.setOnClickListener {
            isMuted = !isMuted
            rtcEngine?.muteLocalAudioStream(isMuted)
            muteButton.setImageResource(if (isMuted) R.drawable.mc_muted else R.drawable.mc)
        }

        speakerButton.setOnClickListener {
            isSpeakerOn = !isSpeakerOn
            rtcEngine?.setEnableSpeakerphone(isSpeakerOn)
            speakerButton.setImageResource(if (isSpeakerOn) R.drawable.volume else R.drawable.volume1)
        }

        endCallButton.setOnClickListener {
            endCall()
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
        notificationManager.cancel(1)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (rtcEngine != null) {
            endCall()
        }
    }
}