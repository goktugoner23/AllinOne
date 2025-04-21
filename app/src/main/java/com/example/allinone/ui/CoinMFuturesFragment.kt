package com.example.allinone.ui

import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.allinone.R
import com.example.allinone.adapters.BinanceFuturesAdapter
import com.example.allinone.api.CoinMFuturesApiClient
import com.example.allinone.data.BinanceBalance
import com.example.allinone.data.BinanceFutures
import com.example.allinone.data.BinanceOrder
import com.example.allinone.databinding.DialogFuturesTpSlBinding
import com.example.allinone.databinding.FragmentFuturesTabBinding
import com.example.allinone.viewmodels.InvestmentsViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.Locale

class CoinMFuturesFragment : Fragment() {
    private var _binding: FragmentFuturesTabBinding? = null
    private val binding get() = _binding!!
    private val viewModel: InvestmentsViewModel by viewModels({ requireParentFragment().requireParentFragment() })

    private lateinit var coinMFuturesApiClient: CoinMFuturesApiClient
    private lateinit var futuresAdapter: BinanceFuturesAdapter
    private var openOrders: List<BinanceOrder> = emptyList()

    private val currencyFormatter = NumberFormat.getCurrencyInstance(Locale.US).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2  // Only show 2 decimal places for USDT values
    }
    // Don't use currency formatter for prices to show exact values from Binance
    private val priceFormatter = NumberFormat.getInstance(Locale.US).apply {
        minimumFractionDigits = 0
        maximumFractionDigits = 20  // Allow for maximum precision
        isGroupingUsed = true  // Keep the thousands separator
    }
    private val numberFormatter = NumberFormat.getNumberInstance(Locale.US).apply {
        maximumFractionDigits = 8
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFuturesTabBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize Binance COIN-M Futures API client
        coinMFuturesApiClient = CoinMFuturesApiClient()

        // Setup RecyclerView and adapter
        setupRecyclerView()

        // Setup swipe refresh
        setupSwipeRefresh()

        // Set initial UI state
        showLoading(true)

        // Force an initial refresh
        refreshData()
    }

    private fun setupRecyclerView() {
        futuresAdapter = BinanceFuturesAdapter(
            onItemClick = { position ->
                // Show TP/SL dialog when position card is clicked
                showTpSlDialog(position)
            },
            // We'll update the prices later when they're fetched
            prices = emptyMap()
        )

        binding.positionsRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = futuresAdapter
        }
    }

    private fun showTpSlDialog(position: BinanceFutures) {
        val dialogBinding = DialogFuturesTpSlBinding.inflate(layoutInflater)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .setCancelable(true)
            .create()

        // Set position details
        dialogBinding.positionSymbolText.text = position.symbol

        // Format position details text
        val positionSide = if (position.positionAmt > 0) "LONG" else "SHORT"
        val formattedEntryPrice = formatPrice(position.entryPrice)
        val formattedMarkPrice = formatPrice(position.markPrice)
        dialogBinding.positionDetailsText.text = "$positionSide | Size: ${Math.abs(position.positionAmt)} | Entry: $formattedEntryPrice | Mark: $formattedMarkPrice"

        // Find existing TP/SL orders for this position
        val isLong = position.positionAmt > 0
        val expectedSide = if (isLong) "SELL" else "BUY" // TP/SL orders are opposite to position side

        // Filter orders for this symbol and side
        val positionOrders = openOrders.filter { it.symbol == position.symbol && it.side == expectedSide }

        // Find TP order (TAKE_PROFIT_MARKET)
        val tpOrder = positionOrders.find { it.type == "TAKE_PROFIT_MARKET" }

        // Find SL order (STOP_MARKET)
        val slOrder = positionOrders.find { it.type == "STOP_MARKET" }

        Log.d("CoinMFuturesFragment", "Found TP order: $tpOrder, SL order: $slOrder for ${position.symbol}")

        // Only set values from existing orders, don't set defaults
        if (tpOrder != null && tpOrder.stopPrice > 0) {
            dialogBinding.takeProfitInput.setText(formatPrice(tpOrder.stopPrice))
        } else {
            // Leave empty if no existing TP order
            dialogBinding.takeProfitInput.setText("")
        }

        if (slOrder != null && slOrder.stopPrice > 0) {
            dialogBinding.stopLossInput.setText(formatPrice(slOrder.stopPrice))
        } else {
            // Leave empty if no existing SL order
            dialogBinding.stopLossInput.setText("")
        }

        // Add validation for TP/SL inputs
        dialogBinding.takeProfitInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                validateTpSlInputs(position, dialogBinding)
            }
        })

        dialogBinding.stopLossInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                validateTpSlInputs(position, dialogBinding)
            }
        })

        // Set up button click listeners
        dialogBinding.confirmButton.setOnClickListener {
            // Get TP/SL values
            val tpPriceStr = dialogBinding.takeProfitInput.text.toString().replace(',', '.')
            val slPriceStr = dialogBinding.stopLossInput.text.toString().replace(',', '.')
            val tpPrice = tpPriceStr.toDoubleOrNull()
            val slPrice = slPriceStr.toDoubleOrNull()

            // Find existing TP/SL orders for this position
            val existingTpOrder = openOrders.find { it.symbol == position.symbol && it.type == "TAKE_PROFIT_MARKET" }
            val existingSlOrder = openOrders.find { it.symbol == position.symbol && it.type == "STOP_MARKET" }

            // Check if at least one valid price is provided or if we're deleting an existing order
            val hasTp = tpPriceStr.isNotEmpty() && tpPrice != null
            val hasSl = slPriceStr.isNotEmpty() && slPrice != null
            val deletingTp = tpPriceStr.isEmpty() && existingTpOrder != null
            val deletingSl = slPriceStr.isEmpty() && existingSlOrder != null

            if (hasTp || hasSl || deletingTp || deletingSl) {
                // Show loading state
                dialogBinding.confirmButton.isEnabled = false
                dialogBinding.confirmButton.text = "Setting TP/SL..."

                // Place TP/SL orders
                placeTpSlOrders(position, if (hasTp) tpPrice!! else null, if (hasSl) slPrice!! else null, dialog)
            } else {
                Toast.makeText(context, "Please enter at least one valid price or clear a field to delete an existing order", Toast.LENGTH_SHORT).show()
            }
        }

        dialogBinding.closePositionButton.setOnClickListener {
            // Show confirmation dialog for closing position
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Close Position")
                .setMessage("Are you sure you want to close your ${position.symbol} position?")
                .setPositiveButton("Yes") { _, _ ->
                    // Show loading state
                    dialogBinding.closePositionButton.isEnabled = false
                    dialogBinding.closePositionButton.text = "Closing..."

                    // Close the position
                    closePosition(position, dialog)
                }
                .setNegativeButton("No", null)
                .show()
        }

        dialogBinding.cancelButton.setOnClickListener {
            dialog.dismiss()
        }

        // Show the dialog
        dialog.show()
    }

    private fun validateTpSlInputs(position: BinanceFutures, dialogBinding: DialogFuturesTpSlBinding) {
        val tpPriceStr = dialogBinding.takeProfitInput.text.toString().replace(',', '.')
        val slPriceStr = dialogBinding.stopLossInput.text.toString().replace(',', '.')
        val tpPrice = tpPriceStr.toDoubleOrNull()
        val slPrice = slPriceStr.toDoubleOrNull()

        // Find existing TP/SL orders for this position
        val existingTpOrder = openOrders.find { it.symbol == position.symbol && it.type == "TAKE_PROFIT_MARKET" }
        val existingSlOrder = openOrders.find { it.symbol == position.symbol && it.type == "STOP_MARKET" }

        // Allow empty fields - only validate if values are provided
        val isLong = position.positionAmt > 0
        var isTpValid = true
        var isSlValid = true

        // Clear previous errors
        dialogBinding.takeProfitLayout.error = null
        dialogBinding.stopLossLayout.error = null

        // Validate TP if provided
        if (tpPriceStr.isNotEmpty()) {
            if (tpPrice == null) {
                dialogBinding.takeProfitLayout.error = "Invalid price format"
                isTpValid = false
            } else {
                // For LONG positions: TP should be above current price
                // For SHORT positions: TP should be below current price
                isTpValid = if (isLong) tpPrice > position.markPrice else tpPrice < position.markPrice
                if (!isTpValid) {
                    dialogBinding.takeProfitLayout.error = if (isLong) "TP must be above current price" else "TP must be below current price"
                }
            }
        }

        // Validate SL if provided
        if (slPriceStr.isNotEmpty()) {
            if (slPrice == null) {
                dialogBinding.stopLossLayout.error = "Invalid price format"
                isSlValid = false
            } else {
                // For LONG positions: SL should be below current price
                // For SHORT positions: SL should be above current price
                isSlValid = if (isLong) slPrice < position.markPrice else slPrice > position.markPrice
                if (!isSlValid) {
                    dialogBinding.stopLossLayout.error = if (isLong) "SL must be below current price" else "SL must be above current price"
                }
            }
        }

        // Enable confirm button if at least one valid value is provided or if we're deleting an existing order
        val hasValidInput = (tpPriceStr.isNotEmpty() && isTpValid) || (slPriceStr.isNotEmpty() && isSlValid) ||
                           (tpPriceStr.isEmpty() && existingTpOrder != null) || (slPriceStr.isEmpty() && existingSlOrder != null)
        dialogBinding.confirmButton.isEnabled = hasValidInput
    }

    private fun placeTpSlOrders(position: BinanceFutures, tpPrice: Double?, slPrice: Double?, dialog: Dialog) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // Determine the side for TP/SL orders (opposite of position side)
                val side = if (position.positionAmt > 0) "SELL" else "BUY"
                val quantity = Math.abs(position.positionAmt)

                var tpResult = ""
                var slResult = ""
                var hasError = false

                // Find existing TP/SL orders for this position
                val existingTpOrder = openOrders.find { it.symbol == position.symbol && it.type == "TAKE_PROFIT_MARKET" }
                val existingSlOrder = openOrders.find { it.symbol == position.symbol && it.type == "STOP_MARKET" }

                // Handle Take Profit order
                if (tpPrice != null) {
                    // If there's an existing TP order, cancel it first
                    if (existingTpOrder != null) {
                        val cancelResult = coinMFuturesApiClient.cancelOrder(position.symbol, existingTpOrder.orderId)
                        Log.d("CoinMFuturesFragment", "Cancel TP Order Result: $cancelResult")
                        if (cancelResult.contains("error")) {
                            hasError = true
                            tpResult = cancelResult
                        }
                    }

                    // Place new TP order if no error occurred during cancellation
                    if (!hasError) {
                        tpResult = coinMFuturesApiClient.placeTakeProfitMarketOrder(
                            symbol = position.symbol,
                            side = side,
                            quantity = quantity,
                            stopPrice = tpPrice
                        )
                        Log.d("CoinMFuturesFragment", "TP Order Result: $tpResult")
                        Log.d("CoinMFuturesFragment", "TP Order Query: symbol=${position.symbol}&side=$side&type=TAKE_PROFIT_MARKET&quantity=$quantity&stopPrice=$tpPrice&closePosition=true&workingType=CONTRACT_PRICE&timeInForce=GTE_GTC")
                        hasError = hasError || tpResult.contains("error")
                    }
                } else if (existingTpOrder != null) {
                    // If TP price is not provided but there's an existing TP order, cancel it
                    val cancelResult = coinMFuturesApiClient.cancelOrder(position.symbol, existingTpOrder.orderId)
                    Log.d("CoinMFuturesFragment", "Cancel TP Order Result: $cancelResult")
                    if (cancelResult.contains("error")) {
                        hasError = true
                        tpResult = cancelResult
                    }
                }

                // Handle Stop Loss order
                if (slPrice != null && !hasError) {
                    // If there's an existing SL order, cancel it first
                    if (existingSlOrder != null) {
                        val cancelResult = coinMFuturesApiClient.cancelOrder(position.symbol, existingSlOrder.orderId)
                        Log.d("CoinMFuturesFragment", "Cancel SL Order Result: $cancelResult")
                        if (cancelResult.contains("error")) {
                            hasError = true
                            slResult = cancelResult
                        }
                    }

                    // Place new SL order if no error occurred during cancellation
                    if (!hasError) {
                        slResult = coinMFuturesApiClient.placeStopLossMarketOrder(
                            symbol = position.symbol,
                            side = side,
                            quantity = quantity,
                            stopPrice = slPrice
                        )
                        Log.d("CoinMFuturesFragment", "SL Order Result: $slResult")
                        Log.d("CoinMFuturesFragment", "SL Order Query: symbol=${position.symbol}&side=$side&type=STOP_MARKET&quantity=$quantity&stopPrice=$slPrice&closePosition=true&workingType=MARK_PRICE&timeInForce=GTE_GTC")
                        hasError = hasError || slResult.contains("error")
                    }
                } else if (existingSlOrder != null && !hasError) {
                    // If SL price is not provided but there's an existing SL order, cancel it
                    val cancelResult = coinMFuturesApiClient.cancelOrder(position.symbol, existingSlOrder.orderId)
                    Log.d("CoinMFuturesFragment", "Cancel SL Order Result: $cancelResult")
                    if (cancelResult.contains("error")) {
                        hasError = true
                        slResult = cancelResult
                    }
                }

                // Check for errors
                if (hasError) {
                    withContext(Dispatchers.Main) {
                        val errorMsg = when {
                            tpResult.contains("error") -> tpResult
                            slResult.contains("error") -> slResult
                            else -> "Unknown error"
                        }
                        Toast.makeText(context, "Error setting TP/SL: $errorMsg", Toast.LENGTH_LONG).show()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        // Use the existing TP/SL order flags from before the operation
                        val hadTpOrder = existingTpOrder != null
                        val hadSlOrder = existingSlOrder != null

                        val message = when {
                            // Setting both TP and SL
                            tpPrice != null && slPrice != null -> "TP/SL orders placed successfully"
                            // Setting TP only
                            tpPrice != null && !hadSlOrder -> "Take Profit order placed successfully"
                            // Setting TP and deleting SL
                            tpPrice != null && hadSlOrder -> "Take Profit set, Stop Loss removed"
                            // Setting SL only
                            slPrice != null && !hadTpOrder -> "Stop Loss order placed successfully"
                            // Setting SL and deleting TP
                            slPrice != null && hadTpOrder -> "Stop Loss set, Take Profit removed"
                            // Deleting TP only
                            tpPrice == null && slPrice == null && hadTpOrder && !hadSlOrder -> "Take Profit order removed"
                            // Deleting SL only
                            tpPrice == null && slPrice == null && !hadTpOrder && hadSlOrder -> "Stop Loss order removed"
                            // Deleting both TP and SL
                            tpPrice == null && slPrice == null && hadTpOrder && hadSlOrder -> "TP/SL orders removed"
                            // No changes
                            else -> "No changes made"
                        }
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                        // Refresh data to show updated positions
                        refreshData()
                    }
                }
            } catch (e: Exception) {
                Log.e("CoinMFuturesFragment", "Error placing TP/SL orders", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun closePosition(position: BinanceFutures, dialog: Dialog) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val result = coinMFuturesApiClient.closePosition(
                    symbol = position.symbol,
                    positionAmt = position.positionAmt
                )
                Log.d("CoinMFuturesFragment", "Close Position Result: $result")

                // Check for errors
                if (result.contains("error")) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Error closing position: $result", Toast.LENGTH_LONG).show()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Position closed successfully", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                        // Refresh data to show updated positions
                        refreshData()
                    }
                }
            } catch (e: Exception) {
                Log.e("CoinMFuturesFragment", "Error closing position", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun formatPrice(price: Double): String {
        // Use Locale.US to ensure decimal point is a dot, not a comma
        return if (price >= 1.0) {
            // For prices >= 1, show 2 decimal places
            String.format(Locale.US, "%.2f", price)
        } else {
            // For prices < 1, show up to 7 decimal places
            String.format(Locale.US, "%.7f", price)
        }
    }

    private fun setupSwipeRefresh() {
        binding.futuresSwipeRefreshLayout.setOnRefreshListener {
            // Add a toast to indicate refresh is happening
            Toast.makeText(context, "Refreshing COIN-M futures data...", Toast.LENGTH_SHORT).show()
            Log.d("CoinMFuturesFragment", "Pull-to-refresh triggered")
            refreshData()
        }
        binding.futuresSwipeRefreshLayout.setColorSchemeResources(
            R.color.colorPrimary,
            R.color.colorAccent,
            R.color.colorPrimaryDark
        )
    }

    private fun showLoading(isLoading: Boolean) {
        if (_binding == null) return

        if (isLoading) {
            binding.loadingProgress.visibility = View.VISIBLE
            binding.emptyStateText.visibility = View.GONE
        } else {
            binding.loadingProgress.visibility = View.GONE
        }
    }

    private fun refreshData() {
        // Safely check if binding is still valid before starting operation
        if (_binding == null) return
        binding.futuresSwipeRefreshLayout.isRefreshing = true
        showLoading(true)

        // Use viewLifecycleOwner.lifecycleScope instead of lifecycleScope to tie to view lifecycle
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // Fetch Binance COIN-M Futures data
                Log.d("CoinMFuturesFragment", "Fetching COIN-M account balance...")
                val balances = coinMFuturesApiClient.getAccountBalance()
                Log.d("CoinMFuturesFragment", "Fetched ${balances.size} balances")

                Log.d("CoinMFuturesFragment", "Fetching COIN-M position information...")
                val positions = coinMFuturesApiClient.getPositionInformation()
                Log.d("CoinMFuturesFragment", "Fetched ${positions.size} positions")

                Log.d("CoinMFuturesFragment", "Fetching COIN-M latest prices...")
                val prices = coinMFuturesApiClient.getLatestPrices()
                Log.d("CoinMFuturesFragment", "Fetched ${prices.size} prices")

                // Fetch open orders
                Log.d("CoinMFuturesFragment", "Fetching COIN-M open orders...")
                openOrders = coinMFuturesApiClient.getOpenOrders()
                Log.d("CoinMFuturesFragment", "Fetched ${openOrders.size} open orders")

                // Update UI with the fetched data
                updateUI(balances, positions, prices)

                // Add a small delay to make the refresh animation visible
                delay(500)

                // Check if binding is still valid after async operation
                if (_binding != null) {
                    binding.futuresSwipeRefreshLayout.isRefreshing = false
                    showLoading(false)
                    Log.d("CoinMFuturesFragment", "Refreshed data successfully")
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Job cancellation is normal when leaving the fragment, no need to show error
                Log.d("CoinMFuturesFragment", "Refresh job was cancelled")
            } catch (e: Exception) {
                Log.e("CoinMFuturesFragment", "Error refreshing data: ${e.message}")
                // Check if binding is still valid after async operation
                if (_binding != null) {
                    binding.futuresSwipeRefreshLayout.isRefreshing = false
                    showLoading(false)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Error refreshing data: ${e.message}", Toast.LENGTH_SHORT).show()
                        binding.emptyStateText.visibility = View.VISIBLE
                        binding.emptyStateText.text = "Error loading data: ${e.message}"
                    }
                }
            }
        }
    }

    private suspend fun updateUI(balances: List<BinanceBalance>, positions: List<BinanceFutures>, prices: Map<String, Double>) {
        if (_binding == null) return

        withContext(Dispatchers.Main) {
            // Filter for COIN-M futures only
            val coinMPositions = positions.filter { it.futuresType == "COIN-M" }

            // Create a new adapter with the latest prices and open orders
            futuresAdapter = BinanceFuturesAdapter(
                onItemClick = { position ->
                    // Show TP/SL dialog when position card is clicked
                    showTpSlDialog(position)
                },
                prices = prices,
                openOrders = openOrders
            )

            // Set the new adapter
            binding.positionsRecyclerView.adapter = futuresAdapter

            // Update positions list
            futuresAdapter.submitList(coinMPositions)

            // Show empty state if no positions
            binding.emptyStateText.visibility = if (coinMPositions.isEmpty()) View.VISIBLE else View.GONE
            if (coinMPositions.isEmpty()) {
                binding.emptyStateText.text = "No open COIN-M futures positions"
            }

            // Log all balances for debugging
            Log.d("CoinMFuturesFragment", "COIN-M Balances: ${balances.joinToString { "${it.asset}=${it.balance}" }}")

            // Calculate total unrealized PNL in USDT
            var totalPnlUsdt = 0.0

            // Calculate PNL in USDT for each position
            for (position in coinMPositions) {
                val symbol = position.symbol
                val baseAsset = symbol.split("_").firstOrNull()?.replace("USD", "") ?: continue
                val price = prices[baseAsset] ?: 0.0
                val pnlUsdt = position.unRealizedProfit * price
                Log.d("CoinMFuturesFragment", "PNL for $symbol: ${position.unRealizedProfit} $baseAsset * $price = $pnlUsdt USDT")
                totalPnlUsdt += pnlUsdt
            }

            // Calculate total USDT value of all balances
            var totalUsdtValue = 0.0

            // Log all prices for debugging
            Log.d("CoinMFuturesFragment", "Available prices: ${prices.keys.joinToString()}")

            // Calculate USDT value for each non-zero balance
            for (balance in balances) {
                if (balance.balance > 0) {
                    val asset = balance.asset
                    val price = prices[asset] ?: 0.0
                    val usdtValue = balance.balance * price
                    Log.d("CoinMFuturesFragment", "$asset: ${balance.balance} * $price = $usdtValue USDT")
                    totalUsdtValue += usdtValue
                }
            }

            // Calculate margin balance as (wallet balance + unrealized PNL) in USDT
            // This is the correct formula used by Binance
            val marginBalanceUsdt = totalUsdtValue + totalPnlUsdt

            // Update the UI with the correct balance information
            // 1. At the top: Margin Balance
            binding.balanceValueText.text = currencyFormatter.format(marginBalanceUsdt)
            Log.d("CoinMFuturesFragment", "Setting top balance text to margin balance: ${currencyFormatter.format(marginBalanceUsdt)}")

            // 2. Below: Wallet Balance (total balance)
            binding.marginBalanceValueText.text = currencyFormatter.format(totalUsdtValue)
            Log.d("CoinMFuturesFragment", "Setting margin balance text to wallet balance: ${currencyFormatter.format(totalUsdtValue)}")

            // Update PNL with color (using USDT value)
            binding.pnlValueText.text = if (totalPnlUsdt >= 0) "+${currencyFormatter.format(totalPnlUsdt)}"
                                       else currencyFormatter.format(totalPnlUsdt)
            binding.pnlValueText.setTextColor(
                if (totalPnlUsdt >= 0) Color.parseColor("#4CAF50") // Green
                else Color.parseColor("#F44336") // Red
            )
            Log.d("CoinMFuturesFragment", "Setting PNL text to USDT value: ${currencyFormatter.format(totalPnlUsdt)}")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
