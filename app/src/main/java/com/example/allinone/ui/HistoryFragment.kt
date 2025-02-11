package com.example.allinone.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.allinone.R
import com.example.allinone.adapters.TransactionAdapter
import com.example.allinone.data.Transaction
import com.example.allinone.databinding.FragmentHistoryBinding
import com.example.allinone.databinding.FragmentHomeBinding
import com.example.allinone.viewmodels.HistoryViewModel
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import java.text.SimpleDateFormat
import java.util.Locale

class HistoryFragment : Fragment() {
    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!
    private val viewModel: HistoryViewModel by viewModels()
    private lateinit var transactionAdapter: TransactionAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupFilterButtons()
        setupSortButton()

        viewModel.filteredTransactions.observe(viewLifecycleOwner) { transactions ->
            transactionAdapter.submitList(transactions)
            binding.emptyStateText.visibility = 
                if (transactions.isEmpty()) View.VISIBLE else View.GONE
        }

        // Observe totals
        viewModel.totalIncome.observe(viewLifecycleOwner) { income ->
            binding.totalIncomeText.text = String.format("Total Income: ₺%.2f", income)
        }

        viewModel.totalExpense.observe(viewLifecycleOwner) { expense ->
            binding.totalExpenseText.text = String.format("Total Expense: ₺%.2f", expense)
        }

        viewModel.balance.observe(viewLifecycleOwner) { balance ->
            binding.balanceText.text = String.format("Balance: ₺%.2f", balance)
            binding.balanceText.setTextColor(
                if (balance >= 0) {
                    requireContext().getColor(android.R.color.holo_green_dark)
                } else {
                    requireContext().getColor(android.R.color.holo_red_dark)
                }
            )
        }
    }

    private fun setupRecyclerView() {
        transactionAdapter = TransactionAdapter(
            onItemClick = { transaction ->
                showTransactionDetails(transaction)
            },
            onItemLongClick = { transaction ->
                showTransactionOptions(transaction)
            }
        )
        binding.transactionsRecyclerView.apply {
            adapter = transactionAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
    }

    private fun setupFilterButtons() {
        binding.apply {
            allButton.setOnClickListener { 
                viewModel.setFilter(HistoryViewModel.TransactionFilter.ALL)
                updateFilterButtonStates(allButton)
            }
            incomeButton.setOnClickListener { 
                viewModel.setFilter(HistoryViewModel.TransactionFilter.INCOME)
                updateFilterButtonStates(incomeButton)
            }
            expenseButton.setOnClickListener { 
                viewModel.setFilter(HistoryViewModel.TransactionFilter.EXPENSE)
                updateFilterButtonStates(expenseButton)
            }
        }
    }

    private fun updateFilterButtonStates(selectedButton: View) {
        binding.apply {
            allButton.isSelected = allButton == selectedButton
            incomeButton.isSelected = incomeButton == selectedButton
            expenseButton.isSelected = expenseButton == selectedButton
        }
    }

    private fun setupSortButton() {
        binding.sortButton.setOnClickListener {
            showSortOptions()
        }
    }

    private fun showSortOptions() {
        val options = arrayOf(
            "Date (Newest First)", 
            "Date (Oldest First)", 
            "Amount (Highest First)", 
            "Amount (Lowest First)"
        )
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Sort By")
            .setItems(options) { _, which ->
                val order = when (which) {
                    0 -> HistoryViewModel.SortOrder.DATE_DESC
                    1 -> HistoryViewModel.SortOrder.DATE_ASC
                    2 -> HistoryViewModel.SortOrder.AMOUNT_DESC
                    3 -> HistoryViewModel.SortOrder.AMOUNT_ASC
                    else -> HistoryViewModel.SortOrder.DATE_DESC
                }
                viewModel.setSortOrder(order)
            }
            .show()
    }

    private fun showTransactionOptions(transaction: Transaction) {
        val options = arrayOf("Edit", "Delete")
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Transaction Options")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showEditTransactionDialog(transaction)
                    1 -> showDeleteConfirmation(transaction)
                }
            }
            .show()
    }

    private fun showDeleteConfirmation(transaction: Transaction) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Transaction")
            .setMessage("Are you sure you want to delete this transaction?")
            .setPositiveButton("Delete") { _, _ ->
                viewModel.deleteTransaction(transaction)
                showSnackbar("Transaction deleted")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showEditTransactionDialog(transaction: Transaction) {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_edit_transaction, null)
        
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Edit Transaction")
            .setView(dialogView)
            .create()

        // Setup views
        val amountInput = dialogView.findViewById<TextInputEditText>(R.id.amountInput)
        val typeInput = dialogView.findViewById<AutoCompleteTextView>(R.id.typeInput)
        val descriptionInput = dialogView.findViewById<TextInputEditText>(R.id.descriptionInput)
        val saveButton = dialogView.findViewById<MaterialButton>(R.id.saveButton)

        // Pre-fill the fields
        amountInput.setText(transaction.amount.toString())
        typeInput.setText(transaction.type)
        descriptionInput.setText(transaction.description)

        // Setup type dropdown
        val types = arrayOf("Food", "Transport", "Bills", "Shopping", "Investment", "Other")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, types)
        typeInput.setAdapter(adapter)

        saveButton.setOnClickListener {
            val amount = amountInput.text.toString().toDoubleOrNull()
            val type = typeInput.text.toString()
            val description = descriptionInput.text.toString().takeIf { it.isNotBlank() }

            if (amount == null || type.isBlank()) {
                showSnackbar("Please fill all required fields")
                return@setOnClickListener
            }

            val updatedTransaction = transaction.copy(
                amount = amount,
                type = type,
                description = description
            )

            viewModel.updateTransaction(updatedTransaction)
            dialog.dismiss()
            showSnackbar("Transaction updated successfully")
        }

        dialog.show()
    }

    private fun showTransactionDetails(transaction: Transaction) {
        val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Transaction Details")
            .setMessage("""
                Amount: ₺${transaction.amount}
                Type: ${transaction.type}
                Category: ${transaction.category}
                Date: ${dateFormat.format(transaction.date)}
                ${transaction.description?.let { "Description: $it" } ?: ""}
            """.trimIndent())
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showSnackbar(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
} 