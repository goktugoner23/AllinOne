package com.example.allinone.adapters

import android.graphics.Color
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
import com.example.allinone.data.TaskGroup
import com.example.allinone.databinding.ItemTaskBinding
import com.example.allinone.databinding.ItemTaskGroupHeaderBinding

/**
 * Adapter for displaying tasks grouped by categories with expandable sections
 */
class GroupedTasksAdapter(
    private val onTaskClick: (Task) -> Unit,
    private val onTaskLongClick: (Task, View) -> Unit,
    private val onGroupClick: (TaskGroup?, Boolean) -> Unit, // group, isExpanded
    private val onGroupLongClick: (TaskGroup, View) -> Unit
) : ListAdapter<GroupedTasksAdapter.GroupedItem, RecyclerView.ViewHolder>(GroupedItemDiffCallback()) {

    companion object {
        private const val VIEW_TYPE_GROUP_HEADER = 0
        private const val VIEW_TYPE_TASK = 1
    }

    private val expandedGroups = mutableSetOf<Long?>() // Track expanded groups (null for ungrouped)

    /**
     * Sealed class representing items in the grouped list
     */
    sealed class GroupedItem {
        data class GroupHeader(
            val group: TaskGroup?,
            val taskCount: Int,
            val completedCount: Int,
            val isExpanded: Boolean
        ) : GroupedItem()

        data class TaskItem(val task: Task) : GroupedItem()
    }

    /**
     * Update the list with grouped tasks
     */
    fun updateGroupedTasks(groupedTasks: Map<TaskGroup?, List<Task>>) {
        val items = mutableListOf<GroupedItem>()

        groupedTasks.forEach { (group, tasks) ->
            val groupId = group?.id
            val isExpanded = expandedGroups.contains(groupId)
            val completedCount = tasks.count { it.completed }

            // Add group header
            items.add(
                GroupedItem.GroupHeader(
                    group = group,
                    taskCount = tasks.size,
                    completedCount = completedCount,
                    isExpanded = isExpanded
                )
            )

            // Add tasks if group is expanded
            if (isExpanded) {
                tasks.forEach { task ->
                    items.add(GroupedItem.TaskItem(task))
                }
            }
        }

        submitList(items)
    }

    /**
     * Toggle group expansion state
     */
    fun toggleGroupExpansion(group: TaskGroup?) {
        val groupId = group?.id
        if (expandedGroups.contains(groupId)) {
            expandedGroups.remove(groupId)
        } else {
            expandedGroups.add(groupId)
        }
        // Notify that expansion state changed
        onGroupClick(group, expandedGroups.contains(groupId))
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is GroupedItem.GroupHeader -> VIEW_TYPE_GROUP_HEADER
            is GroupedItem.TaskItem -> VIEW_TYPE_TASK
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_GROUP_HEADER -> {
                val binding = ItemTaskGroupHeaderBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
                GroupHeaderViewHolder(binding)
            }
            VIEW_TYPE_TASK -> {
                val binding = ItemTaskBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
                TaskViewHolder(binding)
            }
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is GroupedItem.GroupHeader -> (holder as GroupHeaderViewHolder).bind(item)
            is GroupedItem.TaskItem -> (holder as TaskViewHolder).bind(item.task)
        }
    }

    /**
     * ViewHolder for group headers
     */
    inner class GroupHeaderViewHolder(private val binding: ItemTaskGroupHeaderBinding) :
        RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                if (bindingAdapterPosition != RecyclerView.NO_POSITION) {
                    val item = getItem(bindingAdapterPosition) as GroupedItem.GroupHeader
                    toggleGroupExpansion(item.group)
                }
            }

            binding.root.setOnLongClickListener {
                if (bindingAdapterPosition != RecyclerView.NO_POSITION) {
                    val item = getItem(bindingAdapterPosition) as GroupedItem.GroupHeader
                    item.group?.let { group ->
                        onGroupLongClick(group, it)
                    }
                }
                true
            }
        }

        fun bind(groupHeader: GroupedItem.GroupHeader) {
            val group = groupHeader.group

            // Set group title
            binding.groupTitle.text = group?.title ?: binding.root.context.getString(R.string.no_group)

            // Set task count
            binding.taskCount.text = "${groupHeader.completedCount}/${groupHeader.taskCount} completed"

            // Set color indicator
            if (group != null) {
                try {
                    val color = Color.parseColor(group.color)
                    binding.colorIndicator.setBackgroundColor(color)
                    binding.colorIndicator.visibility = View.VISIBLE
                } catch (e: IllegalArgumentException) {
                    binding.colorIndicator.visibility = View.GONE
                }
            } else {
                binding.colorIndicator.visibility = View.GONE
            }

            // Set expansion indicator
            if (groupHeader.isExpanded) {
                binding.expandIcon.setImageResource(R.drawable.ic_expand_less)
            } else {
                binding.expandIcon.setImageResource(R.drawable.ic_expand_more)
            }

            // Set progress indicator
            if (groupHeader.taskCount > 0) {
                val progress = (groupHeader.completedCount.toFloat() / groupHeader.taskCount * 100).toInt()
                binding.progressBar.progress = progress
                binding.progressBar.visibility = View.VISIBLE
            } else {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    /**
     * ViewHolder for tasks (reusing from TasksAdapter)
     */
    inner class TaskViewHolder(private val binding: ItemTaskBinding) : RecyclerView.ViewHolder(binding.root) {

        init {
            // Set click listeners for both the card and checkbox
            binding.root.setOnClickListener {
                if (bindingAdapterPosition != RecyclerView.NO_POSITION) {
                    val item = getItem(bindingAdapterPosition) as GroupedItem.TaskItem
                    onTaskClick(item.task)
                }
            }

            binding.taskCheckbox.setOnClickListener {
                if (bindingAdapterPosition != RecyclerView.NO_POSITION) {
                    val item = getItem(bindingAdapterPosition) as GroupedItem.TaskItem
                    onTaskClick(item.task)
                }
            }

            // Long press for context menu
            binding.root.setOnLongClickListener {
                if (bindingAdapterPosition != RecyclerView.NO_POSITION) {
                    val item = getItem(bindingAdapterPosition) as GroupedItem.TaskItem
                    onTaskLongClick(item.task, it)
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

            // Add indentation for tasks to show they belong to a group
            val layoutParams = binding.root.layoutParams as ViewGroup.MarginLayoutParams
            layoutParams.marginStart = binding.root.context.resources.getDimensionPixelSize(R.dimen.task_indent_margin)
            binding.root.layoutParams = layoutParams
        }
    }
}

/**
 * DiffUtil callback for grouped items
 */
class GroupedItemDiffCallback : DiffUtil.ItemCallback<GroupedTasksAdapter.GroupedItem>() {
    override fun areItemsTheSame(
        oldItem: GroupedTasksAdapter.GroupedItem,
        newItem: GroupedTasksAdapter.GroupedItem
    ): Boolean {
        return when {
            oldItem is GroupedTasksAdapter.GroupedItem.GroupHeader && newItem is GroupedTasksAdapter.GroupedItem.GroupHeader -> {
                oldItem.group?.id == newItem.group?.id
            }
            oldItem is GroupedTasksAdapter.GroupedItem.TaskItem && newItem is GroupedTasksAdapter.GroupedItem.TaskItem -> {
                oldItem.task.id == newItem.task.id
            }
            else -> false
        }
    }

    override fun areContentsTheSame(
        oldItem: GroupedTasksAdapter.GroupedItem,
        newItem: GroupedTasksAdapter.GroupedItem
    ): Boolean {
        return oldItem == newItem
    }
} 