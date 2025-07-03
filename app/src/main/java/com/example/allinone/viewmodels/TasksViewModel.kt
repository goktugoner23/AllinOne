package com.example.allinone.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.allinone.data.Task
import com.example.allinone.firebase.FirebaseIdManager
import com.example.allinone.firebase.FirebaseRepository
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.Date

class TasksViewModel(application: Application) : AndroidViewModel(application) {
    
    private val repository = FirebaseRepository(application)
    private val idManager = FirebaseIdManager()
    
    // LiveData for all tasks
    private val _allTasks = MutableLiveData<List<Task>>(emptyList())
    val allTasks: LiveData<List<Task>> = _allTasks
    
    // Loading state
    val isLoading: LiveData<Boolean> = repository.isLoading
    
    // Error messages
    val errorMessage: LiveData<String?> = repository.errorMessage
    
    init {
        // Collect tasks from the repository
        viewModelScope.launch {
            repository.tasks.collect { taskList ->
                _allTasks.postValue(taskList)
            }
        }
    }
    
    /**
     * Add a new task
     */
    fun addTask(description: String) = viewModelScope.launch {
        val newId = idManager.getNextId("tasks")
        val task = Task(
            id = newId,
            description = description,
            completed = false,
            date = Date()
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
     * Update task description
     */
    fun editTask(task: Task, newDescription: String) = viewModelScope.launch {
        val updatedTask = task.copy(description = newDescription)
        repository.updateTask(updatedTask)
    }
    
    /**
     * Delete a task
     */
    fun deleteTask(task: Task) = viewModelScope.launch {
        repository.deleteTask(task)
    }
    
    /**
     * Refresh data from Firebase
     */
    fun refreshData() = viewModelScope.launch {
        repository.refreshTasks()
    }
    
    /**
     * Clear error message
     */
    fun clearErrorMessage() {
        repository.clearErrorMessage()
    }
} 