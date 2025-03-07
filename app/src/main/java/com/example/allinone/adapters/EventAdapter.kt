package com.example.allinone.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.allinone.R
import com.example.allinone.data.WTEvent
import java.text.SimpleDateFormat
import java.util.Locale

class EventAdapter(
    private val onItemClick: (WTEvent) -> Unit
) : ListAdapter<WTEvent, EventAdapter.EventViewHolder>(EventDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_wt_event, parent, false)
        return EventViewHolder(view, onItemClick)
    }

    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
        val event = getItem(position)
        holder.bind(event)
    }

    class EventViewHolder(
        itemView: View,
        private val onItemClick: (WTEvent) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val titleTextView: TextView = itemView.findViewById(R.id.eventTitle)
        private val descriptionTextView: TextView = itemView.findViewById(R.id.eventDescription)
        private val timeTextView: TextView = itemView.findViewById(R.id.eventTime)
        private val dateTimeFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())

        fun bind(event: WTEvent) {
            titleTextView.text = event.title
            descriptionTextView.text = event.description ?: ""
            timeTextView.text = dateTimeFormat.format(event.date)
            
            // Set background color based on event type
            val backgroundColor = when (event.type) {
                "Lesson" -> itemView.context.getColor(R.color.lesson_event_color)
                else -> itemView.context.getColor(R.color.default_event_color)
            }
            itemView.setBackgroundColor(backgroundColor)
            
            // Set click listener
            itemView.setOnClickListener {
                onItemClick(event)
            }
        }
    }

    class EventDiffCallback : DiffUtil.ItemCallback<WTEvent>() {
        override fun areItemsTheSame(oldItem: WTEvent, newItem: WTEvent): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: WTEvent, newItem: WTEvent): Boolean {
            return oldItem == newItem
        }
    }
} 