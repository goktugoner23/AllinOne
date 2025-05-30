package com.example.allinone.ui.wt

import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.allinone.R
import com.example.allinone.adapters.WTRegistrationAdapter
import com.example.allinone.data.WTRegistration
import com.example.allinone.data.WTStudent
import com.example.allinone.databinding.DialogEditWtStudentBinding
import com.example.allinone.databinding.FragmentWtRegisterBinding
import com.example.allinone.viewmodels.CalendarViewModel
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
    private val calendarViewModel: CalendarViewModel by viewModels()
    private lateinit var adapter: WTRegistrationAdapter
    private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    private var selectedAttachmentUri: Uri? = null
    private var currentDialogBinding: DialogEditWtStudentBinding? = null
    private var students: List<WTStudent> = emptyList()
    private var registrations: List<WTRegistration> = emptyList()
    private var selectedStudent: WTStudent? = null
    private var selectedRegistration: WTRegistration? = null
    private var isEditMode = false

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
                    binding.attachmentNameText.text = getFileName(uri)
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
        observeRegistrations()
        observeNetworkStatus()

        // Force data refresh
        viewModel.refreshData()
    }

    override fun onResume() {
        super.onResume()
        // Force refresh data on resume
        viewModel.refreshData()
    }

    private fun setupRecyclerView() {
        // Add log to track setup
        Log.d("WTRegisterFragment", "Setting up RecyclerView")

        adapter = WTRegistrationAdapter(
            onItemClick = { registration -> showEditDialog(registration) },
            onLongPress = { registration, view -> showContextMenu(registration, view) },
            onPaymentStatusClick = { registration ->
                // No need for payment status handling as all registrations are paid
                // Could add ability to delete registration here
                showDeleteConfirmation(registration)
            },
            onShareClick = { registration -> shareRegistrationInfo(registration) },
            getStudentName = { studentId ->
                students.find { it.id == studentId }?.name ?: "Unknown Student"
            },
            getStudentPhotoUri = { studentId ->
                students.find { it.id == studentId }?.photoUri
            }
        )

        binding.studentsRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@WTRegisterFragment.adapter
            // Add log to verify adapter is attached
            Log.d("WTRegisterFragment", "Adapter attached to RecyclerView")
        }
    }

    private fun setupFab() {
        binding.addStudentFab.setOnClickListener {
            showAddDialog()
        }
    }

    private fun observeStudents() {
        viewModel.students.observe(viewLifecycleOwner) { studentList ->
            students = studentList.filter { it.isActive }
        }
    }

    private fun observeRegistrations() {
        viewModel.registrations.observe(viewLifecycleOwner) { registrationsList ->
            registrations = registrationsList

            // Sort registrations by start date, with most recent first
            val sortedRegistrations = registrationsList.sortedByDescending { it.startDate }
            adapter.submitList(sortedRegistrations)

            // Show empty state if there are no registrations
            if (sortedRegistrations.isEmpty()) {
                binding.emptyState.visibility = View.VISIBLE
                binding.studentsRecyclerView.visibility = View.GONE
            } else {
                binding.emptyState.visibility = View.GONE
                binding.studentsRecyclerView.visibility = View.VISIBLE
            }

            // Force adapter to refresh
            adapter.notifyDataSetChanged()
        }
    }

    private fun showAddDialog() {
        val dialogBinding = DialogEditWtStudentBinding.inflate(layoutInflater)
        currentDialogBinding = dialogBinding
        setupDatePickers(dialogBinding)
        setupStudentDropdown(dialogBinding)

        // Set paid switch to false by default
        dialogBinding.paidSwitch.isChecked = false

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.add_registration)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.save, null) // Set action later
            .setNegativeButton(R.string.cancel, null)
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

        dialog.setOnShowListener {
            val positiveButton = dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)
            positiveButton.setOnClickListener {
                // Validate form
                if (validateRegistrationForm(dialogBinding)) {
                    val student = selectedStudent!!
                    val startDate = dialogBinding.startDateInput.tag as Date
                    val endDate = dialogBinding.endDateInput.tag as Date
                    val amount = dialogBinding.amountInput.text.toString().toDoubleOrNull() ?: 0.0
                    val isPaid = dialogBinding.paidSwitch.isChecked

                    // Create a new registration record
                    viewModel.addRegistration(
                        studentId = student.id,
                        amount = amount,
                        startDate = startDate,
                        endDate = endDate,
                        attachmentUri = selectedAttachmentUri?.toString(),
                        notes = dialogBinding.notesEditText.text.toString(),
                        isPaid = isPaid
                    )

                    // Refresh calendar to show the new registration
                    calendarViewModel.forceRefresh()

                    dialog.dismiss()
                    showSnackbar(getString(R.string.registration_success))
                }
                // If validation fails, dialog stays open with errors shown
            }
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
                val existingRegistration = registrations.find {
                    it.studentId == student.id &&
                    it.startDate?.time == startDate.time &&
                    it.id != selectedRegistration?.id  // Skip current registration if editing
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

            // Check that end date is after start date
            if (startDate != null && endDate.before(startDate)) {
                dialogBinding.endDateInput.error = "End date must be after start date"
                isValid = false
            }
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
        val studentNames = students.map { it.name }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, studentNames)
        dialogBinding.studentDropdown.setAdapter(adapter)

        // Set listener for selection
        dialogBinding.studentDropdown.setOnItemClickListener { _, _, position, _ ->
            selectedStudent = students[position]
        }
    }

    private fun showEditDialog(registration: WTRegistration) {
        val dialogBinding = DialogEditWtStudentBinding.inflate(layoutInflater)
        currentDialogBinding = dialogBinding

        // Pre-fill form with registration details
        setupDatePickers(dialogBinding)
        setupStudentDropdown(dialogBinding)

        // Store resources for attachment handling
        selectedAttachmentUri = registration.attachmentUri?.let { Uri.parse(it) }
        selectedRegistration = registration

        // Fill in form fields
        dialogBinding.apply {
            // Set student dropdown
            students.forEachIndexed { _, student ->
                if (student.id == registration.studentId) {
                    selectedStudent = student
                    studentDropdown.setText(student.name, false)
                }
            }

            // Set dates
            if (registration.startDate != null) {
                startDateInput.setText(dateFormat.format(registration.startDate))
                startDateInput.tag = registration.startDate
            }

            if (registration.endDate != null) {
                endDateInput.setText(dateFormat.format(registration.endDate))
                endDateInput.tag = registration.endDate
            }

            // Set amount and notes
            amountInput.setText(registration.amount.toString())
            notesEditText.setText(registration.notes ?: "")

            // Set payment status
            paidSwitch.isChecked = registration.isPaid

            // Set attachment if available
            if (registration.attachmentUri != null) {
                try {
                    val uri = Uri.parse(registration.attachmentUri)
                    val fileName = getFileName(uri)
                    attachmentNameText.text = fileName

                    attachmentPreview.visibility = View.VISIBLE
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
                getContent.launch("*/*")
            }
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.edit_registration)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.save, null) // Set action later
            .setNegativeButton(R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            val positiveButton = dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)
            positiveButton.setOnClickListener {
                // Validate form
                if (validateRegistrationForm(dialogBinding)) {
                    val updatedRegistration = selectedRegistration!!.copy(
                        startDate = dialogBinding.startDateInput.tag as Date,
                        endDate = dialogBinding.endDateInput.tag as Date,
                        amount = dialogBinding.amountInput.text.toString().toDoubleOrNull()!!,
                        attachmentUri = selectedAttachmentUri?.toString() ?: registration.attachmentUri,
                        isPaid = dialogBinding.paidSwitch.isChecked
                    )
                    viewModel.updateRegistration(updatedRegistration)

                    // Refresh calendar to show the updated registration
                    calendarViewModel.forceRefresh()

                    dialog.dismiss()
                    showSnackbar(getString(R.string.registration_updated))
                }
                // If validation fails, dialog stays open with errors shown
            }
        }

        dialog.show()
    }

    private fun setupDatePickers(dialogBinding: DialogEditWtStudentBinding) {
        val calendar = Calendar.getInstance()

        // For start date
        dialogBinding.startDateInput.setOnClickListener {
            val year = calendar.get(Calendar.YEAR)
            val month = calendar.get(Calendar.MONTH)
            val day = calendar.get(Calendar.DAY_OF_MONTH)

            DatePickerDialog(requireContext(), { _, selectedYear, selectedMonth, selectedDay ->
                calendar.set(selectedYear, selectedMonth, selectedDay)
                val selectedDate = calendar.time
                dialogBinding.startDateInput.setText(dateFormat.format(selectedDate))
                dialogBinding.startDateInput.tag = selectedDate

                // Get lessons from parent fragment
                val lessons = when (val parentFragment = parentFragment) {
                    is WTRegistryFragment -> parentFragment.getLessons()
                    else -> emptyList()
                }

                // Calculate end date after 8 lessons from the start date
                val startCalendar = Calendar.getInstance()
                startCalendar.time = selectedDate
                val endDate = calendarViewModel.calculateDateAfterNLessons(startCalendar, lessons, 8)

                // Set end time to 22:00 (10pm)
                val endCalendar = Calendar.getInstance()
                endCalendar.time = endDate
                endCalendar.set(Calendar.HOUR_OF_DAY, 22)
                endCalendar.set(Calendar.MINUTE, 0)
                endCalendar.set(Calendar.SECOND, 0)
                endCalendar.set(Calendar.MILLISECOND, 0)

                // Update the end date field
                dialogBinding.endDateInput.setText(dateFormat.format(endCalendar.time))
                dialogBinding.endDateInput.tag = endCalendar.time
            }, year, month, day).show()
        }

        // For end date
        dialogBinding.endDateInput.setOnClickListener {
            val year = calendar.get(Calendar.YEAR)
            val month = calendar.get(Calendar.MONTH)
            val day = calendar.get(Calendar.DAY_OF_MONTH)

            DatePickerDialog(requireContext(), { _, selectedYear, selectedMonth, selectedDay ->
                calendar.set(selectedYear, selectedMonth, selectedDay)

                // Set time to 22:00 (10pm)
                calendar.set(Calendar.HOUR_OF_DAY, 22)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)

                val selectedDate = calendar.time
                dialogBinding.endDateInput.setText(dateFormat.format(selectedDate))
                dialogBinding.endDateInput.tag = selectedDate
            }, year, month, day).show()
        }
    }

    private fun updateAttachmentPreview(dialogBinding: DialogEditWtStudentBinding, uri: Uri) {
        dialogBinding.attachmentNameText.text = getFileName(uri)
        dialogBinding.attachmentNameText.visibility = View.VISIBLE

        val mimeType = context?.contentResolver?.getType(uri)
        if (mimeType?.startsWith("image/") == true) {
            try {
                dialogBinding.attachmentPreview.setImageURI(uri)
                dialogBinding.attachmentPreview.visibility = View.VISIBLE
            } catch (e: Exception) {
                dialogBinding.attachmentPreview.visibility = View.GONE
            }
        } else {
            dialogBinding.attachmentPreview.visibility = View.GONE
        }
    }

    private fun getFileName(uri: Uri): String {
        val contentResolver = context?.contentResolver ?: return "File"

        // First try with query
        val cursor = contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val displayNameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (displayNameIndex != -1) {
                    return it.getString(displayNameIndex)
                }
            }
        }

        // If query failed, try to extract from URI path
        val result = uri.path?.let { path ->
            path.lastIndexOf('/').let { lastSlash ->
                if (lastSlash != -1) path.substring(lastSlash + 1) else path
            }
        } ?: "File"

        return result
    }

    private fun showSnackbar(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    }

    private fun shareRegistrationInfo(registration: WTRegistration) {
        // Get student name from the students list
        val studentName = students.find { it.id == registration.studentId }?.name ?: "Unknown Student"

        // Format dates with null check
        val startDateFormatted = registration.startDate?.let { dateFormat.format(it) } ?: "N/A"
        val endDateFormatted = registration.endDate?.let { dateFormat.format(it) } ?: "N/A"

        val message = """
            Student: $studentName
            Course Period: $startDateFormatted - $endDateFormatted
            Amount: ₺${registration.amount}
            Payment Status: Paid
        """.trimIndent()

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Registration Information: $studentName")
            putExtra(Intent.EXTRA_TEXT, message)
        }

        startActivity(Intent.createChooser(intent, "Share via"))
    }

    private fun observeNetworkStatus() {
        viewModel.isNetworkAvailable.observe(viewLifecycleOwner) { isAvailable ->
            if (isAvailable) {
                binding.networkStatusBanner.visibility = View.GONE
            } else {
                binding.networkStatusBanner.visibility = View.VISIBLE
            }
        }
    }

    private fun showContextMenu(registration: WTRegistration, view: View) {
        val popup = PopupMenu(requireContext(), view)
        popup.menuInflater.inflate(R.menu.wt_registration_context_menu, popup.menu)

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_edit -> {
                    showEditDialog(registration)
                    true
                }
                R.id.action_delete -> {
                    showDeleteConfirmation(registration)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun showDeleteConfirmation(registration: WTRegistration) {
        // Get student name from the students list
        val studentName = students.find { it.id == registration.studentId }?.name ?: "Unknown Student"

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.delete_registration)
            .setMessage(getString(R.string.delete_registration_confirmation, studentName))
            .setPositiveButton(R.string.delete) { _, _ ->
                deleteFromHistory(registration)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun deleteFromHistory(registration: WTRegistration) {
        // Show loading indicator
        val loadingSnackbar = Snackbar.make(
            binding.root,
            getString(R.string.deleting),
            Snackbar.LENGTH_INDEFINITE
        )
        loadingSnackbar.show()

        viewModel.deleteRegistration(registration)

        // Observe changes to registrations to update UI
        viewModel.registrations.observe(viewLifecycleOwner) { registrations ->
            loadingSnackbar.dismiss()

            // Check if registration was successfully removed
            val stillExists = registrations.any { it.id == registration.id && it.startDate != null }
            if (!stillExists) {
                // Success - show success message
                Snackbar.make(
                    binding.root,
                    getString(R.string.registration_deleted),
                    Snackbar.LENGTH_LONG
                ).show()

                // Update adapter
                adapter.submitList(registrations)

                // Refresh calendar to remove the deleted registration
                calendarViewModel.forceRefresh()
            } else {
                // Failed to remove - show error
                Snackbar.make(
                    binding.root,
                    getString(R.string.error_deleting_registration),
                    Snackbar.LENGTH_LONG
                ).setAction(R.string.retry) {
                    // Try again
                    deleteFromHistory(registration)
                }.show()
            }

            // Remove the observer to prevent multiple callbacks
            viewModel.registrations.removeObservers(viewLifecycleOwner)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        currentDialogBinding = null
    }
}