package com.example.allinone.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.allinone.adapters.TransactionAdapter
import com.example.allinone.data.Transaction
import com.example.allinone.databinding.FragmentInvestmentsBinding
import com.example.allinone.databinding.DialogEditInvestmentBinding
import com.example.allinone.viewmodels.HomeViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import java.text.SimpleDateFormat
import java.util.Locale

class InvestmentsFragment : Fragment() {
    private var _binding: FragmentInvestmentsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: HomeViewModel by viewModels()
    private lateinit var investmentAdapter: TransactionAdapter
    private var _dialogBinding: DialogEditInvestmentBinding? = null
    private val dialogBinding get() = _dialogBinding!!
    private var selectedImageUri: Uri? = null
    
    private val getContent = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            selectedImageUri = it
            dialogBinding.imageContainer.visibility = View.VISIBLE
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentInvestmentsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupAddInvestmentButton()
        observeTransactions()
    }

    private fun setupRecyclerView() {
        investmentAdapter = TransactionAdapter(
            onItemClick = { transaction ->
                showTransactionDetails(transaction)
            },
            onItemLongClick = { transaction ->
                showTransactionOptions(transaction)
            }
        )
        binding.investmentsRecyclerView.apply {
            adapter = investmentAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
    }

    private fun setupAddInvestmentButton() {
        binding.addInvestmentButton.setOnClickListener {
            showAddInvestmentDialog()
        }
    }

    private fun showAddInvestmentDialog() {
        _dialogBinding = DialogEditInvestmentBinding.inflate(layoutInflater)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Add Investment")
            .setView(dialogBinding.root)
            .create()

        setupInvestmentTypeDropdown()
        
        dialogBinding.addImageButton.setOnClickListener {
            openImagePicker()
        }

        dialogBinding.deleteImageButton.setOnClickListener {
            selectedImageUri = null
            dialogBinding.imageContainer.visibility = View.GONE
        }

        dialogBinding.saveButton.setOnClickListener {
            if (validateInputs()) {
                handleAddInvestment()
                dialog.dismiss()
            }
        }

        dialogBinding.cancelButton.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun setupInvestmentTypeDropdown() {
        val types = arrayOf("Stock", "Crypto", "Real Estate", "Gold", "Other")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, types)
        (dialogBinding.typeLayout.editText as? AutoCompleteTextView)?.setAdapter(adapter)
    }

    private fun validateInputs(): Boolean {
        var isValid = true
        
        dialogBinding.apply {
            if (nameInput.text.isNullOrBlank()) {
                nameLayout.error = "Name is required"
                isValid = false
            }
            
            if (amountInput.text.isNullOrBlank()) {
                amountLayout.error = "Amount is required"
                isValid = false
            }
            
            if ((typeLayout.editText as? AutoCompleteTextView)?.text.isNullOrBlank()) {
                typeLayout.error = "Type is required"
                isValid = false
            }
        }
        
        return isValid
    }

    private fun handleAddInvestment() {
        dialogBinding.apply {
            val amount = amountInput.text.toString().toDoubleOrNull() ?: 0.0
            val type = (typeLayout.editText as? AutoCompleteTextView)?.text.toString()
            val description = descriptionInput.text.toString().takeIf { it.isNotBlank() }
            
            viewModel.addTransaction(
                amount = amount,
                type = "Investment",
                description = description,
                isIncome = false,
                category = type ?: "Investment"
            )
        }
        clearFields()
    }

    private fun clearFields() {
        dialogBinding.apply {
            nameInput.text?.clear()
            amountInput.text?.clear()
            (typeLayout.editText as? AutoCompleteTextView)?.text?.clear()
            descriptionInput.text?.clear()
            selectedImageUri = null
            imageContainer.visibility = View.GONE
            
            nameLayout.error = null
            amountLayout.error = null
            typeLayout.error = null
        }
    }

    private fun openImagePicker() {
        getContent.launch("image/*")
    }

    private fun observeTransactions() {
        viewModel.allTransactions.observe(viewLifecycleOwner) { transactions ->
            val investmentTransactions = transactions.filter { it.type == "Investment" }
            investmentAdapter.submitList(investmentTransactions)
            binding.emptyStateText.visibility = 
                if (investmentTransactions.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun showTransactionDetails(transaction: Transaction) {
        val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Investment Details")
            .setMessage("""
                Amount: ₺${transaction.amount}
                Category: ${transaction.category}
                Date: ${dateFormat.format(transaction.date)}
                ${transaction.description?.let { "Description: $it" } ?: ""}
            """.trimIndent())
            .setPositiveButton("Close", null)
            .show()
    }

    private fun showTransactionOptions(transaction: Transaction) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Transaction Options")
            .setItems(arrayOf("Edit", "Delete")) { _, which ->
                when (which) {
                    0 -> showEditDialog(transaction)
                    1 -> deleteTransaction(transaction)
                }
            }
            .show()
    }

    private fun deleteTransaction(transaction: Transaction) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Transaction")
            .setMessage("Are you sure you want to delete this transaction?")
            .setPositiveButton("Delete") { _, _ ->
                viewModel.run { deleteTransaction(transaction) }
                showSnackbar("Transaction deleted")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showSnackbar(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    }

    private fun showEditDialog(transaction: Transaction) {
        _dialogBinding = DialogEditInvestmentBinding.inflate(layoutInflater)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Edit Investment")
            .setView(dialogBinding.root)
            .create()

        // Pre-fill the fields
        dialogBinding.apply {
            amountInput.setText(transaction.amount.toString())
            (typeLayout.editText as? AutoCompleteTextView)?.setText(transaction.category)
            descriptionInput.setText(transaction.description)
        }

        setupInvestmentTypeDropdown()

        dialogBinding.saveButton.setOnClickListener {
            if (validateInputs()) {
                handleEditInvestment(transaction)
                dialog.dismiss()
            }
        }

        dialogBinding.cancelButton.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun handleEditInvestment(transaction: Transaction) {
        dialogBinding.apply {
            val amount = amountInput.text.toString().toDoubleOrNull() ?: 0.0
            val type = (typeLayout.editText as? AutoCompleteTextView)?.text.toString()
            val description = descriptionInput.text.toString()

            val updatedTransaction = transaction.copy(
                amount = amount,
                type = "Investment",
                description = description,
                category = type ?: "Investment"
            )
            viewModel.deleteTransaction(transaction)
            viewModel.addTransaction(
                amount = updatedTransaction.amount,
                type = updatedTransaction.type,
                description = updatedTransaction.description,
                isIncome = false,
                category = updatedTransaction.category
            )
            showSnackbar("Investment updated")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        _dialogBinding = null
    }
} 