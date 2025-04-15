package com.example.allinone.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.allinone.databinding.ItemCategorySpendingBinding
import java.text.NumberFormat
import java.util.Locale

data class CategorySpending(
    val category: String,
    val amount: Double,
    val percentage: Double,
    val color: Int
)

class CategorySpendingAdapter : RecyclerView.Adapter<CategorySpendingAdapter.CategoryViewHolder>() {

    private var categories: List<CategorySpending> = emptyList()
    private val currencyFormatter = NumberFormat.getCurrencyInstance().apply {
        maximumFractionDigits = 2
        minimumFractionDigits = 2
    }

    inner class CategoryViewHolder(private val binding: ItemCategorySpendingBinding) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(categorySpending: CategorySpending) {
            binding.categoryNameText.text = categorySpending.category
            binding.categoryAmountText.text = currencyFormatter.format(categorySpending.amount)
            binding.categoryPercentText.text = String.format("%.1f%%", categorySpending.percentage)
            
            // Set the color indicator
            binding.categoryColorIndicator.setBackgroundColor(categorySpending.color)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val binding = ItemCategorySpendingBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return CategoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        holder.bind(categories[position])
    }

    override fun getItemCount(): Int = categories.size

    fun updateCategories(newCategories: List<CategorySpending>) {
        categories = newCategories
        notifyDataSetChanged()
    }
}
