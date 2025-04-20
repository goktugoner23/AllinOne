package com.example.allinone.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.allinone.R
import com.example.allinone.data.BinanceFutures
import java.text.NumberFormat
import java.util.Locale

class BinanceFuturesAdapter(
    private val onItemClick: (BinanceFutures) -> Unit
) : ListAdapter<BinanceFutures, BinanceFuturesAdapter.FuturesViewHolder>(FuturesDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FuturesViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_futures_position, parent, false)
        return FuturesViewHolder(view, onItemClick)
    }

    override fun onBindViewHolder(holder: FuturesViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class FuturesViewHolder(
        itemView: View,
        private val onItemClick: (BinanceFutures) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val symbolText: TextView = itemView.findViewById(R.id.symbolText)
        private val positionSideText: TextView = itemView.findViewById(R.id.positionSideText)
        private val pnlText: TextView = itemView.findViewById(R.id.pnlText)
        private val positionAmtText: TextView = itemView.findViewById(R.id.positionAmtText)
        private val leverageText: TextView = itemView.findViewById(R.id.leverageText)
        private val entryPriceText: TextView = itemView.findViewById(R.id.entryPriceText)
        private val markPriceText: TextView = itemView.findViewById(R.id.markPriceText)
        private val liquidationPriceText: TextView = itemView.findViewById(R.id.liquidationPriceText)
        private val marginTypeText: TextView = itemView.findViewById(R.id.marginTypeText)

        private val currencyFormatter = NumberFormat.getCurrencyInstance(Locale.US)
        private val numberFormatter = NumberFormat.getNumberInstance(Locale.US).apply {
            maximumFractionDigits = 8
        }

        fun bind(position: BinanceFutures) {
            itemView.setOnClickListener { onItemClick(position) }

            symbolText.text = position.symbol

            // Set position side (LONG/SHORT)
            val isLong = position.positionAmt > 0
            positionSideText.text = if (isLong) "LONG" else "SHORT"
            positionSideText.setBackgroundColor(
                if (isLong) Color.parseColor("#4CAF50") // Green
                else Color.parseColor("#F44336") // Red
            )

            // Format PNL with color
            val pnlFormatted = currencyFormatter.format(position.unRealizedProfit)
            pnlText.text = if (position.unRealizedProfit >= 0) "+$pnlFormatted" else pnlFormatted
            pnlText.setTextColor(
                if (position.unRealizedProfit >= 0) Color.parseColor("#4CAF50") // Green
                else Color.parseColor("#F44336") // Red
            )

            // Format position amount
            val baseAsset = position.symbol.replace("USDT", "")
            positionAmtText.text = "${numberFormatter.format(Math.abs(position.positionAmt))} $baseAsset"

            // Set leverage
            leverageText.text = "${position.leverage}x"

            // Format prices
            entryPriceText.text = currencyFormatter.format(position.entryPrice)
            markPriceText.text = currencyFormatter.format(position.markPrice)
            liquidationPriceText.text = currencyFormatter.format(position.liquidationPrice)

            // Set margin type
            marginTypeText.text = position.marginType.capitalize()
        }

        private fun String.capitalize(): String {
            return this.lowercase().replaceFirstChar { 
                if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() 
            }
        }
    }

    class FuturesDiffCallback : DiffUtil.ItemCallback<BinanceFutures>() {
        override fun areItemsTheSame(oldItem: BinanceFutures, newItem: BinanceFutures): Boolean {
            return oldItem.symbol == newItem.symbol && oldItem.positionSide == newItem.positionSide
        }

        override fun areContentsTheSame(oldItem: BinanceFutures, newItem: BinanceFutures): Boolean {
            return oldItem == newItem
        }
    }
}
