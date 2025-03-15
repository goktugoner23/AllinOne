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
    private var editingStudent: WTStudent? = null
    
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
        // Use the common showStudentDialog method to edit a student
        showStudentDialog(student)
    }

    private fun showStudentDialog(student: WTStudent?) {
        val isEdit = student != null
        val dialogTitle = if (isEdit) R.string.edit_student else R.string.add_student
        
        val dialogInflater = LayoutInflater.from(requireContext())
        val dialogBinding = DialogAddStudentBinding.inflate(dialogInflater, null, false)
        
        // Set existing values if editing
        if (isEdit) {
            dialogBinding.apply {
                nameEditText.setText(student!!.name)
                phoneEditText.setText(student.phoneNumber)
                emailEditText.setText(student.email ?: "")
                instagramEditText.setText(student.instagram ?: "")
                activeSwitch.isChecked = student.isActive
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
        
        return isValid
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
        
        if (existingStudent != null) {
            // Update existing student
            val updatedStudent = existingStudent.copy(
                name = name,
                phoneNumber = phone,
                email = email,
                instagram = instagram,
                isActive = isActive,
                notes = existingStudent.notes
            )
            viewModel.updateStudent(updatedStudent)
        } else {
            // Create new student only if not found
            viewModel.addStudent(
                name = name,
                phoneNumber = phone,
                email = email,
                instagram = instagram,
                isActive = isActive
            )
        }
        
        // Reset temporary fields
        editingStudent = null
    }

    private fun observeNetworkStatus() {
        viewModel.isNetworkAvailable.observe(viewLifecycleOwner) { isAvailable ->
            val offlineView = binding.offlineStatusView.root
            if (isAvailable) {
                offlineView.visibility = View.GONE
            } else {
                offlineView.visibility = View.VISIBLE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
} 