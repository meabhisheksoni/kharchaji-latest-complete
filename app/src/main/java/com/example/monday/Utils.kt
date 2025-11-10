package com.example.monday
import android.util.Log
import java.util.Locale // For String.format Locale

// Import data classes used by the new functions
import com.example.monday.TodoItem
import com.example.monday.RecordItem

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
        else -> CategoryType.TERTIARY // Default to tertiary for unknown categories
    }
}

fun parsePrice(text: String): Double {
    // First, remove any category metadata
    val cleanedText = text.split("|CATS:").first()

    // Extract price from the clean text
    val priceString = cleanedText.split(" - ₹").lastOrNull()?.trim() ?: return 0.0
    return priceString.toDoubleOrNull() ?: 0.0
}

fun parseItemText(text: String): Triple<String, String?, String> {
    // First, remove any category metadata
    val cleanedText = text.split("|CATS:").first()

    val parts = cleanedText.split(" - ₹")
    if (parts.size < 2) return Triple(cleanedText, null, "0.0")

    val nameAndMaybeQuantity = parts[0].trim()
    var price = parts[1].trim()

    // Extract quantity from nameAndMaybeQuantity
    val quantityMatch = Regex("""\((.*?)\)""").find(nameAndMaybeQuantity)
    val extractedQuantityString = quantityMatch?.groupValues?.get(1)
    val name = nameAndMaybeQuantity.replace("""\s*\(.*?\)""".toRegex(), "").trim()

    // Clean price by removing any trailing non-numeric characters
    price = Regex("""[^\d.]+$""").replace(price, "").trim()

    return Triple(name, extractedQuantityString, price)
}

// Function to parse item text and extract category information
fun parseCategoryInfo(itemText: String): Pair<String, List<String>> {
    // Add debug logging
    Log.d("CategoryDebug", "Parsing categories from: $itemText")

    return if (itemText.contains("|CATS:")) {
        try {
            val parts = itemText.split("|CATS:")
            val displayText = parts[0]
            val categoryNames = parts[1].split(",").map { it.trim() }

            Log.d("CategoryDebug", "Found categories: $categoryNames")
            displayText to categoryNames
        } catch (e: Exception) {
            Log.e("CategoryDebug", "Error parsing categories: ${e.message}")
            itemText to emptyList() // Default to empty list on error
        }
    } else {
        Log.d("CategoryDebug", "No categories found in item text")
        itemText to emptyList()
    }
}

// --- ADDING NEW FUNCTIONS BELOW THIS LINE ---

// New helper functions moved from DedicatedExpenseListScreen
fun todoItemToRecordItem(todoItem: TodoItem): RecordItem {
    val (name, quantity, price) = parseItemText(todoItem.text) // parseItemText is already in this file
    return RecordItem(
        description = name,
        quantity = quantity,
        price = price,
        isChecked = todoItem.isDone,
        categories = todoItem.categories,
        imageUris = todoItem.imageUris, // Copy the image URIs
        sourceItemId = todoItem.id // Include the TodoItem ID
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
    
    // Add categories if they exist
    return if (!recordItem.categories.isNullOrEmpty()) {
        "$baseText|CATS:${recordItem.categories!!.joinToString(",")}"
    } else {
        baseText
    }
}

// --- DATE UTILITY FUNCTIONS ---

fun Long.toLocalDate(): java.time.LocalDate {
    return java.time.Instant.ofEpochMilli(this)
        .atZone(java.time.ZoneId.systemDefault())
        .toLocalDate()
}

fun java.time.LocalDate.toEpochMilli(): Long {
    return this.atStartOfDay(java.time.ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()
}

fun java.time.LocalDate.formatForDisplay(pattern: String = "MMM dd, yyyy"): String {
    return this.format(java.time.format.DateTimeFormatter.ofPattern(pattern))
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
