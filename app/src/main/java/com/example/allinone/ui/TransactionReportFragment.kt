package com.example.allinone.ui

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.allinone.R
import com.example.allinone.adapters.TransactionReportAdapter
import com.example.allinone.config.TransactionCategories
import com.example.allinone.data.Transaction
import com.example.allinone.databinding.FragmentTransactionReportBinding
import com.example.allinone.firebase.FirebaseRepository
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

enum class GroupingPeriod {
    DAYS, WEEKS, MONTHS
}

class TransactionReportFragment : BaseFragment() {
    private var _binding: FragmentTransactionReportBinding? = null
    private val binding get() = _binding!!

    private val firebaseRepository by lazy { FirebaseRepository(requireContext()) }
    private val transactionAdapter by lazy { TransactionReportAdapter() }
    
    private val currencyFormatter = NumberFormat.getCurrencyInstance().apply {
        maximumFractionDigits = 2
        minimumFractionDigits = 2
    }
    
    // Filter options
    private val dateRangeOptions = arrayOf("Last 7 Days", "Last 30 Days", "Last 90 Days", "This Year", "All Time")
    private var selectedDateRange = "Last 30 Days"
    private var selectedCategory = "All Categories"
    private var showIncome = true
    private var showExpense = true
    
    // Filtered transactions
    private var allTransactions: List<Transaction> = emptyList()
    private var filteredTransactions: List<Transaction> = emptyList()
    
    // Pagination
    private val PAGE_SIZE = 5
    private var currentPage = 0
    private var totalPages = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTransactionReportBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupRecyclerView()
        setupFilterOptions()
        setupChipListeners()
        setupApplyButton()
        setupPaginationButtons()
        observeTransactions()
    }
    
    private fun setupRecyclerView() {
        binding.transactionsRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = transactionAdapter
        }
    }
    
    private fun setupFilterOptions() {
        // Date range dropdown
        val dateRangeAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            dateRangeOptions
        )
        (binding.dateRangeLayout.editText as? AutoCompleteTextView)?.setAdapter(dateRangeAdapter)
        (binding.dateRangeLayout.editText as? AutoCompleteTextView)?.setText(selectedDateRange, false)
        (binding.dateRangeLayout.editText as? AutoCompleteTextView)?.setOnItemClickListener { _, _, position, _ ->
            selectedDateRange = dateRangeOptions[position]
        }
        
        // Category dropdown
        val allCategories = mutableListOf("All Categories")
        allCategories.addAll(TransactionCategories.CATEGORIES)
        
        val categoryAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            allCategories
        )
        (binding.categoryLayout.editText as? AutoCompleteTextView)?.setAdapter(categoryAdapter)
        (binding.categoryLayout.editText as? AutoCompleteTextView)?.setText(selectedCategory, false)
        (binding.categoryLayout.editText as? AutoCompleteTextView)?.setOnItemClickListener { _, _, position, _ ->
            selectedCategory = allCategories[position]
        }
    }
    
    private fun setupChipListeners() {
        binding.incomeChip.setOnCheckedChangeListener { _, isChecked ->
            showIncome = isChecked
        }
        
        binding.expenseChip.setOnCheckedChangeListener { _, isChecked ->
            showExpense = isChecked
        }
    }
    
    private fun setupApplyButton() {
        binding.applyFiltersButton.setOnClickListener {
            applyFilters()
        }
    }
    
    private fun setupPaginationButtons() {
        binding.prevPageButton.setOnClickListener {
            if (currentPage > 0) {
                currentPage--
                updateTransactionsList()
            }
        }
        
        binding.nextPageButton.setOnClickListener {
            if (currentPage < totalPages - 1) {
                currentPage++
                updateTransactionsList()
            }
        }
    }
    
    private fun observeTransactions() {
        lifecycleScope.launch {
            firebaseRepository.transactions.collectLatest { transactions ->
                allTransactions = transactions
                applyFilters()
            }
        }
    }
    
    private fun applyFilters() {
        // Filter by date range
        val startDate = getStartDateFromRange(selectedDateRange)
        
        // Apply all filters
        filteredTransactions = allTransactions.filter { transaction ->
            val passesDateFilter = startDate == null || transaction.date.after(startDate) || transaction.date == startDate
            val passesTypeFilter = (showIncome && transaction.isIncome) || (showExpense && !transaction.isIncome)
            val passesCategoryFilter = selectedCategory == "All Categories" || transaction.category == selectedCategory
            
            passesDateFilter && passesTypeFilter && passesCategoryFilter
        }.sortedByDescending { it.date }
        
        currentPage = 0 // Reset to first page
        
        // Update UI with filtered transactions
        updateTransactionsList()
        updateSummarySection()
        updateChart()
    }
    
    private fun getStartDateFromRange(range: String): Date? {
        val calendar = Calendar.getInstance()
        
        when (range) {
            "Last 7 Days" -> calendar.add(Calendar.DAY_OF_MONTH, -7)
            "Last 30 Days" -> calendar.add(Calendar.DAY_OF_MONTH, -30)
            "Last 90 Days" -> calendar.add(Calendar.DAY_OF_MONTH, -90)
            "This Year" -> {
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.MONTH, Calendar.JANUARY)
            }
            "All Time" -> return null
        }
        
        return calendar.time
    }
    
    private fun updateTransactionsList() {
        if (filteredTransactions.isEmpty()) {
            binding.transactionsRecyclerView.visibility = View.GONE
            binding.emptyTransactionsText.visibility = View.VISIBLE
            binding.paginationControls.visibility = View.GONE
        } else {
            binding.transactionsRecyclerView.visibility = View.VISIBLE
            binding.emptyTransactionsText.visibility = View.GONE
            
            // Calculate pagination
            totalPages = Math.ceil(filteredTransactions.size.toDouble() / PAGE_SIZE).toInt()
            
            // Make sure current page is valid
            if (currentPage >= totalPages) {
                currentPage = totalPages - 1
            }
            if (currentPage < 0) {
                currentPage = 0
            }
            
            // Update page indicator
            binding.pageIndicator.text = "Page ${currentPage + 1} of $totalPages"
            
            // Enable/disable pagination buttons
            binding.prevPageButton.isEnabled = currentPage > 0
            binding.nextPageButton.isEnabled = currentPage < totalPages - 1
            
            // Show pagination controls if there's more than one page
            binding.paginationControls.visibility = if (totalPages > 1) View.VISIBLE else View.GONE
            
            // Get current page of transactions
            val startIndex = currentPage * PAGE_SIZE
            val endIndex = minOf(startIndex + PAGE_SIZE, filteredTransactions.size)
            val pagedTransactions = filteredTransactions.subList(startIndex, endIndex)
            
            // Update adapter with current page
            transactionAdapter.updateTransactions(pagedTransactions)
        }
    }
    
    private fun updateSummarySection() {
        val totalIncome = filteredTransactions.filter { it.isIncome }.sumOf { it.amount }
        val totalExpense = filteredTransactions.filter { !it.isIncome }.sumOf { it.amount }
        val balance = totalIncome - totalExpense
        
        binding.totalIncomeText.text = currencyFormatter.format(totalIncome)
        binding.totalExpenseText.text = currencyFormatter.format(totalExpense)
        binding.balanceText.text = currencyFormatter.format(balance)
        
        // Set balance text color based on positive/negative
        if (balance < 0) {
            binding.balanceText.setTextColor(ContextCompat.getColor(requireContext(), R.color.red))
        } else {
            binding.balanceText.setTextColor(ContextCompat.getColor(requireContext(), R.color.green))
        }
    }
    
    private fun updateChart() {
        if (filteredTransactions.isEmpty()) {
            binding.lineChart.setNoDataText("No data available")
            binding.lineChart.invalidate()
            return
        }
        
        // Group transactions by day/week/month depending on date range
        val groupedTransactions = groupTransactionsByTimePeriod()
        
        // Prepare data for chart
        val incomeEntries = mutableListOf<Entry>()
        val expenseEntries = mutableListOf<Entry>()
        val labels = mutableListOf<String>()
        
        groupedTransactions.forEachIndexed { index, (date, transactions) ->
            val totalIncome = transactions.filter { it.isIncome }.sumOf { it.amount }.toFloat()
            val totalExpense = transactions.filter { !it.isIncome }.sumOf { it.amount }.toFloat()
            
            incomeEntries.add(Entry(index.toFloat(), totalIncome))
            expenseEntries.add(Entry(index.toFloat(), totalExpense))
            
            // Format date for label
            val dateFormat = SimpleDateFormat("MMM dd", Locale.getDefault())
            labels.add(dateFormat.format(date))
        }
        
        // Create datasets
        val incomeDataSet = LineDataSet(incomeEntries, "Income").apply {
            color = ContextCompat.getColor(requireContext(), R.color.green)
            setCircleColor(ContextCompat.getColor(requireContext(), R.color.green))
            lineWidth = 2f
            circleRadius = 4f
            setDrawCircleHole(false)
            mode = LineDataSet.Mode.CUBIC_BEZIER
            setDrawValues(false)
        }
        
        val expenseDataSet = LineDataSet(expenseEntries, "Expense").apply {
            color = ContextCompat.getColor(requireContext(), R.color.red)
            setCircleColor(ContextCompat.getColor(requireContext(), R.color.red))
            lineWidth = 2f
            circleRadius = 4f
            setDrawCircleHole(false)
            mode = LineDataSet.Mode.CUBIC_BEZIER
            setDrawValues(false)
        }
        
        // Create line data with both datasets
        val lineData = LineData(incomeDataSet, expenseDataSet)
        
        // Configure chart
        binding.lineChart.apply {
            data = lineData
            description.isEnabled = false
            legend.isEnabled = true
            
            // Configure X axis (dates)
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                valueFormatter = IndexAxisValueFormatter(labels)
                granularity = 1f
                setDrawGridLines(false)
                textColor = Color.GRAY
            }
            
            // Configure Y axis (amounts)
            axisLeft.apply {
                setDrawGridLines(true)
                textColor = Color.GRAY
            }
            
            axisRight.isEnabled = false
            
            // Set zoom and interaction
            setTouchEnabled(true)
            isDragEnabled = true
            setScaleEnabled(true)
            setPinchZoom(true)
            
            // Refresh the chart
            animateX(1000)
            invalidate()
        }
    }
    
    private fun groupTransactionsByTimePeriod(): List<Pair<Date, List<Transaction>>> {
        val calendar = Calendar.getInstance()
        
        // Determine grouping period based on selected date range
        val groupingPeriod = when (selectedDateRange) {
            "Last 7 Days" -> GroupingPeriod.DAYS
            "Last 30 Days" -> GroupingPeriod.DAYS
            "Last 90 Days" -> GroupingPeriod.WEEKS
            "This Year" -> GroupingPeriod.MONTHS
            else -> GroupingPeriod.MONTHS
        }
        
        // Group transactions by period
        val groupedTransactions = mutableMapOf<String, MutableList<Transaction>>()
        val dateFormat = when (groupingPeriod) {
            GroupingPeriod.DAYS -> SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            GroupingPeriod.WEEKS -> SimpleDateFormat("yyyy-'W'ww", Locale.getDefault())
            GroupingPeriod.MONTHS -> SimpleDateFormat("yyyy-MM", Locale.getDefault())
        }
        
        // Group transactions
        for (transaction in filteredTransactions) {
            val key = dateFormat.format(transaction.date)
            if (!groupedTransactions.containsKey(key)) {
                groupedTransactions[key] = mutableListOf()
            }
            groupedTransactions[key]?.add(transaction)
        }
        
        // Convert to list of pairs and sort by date
        return groupedTransactions.map { (dateKey, transactions) ->
            // Parse date key back to Date object for sorting
            val date = when (groupingPeriod) {
                GroupingPeriod.DAYS -> dateFormat.parse(dateKey) ?: Date()
                GroupingPeriod.WEEKS -> {
                    // Parse week format (yyyy-'W'ww)
                    val year = dateKey.substring(0, 4).toInt()
                    val week = dateKey.substring(6).toInt()
                    calendar.clear()
                    calendar.set(Calendar.YEAR, year)
                    calendar.set(Calendar.WEEK_OF_YEAR, week)
                    calendar.time
                }
                GroupingPeriod.MONTHS -> {
                    // Parse month format (yyyy-MM)
                    val year = dateKey.substring(0, 4).toInt()
                    val month = dateKey.substring(5).toInt() - 1 // Calendar months are 0-based
                    calendar.clear()
                    calendar.set(Calendar.YEAR, year)
                    calendar.set(Calendar.MONTH, month)
                    calendar.time
                }
            }
            
            Pair(date, transactions)
        }.sortedBy { it.first }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
} 