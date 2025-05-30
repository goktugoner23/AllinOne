package com.example.allinone.ui.wt

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.os.Handler
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.allinone.R
import com.example.allinone.adapters.WTStudentAdapter
import com.example.allinone.data.WTStudent
import com.example.allinone.databinding.DialogAddStudentBinding
import com.example.allinone.databinding.FragmentWtStudentsBinding
import com.example.allinone.utils.TextStyleUtils
import com.example.allinone.viewmodels.WTRegisterViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.google.firebase.firestore.FirebaseFirestore
import androidx.appcompat.widget.SearchView
import android.transition.TransitionManager
import android.transition.Slide
import android.view.Gravity
import android.widget.EditText
import android.view.MotionEvent
import android.view.inputmethod.EditorInfo
import android.widget.ImageView
import android.app.Dialog

class WTStudentsFragment : Fragment() {
    private var _binding: FragmentWtStudentsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: WTRegisterViewModel by viewModels()
    private lateinit var adapter: WTStudentAdapter
    private var editingStudent: WTStudent? = null
    private var searchMenuItem: MenuItem? = null
    
    // Variables for handling profile pictures
    private var currentPhotoUri: Uri? = null
    private var currentDialogBinding: DialogAddStudentBinding? = null
    private var photoRemoved = false // Add a flag to track if photo was deliberately removed
    
    // Activity result launcher for selecting image from gallery
    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            // Save the selected URI
            currentPhotoUri = uri
            // Update the image view in the dialog
            currentDialogBinding?.profileImageView?.setImageURI(uri)
        }
    }
    
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentWtStudentsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupFab()
        setupMenu()
        observeStudents()
        observeNetworkStatus()
        setupSwipeRefresh()
    }
    
    private fun setupMenu() {
        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.search_students, menu)
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
                        
                        // Reset the student list
                        resetStudentList()
                        true
                    }
                    
                    // Set up query listener
                    it.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                        override fun onQueryTextSubmit(query: String?): Boolean {
                            query?.let { searchQuery ->
                                filterStudents(searchQuery)
                            }
                            
                            // Hide keyboard
                            val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                            imm.hideSoftInputFromWindow(it.windowToken, 0)
                            
                            return true
                        }
                        
                        override fun onQueryTextChange(newText: String?): Boolean {
                            newText?.let { searchQuery ->
                                filterStudents(searchQuery)
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
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun setupRecyclerView() {
        adapter = WTStudentAdapter(
            onItemClick = { student -> showStudentDetails(student) },
            onLongPress = { student, view -> showContextMenu(student, view) },
            isStudentRegistered = { studentId ->
                viewModel.isStudentCurrentlyRegistered(studentId)
            }
        )
        
        binding.studentsRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@WTStudentsFragment.adapter
        }
    }

    private fun setupFab() {
        binding.addStudentFab.setOnClickListener {
            showAddStudentDialog()
        }
    }

    private fun resetStudentList() {
        viewModel.students.value?.let { students ->
            val uniqueStudents = students.distinctBy { it.id }
            val sortedStudents = uniqueStudents.sortedBy { it.name.lowercase(Locale.getDefault()) }
            adapter.submitList(sortedStudents)
            
            // Show or hide empty state
            binding.emptyState.visibility = if (sortedStudents.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun filterStudents(query: String) {
        viewModel.students.value?.let { students ->
            // Deduplicate students by ID
            val uniqueStudents = students.distinctBy { it.id }
            
            // Filter and sort students
            val filteredStudents = if (query.isBlank()) {
                uniqueStudents
            } else {
                uniqueStudents.filter { 
                    it.name.contains(query, ignoreCase = true) || 
                    it.phoneNumber?.contains(query, ignoreCase = true) == true ||
                    it.email?.contains(query, ignoreCase = true) == true
                }
            }
            
            // Sort students alphabetically by name
            val sortedStudents = filteredStudents.sortedBy { it.name.lowercase(Locale.getDefault()) }
            
            // Update adapter with filtered list
            adapter.submitList(sortedStudents)
            
            // Show or hide empty state
            binding.emptyState.visibility = if (sortedStudents.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun observeStudents() {
        viewModel.students.observe(viewLifecycleOwner) { students ->
            // Check for students with old image paths and update them
            students.forEach { student ->
                if (student.photoUri != null && student.photoUri.contains("users/anonymous/profile_pictures")) {
                    // Fix the photoUri to point to the new location
                    val updatedUri = student.photoUri.replace("users/anonymous/profile_pictures", "profile_pictures")
                    val updatedStudent = student.copy(photoUri = updatedUri)
                    viewModel.updateStudent(updatedStudent)
                    Log.d("WTStudentsFragment", "Updated student photo URI from ${student.photoUri} to $updatedUri")
                }
            }
            
            // Apply current search filter if search is active
            val searchView = searchMenuItem?.actionView as? SearchView
            if (searchView?.isIconified == false && !searchView.query.isNullOrEmpty()) {
                filterStudents(searchView.query.toString())
            } else {
                // Deduplicate students by ID before submitting to adapter
                val uniqueStudents = students.distinctBy { it.id }
                
                // Sort students alphabetically by name
                val sortedStudents = uniqueStudents.sortedBy { it.name.lowercase(Locale.getDefault()) }
                
                adapter.submitList(sortedStudents)
                
                // Show or hide empty state
                binding.emptyState.visibility = if (sortedStudents.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun showAddStudentDialog() {
        editingStudent = null
        showStudentDialog(null)
    }

    private fun showEditStudentDialog(student: WTStudent) {
        // Store the student being edited
        editingStudent = student
        // Use the common showStudentDialog method to edit a student
        showStudentDialog(student)
    }

    private fun showStudentDialog(student: WTStudent?) {
        val isEdit = student != null
        val dialogTitle = if (isEdit) R.string.edit_student else R.string.add_student
        
        // Reset the photo removed flag
        photoRemoved = false
        
        val dialogInflater = LayoutInflater.from(requireContext())
        val dialogBinding = DialogAddStudentBinding.inflate(dialogInflater, null, false)
        currentDialogBinding = dialogBinding
        
        // Set existing values if editing
        if (isEdit) {
            dialogBinding.apply {
                nameEditText.setText(student!!.name)
                phoneEditText.setText(student.phoneNumber)
                emailEditText.setText(student.email ?: "")
                instagramEditText.setText(student.instagram ?: "")
                activeSwitch.isChecked = student.isActive
                
                // If student has a photo URI, set it
                student.photoUri?.let { uri ->
                    try {
                        Log.d("WTStudentsFragment", "Loading profile image from URI: $uri")
                        if (uri.startsWith("https://")) {
                            com.bumptech.glide.Glide.with(requireContext())
                                .load(uri)
                                .placeholder(R.drawable.default_profile)
                                .error(R.drawable.default_profile)
                                .into(profileImageView)
                        } else {
                            profileImageView.setImageURI(Uri.parse(uri))
                        }
                        currentPhotoUri = Uri.parse(uri)
                    } catch (e: Exception) {
                        Log.e("WTStudentsFragment", "Error loading profile image: ${e.message}")
                        profileImageView.setImageResource(R.drawable.default_profile)
                    }
                }
            }
        }
        
        // Setup click listener for the profile image view
        dialogBinding.profileImageView.setOnClickListener {
            checkPermissionAndOpenGallery()
        }
        
        // Setup long click listener for the profile image view
        dialogBinding.profileImageView.setOnLongClickListener {
            if (currentPhotoUri != null) {
                showPhotoOptions()
                true
            } else {
                false
            }
        }
        
        // Create dialog without buttons (we'll use our own buttons)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(dialogTitle)
            .setView(dialogBinding.root)
            .create()
            
        // Setup save button listener
        dialogBinding.saveButton.setOnClickListener {
            if (validateStudentForm(dialogBinding)) {
                saveStudent()
                dialog.dismiss()
                currentDialogBinding = null
            }
            // If validation fails, dialog stays open with errors shown
        }
        
        // Setup cancel button listener
        dialogBinding.cancelButton.setOnClickListener {
            dialog.dismiss()
            currentDialogBinding = null
        }
            
        // Configure dialog window for better keyboard handling
        dialog.window?.apply {
            // Set soft input mode with backward compatibility
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                // For Android 11+, use the new WindowInsets API
                setDecorFitsSystemWindows(false)
            } else {
                // For older Android versions, use the deprecated approach
                @Suppress("DEPRECATION")
                setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            }
            
            // Set window size to match most of the screen space
            setLayout(
                android.view.WindowManager.LayoutParams.MATCH_PARENT,
                android.view.WindowManager.LayoutParams.WRAP_CONTENT
            )
            
            // Make dialog background properly rounded with padding
            setBackgroundDrawableResource(R.drawable.dialog_rounded_bg)
        }
        
        dialog.show()
    }
    
    private fun validateStudentForm(dialogBinding: DialogAddStudentBinding): Boolean {
        var isValid = true
        
        // Clear previous errors
        dialogBinding.nameInputLayout.error = null
        dialogBinding.phoneInputLayout.error = null
        
        // Validate name
        val name = dialogBinding.nameEditText.text.toString().trim()
        if (name.isEmpty()) {
            dialogBinding.nameInputLayout.error = getString(R.string.field_required)
            isValid = false
        }
        
        // Validate phone
        val phone = dialogBinding.phoneEditText.text.toString().trim()
        if (phone.isEmpty()) {
            dialogBinding.phoneInputLayout.error = getString(R.string.field_required)
            isValid = false
        } else {
            // Basic validation for Turkish phone numbers
            val digitsOnly = phone.replace(Regex("[^0-9]"), "")
            // Check if it's a valid Turkish mobile number (10 digits after removing 0 prefix)
            if (!(digitsOnly.length == 10 || (digitsOnly.startsWith("0") && digitsOnly.length == 11))) {
                dialogBinding.phoneInputLayout.error = "Enter a valid Turkish mobile number"
                isValid = false
            }
        }
        
        // Check for duplicates if not editing
        if (isValid && editingStudent == null) {
            // For phone number comparison, format both numbers
            val formattedPhone = formatPhoneNumber(phone)
            
            val existingStudentWithName = viewModel.students.value?.find { it.name == name }
            if (existingStudentWithName != null) {
                dialogBinding.nameInputLayout.error = "A student with this name already exists"
                isValid = false
            }
            
            val existingStudentWithPhone = viewModel.students.value?.find { 
                formatPhoneNumber(it.phoneNumber) == formattedPhone 
            }
            if (existingStudentWithPhone != null) {
                dialogBinding.phoneInputLayout.error = "A student with this phone number already exists"
                isValid = false
            }
        }
        // Check for duplicates if editing but name or phone number has changed
        else if (isValid && editingStudent != null) {
            // Create a local val to avoid smart cast issues
            val currentEditingStudent = editingStudent
            
            // Only check for duplicate name if name has changed
            if (currentEditingStudent?.name != name) {
                val existingStudentWithName = viewModel.students.value?.find { 
                    it.name == name && it.id != currentEditingStudent?.id 
                }
                if (existingStudentWithName != null) {
                    dialogBinding.nameInputLayout.error = "A student with this name already exists"
                    isValid = false
                }
            }
            
            // Only check for duplicate phone if phone has changed
            // Format both numbers for comparison
            val formattedPhone = formatPhoneNumber(phone)
            val formattedEditingPhone = formatPhoneNumber(currentEditingStudent?.phoneNumber ?: "")
            
            if (formattedEditingPhone != formattedPhone) {
                val existingStudentWithPhone = viewModel.students.value?.find { 
                    formatPhoneNumber(it.phoneNumber) == formattedPhone && it.id != currentEditingStudent?.id 
                }
                if (existingStudentWithPhone != null) {
                    dialogBinding.phoneInputLayout.error = "A student with this phone number already exists"
                    isValid = false
                }
            }
        }
        
        return isValid
    }
    
    // Check for permission and open gallery
    private fun checkPermissionAndOpenGallery() {
        // Check if we already have the permission (should be requested at app startup)
        val hasStoragePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.READ_MEDIA_IMAGES
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
        
        if (hasStoragePermission) {
            // Permission already granted, open gallery
            openGallery()
        } else {
            // For users who denied permission at app startup, show a message and let them know
            // they can enable it in settings
            Toast.makeText(
                requireContext(),
                "Storage permission is required to select images. Please enable it in app settings.",
                Toast.LENGTH_LONG
            ).show()
        }
    }
    
    // Open gallery to select an image
    private fun openGallery() {
        galleryLauncher.launch("image/*")
    }
    
    // Show options when user long presses on a photo
    private fun showPhotoOptions() {
        val options = arrayOf("View Photo", "Change Photo", "Remove Photo")
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Photo Options")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> viewFullPhoto()
                    1 -> checkPermissionAndOpenGallery()
                    2 -> removePhoto()
                }
            }
            .show()
    }
    
    // View the photo in a fullscreen dialog with slider support
    private fun viewFullPhoto() {
        currentPhotoUri?.let { uri ->
            // Just delegate to our existing full screen image method
            showFullScreenImage(uri.toString())
        }
    }
    
    // Show the photo in a fullscreen dialog with slider support
    private fun showFullScreenImage(photoUri: String?) {
        if (photoUri == null) return
        
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_fullscreen_image, null)
        val dialog = Dialog(requireContext(), android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.setContentView(dialogView)
        
        // Create a list with just this one image
        val images = listOf(photoUri)
        
        // Setup ViewPager
        val viewPager = dialogView.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.fullscreenViewPager)
        val imageCounter = dialogView.findViewById<TextView>(R.id.imageCounterText)
        
        // Hide the counter since we only have one image
        imageCounter.visibility = View.GONE
        
        // Create and set adapter
        val adapter = com.example.allinone.adapters.FullscreenImageAdapter(requireContext(), images)
        viewPager.adapter = adapter
        
        dialog.show()
        
        // Close on tap
        dialogView.setOnClickListener {
            dialog.dismiss()
        }
    }
    
    // Remove the current photo
    private fun removePhoto() {
        currentPhotoUri = null
        photoRemoved = true
        
        // Update UI to show default image
        currentDialogBinding?.profileImageView?.setImageResource(R.drawable.default_profile)
        
        // If this is an existing student, update the database record immediately
        editingStudent?.id?.let { studentId ->
            // Only update if we're editing an existing student
            if (studentId > 0) {
                val db = FirebaseFirestore.getInstance()
                val studentRef = db.collection("wtStudents").document(studentId.toString())
                
                // Update photoUri field to null in the database
                studentRef.update("photoUri", null)
                    .addOnSuccessListener {
                        Log.d("WTStudentsFragment", "Photo URI successfully removed from database")
                        Toast.makeText(context, "Photo removed", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        Log.e("WTStudentsFragment", "Error removing photo URI from database", e)
                        Toast.makeText(context, "Failed to remove photo from database", Toast.LENGTH_SHORT).show()
                    }
            }
        }
    }
    
    private fun saveStudent() {
        val dialogBinding = currentDialogBinding ?: return
        
        val name = dialogBinding.nameEditText.text.toString().trim()
        val rawPhone = dialogBinding.phoneEditText.text.toString().trim()
        val phone = formatPhoneNumber(rawPhone)
        val email = dialogBinding.emailEditText.text.toString().trim()
        val instagram = dialogBinding.instagramEditText.text.toString().trim()
        val isActive = dialogBinding.activeSwitch.isChecked
        
        if (name.isBlank()) {
            showSnackbar("Name is required")
            return
        }
        
        // Show loading indicator
        val loadingDialog = MaterialAlertDialogBuilder(requireContext())
            .setView(R.layout.dialog_loading)
            .setCancelable(false)
            .create()
        loadingDialog.show()
        
        // Safely convert the existing photo URI string to a Uri object
        val existingPhotoUri = if (!editingStudent?.photoUri.isNullOrEmpty()) {
            try {
                Uri.parse(editingStudent?.photoUri)
            } catch (e: Exception) {
                Log.e("WTStudentsFragment", "Error parsing existing photo URI: ${e.message}")
                null
            }
        } else {
            null
        }
        
        // Different logic for new vs existing students to handle photo uploads properly
        if (editingStudent != null) {
            // EXISTING STUDENT - We have an ID to use for the photo folder
            handleExistingStudentUpdate(
                editingStudent!!,
                name, phone, email, instagram, isActive,
                currentPhotoUri, existingPhotoUri, photoRemoved,
                loadingDialog
            )
        } else {
            // NEW STUDENT - We need to create the student first, then handle photo upload
            handleNewStudentCreation(
                name, phone, email, instagram, isActive,
                currentPhotoUri, photoRemoved,
                loadingDialog
            )
        }
    }
    
    /**
     * Handle updating an existing student, including photo upload if needed
     */
    private fun handleExistingStudentUpdate(
        student: WTStudent,
        name: String,
        phone: String,
        email: String,
        instagram: String,
        isActive: Boolean,
        newPhotoUri: Uri?,
        existingPhotoUri: Uri?,
        photoRemoved: Boolean,
        loadingDialog: androidx.appcompat.app.AlertDialog
    ) {
        // Define a holder class to capture photo URI from callback
        class PhotoResultHolder {
            var photoUri: String? = null
        }
        val resultHolder = PhotoResultHolder()
        
        // Define function to update the student with new information
        val updateStudentWithPhoto = { 
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    // Log what's happening with the photo
                    Log.d("WTStudentsFragment", "Updating student photo - removed: $photoRemoved, newUri: $newPhotoUri, existing: $existingPhotoUri")
                    
                    // Create updated student object
                    val updatedStudent = student.copy(
                        name = name,
                        phoneNumber = phone,
                        email = email,
                        instagram = instagram,
                        isActive = isActive,
                        notes = student.notes,
                        photoUri = resultHolder.photoUri
                    )
                    
                    // Update student in database
                    viewModel.updateStudent(updatedStudent)
                    
                    withContext(Dispatchers.Main) {
                        loadingDialog.dismiss()
                        resetState()
                        
                        // If the photo was removed, clear any image caches
                        if (photoRemoved && existingPhotoUri != null) {
                            try {
                                // Clear any cached images for this student
                                com.bumptech.glide.Glide.get(requireContext()).clearMemory()
                                Thread {
                                    try {
                                        com.bumptech.glide.Glide.get(requireContext()).clearDiskCache()
                                    } catch (e: Exception) {
                                        Log.e("WTStudentsFragment", "Error clearing disk cache: ${e.message}")
                                    }
                                }.start()
                            } catch (e: Exception) {
                                Log.e("WTStudentsFragment", "Error clearing image cache: ${e.message}")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("WTStudentsFragment", "Error updating student: ${e.message}", e)
                    withContext(Dispatchers.Main) {
                        loadingDialog.dismiss()
                        showSnackbar("Error updating student: ${e.message}")
                    }
                }
            }
        }
        
        // Check if we need to upload a new photo
        if (newPhotoUri != null && (existingPhotoUri == null || newPhotoUri != existingPhotoUri)) {
            // Upload new photo with student ID as subfolder
            viewModel.uploadProfilePicture(
                uri = newPhotoUri,
                studentId = student.id,  // Use existing student ID for folder
                onComplete = { cloudUri ->
                    resultHolder.photoUri = cloudUri
                    updateStudentWithPhoto()
                }
            )
        } else if (photoRemoved) {
            // Photo was removed - update with null URI
            resultHolder.photoUri = null
            updateStudentWithPhoto()
        } else {
            // No photo change - keep existing URI
            resultHolder.photoUri = student.photoUri
            updateStudentWithPhoto()
        }
    }
    
    // Helper function to reset state
    private fun resetState() {
        editingStudent = null
        currentPhotoUri = null
        photoRemoved = false
    }
    
    /**
     * Format Turkish phone number to international format
     * Example: "05306778765" -> "+90 530 677 8765"
     */
    private fun formatPhoneNumber(phone: String?): String {
        // If null or empty, return empty string
        if (phone.isNullOrEmpty()) return ""
        
        // Clean the input by removing any non-digit characters
        val digitsOnly = phone.replace(Regex("[^0-9]"), "")
        
        // If empty after cleaning, return empty string
        if (digitsOnly.isEmpty()) return ""
        
        // Remove leading 0 if present and add country code
        val withCountryCode = if (digitsOnly.startsWith("0")) {
            "+90${digitsOnly.substring(1)}"
        } else if (!digitsOnly.startsWith("+90") && !digitsOnly.startsWith("90")) {
            "+90$digitsOnly"
        } else {
            if (digitsOnly.startsWith("90")) "+$digitsOnly" else digitsOnly
        }
        
        // If the number isn't the right length, just return with country code but no formatting
        if (withCountryCode.length != 13) { // +90 + 10 digits
            return withCountryCode
        }
        
        // Format with spaces
        return try {
            val formatted = StringBuilder()
            formatted.append(withCountryCode.substring(0, 3)) // +90
            formatted.append(" ")
            formatted.append(withCountryCode.substring(3, 6)) // Area code
            formatted.append(" ")
            formatted.append(withCountryCode.substring(6, 9)) // First part
            formatted.append(" ")
            formatted.append(withCountryCode.substring(9)) // Last part
            formatted.toString()
        } catch (e: Exception) {
            // If any error in formatting, return with country code
            Log.e("WTStudentsFragment", "Error formatting phone number: $phone", e)
            withCountryCode
        }
    }
    
    /**
     * Handle creating a new student, then uploading photo if needed
     */
    private fun handleNewStudentCreation(
        name: String,
        phone: String,
        email: String,
        instagram: String,
        isActive: Boolean,
        newPhotoUri: Uri?,
        photoRemoved: Boolean,
        loadingDialog: androidx.appcompat.app.AlertDialog
    ) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                Log.d("WTStudentsFragment", "Creating new student with photo: ${newPhotoUri != null}")
                
                // First create the student without a photo to get an ID
                val studentId = viewModel.addStudentAndGetId(
                    name = name,
                    phoneNumber = phone,
                    email = email,
                    instagram = instagram,
                    isActive = isActive
                )
                
                Log.d("WTStudentsFragment", "Student created with ID: $studentId, has photo: ${newPhotoUri != null}")
                
                // If we got a valid ID and have a photo to upload
                if (studentId != null && studentId > 0 && newPhotoUri != null && !photoRemoved) {
                    // Now upload the photo with the real student ID
                    withContext(Dispatchers.Main) {
                        viewModel.uploadProfilePicture(
                            uri = newPhotoUri,
                            studentId = studentId,
                            onComplete = { cloudUri ->
                                Log.d("WTStudentsFragment", "Photo uploaded successfully: $cloudUri")
                                
                                // Update the student with the photo URI
                                if (cloudUri != null) {
                                    lifecycleScope.launch(Dispatchers.IO) {
                                        try {
                                            Log.d("WTStudentsFragment", "Updating student $studentId with photo URI: $cloudUri")
                                            viewModel.updateStudentPhoto(studentId, cloudUri)
                                            Log.d("WTStudentsFragment", "Student photo URI updated successfully")
                                        } catch (e: Exception) {
                                            Log.e("WTStudentsFragment", "Error updating student photo: ${e.message}", e)
                                        } finally {
                                            withContext(Dispatchers.Main) {
                                                loadingDialog.dismiss()
                                                resetState()
                                            }
                                        }
                                    }
                                } else {
                                    Log.e("WTStudentsFragment", "Photo upload returned null URI")
                                    loadingDialog.dismiss()
                                    resetState()
                                }
                            }
                        )
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        loadingDialog.dismiss()
                        resetState()
                    }
                }
            } catch (e: Exception) {
                Log.e("WTStudentsFragment", "Error creating student: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    loadingDialog.dismiss()
                    showSnackbar("Error creating student: ${e.message}")
                }
            }
        }
    }

    private fun observeNetworkStatus() {
        // Handler for delayed operations
        val handler = Handler(android.os.Looper.getMainLooper())
        var isOfflineViewShown = false
        
        viewModel.isNetworkAvailable.observe(viewLifecycleOwner) { isAvailable ->
            val offlineView = binding.offlineStatusView.root
            
            if (!isAvailable) {
                // When offline, show the banner
                offlineView.visibility = View.VISIBLE
                isOfflineViewShown = true
            } else if (isOfflineViewShown) {
                // When online and banner was shown, hide it after 2 seconds
                handler.postDelayed({
                    // Check if fragment is still attached before changing visibility
                    if (isAdded && _binding != null) {
                        offlineView.visibility = View.GONE
                        isOfflineViewShown = false
                    }
                }, 2000)
            }
        }
    }

    private fun showStudentDetails(student: WTStudent) {
        // Create a custom view for the dialog
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_student_details, null)
        
        // Set up the profile image
        val profileImageView = dialogView.findViewById<com.google.android.material.imageview.ShapeableImageView>(R.id.profileImageView)
        if (!student.photoUri.isNullOrEmpty()) {
            Log.d("WTStudentsFragment", "Loading profile image from URI: ${student.photoUri}")
            if (student.photoUri.startsWith("https://")) {
                com.bumptech.glide.Glide.with(requireContext())
                    .load(student.photoUri)
                    .placeholder(R.drawable.default_profile)
                    .error(R.drawable.default_profile)
                    .into(profileImageView)
            } else {
                try {
                    // Safely parse URI
                    val photoUri = Uri.parse(student.photoUri)
                    profileImageView.setImageURI(photoUri)
                } catch (e: Exception) {
                    Log.e("WTStudentsFragment", "Error loading local image: ${e.message}")
                    profileImageView.setImageResource(R.drawable.default_profile)
                }
            }
            
            // Set click listener to show full screen image
            profileImageView.setOnClickListener {
                showFullScreenImage(student.photoUri)
            }
        } else {
            Log.d("WTStudentsFragment", "No profile image URI available")
            profileImageView.setImageResource(R.drawable.default_profile)
            profileImageView.setOnClickListener(null)
        }
        
        // Set up the name
        val nameTextView = dialogView.findViewById<TextView>(R.id.nameTextView)
        nameTextView.text = student.name
        
        // Set up the details text with bold labels
        val detailsTextView = dialogView.findViewById<TextView>(R.id.detailsTextView)
        val details = SpannableStringBuilder().apply {
            // Phone - ensure it's properly formatted
            append(TextStyleUtils.createBoldSpan(requireContext(), "Phone: "))
            // If the phone number is not formatted, format it now for display
            val displayPhone = if (!student.phoneNumber.isNullOrEmpty() && student.phoneNumber.contains(" ")) {
                student.phoneNumber // Already formatted
            } else {
                formatPhoneNumber(student.phoneNumber)
            }
            append("$displayPhone\n")
            
            // Instagram (if available)
            if (!student.instagram.isNullOrEmpty()) {
                append(TextStyleUtils.createBoldSpan(requireContext(), "Instagram: "))
                append("${student.instagram}\n")
            }
            
            // Status
            append(TextStyleUtils.createBoldSpan(requireContext(), "Status: "))
            append("${if (student.isActive) "Active" else "Inactive"}\n")
            
            // Registration
            append(TextStyleUtils.createBoldSpan(requireContext(), "Registration: "))
            append(if (viewModel.isStudentCurrentlyRegistered(student.id)) 
                "Currently registered" else "Not registered")
        }
        detailsTextView.text = details
        
        // Set up call button
        val callButton = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.callButton)
        callButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:${student.phoneNumber}")
            }
            try {
                startActivity(intent)
            } catch (e: Exception) {
                showSnackbar("Could not open phone app")
            }
        }
        
        // Set up WhatsApp button
        val whatsappButton = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.whatsappButton)
        whatsappButton.setOnClickListener {
            // Extract just the digits from the formatted phone number
            val phoneNumber = if (!student.phoneNumber.isNullOrEmpty()) {
                // Remove all non-digit characters
                val digitsOnly = student.phoneNumber.replace(Regex("[^0-9+]"), "")
                
                // Handle different formats
                when {
                    // If it already has the international format with +90
                    digitsOnly.startsWith("+90") -> digitsOnly.substring(1) // Remove the + but keep the 90
                    // If it has 90 prefix without +
                    digitsOnly.startsWith("90") && digitsOnly.length >= 12 -> digitsOnly
                    // If it's a 10-digit number without country code
                    digitsOnly.length == 10 -> "90$digitsOnly"
                    // If it starts with 0, remove it and add 90
                    digitsOnly.startsWith("0") -> "90${digitsOnly.substring(1)}"
                    // Otherwise, just add 90 prefix
                    else -> "90$digitsOnly"
                }
            } else {
                return@setOnClickListener
            }
            
            Log.d("WTStudentsFragment", "Opening WhatsApp with phone number: $phoneNumber")
            
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://api.whatsapp.com/send?phone=$phoneNumber")
            }
            try {
                startActivity(intent)
            } catch (e: Exception) {
                showSnackbar("Could not open WhatsApp")
            }
        }
        
        // Set up Instagram button
        val instagramButton = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.instagramButton)
        if (!student.instagram.isNullOrEmpty()) {
            instagramButton.visibility = View.VISIBLE
            instagramButton.setOnClickListener {
                val instagramUsername = student.instagram.trim().replace("@", "")
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("https://instagram.com/$instagramUsername")
                }
                try {
                    startActivity(intent)
                } catch (e: Exception) {
                    // Try opening in browser if Instagram app is not installed
                    val browserIntent = Intent(Intent.ACTION_VIEW).apply {
                        data = Uri.parse("https://www.instagram.com/$instagramUsername")
                    }
                    try {
                        startActivity(browserIntent)
                    } catch (e: Exception) {
                        showSnackbar("Could not open Instagram")
                    }
                }
            }
        } else {
            instagramButton.visibility = View.GONE
        }
        
        MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .show()
    }
    
    private fun showContextMenu(student: WTStudent, view: View) {
        val popup = android.widget.PopupMenu(requireContext(), view)
        val inflater = popup.menuInflater
        inflater.inflate(R.menu.wt_student_context_menu, popup.menu)
        
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_edit -> {
                    showEditStudentDialog(student)
                    true
                }
                R.id.action_delete -> {
                    showDeleteConfirmation(student)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }
    
    private fun showDeleteConfirmation(student: WTStudent) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.delete_student)
            .setMessage(getString(R.string.delete_student_confirmation, student.name))
            .setPositiveButton(R.string.delete) { _, _ ->
                deleteStudent(student)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
    
    private fun deleteStudent(student: WTStudent) {
        // Check if student has active registrations
        if (viewModel.isStudentCurrentlyRegistered(student.id)) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.cannot_delete)
                .setMessage(R.string.student_has_active_registrations)
                .setPositiveButton(R.string.ok, null)
                .show()
            return
        }
        
        // Delete the student
        viewModel.deleteStudent(student.id)
        
        // Show success message
        com.google.android.material.snackbar.Snackbar.make(
            binding.root,
            getString(R.string.student_deleted),
            com.google.android.material.snackbar.Snackbar.LENGTH_LONG
        ).show()
    }

    private fun showSnackbar(message: String) {
        com.google.android.material.snackbar.Snackbar.make(
            binding.root,
            message,
            com.google.android.material.snackbar.Snackbar.LENGTH_LONG
        ).show()
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        currentDialogBinding = null
    }
} 