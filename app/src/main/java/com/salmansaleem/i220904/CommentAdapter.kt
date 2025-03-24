package com.salmansaleem.i220904

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import android.util.Base64

class CommentAdapter(
    private val comments: MutableList<Pair<User, Comment>> = mutableListOf()
) : RecyclerView.Adapter<CommentAdapter.CommentViewHolder>() {

    class CommentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val profilePic: ImageView = itemView.findViewById(R.id.commentProfilePic)
        val username: TextView = itemView.findViewById(R.id.commentUsername)
        val commentText: TextView = itemView.findViewById(R.id.commentText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_comment, parent, false)
        return CommentViewHolder(view)
    }

    override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
        val (user, comment) = comments[position]

        // Load profile picture
        if (user.profileImageBase64.isNotEmpty()) {
            val imageBytes = Base64.decode(user.profileImageBase64, Base64.DEFAULT)
            Glide.with(holder.itemView.context)
                .load(imageBytes)
                .circleCrop()
                .into(holder.profilePic)
        }

        holder.username.text = user.username
        holder.commentText.text = comment.text
    }

    override fun getItemCount(): Int = comments.size

    fun addComment(user: User, comment: Comment) {
        comments.add(Pair(user, comment))
        notifyItemInserted(comments.size - 1)
    }

    fun setComments(newComments: List<Pair<User, Comment>>) {
        comments.clear()
        comments.addAll(newComments)
        notifyDataSetChanged()
    }
}