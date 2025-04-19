package com.example.allinone.adapters

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.example.allinone.databinding.ItemInvestmentImageBinding

class InvestmentImageAdapter(
    private val onDeleteClick: (Uri) -> Unit,
    private val onImageClick: (Uri) -> Unit
) : ListAdapter<Uri, InvestmentImageAdapter.ImageViewHolder>(ImageDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val binding = ItemInvestmentImageBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ImageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ImageViewHolder(
        private val binding: ItemInvestmentImageBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.deleteButton.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onDeleteClick(getItem(position))
                }
            }

            binding.imageView.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onImageClick(getItem(position))
                }
            }
        }

        fun bind(uri: Uri) {
            try {
                // Check if this is a content URI (local) or http/https (remote)
                if (uri.scheme == "content") {
                    try {
                        val contentResolver = itemView.context.contentResolver
                        contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    } catch (e: Exception) {
                        // Log but continue - some URIs might not support this
                    }
                }
                
                // Use Glide with placeholders for better visual feedback
                Glide.with(itemView.context)
                    .load(uri)
                    .placeholder(com.example.allinone.R.drawable.placeholder_image)
                    .error(com.example.allinone.R.drawable.error_image)
                    .centerCrop()
                    .into(binding.imageView)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private class ImageDiffCallback : DiffUtil.ItemCallback<Uri>() {
        override fun areItemsTheSame(oldItem: Uri, newItem: Uri): Boolean {
            return oldItem == newItem
        }

        override fun areContentsTheSame(oldItem: Uri, newItem: Uri): Boolean {
            return oldItem == newItem
        }
    }
} 