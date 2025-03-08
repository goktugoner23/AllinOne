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
        binding.pieChart.apply {
            description.isEnabled = false
            setUsePercentValues(true)
            setDrawEntryLabels(false)
            legend.isEnabled = true
            isDrawHoleEnabled = true
            holeRadius = 40f
            setHoleColor(Color.WHITE)
            setTransparentCircleAlpha(0)
            setNoDataText("No transactions yet")
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
        
        // Prepare data for income and expense transactions
        val incomeTransactions = transactions.filter { it.isIncome }
        val expenseTransactions = transactions.filter { !it.isIncome }
        
        val entries = ArrayList<PieEntry>()
        val colors = ArrayList<Int>()
        
        // Process income transactions by category
        if (incomeTransactions.isNotEmpty()) {
            val incomeByCategoryMap = incomeTransactions
                .groupBy { 
                    if (it.category.isNullOrEmpty()) "Uncategorized Income" else "${it.category} (Income)" 
                }
                .mapValues { (_, txns) -> txns.sumOf { it.amount } }
                .toList()
                .sortedByDescending { it.second }
            
            incomeByCategoryMap.forEach { (category, amount) ->
                entries.add(PieEntry(amount.toFloat(), category))
                
                // Assign a consistent color with income-biased colors (generally green hues)
                if (!categoryColors.containsKey(category)) {
                    val color = when {
                        category.contains("Salary") -> Color.rgb(76, 175, 80)  // Green
                        category.contains("Income") -> Color.rgb(129, 199, 132)  // Light Green
                        else -> Color.rgb(
                            100 + random.nextInt(155),  // Bias toward greener colors
                            100 + random.nextInt(155),
                            random.nextInt(100)
                        )
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
                    if (it.category.isNullOrEmpty()) "Uncategorized Expense" else "${it.category} (Expense)" 
                }
                .mapValues { (_, txns) -> txns.sumOf { it.amount } }
                .toList()
                .sortedByDescending { it.second }
            
            expenseByCategoryMap.forEach { (category, amount) ->
                entries.add(PieEntry(amount.toFloat(), category))
                
                // Assign a consistent color with expense-biased colors (generally red hues)
                if (!categoryColors.containsKey(category)) {
                    val color = when {
                        category.contains("Wing Tzun") -> Color.rgb(255, 152, 0)  // Orange
                        category.contains("Investment") -> Color.rgb(33, 150, 243)  // Blue
                        category.contains("General") -> Color.rgb(156, 39, 176)  // Purple
                        category.contains("Expense") -> Color.rgb(239, 83, 80)  // Red
                        else -> Color.rgb(
                            100 + random.nextInt(155),
                            random.nextInt(100),
                            random.nextInt(100)  // Bias toward redder colors
                        )
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
        val categories = arrayOf("Salary", "Wing Tzun", "General", "Investment")
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            categories
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