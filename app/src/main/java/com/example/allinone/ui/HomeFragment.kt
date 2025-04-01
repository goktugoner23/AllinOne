package com.example.allinone.ui

import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.allinone.R
import com.example.allinone.adapters.InvestmentSelectionAdapter
import com.example.allinone.config.TransactionCategories
import com.example.allinone.databinding.FragmentHomeBinding
import com.example.allinone.viewmodels.HomeViewModel
import com.github.mikephil.charting.components.MarkerView
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.PercentFormatter
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.utils.MPPointF
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import java.util.Random
import androidx.lifecycle.ViewModelProvider
import kotlin.math.absoluteValue
import androidx.navigation.fragment.findNavController

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
    private val viewModel: HomeViewModel by viewModels()
    private val random = Random()
    private val categoryColors = mutableMapOf<String, Int>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupTypeDropdowns()
        setupButtons()
        setupPieChart()
        observeTransactions()
        observeCombinedBalance()
    }

    private fun setupPieChart() {
        // Get the appropriate hole color based on the current theme
        val isNightMode = AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES
        val holeColor = if (isNightMode) {
            ContextCompat.getColor(requireContext(), R.color.navy_surface)
        } else {
            Color.WHITE
        }

        // Create custom tooltip marker
        val tooltipMarker = PieChartTooltip(requireContext(), R.layout.pie_chart_tooltip)
        tooltipMarker.chartView = binding.pieChart

        binding.pieChart.apply {
            description.isEnabled = false
            setUsePercentValues(true)
            setDrawEntryLabels(false)
            legend.isEnabled = false // Disable legend (category explanations) below the chart
            isDrawHoleEnabled = true
            holeRadius = 40f
            setHoleColor(holeColor)
            setTransparentCircleAlpha(0)
            setNoDataText("No transactions yet")
            setRotationEnabled(false)
            
            // Set no data text color
            setNoDataTextColor(ContextCompat.getColor(requireContext(), 
                if (isNightMode) R.color.white else R.color.textPrimary))
                
            // Set the custom marker
            marker = tooltipMarker
                
            animateY(1000)
            
            // Add click listener for tooltips
            setOnChartValueSelectedListener(object : com.github.mikephil.charting.listener.OnChartValueSelectedListener {
                override fun onValueSelected(e: Entry?, h: Highlight?) {
                    // When a value is selected, the marker/tooltip will automatically show
                    // due to the marker being set and the chart's internal implementation
                }
                
                override fun onNothingSelected() {
                    // Hide the marker when nothing is selected
                    highlightValue(null)
                }
            })
        }
    }

    private fun observeTransactions() {
        viewModel.allTransactions.observe(viewLifecycleOwner) { transactions ->
            // Update pie chart with category data
            updateCategoryPieChart(transactions)
        }
    }
    
    private fun observeCombinedBalance() {
        viewModel.combinedBalance.observe(viewLifecycleOwner) { (totalIncome, totalExpense, balance) ->
            // Define income and expense colors matching the pie chart theme
            val incomeColor = Color.rgb(48, 138, 52) // Dark green
            val expenseColor = Color.rgb(183, 28, 28) // Dark red
            
            // Update balance text
            binding.balanceText.text = String.format("₺%.2f", balance)
            binding.balanceText.setTextColor(
                if (balance >= 0) {
                    incomeColor // Use green from pie chart
                } else {
                    expenseColor // Use red from pie chart
                }
            )
            
            // Update income and expense text with matched colors
            binding.incomeText.text = String.format("Income: ₺%.2f", totalIncome)
            binding.incomeText.setTextColor(incomeColor)
            
            binding.expenseText.text = String.format("Expense: ₺%.2f", totalExpense)
            binding.expenseText.setTextColor(expenseColor)
        }
    }
    
    private fun updateCategoryPieChart(transactions: List<com.example.allinone.data.Transaction>) {
        if (transactions.isEmpty()) {
            binding.pieChart.setNoDataText("No transactions yet")
            binding.pieChart.invalidate()
            return
        }
        
        // Prepare data for income and expense transactions
        // For income transactions, only include positive amounts - completely exclude adjustments
        val positiveIncomeTransactions = transactions.filter { it.isIncome && it.amount > 0 }
        val expenseTransactions = transactions.filter { !it.isIncome }
        
        val entries = ArrayList<PieEntry>()
        val colors = ArrayList<Int>()
        val valueColors = ArrayList<Int>()
        
        // Define income and expense color palettes
        val incomeColors = listOf(
            Color.rgb(76, 175, 80),    // Medium green
            Color.rgb(129, 199, 132),  // Light green
            Color.rgb(48, 138, 52),    // Dark green
            Color.rgb(165, 214, 167),  // Pale green
            Color.rgb(104, 159, 56)    // Moss green
        )
        
        val expenseColors = listOf(
            Color.rgb(244, 67, 54),    // Medium red
            Color.rgb(229, 115, 115),  // Light red
            Color.rgb(183, 28, 28),    // Dark red
            Color.rgb(239, 154, 154),  // Pale red
            Color.rgb(211, 47, 47)     // Deep red
        )
        
        // Define income and expense text colors - slightly darker for readability
        val incomeTextColor = Color.rgb(27, 94, 32)    // Darker green
        val expenseTextColor = Color.rgb(183, 28, 28)  // Darker red
        
        // Process positive income transactions by category
        val incomeByCategoryMap = if (positiveIncomeTransactions.isNotEmpty()) {
            positiveIncomeTransactions
                .groupBy { 
                    if (it.category.isNullOrEmpty()) "Uncategorized Income" 
                    else "${it.category} (Income)" 
                }
                .mapValues { (_, txns) -> txns.sumOf { it.amount } }
                .toList()
                .sortedByDescending { it.second }
        } else {
            emptyList()
        }
        
        // Process expense transactions by category
        val expenseByCategoryMap = if (expenseTransactions.isNotEmpty()) {
            expenseTransactions
                .groupBy { 
                    if (it.category.isNullOrEmpty()) "Uncategorized Expense" 
                    else "${it.category} (Expense)" 
                }
                .mapValues { (_, txns) -> txns.sumOf { it.amount } }
                .toList()
                .sortedByDescending { it.second }
        } else {
            emptyList()
        }
        
        // Process positive income transactions by category
        if (incomeByCategoryMap.isNotEmpty()) {
            incomeByCategoryMap.forEachIndexed { index, (category, amount) ->
                entries.add(PieEntry(amount.toFloat(), category))
                
                // Assign a color from the income palette with wrapping
                val colorIndex = index % incomeColors.size
                colors.add(incomeColors[colorIndex])
                valueColors.add(incomeTextColor)
            }
        }
        
        // Process expense transactions by category
        if (expenseByCategoryMap.isNotEmpty()) {
            expenseByCategoryMap.forEachIndexed { index, (category, amount) ->
                entries.add(PieEntry(amount.toFloat(), category))
                
                // Assign a color from the expense palette with wrapping
                val colorIndex = index % expenseColors.size
                colors.add(expenseColors[colorIndex])
                valueColors.add(expenseTextColor)
            }
        }
        
        // Create dataset and apply to chart
        val dataSet = PieDataSet(entries, "Income & Expense Categories")
        dataSet.colors = colors
        dataSet.sliceSpace = 3f
        dataSet.selectionShift = 5f
        
        // Draw values outside the slices for better visibility
        dataSet.setValueLinePart1Length(0.8f) // Increased from 0.5f to reduce overlapping
        dataSet.setValueLinePart2Length(0.6f)
        dataSet.valueLinePart1OffsetPercentage = 90f
        dataSet.valueLineWidth = 1.5f
        
        // Set line colors based on entry type (income=green, expense=red)
        val allIncome = positiveIncomeTransactions.isNotEmpty() && expenseTransactions.isEmpty()
        val allExpense = expenseTransactions.isNotEmpty() && positiveIncomeTransactions.isEmpty()
        
        // If all entries are of one type, use that type's color for lines
        if (allIncome) {
            dataSet.valueLineColor = Color.rgb(48, 138, 52) // Dark green
        } else if (allExpense) {
            dataSet.valueLineColor = Color.rgb(183, 28, 28) // Dark red
        } else {
            // For mixed charts, create separate datasets for income and expenses
            val incomeEntries = ArrayList<PieEntry>()
            val expenseEntries = ArrayList<PieEntry>()
            val incomeValueColors = ArrayList<Int>()
            val expenseValueColors = ArrayList<Int>()
            val incomeSliceColors = ArrayList<Int>()
            val expenseSliceColors = ArrayList<Int>()
            
            // Split entries into income and expense
            val incomeEntryCount = incomeByCategoryMap.size
            
            // Split entries into income and expense
            for (i in entries.indices) {
                if (i < incomeEntryCount) {
                    incomeEntries.add(entries[i])
                    incomeValueColors.add(valueColors[i])
                    incomeSliceColors.add(colors[i])
                } else {
                    expenseEntries.add(entries[i])
                    expenseValueColors.add(valueColors[i])
                    expenseSliceColors.add(colors[i])
                }
            }
            
            // Create and configure the income dataset
            val incomeDataSet = PieDataSet(incomeEntries, "Income Categories")
            incomeDataSet.colors = incomeSliceColors
            incomeDataSet.sliceSpace = 3f
            incomeDataSet.selectionShift = 5f
            incomeDataSet.setValueLinePart1Length(0.8f)
            incomeDataSet.setValueLinePart2Length(0.6f)
            incomeDataSet.valueLinePart1OffsetPercentage = 90f
            incomeDataSet.valueLineWidth = 1.5f
            incomeDataSet.valueLineColor = Color.rgb(48, 138, 52) // Dark green
            incomeDataSet.yValuePosition = PieDataSet.ValuePosition.OUTSIDE_SLICE
            incomeDataSet.setValueTextColors(incomeValueColors)
            incomeDataSet.setValueTypeface(android.graphics.Typeface.DEFAULT_BOLD)
            
            // Create and configure the expense dataset
            val expenseDataSet = PieDataSet(expenseEntries, "Expense Categories")
            expenseDataSet.colors = expenseSliceColors
            expenseDataSet.sliceSpace = 3f
            expenseDataSet.selectionShift = 5f
            expenseDataSet.setValueLinePart1Length(0.8f)
            expenseDataSet.setValueLinePart2Length(0.6f)
            expenseDataSet.valueLinePart1OffsetPercentage = 90f
            expenseDataSet.valueLineWidth = 1.5f
            expenseDataSet.valueLineColor = Color.rgb(183, 28, 28) // Dark red
            expenseDataSet.yValuePosition = PieDataSet.ValuePosition.OUTSIDE_SLICE
            expenseDataSet.setValueTextColors(expenseValueColors)
            expenseDataSet.setValueTypeface(android.graphics.Typeface.DEFAULT_BOLD)
            
            // Combine datasets - MPAndroidChart doesn't support multiple pie datasets directly
            // So we need to use just one dataset
            dataSet.colors = colors
            dataSet.setValueLinePart1Length(0.8f)
            dataSet.setValueLinePart2Length(0.6f)
            dataSet.valueLinePart1OffsetPercentage = 90f
            dataSet.valueLineWidth = 1.5f
            dataSet.yValuePosition = PieDataSet.ValuePosition.OUTSIDE_SLICE
            
            // Use value colors for text and custom line colors for each entry
            dataSet.setValueTextColors(valueColors)
            
            // Set custom valueLineColors based on income/expense
            val lineColors = ArrayList<Int>()
            for (i in entries.indices) {
                if (i < incomeEntryCount) {
                    lineColors.add(Color.rgb(48, 138, 52)) // Dark green
                } else {
                    lineColors.add(Color.rgb(183, 28, 28)) // Dark red
                }
            }
            dataSet.valueLineColor = if (lineColors.size == 1) lineColors[0] else Color.BLACK
            
            // Apply data to the chart
            val pieData = PieData(dataSet)
            pieData.setValueFormatter(PercentFormatter(binding.pieChart))
            pieData.setValueTextSize(11f)
            
            // Apply to chart
            binding.pieChart.apply {
                this.data = pieData
                setExtraOffsets(30f, 20f, 30f, 20f)
                setUsePercentValues(true)
                minimumHeight = 600
                minimumWidth = 600
                invalidate()
            }
            return
        }
        
        dataSet.yValuePosition = PieDataSet.ValuePosition.OUTSIDE_SLICE
        
        // Apply income/expense specific colors to the values
        dataSet.setValueTextColors(valueColors)
        dataSet.setValueTypeface(android.graphics.Typeface.DEFAULT_BOLD)
        
        // Apply the data to chart with padding
        binding.pieChart.apply {
            val pieData = PieData(dataSet)
            pieData.setValueFormatter(PercentFormatter(binding.pieChart))
            pieData.setValueTextSize(11f)
            this.data = pieData
            setExtraOffsets(30f, 20f, 30f, 20f) // Increase padding to prevent labels from being cut off
            setUsePercentValues(true)
            // Ensure minimum size for the chart to give labels enough room
            minimumHeight = 600
            minimumWidth = 600
            invalidate()
        }
    }

    private fun setupTypeDropdowns() {
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            TransactionCategories.CATEGORIES
        )
        (binding.typeLayout.editText as? AutoCompleteTextView)?.setAdapter(adapter)
    }

    private fun setupButtons() {
        // Define income and expense colors matching the pie chart theme
        val incomeColor = Color.rgb(48, 138, 52) // Dark green
        val expenseColor = Color.rgb(183, 28, 28) // Dark red
        
        // Update button colors to match the transaction total colors
        binding.addIncomeButton.backgroundTintList = android.content.res.ColorStateList.valueOf(incomeColor)
        binding.addExpenseButton.backgroundTintList = android.content.res.ColorStateList.valueOf(expenseColor)
        
        binding.addIncomeButton.setOnClickListener {
            handleTransaction(true)
        }

        binding.addExpenseButton.setOnClickListener {
            handleTransaction(false)
        }
    }

    private fun handleTransaction(isIncome: Boolean) {
        val amountStr = binding.amountInput.text.toString()
        val type = (binding.typeLayout.editText as? AutoCompleteTextView)?.text.toString()
        val description = binding.descriptionInput.text?.toString()?.takeIf { it.isNotBlank() }

        if (amountStr.isBlank() || type.isBlank()) {
            showSnackbar("Please fill in all required fields")
            return
        }

        val amount = amountStr.toDoubleOrNull()
        if (amount == null || amount <= 0) {
            showSnackbar("Please enter a valid amount")
            return
        }
        
        // Special handling for investment income
        if (isIncome && type == "Investment") {
            showInvestmentSelectionDialog(amount, description)
            return
        }
        
        viewModel.addTransaction(
            amount = amount,
            type = type,
            description = description,
            isIncome = isIncome,
            category = type
        )

        clearFields()
        showSnackbar(if (isIncome) "Income added" else "Expense added")
    }
    
    private fun showInvestmentSelectionDialog(amount: Double, description: String?) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(
            R.layout.dialog_select_investment, null
        )
        
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setCancelable(true)
            .create()
            
        val recyclerView = dialogView.findViewById<RecyclerView>(R.id.investmentsRecyclerView)
        val emptyStateText = dialogView.findViewById<TextView>(R.id.emptyStateText)
        val cancelButton = dialogView.findViewById<Button>(R.id.cancelButton)
        val newInvestmentButton = dialogView.findViewById<Button>(R.id.newInvestmentButton)
        
        // Set up the recyclerview with adapter
        val adapter = InvestmentSelectionAdapter { investment ->
            // Add income to the selected investment
            viewModel.addIncomeToInvestment(amount, investment, description)
            dialog.dismiss()
            clearFields()
            showSnackbar("Investment income added")
        }
        
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
        
        // Get all investments and update the adapter - include past investments
        val investments = viewModel.allInvestments.value ?: emptyList()
        adapter.submitList(investments)
        
        // Show empty state if no investments
        if (investments.isEmpty()) {
            recyclerView.visibility = View.GONE
            emptyStateText.visibility = View.VISIBLE
            emptyStateText.text = "No investments found. Create one first."
        } else {
            recyclerView.visibility = View.VISIBLE
            emptyStateText.visibility = View.GONE
        }
        
        // Button click listeners
        cancelButton.setOnClickListener {
            dialog.dismiss()
        }
        
        newInvestmentButton.setOnClickListener {
            // Navigate to investments page
            findNavController().navigate(R.id.nav_investments)
            dialog.dismiss()
        }
        
        dialog.show()
    }

    private fun clearFields() {
        binding.amountInput.text?.clear()
        (binding.typeLayout.editText as? AutoCompleteTextView)?.text?.clear()
        binding.descriptionInput.text?.clear()
    }

    private fun showSnackbar(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
} 