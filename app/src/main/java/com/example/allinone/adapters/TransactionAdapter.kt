package com.example.allinone.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.allinone.R
import com.example.allinone.data.Transaction
import com.example.allinone.databinding.ItemTransactionBinding
import java.text.SimpleDateFormat
import java.util.Locale
import com.example.allinone.utils.NumberFormatUtils

class TransactionAdapter(
    private val onItemClick: (Transaction) -> Unit,
    private val onItemLongClick: (Transaction) -> Unit
) : ListAdapter<Transaction, TransactionAdapter.TransactionViewHolder>(
    TransactionDiffCallback()
) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
        val binding = ItemTransactionBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return TransactionViewHolder(
            binding,
            { position -> onItemClick(getItem(position)) },
            { position -> onItemLongClick(getItem(position)) }
        )
    }

    override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class TransactionViewHolder(
        private val binding: ItemTransactionBinding,
        onItemClick: (Int) -> Unit,
        onItemLongClick: (Int) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(position)
                }
            }
            binding.root.setOnLongClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemLongClick(position)
                }
                true
            }
        }

        fun bind(transaction: Transaction) {
            binding.apply {
                typeText.text = transaction.type
                dateText.text = dateFormat.format(transaction.date)
                amountText.text = NumberFormatUtils.formatAmount(transaction.amount)
                amountText.typeface = ResourcesCompat.getFont(root.context, R.font.opensans)

                // Show description if available
                if (transaction.description.isNullOrEmpty()) {
                    descriptionText.visibility = View.GONE
                } else {
                    descriptionText.text = transaction.description
                    descriptionText.visibility = View.VISIBLE
                }

                val textColor = if (transaction.isIncome) {
                    ContextCompat.getColor(root.context, android.R.color.holo_green_dark)
                } else {
                    ContextCompat.getColor(root.context, android.R.color.holo_red_dark)
                }
                amountText.setTextColor(textColor)
            }
        }
    }

    private class TransactionDiffCallback : DiffUtil.ItemCallback<Transaction>() {
        override fun areItemsTheSame(oldItem: Transaction, newItem: Transaction): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Transaction, newItem: Transaction): Boolean {
            return oldItem == newItem
        }
    }
}