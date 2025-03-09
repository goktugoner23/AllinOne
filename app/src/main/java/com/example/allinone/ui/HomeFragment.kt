package com.example.allinone.ui

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.example.allinone.R
import com.example.allinone.config.TransactionCategories
import com.example.allinone.databinding.FragmentHomeBinding
import com.example.allinone.viewmodels.HomeViewModel
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.PercentFormatter
import com.github.mikephil.charting.utils.ColorTemplate
import com.google.android.material.snackbar.Snackbar
import java.util.Random
import androidx.lifecycle.ViewModelProvider
import kotlin.math.absoluteValue
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat

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

        binding.pieChart.apply {
            description.isEnabled = false
            setUsePercentValues(true)
            setDrawEntryLabels(false)
            legend.isEnabled = true
            isDrawHoleEnabled = true
            holeRadius = 40f
            setHoleColor(holeColor)
            setTransparentCircleAlpha(0)
            setNoDataText("No transactions yet")
            
            // Set theme-appropriate text colors
            legend.textColor = ContextCompat.getColor(requireContext(), 
                if (isNightMode) R.color.white else R.color.textPrimary)
            
            // Set no data text color
            setNoDataTextColor(ContextCompat.getColor(requireContext(), 
                if (isNightMode) R.color.white else R.color.textPrimary))
                
            animateY(1000)
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
            // Update balance text
            binding.balanceText.text = String.format("₺%.2f", balance)
            binding.balanceText.setTextColor(
                if (balance >= 0) {
                    requireContext().getColor(android.R.color.holo_green_dark)
                } else {
                    requireContext().getColor(android.R.color.holo_red_dark)
                }
            )
            
            // Update income and expense text
            binding.incomeText.text = String.format("Income: ₺%.2f", totalIncome)
            binding.expenseText.text = String.format("Expense: ₺%.2f", totalExpense)
        }
    }
    
    private fun updateCategoryPieChart(transactions: List<com.example.allinone.data.Transaction>) {
        if (transactions.isEmpty()) {
            binding.pieChart.setNoDataText("No transactions yet")
            binding.pieChart.invalidate()
            return
        }
        
        // Check if dark theme is enabled
        val isNightMode = AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES
        
        // Prepare data for income and expense transactions
        // For income transactions, only include positive amounts - completely exclude adjustments
        val positiveIncomeTransactions = transactions.filter { it.isIncome && it.amount > 0 }
        val expenseTransactions = transactions.filter { !it.isIncome }
        
        val entries = ArrayList<PieEntry>()
        val colors = ArrayList<Int>()
        
        // Process positive income transactions by category
        if (positiveIncomeTransactions.isNotEmpty()) {
            val incomeByCategoryMap = positiveIncomeTransactions
                .groupBy { 
                    if (it.category.isNullOrEmpty()) "Uncategorized Income" 
                    else "${it.category} (Income)" 
                }
                .mapValues { (_, txns) -> txns.sumOf { it.amount } }
                .toList()
                .sortedByDescending { it.second }
            
            incomeByCategoryMap.forEach { (category, amount) ->
                entries.add(PieEntry(amount.toFloat(), category))
                
                // Assign a consistent color with income-biased colors
                if (!categoryColors.containsKey(category)) {
                    val color = when {
                        category.contains("Salary") -> ContextCompat.getColor(requireContext(), 
                            if (isNightMode) R.color.navy_accent else R.color.start_color)
                        category.contains("Income") -> if (isNightMode) 
                            Color.rgb(77, 168, 218) else Color.rgb(129, 199, 132)  // Light Blue/Green
                        category.contains("Wing Tzun") -> ContextCompat.getColor(requireContext(), 
                            if (isNightMode) R.color.navy_accent else R.color.lesson_event_color)
                        else -> if (isNightMode) {
                            // Professional blue-palette random colors for dark theme
                            Color.rgb(
                                50 + random.nextInt(50),  // Dark-medium blue range
                                100 + random.nextInt(100),
                                150 + random.nextInt(100)
                            )
                        } else {
                            // Green-biased colors for light theme
                            Color.rgb(
                                100 + random.nextInt(155),
                                100 + random.nextInt(155),
                                random.nextInt(100)
                            )
                        }
                    }
                    categoryColors[category] = color
                }
                
                colors.add(categoryColors[category]!!)
            }
        }
        
        // Process expense transactions by category
        if (expenseTransactions.isNotEmpty()) {
            val expenseByCategoryMap = expenseTransactions
                .groupBy { 
                    if (it.category.isNullOrEmpty()) "Uncategorized Expense" 
                    else "${it.category} (Expense)" 
                }
                .mapValues { (_, txns) -> txns.sumOf { it.amount } }
                .toList()
                .sortedByDescending { it.second }
            
            expenseByCategoryMap.forEach { (category, amount) ->
                entries.add(PieEntry(amount.toFloat(), category))
                
                // Assign a consistent color with expense-biased colors
                if (!categoryColors.containsKey(category)) {
                    val color = when {
                        category.contains("Wing Tzun") -> if (isNightMode)
                            Color.rgb(230, 145, 56) else Color.rgb(255, 152, 0)  // Orange
                        category.contains("Investment") -> if (isNightMode)
                            Color.rgb(41, 121, 255) else Color.rgb(33, 150, 243)  // Blue
                        category.contains("General") -> if (isNightMode)
                            Color.rgb(165, 85, 236) else Color.rgb(156, 39, 176)  // Purple
                        category.contains("Expense") -> if (isNightMode)
                            Color.rgb(247, 86, 86) else Color.rgb(239, 83, 80)  // Red
                        else -> if (isNightMode) {
                            // Professional warm-palette random colors for dark theme
                            Color.rgb(
                                180 + random.nextInt(75),  // Reddish tones
                                100 + random.nextInt(80),
                                50 + random.nextInt(50)
                            )
                        } else {
                            // Red-biased colors for light theme
                            Color.rgb(
                                100 + random.nextInt(155),
                                random.nextInt(100),
                                random.nextInt(100)
                            )
                        }
                    }
                    categoryColors[category] = color
                }
                
                colors.add(categoryColors[category]!!)
            }
        }
        
        // Create dataset and apply to chart
        val dataSet = PieDataSet(entries, "Income & Expense Categories")
        dataSet.colors = colors
        dataSet.sliceSpace = 3f
        dataSet.selectionShift = 5f
        
        val data = PieData(dataSet)
        data.setValueFormatter(PercentFormatter(binding.pieChart))
        data.setValueTextSize(12f)
        data.setValueTextColor(Color.WHITE)
        
        binding.pieChart.data = data
        binding.pieChart.invalidate()
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