package com.example.allinone.viewmodels

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.*
import androidx.lifecycle.ViewModelProvider
import com.example.allinone.data.WTStudent
import com.example.allinone.data.WTRegistration
import com.example.allinone.data.Event
import com.example.allinone.data.Transaction
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
    
    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading
    
    private val _error = MutableLiveData<String?>(null)
    val error: LiveData<String?> = _error
    
    enum class TransactionType {
        INCOME, 
        EXPENSE
    }
    
    companion object {
        private const val TAG = "WTRegisterViewModel"
    }

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
        isPaid: Boolean = false  // Default to unpaid to match data class
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
            // Save the registration
            repository.insertRegistration(registration)
            
            // If it's marked as paid, also add a transaction
            if (isPaid && amount > 0) {
                val studentName = repository.students.value.find { it.id == studentId }?.name ?: "Unknown Student"
                val description = "Course Registration: $studentName"
                val formattedDate = startDate?.let { 
                    java.text.SimpleDateFormat("MMM dd", java.util.Locale.getDefault()).format(it)
                } ?: ""
                
                val category = "Course Registration"
                
                // Create transaction with reference to registration
                repository.insertTransaction(
                    amount = amount,
                    type = if (formattedDate.isNotEmpty()) "Registration ($formattedDate)" else "Registration", 
                    description = description,
                    isIncome = true,  // Registration payments are income
                    category = category,
                    relatedRegistrationId = registration.id  // Link transaction to registration
                )
                
                // Refresh transactions to update UI
                repository.refreshTransactions()
            }
            
            // Add end date to calendar if available
            endDate?.let { date ->
                val studentName = repository.students.value.find { it.id == studentId }?.name ?: "Unknown Student"
                val title = "Registration End: $studentName"
                val description = "Registration period ending for $studentName. Amount: $amount"
                
                // Use direct call to calendar repository instead of ViewModel
                val event = Event(
                    id = registration.id,  // Reuse registration ID for the event
                    title = title,
                    description = description,
                    date = date,
                    type = "Registration End"
                )
                repository.insertEvent(event)
                repository.refreshEvents()
            }
            
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
        Log.d(TAG, "Updating registration: $registration")
        viewModelScope.launch {
            try {
                _isLoading.value = true
                
                // Get original registration to check if payment status changed
                val originalRegistration = _registrations.value?.find { it.id == registration.id }
                
                // If original found, check for payment status changes
                if (originalRegistration != null) {
                    // Payment status changed from unpaid to paid
                    if (!originalRegistration.isPaid && registration.isPaid && registration.amount > 0) {
                        Log.d(TAG, "Registration marked as PAID, creating transaction")
                        val studentName = repository.students.value.find { it.id == registration.studentId }?.name ?: "Unknown Student"
                        
                        repository.insertTransaction(
                            amount = registration.amount,
                            type = "Registration",
                            description = "Registration payment: $studentName",
                            isIncome = true,
                            category = "Course Registration",
                            relatedRegistrationId = registration.id
                        )
                    } 
                    // Payment status changed from paid to unpaid
                    else if (originalRegistration.isPaid && !registration.isPaid) {
                        Log.d(TAG, "Registration marked as UNPAID, deleting related transactions")
                        repository.deleteTransactionsByRegistrationId(registration.id)
                    }
                    
                    // If end date changed, update the calendar event
                    if (originalRegistration.endDate != registration.endDate) {
                        // First try to remove any existing event
                        val event = Event(
                            id = registration.id,  // Same ID used for both registration and event
                            title = "",  // These fields don't matter for deletion
                            description = "",
                            date = Date(),
                            type = "Registration End"
                        )
                        repository.deleteEvent(event)
                        
                        // Add new event for the updated end date
                        registration.endDate?.let { newDate ->
                            val studentName = repository.students.value.find { it.id == registration.studentId }?.name ?: "Unknown Student"
                            val title = "Registration End: $studentName"
                            val description = "Registration period ending for $studentName. Amount: ${registration.amount}"
                            
                            val newEvent = Event(
                                id = registration.id,  // Reuse registration ID for the event
                                title = title,
                                description = description,
                                date = newDate,
                                type = "Registration End"
                            )
                            repository.insertEvent(newEvent)
                            repository.refreshEvents()
                        }
                    }
                }
                
                // Update the registration
                repository.updateRegistration(registration)
                
                // Refresh data to ensure UI updates
                refreshData()
                
                _isLoading.value = false
            } catch (e: Exception) {
                _isLoading.value = false
                _error.value = e.localizedMessage ?: "Error updating registration"
                Log.e(TAG, "Error updating registration: ${e.message}", e)
            }
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
    
    fun deleteRegistration(registration: WTRegistration) = viewModelScope.launch {
        try {
            _isLoading.value = true
            Log.d(TAG, "Deleting registration: ${registration.id}")
            
            // First delete any related transactions
            if (registration.isPaid) {
                repository.deleteTransactionsByRegistrationId(registration.id)
            }
            
            // Delete any related calendar events
            val event = Event(
                id = registration.id,  // Same ID used for both registration and event
                title = "",  // These fields don't matter for deletion
                description = "",
                date = Date(),
                type = "Registration End"
            )
            repository.deleteEvent(event)
            
            // Now delete the registration itself
            repository.deleteRegistration(registration)
            
            // Refresh data to ensure UI updates
            refreshData()
            
            _isLoading.value = false
        } catch (e: Exception) {
            _isLoading.value = false
            _error.value = e.localizedMessage ?: "Error deleting registration"
            Log.e(TAG, "Error deleting registration: ${e.message}", e)
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