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

class WTEventAdapter : ListAdapter<WTEvent, WTEventAdapter.EventViewHolder>(EventDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_wt_event, parent, false)
        return EventViewHolder(view)
    }

    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
        val event = getItem(position)
        holder.bind(event)
    }

    class EventViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleTextView: TextView = itemView.findViewById(R.id.eventTitle)
        private val descriptionTextView: TextView = itemView.findViewById(R.id.eventDescription)
        private val dateTextView: TextView = itemView.findViewById(R.id.eventDate)
        private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

        fun bind(event: WTEvent) {
            titleTextView.text = event.title
            descriptionTextView.text = event.description
            dateTextView.text = dateFormat.format(event.date)
            
            // Set background color based on event type
            val backgroundColor = when (event.type) {
                "expiration" -> itemView.context.getColor(R.color.expiration_color)
                "start" -> itemView.context.getColor(R.color.start_color)
                else -> itemView.context.getColor(R.color.default_event_color)
            }
            itemView.setBackgroundColor(backgroundColor)
        }
    }

    class EventDiffCallback : DiffUtil.ItemCallback<WTEvent>() {
        override fun areItemsTheSame(oldItem: WTEvent, newItem: WTEvent): Boolean {
            return oldItem.id == newItem.id && oldItem.type == newItem.type
        }

        override fun areContentsTheSame(oldItem: WTEvent, newItem: WTEvent): Boolean {
            return oldItem == newItem
        }
    }
} 