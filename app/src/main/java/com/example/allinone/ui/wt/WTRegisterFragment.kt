package com.example.allinone.ui.wt

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.allinone.adapters.WTStudentAdapter
import com.example.allinone.data.WTStudent
import com.example.allinone.databinding.DialogEditWtStudentBinding
import com.example.allinone.databinding.FragmentWtRegisterBinding
import com.example.allinone.viewmodels.WTRegisterViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class WTRegisterFragment : Fragment() {
    private var _binding: FragmentWtRegisterBinding? = null
    private val binding get() = _binding!!
    private val viewModel: WTRegisterViewModel by viewModels()
    private lateinit var adapter: WTStudentAdapter
    private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentWtRegisterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupFab()
        observeStudents()
    }

    private fun setupRecyclerView() {
        adapter = WTStudentAdapter(
            onItemClick = { student -> showEditDialog(student) },
            onPaymentStatusClick = { student -> 
                if (!student.isPaid) {
                    showPaymentConfirmation(student)
                }
            }
        )
        
        binding.studentsRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@WTRegisterFragment.adapter
        }
    }

    private fun setupFab() {
        binding.addStudentFab.setOnClickListener {
            showAddDialog()
        }
    }

    private fun observeStudents() {
        viewModel.allStudents.observe(viewLifecycleOwner) { students ->
            adapter.submitList(students)
        }
    }

    private fun showAddDialog() {
        val dialogBinding = DialogEditWtStudentBinding.inflate(layoutInflater)
        setupDatePickers(dialogBinding)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Add New Student")
            .setView(dialogBinding.root)
            .setPositiveButton("Save") { _, _ ->
                val name = dialogBinding.nameInput.text.toString()
                val startDate = dialogBinding.startDateInput.tag as? Date
                val endDate = dialogBinding.endDateInput.tag as? Date
                val amount = dialogBinding.amountInput.text.toString().toDoubleOrNull()

                if (name.isNotBlank() && startDate != null && endDate != null && amount != null) {
                    viewModel.addStudent(name, startDate, endDate, amount)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showEditDialog(student: WTStudent) {
        val dialogBinding = DialogEditWtStudentBinding.inflate(layoutInflater)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .create()

        setupDatePickers(dialogBinding)

        // Pre-fill the form
        dialogBinding.apply {
            nameInput.setText(student.name)
            startDateInput.setText(dateFormat.format(student.startDate))
            startDateInput.tag = student.startDate
            endDateInput.setText(dateFormat.format(student.endDate))
            endDateInput.tag = student.endDate
            amountInput.setText(student.amount.toString())
            paidSwitch.isChecked = student.isPaid
            
            saveButton.setOnClickListener {
                val name = nameInput.text.toString()
                val startDate = startDateInput.tag as? Date
                val endDate = endDateInput.tag as? Date
                val amount = amountInput.text.toString().toDoubleOrNull()
                val isPaid = paidSwitch.isChecked

                when {
                    name.isBlank() -> showSnackbar("Please enter student name")
                    startDate == null -> showSnackbar("Please select start date")
                    endDate == null -> showSnackbar("Please select end date")
                    amount == null -> showSnackbar("Please enter valid amount")
                    else -> {
                        val updatedStudent = student.copy(
                            name = name,
                            startDate = startDate,
                            endDate = endDate,
                            amount = amount,
                            isPaid = isPaid,
                            paymentDate = if (isPaid && !student.isPaid) Date() else student.paymentDate
                        )
                        viewModel.updateStudent(updatedStudent)
                        dialog.dismiss()
                        showSnackbar("Student updated successfully")
                    }
                }
            }

            cancelButton.setOnClickListener {
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun setupDatePickers(dialogBinding: DialogEditWtStudentBinding) {
        val calendar = Calendar.getInstance()

        dialogBinding.startDateInput.setOnClickListener {
            DatePickerDialog(
                requireContext(),
                { _, year, month, day ->
                    calendar.set(year, month, day)
                    val date = calendar.time
                    dialogBinding.startDateInput.setText(dateFormat.format(date))
                    dialogBinding.startDateInput.tag = date
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        dialogBinding.endDateInput.setOnClickListener {
            DatePickerDialog(
                requireContext(),
                { _, year, month, day ->
                    calendar.set(year, month, day)
                    val date = calendar.time
                    dialogBinding.endDateInput.setText(dateFormat.format(date))
                    dialogBinding.endDateInput.tag = date
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }
    }

    private fun showPaymentConfirmation(student: WTStudent) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Confirm Payment")
            .setMessage("Mark payment as received for ${student.name}?")
            .setPositiveButton("Yes") { _, _ ->
                viewModel.markAsPaid(student)
                showSnackbar("Payment marked as received")
            }
            .setNegativeButton("No", null)
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