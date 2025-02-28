package com.example.allinone.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.allinone.R
import com.example.allinone.data.HistoryItem
import com.example.allinone.databinding.ItemHistoryBinding
import java.text.SimpleDateFormat
import java.util.Locale

class HistoryAdapter(
    private val onDeleteClick: (HistoryItem) -> Unit
) : ListAdapter<HistoryItem, HistoryAdapter.HistoryViewHolder>(HistoryDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val binding = ItemHistoryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return HistoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class HistoryViewHolder(
        private val binding: ItemHistoryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: HistoryItem) {
            val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            
            binding.apply {
                titleText.text = item.title
                descriptionText.text = item.description
                dateText.text = dateFormat.format(item.date)
                
                // Set type icon based on item type
                when (item.itemType) {
                    HistoryItem.ItemType.TRANSACTION -> {
                        typeIcon.setImageResource(
                            if (item.type == "Income") R.drawable.ic_income else R.drawable.ic_expense
                        )
                    }
                    HistoryItem.ItemType.INVESTMENT -> {
                        typeIcon.setImageResource(R.drawable.ic_investment)
                    }
                    HistoryItem.ItemType.NOTE -> {
                        typeIcon.setImageResource(R.drawable.ic_note)
                    }
                    HistoryItem.ItemType.STUDENT -> {
                        typeIcon.setImageResource(R.drawable.ic_student)
                    }
                }
                
                // Show amount if available
                if (item.amount != null) {
                    amountText.text = String.format("$%.2f", item.amount)
                    amountText.visibility = android.view.View.VISIBLE
                } else {
                    amountText.visibility = android.view.View.GONE
                }
                
                // Set delete button click listener
                deleteButton.setOnClickListener {
                    onDeleteClick(item)
                }
            }
        }
    }

    private class HistoryDiffCallback : DiffUtil.ItemCallback<HistoryItem>() {
        override fun areItemsTheSame(oldItem: HistoryItem, newItem: HistoryItem): Boolean {
            return oldItem.id == newItem.id && oldItem.itemType == newItem.itemType
        }

        override fun areContentsTheSame(oldItem: HistoryItem, newItem: HistoryItem): Boolean {
            return oldItem == newItem
        }
    }
} 