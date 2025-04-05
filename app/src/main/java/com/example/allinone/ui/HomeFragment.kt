package com.example.allinone.ui

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.allinone.R
import com.example.allinone.databinding.FragmentHomeBinding
import com.github.mikephil.charting.components.MarkerView
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.utils.MPPointF

// Custom MarkerView for pie chart tooltip
class PieChartTooltip(context: android.content.Context, layoutResource: Int) : MarkerView(context, layoutResource) {
    private val tooltipText: TextView = findViewById(R.id.tooltipText)
    
    override fun refreshContent(e: Entry?, highlight: Highlight?) {
        if (e is PieEntry) {
            val formattedValue = String.format("₺%.2f", e.value)
            tooltipText.text = "${e.label}\n$formattedValue"
        }
        super.refreshContent(e, highlight)
    }
    
    override fun getOffset(): MPPointF {
        // Position tooltip higher above the selected segment
        return MPPointF(-(width / 2f), -height.toFloat() - 30f)
    }
}

class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Load TransactionsOverviewFragment directly
        if (savedInstanceState == null) {
            childFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, TransactionsOverviewFragment())
                .commit()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
} 