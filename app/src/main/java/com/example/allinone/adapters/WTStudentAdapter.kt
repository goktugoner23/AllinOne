package com.example.allinone.adapters

import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.allinone.R
import com.example.allinone.data.WTStudent
import com.example.allinone.databinding.ItemWtStudentBinding
import java.text.SimpleDateFormat
import java.util.Locale

class WTStudentAdapter(
    private val onItemClick: (WTStudent) -> Unit,
    private val onLongPress: (WTStudent, View) -> Unit,
    private val isStudentRegistered: ((Long) -> Boolean)? = null
) : ListAdapter<WTStudent, WTStudentAdapter.WTStudentViewHolder>(WTStudentDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WTStudentViewHolder {
        val binding = ItemWtStudentBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return WTStudentViewHolder(binding)
    }

    override fun onBindViewHolder(holder: WTStudentViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class WTStudentViewHolder(
        private val binding: ItemWtStudentBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.apply {
                setOnClickListener {
                    val position = bindingAdapterPosition
                    if (position != RecyclerView.NO_POSITION) {
                        onItemClick(getItem(position))
                    }
                }
                
                setOnLongClickListener {
                    val position = bindingAdapterPosition
                    if (position != RecyclerView.NO_POSITION) {
                        onLongPress(getItem(position), it)
                        true
                    } else {
                        false
                    }
                }
            }
            
            // We keep the status indicator clickable but change what it represents
            binding.statusIndicator.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(getItem(position))
                }
            }
        }

        fun bind(student: WTStudent) {
            binding.apply {
                studentName.text = student.name
                phoneNumber.text = student.phoneNumber ?: ""
                
                // Show email if available
                if (!student.email.isNullOrEmpty()) {
                    email.visibility = View.VISIBLE
                    email.text = student.email
                } else {
                    email.visibility = View.GONE
                }
                
                // Load profile image if available
                if (!student.photoUri.isNullOrEmpty()) {
                    Log.d("WTStudentAdapter", "Loading profile image from URI: ${student.photoUri}")
                    if (student.photoUri.startsWith("https://")) {
                        com.bumptech.glide.Glide.with(itemView.context)
                            .load(student.photoUri)
                            .placeholder(R.drawable.default_profile)
                            .error(R.drawable.default_profile)
                            .into(profileImage)
                    } else {
                        try {
                            profileImage.setImageURI(Uri.parse(student.photoUri))
                        } catch (e: Exception) {
                            Log.e("WTStudentAdapter", "Error loading local image: ${e.message}")
                            profileImage.setImageResource(R.drawable.default_profile)
                        }
                    }
                } else {
                    profileImage.setImageResource(R.drawable.default_profile)
                }
                
                // Set registration status indicator color
                // Green if active, blue if registered, red if inactive
                val isRegistered = isStudentRegistered?.invoke(student.id) ?: false
                
                val color = ContextCompat.getColor(
                    itemView.context,
                    when {
                        !student.isActive -> android.R.color.holo_red_light
                        isRegistered -> android.R.color.holo_blue_light
                        else -> android.R.color.holo_green_light
                    }
                )
                
                // Create a new GradientDrawable with the selected color
                val circleDrawable = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(color)
                }
                
                // Apply the drawable
                statusIndicator.background = circleDrawable
                
                // Status indicator is now showing active + registration status
                statusIndicator.isClickable = true
                statusIndicator.isFocusable = true
                statusIndicator.contentDescription = itemView.context.getString(
                    when {
                        !student.isActive -> R.string.status_inactive_desc
                        isRegistered -> R.string.status_registered_desc
                        else -> R.string.status_active_desc
                    }
                )
            }
        }
    }

    private class WTStudentDiffCallback : DiffUtil.ItemCallback<WTStudent>() {
        override fun areItemsTheSame(oldItem: WTStudent, newItem: WTStudent): Boolean {
            // Use only ID to determine if items are the same
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: WTStudent, newItem: WTStudent): Boolean {
            // Check all the fields that are shown in the student item view
            return oldItem.id == newItem.id &&
                   oldItem.name == newItem.name &&
                   oldItem.phoneNumber == newItem.phoneNumber &&
                   oldItem.email == newItem.email &&
                   oldItem.isActive == newItem.isActive &&
                   oldItem.photoUri == newItem.photoUri
        }
    }
} 