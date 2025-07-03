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
        val editText = EditText(requireContext()).apply {
            hint = getString(R.string.task_description)
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.new_task))
            .setMessage(getString(R.string.enter_task_description))
            .setView(editText)
            .setPositiveButton(getString(R.string.add_task)) { _, _ ->
                val description = editText.text.toString().trim()
                if (description.isNotEmpty()) {
                    viewModel.addTask(description)
                    Toast.makeText(requireContext(), getString(R.string.task_added), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showEditTaskDialog(task: Task) {
        val editText = EditText(requireContext()).apply {
            setText(task.description)
            hint = getString(R.string.task_description)
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.edit_task))
            .setView(editText)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                val description = editText.text.toString().trim()
                if (description.isNotEmpty()) {
                    viewModel.editTask(task, description)
                    Toast.makeText(requireContext(), getString(R.string.task_updated), Toast.LENGTH_SHORT).show()
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