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
import com.example.allinone.viewmodels.InvestmentsViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import java.text.SimpleDateFormat
import java.util.Locale

class InvestmentsFragment : Fragment() {
    private var _binding: FragmentInvestmentsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: InvestmentsViewModel by viewModels()
    private var selectedImageUri: Uri? = null
    
    private lateinit var investmentAdapter: InvestmentAdapter

    private val getContent = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                selectedImageUri = uri
                binding.selectedImageView.apply {
                    setImageURI(uri)
                    visibility = View.VISIBLE
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInvestmentsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupInvestmentTypeDropdown()
        setupButtons()
        observeData()
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

    private fun setupInvestmentTypeDropdown() {
        val types = arrayOf("Stock", "Crypto", "Real Estate", "Gold", "Other")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, types)
        (binding.typeLayout.editText as? AutoCompleteTextView)?.setAdapter(adapter)
    }

    private fun setupButtons() {
        binding.addInvestmentButton.setOnClickListener {
            binding.addInvestmentCard.visibility = View.VISIBLE
        }

        binding.addImageButton.setOnClickListener {
            openImagePicker()
        }

        binding.saveButton.setOnClickListener {
            saveInvestment()
        }

        binding.cancelButton.setOnClickListener {
            clearFields()
            binding.addInvestmentCard.visibility = View.GONE
        }
    }

    private fun observeData() {
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

    private fun saveInvestment() {
        val name = binding.nameInput.text.toString()
        val amountStr = binding.amountInput.text.toString()
        val currentValueStr = binding.currentValueInput.text.toString()
        val type = (binding.typeLayout.editText as? AutoCompleteTextView)?.text.toString()
        val description = binding.descriptionInput.text?.toString()?.takeIf { it.isNotBlank() }

        if (name.isBlank()) {
            binding.nameLayout.error = "Name is required"
            return
        }
        if (amountStr.isBlank()) {
            binding.amountLayout.error = "Amount is required"
            return
        }
        if (currentValueStr.isBlank()) {
            binding.currentValueLayout.error = "Current value is required"
            return
        }
        if (type.isNullOrBlank()) {
            binding.typeLayout.error = "Type is required"
            return
        }

        try {
            val amount = amountStr.toDouble()
            val currentValue = currentValueStr.toDouble()
            
            viewModel.addInvestment(
                name = name,
                amount = amount,
                type = type,
                currentValue = currentValue,
                description = description,
                imageUri = selectedImageUri?.toString()
            )

            clearFields()
            binding.addInvestmentCard.visibility = View.GONE
            showSnackbar("Investment added successfully")
        } catch (e: NumberFormatException) {
            binding.amountLayout.error = "Invalid amount"
        }
    }

    private fun clearFields() {
        binding.nameInput.text?.clear()
        binding.amountInput.text?.clear()
        binding.currentValueInput.text?.clear()
        (binding.typeLayout.editText as? AutoCompleteTextView)?.text?.clear()
        binding.descriptionInput.text?.clear()
        selectedImageUri = null
        binding.selectedImageView.visibility = View.GONE
        
        binding.nameLayout.error = null
        binding.amountLayout.error = null
        binding.currentValueLayout.error = null
        binding.typeLayout.error = null
    }

    private fun openImagePicker() {
        val intent = Intent().apply {
            type = "image/*"
            action = Intent.ACTION_GET_CONTENT
        }
        getContent.launch(intent)
    }

    private fun showInvestmentDetails(investment: Investment) {
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(investment.name)
            .setMessage("""
                Type: ${investment.type}
                Amount: ₺${investment.amount}
                Current Value: ₺${investment.currentValue}
                Profit/Loss: ₺${investment.profitLoss}
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
        // Implement edit functionality
        // This will be similar to adding a new investment but with pre-filled values
    }

    private fun showSnackbar(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
} 