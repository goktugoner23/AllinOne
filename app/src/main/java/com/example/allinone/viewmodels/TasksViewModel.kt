package com.example.allinone.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.allinone.data.Task
import com.example.allinone.data.TaskGroup
import com.example.allinone.firebase.FirebaseIdManager
import com.example.allinone.firebase.FirebaseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

@HiltViewModel
class TasksViewModel @Inject constructor(
    private val repository: FirebaseRepository
) : ViewModel() {
    
    private val idManager = FirebaseIdManager()
    
    // LiveData for all tasks
    private val _allTasks = MutableLiveData<List<Task>>(emptyList())
    val allTasks: LiveData<List<Task>> = _allTasks
    
    // LiveData for all task groups
    private val _allTaskGroups = MutableLiveData<List<TaskGroup>>(emptyList())
    val allTaskGroups: LiveData<List<TaskGroup>> = _allTaskGroups
    
    // LiveData for tasks grouped by their group
    private val _groupedTasks = MutableLiveData<Map<TaskGroup?, List<Task>>>(emptyMap())
    val groupedTasks: LiveData<Map<TaskGroup?, List<Task>>> = _groupedTasks
    
    // Loading state
    val isLoading: LiveData<Boolean> = repository.isLoading
    
    // Error messages
    val errorMessage: LiveData<String?> = repository.errorMessage
    
    init {
        // Collect tasks from the repository
        viewModelScope.launch {
            repository.tasks.collect { taskList ->
                _allTasks.postValue(taskList)
                updateGroupedTasks()
            }
        }
        
        // Collect task groups from the repository
        viewModelScope.launch {
            repository.taskGroups.collect { groupList ->
                _allTaskGroups.postValue(groupList)
                updateGroupedTasks()
            }
        }
    }
    
    /**
     * Update grouped tasks whenever tasks or groups change
     */
    private fun updateGroupedTasks() {
        val tasks = _allTasks.value ?: emptyList()
        val groups = _allTaskGroups.value ?: emptyList()
        
        val groupedMap = mutableMapOf<TaskGroup?, List<Task>>()
        
        // Group tasks by their groupId
        val groupedTasks = tasks.groupBy { task ->
            groups.find { it.id == task.groupId }
        }
        
        // Add ungrouped tasks (null group)
        groupedMap[null] = groupedTasks[null] ?: emptyList()
        
        // Add grouped tasks
        groups.forEach { group ->
            groupedMap[group] = groupedTasks[group] ?: emptyList()
        }
        
        _groupedTasks.postValue(groupedMap)
    }

    /**
     * Add a new task
     */
    fun addTask(name: String, description: String?, dueDate: Date?, groupId: Long? = null) = viewModelScope.launch {
        val newId = idManager.getNextId("tasks")
        val task = Task(
            id = newId,
            name = name,
            description = description,
            completed = false,
            date = Date(),
            dueDate = dueDate,
            groupId = groupId
        )
        repository.insertTask(task)
    }
    
    /**
     * Toggle task completion status
     */
    fun toggleTaskCompleted(task: Task) = viewModelScope.launch {
        val updatedTask = task.copy(completed = !task.completed)
        repository.updateTask(updatedTask)
    }
    
    /**
     * Update task name, description, due date, and group
     */
    fun editTask(task: Task, newName: String, newDescription: String?, newDueDate: Date?, newGroupId: Long? = null) = viewModelScope.launch {
        val updatedTask = task.copy(name = newName, description = newDescription, dueDate = newDueDate, groupId = newGroupId)
        repository.updateTask(updatedTask)
    }
    
    /**
     * Delete a task
     */
    fun deleteTask(task: Task) = viewModelScope.launch {
        repository.deleteTask(task)
    }
    
    /**
     * Add a new task group
     */
    fun addTaskGroup(title: String, description: String?, color: String = "#2196F3") = viewModelScope.launch {
        val newId = idManager.getNextId("taskGroups")
        val taskGroup = TaskGroup(
            id = newId,
            title = title,
            description = description,
            color = color,
            createdAt = Date(),
            isCompleted = false
        )
        repository.insertTaskGroup(taskGroup)
    }
    
    /**
     * Add a task group from TaskGroup object
     */
    fun addTaskGroup(taskGroup: TaskGroup) = viewModelScope.launch {
        val newId = idManager.getNextId("taskGroups")
        val newTaskGroup = taskGroup.copy(id = newId)
        repository.insertTaskGroup(newTaskGroup)
    }
    
    /**
     * Update a task group
     */
    fun editTaskGroup(taskGroup: TaskGroup, newTitle: String, newDescription: String?, newColor: String) = viewModelScope.launch {
        val updatedTaskGroup = taskGroup.copy(title = newTitle, description = newDescription, color = newColor)
        repository.updateTaskGroup(updatedTaskGroup)
    }
    
    /**
     * Update a task group from TaskGroup object
     */
    fun editTaskGroup(taskGroup: TaskGroup) = viewModelScope.launch {
        repository.updateTaskGroup(taskGroup)
    }
    
    /**
     * Delete a task group (and unassign all tasks from it)
     */
    fun deleteTaskGroup(taskGroup: TaskGroup) = viewModelScope.launch {
        repository.deleteTaskGroup(taskGroup)
    }
    
    /**
     * Delete a task group by ID
     */
    fun deleteTaskGroup(taskGroupId: Long) = viewModelScope.launch {
        val taskGroup = _allTaskGroups.value?.find { it.id == taskGroupId }
        taskGroup?.let { repository.deleteTaskGroup(it) }
    }
    
    /**
     * Toggle task group completion status
     */
    fun toggleTaskGroupCompleted(taskGroup: TaskGroup) = viewModelScope.launch {
        val updatedTaskGroup = taskGroup.copy(isCompleted = !taskGroup.isCompleted)
        repository.updateTaskGroup(updatedTaskGroup)
    }

    /**
     * Get tasks for a specific group
     */
    fun getTasksForGroup(groupId: Long?): List<Task> {
        return _allTasks.value?.filter { it.groupId == groupId } ?: emptyList()
    }
    
    /**
     * Get ungrouped tasks
     */
    fun getUngroupedTasks(): List<Task> {
        return _allTasks.value?.filter { it.groupId == null } ?: emptyList()
    }
    
    /**
     * Refresh data from repository
     */
    fun refreshData() {
        // For now, data refreshing is handled automatically by the repository flows
        // This method can be extended in the future if manual refresh is needed
    }
    
    /**
     * Clear error message
     */
    fun clearErrorMessage() {
        // For now, error messages are handled automatically
        // This method can be extended in the future if manual error clearing is needed
    }
} 