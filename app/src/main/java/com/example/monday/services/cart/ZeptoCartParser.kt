package com.example.monday.services.cart

import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.example.monday.data.models.CartItem

/**
 * Zepto cart parser.
 *
 * Zepto's cart screen (com.zeptonow.app) exposes:
 * ┌──────────────────────────────────────────────────┐
 * │ [Image]  Lemon                        − 1 +     │
 * │          4 Pcs                     ₹39 ₹21      │
 * ├──────────────────────────────────────────────────┤
 * │ [Image]  Cucumber English             − 1 +     │
 * │          (500-600 g)               ₹44 ₹27      │
 * └──────────────────────────────────────────────────┘
 *
 * Accessibility tree pattern:
 * - Text-based stepper: siblings with text "−", "1", "+" (all as separate text nodes)
 * - Item name: longest text in the row (e.g. "Grapes Green Sonaka Seedless")
 * - Price: ₹-prefixed text nodes; we pick the smallest (discounted price)
 * - Optional "Remove" button on some items
 * - Quantity descriptor like "4 Pcs", "500 g" as separate text nodes
 */
class ZeptoCartParser : CartParserBase() {

    companion object {
        private const val TAG = "Kharchaji_Zepto"
    }

    override val supportedPackage = "com.zeptonow.app"

    override fun parse(rootNode: AccessibilityNodeInfo): List<CartItem> {
        val anchors = mutableListOf<AccessibilityNodeInfo>()

        // Step 1: Find quantity anchors (the number between − and +)
        findQuantityAnchors(rootNode, anchors)

        // Step 2: For each anchor, walk up to the row and extract item info
        val items = mutableListOf<CartItem>()
        for (anchor in anchors) {
            val item = extractItemFromAnchor(anchor)
            if (item != null) {
                // Deduplicate within this scan
                if (items.none { it.rawName == item.rawName && it.price == item.price }) {
                    items.add(item)
                }
            }
        }

        // Cleanup
        anchors.forEach { try { it.recycle() } catch (_: Exception) {} }
        return filterNewItems(items)
    }

    // ── Anchor Detection ────────────────────────────────────────────────

    /**
     * Recursively searches for the quantity number node sandwiched between − and +.
     * Zepto uses plain text nodes: "−", "1", "+"
     */
    private fun findQuantityAnchors(node: AccessibilityNodeInfo, anchors: MutableList<AccessibilityNodeInfo>) {
        val text = node.text?.toString() ?: node.contentDescription?.toString()

        // If this node is a pure number, check if its siblings form a stepper
        if (text != null && text.matches(NUMBER_REGEX)) {
            if (hasSiblingStepperPattern(node)) {
                // Deduplicate by bounds
                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                val isDuplicate = anchors.any { existing ->
                    val b = Rect()
                    existing.getBoundsInScreen(b)
                    b == bounds
                }
                if (!isDuplicate) {
                    anchors.add(AccessibilityNodeInfo.obtain(node))
                }
                return  // Don't recurse into this stepper's children
            }
        }

        // Recurse into children
        for (i in 0 until node.childCount) {
            val child = try { node.getChild(i) } catch (_: Exception) { null }
            if (child != null) {
                findQuantityAnchors(child, anchors)
                try { child.recycle() } catch (_: Exception) {}
            }
        }
    }

    /**
     * Check if this number node's siblings form a − [THIS] + pattern.
     * Zepto renders these as text nodes with literal "−" and "+" characters.
     */
    private fun hasSiblingStepperPattern(numberNode: AccessibilityNodeInfo): Boolean {
        val parent = try { numberNode.parent } catch (_: Exception) { null } ?: return false
        var hasPlus = false
        var hasMinus = false

        for (i in 0 until parent.childCount) {
            val sibling = try { parent.getChild(i) } catch (_: Exception) { null } ?: continue
            val sibText = sibling.text?.toString() ?: sibling.contentDescription?.toString()

            if (sibText != null) {
                if (isPlusIndicator(sibText)) hasPlus = true
                if (isMinusIndicator(sibText)) hasMinus = true
            }

            // Zepto may also use clickable ImageView for the − button on qty=1 (trash icon)
            if (!hasMinus) {
                val cls = sibling.className?.toString() ?: ""
                if ((cls.contains("ImageButton", true) || cls.contains("ImageView", true)) && sibling.isClickable) {
                    val sibBounds = Rect()
                    val numBounds = Rect()
                    sibling.getBoundsInScreen(sibBounds)
                    numberNode.getBoundsInScreen(numBounds)
                    // If the clickable image is to the left of the number, it's the minus/trash
                    if (sibBounds.centerX() < numBounds.centerX()) hasMinus = true
                }
            }

            try { sibling.recycle() } catch (_: Exception) {}
        }

        try { parent.recycle() } catch (_: Exception) {}
        return hasPlus && hasMinus
    }

    // ── Item Extraction ─────────────────────────────────────────────────

    /**
     * Given a quantity anchor, walk up to the cart-item row and extract name + price.
     */
    private fun extractItemFromAnchor(anchor: AccessibilityNodeInfo): CartItem? {
        val anchorBounds = Rect()
        anchor.getBoundsInScreen(anchorBounds)

        // Parse quantity from the anchor text
        val anchorText = anchor.text?.toString() ?: "1"
        val qty = anchorText.trim().toIntOrNull() ?: 1

        // Walk up the tree to find the row container
        // Zepto's row: height 60-400px, width significantly wider than the stepper
        var rowParent = try { anchor.parent } catch (_: Exception) { null }
        var rowBounds = Rect()
        var levels = 0

        while (rowParent != null && levels < 10) {
            rowParent.getBoundsInScreen(rowBounds)
            val isWideEnough = rowBounds.width() > anchorBounds.width() * 2
            val isTallEnough = rowBounds.height() > 60
            val isNotFullScreen = rowBounds.height() < 800  // Don't grab the entire list

            if (isWideEnough && isTallEnough && isNotFullScreen) break

            val nextParent = try { rowParent.parent } catch (_: Exception) { null }
            try { rowParent.recycle() } catch (_: Exception) {}
            rowParent = nextParent
            levels++
        }

        if (rowParent == null) return null

        // Collect all text from the row
        val rowTexts = mutableListOf<String>()
        extractTextFromBox(rowParent, rowTexts)
        try { rowParent.recycle() } catch (_: Exception) {}

        // Extract best price (smallest non-zero = discounted price)
        val price = extractBestPrice(rowTexts)
        if (price <= 0) return null

        // Extract best name (longest valid text)
        val rawName = extractBestName(rowTexts, qty)
        if (rawName.isBlank()) return null

        // Normalize the name (strip brands, weights, etc.)
        val normalizedName = normalizeName(rawName)
        if (normalizedName.isBlank()) return null

        return CartItem(normalizedName, price, qty)
    }
}
