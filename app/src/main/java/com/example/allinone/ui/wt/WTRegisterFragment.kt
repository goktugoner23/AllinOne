package com.example.allinone.ui.wt

import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
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
    private var selectedAttachmentUri: Uri? = null
    
    private val getContent = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            try {
                // Take persistable permission for the URI
                requireContext().contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                
                // Save the URI to your student object
                selectedAttachmentUri = uri.toString()
                
                // Update UI to show the selected file
                binding.attachmentName.text = getFileNameFromUri(uri)
                binding.attachmentName.visibility = View.VISIBLE
                binding.removeAttachmentButton.visibility = View.VISIBLE
            } catch (e: Exception) {
                // Handle the permission error
                Toast.makeText(
                    requireContext(),
                    "Failed to save attachment permission: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
                e.printStackTrace()
            }
        }
    }
    
    private var dialogBinding: DialogEditWtStudentBinding? = null

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
                } else {
                    showUnpaidConfirmation(student)
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
        this.dialogBinding = dialogBinding
        setupDatePickers(dialogBinding)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Add New Student")
            .setView(dialogBinding.root)
            .create()
            
        dialogBinding.saveButton.setOnClickListener {
            val name = dialogBinding.nameInput.text.toString()
            val startDate = dialogBinding.startDateInput.tag as? Date
            val endDate = dialogBinding.endDateInput.tag as? Date
            val amount = dialogBinding.amountInput.text.toString().toDoubleOrNull() ?: 0.0

            when {
                name.isBlank() -> showSnackbar("Please enter student name")
                startDate == null -> showSnackbar("Please select start date")
                endDate == null -> showSnackbar("Please select end date")
                else -> {
                    viewModel.addStudent(name, startDate, endDate, amount)
                    dialog.dismiss()
                    showSnackbar("Student added successfully")
                }
            }
        }
        
        dialogBinding.cancelButton.setOnClickListener {
            dialog.dismiss()
        }
        
        dialog.show()
    }

    private fun showEditDialog(student: WTStudent) {
        val dialogBinding = DialogEditWtStudentBinding.inflate(layoutInflater)
        this.dialogBinding = dialogBinding
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .create()

        setupDatePickers(dialogBinding)
        
        // Reset attachment
        selectedAttachmentUri = student.attachmentUri?.let { Uri.parse(it) }

        // Pre-fill the form
        dialogBinding.apply {
            nameInput.setText(student.name)
            startDateInput.setText(dateFormat.format(student.startDate))
            startDateInput.tag = student.startDate
            endDateInput.setText(dateFormat.format(student.endDate))
            endDateInput.tag = student.endDate
            amountInput.setText(student.amount.toString())
            paidSwitch.isChecked = student.isPaid
            
            // Setup attachment
            if (student.attachmentUri != null) {
                updateAttachmentPreview(dialogBinding, Uri.parse(student.attachmentUri))
            }
            
            addAttachmentButton.setOnClickListener {
                if (student.isPaid || paidSwitch.isChecked) {
                    getContent.launch("*/*")
                } else {
                    showSnackbar("Attachments can only be added for paid registrations")
                }
            }
            
            // Setup share button
            shareButton.setOnClickListener {
                shareStudentInfo(student)
            }
            
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
                            paymentDate = if (isPaid && !student.isPaid) Date() else student.paymentDate,
                            attachmentUri = selectedAttachmentUri?.toString() ?: student.attachmentUri
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

    private fun updateAttachmentPreview(dialogBinding: DialogEditWtStudentBinding, uri: Uri) {
        dialogBinding.apply {
            attachmentNameText.text = "Attachment added"
            
            // Check if it's an image
            val mimeType = context?.contentResolver?.getType(uri)
            if (mimeType?.startsWith("image/") == true) {
                attachmentPreview.setImageURI(uri)
                attachmentPreview.visibility = View.VISIBLE
            } else {
                // For non-image files (like PDF), just show the name
                attachmentPreview.visibility = View.GONE
                val fileName = uri.lastPathSegment ?: "File"
                attachmentNameText.text = "Attachment: $fileName"
            }
        }
    }

    private fun showPaymentConfirmation(student: WTStudent) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Confirm Payment")
            .setMessage("Mark ${student.name}'s registration as paid?")
            .setPositiveButton("Yes") { _, _ ->
                val updatedStudent = student.copy(
                    isPaid = true,
                    paymentDate = Date()
                )
                viewModel.updateStudent(updatedStudent)
                showSnackbar("Payment marked as received")
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun showUnpaidConfirmation(student: WTStudent) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Mark as Unpaid")
            .setMessage("Mark ${student.name}'s registration as unpaid?")
            .setPositiveButton("Yes") { _, _ ->
                val updatedStudent = student.copy(
                    isPaid = false,
                    paymentDate = null
                )
                viewModel.updateStudent(updatedStudent)
                showSnackbar("Payment marked as not received")
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun showSnackbar(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    }

    private fun shareStudentInfo(student: WTStudent) {
        val shareText = """
            Student: ${student.name}
            Start Date: ${dateFormat.format(student.startDate)}
            End Date: ${dateFormat.format(student.endDate)}
            Amount: ${student.amount}
            Paid: ${if (student.isPaid) "Yes" else "No"}
        """.trimIndent()
        
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, shareText)
            type = "text/plain"
        }
        
        val shareIntent = Intent.createChooser(sendIntent, "Share Student Info")
        startActivity(shareIntent)
    }

    private fun getFileNameFromUri(uri: Uri): String {
        val cursor = requireContext().contentResolver.query(uri, null, null, null, null)
        return cursor?.use {
            val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            it.moveToFirst()
            it.getString(nameIndex)
        } ?: "Attachment"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        dialogBinding = null
    }
} 