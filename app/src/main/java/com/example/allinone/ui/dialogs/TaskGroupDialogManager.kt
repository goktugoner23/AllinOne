package com.example.allinone.ui.dialogs

import android.app.AlertDialog
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.allinone.R
import com.example.allinone.data.TaskGroup
import com.example.allinone.databinding.DialogTaskGroupBinding
import com.google.android.material.button.MaterialButton
import java.util.Date

class TaskGroupDialogManager(private val context: Context) {
    
    interface TaskGroupDialogListener {
        fun onTaskGroupCreated(taskGroup: TaskGroup)
        fun onTaskGroupUpdated(taskGroup: TaskGroup)
        fun onTaskGroupDeleted(taskGroupId: Long)
    }
    
    private var selectedColor: String = "#2196F3" // Default blue
    private val colorMap = mapOf(
        R.id.color_blue to "#2196F3",
        R.id.color_green to "#4CAF50",
        R.id.color_red to "#F44336",
        R.id.color_orange to "#FF9800",
        R.id.color_purple to "#9C27B0"
    )
    
    fun showCreateDialog(listener: TaskGroupDialogListener) {
        showDialog(null, listener)
    }
    
    fun showEditDialog(taskGroup: TaskGroup, listener: TaskGroupDialogListener) {
        showDialog(taskGroup, listener)
    }
    
    private fun showDialog(existingGroup: TaskGroup?, listener: TaskGroupDialogListener) {
        val binding = DialogTaskGroupBinding.inflate(LayoutInflater.from(context))
        
        // Set up dialog title and button visibility
        if (existingGroup != null) {
            binding.dialogTitle.text = context.getString(R.string.edit_task_group)
            binding.btnDelete.visibility = View.VISIBLE
            binding.groupTitleEdit.setText(existingGroup.title)
            binding.groupDescriptionEdit.setText(existingGroup.description)
            selectedColor = existingGroup.color
        } else {
            binding.dialogTitle.text = context.getString(R.string.create_task_group)
            binding.btnDelete.visibility = View.GONE
            selectedColor = "#2196F3" // Default blue
        }
        
        // Set up color selection
        setupColorSelection(binding)
        updateColorSelection(binding)
        
        val dialog = AlertDialog.Builder(context)
            .setView(binding.root)
            .setCancelable(true)
            .create()
        
        // Set up button listeners
        binding.btnCancel.setOnClickListener {
            dialog.dismiss()
        }
        
        binding.btnSave.setOnClickListener {
            val title = binding.groupTitleEdit.text?.toString()?.trim()
            val description = binding.groupDescriptionEdit.text?.toString()?.trim()
            
            if (title.isNullOrEmpty()) {
                binding.groupTitleLayout.error = context.getString(R.string.field_required)
                return@setOnClickListener
            }
            
            binding.groupTitleLayout.error = null
            
            if (existingGroup != null) {
                // Update existing group
                val updatedGroup = existingGroup.copy(
                    title = title,
                    description = description,
                    color = selectedColor
                )
                listener.onTaskGroupUpdated(updatedGroup)
            } else {
                // Create new group
                val newGroup = TaskGroup(
                    id = 0, // Will be set by FirebaseIdManager
                    title = title,
                    description = description,
                    color = selectedColor,
                    createdAt = Date(),
                    isCompleted = false
                )
                listener.onTaskGroupCreated(newGroup)
            }
            
            dialog.dismiss()
        }
        
        binding.btnDelete.setOnClickListener {
            dialog.dismiss()
            showDeleteConfirmationDialog(existingGroup!!, listener)
        }
        
        dialog.show()
    }
    
    private fun setupColorSelection(binding: DialogTaskGroupBinding) {
        val colorViews = listOf(
            binding.colorBlue,
            binding.colorGreen,
            binding.colorRed,
            binding.colorOrange,
            binding.colorPurple
        )
        
        colorViews.forEach { colorView ->
            colorView.setOnClickListener {
                selectedColor = colorMap[colorView.id] ?: "#2196F3"
                updateColorSelection(binding)
            }
        }
    }
    
    private fun updateColorSelection(binding: DialogTaskGroupBinding) {
        val colorViews = mapOf(
            "#2196F3" to binding.colorBlue,
            "#4CAF50" to binding.colorGreen,
            "#F44336" to binding.colorRed,
            "#FF9800" to binding.colorOrange,
            "#9C27B0" to binding.colorPurple
        )
        
        colorViews.forEach { (color, view) ->
            if (color == selectedColor) {
                // Add selection indicator (stroke)
                view.background = ContextCompat.getDrawable(context, R.drawable.selected_circle_shape)
            } else {
                // Normal circle shape
                view.background = ContextCompat.getDrawable(context, R.drawable.circle_shape)
            }
        }
    }
    
    private fun showDeleteConfirmationDialog(taskGroup: TaskGroup, listener: TaskGroupDialogListener) {
        AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.delete_group))
            .setMessage(context.getString(R.string.delete_group_confirmation))
            .setPositiveButton(context.getString(R.string.delete)) { _, _ ->
                listener.onTaskGroupDeleted(taskGroup.id)
            }
            .setNegativeButton(context.getString(R.string.cancel), null)
            .show()
    }
} 