package com.example.allinone.feature.instagram.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.allinone.R
import com.example.allinone.databinding.ItemInstagramPostNewBinding
import com.example.allinone.feature.instagram.data.model.InstagramPost
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class PostsAdapter(
    private val onPostClick: (InstagramPost) -> Unit
) : RecyclerView.Adapter<PostsAdapter.PostViewHolder>() {
    
    private var posts = listOf<InstagramPost>()
    
    fun updatePosts(newPosts: List<InstagramPost>) {
        posts = newPosts
        notifyDataSetChanged()
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewHolder {
        val binding = ItemInstagramPostNewBinding.inflate(
            LayoutInflater.from(parent.context), 
            parent, 
            false
        )
        return PostViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: PostViewHolder, position: Int) {
        holder.bind(posts[position])
    }
    
    override fun getItemCount() = posts.size
    
    inner class PostViewHolder(private val binding: ItemInstagramPostNewBinding) : 
        RecyclerView.ViewHolder(binding.root) {
        
        fun bind(post: InstagramPost) {
            binding.apply {
                // Post content
                textCaption.text = if (post.caption.length > 100) {
                    "${post.caption.take(100)}..."
                } else post.caption
                
                textDate.text = formatDate(post.timestamp)
                textMediaType.text = post.mediaType
                
                // Metrics with clear labels
                textLikes.text = "❤️ ${formatNumber(post.metrics.likesCount)} Likes"
                textComments.text = "💬 ${formatNumber(post.metrics.commentsCount)} Comments"
                textReach.text = "👥 ${formatNumber(post.metrics.reachCount ?: 0)} Reach"
                textEngagement.text = "📈 ${String.format("%.1f", post.metrics.engagementRate)}% Engagement"
                
                // Engagement rate color coding
                val engagementColor = when {
                    post.metrics.engagementRate >= 5.0 -> R.color.excellent_green
                    post.metrics.engagementRate >= 3.0 -> R.color.good_orange
                    else -> R.color.poor_red
                }
                textEngagement.setTextColor(
                    ContextCompat.getColor(itemView.context, engagementColor)
                )
                
                // Hide hashtags as requested
                textHashtags.isVisible = false
                
                // Additional metrics with clear labels
                if (post.metrics.impressionsCount != null) {
                    textImpressions.text = "👁️ ${formatNumber(post.metrics.impressionsCount)} Impressions"
                    textImpressions.isVisible = true
                    labelImpressions.isVisible = false // Hide emoji label since we include it in text
                } else {
                    textImpressions.isVisible = false
                    labelImpressions.isVisible = false
                }
                
                if (post.metrics.savesCount != null) {
                    textSaves.text = "🔖 ${formatNumber(post.metrics.savesCount)} Saves"
                    textSaves.isVisible = true
                    labelSaves.isVisible = false // Hide emoji label since we include it in text
                } else {
                    textSaves.isVisible = false
                    labelSaves.isVisible = false
                }
                
                // Media type indicator with different colors
                val mediaTypeColor = when (post.mediaType) {
                    "REELS" -> R.color.reels_purple
                    "VIDEO" -> R.color.video_blue
                    "CAROUSEL_ALBUM" -> R.color.carousel_orange
                    else -> R.color.image_green
                }
                textMediaType.setBackgroundColor(
                    ContextCompat.getColor(itemView.context, mediaTypeColor)
                )
                
                // Click listener
                root.setOnClickListener { onPostClick(post) }
                
                // Load image if available - improved error handling
                when {
                    !post.mediaUrl.isNullOrBlank() -> {
                        imagePost.isVisible = true
                        Glide.with(itemView.context)
                            .load(post.mediaUrl)
                            .placeholder(R.drawable.placeholder_image)
                            .error(R.drawable.error_image)
                            .centerCrop()
                            .into(imagePost)
                    }
                    !post.thumbnailUrl.isNullOrBlank() -> {
                        imagePost.isVisible = true
                        Glide.with(itemView.context)
                            .load(post.thumbnailUrl)
                            .placeholder(R.drawable.placeholder_image)
                            .error(R.drawable.error_image)
                            .centerCrop()
                            .into(imagePost)
                    }
                    else -> {
                        // Show a default image instead of hiding
                        imagePost.isVisible = true
                        Glide.with(itemView.context)
                            .load(R.drawable.ic_instagram_posts)
                            .into(imagePost)
                    }
                }
            }
        }
    }
    
    private fun formatDate(timestamp: String): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val outputFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            val date = inputFormat.parse(timestamp)
            outputFormat.format(date ?: Date())
        } catch (e: Exception) {
            timestamp.take(10) // Fallback to just the date part
        }
    }
    
    private fun formatNumber(number: Int): String {
        return when {
            number >= 1_000_000 -> "${String.format("%.1f", number / 1_000_000.0)}M"
            number >= 1_000 -> "${String.format("%.1f", number / 1_000.0)}K"
            else -> NumberFormat.getNumberInstance().format(number)
        }
    }
} 