package com.example.allinone.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.allinone.data.Investment
import com.example.allinone.databinding.ItemInvestmentBinding
import java.text.SimpleDateFormat
import java.util.Locale

class InvestmentAdapter(
    private val onItemClick: (Investment) -> Unit,
    private val onItemLongClick: (Investment) -> Unit
) : ListAdapter<Investment, InvestmentAdapter.InvestmentViewHolder>(InvestmentDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): InvestmentViewHolder {
        val binding = ItemInvestmentBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return InvestmentViewHolder(
            binding,
            { position -> onItemClick(getItem(position)) },
            { position -> onItemLongClick(getItem(position)) }
        )
    }

    override fun onBindViewHolder(holder: InvestmentViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class InvestmentViewHolder(
        private val binding: ItemInvestmentBinding,
        onItemClick: (Int) -> Unit,
        onItemLongClick: (Int) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

        init {
            binding.root.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(position)
                }
            }
            binding.root.setOnLongClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemLongClick(position)
                }
                true
            }
        }

        fun bind(investment: Investment) {
            binding.apply {
                root.setOnClickListener { onItemClick(investment) }
                nameText.text = investment.name
                typeText.text = investment.type
                dateText.text = dateFormat.format(investment.date)
                amountText.text = String.format("₺%.2f", investment.amount)
                profitLossText.text = String.format("₺%.2f", investment.profitLoss)
                
                profitLossText.setTextColor(
                    if (investment.profitLoss >= 0) {
                        ContextCompat.getColor(root.context, android.R.color.holo_green_dark)
                    } else {
                        ContextCompat.getColor(root.context, android.R.color.holo_red_dark)
                    }
                )

                investment.imageUri?.let { uri ->
                    investmentImage.setImageURI(android.net.Uri.parse(uri))
                }
            }
        }
    }

    private class InvestmentDiffCallback : DiffUtil.ItemCallback<Investment>() {
        override fun areItemsTheSame(oldItem: Investment, newItem: Investment): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Investment, newItem: Investment): Boolean {
            return oldItem == newItem
        }
    }
} 