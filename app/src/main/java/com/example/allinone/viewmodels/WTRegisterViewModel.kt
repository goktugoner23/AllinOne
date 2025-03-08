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
import com.example.allinone.ui.wt.WTCalendarViewModel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date
import java.util.UUID

class WTRegisterViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = FirebaseRepository(application)
    
    private val _allStudents = MutableLiveData<List<WTStudent>>(emptyList())
    val allStudents: LiveData<List<WTStudent>> = _allStudents
    
    private val _unpaidStudents = MutableLiveData<List<WTStudent>>(emptyList())
    val unpaidStudents: LiveData<List<WTStudent>> = _unpaidStudents
    
    private val _paidStudents = MutableLiveData<List<WTStudent>>(emptyList())
    val paidStudents: LiveData<List<WTStudent>> = _paidStudents
    
    private val _lessonSchedule = MutableLiveData<List<WTLesson>>(emptyList())
    private val calendarViewModel: WTCalendarViewModel = WTCalendarViewModel(application)
    
    // Add isNetworkAvailable property
    val isNetworkAvailable = repository.isNetworkAvailable
    
    // Error message live data
    private val _errorMessage = MutableLiveData<String?>(null)
    val errorMessage: LiveData<String?> = _errorMessage
    
    init {
        // Collect students from the repository flow
        viewModelScope.launch {
            repository.students.collect { students ->
                _allStudents.value = students
                _unpaidStudents.value = students.filter { !it.isPaid }
                _paidStudents.value = students.filter { it.isPaid }
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

    fun addStudent(name: String, startDate: Date, endDate: Date, amount: Double) {
        viewModelScope.launch {
            // Calculate accurate end date based on lesson schedule
            val calculatedEndDate = calculateEndDateBasedOnLessons(startDate)
            
            val student = WTStudent(
                id = UUID.randomUUID().mostSignificantBits,
                name = name,
                startDate = startDate,
                endDate = calculatedEndDate ?: endDate, // Use calculated date or fallback to provided date
                amount = amount
            )
            repository.insertStudent(student)
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
            // If start date was changed, recalculate the end date
            val existingStudent = _allStudents.value?.find { it.id == student.id }
            
            if (existingStudent != null && existingStudent.startDate != student.startDate) {
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
            // Update student payment status
            val updatedStudent = student.copy(isPaid = false, paymentDate = null)
            repository.updateStudent(updatedStudent)

            // Add a negative transaction record to offset the payment
            val transaction = Transaction(
                id = UUID.randomUUID().mostSignificantBits,
                amount = student.amount,
                type = "Wing Tzun",
                description = "Payment reversal for ${student.name}",
                isIncome = false,  // This makes it a deduction
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