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
import com.example.allinone.data.WTRegistration
import com.example.allinone.data.WTStudent
import com.example.allinone.databinding.ItemWtRegistrationBinding
import java.text.SimpleDateFormat
import java.util.Locale

class WTRegistrationAdapter(
    private val onItemClick: (WTRegistration) -> Unit,
    private val onLongPress: (WTRegistration, View) -> Unit,
    private val onPaymentStatusClick: (WTRegistration) -> Unit,
    private val onShareClick: (WTRegistration) -> Unit,
    private val getStudentName: (Long) -> String = { _ -> "Unknown Student" } // Function to get student name from ID
) : ListAdapter<WTRegistration, WTRegistrationAdapter.ViewHolder>(RegistrationDiffCallback()) {

    // Extension function to get the student name for a registration
    private fun WTRegistration.getStudentName(): String {
        return getStudentName(this.studentId)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemWtRegistrationBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemWtRegistrationBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(getItem(position))
                }
            }

            binding.root.setOnLongClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onLongPress(getItem(position), it)
                }
                true
            }

            binding.paymentStatusChip.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onPaymentStatusClick(getItem(position))
                }
            }

            binding.shareButton.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onShareClick(getItem(position))
                }
            }
        }

        fun bind(registration: WTRegistration) {
            binding.studentName.text = registration.getStudentName()
            binding.startDate.text = registration.startDate?.let { dateFormat.format(it) } ?: "Not set"
            binding.endDate.text = registration.endDate?.let { dateFormat.format(it) } ?: "Not set"
            binding.amount.text = String.format("%.2f", registration.amount)
            
            // A registration is considered "paid" if it exists (since it has a payment date)
            val isPaid = true
            
            binding.paymentStatusChip.apply {
                text = if (isPaid) "Paid" else "Unpaid"
                setChipBackgroundColorResource(
                    if (isPaid) 
                        com.example.allinone.R.color.colorSuccess
                    else 
                        com.example.allinone.R.color.colorWarning
                )
            }
        }
    }

    class RegistrationDiffCallback : DiffUtil.ItemCallback<WTRegistration>() {
        override fun areItemsTheSame(oldItem: WTRegistration, newItem: WTRegistration): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: WTRegistration, newItem: WTRegistration): Boolean {
            return oldItem == newItem
        }
    }
} 