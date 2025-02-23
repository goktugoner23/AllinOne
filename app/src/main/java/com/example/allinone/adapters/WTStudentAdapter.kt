package com.example.allinone.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.allinone.data.WTStudent
import com.example.allinone.databinding.ItemWtStudentBinding
import java.text.SimpleDateFormat
import java.util.Locale

class WTStudentAdapter(
    private val onItemClick: (WTStudent) -> Unit,
    private val onPaymentStatusClick: (WTStudent) -> Unit
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
        private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

        init {
            binding.root.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(getItem(position))
                }
            }

            binding.paymentStatus.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onPaymentStatusClick(getItem(position))
                }
            }
        }

        fun bind(student: WTStudent) {
            binding.apply {
                studentName.text = student.name
                dateRange.text = "${dateFormat.format(student.startDate)} - ${dateFormat.format(student.endDate)}"
                amount.text = String.format("₺%.2f", student.amount)

                paymentStatus.apply {
                    text = if (student.isPaid) "Paid" else "Unpaid"
                    setChipBackgroundColorResource(
                        if (student.isPaid) android.R.color.holo_green_light
                        else android.R.color.holo_red_light
                    )
                }
            }
        }
    }

    private class WTStudentDiffCallback : DiffUtil.ItemCallback<WTStudent>() {
        override fun areItemsTheSame(oldItem: WTStudent, newItem: WTStudent): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: WTStudent, newItem: WTStudent): Boolean {
            return oldItem == newItem
        }
    }
} 