package com.example.monday.services.payment

import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

/**
 * BHIM UPI payment success detector — SPEED OPTIMIZED.
 *
 * Screen flow:
 *   Screen 2 (~50ms): Green "Payment Successful" splash — NO amount
 *   Screen 3 (stable): "₹1.00", "Paid", Transaction ID, "Send Again", "Home"
 *
 * Optimizations:
 *   - Early exit: stops tree traversal as soon as amount + positive signal found
 *   - Pre-compiled regexes (no allocation in hot path)
 *   - Minimal negative checks (only truly blocking patterns)
 */
class BhimUpiPaymentParser : PaymentParser {

    companion object {
        private const val TAG = "Kharchaji_BHIM"
    }

    override val supportedPackages = listOf("in.org.npci.upiapp")

    // Pre-compiled regexes — zero allocation in hot path
    private val amountRegex = Regex("₹\\s?([0-9,]+(\\.[0-9]{1,2})?)")
    private val transactionIdRegex = Regex("\\d{10,}")

    // Stateful cache: grab the amount from the PIN screen so we don't have
    // to wait for the 2-second animation to the details screen.
    private var pendingAmount: String? = null

    override fun parse(rootNode: AccessibilityNodeInfo): PaymentResult? {
        var foundAmount: String? = null
        var foundTransactionId: String? = null
        var hasPaymentSuccessful = false
        var hasPositiveSignal = false   // "Paid", "Send Again", "Home", etc.
        var isBlockedScreen = false
        var amountNodeCount = 0
        var earlyExit = false           // Stop traversal ASAP
        var isPrePayment = false

        fun traverse(node: AccessibilityNodeInfo) {
            if (earlyExit) return  // Already have enough data

            val text = node.text?.toString() ?: node.contentDescription?.toString()

            if (text != null) {
                // ── Amount (fastest check first) ──
                val amountMatch = amountRegex.find(text)
                if (amountMatch != null) {
                    amountNodeCount++
                    if (foundAmount == null) {
                        foundAmount = amountMatch.groupValues[1]
                    }
                    // >3 amounts = list screen, abort immediately
                    if (amountNodeCount > 3) {
                        isBlockedScreen = true
                        earlyExit = true
                        return
                    }
                }

                // ── Positive signals ──
                if (text.contains("Payment Successful", true)) {
                    hasPaymentSuccessful = true
                }
                
                // Highly specific to immediate success screen: "Paid in 1.65 Seconds"
                if (text.contains("Paid in", true) && text.contains("Second", true)) {
                    hasPositiveSignal = true
                }
                
                // Broad "Paid" — catches "Paid", "✓ Paid"
                if (text.contains("Paid", true) &&
                    !text.contains("Unpaid", true) &&
                    !text.contains("Paid at", true)) {
                    hasPositiveSignal = true
                }
                if (text.equals("Send Again", true) || text.equals("Home", true)) {
                    hasPositiveSignal = true
                }

                // ── Transaction ID (long numeric) ──
                if (foundTransactionId == null) {
                    if (text.contains("Transaction ID", true)) {
                        val idMatch = transactionIdRegex.find(text)
                        if (idMatch != null) {
                            foundTransactionId = idMatch.groupValues[0]
                            hasPositiveSignal = true
                        }
                    } else if (text.matches(transactionIdRegex)) {
                        foundTransactionId = text.trim()
                        hasPositiveSignal = true
                    }
                }

                // ── HYPER-AGGRESSIVE EARLY EXIT ──
                if (foundAmount != null && text.contains("Paid in", true) && text.contains("Second", true)) {
                    val cleanAmount = foundAmount!!.replace(",", "")
                    val refId = if (foundTransactionId != null) "BHIM_$foundTransactionId" else "TX_BHIM_${cleanAmount}_DETAILS"
                    throw Exception("FAST_SUCCESS|${cleanAmount}|$refId")
                }

                // Standard early exit flag for normal flow
                if (foundAmount != null && hasPositiveSignal && !isBlockedScreen) {
                    earlyExit = true
                    return
                }

                // ── Negative: Pre-payment / PIN screens ──
                if (text.contains("Enter UPI PIN", true) ||
                    text.contains("Scan & Pay", true) ||
                    text.contains("Proceed to Pay", true) ||
                    text.contains("Enter Amount", true)) {
                    isPrePayment = true
                }

                // ── Negative: History / transaction list screens ──
                if (text.contains("Transaction History", true) ||
                    text.contains("All Transactions", true) ||
                    text.equals("Transactions", true) ||
                    text.contains("Tag this transaction", true) ||
                    text.contains("Split this expense", true) ||
                    text.contains("Tag to family", true)) {
                    isBlockedScreen = true
                    earlyExit = true
                    return
                }
            }

            for (i in 0 until node.childCount) {
                if (earlyExit) return
                val child = try { node.getChild(i) } catch (_: Exception) { null }
                child?.let {
                    traverse(it)
                    it.recycle()
                }
            }
        }

        try {
            traverse(rootNode)
        } catch (e: Exception) {
            val msg = e.message ?: ""
            if (msg.startsWith("FAST_SUCCESS|")) {
                val parts = msg.split("|")
                if (parts.size == 3) {
                    pendingAmount = null
                    return PaymentResult(amount = parts[1], refId = parts[2], isIntermediate = false)
                }
            }
            Log.e(TAG, "Exception in traverse: $msg")
        }

        if (isPrePayment) {
            if (foundAmount != null) {
                pendingAmount = foundAmount!!.replace(",", "")
            }
            return null
        }

        if (isBlockedScreen) return null

        if (hasPaymentSuccessful && foundAmount == null) {
            if (pendingAmount != null) {
                val cleanAmount = pendingAmount!!
                pendingAmount = null
                return PaymentResult(amount = cleanAmount, refId = "TX_BHIM_${cleanAmount}_TEMPORARY", isIntermediate = true)
            } else {
                // No cached amount (user entered PIN too fast for us to catch it).
                // Fire popup IMMEDIATELY with blank price — user types it manually.
                // This is far better than 5 seconds of dead silence waiting for Screen 3.
                Log.d(TAG, "Green splash (no cached amount) — firing instant popup with blank price")
                return PaymentResult(amount = "", refId = "TX_BHIM_SPLASH_${System.currentTimeMillis()}", isIntermediate = true)
            }
        }

        if (hasPaymentSuccessful && foundAmount != null) {
            val cleanAmount = foundAmount!!.replace(",", "")
            return PaymentResult(amount = cleanAmount, refId = "TX_BHIM_${cleanAmount}_TEMPORARY", isIntermediate = true)
        }

        if (foundAmount != null && hasPositiveSignal) {
            val cleanAmount = foundAmount!!.replace(",", "")
            val refId = if (foundTransactionId != null) "BHIM_$foundTransactionId" else "TX_BHIM_${cleanAmount}_DETAILS"
            return PaymentResult(amount = cleanAmount, refId = refId, isIntermediate = false)
        }

        return null
    }
}
