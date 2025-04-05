package com.example.allinone.ui

import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.example.allinone.R
import com.example.allinone.config.TransactionCategories
import com.example.allinone.databinding.FragmentTransactionsOverviewBinding
import com.example.allinone.viewmodels.HomeViewModel
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.PercentFormatter
import com.github.mikephil.charting.highlight.Highlight
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.Random

class TransactionsOverviewFragment : Fragment() {
    private var _binding: FragmentTransactionsOverviewBinding? = null
    private val binding get() = _binding!!
    private val viewModel: HomeViewModel by viewModels()
    private val random = Random()
    private val categoryColors = mutableMapOf<String, Int>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTransactionsOverviewBinding.inflate(inflater, container, false)
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
        
        dataSet.yValuePosition = PieDataSet.ValuePosition.OUTSIDE_SLICE
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
        val amount = binding.amountInput.text.toString().toDoubleOrNull()
        val category = (binding.typeLayout.editText as? AutoCompleteTextView)?.text.toString()
        val description = binding.descriptionInput.text.toString()
        
        if (amount == null || amount <= 0) {
            showError("Please enter a valid amount")
            return
        }
        
        if (category.isEmpty()) {
            showError("Please select a category")
            return
        }
        
        // Process the transaction
        viewModel.addTransaction(amount, category, description, isIncome, category)
        
        // Clear inputs
        binding.amountInput.text?.clear()
        binding.descriptionInput.text?.clear()
        (binding.typeLayout.editText as? AutoCompleteTextView)?.text?.clear()
        
        // Show success message
        val message = if (isIncome) "Income added" else "Expense added"
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    }
    
    private fun showError(message: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Error")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
} 