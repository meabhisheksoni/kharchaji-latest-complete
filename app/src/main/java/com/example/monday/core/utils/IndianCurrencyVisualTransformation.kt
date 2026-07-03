package com.example.monday.core.utils

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/**
 * Formats numeric input with Indian comma grouping for display.
 * Pattern: last 3 digits grouped, then groups of 2.
 * Example: 1234567 → 12,34,567
 *
 * Reusable across Widget, AddExpense, and any price input field.
 * Handles decimal points and correct cursor positioning.
 */
class IndianCurrencyVisualTransformation : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        if (raw.isBlank()) return TransformedText(text, OffsetMapping.Identity)

        val parts = raw.split(".")
        val intPart = parts[0]
        val decPart = if (parts.size > 1) ".${parts[1]}" else ""

        // Indian grouping: last 3, then groups of 2
        val formatted = buildString {
            val len = intPart.length
            if (len <= 3) {
                append(intPart)
            } else {
                val firstGroup = (len - 3) % 2
                var i = 0
                if (firstGroup > 0) {
                    append(intPart.substring(0, firstGroup))
                    append(",")
                    i = firstGroup
                }
                while (i < len - 3) {
                    append(intPart.substring(i, i + 2))
                    append(",")
                    i += 2
                }
                append(intPart.substring(len - 3))
            }
            append(decPart)
        }

        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                // Count commas inserted before this offset in the integer part
                var commas = 0
                val intLen = intPart.length
                val clampedOffset = offset.coerceAtMost(raw.length)
                val inIntPart = clampedOffset.coerceAtMost(intLen)
                if (intLen > 3 && inIntPart > 0) {
                    val firstGroup = (intLen - 3) % 2
                    if (firstGroup > 0 && inIntPart > firstGroup) commas++
                    var pos = firstGroup
                    while (pos + 2 < intLen - 2 && inIntPart > pos + 2) {
                        commas++
                        pos += 2
                    }
                }
                return (clampedOffset + commas).coerceAtMost(formatted.length)
            }

            override fun transformedToOriginal(offset: Int): Int {
                // Walk formatted string, skip comma positions
                var orig = 0
                var trans = 0
                for (ch in formatted) {
                    if (trans >= offset) break
                    trans++
                    if (ch != ',') orig++
                }
                return orig.coerceAtMost(raw.length)
            }
        }

        return TransformedText(AnnotatedString(formatted), offsetMapping)
    }
}
