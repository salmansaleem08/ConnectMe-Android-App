package com.salmansaleem.i220904

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import android.util.Base64

class DMFollower_Adapter(
    private val followers: MutableList<User> = mutableListOf()
) : RecyclerView.Adapter<DMFollower_Adapter.FollowerViewHolder>() {

    class FollowerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val profilePic: ImageView = itemView.findViewById(R.id.followerProfilePic)
        val name: TextView = itemView.findViewById(R.id.followerName)
        val cameraIcon: ImageView = itemView.findViewById(R.id.cameraIcon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FollowerViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_dm_follower, parent, false)
        return FollowerViewHolder(view)
    }

    override fun onBindViewHolder(holder: FollowerViewHolder, position: Int) {
        val follower = followers[position]

        // Load profile picture
        if (follower.profileImageBase64.isNotEmpty()) {
            val imageBytes = Base64.decode(follower.profileImageBase64, Base64.DEFAULT)
            Glide.with(holder.itemView.context)
                .load(imageBytes)
                .circleCrop()
                .into(holder.profilePic)
        }

        // Set follower name
        holder.name.text = follower.username
        holder.itemView.setOnClickListener {
            val intent = Intent(holder.itemView.context, MessageActivity::class.java).apply {
                putExtra("OTHER_USER_ID", follower.uid)
            }
            holder.itemView.context.startActivity(intent)
        }
    }

    override fun getItemCount(): Int = followers.size

    fun setFollowers(newFollowers: List<User>) {
        followers.clear()
        followers.addAll(newFollowers)
        notifyDataSetChanged()
    }

    fun addFollower(follower: User) {
        followers.add(follower)
        notifyItemInserted(followers.size - 1)
    }
}