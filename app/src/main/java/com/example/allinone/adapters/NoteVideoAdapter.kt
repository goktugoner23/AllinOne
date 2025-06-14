package com.example.allinone.adapters

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.allinone.R
import com.example.allinone.databinding.ItemNoteVideoBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class NoteVideoAdapter(
    private val onDeleteClick: (Uri) -> Unit,
    private val onVideoClick: (Uri) -> Unit = { }
) : ListAdapter<Uri, NoteVideoAdapter.VideoViewHolder>(VideoDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        val binding = ItemNoteVideoBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return VideoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        val uri = getItem(position)
        if (isValidUri(uri)) {
            holder.bind(uri)
        } else {
            // If invalid URI, notify for removal
            onDeleteClick(uri)
        }
    }
    
    private fun isValidUri(uri: Uri): Boolean {
        if (uri.toString().isEmpty()) return false
        
        return when (uri.scheme) {
            "content" -> true  // Content provider URI
            "file" -> File(uri.path ?: "").exists()  // File exists check
            else -> true  // Other schemes like http, https, etc.
        }
    }

    inner class VideoViewHolder(
        private val binding: ItemNoteVideoBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(uri: Uri) {
            // Show loading initially
            binding.progressBar.visibility = View.VISIBLE
            binding.videoThumbnail.setImageResource(R.drawable.ic_video_placeholder)
            
            // Set up click listeners
            binding.root.setOnClickListener { onVideoClick(uri) }
            binding.deleteButton.setOnClickListener { onDeleteClick(uri) }
            
            // Load thumbnail asynchronously
            loadVideoThumbnail(uri)
        }
        
        private fun loadVideoThumbnail(uri: Uri) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val thumbnail = getVideoThumbnail(uri)
                    withContext(Dispatchers.Main) {
                        binding.progressBar.visibility = View.GONE
                        if (thumbnail != null) {
                            binding.videoThumbnail.setImageBitmap(thumbnail)
                        } else {
                            // If can't load thumbnail, use Glide with video URI
                            Glide.with(binding.root.context)
                                .load(uri)
                                .placeholder(R.drawable.ic_video_placeholder)
                                .error(R.drawable.ic_video_error)
                                .into(binding.videoThumbnail)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("NoteVideoAdapter", "Error loading video thumbnail: ${e.message}", e)
                    withContext(Dispatchers.Main) {
                        binding.progressBar.visibility = View.GONE
                        binding.videoThumbnail.setImageResource(R.drawable.ic_video_error)
                    }
                }
            }
        }
        
        private fun getVideoThumbnail(uri: Uri): Bitmap? {
            return try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(binding.root.context, uri)
                val thumbnail = retriever.getFrameAtTime(0)
                retriever.release()
                thumbnail
            } catch (e: Exception) {
                Log.e("NoteVideoAdapter", "Error creating video thumbnail: ${e.message}", e)
                null
            }
        }
    }

    class VideoDiffCallback : DiffUtil.ItemCallback<Uri>() {
        override fun areItemsTheSame(oldItem: Uri, newItem: Uri): Boolean {
            return oldItem == newItem
        }

        override fun areContentsTheSame(oldItem: Uri, newItem: Uri): Boolean {
            return oldItem == newItem
        }
    }
} 