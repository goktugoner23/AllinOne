package com.example.allinone.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.allinone.data.Transaction
import com.example.allinone.data.WTStudent
import com.example.allinone.firebase.FirebaseRepository
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
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
    
    init {
        // Collect students from the repository flow
        viewModelScope.launch {
            repository.students.collect { students ->
                _allStudents.value = students
                _unpaidStudents.value = students.filter { !it.isPaid }
                _paidStudents.value = students.filter { it.isPaid }
            }
        }
    }

    fun addStudent(name: String, startDate: Date, endDate: Date, amount: Double) {
        viewModelScope.launch {
            val student = WTStudent(
                id = UUID.randomUUID().mostSignificantBits,
                name = name,
                startDate = startDate,
                endDate = endDate,
                amount = amount
            )
            repository.insertStudent(student)
        }
    }

    fun updateStudent(student: WTStudent) {
        viewModelScope.launch {
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
            val updatedStudent = student.copy(isPaid = false, paymentDate = null)
            repository.updateStudent(updatedStudent)
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