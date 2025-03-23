package com.salmansaleem.i220904.adapters
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
import com.google.firebase.auth.FirebaseAuth
import com.salmansaleem.i220904.Contacts
import com.salmansaleem.i220904.R
import com.salmansaleem.i220904.User

class UserInviteAdapter (private val userList : List<User>): RecyclerView.Adapter<UserInviteAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val profileImage: ImageView = itemView.findViewById(R.id.profile_image)
        val username: TextView = itemView.findViewById(R.id.username)
        val inviteButton: TextView = itemView.findViewById(R.id.invite_button)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_user_invite, parent, false)
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

        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        holder.inviteButton.text = if (user.requests.any { it.uid == currentUserId }) "Cancel" else "Invite"

        holder.inviteButton.setOnClickListener {
            (holder.itemView.context as Contacts).handleInviteRequest(user, holder.inviteButton)
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