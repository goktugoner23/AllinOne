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
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.allinone.R
import com.example.allinone.adapters.WTRegistrationAdapter
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
    private lateinit var adapter: WTRegistrationAdapter
    private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    private var selectedAttachmentUri: Uri? = null
    private var currentDialogBinding: DialogEditWtStudentBinding? = null
    private var activeStudents: List<WTStudent> = emptyList()
    private var selectedStudent: WTStudent? = null
    
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
        observeActiveStudents()
        observeNetworkStatus()
    }

    override fun onResume() {
        super.onResume()
        // Force refresh data on resume
        viewModel.refreshData()
    }

    private fun setupRecyclerView() {
        adapter = WTRegistrationAdapter(
            onItemClick = { student -> showEditDialog(student) },
            onPaymentStatusClick = { student -> 
                if (!student.isPaid) {
                    showPaymentConfirmation(student)
                } else {
                    showUnpaidConfirmation(student)
                }
            },
            onShareClick = { student -> shareStudentInfo(student) }
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
        // Use registeredStudents which should contain only students with course data
        viewModel.registeredStudents.observe(viewLifecycleOwner) { students ->
            // Sort students by start date, with most recent first
            val sortedStudents = students.sortedByDescending { it.startDate }
            adapter.submitList(sortedStudents)
            
            // Show empty state if there are no registered students
            if (sortedStudents.isEmpty()) {
                binding.emptyState.visibility = View.VISIBLE
                binding.studentsRecyclerView.visibility = View.GONE
            } else {
                binding.emptyState.visibility = View.GONE
                binding.studentsRecyclerView.visibility = View.VISIBLE
            }
        }
    }

    private fun observeActiveStudents() {
        viewModel.activeStudents.observe(viewLifecycleOwner) { students ->
            activeStudents = students
        }
    }

    private fun showAddDialog() {
        val dialogBinding = DialogEditWtStudentBinding.inflate(layoutInflater)
        currentDialogBinding = dialogBinding
        setupDatePickers(dialogBinding)
        setupStudentDropdown(dialogBinding)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.add_registration)
            .setView(dialogBinding.root)
            .create()
        
        // Configure dialog window for better keyboard handling
        dialog.window?.apply {
            // Set soft input mode to adjust nothing and let scrollview handle scrolling
            setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
            
            // Set window size to match screen width and wrap content for height
            setLayout(
                android.view.WindowManager.LayoutParams.MATCH_PARENT,
                android.view.WindowManager.LayoutParams.WRAP_CONTENT
            )
        }
            
        dialogBinding.saveButton.setOnClickListener {
            // Validate form
            if (validateRegistrationForm(dialogBinding)) {
                val student = selectedStudent!!
                val startDate = dialogBinding.startDateInput.tag as Date
                val endDate = dialogBinding.endDateInput.tag as Date
                val amount = dialogBinding.amountInput.text.toString().toDoubleOrNull() ?: 0.0
                val isPaid = dialogBinding.paidSwitch.isChecked
                
                // Only set course registration data, don't modify personal info
                viewModel.registerStudentForCourse(student, startDate, endDate, amount, isPaid)
                dialog.dismiss()
                showSnackbar(getString(R.string.registration_success))
            }
            // If validation fails, dialog stays open with errors shown
        }
        
        dialogBinding.cancelButton.setOnClickListener {
            dialog.dismiss()
        }
        
        dialog.show()
    }

    private fun validateRegistrationForm(dialogBinding: DialogEditWtStudentBinding): Boolean {
        var isValid = true
        
        // Get required values
        val student = selectedStudent
        val startDate = dialogBinding.startDateInput.tag as? Date
        val endDate = dialogBinding.endDateInput.tag as? Date
        val amountText = dialogBinding.amountInput.text.toString().trim()
        
        // Validate student selection
        if (student == null) {
            // Show error on student dropdown
            dialogBinding.studentDropdown.error = getString(R.string.please_select_student)
            isValid = false
        } else {
            dialogBinding.studentDropdown.error = null
        }
        
        // Validate start date
        if (startDate == null) {
            dialogBinding.startDateInput.error = getString(R.string.please_select_start_date)
            isValid = false
        } else {
            dialogBinding.startDateInput.error = null
            
            // Check for duplicate registration
            if (student != null) {
                val existingRegistration = viewModel.registeredStudents.value?.find { 
                    it.id == student.id && 
                    it.startDate?.time == startDate.time &&
                    it != selectedStudent  // Skip current student if editing
                }
                
                if (existingRegistration != null) {
                    dialogBinding.startDateInput.error = "This student already has a registration with this date"
                    isValid = false
                }
            }
        }
        
        // Validate end date
        if (endDate == null) {
            dialogBinding.endDateInput.error = getString(R.string.please_select_end_date)
            isValid = false
        } else {
            dialogBinding.endDateInput.error = null
        }
        
        // Validate amount
        if (amountText.isEmpty() || amountText.toDoubleOrNull() == null) {
            dialogBinding.amountInput.error = getString(R.string.please_enter_valid_amount)
            isValid = false
        } else {
            dialogBinding.amountInput.error = null
        }
        
        return isValid
    }

    private fun setupStudentDropdown(dialogBinding: DialogEditWtStudentBinding) {
        // Create adapter for the dropdown
        val studentNames = activeStudents.map { it.name }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, studentNames)
        dialogBinding.studentDropdown.setAdapter(adapter)
        
        // Set listener for selection
        dialogBinding.studentDropdown.setOnItemClickListener { _, _, position, _ ->
            selectedStudent = activeStudents[position]
        }
    }

    private fun showEditDialog(student: WTStudent) {
        val dialogBinding = DialogEditWtStudentBinding.inflate(layoutInflater)
        currentDialogBinding = dialogBinding
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.edit_registration)
            .setView(dialogBinding.root)
            .create()

        // Configure dialog window for better keyboard handling
        dialog.window?.apply {
            // Set soft input mode to adjust nothing and let scrollview handle scrolling
            setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
            
            // Set window size to match screen width and wrap content for height
            setLayout(
                android.view.WindowManager.LayoutParams.MATCH_PARENT,
                android.view.WindowManager.LayoutParams.WRAP_CONTENT
            )
        }

        setupDatePickers(dialogBinding)
        setupStudentDropdown(dialogBinding)
        
        // Reset attachment
        selectedAttachmentUri = student.attachmentUri?.let { Uri.parse(it) }
        selectedStudent = student

        // Pre-fill the form
        dialogBinding.apply {
            // Set the student dropdown to the current student
            val studentIndex = activeStudents.indexOfFirst { it.id == student.id }
            if (studentIndex >= 0) {
                studentDropdown.setText(student.name, false)
            }
            
            // Fill in registration data only
            startDateInput.setText(student.startDate?.let { dateFormat.format(it) } ?: "")
            startDateInput.tag = student.startDate
            endDateInput.setText(student.endDate?.let { dateFormat.format(it) } ?: "")
            endDateInput.tag = student.endDate
            amountInput.setText(student.amount.toString())
            paidSwitch.isChecked = student.isPaid
            
            // Setup attachment for payment receipt
            if (student.attachmentUri != null) {
                try {
                    updateAttachmentPreview(dialogBinding, Uri.parse(student.attachmentUri))
                } catch (e: Exception) {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.receipt_unavailable) + ": ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                    attachmentNameText.text = getString(R.string.receipt_unavailable)
                    attachmentPreview.visibility = View.GONE
                }
            }
            
            addAttachmentButton.setOnClickListener {
                if (student.isPaid || paidSwitch.isChecked) {
                    getContent.launch("*/*")
                } else {
                    showSnackbar(getString(R.string.attachments_only_for_paid_registrations))
                }
            }
            
            saveButton.setOnClickListener {
                // Validate form
                if (validateRegistrationForm(dialogBinding)) {
                    val updatedStudent = selectedStudent!!.copy(
                        startDate = startDateInput.tag as Date,
                        endDate = endDateInput.tag as Date,
                        amount = amountInput.text.toString().toDoubleOrNull()!!,
                        isPaid = paidSwitch.isChecked,
                        paymentDate = if (paidSwitch.isChecked && !student.isPaid) Date() else student.paymentDate,
                        attachmentUri = selectedAttachmentUri?.toString() ?: student.attachmentUri
                    )
                    viewModel.updateStudent(updatedStudent)
                    dialog.dismiss()
                    showSnackbar(getString(R.string.registration_updated))
                }
                // If validation fails, dialog stays open with errors shown
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
                viewModel.markAsPaid(student)
                showSnackbar("Payment status updated and transaction recorded")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showUnpaidConfirmation(student: WTStudent) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Change Payment Status")
            .setMessage("Mark ${student.name} as unpaid? This will deduct the payment amount from your transactions.")
            .setPositiveButton("Yes") { _, _ ->
                viewModel.markAsUnpaid(student)
                showSnackbar("Payment status updated and transaction reversed")
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
        // Format dates with null check
        val startDateFormatted = student.startDate?.let { dateFormat.format(it) } ?: "N/A"
        val endDateFormatted = student.endDate?.let { dateFormat.format(it) } ?: "N/A"
        
        val message = """
            Student: ${student.name}
            Course Period: $startDateFormatted - $endDateFormatted
            Amount: ₺${student.amount}
            Payment Status: ${if (student.isPaid) "Paid" else "Unpaid"}
        """.trimIndent()
        
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Registration Information: ${student.name}")
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