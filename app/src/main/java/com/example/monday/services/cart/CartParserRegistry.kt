package com.example.monday.services.cart

import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import com.example.monday.data.models.CartItem

/**
 * Routes the foreground app package to the correct per-app cart parser.
 * Unknown apps get a graceful fallback that dumps the tree for future development.
 */
object CartParserRegistry {

    private const val TAG = "Kharchaji_CartRegistry"

    // ── Supported app packages ──────────────────────────────────────────
    private const val PKG_ZEPTO = "com.zeptonow.app"
    // Future parsers:
    // private const val PKG_BLINKIT = "com.grofers.customerapp"
    // private const val PKG_ZOMATO = "com.application.zomato"
    // private const val PKG_SWIGGY = "in.swiggy.android"
    // private const val PKG_BIGBASKET = "com.bigbasket.mobileapp"

    /**
     * Returns the parser for the given app package.
     * Falls back to UnsupportedAppParser for unknown apps.
     */
    fun getParser(appPackage: String): CartParser {
        val parser = when (appPackage) {
            PKG_ZEPTO -> ZeptoCartParser()
            // Add new parsers here as they are built:
            // PKG_BLINKIT -> BlinkitCartParser()
            // PKG_ZOMATO -> ZomatoCartParser()
            else -> UnsupportedAppParser(appPackage)
        }
        return parser
    }

    /**
     * Check if a given package has a dedicated parser.
     */
    fun isSupported(appPackage: String): Boolean = when (appPackage) {
        PKG_ZEPTO -> true
        else -> false
    }
}

/**
 * Fallback parser for apps that don't have a dedicated parser yet.
 * Always returns empty list but dumps tree for diagnostics so
 * we can build the real parser from the Logcat output.
 */
class UnsupportedAppParser(override val supportedPackage: String) : CartParserBase() {

    companion object {
        private const val TAG = "Kharchaji_Unsupported"
    }

    override fun parse(rootNode: AccessibilityNodeInfo): List<CartItem> {
        Log.w(TAG, "No dedicated parser for '$supportedPackage'. Dumping tree for analysis.")
        // Dump tree automatically so developers can see what this app exposes
        dumpTree(rootNode, maxDepth = 8)
        return emptyList()
    }
}
