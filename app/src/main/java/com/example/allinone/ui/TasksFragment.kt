package com.example.allinone.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.PopupMenu
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.allinone.R
import com.example.allinone.adapters.TasksAdapter
import com.example.allinone.data.Task
import com.example.allinone.databinding.FragmentTasksBinding
import com.example.allinone.viewmodels.TasksViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class TasksFragment : Fragment() {

    private var _binding: FragmentTasksBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: TasksViewModel
    private lateinit var tasksAdapter: TasksAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTasksBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize ViewModel
        viewModel = ViewModelProvider(this)[TasksViewModel::class.java]

        // Setup RecyclerView
        setupRecyclerView()

        // Setup FAB
        setupFab()

        // Setup SwipeRefreshLayout
        setupSwipeRefresh()

        // Observe ViewModel
        observeViewModel()
    }

    private fun setupRecyclerView() {
        tasksAdapter = TasksAdapter(
            onItemClick = { task -> 
                viewModel.toggleTaskCompleted(task)
                // Show feedback message
                val message = if (task.completed) {
                    getString(R.string.task_incomplete)
                } else {
                    getString(R.string.task_completed)
                }
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            },
            onItemLongClick = { task, view -> 
                showTaskContextMenu(task, view)
            }
        )

        binding.tasksRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = tasksAdapter
        }
    }

    private fun setupFab() {
        binding.addTaskFab.setOnClickListener {
            showAddTaskDialog()
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefreshLayout.setOnRefreshListener {
            viewModel.refreshData()
        }
    }

    private fun observeViewModel() {
        // Observe tasks
        viewModel.allTasks.observe(viewLifecycleOwner) { tasks ->
            tasksAdapter.submitList(tasks)
            
            // Show/hide empty state
            if (tasks.isEmpty()) {
                binding.emptyStateText.visibility = View.VISIBLE
                binding.tasksRecyclerView.visibility = View.GONE
            } else {
                binding.emptyStateText.visibility = View.GONE
                binding.tasksRecyclerView.visibility = View.VISIBLE
            }
        }

        // Observe loading state
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.swipeRefreshLayout.isRefreshing = isLoading
        }

        // Observe error messages
        viewModel.errorMessage.observe(viewLifecycleOwner) { errorMessage ->
            errorMessage?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
                viewModel.clearErrorMessage()
            }
        }
    }

    private fun showAddTaskDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_task, null)
        val nameInput = dialogView.findViewById<android.widget.EditText>(R.id.taskNameInput)
        val descInput = dialogView.findViewById<android.widget.EditText>(R.id.taskDescriptionInput)
        val dueDateText = dialogView.findViewById<android.widget.TextView>(R.id.dueDateText)
        val pickDueDateButton = dialogView.findViewById<android.widget.Button>(R.id.pickDueDateButton)
        val clearDueDateButton = dialogView.findViewById<android.widget.Button>(R.id.clearDueDateButton)

        var dueDate: java.util.Date? = null

        fun updateDueDateText() {
            dueDateText.text = dueDate?.let {
                java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(it)
            } ?: getString(R.string.not_set)
        }
        updateDueDateText()

        pickDueDateButton.setOnClickListener {
            val now = java.util.Calendar.getInstance()
            val datePicker = android.app.DatePickerDialog(requireContext(), { _, year, month, dayOfMonth ->
                val timePicker = android.app.TimePickerDialog(requireContext(), { _, hour, minute ->
                    val cal = java.util.Calendar.getInstance()
                    cal.set(year, month, dayOfMonth, hour, minute, 0)
                    dueDate = cal.time
                    updateDueDateText()
                }, now.get(java.util.Calendar.HOUR_OF_DAY), now.get(java.util.Calendar.MINUTE), true)
                timePicker.show()
            }, now.get(java.util.Calendar.YEAR), now.get(java.util.Calendar.MONTH), now.get(java.util.Calendar.DAY_OF_MONTH))
            datePicker.show()
        }
        clearDueDateButton.setOnClickListener {
            dueDate = null
            updateDueDateText()
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.new_task))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.add_task)) { _, _ ->
                val name = nameInput.text.toString().trim()
                val description = descInput.text.toString().trim().ifEmpty { null }
                if (name.isNotEmpty()) {
                    viewModel.addTask(name, description, dueDate)
                    Toast.makeText(requireContext(), getString(R.string.task_added), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), getString(R.string.please_enter_title), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showEditTaskDialog(task: Task) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_task, null)
        val nameInput = dialogView.findViewById<android.widget.EditText>(R.id.taskNameInput)
        val descInput = dialogView.findViewById<android.widget.EditText>(R.id.taskDescriptionInput)
        val dueDateText = dialogView.findViewById<android.widget.TextView>(R.id.dueDateText)
        val pickDueDateButton = dialogView.findViewById<android.widget.Button>(R.id.pickDueDateButton)
        val clearDueDateButton = dialogView.findViewById<android.widget.Button>(R.id.clearDueDateButton)
        nameInput.setText(task.name)
        descInput.setText(task.description ?: "")

        var dueDate: java.util.Date? = task.dueDate
        fun updateDueDateText() {
            dueDateText.text = dueDate?.let {
                java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(it)
            } ?: getString(R.string.not_set)
        }
        updateDueDateText()

        pickDueDateButton.setOnClickListener {
            val calInit = java.util.Calendar.getInstance()
            if (dueDate != null) calInit.time = dueDate!!
            val datePicker = android.app.DatePickerDialog(requireContext(), { _, year, month, dayOfMonth ->
                val timePicker = android.app.TimePickerDialog(requireContext(), { _, hour, minute ->
                    val cal = java.util.Calendar.getInstance()
                    cal.set(year, month, dayOfMonth, hour, minute, 0)
                    dueDate = cal.time
                    updateDueDateText()
                }, calInit.get(java.util.Calendar.HOUR_OF_DAY), calInit.get(java.util.Calendar.MINUTE), true)
                timePicker.show()
            }, calInit.get(java.util.Calendar.YEAR), calInit.get(java.util.Calendar.MONTH), calInit.get(java.util.Calendar.DAY_OF_MONTH))
            datePicker.show()
        }
        clearDueDateButton.setOnClickListener {
            dueDate = null
            updateDueDateText()
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.edit_task))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                val name = nameInput.text.toString().trim()
                val description = descInput.text.toString().trim().ifEmpty { null }
                if (name.isNotEmpty()) {
                    viewModel.editTask(task, name, description, dueDate)
                    Toast.makeText(requireContext(), getString(R.string.task_updated), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), getString(R.string.please_enter_title), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showTaskContextMenu(task: Task, anchor: View) {
        val popup = PopupMenu(requireContext(), anchor)
        popup.menuInflater.inflate(R.menu.menu_task_options, popup.menu)
        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_edit -> {
                    showEditTaskDialog(task)
                    true
                }
                R.id.action_delete -> {
                    showDeleteConfirmationDialog(task)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun showDeleteConfirmationDialog(task: Task) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.delete_task))
            .setMessage(getString(R.string.delete_task_confirmation))
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                viewModel.deleteTask(task)
                Toast.makeText(requireContext(), getString(R.string.task_deleted), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
} 