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
import java.util.Calendar

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
            // Format differently based on how recent the date is
            val today = Calendar.getInstance()
            val itemCal = Calendar.getInstance().apply { time = item.date }
            
            val formattedDateText = when {
                // Today
                isSameDay(today, itemCal) -> {
                    val timeFormat = SimpleDateFormat("'Today at' HH:mm", Locale.getDefault())
                    timeFormat.format(item.date)
                }
                // Yesterday
                isYesterday(today, itemCal) -> {
                    val timeFormat = SimpleDateFormat("'Yesterday at' HH:mm", Locale.getDefault())
                    timeFormat.format(item.date)
                }
                // Within the last 7 days
                isWithinLastWeek(today, itemCal) -> {
                    val dayFormat = SimpleDateFormat("EEEE 'at' HH:mm", Locale.getDefault())
                    dayFormat.format(item.date)
                }
                // This year
                isThisYear(today, itemCal) -> {
                    val monthDayFormat = SimpleDateFormat("MMM dd 'at' HH:mm", Locale.getDefault())
                    monthDayFormat.format(item.date)
                }
                // Older
                else -> {
                    val fullDateFormat = SimpleDateFormat("MMM dd, yyyy 'at' HH:mm", Locale.getDefault())
                    fullDateFormat.format(item.date)
                }
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
        
        // Helper methods for date comparison
        private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
            return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                   cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
        }
        
        private fun isYesterday(today: Calendar, other: Calendar): Boolean {
            val yesterday = Calendar.getInstance().apply { 
                timeInMillis = today.timeInMillis
                add(Calendar.DAY_OF_YEAR, -1)
            }
            return isSameDay(yesterday, other)
        }
        
        private fun isWithinLastWeek(today: Calendar, other: Calendar): Boolean {
            val lastWeek = Calendar.getInstance().apply { 
                timeInMillis = today.timeInMillis
                add(Calendar.DAY_OF_YEAR, -7)
            }
            return other.timeInMillis >= lastWeek.timeInMillis
        }
        
        private fun isThisYear(today: Calendar, other: Calendar): Boolean {
            return today.get(Calendar.YEAR) == other.get(Calendar.YEAR)
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