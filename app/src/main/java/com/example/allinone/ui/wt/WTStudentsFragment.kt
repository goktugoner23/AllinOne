package com.example.allinone.ui.wt

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
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
    private var selectedImageUri: Uri? = null
    private var editingStudent: WTStudent? = null
    private var temporaryCameraImageUri: Uri? = null
    private var pendingImageSource: ImageSource? = null
    
    private enum class ImageSource {
        CAMERA, GALLERY
    }

    // Gallery picker launcher
    private val getImageFromGallery = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                selectedImageUri = uri
                // Update the image in the dialog if it's open
                dialogBinding?.profileImageView?.setImageURI(uri)
            }
        }
    }
    
    // Camera launcher
    private val getImageFromCamera = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && temporaryCameraImageUri != null) {
            selectedImageUri = temporaryCameraImageUri
            // Update the image in the dialog if it's open
            dialogBinding?.profileImageView?.setImageURI(selectedImageUri)
        }
    }
    
    // Permission launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraPermissionGranted = permissions[Manifest.permission.CAMERA] ?: false
        val storagePermissionGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions[Manifest.permission.READ_MEDIA_IMAGES] ?: false
        } else {
            permissions[Manifest.permission.READ_EXTERNAL_STORAGE] ?: false
        }
        
        when (pendingImageSource) {
            ImageSource.CAMERA -> {
                if (cameraPermissionGranted) {
                    openCamera()
                } else {
                    showPermissionDeniedMessage("Camera")
                }
            }
            ImageSource.GALLERY -> {
                if (storagePermissionGranted) {
                    openGallery()
                } else {
                    showPermissionDeniedMessage("Storage")
                }
            }
            null -> {}
        }
        
        pendingImageSource = null
    }

    private var dialogBinding: DialogAddStudentBinding? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentWtStudentsBinding.inflate(inflater, container, false)
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
            onItemClick = { student -> showEditStudentDialog(student) }
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
        viewModel.allStudents.observe(viewLifecycleOwner) { students ->
            // Deduplicate students by ID before submitting to adapter
            val uniqueStudents = students.distinctBy { it.id }
            adapter.submitList(uniqueStudents)
            
            // Show or hide empty state
            if (uniqueStudents.isEmpty()) {
                binding.emptyState.visibility = View.VISIBLE
                binding.studentsRecyclerView.visibility = View.GONE
            } else {
                binding.emptyState.visibility = View.GONE
                binding.studentsRecyclerView.visibility = View.VISIBLE
            }
        }
    }

    private fun showAddStudentDialog() {
        editingStudent = null
        selectedImageUri = null
        showStudentDialog(null)
    }

    private fun showEditStudentDialog(student: WTStudent) {
        editingStudent = student
        selectedImageUri = student.profileImageUri?.let { Uri.parse(it) }
        showStudentDialog(student)
    }

    private fun showStudentDialog(student: WTStudent?) {
        val isEdit = student != null
        val dialogTitle = if (isEdit) R.string.edit_student else R.string.add_student
        
        val dialogInflater = LayoutInflater.from(requireContext())
        val dialogBinding = DialogAddStudentBinding.inflate(dialogInflater, null, false)
        this.dialogBinding = dialogBinding
        
        // Set existing values if editing
        if (isEdit) {
            dialogBinding.apply {
                nameEditText.setText(student!!.name)
                phoneEditText.setText(student.phoneNumber)
                emailEditText.setText(student.email ?: "")
                instagramEditText.setText(student.instagram ?: "")
                activeSwitch.isChecked = student.isActive
                
                // Set profile image
                student.profileImageUri?.let { imageUri ->
                    try {
                        profileImageView.setImageURI(Uri.parse(imageUri))
                    } catch (e: Exception) {
                        profileImageView.setImageResource(R.drawable.default_profile)
                    }
                }
            }
        }
        
        // Set up image picker
        dialogBinding.addImageButton.setOnClickListener {
            showImageSourceDialog()
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
            }
            // If validation fails, dialog stays open with errors shown
        }
        
        // Setup cancel button listener
        dialogBinding.cancelButton.setOnClickListener {
            dialog.dismiss()
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
            val existingStudentWithName = viewModel.allStudents.value?.find { it.name == name }
            if (existingStudentWithName != null) {
                dialogBinding.nameInputLayout.error = "A student with this name already exists"
                isValid = false
            }
            
            val existingStudentWithPhone = viewModel.allStudents.value?.find { it.phoneNumber == phone }
            if (existingStudentWithPhone != null) {
                dialogBinding.phoneInputLayout.error = "A student with this phone number already exists"
                isValid = false
            }
        }
        
        return isValid
    }
    
    private fun showImageSourceDialog() {
        val options = arrayOf(
            getString(R.string.take_photo), 
            getString(R.string.choose_from_gallery)
        )
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.select_profile_photo)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        pendingImageSource = ImageSource.CAMERA
                        checkAndRequestCameraPermission()
                    }
                    1 -> {
                        pendingImageSource = ImageSource.GALLERY
                        checkAndRequestStoragePermission()
                    }
                }
            }
            .show()
    }
    
    private fun checkAndRequestCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                openCamera()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) -> {
                showPermissionRationaleDialog(
                    getString(R.string.camera_permission_title),
                    getString(R.string.camera_permission_message),
                    arrayOf(Manifest.permission.CAMERA)
                )
            }
            else -> {
                requestPermissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
            }
        }
    }
    
    private fun checkAndRequestStoragePermission() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        
        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                permission
            ) == PackageManager.PERMISSION_GRANTED -> {
                openGallery()
            }
            shouldShowRequestPermissionRationale(permission) -> {
                showPermissionRationaleDialog(
                    getString(R.string.storage_permission_title),
                    getString(R.string.storage_permission_message),
                    arrayOf(permission)
                )
            }
            else -> {
                requestPermissionLauncher.launch(arrayOf(permission))
            }
        }
    }
    
    private fun showPermissionRationaleDialog(title: String, message: String, permissions: Array<String>) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(R.string.grant_permission) { _, _ ->
                requestPermissionLauncher.launch(permissions)
            }
            .setNegativeButton(R.string.cancel) { _, _ ->
                pendingImageSource = null
            }
            .show()
    }
    
    private fun showPermissionDeniedMessage(permissionType: String) {
        Toast.makeText(
            requireContext(),
            getString(R.string.permission_denied, permissionType),
            Toast.LENGTH_LONG
        ).show()
    }
    
    private fun openCamera() {
        try {
            val photoFile = createImageFile()
            photoFile.also {
                temporaryCameraImageUri = FileProvider.getUriForFile(
                    requireContext(),
                    "${requireContext().packageName}.fileprovider",
                    it
                )
                getImageFromCamera.launch(temporaryCameraImageUri)
            }
        } catch (e: Exception) {
            Toast.makeText(
                requireContext(),
                getString(R.string.error_camera, e.message),
                Toast.LENGTH_LONG
            ).show()
            e.printStackTrace()
        }
    }
    
    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        getImageFromGallery.launch(intent)
    }
    
    private fun createImageFile(): File {
        // Create an image file name with timestamp to avoid duplicates
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = requireContext().getExternalFilesDir(null)
        return File.createTempFile(
            "JPEG_${timeStamp}_", /* prefix */
            ".jpg", /* suffix */
            storageDir /* directory */
        )
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
        
        val student = if (editingStudent != null) {
            // Update existing student
            editingStudent!!.copy(
                name = name,
                phoneNumber = phone,
                email = email,
                instagram = instagram,
                isActive = isActive,
                profileImageUri = selectedImageUri?.toString() ?: editingStudent!!.profileImageUri
            )
        } else {
            // Create new student
            WTStudent(
                id = abs(UUID.randomUUID().mostSignificantBits),
                name = name,
                phoneNumber = phone,
                email = email,
                instagram = instagram,
                isActive = isActive,
                profileImageUri = selectedImageUri?.toString()
            )
        }
        
        // Save student via view model
        if (editingStudent != null) {
            viewModel.updateStudent(student)
        } else {
            viewModel.addStudent(student)
        }
        
        // Reset temporary fields
        editingStudent = null
        selectedImageUri = null
        temporaryCameraImageUri = null
        pendingImageSource = null
        this.dialogBinding = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        dialogBinding = null
        _binding = null
    }
} 