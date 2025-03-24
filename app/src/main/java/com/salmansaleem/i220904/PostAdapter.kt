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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class PostAdapter(
    public val posts: MutableList<Pair<User, Post>> = mutableListOf()
) : RecyclerView.Adapter<PostAdapter.PostViewHolder>() {

    private val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    class PostViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val profilePic: ImageView = itemView.findViewById(R.id.profilePic)
        val username: TextView = itemView.findViewById(R.id.username)
        val postImage: ImageView = itemView.findViewById(R.id.imageView)
        val captionUsername: TextView = itemView.findViewById(R.id.name)
        val captionText: TextView = itemView.findViewById(R.id.captionText)
        val likeIcon: ImageView = itemView.findViewById(R.id.likeIcon)
        val commentIcon: ImageView = itemView.findViewById(R.id.commentIcon)
        val shareIcon: ImageView = itemView.findViewById(R.id.shareIcon)
        val saveIcon: ImageView = itemView.findViewById(R.id.saveIcon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_post_home, parent, false)
        return PostViewHolder(view)
    }

    override fun onBindViewHolder(holder: PostViewHolder, position: Int) {
        if (position < 0 || position >= posts.size) {
            return // Prevent IndexOutOfBoundsException
        }

        val (user, post) = posts[position]

        if (user.profileImageBase64.isNotEmpty()) {
            val imageBytes = Base64.decode(user.profileImageBase64, Base64.DEFAULT)
            Glide.with(holder.itemView.context)
                .load(imageBytes)
                .circleCrop()
                .into(holder.profilePic)
        }

        holder.username.text = user.username

        if (post.photoBase64.isNotEmpty()) {
            val postImageBytes = Base64.decode(post.photoBase64, Base64.DEFAULT)
            Glide.with(holder.itemView.context)
                .load(postImageBytes)
                .into(holder.postImage)
        }

        holder.captionUsername.text = user.username
        holder.captionText.text = post.caption

        updateLikeIcon(holder.likeIcon, post)

        holder.likeIcon.setOnClickListener {
            val currentPosition = holder.adapterPosition
            if (currentPosition != RecyclerView.NO_POSITION) {
                val (currentUser, currentPost) = posts[currentPosition]
                toggleLike(currentUser.uid, currentPost, currentPosition)
            }
        }

        holder.postImage.setOnClickListener(object : View.OnClickListener {
            private var lastClickTime: Long = 0
            private val doubleClickTimeDelta: Long = 300

            override fun onClick(v: View?) {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastClickTime < doubleClickTimeDelta) {
                    val currentPosition = holder.adapterPosition
                    if (currentPosition != RecyclerView.NO_POSITION) {
                        val (currentUser, currentPost) = posts[currentPosition]
                        if (!currentPost.likedBy.contains(currentUserId)) {
                            toggleLike(currentUser.uid, currentPost, currentPosition)
                        }
                    }
                }
                lastClickTime = currentTime
            }
        })

        holder.commentIcon.setOnClickListener {
            val currentPosition = holder.adapterPosition
            if (currentPosition != RecyclerView.NO_POSITION) {
                val (currentUser, currentPost) = posts[currentPosition]
                val intent = Intent(holder.itemView.context, CommentActivity::class.java).apply {
                    putExtra("USER_ID", currentUser.uid)
                    putExtra("POST_TIMESTAMP", currentPost.timestamp)
                }
                holder.itemView.context.startActivity(intent)
            }
        }
    }

    private fun updateLikeIcon(likeIcon: ImageView, post: Post) {
        if (post.likedBy.contains(currentUserId)) {
            likeIcon.setImageResource(R.drawable.heart) // Filled heart
        } else {
            likeIcon.setImageResource(R.drawable.heart1) // Empty heart
        }
    }

    private fun toggleLike(userId: String, post: Post, position: Int) {
        val database = FirebaseDatabase.getInstance().reference.child("Users").child(userId)
        database.get().addOnSuccessListener { snapshot ->
            val user = snapshot.getValue(User::class.java) ?: return@addOnSuccessListener
            val postIndex = user.posts.indexOfFirst { it.timestamp == post.timestamp }
            if (postIndex != -1) {
                val updatedPost = user.posts[postIndex]
                if (updatedPost.likedBy.contains(currentUserId)) {
                    updatedPost.likedBy.remove(currentUserId)
                    updatedPost.likeCount = maxOf(0, updatedPost.likeCount - 1)
                } else {
                    updatedPost.likedBy.add(currentUserId)
                    updatedPost.likeCount += 1
                }
                user.posts[postIndex] = updatedPost
                database.setValue(user).addOnSuccessListener {
                    val currentPosition = position
                    if (currentPosition >= 0 && currentPosition < posts.size) {
                        posts[currentPosition] = Pair(user, updatedPost)
                        notifyItemChanged(currentPosition)
                    }
                }
            }
        }
    }

    override fun getItemCount(): Int = posts.size

    fun addPost(user: User, post: Post) {
        posts.add(Pair(user, post)) // Add to the end of the list
        notifyItemInserted(posts.size - 1)
    }

    fun clearPosts() {
        posts.clear()
        notifyDataSetChanged()
    }
}