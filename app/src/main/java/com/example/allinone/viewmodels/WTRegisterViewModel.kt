package com.example.allinone.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.allinone.data.Transaction
import com.example.allinone.data.WTLesson
import com.example.allinone.data.WTStudent
import com.example.allinone.firebase.FirebaseRepository
import com.example.allinone.viewmodels.CalendarViewModel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date
import java.util.UUID
import androidx.lifecycle.ViewModelProvider
import com.example.allinone.config.TransactionCategories
import android.util.Log
import kotlinx.coroutines.delay

class WTRegisterViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = FirebaseRepository(application)
    
    private val _allStudents = MutableLiveData<List<WTStudent>>(emptyList())
    val allStudents: LiveData<List<WTStudent>> = _allStudents
    
    private val _activeStudents = MutableLiveData<List<WTStudent>>(emptyList())
    val activeStudents: LiveData<List<WTStudent>> = _activeStudents
    
    private val _unpaidStudents = MutableLiveData<List<WTStudent>>(emptyList())
    val unpaidStudents: LiveData<List<WTStudent>> = _unpaidStudents
    
    private val _paidStudents = MutableLiveData<List<WTStudent>>(emptyList())
    val paidStudents: LiveData<List<WTStudent>> = _paidStudents
    
    private val _registeredStudents = MutableLiveData<List<WTStudent>>(emptyList())
    val registeredStudents: LiveData<List<WTStudent>> = _registeredStudents
    
    private val _lessonSchedule = MutableLiveData<List<WTLesson>>(emptyList())
    private val calendarViewModel: CalendarViewModel by lazy { 
        ViewModelProvider.AndroidViewModelFactory.getInstance(application)
            .create(CalendarViewModel::class.java) 
    }
    
    // Add isNetworkAvailable property
    val isNetworkAvailable = repository.isNetworkAvailable
    
    // Error message live data
    private val _errorMessage = MutableLiveData<String?>(null)
    val errorMessage: LiveData<String?> = _errorMessage
    
    init {
        // Collect students from the repository flow
        viewModelScope.launch {
            repository.students.collect { students ->
                // First ensure we have no duplicate IDs in the students list
                val uniqueStudents = students.distinctBy { it.id }
                
                _allStudents.value = uniqueStudents
                _activeStudents.value = uniqueStudents.filter { it.isActive }
                _unpaidStudents.value = uniqueStudents.filter { !it.isPaid && it.startDate != null }
                _paidStudents.value = uniqueStudents.filter { it.isPaid && it.startDate != null }
                _registeredStudents.value = uniqueStudents.filter { it.startDate != null }
            }
        }
        
        // Collect lesson schedule
        viewModelScope.launch {
            repository.wtLessons.collect { lessons ->
                _lessonSchedule.value = lessons
            }
        }
    }
    
    // Clear error message
    fun clearErrorMessage() {
        _errorMessage.value = null
    }

    // Add a new student
    fun addStudent(student: WTStudent) {
        viewModelScope.launch {
            repository.insertStudent(student)
        }
    }

    // Register a student for a course
    fun registerStudentForCourse(student: WTStudent, startDate: Date, endDate: Date, amount: Double, isPaid: Boolean = false) {
        viewModelScope.launch {
            // Calculate accurate end date based on lesson schedule
            val calculatedEndDate = calculateEndDateBasedOnLessons(startDate)
            
            // Find the existing student in our database to make sure we're using the most recent version
            val existingStudent = _allStudents.value?.find { it.id == student.id }
            
            // If student doesn't exist in our records, use the provided student
            val studentToUpdate = existingStudent ?: student
            
            // Check if this student is already registered for a course
            val alreadyRegistered = _registeredStudents.value?.any { 
                it.id == studentToUpdate.id && it.startDate != null 
            } ?: false
            
            // When updating a student who is already registered, we need to be careful
            // not to override important fields with default values
            val updatedStudent = if (alreadyRegistered) {
                // Find the registered version of this student
                val registeredStudent = _registeredStudents.value?.find { it.id == studentToUpdate.id }
                
                // Only update fields that were explicitly provided and preserve existing values
                studentToUpdate.copy(
                    // Only update dates if they were explicitly provided and different
                    startDate = startDate,
                    endDate = calculatedEndDate ?: endDate,
                    
                    // For amount, only update if a non-zero value was provided
                    amount = if (amount > 0) amount else registeredStudent?.amount ?: 0.0,
                    
                    // For payment status, update based on provided value
                    isPaid = isPaid,
                    
                    // Set payment date only if paid and no existing payment date
                    paymentDate = if (isPaid && registeredStudent?.paymentDate == null) 
                                    Date() 
                                  else 
                                    registeredStudent?.paymentDate
                )
            } else {
                // For new registrations, use all provided values
                studentToUpdate.copy(
                    startDate = startDate,
                    endDate = calculatedEndDate ?: endDate,
                    amount = amount,
                    isPaid = isPaid,
                    paymentDate = if (isPaid) Date() else null
                )
            }
            
            // Update the student in the repository
            repository.updateStudent(updatedStudent)
        }
    }

    private fun calculateEndDateBasedOnLessons(startDate: Date): Date? {
        val lessons = _lessonSchedule.value ?: return null
        if (lessons.isEmpty()) return null
        
        val startCalendar = Calendar.getInstance()
        startCalendar.time = startDate
        
        return calendarViewModel.calculateEndDateAfterLessons(startCalendar, 8, lessons)
    }

    fun updateStudent(student: WTStudent) {
        viewModelScope.launch {
            // Get the existing student to ensure we're not losing information
            val existingStudent = _allStudents.value?.find { it.id == student.id }
            
            // If the student exists, we need to be careful to preserve fields that 
            // aren't explicitly being updated
            if (existingStudent != null) {
                // Create a properly merged student object
                val updatedStudent = student.copy(
                    // Keep registration info if not explicitly changed
                    startDate = student.startDate ?: existingStudent.startDate,
                    endDate = if (student.startDate != existingStudent.startDate && student.startDate != null) {
                        // If start date changed, recalculate end date
                        calculateEndDateBasedOnLessons(student.startDate) ?: student.endDate ?: existingStudent.endDate
                    } else {
                        student.endDate ?: existingStudent.endDate
                    },
                    // Keep payment info if not explicitly changed
                    amount = if (student.amount > 0) student.amount else existingStudent.amount,
                    isPaid = student.isPaid,
                    paymentDate = student.paymentDate ?: existingStudent.paymentDate
                )
                
                // Update the student with merged information
                repository.updateStudent(updatedStudent)
            } else {
                // For new students, just use the provided student object
                repository.updateStudent(student)
            }
        }
    }

    fun markAsPaid(student: WTStudent) {
        viewModelScope.launch {
            // Ensure student has a course assigned with an amount
            if (student.startDate == null || student.amount <= 0) {
                _errorMessage.value = "Student must be registered for a course first"
                return@launch
            }
            
            // Update student payment status
            val updatedStudent = student.copy(
                isPaid = true,
                paymentDate = Date()
            )
            repository.updateStudent(updatedStudent)

            // Add transaction record
            val transaction = Transaction(
                id = UUID.randomUUID().mostSignificantBits,
                amount = student.amount,
                type = "Wing Tzun",
                description = "Payment from ${student.name}",
                isIncome = true,
                date = Date(),
                category = "Wing Tzun"
            )
            repository.insertTransaction(
                amount = transaction.amount,
                type = transaction.type,
                description = transaction.description,
                isIncome = transaction.isIncome,
                category = transaction.category
            )
        }
    }

    fun markAsUnpaid(student: WTStudent) {
        viewModelScope.launch {
            // Ensure student has a course assigned
            if (student.startDate == null) {
                _errorMessage.value = "Student must be registered for a course first"
                return@launch
            }
            
            // Update student payment status
            val updatedStudent = student.copy(isPaid = false, paymentDate = null)
            repository.updateStudent(updatedStudent)

            // Add a transaction record that deducts the amount but is NOT an expense
            // Using isIncome=true with a negative amount allows us to reduce the balance
            // without categorizing it as an expense in reports/statistics
            val transaction = Transaction(
                id = UUID.randomUUID().mostSignificantBits,
                amount = -student.amount, // Negative amount
                type = "Wing Tzun",
                description = "Payment reversal for ${student.name} (Not an expense)",
                isIncome = true,  // This ensures it's not counted as an expense
                date = Date(),
                category = "Wing Tzun Adjustment"
            )
            repository.insertTransaction(
                amount = transaction.amount,
                type = transaction.type,
                description = transaction.description,
                isIncome = transaction.isIncome,
                category = transaction.category
            )
        }
    }

    fun deleteStudent(student: WTStudent) {
        viewModelScope.launch {
            repository.deleteStudent(student)
        }
    }
    
    fun refreshData() {
        viewModelScope.launch {
            repository.refreshStudents()
        }
    }

    fun registerStudent(student: WTStudent) {
        viewModelScope.launch {
            try {
                // Check for existing registration with same date
                val existingRegistration = _registeredStudents.value?.find { 
                    it.id == student.id && 
                    it.startDate?.time == student.startDate?.time 
                }
                
                if (existingRegistration != null) {
                    _errorMessage.value = "Student already has a registration for this date"
                    return@launch
                }
                
                // Check for overlapping active registrations
                val hasActiveRegistration = _registeredStudents.value?.any { 
                    it.id == student.id && 
                    !it.isPaid &&
                    it.startDate != null &&
                    it.startDate.time <= (student.startDate?.time ?: 0) &&
                    (it.endDate?.time ?: Long.MAX_VALUE) >= (student.startDate?.time ?: 0)
                } ?: false
                
                if (hasActiveRegistration) {
                    _errorMessage.value = "Student already has an active registration for this period"
                    return@launch
                }
                
                // Proceed with registration
                repository.updateStudent(student)
                _errorMessage.value = null
            } catch (e: Exception) {
                Log.e("WTRegisterViewModel", "Error registering student: ${e.message}", e)
                _errorMessage.value = "Error registering student: ${e.message}"
            }
        }
    }

    // Delete a student's registration
    fun deleteRegistration(student: WTStudent) {
        viewModelScope.launch {
            try {
                // Create a copy of the student with registration data cleared
                val updatedStudent = student.copy(
                    startDate = null,
                    endDate = null,
                    amount = 0.0,
                    isPaid = false,
                    paymentDate = null,
                    attachmentUri = null
                )
                
                // Update the student in the repository
                val success = repository.updateStudent(updatedStudent)
                
                // If the student was paid, add a transaction to reverse the payment
                if (student.isPaid) {
                    val transaction = Transaction(
                        id = UUID.randomUUID().mostSignificantBits,
                        amount = -student.amount, // Negative amount
                        type = "Wing Tzun",
                        description = "Registration deleted for ${student.name} (Payment reversed)",
                        isIncome = true,  // This ensures it's not counted as an expense
                        date = Date(),
                        category = "Wing Tzun Adjustment"
                    )
                    repository.insertTransaction(
                        amount = transaction.amount,
                        type = transaction.type,
                        description = transaction.description,
                        isIncome = transaction.isIncome,
                        category = transaction.category
                    )
                }
                
                // Force refresh the data to ensure UI is updated
                refreshData()
                
                // Give LiveData a moment to propagate changes
                delay(100)
                
                // Check if the student is still in the registered list
                val stillRegistered = _registeredStudents.value?.any { it.id == student.id && it.startDate != null } ?: false
                if (stillRegistered) {
                    // If student is still registered, try once more with a direct deletion
                    Log.d("WTRegisterViewModel", "Student still registered after update, trying direct deletion")
                    repository.deleteStudent(student)
                    repository.insertStudent(updatedStudent)
                    refreshData()
                }
            } catch (e: Exception) {
                Log.e("WTRegisterViewModel", "Error deleting registration: ${e.message}", e)
                _errorMessage.value = "Error deleting registration: ${e.message}"
            }
        }
    }
} 