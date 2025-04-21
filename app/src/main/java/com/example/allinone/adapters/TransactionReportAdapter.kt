package com.example.allinone.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.allinone.R
import com.example.allinone.data.Transaction
import com.example.allinone.databinding.ItemTransactionReportBinding
import java.text.SimpleDateFormat
import java.util.Locale
import com.example.allinone.utils.NumberFormatUtils

class TransactionReportAdapter : RecyclerView.Adapter<TransactionReportAdapter.TransactionViewHolder>() {

    private var transactions: List<Transaction> = emptyList()
    private val dateFormatter = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

    inner class TransactionViewHolder(private val binding: ItemTransactionReportBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(transaction: Transaction) {
            // Set title (using type or category)
            binding.transactionTitle.text = transaction.type.ifEmpty {
                transaction.category.ifEmpty { if (transaction.isIncome) "Income" else "Expense" }
            }

            // Set description
            binding.transactionDescription.text = transaction.description.ifEmpty {
                if (transaction.isIncome) "Income transaction" else "Expense transaction"
            }

            // Set date
            binding.transactionDate.text = dateFormatter.format(transaction.date)

            // Set amount with proper formatting and color
            val amountText = NumberFormatUtils.formatAmount(transaction.amount)
            binding.transactionAmount.text = amountText
            binding.transactionAmount.setTextColor(
                if (transaction.isIncome)
                    ContextCompat.getColor(binding.root.context, R.color.green)
                else
                    ContextCompat.getColor(binding.root.context, R.color.red)
            )

            // Set category text
            val categoryText = if (transaction.category.isEmpty()) {
                if (transaction.isIncome) "Uncategorized" else "Uncategorized"
            } else {
                transaction.category
            }
            binding.categoryChip.text = categoryText

            // Set color indicator based on transaction type
            val indicatorColor = if (transaction.isIncome) {
                ContextCompat.getColor(binding.root.context, R.color.green)
            } else {
                ContextCompat.getColor(binding.root.context, R.color.red)
            }
            binding.categoryColorIndicator.setBackgroundColor(indicatorColor)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
        val binding = ItemTransactionReportBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return TransactionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) {
        holder.bind(transactions[position])
    }

    override fun getItemCount(): Int = transactions.size

    fun updateTransactions(newTransactions: List<Transaction>) {
        transactions = newTransactions
        notifyDataSetChanged()
    }
}