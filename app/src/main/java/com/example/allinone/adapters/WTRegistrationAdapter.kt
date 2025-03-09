package com.example.allinone.adapters

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
import com.example.allinone.databinding.ItemWtRegistrationBinding
import java.text.SimpleDateFormat
import java.util.Locale

class WTRegistrationAdapter(
    private val onItemClick: (WTStudent) -> Unit,
    private val onPaymentStatusClick: (WTStudent) -> Unit,
    private val onShareClick: (WTStudent) -> Unit
) : ListAdapter<WTStudent, WTRegistrationAdapter.WTRegistrationViewHolder>(WTStudentDiffCallback()) {

    private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WTRegistrationViewHolder {
        val binding = ItemWtRegistrationBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return WTRegistrationViewHolder(binding)
    }

    override fun onBindViewHolder(holder: WTRegistrationViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class WTStudentDiffCallback : DiffUtil.ItemCallback<WTStudent>() {
        override fun areItemsTheSame(oldItem: WTStudent, newItem: WTStudent): Boolean {
            return oldItem.id == newItem.id && 
                   oldItem.startDate?.time == newItem.startDate?.time
        }

        override fun areContentsTheSame(oldItem: WTStudent, newItem: WTStudent): Boolean {
            return oldItem.id == newItem.id &&
                   oldItem.name == newItem.name &&
                   oldItem.startDate == newItem.startDate &&
                   oldItem.endDate == newItem.endDate &&
                   oldItem.amount == newItem.amount &&
                   oldItem.isPaid == newItem.isPaid
        }
    }

    inner class WTRegistrationViewHolder(
        private val binding: ItemWtRegistrationBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(getItem(position))
                }
            }
            
            binding.paymentStatusChip.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onPaymentStatusClick(getItem(position))
                }
            }
            
            binding.shareButton.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onShareClick(getItem(position))
                }
            }
        }

        fun bind(student: WTStudent) {
            binding.apply {
                studentName.text = student.name
                
                // Show start date if available
                val startDateText = student.startDate?.let { 
                    "Started: ${dateFormat.format(it)}" 
                } ?: "No start date"
                courseStartDate.text = startDateText
                
                // Show amount with Turkish Lira symbol
                amountText.text = "Amount: ₺${student.amount}"
                
                // Set profile image if available
                student.profileImageUri?.let { imageUri ->
                    try {
                        profileImage.setImageURI(Uri.parse(imageUri))
                    } catch (e: Exception) {
                        profileImage.setImageResource(R.drawable.default_profile)
                    }
                } ?: profileImage.setImageResource(R.drawable.default_profile)
                
                // Set payment status chip
                paymentStatusChip.apply {
                    text = if (student.isPaid) "Paid" else "Unpaid"
                    chipBackgroundColor = ContextCompat.getColorStateList(
                        itemView.context,
                        if (student.isPaid) R.color.colorSuccess else R.color.colorError
                    )
                    setTextColor(
                        ContextCompat.getColor(
                            itemView.context,
                            R.color.white
                        )
                    )
                }
            }
        }
    }
} 