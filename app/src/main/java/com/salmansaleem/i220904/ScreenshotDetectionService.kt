package com.salmansaleem.i220904

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class ScreenshotDetectionService : Service() {

    private var contentObserver: ContentObserver? = null
    private var currentUserId: String? = null
    private var otherUserId: String? = null

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "screenshot_detection_channel"
        private const val TAG = "ScreenshotDetection"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        // Minimal initialization here to avoid delays
    }



    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand started")

        // Start as foreground service immediately
        try {
            startForeground(NOTIFICATION_ID, createNotification())
            Log.d(TAG, "Foreground service started successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground: ${e.message}", e)
            stopSelf()
            return START_NOT_STICKY
        }

        // Initialize user IDs
        currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        otherUserId = intent?.getStringExtra("OTHER_USER_ID")

        if (currentUserId == null || otherUserId == null) {
            Log.e(TAG, "Missing user IDs: currentUserId=$currentUserId, otherUserId=$otherUserId")
            stopSelf()
            return START_NOT_STICKY
        }

        // Only setup observer if permissions are granted
        if (checkStoragePermission()) {
            setupContentObserver()
        } else {
            Log.w(TAG, "No storage permission, running without observer")
            // Service runs but won't detect screenshots
        }

        Log.d(TAG, "onStartCommand completed")
        return START_STICKY
    }


    private fun checkStoragePermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun createNotification(): Notification {
        Log.d(TAG, "Creating notification")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Screenshot Detection",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Channel for screenshot detection service"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }

        val notificationIntent = Intent(this, MessageActivity::class.java)
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, pendingIntentFlags)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Chat Monitoring")
            .setContentText("Monitoring for screenshots")
            .setSmallIcon(android.R.drawable.ic_dialog_info) // Safe default icon
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }


    private fun setupContentObserver() {
        if (contentObserver != null) {
            Log.d(TAG, "Content observer already set up, skipping")
            return
        }

        contentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)
                uri?.let { checkForScreenshot(it) }
            }
        }
        try {
            contentResolver.registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                true,
                contentObserver!!
            )
            Log.d(TAG, "Content observer registered")
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied registering content observer: ${e.message}", e)
        }
    }





    private fun checkForScreenshot(uri: Uri) {
        val cursor = contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val pathIndex = it.getColumnIndex(MediaStore.Images.Media.DATA)
                if (pathIndex != -1) {
                    val path = it.getString(pathIndex)
                    if (path.contains("Screenshots", ignoreCase = true)) {
                        Log.d(TAG, "Screenshot detected at: $path")
                        notifyOtherUser()
                    }
                } else {
                    val relativePathIndex = it.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH)
                    if (relativePathIndex != -1) {
                        val relativePath = it.getString(relativePathIndex)
                        if (relativePath.contains("Screenshots", ignoreCase = true)) {
                            Log.d(TAG, "Screenshot detected in relative path: $relativePath")
                            notifyOtherUser()
                        }
                    }
                }
            }
        }
    }

    private fun notifyOtherUser() {
        if (currentUserId == null || otherUserId == null) return

        FirebaseDatabase.getInstance().reference.child("Users").child(currentUserId!!)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val username = snapshot.child("username").getValue(String::class.java) ?: "Unknown"
                    sendScreenshotNotification(username)
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Failed to fetch username: ${error.message}")
                    sendScreenshotNotification("Someone")
                }
            })
    }

    private fun sendScreenshotNotification(username: String) {
        val notificationData = mapOf(
            "type" to "screenshot_taken",
            "senderId" to currentUserId!!,
            "receiverId" to otherUserId!!,
            "username" to username,
            "timestamp" to System.currentTimeMillis().toString()
        )

        FirebaseDatabase.getInstance().reference.child("Notifications").child(otherUserId!!).push()
            .setValue(notificationData)
            .addOnSuccessListener {
                Log.d(TAG, "Screenshot notification sent to $otherUserId")
            }
            .addOnFailureListener {
                Log.e(TAG, "Failed to send screenshot notification: ${it.message}")
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        contentObserver?.let {
            contentResolver.unregisterContentObserver(it)
            Log.d(TAG, "Content observer unregistered")
        }
        stopForeground(true)
        Log.d(TAG, "Service destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}