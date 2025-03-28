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
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
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

class WTStudentsFragment : Fragment() {
    private var _binding: FragmentWtStudentsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: WTRegisterViewModel by viewModels()
    private lateinit var adapter: WTStudentAdapter
    private var editingStudent: WTStudent? = null
    
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
        observeStudents()
        observeNetworkStatus()
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
            
            // Deduplicate students by ID before submitting to adapter
            val uniqueStudents = students.distinctBy { it.id }
            adapter.submitList(uniqueStudents)
            
            // Show or hide empty state
            binding.emptyState.visibility = if (uniqueStudents.isEmpty()) View.VISIBLE else View.GONE
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
        }
        
        // Check for duplicates if not editing
        if (isValid && editingStudent == null) {
            val existingStudentWithName = viewModel.students.value?.find { it.name == name }
            if (existingStudentWithName != null) {
                dialogBinding.nameInputLayout.error = "A student with this name already exists"
                isValid = false
            }
            
            val existingStudentWithPhone = viewModel.students.value?.find { it.phoneNumber == phone }
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
            if (currentEditingStudent?.phoneNumber != phone) {
                val existingStudentWithPhone = viewModel.students.value?.find { 
                    it.phoneNumber == phone && it.id != currentEditingStudent?.id 
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
    
    // View the photo in a fullscreen dialog
    private fun viewFullPhoto() {
        currentPhotoUri?.let { uri ->
            val dialog = MaterialAlertDialogBuilder(requireContext())
                .setView(R.layout.dialog_fullscreen_image)
                .create()
            
            dialog.show()
            
            val imageView = dialog.findViewById<com.google.android.material.imageview.ShapeableImageView>(R.id.fullscreenImageView)
            try {
                Log.d("WTStudentsFragment", "Loading fullscreen image from URI: $uri")
                if (uri.toString().startsWith("https://")) {
                    com.bumptech.glide.Glide.with(requireContext())
                        .load(uri)
                        .placeholder(R.drawable.default_profile)
                        .error(R.drawable.default_profile)
                        .into(imageView!!)
                } else {
                    imageView?.setImageURI(uri)
                }
            } catch (e: Exception) {
                Log.e("WTStudentsFragment", "Error loading fullscreen image: ${e.message}")
                Toast.makeText(requireContext(), "Failed to load image", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            
            // Close on tap
            imageView?.setOnClickListener {
                dialog.dismiss()
            }
        }
    }
    
    // Remove the current photo
    private fun removePhoto() {
        currentPhotoUri = null
        photoRemoved = true // Set the flag when photo is deliberately removed
        currentDialogBinding?.profileImageView?.setImageResource(R.drawable.default_profile)
    }
    
    private fun saveStudent() {
        val dialogBinding = currentDialogBinding ?: return
        
        val name = dialogBinding.nameEditText.text.toString()
        val phone = dialogBinding.phoneEditText.text.toString()
        val email = dialogBinding.emailEditText.text.toString()
        val instagram = dialogBinding.instagramEditText.text.toString()
        val isActive = dialogBinding.activeSwitch.isChecked
        
        if (name.isBlank()) {
            showSnackbar("Name is required")
            return
        }
        
        // Handle photo upload
        var finalPhotoUri: String?
        if (currentPhotoUri != null && (currentPhotoUri != Uri.parse(editingStudent?.photoUri))) {
            // Only upload if the photo URI is different from the existing one
            // Upload the photo to Firebase Storage
            viewModel.uploadProfilePicture(currentPhotoUri!!) { cloudUri ->
                finalPhotoUri = cloudUri
                saveStudentToDatabase(name, phone, email, instagram, isActive, finalPhotoUri)
            }
        } else if (!photoRemoved) {
            // If no new photo and not removed, keep the existing one
            finalPhotoUri = editingStudent?.photoUri
            saveStudentToDatabase(name, phone, email, instagram, isActive, finalPhotoUri)
        } else {
            // Photo was removed
            saveStudentToDatabase(name, phone, email, instagram, isActive, null)
        }
    }
    
    private fun saveStudentToDatabase(
        name: String,
        phone: String,
        email: String,
        instagram: String,
        isActive: Boolean,
        photoUri: String?
    ) {
        val existingStudent = editingStudent
        
        if (existingStudent != null) {
            // Update existing student
            val updatedStudent = existingStudent.copy(
                name = name,
                phoneNumber = phone,
                email = email,
                instagram = instagram,
                isActive = isActive,
                notes = existingStudent.notes,
                photoUri = photoUri
            )
            
            viewModel.updateStudent(updatedStudent)
        } else {
            // Create new student
            viewModel.addStudent(
                name = name,
                phoneNumber = phone,
                email = email,
                instagram = instagram,
                isActive = isActive,
                photoUri = photoUri
            )
        }
        
        // Reset temporary fields
        editingStudent = null
        currentPhotoUri = null
        photoRemoved = false
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
                    profileImageView.setImageURI(Uri.parse(student.photoUri))
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
            // Phone
            append(TextStyleUtils.createBoldSpan(requireContext(), "Phone: "))
            append("${student.phoneNumber}\n")
            
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
            val phoneNumber = student.phoneNumber?.let { number ->
                // Remove all non-digit characters and ensure it starts with 90
                val digitsOnly = number.replace(Regex("[^0-9]"), "")
                if (digitsOnly.startsWith("0")) {
                    "90${digitsOnly.substring(1)}"
                } else {
                    digitsOnly
                }
            } ?: return@setOnClickListener
            
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

    private fun showFullScreenImage(imageUri: String?) {
        imageUri?.let { uri ->
            val dialog = MaterialAlertDialogBuilder(requireContext())
                .setView(R.layout.dialog_fullscreen_image)
                .create()
            
            dialog.show()
            
            val imageView = dialog.findViewById<com.google.android.material.imageview.ShapeableImageView>(R.id.fullscreenImageView)
            try {
                Log.d("WTStudentsFragment", "Loading fullscreen image from URI: $uri")
                if (uri.startsWith("https://")) {
                    com.bumptech.glide.Glide.with(requireContext())
                        .load(uri)
                        .placeholder(R.drawable.default_profile)
                        .error(R.drawable.default_profile)
                        .into(imageView!!)
                } else {
                    imageView?.setImageURI(Uri.parse(uri))
                }
            } catch (e: Exception) {
                Log.e("WTStudentsFragment", "Error loading fullscreen image: ${e.message}")
                Toast.makeText(requireContext(), "Failed to load image", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            
            // Close on tap
            imageView?.setOnClickListener {
                dialog.dismiss()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        currentDialogBinding = null
    }
} 