package com.example.allinone.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.allinone.R
import com.google.android.material.imageview.ShapeableImageView

class FullscreenImageAdapter(
    private val context: Context,
    private val imageUris: List<String>
) : RecyclerView.Adapter<FullscreenImageAdapter.ImageViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_fullscreen_image, parent, false)
        return ImageViewHolder(view)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        val imageUri = imageUris[position]
        
        // Use Glide to load the image
        Glide.with(context)
            .load(imageUri)
            .into(holder.imageView)
    }

    override fun getItemCount(): Int = imageUris.size

    inner class ImageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: ShapeableImageView = itemView.findViewById(R.id.fullscreenImageItem)
    }
} 