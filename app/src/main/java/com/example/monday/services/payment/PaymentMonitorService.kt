package com.example.monday.services.payment

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.monday.ui.overlay.OverlayService
import com.example.monday.services.payment.BhimUpiPaymentParser
import com.example.monday.services.cart.CartParserRegistry
import com.example.monday.widget.WidgetInputActivity
import com.example.monday.ui.cart.CartBatchPopupActivity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.widget.Toast
import java.util.ArrayList

class PaymentMonitorService : AccessibilityService() {

    // Prevent duplicate triggers: Store recent transaction references
    private val processedTransactions = mutableSetOf<String>()
    
    // ── Per-app parsers (each app gets its own file) ──
    private val bhimParser = BhimUpiPaymentParser()

    // RegEx to survive Paytm UI changes
    private val amountRegex = Regex("₹\\s?([0-9,]+(\\.[0-9]{1,2})?)")
    private val refRegex = Regex("Ref\\.?\\s*No\\.?\\s*:\\s*([0-9A-Za-z]+)", RegexOption.IGNORE_CASE)

    // Dedicated receiver for handling Universal Cart Scanner trigger from Overlay
    private val cartScannerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.example.monday.ACTION_SCAN_CART") {
                performCartScan()
            }
        }
    }

    private fun performCartScan() {
        Toast.makeText(this, "Scanning cart...", Toast.LENGTH_SHORT).show()
        val rootNode = try {
            rootInActiveWindow
        } catch (e: Exception) {
            Log.e("Kharchaji_CartScanner", "Cannot get root active window", e)
            null
        }
        
        if (rootNode == null) {
            Toast.makeText(this, "Cannot scan screen right now.", Toast.LENGTH_SHORT).show()
            return
        }

        // Detect the foreground app for app-specific parsing hints
        val appPackage = rootNode.packageName?.toString() ?: ""

        val parser = CartParserRegistry.getParser(appPackage)
        val items = parser.parse(rootNode)

        if (items.isNotEmpty()) {
            val popupIntent = Intent(this, CartBatchPopupActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("cart_names", items.map { it.rawName }.toTypedArray())
                putExtra("cart_qtys", items.map { it.quantity }.toIntArray())
                putExtra("cart_prices", items.map { it.price }.toDoubleArray())
            }
            startActivity(popupIntent)
        } else {
            val isSupported = CartParserRegistry.isSupported(appPackage)
            val msg = if (isSupported) "No valid cart items found!" 
                      else "Cart scan not supported for this app yet."
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            // Tree dump happens automatically inside UnsupportedAppParser, 
            // but also dump here for supported apps that found 0 items
            if (isSupported) {
                val diagRoot = try { rootInActiveWindow } catch (_: Exception) { null }
                if (diagRoot != null) {
                    parser.dumpTree(diagRoot, maxDepth = 8)
                    try { diagRoot.recycle() } catch (_: Exception) {}
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 50 
            flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
        }
        this.serviceInfo = info

        val filter = IntentFilter("com.example.monday.ACTION_SCAN_CART")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(cartScannerReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(cartScannerReceiver, filter)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val prefManager = com.example.monday.managers.PreferenceManager.from(this)
        val isMonitorEnabled = prefManager.getPaymentMonitorSetting("enable_monitor") ?: true
        if (!isMonitorEnabled) return

        val eventPackage = event?.packageName?.toString() ?: ""
        if (eventPackage != "net.one97.paytm" && 
            eventPackage != "com.google.android.apps.nbu.paisa.user" &&
            eventPackage != "com.phonepe.app" &&
            eventPackage != "in.org.npci.upiapp") {
            return
        }

        val rootNode = try {
            rootInActiveWindow
        } catch (e: Exception) {
            null
        } ?: return

        if (eventPackage == "in.org.npci.upiapp") {
            handleBhimEvent(rootNode)
            return
        }

        var foundAmount: String? = null
        var foundRefNo: String? = null
        var hasSuccessScreenProof = false
        var isPrePaymentScreen = false
        var isHistoryScreen = false
        var amountNodeCount = 0

        fun traverseNode(node: AccessibilityNodeInfo): Boolean {
            // SRE Optimization: Fail-fast if we already have everything we need
            if (foundAmount != null && foundRefNo != null && hasSuccessScreenProof) {
                return true 
            }
            
            val text = node.text?.toString() ?: node.contentDescription?.toString()
            
            if (text != null) {
                val amountMatch = amountRegex.find(text)
                if (amountMatch != null) {
                    amountNodeCount++
                    if (foundAmount == null) foundAmount = amountMatch.groupValues[1]
                }
                
                if (foundRefNo == null) {
                    val refMatch = refRegex.find(text)
                    if (refMatch != null) {
                        if (text.contains("UPI Ref", true)) isHistoryScreen = true
                        else foundRefNo = refMatch.groupValues[1]
                    }
                }

                if (text.contains("Pay securely", true) || 
                    text.contains("Paying securely", true) ||
                    text.contains("Proceed Securely", true) ||
                    text.contains("Enter UPI PIN", true) ||
                    text.contains("Enter your PIN", true) ||
                    text.contains("UPI PIN", true) ||
                    text.contains("To Pay", true) ||
                    text.contains("Link Bank Account", true)) {
                    isPrePaymentScreen = true
                }

                if (text.contains("Sent Successfully", true) || 
                    text.contains("Transferred Successfully", true) ||
                    text.contains("Received Successfully", true) ||
                    text.contains("Payment Details", true) ||
                    text.contains("Money Transfer", true) ||
                    text.contains("Self Transfer", true) ||
                    text.contains("View History", true) ||
                    text.contains("Paid at ", true)) {
                    isHistoryScreen = true
                }

                if (text.equals("Check Balance", true) || 
                    text.equals("Payment Successful", true) ||
                    text.contains("Payment completed in", true)) {
                    hasSuccessScreenProof = true
                }
            }

            for (i in 0 until node.childCount) {
                val child = try { node.getChild(i) } catch (e: Exception) { null }
                child?.let { 
                    val shouldStop = traverseNode(it) 
                    it.recycle()
                    if (shouldStop) return true
                }
            }
            return false
        }

        traverseNode(rootNode)
        rootNode.recycle()

        if (isPrePaymentScreen || isHistoryScreen) return

        // Ref number is definitive transaction proof — trigger immediately when present
        val isLikelySuccessScreen = foundAmount != null && (
            foundRefNo != null || // Ref# = confirmed transaction, no extra checks needed
            (amountNodeCount <= 3 && hasSuccessScreenProof) // Fallback: heuristic proof
        )

        if (isLikelySuccessScreen) {
            val uniqueTxId = foundRefNo ?: "TX_${foundAmount}_TEMPORARY"
            var isDuplicate = processedTransactions.contains(uniqueTxId)
            val tempId = "TX_${foundAmount}_TEMPORARY"

            if (!isDuplicate && foundRefNo != null && processedTransactions.contains(tempId)) {
                isDuplicate = true
                processedTransactions.remove(tempId)
                processedTransactions.add(uniqueTxId)
            }

            if (!isDuplicate) {
                processedTransactions.add(uniqueTxId)
                // Evict oldest rather than wiping the entire cache,
                // which would open the floodgates to duplicates if the screen lingers
                if (processedTransactions.size > 50) {
                    val iterator = processedTransactions.iterator()
                    if (iterator.hasNext()) {
                        iterator.next()
                        iterator.remove()
                    }
                }
                triggerExpenseOverlay(foundAmount!!, uniqueTxId)
            }
        }
    }

    private fun handleBhimEvent(rootNode: AccessibilityNodeInfo) {
        val result = bhimParser.parse(rootNode)
        rootNode.recycle()

        if (result == null) return

        val uniqueTxId = result.refId
        var isDuplicate = processedTransactions.contains(uniqueTxId)

        if (!isDuplicate && !result.isIntermediate) {
            val tempId = "TX_BHIM_${result.amount}_TEMPORARY"
            if (processedTransactions.contains(tempId)) {
                isDuplicate = true
                processedTransactions.remove(tempId)
                processedTransactions.add(uniqueTxId)
            }
        }

        if (!isDuplicate) {
            processedTransactions.add(uniqueTxId)
            if (processedTransactions.size > 50) {
                val iterator = processedTransactions.iterator()
                if (iterator.hasNext()) {
                    iterator.next()
                    iterator.remove()
                }
            }
            triggerExpenseOverlay(result.amount, result.refId)
        }
    }

    private fun triggerExpenseOverlay(amount: String, refId: String) {
        val overlayIntent = Intent(this, OverlayService::class.java).apply {
            action = "ACTION_QUICK_ADD_PAYMENT"
            putExtra("EXTRA_AMOUNT", amount)
            putExtra("EXTRA_REF", refId)
        }
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(overlayIntent)
            } else {
                startService(overlayIntent)
            }
        } catch (e: Exception) {
            try {
                val activityIntent = Intent(this, WidgetInputActivity::class.java).apply {
                    putExtra("field", "item_name")
                    putExtra("prefill_amount", amount.replace(",", ""))
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                startActivity(activityIntent)
            } catch (e2: Exception) {
                Log.e("Kharchaji_Monitor", "Cannot launch any UI", e2)
            }
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(cartScannerReceiver)
        } catch (e: Exception) {}
    }
}
