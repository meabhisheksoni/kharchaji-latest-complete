package com.example.monday.services.cart

import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.example.monday.data.models.CartItem
import com.example.monday.core.cache.ScanMemoryCache
import com.example.monday.core.nlp.GenericNameHelper

/**
 * Shared utilities for all per-app cart parsers.
 * Contains price regex, tree dump, text extraction, and deduplication logic.
 *
 * Each app-specific parser extends this and implements its own parse().
 */
abstract class CartParserBase : CartParser {

    companion object {
        private const val DUMP_TAG = "Kharchaji_TreeDump"

        // Price: ₹150, Rs. 150, Rs 150.50
        val PRICE_REGEX = Regex("(?:₹|Rs\\.?)\\s*([0-9,]+(\\.[0-9]{1,2})?)", RegexOption.IGNORE_CASE)
        // Pure integer
        val NUMBER_REGEX = Regex("^\\d+$")

        // All forms of minus: ASCII hyphen, Unicode minus sign, en-dash, em-dash
        val MINUS_CHARS = setOf('-', '−', '–', '—', '\u2212', '\u2013', '\u2014')

        // Common UI chrome to skip during name extraction
        val IGNORE_TEXTS = setOf(
            "remove", "save", "delete", "move to wishlist", "apply",
            "schedule", "edit", "add more", "add", "quantity is",
            "increase", "decrease", "increment", "decrement",
            "add to cart", "remove from cart", "add item", "remove item",
            "add more items", "forgot something", "place order",
            "checkout", "pay", "proceed", "share", "change",
            "delivering to", "you saved", "special offer",
            "complete your meal", "add a note", "don't send",
            "paying via", "pay using", "add money", "free"
        )
    }

    // ── Shared Helpers ──────────────────────────────────────────────────

    /**
     * Recursively extract all text (text + contentDescription) from a subtree.
     */
    protected fun extractTextFromBox(node: AccessibilityNodeInfo, results: MutableList<String>) {
        val text = node.text?.toString() ?: node.contentDescription?.toString()
        if (!text.isNullOrBlank()) results.add(text)

        for (i in 0 until node.childCount) {
            val child = try { node.getChild(i) } catch (_: Exception) { null }
            if (child != null) {
                extractTextFromBox(child, results)
                try { child.recycle() } catch (_: Exception) {}
            }
        }
    }

    /**
     * Extract the best price from a list of text strings.
     * Returns the smallest non-zero price (usually the discounted/sale price).
     */
    protected fun extractBestPrice(texts: List<String>): Double {
        var best = 0.0
        for (text in texts) {
            val match = PRICE_REGEX.find(text) ?: continue
            val price = match.groupValues[1].replace(",", "").toDoubleOrNull() ?: continue
            if (price > 0 && (best == 0.0 || price < best)) best = price
        }
        return best
    }

    /**
     * Extract the best item name from a list of text strings.
     * Picks the longest text that isn't a price, stepper, or UI chrome.
     */
    protected fun extractBestName(texts: List<String>, qtyValue: Int = 1): String {
        var bestName = ""
        for (text in texts) {
            val trimmed = text.trim()
            if (trimmed.isBlank()) continue
            if (trimmed.length <= 3) continue
            // Skip stepper symbols
            if (trimmed == "+" || (trimmed.length <= 2 && trimmed.any { it in MINUS_CHARS })) continue
            // Skip pure numbers that match the qty
            if (trimmed.matches(NUMBER_REGEX) && trimmed.toIntOrNull() == qtyValue) continue
            // Skip prices
            if (PRICE_REGEX.containsMatchIn(trimmed) && trimmed.length < 15) continue
            // Skip known UI chrome
            if (IGNORE_TEXTS.any { trimmed.equals(it, true) }) continue

            if (trimmed.length > bestName.length) bestName = trimmed
        }
        return bestName
    }

    /**
     * Normalize a raw name using GenericNameHelper.
     */
    protected fun normalizeName(raw: String): String = GenericNameHelper.normalize(raw)

    /**
     * Filter out items already seen in the scan memory cache.
     */
    protected fun filterNewItems(items: List<CartItem>): List<CartItem> =
        ScanMemoryCache.filterNewItems(items)

    /**
     * Deduplicate within a single scan batch.
     */
    protected fun deduplicateItems(items: List<CartItem>): List<CartItem> =
        items.distinctBy { "${it.rawName}_${it.price}" }

    // ── +/− Indicator Detection ─────────────────────────────────────────

    protected fun isPlusIndicator(text: String): Boolean {
        val t = text.trim()
        if (t == "+") return true
        val lower = t.lowercase()
        return lower.contains("add") || lower.contains("increase") || lower.contains("increment")
    }

    protected fun isMinusIndicator(text: String): Boolean {
        val t = text.trim()
        if (t.length == 1 && t[0] in MINUS_CHARS) return true
        if (t.any { it in MINUS_CHARS }) return true
        val lower = t.lowercase()
        return lower.contains("remove") || lower.contains("delete") ||
                lower.contains("decrease") || lower.contains("decrement")
    }

    // ── Diagnostic Tree Dump ────────────────────────────────────────────

    override fun dumpTree(rootNode: AccessibilityNodeInfo, maxDepth: Int) {
        dumpNode(rootNode, 0, maxDepth)
    }

    private fun dumpNode(node: AccessibilityNodeInfo, depth: Int, maxDepth: Int) {
        if (depth > maxDepth) return
        val indent = "  ".repeat(depth)
        val text = node.text?.toString() ?: ""
        val desc = node.contentDescription?.toString() ?: ""
        val cls = node.className?.toString()?.substringAfterLast('.') ?: "?"
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        val clickable = if (node.isClickable) " [CLICK]" else ""
        val viewId = try { node.viewIdResourceName ?: "" } catch (_: Exception) { "" }
        val viewIdStr = if (viewId.isNotBlank()) " id='$viewId'" else ""

        if (text.isNotBlank() || desc.isNotBlank() || depth <= 3) {
        }

        for (i in 0 until node.childCount) {
            val child = try { node.getChild(i) } catch (_: Exception) { null }
            if (child != null) {
                dumpNode(child, depth + 1, maxDepth)
                try { child.recycle() } catch (_: Exception) {}
            }
        }
    }
}
