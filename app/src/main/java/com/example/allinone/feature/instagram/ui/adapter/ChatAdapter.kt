package com.example.allinone.feature.instagram.ui.adapter

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.allinone.databinding.ItemChatAiBinding
import com.example.allinone.databinding.ItemChatUserBinding
import com.example.allinone.feature.instagram.data.model.*
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
                // ✅ ENHANCED: Show content type indicator
                val contentTypeEmoji = when (message.contentType) {
                    ContentType.IMAGE -> "📱 "
                    ContentType.AUDIO -> "🎵 "
                    ContentType.PDF -> "📄 "
                    ContentType.URL -> "🔗 "
                    ContentType.MULTIMODAL -> "🎨 "
                    else -> ""
                }
                
                textUserMessage.text = "$contentTypeEmoji${message.text}"
                textUserTimestamp.text = formatTime(message.timestamp)
                
                // ✅ NEW: Show attachments if any
                if (message.attachments.isNotEmpty()) {
                    // For user messages, show attachment count and types
                    val attachmentInfo = getAttachmentSummary(message.attachments)
                    textUserMessage.text = "${textUserMessage.text}\n\n$attachmentInfo"
                }
                
                // ✅ NEW: Add copy functionality on long press
                setupCopyFunctionality(textUserMessage.text.toString(), "Your message")
            }
        }
        
        private fun getAttachmentSummary(attachments: List<MessageAttachment>): String {
            if (attachments.isEmpty()) return ""
            
            val types = attachments.groupBy { it.type }
            val summary = types.map { (type, items) ->
                val icon = when (type) {
                    AttachmentType.IMAGE -> "📱"
                    AttachmentType.AUDIO -> "🎵"
                    AttachmentType.PDF -> "📄"
                    AttachmentType.VIDEO -> "🎬"
                    AttachmentType.VOICE_RECORDING -> "🎤"
                }
                "$icon ${items.size} ${type.name.lowercase()}"
            }.joinToString(" • ")
            
            return "📎 $summary"
        }
        
        // ✅ NEW: Setup copy functionality for user messages
        private fun setupCopyFunctionality(text: String, messageType: String) {
            binding.root.setOnLongClickListener {
                copyMessageToClipboard(text, messageType)
                true
            }
            
            // Also add copy on text view long press
            binding.textUserMessage.setOnLongClickListener {
                copyMessageToClipboard(text, messageType)
                true
            }
        }
        
        private fun copyMessageToClipboard(text: String, messageType: String) {
            val context = binding.root.context
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText(messageType, text)
            clipboard.setPrimaryClip(clip)
            
            // Show feedback to user
            Toast.makeText(context, "✅ Message copied", Toast.LENGTH_SHORT).show()
        }
    }
    
    inner class AIMessageViewHolder(
        private val binding: ItemChatAiBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(message: ChatMessage) {
            binding.apply {
                textAIMessage.text = message.text
                textAITimestamp.text = formatTime(message.timestamp)
                
                // ✅ ENHANCED: Show typing indicator or loading state
                val isProcessing = message.isLoading || message.isTyping
                layoutTyping.isVisible = isProcessing
                cardAIMessage.isVisible = !isProcessing
                
                if (!isProcessing) {
                    // ✅ NEW: Show processing time if available
                    message.processingTime?.let { time ->
                        textAITimestamp.text = "${formatTime(message.timestamp)} • ${time}ms"
                    }
                    
                    // Confidence score with enhanced display
                    message.confidence?.let { confidence ->
                        layoutConfidence.isVisible = true
                        textConfidence.text = "${String.format("%.0f", confidence * 100)}%"
                        
                        // ✅ NEW: Color code confidence levels
                        val confidenceColor = when {
                            confidence >= 0.9 -> android.R.color.holo_green_dark
                            confidence >= 0.7 -> android.R.color.holo_orange_dark
                            else -> android.R.color.holo_red_dark
                        }
                        textConfidence.setTextColor(
                            textConfidence.context.getColor(confidenceColor)
                        )
                    } ?: run {
                        layoutConfidence.isVisible = false
                    }
                    
                    // ✅ ENHANCED: Sources with better formatting
                    if (message.sources.isNotEmpty()) {
                        layoutSources.isVisible = true
                        setupSourcesRecycler(message.sources)
                    } else {
                        layoutSources.isVisible = false
                    }
                    
                    // ✅ NEW: Show content type analysis indicator
                    if (message.text.contains("image") || message.text.contains("audio") || 
                        message.text.contains("PDF") || message.text.contains("URL")) {
                        // Add subtle indicator for multimodal analysis
                        textAIMessage.text = "🎯 ${message.text}"
                    }
                    
                    // ✅ NEW: Add copy functionality for AI messages
                    setupAICopyFunctionality(message)
                } else {
                    layoutConfidence.isVisible = false
                    layoutSources.isVisible = false
                    
                    // ✅ ENHANCED: Better loading states
                    if (message.isTyping) {
                        // Show typing animation or enhanced loading text
                        textAIMessage.text = "Analyzing your content..."
                    }
                }
            }
        }
        
        // ✅ NEW: Setup copy functionality for AI messages with enhanced options
        private fun setupAICopyFunctionality(message: ChatMessage) {
            binding.apply {
                // Copy full AI response on long press
                root.setOnLongClickListener {
                    showAICopyOptions(message)
                    true
                }
                
                // Copy text on AI message long press
                textAIMessage.setOnLongClickListener {
                    copyAIMessageToClipboard(message.text, "AI Response")
                    true
                }
                
                // ✅ NEW: Copy individual sources on long press
                if (message.sources.isNotEmpty()) {
                    layoutSources.setOnLongClickListener {
                        copySourcesInfoToClipboard(message.sources)
                        true
                    }
                }
            }
        }
        
        private fun showAICopyOptions(message: ChatMessage) {
            // Build copyable content options
            val fullResponse = buildString {
                append("🤖 AI Response:\n")
                append(message.text)
                
                if (message.confidence != null) {
                    append("\n\n📊 Confidence: ${String.format("%.0f", message.confidence * 100)}%")
                }
                
                if (message.sources.isNotEmpty()) {
                    append("\n\n📚 Sources (${message.sources.size}):")
                    message.sources.forEachIndexed { index, source ->
                        append("\n${index + 1}. ${source.content.take(100)}...")
                        source.metadata.engagementRate?.let { rate ->
                            append(" (${String.format("%.1f", rate)}% engagement)")
                        }
                    }
                }
                
                message.processingTime?.let { time ->
                    append("\n\n⏱️ Processing time: ${time}ms")
                }
            }
            
            copyAIMessageToClipboard(fullResponse, "Complete AI Response")
        }
        
        private fun copyAIMessageToClipboard(text: String, messageType: String) {
            val context = binding.root.context
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText(messageType, text)
            clipboard.setPrimaryClip(clip)
            
            // Show enhanced feedback
            Toast.makeText(context, "📋 $messageType copied", Toast.LENGTH_SHORT).show()
        }
        
        private fun copySourcesInfoToClipboard(sources: List<AISource>) {
            val sourcesText = buildString {
                append("📚 Instagram AI Sources (${sources.size}):\n\n")
                sources.forEachIndexed { index, source ->
                    append("${index + 1}. ")
                    append("📊 Relevance: ${String.format("%.0f", source.score * 100)}%\n")
                    append("📝 Content: ${source.content}\n")
                    
                    source.metadata.let { meta ->
                        meta.likesCount?.let { append("❤️ Likes: $it ") }
                        meta.commentsCount?.let { append("💬 Comments: $it ") }
                        meta.engagementRate?.let { append("📈 Engagement: ${String.format("%.1f", it)}%") }
                    }
                    
                    if (index < sources.size - 1) append("\n\n")
                }
            }
            
            val context = binding.root.context
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Instagram AI Sources", sourcesText)
            clipboard.setPrimaryClip(clip)
            
            Toast.makeText(context, "📚 Sources information copied", Toast.LENGTH_SHORT).show()
        }
        
        private fun setupSourcesRecycler(sources: List<AISource>) {
            val sourcesAdapter = ChatSourcesAdapter { source ->
                // ✅ NEW: Copy individual source on click
                copyIndividualSource(source)
            }
            binding.recyclerSources.apply {
                adapter = sourcesAdapter
                layoutManager = LinearLayoutManager(context)
                isNestedScrollingEnabled = false
            }
            sourcesAdapter.updateSources(sources)
        }
        
        private fun copyIndividualSource(source: AISource) {
            val sourceText = buildString {
                append("📝 Instagram Post Analysis:\n\n")
                append("Content: ${source.content}\n\n")
                append("📊 Relevance Score: ${String.format("%.0f", source.score * 100)}%\n")
                
                source.metadata.let { meta ->
                    append("📈 Metrics:\n")
                    meta.likesCount?.let { append("❤️ Likes: $it\n") }
                    meta.commentsCount?.let { append("💬 Comments: $it\n") }
                    meta.engagementRate?.let { append("📈 Engagement Rate: ${String.format("%.1f", it)}%\n") }
                    meta.mediaType?.let { append("🎬 Media Type: $it\n") }
                    meta.hashtags?.let { tags ->
                        if (tags.isNotEmpty()) {
                            append("🏷️ Hashtags: ${tags.joinToString(" ")}")
                        }
                    }
                }
            }
            
            val context = binding.root.context
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Instagram Post Analysis", sourceText)
            clipboard.setPrimaryClip(clip)
            
            Toast.makeText(context, "📝 Post analysis copied", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun formatTime(timestamp: Long): String {
        val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        return dateFormat.format(Date(timestamp))
    }
}

/**
 * ✅ ENHANCED: ChatSourcesAdapter with copy functionality
 */
class ChatSourcesAdapter(
    private val onSourceClick: ((AISource) -> Unit)? = null
) : RecyclerView.Adapter<ChatSourcesAdapter.SourceViewHolder>() {
    
    private var sources = listOf<AISource>()
    
    fun updateSources(newSources: List<AISource>) {
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
        
        fun bind(source: AISource) {
            binding.apply {
                // ✅ ENHANCED: Better emoji selection based on content type
                val emoji = when {
                    source.metadata.mediaType?.contains("VIDEO") == true -> "🎬"
                    source.metadata.mediaType?.contains("REELS") == true -> "🎬"
                    source.metadata.mediaType?.contains("CAROUSEL") == true -> "🖼️"
                    source.metadata.mediaType?.contains("IMAGE") == true -> "📷"
                    source.content.contains("hashtag") -> "#️⃣"
                    source.content.contains("engagement") -> "📈"
                    source.content.contains("strategy") -> "🎯"
                    else -> "📝"
                }
                textSourceEmoji.text = emoji
                
                // ✅ ENHANCED: Better content preview
                val preview = when {
                    source.content.length > 80 -> source.content.take(80) + "..."
                    else -> source.content
                }
                textSourcePost.text = preview
                
                // ✅ ENHANCED: Rich metrics display
                val metrics = buildString {
                    source.metadata.likesCount?.let { likes ->
                        append("❤️ ${formatNumber(likes)} ")
                    }
                    source.metadata.commentsCount?.let { comments ->
                        append("💬 ${formatNumber(comments)} ")
                    }
                    source.metadata.engagementRate?.let { rate ->
                        append("📈 ${String.format("%.1f", rate)}%")
                    }
                }
                textSourceMetrics.text = metrics.ifBlank { "📊 Post data" }
                
                // ✅ ENHANCED: Better relevance score display
                val relevanceScore = String.format("%.0f", source.score * 100)
                textSourceScore.text = "$relevanceScore%"
                
                // ✅ NEW: Color code relevance scores
                val scoreColor = when {
                    source.score >= 0.9 -> android.R.color.holo_green_dark
                    source.score >= 0.7 -> android.R.color.holo_orange_dark
                    else -> android.R.color.holo_red_dark
                }
                textSourceScore.setTextColor(
                    textSourceScore.context.getColor(scoreColor)
                )
                
                // ✅ NEW: Add click listener for copying source
                root.setOnClickListener {
                    onSourceClick?.invoke(source)
                }
                
                // ✅ NEW: Add visual feedback for clickable items
                root.isClickable = true
                root.isFocusable = true
            }
        }
        
        private fun formatNumber(number: Int): String {
            return when {
                number >= 1000000 -> "${String.format("%.1f", number / 1000000.0)}M"
                number >= 1000 -> "${String.format("%.1f", number / 1000.0)}K"
                else -> number.toString()
            }
        }
    }
} 