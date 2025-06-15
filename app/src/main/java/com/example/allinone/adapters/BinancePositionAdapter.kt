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
import com.example.allinone.data.BinancePosition
import com.example.allinone.utils.NumberFormatUtils
import java.util.Locale
import kotlin.math.abs

class BinancePositionAdapter(
    private val onItemClick: (BinancePosition) -> Unit,
    private val onTpSlClick: (BinancePosition) -> Unit
) : ListAdapter<BinancePosition, BinancePositionAdapter.PositionViewHolder>(PositionDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PositionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_binance_position, parent, false)
        return PositionViewHolder(view)
    }

    override fun onBindViewHolder(holder: PositionViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class PositionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val symbolText: TextView = itemView.findViewById(R.id.symbolText)
        private val leverageText: TextView = itemView.findViewById(R.id.leverageText)
        private val pnlValue: TextView = itemView.findViewById(R.id.pnlValue)
        private val roiValue: TextView = itemView.findViewById(R.id.roiValue)
        private val sizeLabel: TextView = itemView.findViewById(R.id.sizeLabel)
        private val sizeValue: TextView = itemView.findViewById(R.id.sizeValue)
        private val marginValue: TextView = itemView.findViewById(R.id.marginValue)
        private val entryPriceValue: TextView = itemView.findViewById(R.id.entryPriceValue)
        private val markPriceValue: TextView = itemView.findViewById(R.id.markPriceValue)
        private val liqPriceValue: TextView = itemView.findViewById(R.id.liqPriceValue)
        private val tpslValue: TextView = itemView.findViewById(R.id.tpslValue)

        init {
            itemView.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(getItem(position))
                }
            }

            tpslValue.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onTpSlClick(getItem(position))
                }
            }
        }

        fun bind(position: BinancePosition) {
            // Set symbol and leverage
            symbolText.text = position.symbol
            leverageText.text = "Perp ${position.marginType} ${position.leverage}x"

            // Set PNL with color
            val pnl = position.unrealizedProfit
            val pnlColor = if (pnl >= 0) Color.parseColor("#4CAF50") else Color.parseColor("#FF5252")
            pnlValue.text = NumberFormatUtils.formatDecimal(pnl)
            pnlValue.setTextColor(pnlColor)

            // Calculate and set ROI with color
            val roi = calculateROI(position)
            val roiText = if (roi.isNaN() || roi.isInfinite()) {
                "0.00%" // Default value for invalid ROI
            } else {
                String.format(Locale.US, "%.2f%%", roi)
            }
            val roiColor = if (roi >= 0) Color.parseColor("#4CAF50") else Color.parseColor("#FF5252")
            roiValue.text = roiText
            roiValue.setTextColor(roiColor)

            // Get base asset name
            val baseAsset = position.symbol.replace("USDT", "")

            // Set size value with asset name
            sizeValue.text = formatPositionAmount(position.positionAmt) + " " + baseAsset

            // Set margin value with USDT
            marginValue.text = NumberFormatUtils.formatDecimal(position.isolatedMargin) + " USDT"

            // Set prices
            val entryPrice = position.entryPrice
            val markPrice = position.markPrice
            val liqPrice = position.liquidationPrice

            // Format prices based on value
            entryPriceValue.text = formatPrice(entryPrice)
            markPriceValue.text = formatPrice(markPrice)
            liqPriceValue.text = formatPrice(liqPrice)

            // Set TP/SL values
            val tpPrice = position.takeProfitPrice
            val slPrice = position.stopLossPrice

            val tpText = if (tpPrice > 0) formatPrice(tpPrice) else "--"
            val slText = if (slPrice > 0) formatPrice(slPrice) else "--"

            tpslValue.text = "$tpText / $slText"
        }

        private fun formatPositionAmount(amount: Double): String {
            return if (abs(amount) < 0.001) {
                String.format(Locale.US, "%.7f", amount)
            } else if (abs(amount) < 1) {
                String.format(Locale.US, "%.4f", amount)
            } else {
                String.format(Locale.US, "%.3f", amount)
            }
        }

        private fun formatPrice(price: Double): String {
            return if (price >= 1.0) {
                String.format(Locale.US, "%.2f", price)
            } else if (price > 0) {
                String.format(Locale.US, "%.7f", price)
            } else {
                "--"
            }
        }

        /**
         * Calculate ROI (Return on Investment) for a futures position
         * ROI = (Unrealized Profit / Initial Margin) × 100
         */
        private fun calculateROI(position: BinancePosition): Double {
            return try {
                // If position amount is 0, no ROI
                if (position.positionAmt == 0.0) return 0.0
                
                // Calculate initial margin: (Position Size × Entry Price) / Leverage
                val positionValue = abs(position.positionAmt) * position.entryPrice
                val initialMargin = positionValue / position.leverage
                
                // Avoid division by zero
                if (initialMargin == 0.0) return 0.0
                
                // Calculate ROI as percentage
                val roi = (position.unrealizedProfit / initialMargin) * 100
                
                // Return the calculated ROI
                roi
            } catch (e: Exception) {
                // Return 0 if calculation fails
                0.0
            }
        }
    }

    private class PositionDiffCallback : DiffUtil.ItemCallback<BinancePosition>() {
        override fun areItemsTheSame(oldItem: BinancePosition, newItem: BinancePosition): Boolean {
            return oldItem.symbol == newItem.symbol
        }

        override fun areContentsTheSame(oldItem: BinancePosition, newItem: BinancePosition): Boolean {
            return oldItem == newItem
        }
    }
}
