package com.example.allinone.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.allinone.R
import com.example.allinone.data.HistoryItem
import com.example.allinone.databinding.ItemHistoryBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class HistoryAdapter(
    private val onItemClick: (HistoryItem) -> Unit,
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
        val item = getItem(position)
        holder.bind(item)
    }

    inner class HistoryViewHolder(
        private val binding: ItemHistoryBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        private val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        private val fullDateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
        private val calendar = Calendar.getInstance()
        
        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(getItem(position))
                }
            }
        }

        fun bind(item: HistoryItem) {
            // Format date for display
            val today = Calendar.getInstance()
            calendar.time = item.date
            
            val formattedDateText = when {
                isSameDay(calendar, today) -> "Today at ${dateFormat.format(item.date)}"
                isYesterday(calendar) -> "Yesterday at ${dateFormat.format(item.date)}"
                else -> fullDateFormat.format(item.date)
            }
            
            binding.apply {
                titleText.text = item.title
                descriptionText.text = item.description
                dateText.text = formattedDateText
                
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
                    HistoryItem.ItemType.REGISTRATION -> {
                        typeIcon.setImageResource(R.drawable.ic_student)
                    }
                }
                
                // Show amount if available
                if (item.amount != null) {
                    amountText.text = String.format("$%.2f", item.amount)
                    amountText.visibility = View.VISIBLE
                } else {
                    amountText.visibility = View.GONE
                }
                
                // Set delete button click listener
                deleteButton.setOnClickListener {
                    onDeleteClick(item)
                }
            }
        }
        
        // Helper methods for date comparison
        private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
            return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                   cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
        }
        
        private fun isYesterday(cal1: Calendar): Boolean {
            val yesterday = Calendar.getInstance()
            yesterday.add(Calendar.DAY_OF_YEAR, -1)
            return cal1.get(Calendar.YEAR) == yesterday.get(Calendar.YEAR) &&
                   cal1.get(Calendar.DAY_OF_YEAR) == yesterday.get(Calendar.DAY_OF_YEAR)
        }
    }
}

class HistoryDiffCallback : DiffUtil.ItemCallback<HistoryItem>() {
    override fun areItemsTheSame(oldItem: HistoryItem, newItem: HistoryItem): Boolean {
        return oldItem.id == newItem.id && oldItem.itemType == newItem.itemType
    }

    override fun areContentsTheSame(oldItem: HistoryItem, newItem: HistoryItem): Boolean {
        return oldItem == newItem
    }
} 