package com.example.allinone.feature.instagram.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.example.allinone.R
import com.example.allinone.databinding.ItemChatAiBinding
import com.example.allinone.databinding.ItemChatUserBinding
import com.example.allinone.feature.instagram.data.model.ChatMessage
import java.text.SimpleDateFormat
import java.util.*

class ChatAdapter(
    private val messages: List<ChatMessage>
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    
    companion object {
        private const val VIEW_TYPE_USER = 1
        private const val VIEW_TYPE_AI = 2
    }
    
    override fun getItemViewType(position: Int): Int {
        return if (messages[position].isUser) VIEW_TYPE_USER else VIEW_TYPE_AI
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_USER -> {
                val binding = ItemChatUserBinding.inflate(
                    LayoutInflater.from(parent.context), 
                    parent, 
                    false
                )
                UserMessageViewHolder(binding)
            }
            else -> {
                val binding = ItemChatAiBinding.inflate(
                    LayoutInflater.from(parent.context), 
                    parent, 
                    false
                )
                AIMessageViewHolder(binding)
            }
        }
    }
    
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is UserMessageViewHolder -> holder.bind(messages[position])
            is AIMessageViewHolder -> holder.bind(messages[position])
        }
    }
    
    override fun getItemCount() = messages.size
    
    inner class UserMessageViewHolder(
        private val binding: ItemChatUserBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(message: ChatMessage) {
            binding.apply {
                textMessage.text = message.text
                textTime.text = formatTime(message.timestamp)
            }
        }
    }
    
    inner class AIMessageViewHolder(
        private val binding: ItemChatAiBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(message: ChatMessage) {
            binding.apply {
                textMessage.text = message.text
                textTime.text = formatTime(message.timestamp)
                
                // Show typing animation
                if (message.isTyping) {
                    progressTyping.isVisible = true
                    textMessage.text = "Analyzing your Instagram data..."
                } else {
                    progressTyping.isVisible = false
                }
                
                // Show confidence score
                if (message.confidence != null && message.confidence > 0) {
                    textConfidence.isVisible = true
                    textConfidence.text = "Confidence: ${(message.confidence * 100).toInt()}%"
                    
                    // Color code the confidence
                    val confidenceColor = when {
                        message.confidence >= 0.8 -> R.color.excellent_green
                        message.confidence >= 0.6 -> R.color.good_orange
                        else -> R.color.poor_red
                    }
                    textConfidence.setTextColor(
                        ContextCompat.getColor(itemView.context, confidenceColor)
                    )
                } else {
                    textConfidence.isVisible = false
                }
                
                // Show sources if available
                if (!message.sources.isNullOrEmpty()) {
                    textSources.isVisible = true
                    textSources.text = "Sources: ${message.sources.size} data points analyzed"
                } else {
                    textSources.isVisible = false
                }
                
                // Show error styling
                if (message.isError) {
                    cardMessage.setCardBackgroundColor(
                        ContextCompat.getColor(itemView.context, R.color.error_light)
                    )
                    textMessage.setTextColor(
                        ContextCompat.getColor(itemView.context, R.color.error_dark)
                    )
                } else {
                    cardMessage.setCardBackgroundColor(
                        ContextCompat.getColor(itemView.context, R.color.ai_message_bg)
                    )
                    textMessage.setTextColor(
                        ContextCompat.getColor(itemView.context, R.color.text_primary)
                    )
                }
            }
        }
    }
    
    private fun formatTime(timestamp: Long): String {
        val format = SimpleDateFormat("HH:mm", Locale.getDefault())
        return format.format(Date(timestamp))
    }
} 