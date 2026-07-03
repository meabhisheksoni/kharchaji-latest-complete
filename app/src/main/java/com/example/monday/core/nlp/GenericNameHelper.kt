package com.example.monday.core.nlp

import java.util.Locale

object GenericNameHelper {
    // A robust list of common Indian FMCG and E-commerce brands/noise words
    private val brandList = listOf(
        "amul", "mother dairy", "aashirvaad", "fortune", "tata", "nestle", 
        "britania", "britannia", "parle", "haldiram", "haldiram's", "lays", 
        "kurkure", "surf excel", "ariel", "tide", "nirma", "lifebuoy", 
        "dettol", "pears", "dove", "pantene", "head and shoulders", 
        "clinic plus", "sunflower", "saffola", "dhara", "पतंजलि", "patanjali", 
        "dabur", "himalaya", "maggi", "yippee", "cadbury", "dairy milk", 
        "kitkat", "munch", "perk", "five star", "5 star", "flipkart", 
        "smartbuy", "amazon", "amazon basics", "blinkit", "grofers", "zepto", 
        "swiggy", "instamart", "zomato", "bigbasket", "bb royal", "bb popular",
        "gowardhan", "amulya", "heritage", "nandini", "safal", "kissan",
        "maggi", "sunfeast", "bingo", "paper boat", "real", "tropicana"
    )
    
    // Additional promotional/descriptive stop words to strip out
    private val stopWords = listOf(
        "buy", "get", "free", "offer", "discount", "sale", "new", "combo", 
        "super", "saver", "premium", "classic", "regular", "fresh", "pure", 
        "natural", "organic", "quality", "best", "top", "choice", "select",
        "kachi ghani", "extract", "rich", "tasty", "healthy", "special",
        "original", "authentic", "extra", "large", "small", "medium"
    )

    fun normalize(raw: String): String {
        var s = raw.lowercase(Locale.getDefault())
        
        // 1. Remove all punctuation but keep alphanumeric and spaces
        s = s.replace(Regex("[^a-z0-9\\s]"), " ").trim()
        
        // 2. Remove Weight and Volume tokens exactly as the user specified
        // Matches e.g., 1kg, 500g, 1litre, 2ltr, 250 ml, pack, pcs, 1l, 500ml
        val weightRegex = Regex("\\b([0-9]+)?\\s*(kg|g|gm|litre|ltr|ml|pack|pcs|piece|unit|dozen)\\b")
        s = s.replace(weightRegex, " ")
        s = s.replace(Regex("\\b[0-9]+[l]\\b"), " ") // Catch isolated 1l, 2l, 5l
        
        // 3. Strip Brands
        for (brand in brandList) {
            s = s.replace(Regex("\\b$brand\\b"), " ")
        }
        
        // 4. Strip Promotional/Descriptive Stop Words
        for (word in stopWords) {
            s = s.replace(Regex("\\b$word\\b"), " ")
        }
        // 5. Remove any standalone floating numbers left behind (like "5" from "5 pack")
        s = s.replace(Regex("\\b[0-9]+\\b"), " ")
        // 6. Collapse multiple spaces into a single space
        s = s.replace(Regex("\\s+"), " ").trim()
        
        // 7. Extract the Top 2 words and Title Case them for a beautiful UI presentation
        val words = s.split(" ").filter { it.isNotBlank() }.take(2)
        val genericName = words.joinToString(" ") { 
            it.replaceFirstChar { char -> 
                if (char.isLowerCase()) char.titlecase(Locale.getDefault()) else char.toString() 
            }
        }.trim()
        // Anti-Fragile Fail-Safe: If the regex stripped absolutely everything, return the original truncated
        return genericName.ifBlank {
            raw.take(20).trim()
        }
    }
}

