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
    private val onLongPress: (WTStudent, View) -> Unit,
    private val onPaymentStatusClick: (WTStudent) -> Unit,
    private val onShareClick: (WTStudent) -> Unit
) : ListAdapter<WTStudent, WTRegistrationAdapter.ViewHolder>(StudentDiffCallback()) {

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
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(getItem(position))
                }
            }

            binding.root.setOnLongClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onLongPress(getItem(position), it)
                }
                true
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
            binding.studentName.text = student.name
            binding.startDate.text = student.startDate?.let { dateFormat.format(it) } ?: "Not set"
            binding.endDate.text = student.endDate?.let { dateFormat.format(it) } ?: "Not set"
            binding.amount.text = String.format("%.2f", student.amount)
            
            binding.paymentStatusChip.apply {
                text = if (student.isPaid) "Paid" else "Unpaid"
                setChipBackgroundColorResource(
                    if (student.isPaid) 
                        com.example.allinone.R.color.colorSuccess
                    else 
                        com.example.allinone.R.color.colorWarning
                )
            }
        }
    }

    class StudentDiffCallback : DiffUtil.ItemCallback<WTStudent>() {
        override fun areItemsTheSame(oldItem: WTStudent, newItem: WTStudent): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: WTStudent, newItem: WTStudent): Boolean {
            return oldItem == newItem
        }
    }
} 