package com.example.allinone.ui.wt

import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
    private var currentDialogBinding: DialogEditWtStudentBinding? = null
    
    private val getContent = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            try {
                // For non-picker URIs, try to take persistable permission
                if (!uri.toString().contains("picker_get_content")) {
                    try {
                        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                        requireContext().contentResolver.takePersistableUriPermission(uri, flags)
                    } catch (e: SecurityException) {
                        // Log but continue - we'll still have temporary access
                        e.printStackTrace()
                    }
                }
                
                // Save the URI to your student object
                selectedAttachmentUri = uri
                
                // Update UI to show the selected file
                currentDialogBinding?.let { binding ->
                    binding.attachmentNameText.text = getFileNameFromUri(uri)
                    binding.attachmentNameText.visibility = View.VISIBLE
                    
                    // Check if it's an image
                    val mimeType = context?.contentResolver?.getType(uri)
                    if (mimeType?.startsWith("image/") == true) {
                        try {
                            binding.attachmentPreview.setImageURI(uri)
                            binding.attachmentPreview.visibility = View.VISIBLE
                        } catch (e: SecurityException) {
                            // If we can't load the image, just show the name
                            binding.attachmentPreview.visibility = View.GONE
                            Toast.makeText(
                                requireContext(),
                                "Cannot preview this image: ${e.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } else {
                        binding.attachmentPreview.visibility = View.GONE
                    }
                    
                    // Add long press listener to remove attachment
                    binding.attachmentNameText.setOnLongClickListener {
                        selectedAttachmentUri = null
                        binding.attachmentNameText.text = "No attachment"
                        binding.attachmentPreview.visibility = View.GONE
                        true
                    }
                    
                    // Add tooltip to inform user about removal option
                    binding.attachmentNameText.setOnClickListener {
                        Toast.makeText(context, "Long press to remove attachment", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                // Handle the permission error
                Toast.makeText(
                    requireContext(),
                    "Failed to handle attachment: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
                e.printStackTrace()
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentWtRegisterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupFab()
        observeStudents()
        observeNetworkStatus()
    }

    override fun onResume() {
        super.onResume()
        // Force refresh data on resume
        viewModel.refreshData()
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
            // Sort students by start date, with most recent first
            val sortedStudents = students.sortedByDescending { it.startDate }
            adapter.submitList(sortedStudents)
        }
    }

    private fun showAddDialog() {
        val dialogBinding = DialogEditWtStudentBinding.inflate(layoutInflater)
        currentDialogBinding = dialogBinding
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
        currentDialogBinding = dialogBinding
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
                try {
                    updateAttachmentPreview(dialogBinding, Uri.parse(student.attachmentUri))
                } catch (e: Exception) {
                    // If we can't load the attachment, just show an error
                    Toast.makeText(
                        requireContext(),
                        "Failed to load attachment: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                    attachmentNameText.text = "Attachment unavailable"
                    attachmentPreview.visibility = View.GONE
                }
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
        val fileName = getFileNameFromUri(uri)
        dialogBinding.attachmentNameText.text = "Attachment: $fileName"
        dialogBinding.attachmentNameText.visibility = View.VISIBLE
        
        // Check if it's an image
        val mimeType = context?.contentResolver?.getType(uri)
        if (mimeType?.startsWith("image/") == true) {
            try {
                // For images, show the preview
                dialogBinding.attachmentPreview.setImageURI(uri)
                dialogBinding.attachmentPreview.visibility = View.VISIBLE
            } catch (e: SecurityException) {
                // If we can't load the image, just show the name
                dialogBinding.attachmentPreview.visibility = View.GONE
                Toast.makeText(
                    requireContext(),
                    "Cannot preview this image: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        } else {
            // For non-image files, just show the name
            dialogBinding.attachmentPreview.visibility = View.GONE
        }
        
        // Add tooltip to inform user about removal option
        dialogBinding.attachmentNameText.setOnClickListener {
            Toast.makeText(context, "Long press to remove attachment", Toast.LENGTH_SHORT).show()
        }
        
        // Add long press listener to remove attachment
        dialogBinding.attachmentNameText.setOnLongClickListener {
            selectedAttachmentUri = null
            dialogBinding.attachmentNameText.text = "No attachment"
            dialogBinding.attachmentPreview.visibility = View.GONE
            true
        }
    }

    private fun showPaymentConfirmation(student: WTStudent) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Confirm Payment")
            .setMessage("Mark ${student.name} as paid?")
            .setPositiveButton("Yes") { _, _ ->
                val updatedStudent = student.copy(
                    isPaid = true,
                    paymentDate = Date()
                )
                viewModel.updateStudent(updatedStudent)
                showSnackbar("Payment status updated")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showUnpaidConfirmation(student: WTStudent) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Change Payment Status")
            .setMessage("Mark ${student.name} as unpaid?")
            .setPositiveButton("Yes") { _, _ ->
                val updatedStudent = student.copy(
                    isPaid = false,
                    paymentDate = null
                )
                viewModel.updateStudent(updatedStudent)
                showSnackbar("Payment status updated")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showSnackbar(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    }

    private fun getFileNameFromUri(uri: Uri): String {
        try {
            val cursor = requireContext().contentResolver.query(uri, null, null, null, null)
            return cursor?.use {
                val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                it.moveToFirst()
                if (nameIndex >= 0) it.getString(nameIndex) else "Unknown file"
            } ?: uri.lastPathSegment ?: "Unknown file"
        } catch (e: Exception) {
            e.printStackTrace()
            return uri.lastPathSegment ?: "Unknown file"
        }
    }

    private fun shareStudentInfo(student: WTStudent) {
        val message = """
            Student: ${student.name}
            Period: ${dateFormat.format(student.startDate)} - ${dateFormat.format(student.endDate)}
            Amount: $${student.amount}
            Payment Status: ${if (student.isPaid) "Paid" else "Unpaid"}
        """.trimIndent()
        
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Student Information: ${student.name}")
            putExtra(Intent.EXTRA_TEXT, message)
        }
        
        startActivity(Intent.createChooser(intent, "Share via"))
    }

    private fun observeNetworkStatus() {
        // Observe Firebase repository connection status
        viewModel.isNetworkAvailable.observe(viewLifecycleOwner) { isAvailable ->
            // Update with a delay to prevent false network status changes
            Handler(Looper.getMainLooper()).postDelayed({
                if (view != null && isAdded) {
                    // Update the network status indicator in the parent fragment if possible
                    val parentFragment = parentFragment
                    if (parentFragment is WTRegistryFragment) {
                        // Network status already handled by parent
                        return@postDelayed
                    }
                    
                    if (!isAvailable) {
                        // If network becomes unavailable, show message
                        Toast.makeText(
                            context, 
                            "Network unavailable. Using cached data.",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        // When network becomes available, refresh data
                        viewModel.refreshData()
                    }
                }
            }, 1000) // 1-second delay
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        currentDialogBinding = null
    }
} 