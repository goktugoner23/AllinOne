package com.example.allinone.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.Switch
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.allinone.R
import com.example.allinone.data.Schedule

class ScheduleAdapter(
    private val onScheduleClick: (Schedule) -> Unit,
    private val onScheduleEdit: (Schedule) -> Unit,
    private val onScheduleDelete: (Schedule) -> Unit,
    private val onScheduleToggle: (Schedule) -> Unit
) : ListAdapter<Schedule, ScheduleAdapter.ScheduleViewHolder>(ScheduleDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ScheduleViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_schedule, parent, false)
        return ScheduleViewHolder(view)
    }

    override fun onBindViewHolder(holder: ScheduleViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ScheduleViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val timeText: TextView = itemView.findViewById(R.id.timeText)
        private val titleText: TextView = itemView.findViewById(R.id.titleText)
        private val descriptionText: TextView = itemView.findViewById(R.id.descriptionText)
        private val categoryText: TextView = itemView.findViewById(R.id.categoryText)
        private val daysText: TextView = itemView.findViewById(R.id.daysText)
        private val colorIndicator: View = itemView.findViewById(R.id.colorIndicator)
        private val enabledSwitch: Switch = itemView.findViewById(R.id.enabledSwitch)
        private val editButton: ImageButton = itemView.findViewById(R.id.editButton)
        private val deleteButton: ImageButton = itemView.findViewById(R.id.deleteButton)

        fun bind(schedule: Schedule) {
            timeText.text = schedule.getFormattedTime()
            titleText.text = schedule.title
            
            // Set description visibility
            if (schedule.description.isNullOrBlank()) {
                descriptionText.visibility = View.GONE
            } else {
                descriptionText.text = schedule.description
                descriptionText.visibility = View.VISIBLE
            }
            
            categoryText.text = schedule.category
            daysText.text = schedule.getDayNames()
            
            // Set color indicator
            try {
                colorIndicator.setBackgroundColor(Color.parseColor(schedule.color))
            } catch (e: Exception) {
                colorIndicator.setBackgroundColor(Color.parseColor("#4CAF50")) // Default green
            }
            
            // Set enabled state
            enabledSwitch.isChecked = schedule.isEnabled
            
            // Set opacity based on enabled state
            val alpha = if (schedule.isEnabled) 1.0f else 0.6f
            itemView.alpha = alpha
            
            // Set click listeners
            itemView.setOnClickListener {
                onScheduleClick(schedule)
            }
            
            editButton.setOnClickListener {
                onScheduleEdit(schedule)
            }
            
            deleteButton.setOnClickListener {
                onScheduleDelete(schedule)
            }
            
            enabledSwitch.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked != schedule.isEnabled) {
                    onScheduleToggle(schedule)
                }
            }
        }
    }

    class ScheduleDiffCallback : DiffUtil.ItemCallback<Schedule>() {
        override fun areItemsTheSame(oldItem: Schedule, newItem: Schedule): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Schedule, newItem: Schedule): Boolean {
            return oldItem == newItem
        }
    }
} 