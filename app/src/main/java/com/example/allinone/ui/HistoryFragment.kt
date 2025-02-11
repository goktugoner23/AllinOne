package com.example.allinone.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.allinone.adapters.TransactionAdapter
import com.example.allinone.data.Transaction
import com.example.allinone.databinding.FragmentHistoryBinding
import com.example.allinone.viewmodels.HomeViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

class HistoryFragment : Fragment() {
    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!
    private val viewModel: HomeViewModel by viewModels()
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

        // Update the observer with explicit type
        viewModel.allTransactions.observe(viewLifecycleOwner) { transactions: List<Transaction> ->
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
        // Implement edit functionality
        // This will be similar to adding a new transaction but with pre-filled values
    }

    private fun showTransactionDetails(transaction: Transaction) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Transaction Details")
            .setMessage("""
                Amount: ₺${transaction.amount}
                Type: ${transaction.type}
                Category: ${transaction.category}
                Date: ${transaction.date}
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