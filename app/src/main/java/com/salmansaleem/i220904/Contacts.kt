package com.salmansaleem.i220904

import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.salmansaleem.i220904.adapters.UserInviteAdapter
import com.salmansaleem.i220904.adapters.FollowersAdapter

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