package com.example.allinone.adapters

import android.graphics.drawable.GradientDrawable
import android.net.Uri
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
    private val onPaymentStatusClick: ((WTStudent) -> Unit)? = null
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
            binding.root.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(getItem(position))
                }
            }
            
            // Add payment status click listener only if callback is provided
            onPaymentStatusClick?.let { callback ->
                binding.statusIndicator.setOnClickListener {
                    val position = adapterPosition
                    if (position != RecyclerView.NO_POSITION) {
                        callback(getItem(position))
                    }
                }
            }
        }

        fun bind(student: WTStudent) {
            binding.apply {
                studentName.text = student.name
                phoneNumber.text = student.phoneNumber
                
                // Show email if available
                if (!student.email.isNullOrEmpty()) {
                    email.visibility = View.VISIBLE
                    email.text = student.email
                } else {
                    email.visibility = View.GONE
                }
                
                // Set profile image if available
                student.profileImageUri?.let { imageUri ->
                    try {
                        profileImage.setImageURI(Uri.parse(imageUri))
                    } catch (e: Exception) {
                        profileImage.setImageResource(R.drawable.default_profile)
                    }
                } ?: profileImage.setImageResource(R.drawable.default_profile)
                
                // Set active status indicator color
                val color = ContextCompat.getColor(
                    itemView.context,
                    if (student.isActive) android.R.color.holo_green_light 
                    else android.R.color.holo_red_light
                )
                
                // Create a new GradientDrawable with the selected color
                val circleDrawable = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(color)
                }
                
                // Apply the drawable
                statusIndicator.background = circleDrawable
                
                // Make status indicator clickable if payment callback is provided
                if (onPaymentStatusClick != null) {
                    statusIndicator.isClickable = true
                    statusIndicator.isFocusable = true
                    statusIndicator.contentDescription = itemView.context.getString(
                        if (student.isPaid) 
                            R.string.status_paid_desc 
                        else 
                            R.string.status_unpaid_desc
                    )
                }
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
                   oldItem.profileImageUri == newItem.profileImageUri
        }
    }
} 