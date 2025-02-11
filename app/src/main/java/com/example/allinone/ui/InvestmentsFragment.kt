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
import com.example.allinone.adapters.InvestmentAdapter
import com.example.allinone.data.Investment
import com.example.allinone.databinding.FragmentInvestmentsBinding
import com.example.allinone.databinding.DialogEditInvestmentBinding
import com.example.allinone.viewmodels.InvestmentsViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import java.text.SimpleDateFormat
import java.util.Locale

class InvestmentsFragment : Fragment() {
    private var _binding: FragmentInvestmentsBinding? = null
    private val binding get() = _binding!!
    private var _dialogBinding: DialogEditInvestmentBinding? = null
    private val dialogBinding get() = _dialogBinding!!
    
    private val viewModel: InvestmentsViewModel by viewModels()
    private var selectedImageUri: Uri? = null
    
    private val getContent = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            selectedImageUri = it
            dialogBinding.selectedImageView.setImageURI(it)
            dialogBinding.imageContainer.visibility = View.VISIBLE
        }
    }

    private lateinit var investmentAdapter: InvestmentAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentInvestmentsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupAddInvestmentButton()
        observeInvestments()
    }

    private fun setupRecyclerView() {
        investmentAdapter = InvestmentAdapter(
            onItemClick = { investment ->
                showInvestmentDetails(investment)
            },
            onItemLongClick = { investment ->
                showInvestmentOptions(investment)
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
            val name = nameInput.text.toString()
            val amount = amountInput.text.toString().toDoubleOrNull() ?: 0.0
            val type = (typeLayout.editText as? AutoCompleteTextView)?.text.toString()
            val description = descriptionInput.text.toString().takeIf { it.isNotBlank() }
            
            viewModel.addInvestment(
                name = name,
                amount = amount,
                type = type,
                description = description,
                imageUri = selectedImageUri?.toString()
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

    private fun observeInvestments() {
        viewModel.allInvestments.observe(viewLifecycleOwner) { investments ->
            investmentAdapter.submitList(investments)
            binding.emptyStateText.visibility = if (investments.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.totalInvestment.observe(viewLifecycleOwner) { total ->
            binding.totalInvestmentText.text = String.format("Total Investment: ₺%.2f", total)
        }

        viewModel.totalProfitLoss.observe(viewLifecycleOwner) { profitLoss ->
            binding.profitLossText.text = String.format("Total Profit/Loss: ₺%.2f", profitLoss)
            binding.profitLossText.setTextColor(
                if (profitLoss >= 0) {
                    requireContext().getColor(android.R.color.holo_green_dark)
                } else {
                    requireContext().getColor(android.R.color.holo_red_dark)
                }
            )
        }
    }

    private fun showInvestmentDetails(investment: Investment) {
        val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(investment.name)
            .setMessage("""
                Type: ${investment.type}
                Amount: ₺${investment.amount}
                Date: ${dateFormat.format(investment.date)}
                ${investment.description?.let { "Description: $it" } ?: ""}
            """.trimIndent())
            .setPositiveButton("Close", null)
            .show()
    }

    private fun showInvestmentOptions(investment: Investment) {
        val options = arrayOf("Edit", "Delete")
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Investment Options")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showEditInvestmentDialog(investment)
                    1 -> showDeleteConfirmation(investment)
                }
            }
            .show()
    }

    private fun showDeleteConfirmation(investment: Investment) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Investment")
            .setMessage("Are you sure you want to delete this investment?")
            .setPositiveButton("Delete") { _, _ ->
                viewModel.deleteInvestment(investment)
                showSnackbar("Investment deleted")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showEditInvestmentDialog(investment: Investment) {
        _dialogBinding = DialogEditInvestmentBinding.inflate(layoutInflater)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Edit Investment")
            .setView(dialogBinding.root)
            .create()

        // Pre-fill the fields with current values
        dialogBinding.apply {
            nameInput.setText(investment.name)
            amountInput.setText(investment.amount.toString())
            (typeLayout.editText as? AutoCompleteTextView)?.setText(investment.type)
            descriptionInput.setText(investment.description)
            
            // Handle image if it exists
            investment.imageUri?.let { uri ->
                selectedImageUri = Uri.parse(uri)
                selectedImageView.setImageURI(selectedImageUri)
                imageContainer.visibility = View.VISIBLE
            }

            saveButton.setOnClickListener {
                val name = nameInput.text.toString()
                val amount = amountInput.text.toString().toDoubleOrNull()
                val type = (typeLayout.editText as? AutoCompleteTextView)?.text.toString()
                val description = descriptionInput.text.toString().takeIf { it.isNotBlank() }

                if (name.isBlank() || amount == null || type.isBlank()) {
                    showSnackbar("Please fill all required fields")
                    return@setOnClickListener
                }

                val updatedInvestment = investment.copy(
                    name = name,
                    amount = amount,
                    type = type,
                    description = description,
                    imageUri = selectedImageUri?.toString()
                )

                viewModel.updateInvestment(updatedInvestment)
                dialog.dismiss()
                showSnackbar("Investment updated successfully")
            }

            cancelButton.setOnClickListener {
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun showSnackbar(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        _dialogBinding = null
    }
} 