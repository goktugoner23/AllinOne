package com.example.allinone.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.PopupMenu
import android.widget.Spinner
import android.widget.Toast
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.allinone.R
import com.example.allinone.adapters.TasksAdapter
import com.example.allinone.adapters.GroupedTasksAdapter
import com.example.allinone.data.Task
import com.example.allinone.data.TaskGroup
import com.example.allinone.databinding.FragmentTasksBinding
import com.example.allinone.ui.dialogs.TaskGroupDialogManager
import com.example.allinone.viewmodels.TasksViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class TasksFragment : Fragment() {

    private var _binding: FragmentTasksBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: TasksViewModel
    private lateinit var tasksAdapter: TasksAdapter
    private lateinit var groupedTasksAdapter: GroupedTasksAdapter
    private lateinit var taskGroupDialogManager: TaskGroupDialogManager
    private var allGroups: List<TaskGroup> = emptyList()
    private var isGroupedView = false

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

        // Setup menu provider
        setupMenuProvider()

        // Initialize ViewModel
        viewModel = ViewModelProvider(this)[TasksViewModel::class.java]

        // Initialize TaskGroupDialogManager
        taskGroupDialogManager = TaskGroupDialogManager(requireContext())

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
        // Initialize simple tasks adapter
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

        // Initialize grouped tasks adapter
        groupedTasksAdapter = GroupedTasksAdapter(
            onTaskClick = { task -> 
                viewModel.toggleTaskCompleted(task)
                // Show feedback message
                val message = if (task.completed) {
                    getString(R.string.task_incomplete)
                } else {
                    getString(R.string.task_completed)
                }
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            },
            onTaskLongClick = { task, view -> 
                showTaskContextMenu(task, view)
            },
            onGroupClick = { _, _ ->
                // Refresh the grouped view when expansion state changes
                viewModel.groupedTasks.value?.let { groupedTasks ->
                    groupedTasksAdapter.updateGroupedTasks(groupedTasks)
                }
            },
            onGroupLongClick = { group, view ->
                showGroupContextMenu(group, view)
            }
        )

        binding.tasksRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = if (isGroupedView) groupedTasksAdapter else tasksAdapter
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
        // Observe tasks for simple view
        viewModel.allTasks.observe(viewLifecycleOwner) { tasks ->
            if (!isGroupedView) {
                tasksAdapter.submitList(tasks)
            }
            
            // Show/hide empty state
            if (tasks.isEmpty()) {
                binding.emptyStateText.visibility = View.VISIBLE
                binding.tasksRecyclerView.visibility = View.GONE
            } else {
                binding.emptyStateText.visibility = View.GONE
                binding.tasksRecyclerView.visibility = View.VISIBLE
            }
        }

        // Observe task groups
        viewModel.allTaskGroups.observe(viewLifecycleOwner) { groups ->
            allGroups = groups
            // Optionally auto-switch to grouped view only if there are actual grouped tasks
            // Users can manually toggle between views using the menu
        }

        // Observe grouped tasks for grouped view
        viewModel.groupedTasks.observe(viewLifecycleOwner) { groupedTasks ->
            if (isGroupedView) {
                groupedTasksAdapter.updateGroupedTasks(groupedTasks)
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
        val groupSpinner = dialogView.findViewById<Spinner>(R.id.groupSpinner)
        val dueDateText = dialogView.findViewById<android.widget.TextView>(R.id.dueDateText)
        val pickDueDateButton = dialogView.findViewById<android.widget.Button>(R.id.pickDueDateButton)
        val clearDueDateButton = dialogView.findViewById<android.widget.Button>(R.id.clearDueDateButton)

        // Setup group spinner
        val groupOptions = mutableListOf<String>()
        val groupIds = mutableListOf<Long?>()
        
        groupOptions.add(getString(R.string.no_group))
        groupIds.add(null)
        
        allGroups.forEach { group ->
            groupOptions.add(group.title)
            groupIds.add(group.id)
        }
        
        val groupAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, groupOptions)
        groupAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        groupSpinner.adapter = groupAdapter

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
                val selectedGroupId = groupIds[groupSpinner.selectedItemPosition]
                
                if (name.isNotEmpty()) {
                    viewModel.addTask(name, description, dueDate, selectedGroupId)
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
        val groupSpinner = dialogView.findViewById<Spinner>(R.id.groupSpinner)
        val dueDateText = dialogView.findViewById<android.widget.TextView>(R.id.dueDateText)
        val pickDueDateButton = dialogView.findViewById<android.widget.Button>(R.id.pickDueDateButton)
        val clearDueDateButton = dialogView.findViewById<android.widget.Button>(R.id.clearDueDateButton)
        nameInput.setText(task.name)
        descInput.setText(task.description ?: "")

        // Setup group spinner
        val groupOptions = mutableListOf<String>()
        val groupIds = mutableListOf<Long?>()
        
        groupOptions.add(getString(R.string.no_group))
        groupIds.add(null)
        
        allGroups.forEach { group ->
            groupOptions.add(group.title)
            groupIds.add(group.id)
        }
        
        val groupAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, groupOptions)
        groupAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        groupSpinner.adapter = groupAdapter
        
        // Set current group selection
        val currentGroupIndex = groupIds.indexOf(task.groupId)
        if (currentGroupIndex >= 0) {
            groupSpinner.setSelection(currentGroupIndex)
        }

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
                val selectedGroupId = groupIds[groupSpinner.selectedItemPosition]
                
                if (name.isNotEmpty()) {
                    viewModel.editTask(task, name, description, dueDate, selectedGroupId)
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

    private fun setupMenuProvider() {
        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.menu_tasks, menu)
                
                // Update toggle view menu item text based on current view
                val toggleItem = menu.findItem(R.id.action_toggle_view)
                toggleItem?.title = if (isGroupedView) {
                    getString(R.string.switch_to_list_view)
                } else {
                    getString(R.string.switch_to_grouped_view)
                }
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    R.id.action_toggle_view -> {
                        toggleView()
                        true
                    }
                    R.id.action_manage_groups -> {
                        showGroupManagementMenu()
                        true
                    }
                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun showGroupManagementMenu() {
        val options = arrayOf(
            getString(R.string.create_task_group),
            if (allGroups.isNotEmpty()) getString(R.string.edit_task_group) else null,
            if (allGroups.isNotEmpty()) getString(R.string.delete_group) else null
        ).filterNotNull()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.manage_groups))
            .setItems(options.toTypedArray()) { _, which ->
                when (options[which]) {
                    getString(R.string.create_task_group) -> {
                        showCreateGroupDialog()
                    }
                    getString(R.string.edit_task_group) -> {
                        showEditGroupSelectionDialog()
                    }
                    getString(R.string.delete_group) -> {
                        showDeleteGroupSelectionDialog()
                    }
                }
            }
            .show()
    }

    private fun showCreateGroupDialog() {
        taskGroupDialogManager.showCreateDialog(object : TaskGroupDialogManager.TaskGroupDialogListener {
            override fun onTaskGroupCreated(taskGroup: TaskGroup) {
                viewModel.addTaskGroup(taskGroup)
                Toast.makeText(requireContext(), getString(R.string.group_created), Toast.LENGTH_SHORT).show()
            }

            override fun onTaskGroupUpdated(taskGroup: TaskGroup) {
                // Not used in create dialog
            }

            override fun onTaskGroupDeleted(taskGroupId: Long) {
                // Not used in create dialog
            }
        })
    }

    private fun showEditGroupSelectionDialog() {
        if (allGroups.isEmpty()) return

        val groupNames = allGroups.map { it.title }.toTypedArray()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.edit_task_group))
            .setItems(groupNames) { _, which ->
                val selectedGroup = allGroups[which]
                taskGroupDialogManager.showEditDialog(selectedGroup, object : TaskGroupDialogManager.TaskGroupDialogListener {
                    override fun onTaskGroupCreated(taskGroup: TaskGroup) {
                        // Not used in edit dialog
                    }

                    override fun onTaskGroupUpdated(taskGroup: TaskGroup) {
                        viewModel.editTaskGroup(taskGroup)
                        Toast.makeText(requireContext(), getString(R.string.group_updated), Toast.LENGTH_SHORT).show()
                    }

                    override fun onTaskGroupDeleted(taskGroupId: Long) {
                        viewModel.deleteTaskGroup(taskGroupId)
                        Toast.makeText(requireContext(), getString(R.string.group_deleted), Toast.LENGTH_SHORT).show()
                    }
                })
            }
            .show()
    }

    private fun showDeleteGroupSelectionDialog() {
        if (allGroups.isEmpty()) return

        val groupNames = allGroups.map { it.title }.toTypedArray()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.delete_group))
            .setItems(groupNames) { _, which ->
                val selectedGroup = allGroups[which]
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(getString(R.string.delete_group))
                    .setMessage(getString(R.string.delete_group_confirmation))
                    .setPositiveButton(getString(R.string.delete)) { _, _ ->
                        viewModel.deleteTaskGroup(selectedGroup.id)
                        Toast.makeText(requireContext(), getString(R.string.group_deleted), Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton(getString(R.string.cancel), null)
                    .show()
            }
            .show()
    }

    private fun toggleView() {
        if (isGroupedView) {
            switchToListView()
        } else {
            switchToGroupedView()
        }
        // Refresh menu to update toggle button text
        requireActivity().invalidateMenu()
    }

    private fun switchToListView() {
        isGroupedView = false
        binding.tasksRecyclerView.adapter = tasksAdapter
        // Update with current tasks
        viewModel.allTasks.value?.let { tasks ->
            tasksAdapter.submitList(tasks)
        }
    }

    private fun switchToGroupedView() {
        isGroupedView = true
        binding.tasksRecyclerView.adapter = groupedTasksAdapter
        // Update with current grouped tasks
        viewModel.groupedTasks.value?.let { groupedTasks ->
            groupedTasksAdapter.updateGroupedTasks(groupedTasks)
        }
    }

    private fun showGroupContextMenu(group: TaskGroup, anchor: View) {
        val popup = PopupMenu(requireContext(), anchor)
        popup.menuInflater.inflate(R.menu.menu_group_options, popup.menu)
        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_edit_group -> {
                    taskGroupDialogManager.showEditDialog(group, object : TaskGroupDialogManager.TaskGroupDialogListener {
                        override fun onTaskGroupCreated(taskGroup: TaskGroup) {
                            // Not used in edit dialog
                        }

                        override fun onTaskGroupUpdated(taskGroup: TaskGroup) {
                            viewModel.editTaskGroup(taskGroup)
                            Toast.makeText(requireContext(), getString(R.string.group_updated), Toast.LENGTH_SHORT).show()
                        }

                        override fun onTaskGroupDeleted(taskGroupId: Long) {
                            viewModel.deleteTaskGroup(taskGroupId)
                            Toast.makeText(requireContext(), getString(R.string.group_deleted), Toast.LENGTH_SHORT).show()
                        }
                    })
                    true
                }
                R.id.action_delete_group -> {
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle(getString(R.string.delete_group))
                        .setMessage(getString(R.string.delete_group_confirmation))
                        .setPositiveButton(getString(R.string.delete)) { _, _ ->
                            viewModel.deleteTaskGroup(group.id)
                            Toast.makeText(requireContext(), getString(R.string.group_deleted), Toast.LENGTH_SHORT).show()
                        }
                        .setNegativeButton(getString(R.string.cancel), null)
                        .show()
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
} 