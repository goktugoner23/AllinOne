package com.example.allinone.adapters

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.allinone.R
import com.example.allinone.data.Task
import com.example.allinone.databinding.ItemTaskBinding

class TasksAdapter(
    private val onItemClick: (Task) -> Unit,
    private val onItemLongClick: (Task, View) -> Unit
) : ListAdapter<Task, TasksAdapter.TaskViewHolder>(TaskDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val binding = ItemTaskBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TaskViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class TaskViewHolder(private val binding: ItemTaskBinding) : RecyclerView.ViewHolder(binding.root) {
        
        init {
            // Set click listeners for both the card and checkbox
            binding.root.setOnClickListener {
                if (bindingAdapterPosition != RecyclerView.NO_POSITION) {
                    onItemClick(getItem(bindingAdapterPosition))
                }
            }
            
            binding.taskCheckbox.setOnClickListener {
                if (bindingAdapterPosition != RecyclerView.NO_POSITION) {
                    onItemClick(getItem(bindingAdapterPosition))
                }
            }
            
            // Long press for context menu
            binding.root.setOnLongClickListener {
                if (bindingAdapterPosition != RecyclerView.NO_POSITION) {
                    onItemLongClick(getItem(bindingAdapterPosition), it)
                }
                true
            }
        }
        
        fun bind(task: Task) {
            // Set task name and description
            binding.taskDescription.text = task.name
            binding.taskDescription.setTypeface(null, android.graphics.Typeface.BOLD)
            if (!task.description.isNullOrBlank()) {
                binding.taskDescription.append("\n" + task.description)
                binding.taskDescription.setTypeface(null, android.graphics.Typeface.NORMAL)
            }

            // Show due date if set
            if (task.dueDate != null) {
                binding.taskDescription.append("\nDue: " + java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(task.dueDate))
            }
            
            // Set checkbox state
            binding.taskCheckbox.isChecked = task.completed
            
            // Make card red if due today
            if (task.dueDate != null) {
                val now = java.util.Calendar.getInstance()
                val due = java.util.Calendar.getInstance().apply { time = task.dueDate }
                val sameDay = now.get(java.util.Calendar.YEAR) == due.get(java.util.Calendar.YEAR) &&
                        now.get(java.util.Calendar.DAY_OF_YEAR) == due.get(java.util.Calendar.DAY_OF_YEAR)
                if (sameDay) {
                    binding.root.setCardBackgroundColor(android.graphics.Color.RED)
                } else {
                    binding.root.setCardBackgroundColor(android.graphics.Color.WHITE)
                }
            } else {
                binding.root.setCardBackgroundColor(android.graphics.Color.WHITE)
            }
            
            // Apply styling based on completion status
            if (task.completed) {
                // Strike through text and dim color for completed tasks
                binding.taskDescription.paintFlags = binding.taskDescription.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                binding.taskDescription.setTextColor(
                    ContextCompat.getColor(binding.root.context, android.R.color.darker_gray)
                )
            } else {
                // Remove strike through and restore normal color for incomplete tasks
                binding.taskDescription.paintFlags = binding.taskDescription.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                binding.taskDescription.setTextColor(
                    ContextCompat.getColor(binding.root.context, android.R.color.black)
                )
            }
        }
    }
}

class TaskDiffCallback : DiffUtil.ItemCallback<Task>() {
    override fun areItemsTheSame(oldItem: Task, newItem: Task): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: Task, newItem: Task): Boolean {
        return oldItem == newItem
    }
} 