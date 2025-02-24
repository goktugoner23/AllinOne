package com.example.allinone.ui.wt

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CalendarView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.allinone.R
import com.example.allinone.adapters.WTEventAdapter
import com.example.allinone.data.WTEvent
import java.util.Calendar
import java.util.Date

class WTCalendarFragment : Fragment() {

    private lateinit var calendarView: CalendarView
    private lateinit var eventsRecyclerView: RecyclerView
    private lateinit var emptyStateText: TextView
    private lateinit var viewModel: WTCalendarViewModel
    private lateinit var adapter: WTEventAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_wt_calendar, container, false)
        
        calendarView = view.findViewById(R.id.calendarView)
        eventsRecyclerView = view.findViewById(R.id.eventsRecyclerView)
        emptyStateText = view.findViewById(R.id.emptyStateText)
        
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        viewModel = ViewModelProvider(this)[WTCalendarViewModel::class.java]
        
        setupRecyclerView()
        setupCalendarView()
        observeEvents()
    }

    private fun setupRecyclerView() {
        adapter = WTEventAdapter()
        eventsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        eventsRecyclerView.adapter = adapter
    }

    private fun setupCalendarView() {
        calendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->
            val calendar = Calendar.getInstance()
            calendar.set(year, month, dayOfMonth)
            viewModel.setSelectedDate(calendar.time)
        }
    }

    private fun observeEvents() {
        viewModel.eventsForSelectedDate.observe(viewLifecycleOwner) { events ->
            adapter.submitList(events)
            
            if (events.isEmpty()) {
                emptyStateText.visibility = View.VISIBLE
                eventsRecyclerView.visibility = View.GONE
            } else {
                emptyStateText.visibility = View.GONE
                eventsRecyclerView.visibility = View.VISIBLE
            }
        }
    }
} 