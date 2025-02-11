package com.example.allinone.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.example.allinone.databinding.FragmentHomeBinding
import com.example.allinone.viewmodels.HomeViewModel
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: HomeViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupTypeDropdowns()
        setupButtons()
        
        viewModel.balance.observe(viewLifecycleOwner) { balance ->
            binding.balanceText.text = String.format("₺%.2f", balance)
            binding.balanceText.setTextColor(
                if (balance >= 0) {
                    requireContext().getColor(android.R.color.holo_green_dark)
                } else {
                    requireContext().getColor(android.R.color.holo_red_dark)
                }
            )
        }
    }

    private fun setupTypeDropdowns() {
        val categories = arrayOf("Salary", "Wing Chun", "General", "Food", "Investment")
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            categories
        )
        (binding.typeLayout.editText as? AutoCompleteTextView)?.setAdapter(adapter)

        // Add investment selection when "Investment" type is selected
        (binding.typeLayout.editText as? AutoCompleteTextView)?.setOnItemClickListener { _, _, _, _ ->
            val selectedType = (binding.typeLayout.editText as? AutoCompleteTextView)?.text.toString()
            if (selectedType == "Investment") {
                showInvestmentSelector()
            }
        }
    }

    private fun showInvestmentSelector() {
        viewModel.getAllInvestments().observe(viewLifecycleOwner) { investments ->
            val investmentNames = investments.map { it.name }.toTypedArray()
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Select Investment")
                .setItems(investmentNames) { _, which ->
                    val selectedInvestment = investments[which]
                    binding.descriptionInput.setText("Return from ${selectedInvestment.name}")
                }
                .show()
        }
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

        if (amountStr.isBlank()) {
            binding.amountLayout.error = "Amount is required"
            return
        }
        if (type.isNullOrBlank()) {
            binding.typeLayout.error = "Please select a category"
            return
        }

        try {
            val amount = amountStr.toDouble()
            if (isIncome) {
                viewModel.addIncome(amount, type, description)
            } else {
                viewModel.addExpense(amount, type, description)
            }
            clearFields()
            showSnackbar(if (isIncome) "Income added successfully" else "Expense added successfully")
        } catch (e: NumberFormatException) {
            binding.amountLayout.error = "Invalid amount"
        }
    }

    private fun showSnackbar(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    }

    private fun clearFields() {
        binding.amountInput.text?.clear()
        (binding.typeLayout.editText as? AutoCompleteTextView)?.text?.clear()
        binding.descriptionInput.text?.clear()
        binding.amountLayout.error = null
        binding.typeLayout.error = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
} 