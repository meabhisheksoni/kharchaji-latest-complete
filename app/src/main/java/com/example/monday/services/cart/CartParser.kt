package com.example.monday.services.cart

import android.view.accessibility.AccessibilityNodeInfo
import com.example.monday.data.models.CartItem

/**
 * Contract for per-app cart parsers.
 * Each supported app gets its own implementation that knows
 * exactly how that app's accessibility tree is structured.
 */
interface CartParser {

    /** The app package name this parser handles (e.g. "com.zeptonow.app") */
    val supportedPackage: String

    /**
     * Parse cart items from the accessibility tree root.
     * Returns deduplicated items ready for the batch popup.
     */
    fun parse(rootNode: AccessibilityNodeInfo): List<CartItem>

    /**
     * Dump the accessibility tree to Logcat for debugging.
     * Called automatically when parse() returns 0 items.
     */
    fun dumpTree(rootNode: AccessibilityNodeInfo, maxDepth: Int = 8)
}
