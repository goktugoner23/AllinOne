package com.example.allinone.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.allinone.R
import com.example.allinone.data.model.SourceCodeModel
import java.util.Stack

class FileStructureAdapter(
    private val onFileClick: (SourceCodeModel) -> Unit
) : RecyclerView.Adapter<FileStructureAdapter.FileViewHolder>() {

    private var items: List<SourceCodeModel> = emptyList()
    private val directoryStack = Stack<List<SourceCodeModel>>()
    
    // Track current directory for title display
    var currentDirectory: String = "Source Code"
        private set
    
    // Set initial list
    fun submitInitialList(newItems: List<SourceCodeModel>) {
        items = newItems
        directoryStack.clear()
        currentDirectory = "Source Code"
        notifyDataSetChanged()
    }
    
    // Navigate to a directory
    fun navigateToDirectory(directory: SourceCodeModel) {
        if (!directory.isDirectory) return
        
        // Save current list to stack to allow back navigation
        directoryStack.push(items)
        
        // Update current directory name
        currentDirectory = directory.name
        
        // Update the list with directory contents
        items = directory.children ?: emptyList()
        notifyDataSetChanged()
    }
    
    // Navigate back one level
    fun navigateBack(): Boolean {
        if (directoryStack.isEmpty()) return false
        
        // Get previous list from stack
        items = directoryStack.pop()
        
        // Update current directory name
        currentDirectory = if (directoryStack.isEmpty()) {
            "Source Code"
        } else {
            // Just use the current directory name
            try {
                // Use the last part of the path as the directory name
                val firstItem = items.firstOrNull()
                if (firstItem != null && firstItem.path.contains("/")) {
                    val path = firstItem.path
                    val lastSlashIndex = path.lastIndexOf('/')
                    if (lastSlashIndex >= 0 && lastSlashIndex < path.length - 1) {
                        path.substring(0, lastSlashIndex).substringAfterLast('/')
                    } else {
                        "Previous Directory"
                    }
                } else {
                    "Previous Directory"
                }
            } catch (e: Exception) {
                "Previous Directory"
            }
        }
        
        notifyDataSetChanged()
        return true
    }
    
    // Check if we're at root level
    fun isAtRootLevel(): Boolean {
        return directoryStack.isEmpty()
    }
    
    // Legacy method for compatibility
    fun submitList(newItems: List<SourceCodeModel>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_file_structure, parent, false)
        return FileViewHolder(view)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    inner class FileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val fileIcon: ImageView = itemView.findViewById(R.id.fileIcon)
        private val fileName: TextView = itemView.findViewById(R.id.fileName)

        init {
            itemView.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onFileClick(items[position])
                }
            }
        }

        fun bind(item: SourceCodeModel) {
            fileName.text = item.name
            fileIcon.setImageResource(
                if (item.isDirectory) R.drawable.ic_folder
                else R.drawable.ic_file
            )
        }
    }
} 