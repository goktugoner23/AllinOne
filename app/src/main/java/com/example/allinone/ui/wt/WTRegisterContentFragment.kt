package com.example.allinone.ui.wt

import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.os.Handler
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.allinone.R
import com.example.allinone.adapters.WTRegistrationAdapter
import com.example.allinone.data.WTRegistration
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
import kotlinx.coroutines.launch
import android.transition.TransitionManager
import android.transition.Slide
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageView

/**
 * Fragment for displaying the list of registrations.
 * This avoids recursive instantiation in the WTRegistryFragment.
 */
class WTRegisterContentFragment : Fragment() {
    private var _binding: FragmentWtRegisterBinding? = null
    private val binding get() = _binding!!
    private val viewModel: WTRegisterViewModel by viewModels()
    private lateinit var adapter: WTRegistrationAdapter
    private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    private var selectedAttachmentUri: Uri? = null
    private var currentDialogBinding: DialogEditWtStudentBinding? = null
    private var students: List<WTStudent> = emptyList()
    private var registrations: List<WTRegistration> = emptyList()
    private var selectedStudent: WTStudent? = null
    private var selectedRegistration: WTRegistration? = null
    
    private val getContent = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        handleAttachmentResult(uri)
    }
    
    private var searchMenuItem: MenuItem? = null
    private var menuProvider: MenuProvider? = null
    
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentWtRegisterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupFab()
        setupMenu()
        observeStudents()
        observeRegistrations()
        observeNetworkStatus()
        setupSwipeRefresh()
        
        Log.d("WTRegisterContent", "Fragment created and set up")
        
        // Force immediate data refresh
        viewModel.refreshData()
        Log.d("WTRegisterContent", "Forced data refresh initiated")
    }
    
    override fun onResume() {
        super.onResume()
        // Force refresh data on resume
        viewModel.refreshData()
    }

    private fun setupRecyclerView() {
        Log.d("WTRegisterContent", "Setting up RecyclerView")
        
        adapter = WTRegistrationAdapter(
            onItemClick = { registration -> showEditDialog(registration) },
            onLongPress = { registration, view -> showContextMenu(registration, view) },
            onPaymentStatusClick = { registration -> 
                // Toggle payment status
                val updatedRegistration = registration.copy(isPaid = !registration.isPaid)
                viewModel.updateRegistration(updatedRegistration)
            },
            onShareClick = { registration -> shareRegistrationInfo(registration) },
            getStudentName = { studentId -> 
                students.find { it.id == studentId }?.name ?: "Unknown Student"
            }
        )
        
        binding.studentsRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@WTRegisterContentFragment.adapter
            Log.d("WTRegisterContent", "Adapter attached to RecyclerView")
        }
    }

    private fun setupFab() {
        binding.addStudentFab.setOnClickListener {
            showAddDialog()
        }

        // Long press on FAB for debugging
        binding.addStudentFab.setOnLongClickListener {
            dumpRegistrationData()
            true
        }
    }

    private fun setupMenu() {
        // Remove any existing menu provider
        menuProvider?.let {
            try {
                requireActivity().removeMenuProvider(it)
            } catch (e: Exception) {
                Log.e("WTRegisterContent", "Error removing menu provider: ${e.message}")
            }
        }
        
        // Create a new menu provider
        menuProvider = object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                // Clear existing menu items to prevent duplicates
                menu.clear()
                
                menuInflater.inflate(R.menu.search_register, menu)
                searchMenuItem = menu.findItem(R.id.action_search)
                
                val searchView = searchMenuItem?.actionView as? SearchView
                searchView?.let {
                    // Set search view expanded listener
                    it.setOnSearchClickListener { _ ->
                        // Animate search view expansion
                        val slide = Slide(Gravity.END)
                        slide.duration = 200
                        TransitionManager.beginDelayedTransition((activity as AppCompatActivity).findViewById(R.id.toolbar), slide)
                    }
                    
                    // Set search view collapse listener
                    it.setOnCloseListener { 
                        // Animate search view collapse
                        val slide = Slide(Gravity.END)
                        slide.duration = 200
                        TransitionManager.beginDelayedTransition((activity as AppCompatActivity).findViewById(R.id.toolbar), slide)
                        
                        // Reset the registration list
                        resetRegistrationList()
                        true
                    }
                    
                    // Set up query listener
                    it.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                        override fun onQueryTextSubmit(query: String?): Boolean {
                            query?.let { searchQuery ->
                                filterRegistrations(searchQuery)
                            }
                            
                            // Hide keyboard
                            val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                            imm.hideSoftInputFromWindow(it.windowToken, 0)
                            
                            return true
                        }
                        
                        override fun onQueryTextChange(newText: String?): Boolean {
                            newText?.let { searchQuery ->
                                filterRegistrations(searchQuery)
                            }
                            return true
                        }
                    })
                    
                    // Customize search view appearance
                    val searchEditText = it.findViewById<EditText>(androidx.appcompat.R.id.search_src_text)
                    searchEditText?.apply {
                        setHintTextColor(ContextCompat.getColor(requireContext(), android.R.color.white))
                        setTextColor(ContextCompat.getColor(requireContext(), android.R.color.white))
                        hint = getString(R.string.search_hint)
                        imeOptions = EditorInfo.IME_ACTION_SEARCH
                        
                        // Set X button to reset search
                        setOnEditorActionListener { _, actionId, _ ->
                            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                                // Hide keyboard
                                val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                                imm.hideSoftInputFromWindow(this.windowToken, 0)
                                return@setOnEditorActionListener true
                            }
                            false
                        }
                    }

                    // Set search icon color to white (expanded state)
                    val searchIcon = it.findViewById<ImageView>(androidx.appcompat.R.id.search_mag_icon)
                    searchIcon?.setColorFilter(ContextCompat.getColor(requireContext(), android.R.color.white),
                        android.graphics.PorterDuff.Mode.SRC_IN)
                    
                    // Make sure search icon is visible and properly sized
                    searchIcon?.apply {
                        visibility = View.VISIBLE
                        val iconSize = resources.getDimensionPixelSize(androidx.appcompat.R.dimen.abc_dropdownitem_icon_width)
                        val params = layoutParams
                        params.width = iconSize
                        params.height = iconSize
                        layoutParams = params
                    }

                    // Set close button color to white
                    val closeButton = it.findViewById<ImageView>(androidx.appcompat.R.id.search_close_btn)
                    closeButton?.setColorFilter(ContextCompat.getColor(requireContext(), android.R.color.white),
                        android.graphics.PorterDuff.Mode.SRC_IN)
                }
            }
            
            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    R.id.action_search -> {
                        // The search icon click is handled by the SearchView
                        true
                    }
                    else -> false
                }
            }
        }
        
        // Add the menu provider tied to the STARTED lifecycle state to ensure cleanup when not visible
        requireActivity().addMenuProvider(menuProvider!!, viewLifecycleOwner, Lifecycle.State.STARTED)
    }

    private fun resetRegistrationList() {
        viewModel.registrations.value?.let { registrations ->
            val sortedRegistrations = registrations.sortedByDescending { it.startDate }
            adapter.submitList(sortedRegistrations)
            
            // Show or hide empty state
            binding.emptyState.visibility = if (sortedRegistrations.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun filterRegistrations(query: String) {
        viewModel.registrations.value?.let { registrations ->
            val filteredRegistrations = if (query.isBlank()) {
                registrations
            } else {
                // Find the matching student names first
                val matchingStudentIds = students.filter { 
                    it.name.contains(query, ignoreCase = true) 
                }.map { it.id }
                
                // Filter registrations by student name or registration details
                registrations.filter { registration ->
                    matchingStudentIds.contains(registration.studentId) ||
                    registration.notes?.contains(query, ignoreCase = true) == true ||
                    registration.amount.toString().contains(query)
                }
            }
            
            val sortedRegistrations = filteredRegistrations.sortedByDescending { it.startDate }
            adapter.submitList(sortedRegistrations)
            
            // Show or hide empty state
            binding.emptyState.visibility = if (sortedRegistrations.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun observeStudents() {
        viewModel.students.observe(viewLifecycleOwner) { studentList ->
            students = studentList.filter { it.isActive }
        }
    }

    private fun observeRegistrations() {
        viewModel.registrations.observe(viewLifecycleOwner) { registrations ->
            // Apply current search filter if search is active
            val searchView = searchMenuItem?.actionView as? SearchView
            if (searchView?.isIconified == false && !searchView.query.isNullOrEmpty()) {
                filterRegistrations(searchView.query.toString())
            } else {
                // Sort registrations by start date (newest first)
                val sortedRegistrations = registrations.sortedByDescending { it.startDate }
                adapter.submitList(sortedRegistrations)
                
                // Show or hide empty state
                binding.emptyState.visibility = if (sortedRegistrations.isEmpty()) View.VISIBLE else View.GONE
            }

            // Log the registration count
            Log.d("WTRegisterContent", "Loaded ${registrations.size} registrations")
        }
    }
    
    private fun observeNetworkStatus() {
        // Handler for delayed operations
        val handler = Handler(android.os.Looper.getMainLooper())
        var isOfflineViewShown = false
        
        viewModel.isNetworkAvailable.observe(viewLifecycleOwner) { isAvailable ->
            if (!isAvailable) {
                // When offline, show the banner
                binding.networkStatusBanner.visibility = View.VISIBLE
                isOfflineViewShown = true
            } else if (isOfflineViewShown) {
                // When online and banner was shown, hide it after 2 seconds
                handler.postDelayed({
                    // Check if fragment is still attached before changing visibility
                    if (isAdded && _binding != null) {
                        binding.networkStatusBanner.visibility = View.GONE
                        isOfflineViewShown = false
                    }
                }, 2000)
            }
        }
    }
    
    private fun setupSwipeRefresh() {
        binding.swipeRefreshLayout.setOnRefreshListener {
            viewModel.refreshData()
        }
        
        // Observe loading state to hide the refresh indicator when done
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.swipeRefreshLayout.isRefreshing = isLoading
        }
    }
    
    private fun showAddDialog() {
        val dialogBinding = DialogEditWtStudentBinding.inflate(layoutInflater)
        currentDialogBinding = dialogBinding
        setupDatePickers(dialogBinding)
        setupStudentDropdown(dialogBinding)

        // Set switch to unchecked by default (unpaid)
        dialogBinding.paidSwitch.isChecked = false

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.add_registration)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.save, null) // We'll set this later
            .setNegativeButton(R.string.cancel, null)
            .create()

        // Configure dialog window for keyboard handling
        dialog.window?.apply {
            setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
            setLayout(
                android.view.WindowManager.LayoutParams.MATCH_PARENT,
                android.view.WindowManager.LayoutParams.WRAP_CONTENT
            )
        }

        // Set the dialog button listener after creating to prevent auto-dismissal
        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (validateRegistrationForm(dialogBinding)) {
                    // Save the registration
                    val student = selectedStudent ?: return@setOnClickListener
                    val startDate = dialogBinding.startDateInput.tag as? Date
                    val endDate = dialogBinding.endDateInput.tag as? Date
                    val amountText = dialogBinding.amountInput.text.toString().trim()
                    val amount = if (amountText.isEmpty()) 0.0 else amountText.toDoubleOrNull() ?: 0.0
                    val notesText = dialogBinding.notesEditText.text.toString().trim()
                    val isPaid = dialogBinding.paidSwitch.isChecked

                    viewModel.addRegistration(
                        studentId = student.id,
                        amount = amount,
                        startDate = startDate,
                        endDate = endDate,
                        attachmentUri = selectedAttachmentUri?.toString(),
                        notes = if (notesText.isEmpty()) null else notesText,
                        isPaid = isPaid
                    )

                    dialog.dismiss()
                    Toast.makeText(requireContext(), "Registration saved", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Clear selections and show dialog
        selectedStudent = null
        selectedRegistration = null
        selectedAttachmentUri = null
        dialog.show()
    }

    private fun validateRegistrationForm(dialogBinding: DialogEditWtStudentBinding): Boolean {
        var isValid = true
        
        // Get required values
        val student = selectedStudent
        val startDate = dialogBinding.startDateInput.tag as? Date
        val endDate = dialogBinding.endDateInput.tag as? Date
        val amountText = dialogBinding.amountInput.text.toString().trim()
        
        // Validation logic
        if (student == null) {
            dialogBinding.studentDropdown.error = getString(R.string.please_select_student)
            isValid = false
        } else {
            dialogBinding.studentDropdown.error = null
        }
        
        if (startDate == null) {
            dialogBinding.startDateInput.error = getString(R.string.please_select_start_date)
            isValid = false
        } else {
            dialogBinding.startDateInput.error = null
            
            if (student != null) {
                val existingRegistration = registrations.find { 
                    it.studentId == student.id && 
                    it.startDate?.time == startDate.time &&
                    it.id != selectedRegistration?.id
                }
                
                if (existingRegistration != null) {
                    dialogBinding.startDateInput.error = "This student already has a registration with this date"
                    isValid = false
                }
            }
        }
        
        if (endDate == null) {
            dialogBinding.endDateInput.error = getString(R.string.please_select_end_date)
            isValid = false
        } else {
            dialogBinding.endDateInput.error = null
            
            if (startDate != null && endDate.before(startDate)) {
                dialogBinding.endDateInput.error = "End date must be after start date"
                isValid = false
            }
        }
        
        if (amountText.isEmpty()) {
            dialogBinding.amountInput.error = "Please enter an amount"
            isValid = false
        } else {
            dialogBinding.amountInput.error = null
            
            val amount = amountText.toDoubleOrNull()
            if (amount == null || amount <= 0) {
                dialogBinding.amountInput.error = "Please enter a valid amount"
                isValid = false
            }
        }
        
        return isValid
    }
    
    private fun setupDatePickers(dialogBinding: DialogEditWtStudentBinding) {
        dialogBinding.startDateInput.setOnClickListener { showDatePicker(dialogBinding.startDateInput) }
        dialogBinding.endDateInput.setOnClickListener { showDatePicker(dialogBinding.endDateInput) }
    }
    
    private fun showDatePicker(view: View) {
        val calendar = Calendar.getInstance()
        val existingDate = view.tag as? Date
        if (existingDate != null) {
            calendar.time = existingDate
        }
        
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH)
        
        DatePickerDialog(requireContext(), { _, selectedYear, selectedMonth, selectedDay ->
            val selectedCalendar = Calendar.getInstance()
            selectedCalendar.set(selectedYear, selectedMonth, selectedDay)
            val selectedDate = selectedCalendar.time
            
            // Format date for display
            val formattedDate = dateFormat.format(selectedDate)
            
            // Get the current dialog binding
            val dialogBinding = currentDialogBinding ?: return@DatePickerDialog
            
            when (view.id) {
                R.id.startDateInput -> dialogBinding.startDateInput.setText(formattedDate)
                R.id.endDateInput -> dialogBinding.endDateInput.setText(formattedDate)
            }
            
            // Store actual date object in the tag
            view.tag = selectedDate
        }, year, month, dayOfMonth).show()
    }
    
    private fun setupStudentDropdown(dialogBinding: DialogEditWtStudentBinding) {
        val studentNames = students.map { it.name }
        val adapter = ArrayAdapter(requireContext(), R.layout.dropdown_item, studentNames)
        dialogBinding.studentDropdown.setAdapter(adapter)
        
        dialogBinding.studentDropdown.setOnItemClickListener { _, _, position, _ ->
            selectedStudent = students[position]
        }
    }
    
    private fun showEditDialog(registration: WTRegistration) {
        val dialogBinding = DialogEditWtStudentBinding.inflate(layoutInflater)
        currentDialogBinding = dialogBinding
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.edit_registration)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.save, null) // We'll set this later
            .setNegativeButton(R.string.cancel, null)
            .create()

        dialog.window?.apply {
            setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
            setLayout(
                android.view.WindowManager.LayoutParams.MATCH_PARENT,
                android.view.WindowManager.LayoutParams.WRAP_CONTENT
            )
        }

        setupDatePickers(dialogBinding)
        setupStudentDropdown(dialogBinding)
        
        selectedAttachmentUri = registration.attachmentUri?.let { Uri.parse(it) }
        selectedRegistration = registration

        // Pre-fill form with registration details
        dialogBinding.apply {
            // Set student dropdown
            val studentIndex = students.indexOfFirst { it.id == registration.studentId }
            if (studentIndex >= 0) {
                selectedStudent = students[studentIndex]
                studentDropdown.setText(students[studentIndex].name, false)
            }
            
            // Set dates
            startDateInput.setText(registration.startDate?.let { dateFormat.format(it) } ?: "")
            startDateInput.tag = registration.startDate
            endDateInput.setText(registration.endDate?.let { dateFormat.format(it) } ?: "")
            endDateInput.tag = registration.endDate
            
            // Set amount and notes
            amountInput.setText(registration.amount.toString())
            notesEditText.setText(registration.notes ?: "")
            
            // Set isPaid checkbox with switch
            paidSwitch.isChecked = registration.isPaid
            
            // Setup attachment preview if exists
            if (registration.attachmentUri != null) {
                try {
                    updateAttachmentPreview(dialogBinding, Uri.parse(registration.attachmentUri))
                } catch (e: Exception) {
                    attachmentNameText.text = "Attachment unavailable"
                    attachmentPreview.visibility = View.GONE
                }
            }
            
            // Setup attachment button
            addAttachmentButton.setOnClickListener {
                getContent.launch("*/*")
            }
        }

        // Set the positive button listener after creating to prevent auto-dismissal
        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (validateRegistrationForm(dialogBinding)) {
                    // Update the registration
                    val student = selectedStudent ?: return@setOnClickListener
                    val startDate = dialogBinding.startDateInput.tag as? Date
                    val endDate = dialogBinding.endDateInput.tag as? Date
                    val amountText = dialogBinding.amountInput.text.toString().trim()
                    val amount = if (amountText.isEmpty()) 0.0 else amountText.toDoubleOrNull() ?: 0.0
                    val notesText = dialogBinding.notesEditText.text.toString().trim()
                    val isPaid = dialogBinding.paidSwitch.isChecked

                    val updatedRegistration = registration.copy(
                        studentId = student.id,
                        amount = amount,
                        startDate = startDate,
                        endDate = endDate,
                        attachmentUri = selectedAttachmentUri?.toString(),
                        notes = if (notesText.isEmpty()) null else notesText,
                        isPaid = isPaid
                    )

                    viewModel.updateRegistration(updatedRegistration)
                    dialog.dismiss()
                    Toast.makeText(requireContext(), "Registration updated", Toast.LENGTH_SHORT).show()
                }
            }
        }

        dialog.show()
    }
    
    private fun handleAttachmentResult(uri: Uri?) {
        uri ?: return
        
        try {
            selectedAttachmentUri = uri
            val dialogBinding = currentDialogBinding ?: return
            
            // Get file name for display
            val fileName = uri.lastPathSegment ?: "Selected file"
            dialogBinding.attachmentNameText.text = fileName
            dialogBinding.attachmentPreview.visibility = View.VISIBLE
            
        } catch (e: Exception) {
            Toast.makeText(
                requireContext(),
                "Failed to handle attachment: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
            e.printStackTrace()
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
    
    private fun shareRegistrationInfo(registration: WTRegistration) {
        // Get student name
        val studentName = students.find { it.id == registration.studentId }?.name ?: "Unknown Student"
        
        // Format dates
        val startDateText = registration.startDate?.let { dateFormat.format(it) } ?: "Unknown"
        val endDateText = registration.endDate?.let { dateFormat.format(it) } ?: "Unknown"
        
        // Create share text
        val shareText = """
            Registration Information:
            Student: $studentName
            Period: $startDateText to $endDateText
            Amount: ₺${registration.amount}
            Payment Status: ${if (registration.isPaid) "Paid" else "Unpaid"}
            ${if (!registration.notes.isNullOrEmpty()) "Notes: ${registration.notes}" else ""}
        """.trimIndent()
        
        // Create and start intent
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Registration Information for $studentName")
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        
        startActivity(Intent.createChooser(intent, "Share Registration Info"))
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
        
        // Delete the registration
        viewModel.deleteRegistration(registration)
        
        // Dismiss loading indicator
        loadingSnackbar.dismiss()
        
        // Show success message
        Snackbar.make(
            binding.root,
            getString(R.string.registration_deleted),
            Snackbar.LENGTH_LONG
        ).show()
    }
    
    private fun showSnackbar(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

    private fun dumpRegistrationData() {
        val currentRegs = viewModel.registrations.value ?: emptyList()
        Log.d("WTRegisterContent", "DUMP: Currently have ${currentRegs.size} registrations")
        
        currentRegs.forEachIndexed { index, reg ->
            Log.d("WTRegisterContent", "DUMP: Reg[$index] = ID:${reg.id}, StudentID:${reg.studentId}, " +
                    "Date:${reg.startDate}, Amount:${reg.amount}")
        }
        
        // Directly check Firebase and local cache state
        viewModel.refreshData()
        
        // Check Firebase Auth status
        val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
        val currentUser = auth.currentUser
        val authStatus = if (currentUser != null) {
            "Authenticated as: ${currentUser.uid}"
        } else {
            "NOT AUTHENTICATED - this will prevent registrations from loading"
        }
        
        // Show a toast with the count for easy debugging
        val message = "Registrations: ${currentRegs.size} - Check logs for details\n" +
                "Auth: $authStatus\n" +
                "Long press again after refresh to update count"
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
    }

    private fun updateAttachmentPreview(dialogBinding: DialogEditWtStudentBinding, uri: Uri) {
        try {
            // Get the attachment name
            val fileName = uri.lastPathSegment ?: "Selected file"
            dialogBinding.attachmentNameText.text = fileName
            dialogBinding.attachmentPreview.visibility = View.VISIBLE
            
            // Make the attachment area clickable to view the full file
            dialogBinding.attachmentNameText.setOnClickListener {
                openAttachment(uri)
            }
        } catch (e: Exception) {
            Log.e("WTRegisterContent", "Error updating attachment preview", e)
            dialogBinding.attachmentNameText.text = "Error displaying attachment"
            dialogBinding.attachmentPreview.visibility = View.GONE
        }
    }
    
    private fun openAttachment(uri: Uri) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/*")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Failed to open attachment: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        // Remove the menu provider when the view is destroyed
        menuProvider?.let {
            try {
                requireActivity().removeMenuProvider(it)
            } catch (e: Exception) {
                Log.e("WTRegisterContent", "Error removing menu provider: ${e.message}")
            }
        }
        
        super.onDestroyView()
        _binding = null
        currentDialogBinding = null
    }
} 