package com.salmansaleem.i220904

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class FirebaseMessagingService : FirebaseMessagingService() {




    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d("FCM", "Message received: ${remoteMessage.data}")

        val data = remoteMessage.data
        val type = data["type"]
        when (type) {
            "call_invite", "video_call_invite" -> {
                // Existing call/video call handling
                val callerId = data["callerId"] ?: return
                val receiverId = data["receiverId"] ?: return
                val channelName = data["channelName"] ?: return
                val username = data["username"] ?: "Unknown"

                if (receiverId == FirebaseAuth.getInstance().currentUser?.uid) {
                    FirebaseDatabase.getInstance().reference.child("Users").child(callerId)
                        .child("profileImageBase64")
                        .addListenerForSingleValueEvent(object : ValueEventListener {
                            override fun onDataChange(snapshot: DataSnapshot) {
                                val profilePicBase64 = snapshot.getValue(String::class.java) ?: ""
                                if (type == "video_call_invite") {
                                    showVideoCallNotification(callerId, receiverId, channelName, username, profilePicBase64)
                                } else {
                                    showCallNotification(callerId, receiverId, channelName, username, profilePicBase64)
                                }
                            }
                            override fun onCancelled(error: DatabaseError) {
                                if (type == "video_call_invite") {
                                    showVideoCallNotification(callerId, receiverId, channelName, username, "")
                                } else {
                                    showCallNotification(callerId, receiverId, channelName, username, "")
                                }
                            }
                        })
                }
            }
            "message" -> {
                // Existing message handling
                val senderId = data["callerId"] ?: return
                val receiverId = data["receiverId"] ?: return
                val channelName = data["channelName"] ?: return
                val username = data["username"] ?: "Unknown"
                val messageText = data["messageText"] ?: "New Message"

                if (receiverId == FirebaseAuth.getInstance().currentUser?.uid) {
                    showMessageNotification(senderId, receiverId, channelName, username, messageText)
                }
            }
            "follow_request" -> {
                val senderId = data["senderId"] ?: return
                val receiverId = data["receiverId"] ?: return
                val username = data["username"] ?: "Unknown"
                val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
                Log.d("FCM", "Follow request - senderId: $senderId, receiverId: $receiverId, username: $username, currentUserId: $currentUserId")
                if (receiverId == currentUserId) {
                    showFollowRequestNotification(senderId, receiverId, username)
                } else {
                    Log.d("FCM", "Receiver ID does not match current user: $receiverId != $currentUserId")
                }
            }
            "screenshot_taken" -> {
                // Existing screenshot handling
                val senderId = data["senderId"] ?: return
                val receiverId = data["receiverId"] ?: return
                val username = data["username"] ?: "Unknown"

                if (receiverId == FirebaseAuth.getInstance().currentUser?.uid) {
                    showScreenshotNotification(username)
                }
            }
        }
    }



    private fun showMessageNotification(senderId: String, receiverId: String, channelName: String, username: String, messageText: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "message_notifications"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Message Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(this, MessageActivity::class.java).apply {
            putExtra("OTHER_USER_ID", senderId) // Open chat with the sender
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, System.currentTimeMillis().toInt(), intent, // Unique requestCode for each notification
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.logo1) // Replace with your message icon
            .setContentTitle("New Message from $username") // This should now show the sender's username
            .setContentText(messageText)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification) // Unique ID for each notification
    }



    private fun showFollowRequestNotification(senderId: String, receiverId: String, username: String) {
        Log.d("FCM", "Attempting to show follow request notification for $username, receiverId: $receiverId, currentUser: ${FirebaseAuth.getInstance().currentUser?.uid}")
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "follow_request_notifications"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Follow Request Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(this, Contacts::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, System.currentTimeMillis().toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.logo1)
            .setContentTitle("$username requested to follow you")
            .setContentText("Open Contacts to accept or decline.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
        Log.d("FCM", "Follow request notification posted for $username")
    }


    private fun showScreenshotNotification(username: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "screenshot_notifications"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Screenshot Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(this, MessageActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Screenshot Taken")
            .setContentText("$username took a screenshot of your chat")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(2, notification)
    }

    private fun showCallNotification(callerId: String, receiverId: String, channelName: String, username: String, profilePicBase64: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "call_notifications"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Call Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                enableVibration(true)
                setSound(android.provider.Settings.System.DEFAULT_RINGTONE_URI, null)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val acceptIntent = Intent(this, AudioChatActivity::class.java).apply {
            putExtra("USERNAME", username)
            putExtra("CHANNEL_NAME", channelName)
            putExtra("CALLER_ID", callerId)
            putExtra("RECEIVER_ID", receiverId)
            putExtra("PROFILE_PIC_BASE64", profilePicBase64)
            putExtra("IS_CALLER", false)
            action = "ACCEPT_CALL"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val acceptPendingIntent = PendingIntent.getActivity(
            this, 0, acceptIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val rejectIntent = Intent(this, AudioChatActivity::class.java).apply {
            putExtra("CALLER_ID", callerId)
            putExtra("RECEIVER_ID", receiverId)
            action = "REJECT_CALL"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val rejectPendingIntent = PendingIntent.getActivity(
            this, 1, rejectIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_call)
            .setContentTitle("Incoming Call")
            .setContentText("$username is calling you")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setAutoCancel(true)
            .addAction(R.drawable.ic_accept, "Accept", acceptPendingIntent)
            .addAction(R.drawable.ic_reject, "Reject", rejectPendingIntent)
            .setFullScreenIntent(acceptPendingIntent, true)
            .build()

        notificationManager.notify(1, notification)
    }


    private fun showVideoCallNotification(callerId: String, receiverId: String, channelName: String, username: String, profilePicBase64: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "call_notifications"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Call Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                enableVibration(true)
                setSound(android.provider.Settings.System.DEFAULT_RINGTONE_URI, null)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val acceptIntent = Intent(this, VideoChatActivity::class.java).apply {
            putExtra("USERNAME", username)
            putExtra("CHANNEL_NAME", channelName)
            putExtra("CALLER_ID", callerId)
            putExtra("RECEIVER_ID", receiverId)
            putExtra("PROFILE_PIC_BASE64", profilePicBase64)
            putExtra("IS_CALLER", false)
            action = "ACCEPT_CALL"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val acceptPendingIntent = PendingIntent.getActivity(
            this, 0, acceptIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val rejectIntent = Intent(this, VideoChatActivity::class.java).apply {
            putExtra("CALLER_ID", callerId)
            putExtra("RECEIVER_ID", receiverId)
            action = "REJECT_CALL"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val rejectPendingIntent = PendingIntent.getActivity(
            this, 1, rejectIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_call)
            .setContentTitle("Incoming Video Call")
            .setContentText("$username is video calling you")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setAutoCancel(true)
            .addAction(R.drawable.ic_accept, "Accept", acceptPendingIntent)
            .addAction(R.drawable.ic_reject, "Reject", rejectPendingIntent)
            .setFullScreenIntent(acceptPendingIntent, true)
            .build()

        notificationManager.notify(1, notification)
    }
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId != null) {
            FirebaseDatabase.getInstance().reference.child("Users").child(userId).child("fcmToken")
                .setValue(token)
                .addOnSuccessListener { Log.d("FCM", "Token updated: $token") }
                .addOnFailureListener { Log.e("FCM", "Token update failed: ${it.message}") }
        }
    }
}