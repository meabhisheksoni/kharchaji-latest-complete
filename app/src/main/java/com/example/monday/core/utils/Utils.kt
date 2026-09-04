package com.example.monday.core.utils

import com.example.monday.data.models.TodoItem
import com.example.monday.data.models.RecordItem
import android.util.Log
import java.util.Locale
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.Instant
import java.time.ZoneId

enum class CategoryType {
    PRIMARY, SECONDARY, TERTIARY
}

fun getCategoryType(category: String): CategoryType {
    val lowerCategory = category.lowercase(Locale.getDefault())
    val primaryKeywords = setOf("abhishek", "kharcha", "papa", "priya", "mmy")
    val secondaryKeywords = setOf("education", "home", "travel", "wedding")
    val tertiaryKeywords = setOf(
        "grocery", "shopping", "food", "bills", "entertainment", 
        "eating", "hotel", "restaurant", "give", "can be", "medicine"
    )

    return when {
        primaryKeywords.any { it == lowerCategory || lowerCategory.contains(it) } -> CategoryType.PRIMARY
        secondaryKeywords.any { it == lowerCategory || lowerCategory.contains(it) } -> CategoryType.SECONDARY
        tertiaryKeywords.any { it == lowerCategory || lowerCategory.contains(it) } -> CategoryType.TERTIARY
        else -> CategoryType.TERTIARY
    }
}

fun parsePrice(text: String): Double {
    val cleanedText = text.split("|CATS:").first()
    val priceString = cleanedText.split(" - ₹").lastOrNull()?.trim() ?: return 0.0
    return priceString.toDoubleOrNull() ?: 0.0
}

fun parseItemText(text: String): Triple<String, String?, String> {
    val cleanedText = text.split("|CATS:").first()
    val parts = cleanedText.split(" - ₹")
    if (parts.size < 2) return Triple(cleanedText, null, "0.0")

    val nameAndMaybeQuantity = parts[0].trim()
    var price = parts[1].trim()

    val quantityMatch = Regex("""\((.*?)\)""").find(nameAndMaybeQuantity)
    val extractedQuantityString = quantityMatch?.groupValues?.get(1)
    val name = nameAndMaybeQuantity.replace("""\s*\(.*?\)""".toRegex(), "").trim()
    price = Regex("""[^\d.]+$""").replace(price, "").trim()

    return Triple(name, extractedQuantityString, price)
}

fun parseCategoryInfo(itemText: String): Pair<String, List<String>> {
    return if (itemText.contains("|CATS:")) {
        try {
            val parts = itemText.split("|CATS:")
            val displayText = parts[0]
            val categoryNames = parts[1].split(",").map { it.trim() }
            displayText to categoryNames
        } catch (e: Exception) {
            Log.e("CategoryDebug", "Error parsing categories: ${e.message}")
            itemText to emptyList()
        }
    } else {
        itemText to emptyList()
    }
}

fun todoItemToRecordItem(todoItem: TodoItem): RecordItem {
    val (name, quantity, price) = parseItemText(todoItem.text)
    return RecordItem(
        description = name,
        quantity = quantity,
        price = price,
        isChecked = todoItem.isDone,
        categories = todoItem.categories,
        imageUris = todoItem.imageUris,
        sourceItemId = todoItem.id
    )
}

fun recordItemToTodoItemText(recordItem: RecordItem): String {
    val name = recordItem.description
    val quantity = recordItem.quantity
    val priceString = recordItem.price
    val priceAsDouble: Double = try {
        priceString.toDouble()
    } catch (e: NumberFormatException) {
        Log.e("RecordItemConversion", "Failed to parse price string: '${priceString}' to Double. Defaulting to 0.0", e)
        0.0
    }
    
    val baseText = if (quantity != null && quantity.isNotBlank()) {
        "${name} (${quantity}) - ₹${String.format(Locale.US, "%.2f", priceAsDouble)}"
    } else {
        "${name} - ₹${String.format(Locale.US, "%.2f", priceAsDouble)}"
    }
    
    return if (!recordItem.categories.isNullOrEmpty()) {
        "$baseText|CATS:${recordItem.categories!!.joinToString(",")}"
    } else {
        baseText
    }
}

fun Long.toLocalDate(): LocalDate {
    return Instant.ofEpochMilli(this)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
}

fun LocalDate.toEpochMilli(): Long {
    return this.atStartOfDay(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()
}

fun LocalDate.formatForDisplay(pattern: String = "MMM dd, yyyy"): String {
    return this.format(DateTimeFormatter.ofPattern(pattern))
}

fun intelligentlyCategorize(categories: Set<String>): Triple<List<String>, List<String>, List<String>> {
    val primary = mutableListOf<String>()
    val secondary = mutableListOf<String>()
    val tertiary = mutableListOf<String>()

    categories.forEach { category ->
        when (getCategoryType(category)) {
            CategoryType.PRIMARY -> primary.add(category)
            CategoryType.SECONDARY -> secondary.add(category)
            CategoryType.TERTIARY -> tertiary.add(category)
        }
    }
    return Triple(primary.sorted(), secondary.sorted(), tertiary.sorted())
}
fun formatIndianCurrency(amount: Int): String {
    val format = java.text.NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    format.maximumFractionDigits = 0
    val result = format.format(amount)
    return result.replace("â‚¹", "").replace("₹", "").trim()
}

fun formatCompactAmount(amount: Double): String {
    return when {
        amount >= 10_000_000 -> "₹%.1fCr".format(amount / 10_000_000).replace(".0Cr", "Cr")
        amount >= 100_000 -> "₹%.1fL".format(amount / 100_000).replace(".0L", "L")
        amount >= 1_000 -> "₹%.1fk".format(amount / 1_000).replace(".0k", "k")
        else -> "₹%.0f".format(amount)
    }
}

enum class SpendTier {
    NONE,
    LOW,
    MEDIUM,
    HIGH
}

data class SpendRangeConfig(
    val lowThreshold: Double,
    val highThreshold: Double
) {
    fun getTier(amount: Double): SpendTier {
        return when {
            amount <= 0.0 -> SpendTier.NONE
            amount <= lowThreshold -> SpendTier.LOW
            amount <= highThreshold -> SpendTier.MEDIUM
            else -> SpendTier.HIGH
        }
    }
}

/**
 * Calculates adaptive dynamic spend tiers anchored directly around the month's
 * true average daily spend:
 * - Low (Green): < 50% of Average (routine, minor daily expenses)
 * - Moderate (Mild Orange): 50% - 150% of Average (typical daily expenses)
 * - High (Red): > 150% of Average (major / peak expenses)
 *
 * Robust outlier dampening ensures single massive test transactions
 * do not skew the baseline daily average.
 */
fun calculateMonthSpendRange(dailyTotals: Map<*, Double>): SpendRangeConfig {
    val nonZeroSpends = dailyTotals.values.filter { it > 0.0 }.sorted()
    if (nonZeroSpends.isEmpty()) {
        return SpendRangeConfig(lowThreshold = 500.0, highThreshold = 2000.0)
    }

    val median = nonZeroSpends[nonZeroSpends.size / 2]
    // Outlier dampening: cap values that exceed 20x median so single giant transactions don't skew the average
    val maxReasonableMultiplier = 20.0
    val effectiveSpends = if (median > 0.0 && nonZeroSpends.last() > median * maxReasonableMultiplier) {
        val cap = median * maxReasonableMultiplier
        nonZeroSpends.map { if (it > cap) cap else it }
    } else {
        nonZeroSpends
    }

    val effectiveAverage = effectiveSpends.average()

    // Moderate range is 50% to 150% of effective daily average spend
    val rawLow = effectiveAverage * 0.50
    val rawHigh = effectiveAverage * 1.50

    val finalHigh = if (rawHigh <= rawLow) rawLow * 1.5 + 10.0 else rawHigh
    return SpendRangeConfig(
        lowThreshold = rawLow,
        highThreshold = finalHigh
    )
}
