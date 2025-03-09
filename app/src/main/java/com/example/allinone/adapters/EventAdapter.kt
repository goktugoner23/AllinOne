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

class EventAdapter(private val onItemClicked: (WTEvent) -> Unit) : 
    ListAdapter<WTEvent, EventAdapter.EventViewHolder>(EventDiffCallback) {

    // Date formatters
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_event, parent, false)
        return EventViewHolder(view)
    }

    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
        val event = getItem(position)
        holder.bind(event)
        holder.itemView.setOnClickListener {
            onItemClicked(event)
        }
    }

    inner class EventViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val eventTime: TextView = itemView.findViewById(R.id.eventTime)
        private val eventTitle: TextView = itemView.findViewById(R.id.eventTitle)
        private val eventType: TextView = itemView.findViewById(R.id.eventType)
        private val eventDate: TextView = itemView.findViewById(R.id.eventDate)
        private val eventDescription: TextView = itemView.findViewById(R.id.eventDescription)

        fun bind(event: WTEvent) {
            eventTime.text = timeFormat.format(event.date)
            eventTitle.text = event.title
            eventType.text = event.type
            eventDate.text = dateFormat.format(event.date)
            
            if (event.description != null && event.description.isNotEmpty()) {
                eventDescription.text = event.description
                eventDescription.visibility = View.VISIBLE
            } else {
                eventDescription.visibility = View.GONE
            }
        }
    }

    object EventDiffCallback : DiffUtil.ItemCallback<WTEvent>() {
        override fun areItemsTheSame(oldItem: WTEvent, newItem: WTEvent): Boolean {
            // For simplicity, comparing by date and title
            return oldItem.date == newItem.date && oldItem.title == newItem.title
        }

        override fun areContentsTheSame(oldItem: WTEvent, newItem: WTEvent): Boolean {
            return oldItem == newItem
        }
    }
} 