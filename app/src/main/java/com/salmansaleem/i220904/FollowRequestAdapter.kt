package com.salmansaleem.i220904
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.util.Base64
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.salmansaleem.i220904.Follower
import com.salmansaleem.i220904.R
import com.salmansaleem.i220904.User

class FollowRequestAdapter (private val userList : List<User>): RecyclerView.Adapter<FollowRequestAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val profileImage: ImageView = itemView.findViewById(R.id.profile_image)
        val username: TextView = itemView.findViewById(R.id.username)
        val acceptButton: TextView = itemView.findViewById(R.id.acceptButton)
        val rejectButton: TextView = itemView.findViewById(R.id.rejectButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_follow_request, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val user = userList[position]
        holder.username.text = user.username

        try {
            if (user.profileImageBase64.isNotEmpty()) {
                val decodedBytes = Base64.decode(user.profileImageBase64, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                val circularBitmap = getCircularBitmap(bitmap)
                holder.profileImage.setImageBitmap(circularBitmap)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            holder.profileImage.setImageResource(R.drawable.default_profile)
        }

        holder.acceptButton.setOnClickListener {
            acceptRequest(user, holder.itemView.context)
        }

        // Handle reject button
        holder.rejectButton.setOnClickListener {
            rejectRequest(user, holder.itemView.context)
        }

    }


    override fun getItemCount(): Int = userList.size


    private fun getCircularBitmap(bitmap: Bitmap): Bitmap {
        val size = Math.min(bitmap.width, bitmap.height)
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint()
        val rect = android.graphics.Rect(0, 0, size, size)

        paint.isAntiAlias = true
        canvas.drawARGB(0, 0, 0, 0)
        paint.color = android.graphics.Color.WHITE

        val radius = size / 2f
        canvas.drawCircle(radius, radius, radius, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(bitmap, rect, rect, paint)

        return output
    }


    private fun acceptRequest(requestUser: User, context: Context) {
        val database = FirebaseDatabase.getInstance().reference.child("Users")
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        database.child(currentUserId).get().addOnSuccessListener { snapshot ->
            val currentUser = snapshot.getValue(User::class.java)
            currentUser?.let { currUser ->
                // Update current user's followers
                val updatedFollowers = currUser.followers.toMutableList()
                val follower = Follower(requestUser.username, requestUser.profileImageBase64)
                if (!updatedFollowers.contains(follower)) {
                    updatedFollowers.add(follower)
                }

                // Remove from requests
                val updatedRequests = currUser.requests.toMutableList()
                updatedRequests.remove(requestUser)

                // Update database
                database.child(currentUserId).child("followers").setValue(updatedFollowers)
                database.child(currentUserId).child("requests").setValue(updatedRequests)
                    .addOnSuccessListener {
                        Log.d("FollowRequest", "Request accepted successfully")
                        // Optionally update UI here or rely on ValueEventListener
                    }
                    .addOnFailureListener { error ->
                        Log.e("FollowRequest", "Failed to accept request: ${error.message}")
                    }

                // Update requesting user's following list
                database.child(requestUser.uid).get().addOnSuccessListener { userSnapshot ->
                    val requestingUser = userSnapshot.getValue(User::class.java)
                    requestingUser?.let { reqUser ->
                        val updatedFollowing = reqUser.following.toMutableList()
                        val currentFollower = Follower(currUser.username, currUser.profileImageBase64)
                        if (!updatedFollowing.contains(currentFollower)) {
                            updatedFollowing.add(currentFollower)
                            database.child(requestUser.uid).child("following")
                                .setValue(updatedFollowing)
                        }
                    }
                }
            }
        }
    }

    private fun rejectRequest(requestUser: User, context: Context) {
        val database = FirebaseDatabase.getInstance().reference.child("Users")
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        database.child(currentUserId).get().addOnSuccessListener { snapshot ->
            val currentUser = snapshot.getValue(User::class.java)
            currentUser?.let { currUser ->
                // Remove from requests
                val updatedRequests = currUser.requests.toMutableList()
                updatedRequests.remove(requestUser)

                // Update database
                database.child(currentUserId).child("requests").setValue(updatedRequests)
                    .addOnSuccessListener {
                        Log.d("FollowRequest", "Request rejected successfully")
                        // Optionally update UI here or rely on ValueEventListener
                    }
                    .addOnFailureListener { error ->
                        Log.e("FollowRequest", "Failed to reject request: ${error.message}")
                    }
            }
        }
    }
}