package com.salmansaleem.i220904.adapters

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
import com.salmansaleem.i220904.R
import com.salmansaleem.i220904.User

class FollowersAdapter(private val userList: List<User>) : RecyclerView.Adapter<FollowersAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val profileImage: ImageView = itemView.findViewById(R.id.profile_image)
        val username: TextView = itemView.findViewById(R.id.username)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_follower, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val user = userList[position]
        holder.username.text = user.username
        Log.d("FollowersAdapter", "Binding position $position: ${user.username}, Base64 length: ${user.profileImageBase64.length}")

        if (user.profileImageBase64.isNotEmpty()) {
            try {
                val decodedBytes = Base64.decode(user.profileImageBase64, Base64.DEFAULT)
                Log.d("FollowersAdapter", "Decoded bytes length: ${decodedBytes.size}")
                val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                Log.d("FollowersAdapter", "Bitmap created: ${bitmap.width}x${bitmap.height}")
                val circularBitmap = getCircularBitmap(bitmap)
                holder.profileImage.setImageBitmap(circularBitmap)
                Log.d("FollowersAdapter", "Image set for ${user.username}")
            } catch (e: Exception) {
                Log.e("FollowersAdapter", "Error decoding profile image: ${e.message}")
                holder.profileImage.setImageResource(R.drawable.default_profile)
            }
        } else {
            Log.d("FollowersAdapter", "No Base64 for ${user.username}, using default")
            holder.profileImage.setImageResource(R.drawable.default_profile)
        }
    }

    override fun getItemCount(): Int = userList.size

    private fun getCircularBitmap(bitmap: Bitmap): Bitmap {
        val size = minOf(bitmap.width, bitmap.height)
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
}