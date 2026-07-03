package com.example.monday.services.payment

import android.view.accessibility.AccessibilityNodeInfo

/**
 * Result of a successful payment parse.
 * @param amount The payment amount (e.g., "150.00")
 * @param refId Unique transaction ID for deduplication. Use "TX_{APP}_{amount}_TEMPORARY" for intermediate screens.
 * @param isIntermediate True if this is the quick green-screen with no real refId yet.
 */
data class PaymentResult(
    val amount: String,
    val refId: String,
    val isIntermediate: Boolean = false
)

/**
 * Per-app payment parser contract.
 * Each UPI app gets its own implementation to handle its specific UI patterns.
 */
interface PaymentParser {
    /** The package name(s) this parser handles */
    val supportedPackages: List<String>

    /**
     * Parse the accessibility tree and return a PaymentResult if a success screen is detected.
     * Returns null if this is NOT a valid success screen (history, pre-payment, etc).
     */
    fun parse(rootNode: AccessibilityNodeInfo): PaymentResult?
}
