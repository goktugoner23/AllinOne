package com.example.allinone.adapters

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.allinone.R
import com.example.allinone.utils.LogcatHelper
import com.google.android.material.card.MaterialCardView

class LogEntryAdapter(
    private val context: Context,
    private var logEntries: List<LogcatHelper.LogEntry> = emptyList()
) : RecyclerView.Adapter<LogEntryAdapter.LogEntryViewHolder>() {

    class LogEntryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardView: MaterialCardView = itemView as MaterialCardView
        val timestampText: TextView = itemView.findViewById(R.id.timestampText)
        val levelTagText: TextView = itemView.findViewById(R.id.levelTagText)
        val messageText: TextView = itemView.findViewById(R.id.messageText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogEntryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_log_entry, parent, false)
        return LogEntryViewHolder(view)
    }

    override fun onBindViewHolder(holder: LogEntryViewHolder, position: Int) {
        val logEntry = logEntries[position]
        
        // Set timestamp
        holder.timestampText.text = logEntry.formattedTimestamp
        
        // Set level and tag
        holder.levelTagText.text = "[${logEntry.level}/${logEntry.tag}]"
        
        // Set color based on level
        val cardColor = when (logEntry.level) {
            "E" -> ContextCompat.getColor(context, R.color.error_light)
            "W" -> ContextCompat.getColor(context, R.color.warning_light)
            else -> ContextCompat.getColor(context, android.R.color.white)
        }
        holder.cardView.setCardBackgroundColor(cardColor)
        
        // Set message
        holder.messageText.text = logEntry.message

        // Set long press listener to copy log entry
        holder.cardView.setOnLongClickListener {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Error Log", logEntry.toString())
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "Log entry copied to clipboard", Toast.LENGTH_SHORT).show()
            true
        }
    }

    override fun getItemCount(): Int = logEntries.size

    fun updateData(newLogEntries: List<LogcatHelper.LogEntry>) {
        this.logEntries = newLogEntries
        notifyDataSetChanged()
    }
} 