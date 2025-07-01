package com.example.allinone.feature.instagram.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.allinone.databinding.ItemChatAiBinding
import com.example.allinone.databinding.ItemChatUserBinding
import com.example.allinone.feature.instagram.data.model.ChatMessage
import java.text.SimpleDateFormat
import java.util.*

class ChatAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    
    private var messages = listOf<ChatMessage>()
    
    companion object {
        private const val VIEW_TYPE_USER = 1
        private const val VIEW_TYPE_AI = 2
    }
    
    fun updateMessages(newMessages: List<ChatMessage>) {
        messages = newMessages
        notifyDataSetChanged()
    }
    
    override fun getItemViewType(position: Int): Int {
        return if (messages[position].isUser) VIEW_TYPE_USER else VIEW_TYPE_AI
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_USER -> {
                val binding = ItemChatUserBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
                UserMessageViewHolder(binding)
            }
            VIEW_TYPE_AI -> {
                val binding = ItemChatAiBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
                AIMessageViewHolder(binding)
            }
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }
    
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]
        when (holder) {
            is UserMessageViewHolder -> holder.bind(message)
            is AIMessageViewHolder -> holder.bind(message)
        }
    }
    
    override fun getItemCount() = messages.size
    
    inner class UserMessageViewHolder(
        private val binding: ItemChatUserBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(message: ChatMessage) {
            binding.apply {
                textUserMessage.text = message.text
                textUserTimestamp.text = formatTime(message.timestamp)
            }
        }
    }
    
    inner class AIMessageViewHolder(
        private val binding: ItemChatAiBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(message: ChatMessage) {
            binding.apply {
                textAIMessage.text = message.text
                textAITimestamp.text = formatTime(message.timestamp)
                
                // Show typing indicator for loading state
                layoutTyping.isVisible = message.isLoading
                cardAIMessage.isVisible = !message.isLoading
                
                if (!message.isLoading) {
                    // Confidence score
                    message.confidence?.let { confidence ->
                        layoutConfidence.isVisible = true
                        textConfidence.text = "${String.format("%.0f", confidence * 100)}%"
                    } ?: run {
                        layoutConfidence.isVisible = false
                    }
                    
                    // Sources
                    if (message.sources.isNotEmpty()) {
                        layoutSources.isVisible = true
                        setupSourcesRecycler(message.sources)
                    } else {
                        layoutSources.isVisible = false
                    }
                } else {
                    layoutConfidence.isVisible = false
                    layoutSources.isVisible = false
                }
            }
        }
        
        private fun setupSourcesRecycler(sources: List<com.example.allinone.feature.instagram.data.model.AISource>) {
            val sourcesAdapter = ChatSourcesAdapter()
            binding.recyclerSources.apply {
                adapter = sourcesAdapter
                layoutManager = LinearLayoutManager(context)
                isNestedScrollingEnabled = false
            }
            sourcesAdapter.updateSources(sources)
        }
    }
    
    private fun formatTime(timestamp: Long): String {
        val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        return dateFormat.format(Date(timestamp))
    }
}

class ChatSourcesAdapter : RecyclerView.Adapter<ChatSourcesAdapter.SourceViewHolder>() {
    
    private var sources = listOf<com.example.allinone.feature.instagram.data.model.AISource>()
    
    fun updateSources(newSources: List<com.example.allinone.feature.instagram.data.model.AISource>) {
        sources = newSources
        notifyDataSetChanged()
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SourceViewHolder {
        val binding = com.example.allinone.databinding.ItemChatSourceBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return SourceViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: SourceViewHolder, position: Int) {
        holder.bind(sources[position])
    }
    
    override fun getItemCount() = sources.size
    
    inner class SourceViewHolder(
        private val binding: com.example.allinone.databinding.ItemChatSourceBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(source: com.example.allinone.feature.instagram.data.model.AISource) {
            binding.apply {
                // Set emoji based on media type
                val emoji = when (source.metadata.mediaType) {
                    "VIDEO", "REELS" -> "🎬"
                    "CAROUSEL_ALBUM" -> "🖼️"
                    else -> "📝"
                }
                textSourceEmoji.text = emoji
                
                // Show post content snippet
                textSourcePost.text = source.content.take(50) + if (source.content.length > 50) "..." else ""
                
                // Show metrics
                val metrics = buildString {
                    source.metadata.likesCount?.let { append("❤️ $it ") }
                    source.metadata.commentsCount?.let { append("💬 $it ") }
                    source.metadata.engagementRate?.let { 
                        append("📈 ${String.format("%.1f", it)}%")
                    }
                }
                textSourceMetrics.text = metrics
                
                // Show relevance score
                textSourceScore.text = "${String.format("%.0f", source.score * 100)}%"
            }
        }
    }
} 