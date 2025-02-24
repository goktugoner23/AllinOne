package com.example.allinone.ui.wt

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.allinone.adapters.WTStudentAdapter
import com.example.allinone.data.WTStudent
import com.example.allinone.databinding.FragmentWtStudentsBinding
import com.example.allinone.viewmodels.WTRegisterViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class WTStudentsFragment : Fragment() {
    private var _binding: FragmentWtStudentsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: WTRegisterViewModel by viewModels()
    private lateinit var adapter: WTStudentAdapter
    private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

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
            onItemClick = { student -> showStudentDetails(student) },
            onPaymentStatusClick = { /* No payment status in Students tab */ }
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
            adapter.submitList(students)
        }
    }

    private fun showAddStudentDialog() {
        // Implementation similar to WTRegisterFragment but focused on student details
        // Will be implemented in a future update
        Snackbar.make(binding.root, "Add student functionality coming soon", Snackbar.LENGTH_SHORT).show()
    }

    private fun showStudentDetails(student: WTStudent) {
        // Implementation similar to WTRegisterFragment but focused on student details
        // Will be implemented in a future update
        Snackbar.make(binding.root, "Student details: ${student.name}", Snackbar.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
} 