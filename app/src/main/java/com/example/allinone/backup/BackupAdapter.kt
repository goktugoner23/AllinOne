package com.example.allinone.backup

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.allinone.databinding.ItemBackupBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BackupAdapter(
    private val onShareClick: (File) -> Unit,
    private val onDeleteClick: (File) -> Unit,
    private val onRestoreClick: (File) -> Unit
) : ListAdapter<File, BackupAdapter.BackupViewHolder>(BackupDiffCallback()) {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BackupViewHolder {
        val binding = ItemBackupBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return BackupViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: BackupViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    inner class BackupViewHolder(private val binding: ItemBackupBinding) :
        RecyclerView.ViewHolder(binding.root) {
        
        fun bind(file: File) {
            // Format the file name
            val fileName = file.name.replace("allinone_backup_", "").replace(".zip", "")
            
            // Parse the timestamp from the file name
            val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
            val displayFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.US)
            
            try {
                val date = dateFormat.parse(fileName)
                binding.backupName.text = displayFormat.format(date ?: Date())
            } catch (e: Exception) {
                binding.backupName.text = fileName
            }
            
            // Set file size
            val fileSizeKb = file.length() / 1024
            val fileSizeMb = fileSizeKb / 1024.0
            
            binding.backupSize.text = if (fileSizeMb < 1) {
                "$fileSizeKb KB"
            } else {
                String.format("%.2f MB", fileSizeMb)
            }
            
            // Set click listeners
            binding.shareButton.setOnClickListener {
                onShareClick(file)
            }
            
            binding.deleteButton.setOnClickListener {
                onDeleteClick(file)
            }
            
            binding.restoreButton.setOnClickListener {
                onRestoreClick(file)
            }
        }
    }
    
    private class BackupDiffCallback : DiffUtil.ItemCallback<File>() {
        override fun areItemsTheSame(oldItem: File, newItem: File): Boolean {
            return oldItem.absolutePath == newItem.absolutePath
        }
        
        override fun areContentsTheSame(oldItem: File, newItem: File): Boolean {
            return oldItem.lastModified() == newItem.lastModified() &&
                oldItem.length() == newItem.length()
        }
    }
} 