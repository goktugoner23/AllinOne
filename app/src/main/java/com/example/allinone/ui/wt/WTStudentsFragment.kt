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
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
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
                        profileImageView.setImageURI(Uri.parse(uri))
                        currentPhotoUri = Uri.parse(uri)
                    } catch (e: Exception) {
                        // If there's an error loading the image, log it but continue
                        e.printStackTrace()
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
                saveStudent(dialogBinding)
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
            // Set soft input mode to adjust nothing and let scrollview handle scrolling
            setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
            
            // Set window size to match available screen space
            setLayout(
                android.view.WindowManager.LayoutParams.MATCH_PARENT,
                android.view.WindowManager.LayoutParams.WRAP_CONTENT
            )
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
                imageView?.setImageURI(uri)
            } catch (e: Exception) {
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
    
    private fun saveStudent(dialogBinding: DialogAddStudentBinding) {
        val name = dialogBinding.nameEditText.text.toString().trim()
        val phone = dialogBinding.phoneEditText.text.toString().trim()
        val email = dialogBinding.emailEditText.text.toString().trim().let { 
            if (it.isEmpty()) null else it 
        }
        val instagram = dialogBinding.instagramEditText.text.toString().trim().let { 
            if (it.isEmpty()) null else it 
        }
        val isActive = dialogBinding.activeSwitch.isChecked
        
        // First, try to find if this student already exists in our records
        val existingStudentByEdit = editingStudent
        val existingStudentByName = viewModel.students.value?.find { 
            it.name.equals(name, ignoreCase = true) || it.phoneNumber == phone 
        }
        
        // Determine which existing student to use (prefer the one being edited)
        val existingStudent = existingStudentByEdit ?: existingStudentByName
        
        // Handle photo URI based on whether it was updated or removed
        var finalPhotoUri: String? = null
        
        if (photoRemoved) {
            // If photo was deliberately removed, leave URI as null
            finalPhotoUri = null
            Log.d("WTStudentsFragment", "Photo was deliberately removed")
        } else if (currentPhotoUri != null) {
            // If we have a new photo, use its URI
            var persistedPhotoUri = currentPhotoUri?.toString()
            if (persistedPhotoUri?.startsWith("content://") == true) {
                try {
                    // Take a persistable URI permission
                    val contentResolver = requireContext().contentResolver
                    val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                    contentResolver.takePersistableUriPermission(currentPhotoUri!!, flags)
                    
                    // Log success
                    Log.d("WTStudentsFragment", "Took persistable URI permission for: $persistedPhotoUri")
                } catch (e: Exception) {
                    Log.e("WTStudentsFragment", "Failed to take persistable URI permission: ${e.message}")
                    Toast.makeText(requireContext(), "Warning: Photo might not be accessible after app restart", Toast.LENGTH_SHORT).show()
                }
            }
            finalPhotoUri = persistedPhotoUri
        } else if (existingStudent != null && !photoRemoved) {
            // If no new photo and not removed, keep the existing one
            finalPhotoUri = existingStudent.photoUri
        }
        
        Log.d("WTStudentsFragment", "Final photo URI: $finalPhotoUri, removed: $photoRemoved")
        
        if (existingStudent != null) {
            // Update existing student
            val updatedStudent = existingStudent.copy(
                name = name,
                phoneNumber = phone,
                email = email,
                instagram = instagram,
                isActive = isActive,
                notes = existingStudent.notes,
                photoUri = finalPhotoUri
            )
            
            // Log what we're updating to help debug
            Log.d("WTStudentsFragment", "Updating student: ${updatedStudent.id}, name: ${updatedStudent.name}, photoUri: ${updatedStudent.photoUri}")
            
            viewModel.updateStudent(updatedStudent)
        } else {
            // Create new student only if not found
            // Log what we're creating to help debug
            Log.d("WTStudentsFragment", "Creating new student, name: $name, photoUri: $finalPhotoUri")
            
            viewModel.addStudent(
                name = name,
                phoneNumber = phone,
                email = email,
                instagram = instagram,
                isActive = isActive,
                photoUri = finalPhotoUri
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
        // Show student details instead of edit dialog
        val studentName = student.name
        val isActive = if (student.isActive) "Active" else "Inactive"
        val registrationStatus = if (viewModel.isStudentCurrentlyRegistered(student.id)) 
            "Currently registered" else "Not registered"
            
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(studentName)
            .setMessage("Phone: ${student.phoneNumber}\n" +
                     "${student.email?.let { "Email: $it\n" } ?: ""}" +
                     "${student.instagram?.let { "Instagram: $it\n" } ?: ""}" +
                     "Status: $isActive\n" +
                     "Registration: $registrationStatus")
            .setPositiveButton(R.string.ok, null)
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        currentDialogBinding = null
    }
} 