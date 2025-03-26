package com.salmansaleem.i220904

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import android.util.Base64
import java.text.SimpleDateFormat
import java.util.*

class MessageAdapter(
    private var messages: MutableList<Message> = mutableListOf(),
    private val currentUserId: String,
    private val onMessageClick: (Message, Int) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_SENT = 1
        private const val VIEW_TYPE_RECEIVED = 2
        private const val VIEW_TYPE_NOTIFICATION = 3
    }

    class SentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val text: TextView = itemView.findViewById(R.id.messageText)
        val image: ImageView = itemView.findViewById(R.id.messageImage)
        val postReference: TextView = itemView.findViewById(R.id.postReference)
        val timestamp: TextView = itemView.findViewById(R.id.timestamp)
    }

    class ReceivedViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val text: TextView = itemView.findViewById(R.id.messageText)
        val image: ImageView = itemView.findViewById(R.id.messageImage)
        val postReference: TextView = itemView.findViewById(R.id.postReference)
        val timestamp: TextView = itemView.findViewById(R.id.timestamp)
    }

    class NotificationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val text: TextView = itemView.findViewById(R.id.notificationText)
    }

    override fun getItemViewType(position: Int): Int {
        val message = messages[position]
        return when {
            message.text in listOf("VANISH_MODE_ENABLED", "SCREENSHOT_TAKEN") -> VIEW_TYPE_NOTIFICATION
            message.senderId == currentUserId -> VIEW_TYPE_SENT
            else -> VIEW_TYPE_RECEIVED
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_SENT -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_message_sent, parent, false)
                SentViewHolder(view)
            }
            VIEW_TYPE_RECEIVED -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_message_received, parent, false)
                ReceivedViewHolder(view)
            }
            VIEW_TYPE_NOTIFICATION -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_message_notification, parent, false)
                NotificationViewHolder(view)
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val timestampText = timeFormat.format(Date(message.timestamp))

        when (holder) {
            is SentViewHolder -> {
                bindMessage(holder, message, timestampText)
                holder.itemView.setOnClickListener {
                    onMessageClick(message, holder.adapterPosition)
                }
            }
            is ReceivedViewHolder -> {
                bindMessage(holder, message, timestampText)
                holder.itemView.setOnClickListener {
                    onMessageClick(message, holder.adapterPosition)
                }
            }
            is NotificationViewHolder -> {
                holder.text.text = when (message.text) {
                    "VANISH_MODE_ENABLED" -> "Vanish mode enabled. Messages will disappear when chat closes."
                    "SCREENSHOT_TAKEN" -> "A screenshot was taken!"
                    else -> ""
                }
            }
        }
    }

    private fun bindMessage(holder: RecyclerView.ViewHolder, message: Message, timestampText: String) {
        when (holder) {
            is SentViewHolder -> {
                holder.text.visibility = if (message.text != null) View.VISIBLE else View.GONE
                holder.image.visibility = if (message.imageBase64 != null) View.VISIBLE else View.GONE
                holder.postReference.visibility = if (message.postId != null) View.VISIBLE else View.GONE
                holder.text.text = message.text ?: ""
                if (message.imageBase64 != null) {
                    val imageBytes = Base64.decode(message.imageBase64, Base64.DEFAULT)
                    Glide.with(holder.itemView.context).load(imageBytes).into(holder.image)
                }
                holder.timestamp.text = timestampText
            }
            is ReceivedViewHolder -> {
                holder.text.visibility = if (message.text != null) View.VISIBLE else View.GONE
                holder.image.visibility = if (message.imageBase64 != null) View.VISIBLE else View.GONE
                holder.postReference.visibility = if (message.postId != null) View.VISIBLE else View.GONE
                holder.text.text = message.text ?: ""
                if (message.imageBase64 != null) {
                    val imageBytes = Base64.decode(message.imageBase64, Base64.DEFAULT)
                    Glide.with(holder.itemView.context).load(imageBytes).into(holder.image)
                }
                holder.timestamp.text = timestampText
            }
        }
    }

    override fun getItemCount(): Int = messages.size

    fun updateMessages(newMessages: List<Message>) {
        messages.clear()
        messages.addAll(newMessages)
        notifyDataSetChanged()
    }

    fun updateMessage(position: Int, updatedMessage: Message) {
        messages[position] = updatedMessage
        notifyItemChanged(position)
    }

    fun removeMessage(position: Int) {
        messages.removeAt(position)
        notifyItemRemoved(position)
    }
}