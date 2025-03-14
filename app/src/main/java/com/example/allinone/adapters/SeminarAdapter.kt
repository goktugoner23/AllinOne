package com.example.allinone.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.allinone.R
import com.example.allinone.data.WTSeminar
import java.text.SimpleDateFormat
import java.util.Locale

class SeminarAdapter(
    private val onShareClick: (WTSeminar) -> Unit,
    private val onItemClick: (WTSeminar) -> Unit
) : ListAdapter<WTSeminar, SeminarAdapter.SeminarViewHolder>(SeminarDiffCallback()) {

    class SeminarViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val nameText: TextView = itemView.findViewById(R.id.seminarNameText)
        val dateText: TextView = itemView.findViewById(R.id.seminarDateText)
        val timeText: TextView = itemView.findViewById(R.id.seminarTimeText)
        val descriptionText: TextView = itemView.findViewById(R.id.seminarDescriptionText)
        val shareButton: ImageButton = itemView.findViewById(R.id.shareButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SeminarViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_seminar, parent, false)
        return SeminarViewHolder(view)
    }

    override fun onBindViewHolder(holder: SeminarViewHolder, position: Int) {
        val seminar = getItem(position)
        val context = holder.itemView.context
        
        // Set seminar name
        holder.nameText.text = seminar.name
        
        // Format and set date
        val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        holder.dateText.text = dateFormat.format(seminar.date)
        
        // Format and set time
        holder.timeText.text = formatTimeRange(
            context,
            seminar.startHour, seminar.startMinute,
            seminar.endHour, seminar.endMinute
        )
        
        // Set description if available
        if (!seminar.description.isNullOrEmpty()) {
            holder.descriptionText.text = seminar.description
            holder.descriptionText.visibility = View.VISIBLE
        } else {
            holder.descriptionText.visibility = View.GONE
        }
        
        // Set click listeners
        holder.shareButton.setOnClickListener {
            onShareClick(seminar)
        }
        
        holder.itemView.setOnClickListener {
            onItemClick(seminar)
        }
    }
    
    private fun formatTimeRange(
        context: Context,
        startHour: Int,
        startMinute: Int,
        endHour: Int,
        endMinute: Int
    ): String {
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        
        // Create calendar instances for start and end times
        val startCalendar = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, startHour)
            set(java.util.Calendar.MINUTE, startMinute)
        }
        
        val endCalendar = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, endHour)
            set(java.util.Calendar.MINUTE, endMinute)
        }
        
        val startTimeStr = timeFormat.format(startCalendar.time)
        val endTimeStr = timeFormat.format(endCalendar.time)
        
        return "$startTimeStr - $endTimeStr"
    }
}

/**
 * DiffUtil callback for comparing WTSeminar items
 */
class SeminarDiffCallback : DiffUtil.ItemCallback<WTSeminar>() {
    override fun areItemsTheSame(oldItem: WTSeminar, newItem: WTSeminar): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: WTSeminar, newItem: WTSeminar): Boolean {
        return oldItem == newItem
    }
} 