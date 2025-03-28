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
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.salmansaleem.i220904.adapters.FollowersAdapter
import com.salmansaleem.i220904.adapters.UserInviteAdapter
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm

import java.security.spec.PKCS8EncodedKeySpec
class Contacts : AppCompatActivity() {


    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: UserInviteAdapter
    private val userList = mutableListOf<User>()


    private lateinit var recyclerView1: RecyclerView
    private lateinit var adapter1: FollowersAdapter
    private val FollowersList = mutableListOf<User>()


    private lateinit var recyclerView2: RecyclerView
    private lateinit var adapter2: FollowRequestAdapter
    private val RequestList = mutableListOf<User>()



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_contacts)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        recyclerView = findViewById(R.id.recycler_users1)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = UserInviteAdapter(userList)
        recyclerView.adapter = adapter


        fetchUsersFromDatabase()



        recyclerView1 = findViewById(R.id.recycler_followers)
        recyclerView1.layoutManager = LinearLayoutManager(this)
        adapter1 = FollowersAdapter(FollowersList)
        recyclerView1.adapter = adapter1
        fetchFollowersFromDatabase()


        recyclerView2 = findViewById(R.id.recycler_follow_Request)
        recyclerView2.layoutManager = LinearLayoutManager(this)
        adapter2 = FollowRequestAdapter(RequestList)
        recyclerView2.adapter = adapter2
        fetchRequestsFromDatabase()


        findViewById<ImageView>(R.id.search).setOnClickListener {
            startActivity(Intent(this, Search::class.java))
        }

        findViewById<ImageView>(R.id.add).setOnClickListener {
            startActivity(Intent(this, StoryActivity::class.java))
        }

        findViewById<ImageView>(R.id.myProfile).setOnClickListener {
            startActivity(Intent(this, MyProfile::class.java))
        }

        findViewById<ImageView>(R.id.home).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }

        findViewById<ImageView>(R.id.contacts).setOnClickListener {
            startActivity(Intent(this, Contacts::class.java))
        }


    }


    private fun fetchUsersFromDatabase() {
        val database = FirebaseDatabase.getInstance().reference.child("Users")
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        // First get current user's following list
        database.child(currentUserId).get().addOnSuccessListener { currentUserSnapshot ->
            val currentUser = currentUserSnapshot.getValue(User::class.java)
            val followingUsernames = currentUser?.following?.map { it.username }?.toSet() ?: emptySet()

            // Then fetch all users
            database.addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    userList.clear()
                    for (userSnapshot in snapshot.children) {
                        val user = userSnapshot.getValue(User::class.java)
                        user?.let {
                            if (it.uid != currentUserId && it.username !in followingUsernames) {
                                userList.add(it)
                            }
                        }
                    }
                    adapter.notifyDataSetChanged()
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("Contacts", "Database error: ${error.message}")
                }
            })
        }
    }


    private fun fetchFollowersFromDatabase() {
        val database = FirebaseDatabase.getInstance().reference.child("Users")
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        database.child(currentUserId).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                FollowersList.clear()
                val currentUser = snapshot.getValue(User::class.java)

                if (currentUser?.followers.isNullOrEmpty()) {
                    adapter1.notifyDataSetChanged()
                    return
                }

                // Fetch all users once
                database.get().addOnSuccessListener { allUsersSnapshot ->
                    val allUsers = allUsersSnapshot.children.mapNotNull { it.getValue(User::class.java) }
                    currentUser?.followers?.forEach { follower ->
                        allUsers.find { it.username == follower.username }?.let { followerUser ->
                            if (!FollowersList.contains(followerUser)) {
                                FollowersList.add(followerUser)
                            }
                        }
                    }
                    adapter1.notifyDataSetChanged()
                }.addOnFailureListener { error ->
                    Log.e("Contacts", "Failed to fetch all users: ${error.message}")
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("Contacts", "Database error: ${error.message}")
            }
        })
    }



    public fun handleInviteRequest(targetUser: User, inviteButton: TextView) {
        val database = FirebaseDatabase.getInstance().reference.child("Users")
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        // Get current user's data
        database.child(currentUserId).get().addOnSuccessListener { snapshot ->
            val currentUser = snapshot.getValue(User::class.java)
            currentUser?.let { currUser ->
                if (inviteButton.text == "Invite") {
                    // Add current user to target user's requests list
                    val updatedRequests = targetUser.requests.toMutableList()
                    if (!updatedRequests.contains(currUser)) {
                        updatedRequests.add(currUser)
                        database.child(targetUser.uid).child("requests").setValue(updatedRequests)
                            .addOnSuccessListener {
                                inviteButton.text = "Cancel"
                                Log.d("Contacts", "Invite request sent successfully")
                                // Send FCM notification to target user
                                sendInviteNotification(targetUser.uid, currUser.username)
                            }
                            .addOnFailureListener { error ->
                                Log.e("Contacts", "Failed to send invite: ${error.message}")
                            }
                    }
                } else {
                    // Remove current user from target user's requests list
                    val updatedRequests = targetUser.requests.toMutableList()
                    updatedRequests.remove(currUser)
                    database.child(targetUser.uid).child("requests").setValue(updatedRequests)
                        .addOnSuccessListener {
                            inviteButton.text = "Invite"
                            Log.d("Contacts", "Invite request cancelled successfully")
                        }
                        .addOnFailureListener { error ->
                            Log.e("Contacts", "Failed to cancel invite: ${error.message}")
                        }
                }
            }
        }.addOnFailureListener { error ->
            Log.e("Contacts", "Failed to get current user: ${error.message}")
        }
    }

    private fun sendInviteNotification(receiverId: String, senderUsername: String) {
        val database = FirebaseDatabase.getInstance().reference.child("Users").child(receiverId).child("fcmToken")
        val client = OkHttpClient()

        database.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val receiverToken = snapshot.getValue(String::class.java)
                if (receiverToken.isNullOrEmpty()) {
                    Log.e("FCM", "No valid FCM token for $receiverId")
                    return
                }
                Thread {
                    try {
                        val accessToken = getAccessToken() // Reuse your existing getAccessToken function
                        if (accessToken.isEmpty()) {
                            Log.e("FCM", "Failed to obtain access token")
                            return@Thread
                        }

                        val projectId = "assignment2db1" // Replace with your Firebase Project ID
                        val url = "https://fcm.googleapis.com/v1/projects/$projectId/messages:send"
                        val json = JSONObject().apply {
                            put("message", JSONObject().apply {
                                put("token", receiverToken)
                                put("data", JSONObject().apply {
                                    put("type", "follow_request")
                                    put("senderId", FirebaseAuth.getInstance().currentUser?.uid)
                                    put("receiverId", receiverId)
                                    put("username", senderUsername)
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
                                Log.d("FCM", "Invite notification sent successfully to $receiverId")
                            } else {
                                Log.e("FCM", "FCM v1 failed: ${response.code} - ${response.body?.string()}")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("FCM", "Error sending FCM v1: ${e.message}", e)
                    }
                }.start()
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("FCM", "Token fetch failed: ${error.message}")
            }
        })
    }

    // Reuse your existing getAccessToken function from MessageActivity
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

            val keyBytes = Base64.decode(privateKeyPem, Base64.DEFAULT)
            val keySpec = PKCS8EncodedKeySpec(keyBytes)
            val keyFactory = KeyFactory.getInstance("RSA")
            val privateKey = keyFactory.generatePrivate(keySpec)

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

            val requestBody = "grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=$jwt"
                .toRequestBody("application/x-www-form-urlencoded".toMediaType())
            val request = Request.Builder()
                .url(tokenUri)
                .post(requestBody)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .build()

            OkHttpClient().newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: "{}"
                if (response.isSuccessful) {
                    val responseJson = JSONObject(responseBody)
                    return responseJson.getString("access_token")
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





    private fun fetchRequestsFromDatabase() {
        val database = FirebaseDatabase.getInstance().reference.child("Users")
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return



        database.child(currentUserId).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                RequestList.clear()
                val currentUser = snapshot.getValue(User::class.java)

                currentUser?.requests?.forEach { requestUser ->
                    // Since requests already contains User objects, no need for additional fetch
                    if (!RequestList.contains(requestUser)) {
                        RequestList.add(requestUser)
                    }
                    adapter2.notifyDataSetChanged()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("Contacts", "Database error: ${error.message}")
            }
        })
    }






}