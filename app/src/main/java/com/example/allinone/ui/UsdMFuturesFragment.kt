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
import com.example.allinone.adapters.BinancePositionAdapter
import com.example.allinone.data.BinancePosition
import com.example.allinone.api.ExternalBinanceRepository
import com.example.allinone.api.BinanceWebSocketClient
import com.example.allinone.api.PositionData
import com.example.allinone.api.OrderData
import com.example.allinone.data.BinanceFutures
import com.example.allinone.utils.TradingUtils
import com.google.gson.JsonObject
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

class UsdmFuturesFragment : Fragment() {
    private var _binding: FragmentFuturesTabBinding? = null
    private val binding get() = _binding!!
    private val viewModel: InvestmentsViewModel by viewModels({ requireParentFragment().requireParentFragment() })

    private lateinit var externalRepository: ExternalBinanceRepository
    private lateinit var webSocketClient: BinanceWebSocketClient
    private lateinit var futuresAdapter: BinancePositionAdapter
    private var openOrders: List<OrderData> = emptyList()
    private var useExternalService = true // Flag to use external service
    
    companion object {
        private const val TAG = "UsdmFuturesFragment"
        private const val HEARTBEAT_INTERVAL = 30000L // 30 seconds
    }

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

        // Initialize external service
        externalRepository = ExternalBinanceRepository()
        
        // Initialize WebSocket
        initializeWebSocket()

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
        futuresAdapter = BinancePositionAdapter(
            onItemClick = { position ->
                // Show TP/SL dialog when position card is clicked
                showTpSlDialog(position)
            },
            onTpSlClick = { position ->
                // Show TP/SL dialog when TP/SL section is clicked
                showTpSlDialog(position)
            }
        )

        binding.positionsRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = futuresAdapter
        }
    }

    private fun showTpSlDialog(position: BinancePosition) {
        // Convert BinancePosition to BinanceFutures for compatibility with existing dialog
        val binanceFutures = BinanceFutures(
            symbol = position.symbol,
            positionAmt = position.positionAmt,
            entryPrice = position.entryPrice,
            markPrice = position.markPrice,
            unRealizedProfit = position.unrealizedProfit,
            liquidationPrice = position.liquidationPrice,
            leverage = position.leverage,
            marginType = position.marginType,
            isolatedMargin = position.isolatedMargin,
            isAutoAddMargin = false,
            positionSide = if (position.positionAmt >= 0) "LONG" else "SHORT",
            futuresType = "USD-M"
        )

        val dialogBinding = DialogFuturesTpSlBinding.inflate(layoutInflater)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .setCancelable(true)
            .create()

        // Set position details
        dialogBinding.positionSymbolText.text = binanceFutures.symbol

        // Format position details text
        val positionSide = if (binanceFutures.positionAmt > 0) "LONG" else "SHORT"
        val formattedEntryPrice = formatPrice(binanceFutures.entryPrice)
        val formattedMarkPrice = formatPrice(binanceFutures.markPrice)
        dialogBinding.positionDetailsText.text = "$positionSide | Size: ${Math.abs(binanceFutures.positionAmt)} | Entry: $formattedEntryPrice | Mark: $formattedMarkPrice"

        // Find existing TP/SL orders for this position
        val isLong = binanceFutures.positionAmt > 0
        val expectedSide = if (isLong) "SELL" else "BUY" // TP/SL orders are opposite to position side

        // Filter orders for this symbol and side
        val positionOrders = openOrders.filter { it.symbol == binanceFutures.symbol && it.side == expectedSide }

        // Find TP order (TAKE_PROFIT_MARKET)
        val tpOrder = positionOrders.find { it.type == "TAKE_PROFIT_MARKET" }

        // Find SL order (STOP_MARKET)
        val slOrder = positionOrders.find { it.type == "STOP_MARKET" }

        Log.d("UsdmFuturesFragment", "Found TP order: $tpOrder, SL order: $slOrder for ${binanceFutures.symbol}")

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
                placeTpSlOrders(binanceFutures, if (hasTp) tpPrice!! else null, if (hasSl) slPrice!! else null, dialog)
            } else {
                Toast.makeText(context, "Please enter at least one valid price or clear a field to delete an existing order", Toast.LENGTH_SHORT).show()
            }
        }

        dialogBinding.closePositionButton.setOnClickListener {
            // Show confirmation dialog for closing position
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Close Position")
                .setMessage("Are you sure you want to close your ${binanceFutures.symbol} position?")
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

    private fun validateTpSlInputs(position: BinancePosition, dialogBinding: DialogFuturesTpSlBinding) {
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
                // Convert BinanceFutures to BinancePosition for integration guide compatibility
                val binancePosition = BinancePosition(
                    symbol = position.symbol,
                    positionAmt = position.positionAmt,
                    entryPrice = position.entryPrice,
                    markPrice = position.markPrice,
                    unrealizedProfit = position.unRealizedProfit,
                    liquidationPrice = position.liquidationPrice,
                    leverage = position.leverage,
                    marginType = position.marginType,
                    isolatedMargin = position.isolatedMargin,
                    roe = 0.0,
                    takeProfitPrice = tpPrice ?: 0.0,
                    stopLossPrice = slPrice ?: 0.0,
                    positionSide = position.positionSide,
                    percentage = 0.0,
                    maxNotionalValue = 0.0,
                    isAutoAddMargin = position.isAutoAddMargin
                )

                Log.d("UsdmFuturesFragment", "Using integration guide TP/SL method for: ${position.symbol}")
                Log.d("UsdmFuturesFragment", "Position details: positionAmt=${position.positionAmt}, entryPrice=${position.entryPrice}, markPrice=${position.markPrice}")

                // Use the enhanced TP/SL method from integration guide
                val result = TradingUtils.setTPSLWithValidation(
                    repository = externalRepository,
                    position = binancePosition,
                    takeProfitPrice = tpPrice,
                    stopLossPrice = slPrice
                )
                
                result.fold(
                    onSuccess = { message ->
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                            dialog.dismiss()
                            // Refresh data to show updated positions
                            refreshData()
                        }
                    },
                    onFailure = { error ->
                        withContext(Dispatchers.Main) {
                            val errorMessage = "Error setting TP/SL: ${error.message}"
                            Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
                            Log.e("UsdmFuturesFragment", errorMessage, error)
                            
                            // Reset button state
                            val dialogBinding = DialogFuturesTpSlBinding.bind(dialog.findViewById(android.R.id.content))
                            dialogBinding.confirmButton.isEnabled = true
                            dialogBinding.confirmButton.text = "Confirm TP/SL"
                        }
                    }
                )
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    val errorMessage = "Exception setting TP/SL: ${e.message}"
                    Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
                    Log.e("UsdmFuturesFragment", errorMessage, e)
                    
                    // Reset button state
                    val dialogBinding = DialogFuturesTpSlBinding.bind(dialog.findViewById(android.R.id.content))
                    dialogBinding.confirmButton.isEnabled = true
                    dialogBinding.confirmButton.text = "Confirm TP/SL"
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

    private fun calculateRoe(position: BinanceFutures): Double {
        // Calculate isolated margin if needed
        val isolatedMargin = calculateIsolatedMargin(position)

        // If we have valid margin, use the PNL/margin formula
        if (isolatedMargin > 0.0) {
            return position.unRealizedProfit / isolatedMargin
        }

        // Otherwise, use the formula (entry price - mark price)/100 x leverage
        // For long positions: (mark price - entry price)/entry price * leverage
        // For short positions: (entry price - mark price)/entry price * leverage
        val isLong = position.positionAmt > 0
        val entryPrice = position.entryPrice
        val markPrice = position.markPrice

        if (entryPrice <= 0) return 0.0 // Avoid division by zero

        return if (isLong) {
            (markPrice - entryPrice) / entryPrice * position.leverage
        } else {
            (entryPrice - markPrice) / entryPrice * position.leverage
        }
    }

    private fun calculateIsolatedMargin(position: BinanceFutures): Double {
        // If isolatedMargin is already set correctly, use it
        if (position.isolatedMargin > 0.0) {
            return position.isolatedMargin
        }

        // Otherwise, calculate it based on position size, entry price, and leverage
        val positionValue = Math.abs(position.positionAmt) * position.entryPrice
        return positionValue / position.leverage
    }

    private fun setupSwipeRefresh() {
        binding.futuresSwipeRefreshLayout.setOnRefreshListener {
            // Add a toast to indicate refresh is happening
            Toast.makeText(context, "Refreshing USD-M futures data...", Toast.LENGTH_SHORT).show()
            Log.d("UsdmFuturesFragment", "Pull-to-refresh triggered")
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

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                if (useExternalService) {
                    // Check external service health first
                    externalRepository.getHealth().fold(
                        onSuccess = { health ->
                            Log.d("UsdmFuturesFragment", "External service health: ${health.data.status}")
                            if (health.data.services.usdm.isConnected) {
                                refreshExternalData()
                            } else {
                                // Show warning but still try to refresh data since REST API might work
                                Log.w("UsdmFuturesFragment", "USD-M WebSocket not connected, trying REST API")
                                refreshExternalData()
                            }
                        },
                        onFailure = { error ->
                            Log.e("UsdmFuturesFragment", "External service health check failed: ${error.message}")
                            showError("Service unavailable: ${error.message}")
                        }
                    )
                } else {
                    // Fallback to direct API (this branch should not be used anymore)
                    Log.w("UsdmFuturesFragment", "External service is disabled but required")
                    showError("External service is required for USD-M futures")
                }

                // Add a small delay to make the refresh animation visible
                delay(500)

                // Check if binding is still valid after async operation
                if (_binding != null) {
                    binding.futuresSwipeRefreshLayout.isRefreshing = false
                    showLoading(false)
                    Log.d("UsdmFuturesFragment", "Refreshed USD-M futures data successfully")
                }
            } catch (e: Exception) {
                Log.e("UsdmFuturesFragment", "Error in refreshData: ${e.message}")
                if (_binding != null) {
                    binding.futuresSwipeRefreshLayout.isRefreshing = false
                    showLoading(false)
                    showError("Failed to refresh data: ${e.message}")
                }
            }
        }
    }

    private suspend fun refreshExternalData() {
        try {
            Log.d("UsdmFuturesFragment", "Fetching USD-M futures positions...")
            val positionsResponse = externalRepository.getFuturesPositions()
            positionsResponse.fold(
                onSuccess = { response ->
                    if (response.success && response.data != null) {
                        Log.d("UsdmFuturesFragment", "USD-M futures positions fetched: ${response.data.size}")
                        updateExternalPositionsUI(response.data)
                    } else {
                        throw Exception(response.error ?: "Failed to fetch USD-M futures positions")
                    }
                },
                onFailure = { error ->
                    throw error
                }
            )

            // Fetch USD-M futures orders
            Log.d("UsdmFuturesFragment", "Fetching USD-M futures orders...")
            val ordersResponse = externalRepository.getFuturesOrders()
            ordersResponse.fold(
                onSuccess = { response ->
                    if (response.success && response.data != null) {
                        openOrders = response.data
                        Log.d("UsdmFuturesFragment", "USD-M futures orders fetched: ${response.data.size}")
                    }
                },
                onFailure = { error ->
                    Log.e("UsdmFuturesFragment", "Failed to fetch USD-M futures orders: ${error.message}")
                }
            )

            // Fetch USD-M futures account balance
            Log.d("UsdmFuturesFragment", "Fetching USD-M futures account...")
            val accountResponse = externalRepository.getFuturesAccount()
            accountResponse.fold(
                onSuccess = { response ->
                    if (response.success && response.data != null) {
                        Log.d("UsdmFuturesFragment", "USD-M futures account fetched: ${response.data.totalWalletBalance}")
                        updateAccountUI(response.data)
                    }
                },
                onFailure = { error ->
                    Log.e("UsdmFuturesFragment", "Failed to fetch USD-M futures account: ${error.message}")
                }
            )

        } catch (e: Exception) {
            Log.e("UsdmFuturesFragment", "Error in refreshExternalData: ${e.message}")
            throw e
        }
    }

    private fun updateExternalPositionsUI(positions: List<PositionData>) {
        requireActivity().runOnUiThread {
            // Convert PositionData to BinancePosition for adapter compatibility
            val binancePositions = positions.map { position ->
                BinancePosition(
                    symbol = position.symbol,
                    positionAmt = position.positionAmount,
                    entryPrice = position.entryPrice,
                    markPrice = position.markPrice,
                    unrealizedProfit = position.unrealizedProfit,
                    liquidationPrice = 0.0, // Not provided by external API
                    leverage = position.leverage.toInt(),
                    marginType = position.marginType,
                    isolatedMargin = position.isolatedMargin,
                    roe = position.percentage, // Use percentage as ROE
                    takeProfitPrice = 0.0, // Will be filled from orders
                    stopLossPrice = 0.0 // Will be filled from orders
                )
            }

            // Update adapter
            futuresAdapter.submitList(binancePositions)

            // Show empty state if no positions
            binding.emptyStateText.visibility = if (binancePositions.isEmpty()) View.VISIBLE else View.GONE
            if (binancePositions.isEmpty()) {
                binding.emptyStateText.text = "No open USD-M futures positions (Live Data)"
            }

            Log.d("UsdmFuturesFragment", "External UI updated with ${binancePositions.size} USD-M positions")
        }
    }

    private fun updateAccountUI(accountData: com.example.allinone.api.AccountData) {
        requireActivity().runOnUiThread {
            if (_binding == null) return@runOnUiThread

            // Update balance values
            binding.balanceValueText.text = currencyFormatter.format(accountData.totalMarginBalance)
            binding.marginBalanceValueText.text = currencyFormatter.format(accountData.totalWalletBalance)
            binding.pnlValueText.text = currencyFormatter.format(accountData.totalUnrealizedProfit)

            // Set PNL color
            val pnlColor = if (accountData.totalUnrealizedProfit >= 0) {
                android.graphics.Color.parseColor("#4CAF50")
            } else {
                android.graphics.Color.parseColor("#FF5252")
            }
            binding.pnlValueText.setTextColor(pnlColor)

            Log.d("UsdmFuturesFragment", "Account UI updated - Balance: ${accountData.totalMarginBalance}, PNL: ${accountData.totalUnrealizedProfit}")
        }
    }

    private fun initializeWebSocket() {
        if (useExternalService) {
            webSocketClient = BinanceWebSocketClient(
                onMessage = { type, data ->
                    // Check if fragment is still valid before updating UI
                    if (isAdded && _binding != null) {
                        requireActivity().runOnUiThread {
                            handleWebSocketMessage(type, data)
                        }
                    }
                },
                onConnectionChange = { connected ->
                    // Check if fragment is still valid before updating UI
                    if (isAdded && _binding != null) {
                        requireActivity().runOnUiThread {
                            updateConnectionStatus(connected)
                        }
                    }
                }
            )
            
            // Connect WebSocket
            webSocketClient.connect()
            
            // Subscribe to futures-specific data streams
            lifecycleScope.launch {
                delay(1000) // Wait for connection to establish
                if (webSocketClient.isConnected()) {
                    webSocketClient.subscribeToPositionUpdates()
                    webSocketClient.subscribeToOrderUpdates()
                    webSocketClient.subscribeToBalanceUpdates()
                }
            }
        }
    }

    private fun handleWebSocketMessage(type: String, data: JsonObject) {
        // Additional safety check - should not be needed due to caller check, but being extra safe
        if (!isAdded || _binding == null) return
        
        Log.d("UsdmFuturesFragment", "WebSocket message received: $type")
        
        when (type) {
            "positions_update" -> {
                Log.d("UsdmFuturesFragment", "Position update: $data")
                // Refresh positions when update received
                viewLifecycleOwner.lifecycleScope.launch {
                    if (useExternalService) {
                        refreshExternalPositions()
                    }
                }
            }
            "order_update" -> {
                Log.d("UsdmFuturesFragment", "Order update: $data")
                // Refresh orders when update received
                viewLifecycleOwner.lifecycleScope.launch {
                    if (useExternalService) {
                        refreshExternalOrders()
                    }
                }
            }
            "balance_update" -> {
                Log.d("UsdmFuturesFragment", "Balance update: $data")
                // Handle balance updates if needed
            }
            "connection" -> {
                val status = data.get("status")?.asString
                Log.d("UsdmFuturesFragment", "Connection status: $status")
            }
            "pong" -> {
                Log.d("UsdmFuturesFragment", "Heartbeat pong received")
            }
        }
    }

    private fun updateConnectionStatus(connected: Boolean) {
        // Additional safety check - should not be needed due to caller check, but being extra safe
        if (!isAdded || _binding == null) return
        
        Log.d("UsdmFuturesFragment", "WebSocket connection status: $connected")
        
        if (connected) {
            // Show connected indicator
            Toast.makeText(requireContext(), "Live data connected", Toast.LENGTH_SHORT).show()
        } else {
            // Show disconnected indicator
            Toast.makeText(requireContext(), "Live data disconnected", Toast.LENGTH_SHORT).show()
            
            // Try to reconnect after delay
            viewLifecycleOwner.lifecycleScope.launch {
                delay(5000)
                if (::webSocketClient.isInitialized && !webSocketClient.isConnected()) {
                    Log.d("UsdmFuturesFragment", "Attempting to reconnect WebSocket")
                    webSocketClient.connect()
                }
            }
        }
    }

    private suspend fun refreshExternalPositions() {
        try {
            val positionsResponse = externalRepository.getFuturesPositions()
            positionsResponse.fold(
                onSuccess = { response ->
                    if (response.success && response.data != null) {
                        Log.d("UsdmFuturesFragment", "Live USD-M futures positions update: ${response.data.size}")
                        updateExternalPositionsUI(response.data)
                    }
                },
                onFailure = { error ->
                    Log.e("UsdmFuturesFragment", "Failed to refresh live USD-M futures positions: ${error.message}")
                }
            )
        } catch (e: Exception) {
            Log.e("UsdmFuturesFragment", "Error refreshing live USD-M futures positions: ${e.message}")
        }
    }

    private suspend fun refreshExternalOrders() {
        try {
            val ordersResponse = externalRepository.getFuturesOrders()
            ordersResponse.fold(
                onSuccess = { response ->
                    if (response.success && response.data != null) {
                        openOrders = response.data
                        Log.d("UsdmFuturesFragment", "Live USD-M futures orders update: ${response.data.size}")
                    }
                },
                onFailure = { error ->
                    Log.e("UsdmFuturesFragment", "Failed to refresh live USD-M futures orders: ${error.message}")
                }
            )
        } catch (e: Exception) {
            Log.e("UsdmFuturesFragment", "Error refreshing live USD-M futures orders: ${e.message}")
        }
    }

    private fun closePosition(position: BinancePosition, dialog: Dialog) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                Log.d("UsdmFuturesFragment", "Using integration guide close position method for: ${position.symbol}")

                // Use the enhanced close position method from integration guide
                val result = TradingUtils.closePositionSafely(
                    repository = externalRepository,
                    position = position
                )
                
                result.fold(
                    onSuccess = { message ->
                        requireActivity().runOnUiThread {
                            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                            dialog.dismiss()
                            // Refresh data to update UI
                            refreshData()
                        }
                    },
                    onFailure = { error ->
                        requireActivity().runOnUiThread {
                            val errorMessage = "Error closing position: ${error.message}"
                            Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show()
                            Log.e("UsdmFuturesFragment", errorMessage, error)
                            
                            // Reset button state
                            val dialogBinding = DialogFuturesTpSlBinding.bind(dialog.findViewById(android.R.id.content))
                            dialogBinding.closePositionButton.isEnabled = true
                            dialogBinding.closePositionButton.text = "Close Position"
                        }
                    }
                )
            } catch (e: Exception) {
                requireActivity().runOnUiThread {
                    val errorMessage = "Exception closing position: ${e.message}"
                    Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show()
                    Log.e("UsdmFuturesFragment", errorMessage, e)
                    
                    // Reset button state
                    val dialogBinding = DialogFuturesTpSlBinding.bind(dialog.findViewById(android.R.id.content))
                    dialogBinding.closePositionButton.isEnabled = true
                    dialogBinding.closePositionButton.text = "Close Position"
                }
            }
        }
    }

    private fun showError(message: String) {
        requireActivity().runOnUiThread {
            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (::webSocketClient.isInitialized) {
            webSocketClient.disconnect()
        }
        _binding = null
    }
}
