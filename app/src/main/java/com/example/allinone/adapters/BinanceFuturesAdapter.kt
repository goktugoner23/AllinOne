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
    private val onItemClick: (BinanceFutures) -> Unit,
    private val prices: Map<String, Double> = emptyMap() // Optional prices map for COIN-M futures
) : ListAdapter<BinanceFutures, BinanceFuturesAdapter.FuturesViewHolder>(FuturesDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FuturesViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_futures_position, parent, false)
        return FuturesViewHolder(view, onItemClick, prices)
    }

    override fun onBindViewHolder(holder: FuturesViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class FuturesViewHolder(
        itemView: View,
        private val onItemClick: (BinanceFutures) -> Unit,
        private val prices: Map<String, Double> = emptyMap() // Optional prices map for COIN-M futures
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

        private val currencyFormatter = NumberFormat.getCurrencyInstance(Locale.US).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2  // Only show 2 decimal places for USDT values
        }
        // Price formatter for prices above 1 USDT (2 digits after decimal)
        private val highPriceFormatter = NumberFormat.getInstance(Locale.US).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2  // Show exactly 2 decimal places for prices above 1 USDT
            isGroupingUsed = true  // Keep the thousands separator
        }

        // Price formatter for prices below 1 USDT (7 digits after decimal)
        private val lowPriceFormatter = NumberFormat.getInstance(Locale.US).apply {
            minimumFractionDigits = 7
            maximumFractionDigits = 7  // Show exactly 7 decimal places for prices below 1 USDT
            isGroupingUsed = true  // Keep the thousands separator
        }
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
            val pnlValue: Double

            // For COIN-M futures, convert PNL to USDT
            if (position.futuresType == "COIN-M" && prices.isNotEmpty()) {
                // Get the base asset from the symbol (e.g., BTCUSD_PERP -> BTC)
                val baseAsset = position.symbol.split("_").firstOrNull()?.replace("USD", "") ?: ""
                val price = prices[baseAsset] ?: 0.0

                // Convert PNL to USDT
                pnlValue = position.unRealizedProfit * price
            } else {
                // For USD-M futures, PNL is already in USDT
                pnlValue = position.unRealizedProfit
            }

            val pnlFormatted = currencyFormatter.format(pnlValue)
            pnlText.text = if (pnlValue >= 0) "+$pnlFormatted" else pnlFormatted
            pnlText.setTextColor(
                if (pnlValue >= 0) Color.parseColor("#4CAF50") // Green
                else Color.parseColor("#F44336") // Red
            )

            // Format position amount
            val baseAsset = position.symbol.replace("USDT", "")
            positionAmtText.text = "${numberFormatter.format(Math.abs(position.positionAmt))} $baseAsset"

            // Set leverage
            leverageText.text = "${position.leverage}x"

            // Display prices with appropriate precision based on price value
            // Use 2 digits after decimal for prices above 1 USDT, 7 digits for prices below 1 USDT
            entryPriceText.text = "$" + (if (position.entryPrice > 1.0) highPriceFormatter.format(position.entryPrice) else lowPriceFormatter.format(position.entryPrice))
            markPriceText.text = "$" + (if (position.markPrice > 1.0) highPriceFormatter.format(position.markPrice) else lowPriceFormatter.format(position.markPrice))
            liquidationPriceText.text = "$" + (if (position.liquidationPrice > 1.0) highPriceFormatter.format(position.liquidationPrice) else lowPriceFormatter.format(position.liquidationPrice))

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
