package com.example.allinone.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.allinone.R
import com.example.allinone.data.Event
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Adapter for displaying events in a RecyclerView
 */
class EventAdapter(private val onItemClicked: (Event) -> Unit) :
    ListAdapter<Event, EventAdapter.EventViewHolder>(EventDiffCallback) {

    // Date formatters
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("d MMM", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_event, parent, false)
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
        private val eventTitle: TextView = itemView.findViewById(R.id.eventTitle)
        private val eventTime: TextView = itemView.findViewById(R.id.eventTime)
        private val eventDate: TextView = itemView.findViewById(R.id.eventDate)
        private val eventTypeTag: TextView = itemView.findViewById(R.id.eventTypeTag)

        fun bind(event: Event) {
            eventTitle.text = event.title
            
            // Format the time
            eventTime.text = timeFormat.format(event.date)
            
            // Format the date (day and month)
            eventDate.text = dateFormat.format(event.date)
            
            // Set tag for event type
            eventTypeTag.text = event.type
            
            // Set different background colors based on event type
            when (event.type) {
                "Lesson" -> eventTypeTag.setBackgroundResource(R.drawable.bg_tag_blue)
                "Seminar" -> eventTypeTag.setBackgroundResource(R.drawable.bg_tag_red)
                else -> eventTypeTag.setBackgroundResource(R.drawable.bg_tag_green)
            }
        }
    }

    object EventDiffCallback : DiffUtil.ItemCallback<Event>() {
        override fun areItemsTheSame(oldItem: Event, newItem: Event): Boolean {
            return oldItem.id == newItem.id
        }
        
        override fun areContentsTheSame(oldItem: Event, newItem: Event): Boolean {
            return oldItem == newItem
        }
    }
} 