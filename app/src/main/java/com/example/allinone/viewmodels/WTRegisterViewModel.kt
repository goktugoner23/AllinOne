package com.example.allinone.viewmodels

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.*
import com.example.allinone.data.WTStudent
import com.example.allinone.data.WTRegistration
import com.example.allinone.firebase.FirebaseRepository
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.*

class WTRegisterViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = FirebaseRepository(application)
    
    private val _students = MutableLiveData<List<WTStudent>>(emptyList())
    val students: LiveData<List<WTStudent>> = _students
    
    private val _registrations = MutableLiveData<List<WTRegistration>>(emptyList())
    val registrations: LiveData<List<WTRegistration>> = _registrations
    
    val isNetworkAvailable: LiveData<Boolean> = repository.isNetworkAvailable
    
    private val _selectedStudent = MutableLiveData<WTStudent?>(null)
    val selectedStudent: LiveData<WTStudent?> = _selectedStudent
    
    private val _selectedRegistration = MutableLiveData<WTRegistration?>(null)
    val selectedRegistration: LiveData<WTRegistration?> = _selectedRegistration

    init {
        viewModelScope.launch {
            repository.students.collect { students ->
                _students.value = students
            }
        }
        
        viewModelScope.launch {
            repository.registrations.collect { registrations ->
                _registrations.value = registrations
            }
        }
    }

    fun refreshData() {
        viewModelScope.launch {
            Log.d("WTRegisterViewModel", "Starting data refresh")
            repository.refreshStudents()
            repository.refreshRegistrations()
            Log.d("WTRegisterViewModel", "Data refresh completed")
        }
    }
    
    fun addStudent(
        name: String,
        phoneNumber: String? = null,
        email: String? = null,
        instagram: String? = null,
        isActive: Boolean = true,
        deviceId: String? = null,
        notes: String? = null
    ) {
        val student = WTStudent(
            id = UUID.randomUUID().mostSignificantBits,
            name = name,
            phoneNumber = phoneNumber,
            email = email,
            instagram = instagram,
            isActive = isActive,
            deviceId = deviceId,
            notes = notes
        )
        
        viewModelScope.launch {
            repository.insertStudent(student)
            repository.refreshStudents()
        }
    }
    
    fun addRegistration(
        studentId: Long,
        amount: Double,
        startDate: Date?,
        endDate: Date?,
        attachmentUri: String? = null,
        notes: String? = null,
        isPaid: Boolean = true
    ) {
        val registration = WTRegistration(
            id = UUID.randomUUID().mostSignificantBits,
            studentId = studentId,
            amount = amount,
            startDate = startDate,
            endDate = endDate,
            paymentDate = Date(),
            attachmentUri = attachmentUri,
            notes = notes,
            isPaid = isPaid
        )
        
        // Log for debugging
        Log.d("WTRegisterViewModel", "Adding registration: $registration")
        
        viewModelScope.launch {
            repository.insertRegistration(registration)
            // Explicitly refresh registrations to ensure UI updates
            repository.refreshRegistrations()
            // Log updated registrations for debugging
            Log.d("WTRegisterViewModel", "Current registrations: ${_registrations.value?.size ?: 0}")
        }
    }
    
    fun updateStudent(student: WTStudent) {
        viewModelScope.launch {
            repository.updateStudent(student)
            repository.refreshStudents()
        }
    }
    
    fun updateRegistration(registration: WTRegistration) {
        Log.d("WTRegisterViewModel", "Updating registration: $registration")
        viewModelScope.launch {
            repository.updateRegistration(registration)
            // Explicitly refresh registrations to ensure UI updates
            repository.refreshRegistrations()
        }
    }

    fun deleteStudent(student: WTStudent) {
        viewModelScope.launch {
            // Delete the student
            repository.deleteStudent(student)
            
            // Also delete all registrations for this student
            val studentRegistrations = repository.getRegistrationsForStudent(student.id)
            studentRegistrations.forEach { registration ->
                repository.deleteRegistration(registration)
            }
            
            // Refresh data
            repository.refreshStudents()
            repository.refreshRegistrations()
        }
    }
    
    fun deleteRegistration(registration: WTRegistration) {
        viewModelScope.launch {
            try {
                Log.d("WTRegisterViewModel", "Deleting registration ID: ${registration.id}")
                repository.deleteRegistration(registration)
                
                // Wait a moment for Firebase to process the deletion
                kotlinx.coroutines.delay(500)
                
                // Refresh to update the UI
                repository.refreshRegistrations()
                Log.d("WTRegisterViewModel", "Registration deleted successfully")
            } catch (e: Exception) {
                Log.e("WTRegisterViewModel", "Error deleting registration: ${e.message}", e)
                throw e
            }
        }
    }
    
    fun uploadAttachment(uri: Uri, onComplete: (String?) -> Unit) {
        viewModelScope.launch {
            val uploadedUri = repository.uploadAttachment(uri)
            onComplete(uploadedUri)
        }
    }
    
    fun setSelectedStudent(student: WTStudent?) {
        _selectedStudent.value = student
    }
    
    fun setSelectedRegistration(registration: WTRegistration?) {
        _selectedRegistration.value = registration
    }
    
    fun getRegistrationsForStudent(studentId: Long): List<WTRegistration> {
        return registrations.value?.filter { it.studentId == studentId } ?: emptyList()
    }
    
    fun isStudentCurrentlyRegistered(studentId: Long): Boolean {
        return registrations.value?.any { 
            it.studentId == studentId && 
            it.endDate?.after(Date()) ?: false 
        } ?: false
    }
    
    fun getCurrentRegistrationForStudent(studentId: Long): WTRegistration? {
        return registrations.value
            ?.filter { it.studentId == studentId }
            ?.maxByOrNull { it.startDate ?: Date(0) }
    }
} 