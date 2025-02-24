package com.example.allinone.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.allinone.data.Transaction
import com.example.allinone.data.TransactionDatabase
import com.example.allinone.data.WTStudent
import kotlinx.coroutines.launch
import java.util.Date

class WTRegisterViewModel(application: Application) : AndroidViewModel(application) {
    private val database = TransactionDatabase.getDatabase(application)
    private val wtStudentDao = database.wtStudentDao()
    private val transactionDao = database.transactionDao()

    val allStudents: LiveData<List<WTStudent>> = wtStudentDao.getAllStudents().asLiveData()
    val unpaidStudents: LiveData<List<WTStudent>> = wtStudentDao.getUnpaidStudents().asLiveData()
    val paidStudents: LiveData<List<WTStudent>> = wtStudentDao.getPaidStudents().asLiveData()

    fun addStudent(name: String, startDate: Date, endDate: Date, amount: Double) {
        viewModelScope.launch {
            val student = WTStudent(
                name = name,
                startDate = startDate,
                endDate = endDate,
                amount = amount
            )
            wtStudentDao.insertStudent(student)
        }
    }

    fun updateStudent(student: WTStudent) {
        viewModelScope.launch {
            wtStudentDao.updateStudent(student)
        }
    }

    fun markAsPaid(student: WTStudent) {
        viewModelScope.launch {
            // Update student payment status
            val updatedStudent = student.copy(
                isPaid = true,
                paymentDate = Date()
            )
            wtStudentDao.updateStudent(updatedStudent)

            // Add transaction record
            val transaction = Transaction(
                amount = student.amount,
                type = "Wing Tzun",
                description = "Payment from ${student.name}",
                isIncome = true,
                date = Date(),
                category = "Wing Tzun"
            )
            transactionDao.insertTransaction(transaction)
        }
    }

    fun markAsUnpaid(student: WTStudent) {
        viewModelScope.launch {
            val updatedStudent = student.copy(isPaid = false, paymentDate = null)
            wtStudentDao.updateStudent(updatedStudent)
        }
    }

    fun deleteStudent(student: WTStudent) {
        viewModelScope.launch {
            wtStudentDao.deleteStudent(student)
        }
    }
} 