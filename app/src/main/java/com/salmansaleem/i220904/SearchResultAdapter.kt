package com.salmansaleem.i220904

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.salmansaleem.i220904.R
import com.salmansaleem.i220904.User

class SearchResultAdapter(private val userList: List<User>) :
    RecyclerView.Adapter<SearchResultAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val profileImage: ImageView = itemView.findViewById(R.id.profile_image)
        val username: TextView = itemView.findViewById(R.id.username)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_search_result, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val user = userList[position]
        holder.username.text = user.username

        if (user.profileImageBase64.isNotEmpty()) {
            try {
                val decodedBytes = Base64.decode(user.profileImageBase64, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                val circularBitmap = getCircularBitmap(bitmap)
                holder.profileImage.setImageBitmap(circularBitmap)
            } catch (e: Exception) {
                holder.profileImage.setImageResource(R.drawable.default_profile)
            }
        } else {
            holder.profileImage.setImageResource(R.drawable.default_profile) // Set default if no image
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
}