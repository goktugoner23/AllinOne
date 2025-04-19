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
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
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
import com.google.android.material.textfield.MaterialAutoCompleteTextView
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

    // Month filter variables
    private var selectedMonth: Int? = null
    private val monthNames = arrayOf(
        "All Months", "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December", "Total"
    )

    // Constants for month selection
    private val MONTH_ALL = 0
    private val MONTH_TOTAL = 13

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
        setupMonthFilter()
        observeStudents()
        observeRegistrations()
        observeNetworkStatus()
        setupSwipeRefresh()

        // Set up total amount text with SpannableString
        setupTotalAmountText()

        Log.d("WTRegisterContent", "Fragment created and set up")

        // Force immediate data refresh
        viewModel.refreshData()
        Log.d("WTRegisterContent", "Forced data refresh initiated")

        // Calculate initial total amount
        viewModel.registrations.observe(viewLifecycleOwner) { registrations ->
            if (registrations.isNotEmpty()) {
                val totalAmount = registrations.sumOf { it.amount }
                updateTotalAmountText(totalAmount)
            }
        }
    }

    private fun setupTotalAmountText() {
        // Make the total amount text visible by default with a placeholder value
        binding.totalAmountText.visibility = View.VISIBLE
        updateTotalAmountText(0.0)
    }

    private fun updateTotalAmountText(totalAmount: Double) {
        // Create a SpannableString with different styles for label and amount
        val label = getString(R.string.total_amount_label) + " "
        val amount = getString(R.string.amount_format, totalAmount)
        val fullText = label + amount

        val spannableString = SpannableString(fullText)

        // Make the label part bold and black
        spannableString.setSpan(
            StyleSpan(android.graphics.Typeface.BOLD),
            0, label.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        spannableString.setSpan(
            ForegroundColorSpan(ContextCompat.getColor(requireContext(), android.R.color.black)),
            0, label.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        // Set color for the amount part based on value
        val amountColor = when {
            totalAmount > 20000 -> ContextCompat.getColor(requireContext(), R.color.colorSuccess) // Green
            totalAmount > 10000 -> ContextCompat.getColor(requireContext(), R.color.colorWarning) // Orange/Yellow
            else -> ContextCompat.getColor(requireContext(), R.color.colorError) // Red
        }

        spannableString.setSpan(
            ForegroundColorSpan(amountColor),
            label.length, fullText.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        // Set the text
        binding.totalAmountText.text = spannableString
        binding.totalAmountText.visibility = View.VISIBLE
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
        // Apply month filter if one is selected
        if (selectedMonth != null) {
            applyMonthFilter()
        } else {
            viewModel.registrations.value?.let { registrations ->
                val sortedRegistrations = registrations.sortedByDescending { it.startDate }
                adapter.submitList(sortedRegistrations)

                // Calculate and display total amount
                val totalAmount = sortedRegistrations.sumOf { it.amount }
                updateTotalAmountText(totalAmount)

                // Show or hide empty state
                binding.emptyState.visibility = if (sortedRegistrations.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun setupMonthFilter() {
        // Setup month dropdown
        val monthAdapter = ArrayAdapter(requireContext(), R.layout.dropdown_item, monthNames)
        (binding.monthDropdown as? MaterialAutoCompleteTextView)?.setAdapter(monthAdapter)

        // Set default selection to "All Months"
        binding.monthDropdown.setText(monthNames[0], false)
        selectedMonth = null

        // Handle month selection
        binding.monthDropdown.setOnItemClickListener { _, _, position, _ ->
            selectedMonth = if (position == MONTH_ALL) null else position
            Log.d("WTRegisterContent", "Selected month: ${monthNames[position]} (index: $selectedMonth)")
        }

        // Setup apply button
        binding.applyFilterButton.setOnClickListener {
            applyMonthFilter()
        }
    }

    private fun applyMonthFilter() {
        Log.d("WTRegisterContent", "Applying month filter: $selectedMonth")
        val searchView = searchMenuItem?.actionView as? SearchView
        val searchQuery = if (searchView?.isIconified == false && !searchView.query.isNullOrEmpty()) {
            searchView.query.toString()
        } else {
            ""
        }

        filterRegistrations(searchQuery)
    }

    private fun filterRegistrations(query: String) {
        viewModel.registrations.value?.let { registrations ->
            // First filter by search query
            val queryFilteredRegistrations = if (query.isBlank()) {
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

            // Then filter by month if a month is selected
            val monthFilteredRegistrations = when {
                selectedMonth == null -> {
                    // All months
                    queryFilteredRegistrations
                }
                selectedMonth == MONTH_TOTAL -> {
                    // Total (all registrations)
                    queryFilteredRegistrations
                }
                else -> {
                    // Specific month
                    queryFilteredRegistrations.filter { registration ->
                        registration.startDate?.let { startDate ->
                            val calendar = Calendar.getInstance()
                            calendar.time = startDate
                            // Calendar.MONTH is 0-based (0 = January), but our selectedMonth is 1-based (1 = January)
                            calendar.get(Calendar.MONTH) + 1 == selectedMonth
                        } ?: false
                    }
                }
            }

            val sortedRegistrations = monthFilteredRegistrations.sortedByDescending { it.startDate }
            adapter.submitList(sortedRegistrations)

            // Calculate and display total amount
            val totalAmount = sortedRegistrations.sumOf { it.amount }
            updateTotalAmountText(totalAmount)

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
            } else if (selectedMonth != null) {
                // Apply month filter if one is selected
                applyMonthFilter()
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
            val positiveButton = dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)
            positiveButton.setOnClickListener {
                // Validate form
                if (validateRegistrationForm(dialogBinding)) {
                    val student = selectedStudent!!
                    val startDate = dialogBinding.startDateInput.tag as Date
                    val endDate = dialogBinding.endDateInput.tag as Date
                    val amount = dialogBinding.amountInput.text.toString().toDoubleOrNull() ?: 0.0
                    val isPaid = dialogBinding.paidSwitch.isChecked

                    // Log the registration
                    Log.d("WTRegisterContent", "Creating registration for student: ${student.id} " +
                        "with startDate=${startDate}, endDate=${endDate}, " +
                        "amount=${amount}, isPaid=${isPaid}")

                    // Create a new registration record
                    viewModel.addRegistration(
                        studentId = student.id,
                        amount = amount,
                        startDate = startDate,
                        endDate = endDate,
                        attachmentUri = selectedAttachmentUri?.toString(),
                        notes = dialogBinding.notesEditText.text.toString().takeIf { it.isNotBlank() },
                        isPaid = isPaid
                    )

                    dialog.dismiss()
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

        // Add text change listener to start date to calculate end date when start date changes
        dialogBinding.startDateInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                calculateEndDateAfter8Lessons(dialogBinding)
            }
        })
    }

    private fun calculateEndDateAfter8Lessons(dialogBinding: DialogEditWtStudentBinding) {
        val startDate = dialogBinding.startDateInput.tag as? Date

        if (startDate != null) {
            try {
                // Create calendar from start date
                val startCalendar = Calendar.getInstance()
                startCalendar.time = startDate

                // Get parent fragment (which should be WTRegistryFragment)
                val parentFragment = parentFragment
                if (parentFragment is WTRegistryFragment) {
                    // Get lessons from registry fragment
                    val lessons = parentFragment.getLessons()

                    // Calculate end date after 8 lessons
                    val endDate = parentFragment.calculateEndDateAfterNLessons(
                        startCalendar,
                        lessons,
                        8 // Fixed at 8 lessons
                    )

                    // Set time to 22:00 (10pm)
                    val endCalendar = Calendar.getInstance()
                    endCalendar.time = endDate
                    endCalendar.set(Calendar.HOUR_OF_DAY, 22)
                    endCalendar.set(Calendar.MINUTE, 0)
                    endCalendar.set(Calendar.SECOND, 0)
                    endCalendar.set(Calendar.MILLISECOND, 0)

                    // Update the end date field
                    dialogBinding.endDateInput.setText(dateFormat.format(endCalendar.time))
                    dialogBinding.endDateInput.tag = endCalendar.time
                }
            } catch (e: Exception) {
                Log.e("WTRegisterContent", "Error calculating end date: ${e.message}")
            }
        }
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

            // If this is the end date, set the time to 22:00 (10pm)
            if (view.id == R.id.endDateInput) {
                selectedCalendar.set(Calendar.HOUR_OF_DAY, 22)
                selectedCalendar.set(Calendar.MINUTE, 0)
                selectedCalendar.set(Calendar.SECOND, 0)
                selectedCalendar.set(Calendar.MILLISECOND, 0)
            }

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
            val positiveButton = dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)
            positiveButton.setOnClickListener {
                // Validate form
                if (validateRegistrationForm(dialogBinding)) {
                    val startDate = dialogBinding.startDateInput.tag as Date
                    val endDate = dialogBinding.endDateInput.tag as Date
                    val amount = dialogBinding.amountInput.text.toString().toDoubleOrNull() ?: 0.0
                    val isPaid = dialogBinding.paidSwitch.isChecked

                    // Update registration object
                    val updatedRegistration = selectedRegistration!!.copy(
                        startDate = startDate,
                        endDate = endDate,
                        amount = amount,
                        isPaid = isPaid,
                        notes = dialogBinding.notesEditText.text.toString().takeIf { it.isNotBlank() },
                        attachmentUri = selectedAttachmentUri?.toString() ?: selectedRegistration!!.attachmentUri
                    )

                    // Log the update
                    Log.d("WTRegisterContent", "Updating registration: ${updatedRegistration.id} " +
                        "with startDate=${startDate}, endDate=${endDate}, " +
                        "amount=${amount}, isPaid=${isPaid}")

                    // Update via ViewModel
                    viewModel.updateRegistration(updatedRegistration)

                    dialog.dismiss()
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