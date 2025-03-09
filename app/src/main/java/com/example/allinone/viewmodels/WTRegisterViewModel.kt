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
            
            val updatedStudent = student.copy(
                startDate = startDate,
                endDate = calculatedEndDate ?: endDate, // Use calculated date or fallback
                amount = amount,
                isPaid = isPaid,
                paymentDate = if (isPaid) Date() else null
            )
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
            // If start date was changed and not null, recalculate the end date
            val existingStudent = _allStudents.value?.find { it.id == student.id }
            
            if (existingStudent != null && 
                student.startDate != null && 
                existingStudent.startDate != student.startDate) {
                // Calculate new end date
                val calculatedEndDate = calculateEndDateBasedOnLessons(student.startDate)
                
                if (calculatedEndDate != null) {
                    // Use the calculated end date
                    val updatedStudent = student.copy(endDate = calculatedEndDate)
                    repository.updateStudent(updatedStudent)
                    return@launch
                }
            }
            
            // If no recalculation needed or not possible
            repository.updateStudent(student)
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
} 