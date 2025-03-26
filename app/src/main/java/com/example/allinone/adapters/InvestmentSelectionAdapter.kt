package com.example.allinone.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.allinone.data.Investment
import com.example.allinone.databinding.ItemInvestmentSelectionBinding
import java.text.NumberFormat
import java.util.Locale

class InvestmentSelectionAdapter(
    private val onItemClick: (Investment) -> Unit
) : ListAdapter<Investment, InvestmentSelectionAdapter.InvestmentViewHolder>(InvestmentDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): InvestmentViewHolder {
        val binding = ItemInvestmentSelectionBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return InvestmentViewHolder(binding)
    }

    override fun onBindViewHolder(holder: InvestmentViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class InvestmentViewHolder(
        private val binding: ItemInvestmentSelectionBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(getItem(position))
                }
            }
        }

        fun bind(investment: Investment) {
            val currencyFormat = NumberFormat.getCurrencyInstance(Locale("tr", "TR"))
            
            binding.investmentName.text = investment.name
            binding.investmentType.text = investment.type
            binding.investmentAmount.text = currencyFormat.format(investment.amount)
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