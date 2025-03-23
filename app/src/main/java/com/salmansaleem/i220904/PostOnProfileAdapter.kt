package com.salmansaleem.i220904

import android.graphics.BitmapFactory
import android.util.Base64
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView

class PostOnProfileAdapter(private val posts: List<Post>) : RecyclerView.Adapter<PostOnProfileAdapter.PostViewHolder>() {

    class PostViewHolder(val imageView: ImageView) : RecyclerView.ViewHolder(imageView)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewHolder {
        val imageView = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_post_profile, parent, false) as ImageView
        return PostViewHolder(imageView)
    }

    override fun onBindViewHolder(holder: PostViewHolder, position: Int) {
        val post = posts[position]
        val decodedBytes = Base64.decode(post.photoBase64, Base64.DEFAULT)
        val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
        holder.imageView.setImageBitmap(bitmap)
    }

    override fun getItemCount(): Int = posts.size
}