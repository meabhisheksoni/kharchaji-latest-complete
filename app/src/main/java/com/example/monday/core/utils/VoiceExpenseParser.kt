package com.example.monday.core.utils

import java.util.Locale

data class ParsedVoiceExpense(
    val itemName: String,
    val amount: Double,
    val quantity: String? = null,
    val unit: String? = null,
    val originalText: String
)

object VoiceExpenseParser {

    private val hindiNumberWords = mapOf(
        // Units
        "ek" to 1.0, "एक" to 1.0, "one" to 1.0,
        "do" to 2.0, "दो" to 2.0, "two" to 2.0,
        "teen" to 3.0, "तीन" to 3.0, "three" to 3.0,
        "chaar" to 4.0, "char" to 4.0, "चार" to 4.0, "four" to 4.0,
        "paanch" to 5.0, "panch" to 5.0, "पाँच" to 5.0, "पांच" to 5.0, "five" to 5.0,
        "chhah" to 6.0, "chhe" to 6.0, "che" to 6.0, "छह" to 6.0, "छ" to 6.0, "six" to 6.0,
        "saat" to 7.0, "sat" to 7.0, "सात" to 7.0, "seven" to 7.0,
        "aath" to 8.0, "ath" to 8.0, "आठ" to 8.0, "eight" to 8.0,
        "nau" to 9.0, "no" to 9.0, "नौ" to 9.0, "nine" to 9.0,
        "das" to 10.0, "dus" to 10.0, "दस" to 10.0, "ten" to 10.0,

        // Teens
        "gyarah" to 11.0, "gyara" to 11.0, "ग्यारह" to 11.0, "eleven" to 11.0,
        "barah" to 12.0, "bara" to 12.0, "बारह" to 12.0, "twelve" to 12.0,
        "terah" to 13.0, "tera" to 13.0, "तेरह" to 13.0, "thirteen" to 13.0,
        "chaudah" to 14.0, "chauda" to 14.0, "चौदह" to 14.0, "fourteen" to 14.0,
        "pandrah" to 15.0, "pandra" to 15.0, "पंद्रह" to 15.0, "fifteen" to 15.0,
        "solah" to 16.0, "sola" to 16.0, "सोलह" to 16.0, "sixteen" to 16.0,
        "satrah" to 17.0, "satra" to 17.0, "सत्रह" to 17.0, "seventeen" to 17.0,
        "atharah" to 18.0, "athara" to 18.0, "अठारह" to 18.0, "eighteen" to 18.0,
        "unnis" to 19.0, "उन्नीस" to 19.0, "nineteen" to 19.0,

        // Tens
        "bees" to 20.0, "bis" to 20.0, "बीस" to 20.0, "twenty" to 20.0,
        "ikkis" to 21.0, "इक्कीस" to 21.0,
        "baais" to 22.0, "बाईस" to 22.0,
        "teis" to 23.0, "तेईस" to 23.0,
        "chaubis" to 24.0, "चौबीस" to 24.0,
        "pachis" to 25.0, "pachees" to 25.0, "पच्चीस" to 25.0,
        "chabbis" to 26.0, "छब्बीस" to 26.0,
        "sattais" to 27.0, "सत्ताईस" to 27.0,
        "athais" to 28.0, "अट्ठाईस" to 28.0,
        "unantis" to 29.0, "उनतीस" to 29.0,

        "tees" to 30.0, "तीस" to 30.0, "thirty" to 30.0,
        "iktis" to 31.0, "इकतीस" to 31.0,
        "battis" to 32.0, "बत्तीस" to 32.0,
        "tetis" to 33.0, "तैंतीस" to 33.0,
        "chautis" to 34.0, "चौंतीस" to 34.0,
        "paintis" to 35.0, "पैंतीस" to 35.0,
        "chhattis" to 36.0, "छत्तीस" to 36.0,
        "saintis" to 37.0, "सैंतीस" to 37.0,
        "adhtis" to 38.0, "अड़तीस" to 38.0,
        "untalis" to 39.0, "उनतालीस" to 39.0,

        "chalis" to 40.0, "चालीस" to 40.0, "forty" to 40.0,
        "iktalis" to 41.0, "इकतालीस" to 41.0,
        "bayalis" to 42.0, "बयालीस" to 42.0,
        "tentalis" to 43.0, "तैंतालीस" to 43.0,
        "chawalis" to 44.0, "चवालीस" to 44.0,
        "paintalis" to 45.0, "पैंतालीस" to 45.0,
        "chhiyalis" to 46.0, "छियालीस" to 46.0,
        "saintalis" to 47.0, "सैंतालीस" to 47.0,
        "adhtalis" to 48.0, "अड़तालीस" to 48.0,
        "unchas" to 49.0, "उनचास" to 49.0,

        "pachaas" to 50.0, "pachas" to 50.0, "पचास" to 50.0, "fifty" to 50.0,
        "ikkyawan" to 51.0, "इक्यावन" to 51.0,
        "bawan" to 52.0, "बावन" to 52.0,
        "tirepan" to 53.0, "तिरेपन" to 53.0,
        "chauwan" to 54.0, "चौवन" to 54.0,
        "pachpan" to 55.0, "पचपन" to 55.0,
        "chhappan" to 56.0, "छप्पन" to 56.0,
        "sattawan" to 57.0, "सत्तावन" to 57.0,
        "atthawan" to 58.0, "अट्ठावन" to 58.0,
        "unsath" to 59.0, "उनसठ" to 59.0,

        "saath" to 60.0, "sath" to 60.0, "साठ" to 60.0, "sixty" to 60.0,
        "iksath" to 61.0, "इकसठ" to 61.0,
        "basath" to 62.0, "बासठ" to 62.0,
        "tirsath" to 63.0, "तिरसठ" to 63.0,
        "chaunsath" to 64.0, "चौंसठ" to 64.0,
        "painsath" to 65.0, "पैंसठ" to 65.0,
        "chhyasath" to 66.0, "छियासठ" to 66.0,
        "sarsath" to 67.0, "सरसठ" to 67.0,
        "adsath" to 68.0, "अड़सठ" to 68.0,
        "unhattar" to 69.0, "उनहत्तर" to 69.0,

        "sattar" to 70.0, "सत्तर" to 70.0, "seventy" to 70.0,
        "ikhattar" to 71.0, "इकहत्तर" to 71.0,
        "bahattar" to 72.0, "बहत्तर" to 72.0,
        "tihattar" to 73.0, "तिहत्तर" to 73.0,
        "chauhattar" to 74.0, "चौहत्तर" to 74.0,
        "pichattar" to 75.0, "pachattar" to 75.0, "पचहत्तर" to 75.0,
        "chhihattar" to 76.0, "छिहत्तर" to 76.0,
        "sathattar" to 77.0, "सतहत्तर" to 77.0,
        "athattar" to 78.0, "अठहत्तर" to 78.0,
        "unasi" to 79.0, "उनासी" to 79.0,

        "assi" to 80.0, "अस्सी" to 80.0, "eighty" to 80.0,
        "ikyasi" to 81.0, "इक्यासी" to 81.0,
        "bayasi" to 82.0, "बयासी" to 82.0,
        "tirasi" to 83.0, "तिरासी" to 83.0,
        "chaurasi" to 84.0, "चौरासी" to 84.0,
        "pachasi" to 85.0, "पचासी" to 85.0,
        "chhiyasi" to 86.0, "छियासी" to 86.0,
        "sattasi" to 87.0, "सत्तासी" to 87.0,
        "athasi" to 88.0, "अठासी" to 88.0,
        "navasi" to 89.0, "नवासी" to 89.0,

        "nabbe" to 90.0, "nabbey" to 90.0, "नब्बे" to 90.0, "ninety" to 90.0,
        "ikyanve" to 91.0, "इक्यानवे" to 91.0,
        "banve" to 92.0, "बानवे" to 92.0,
        "tiranve" to 93.0, "तिरानवे" to 93.0,
        "chauranve" to 94.0, "चौरानवे" to 94.0,
        "pichanve" to 95.0, "पिचानवे" to 95.0,
        "chhiyanve" to 96.0, "छियानवे" to 96.0,
        "sattanve" to 97.0, "सत्तानवे" to 97.0,
        "atthanve" to 98.0, "अट्ठानवे" to 98.0,
        "ninyanve" to 99.0, "निन्यानवे" to 99.0,

        // Common Multipliers / Scales
        "sau" to 100.0, "hundred" to 100.0, "सौ" to 100.0,
        "dedh sau" to 150.0, "derh sau" to 150.0, "डेढ़ सौ" to 150.0,
        "dhaai sau" to 250.0, "dhai sau" to 250.0, "ढाई सौ" to 250.0,
        "hazar" to 1000.0, "hazaar" to 1000.0, "thousand" to 1000.0, "हजार" to 1000.0, "हज़ार" to 1000.0, "k" to 1000.0
    )

    private val noiseWords = setOf(
        "rupay", "rupaye", "rupees", "rupee", "rs", "inr", "₹",
        "ka", "ke", "ki", "ko", "me", "mein", "par",
        "spent", "paid", "diye", "kharch", "kharcha", "cost", "total", "bought", "buy", "leke", "aaya"
    )

    /**
     * Parses a raw spoken string like "aata pachaas rupay" or "chai 20" into ParsedVoiceExpense
     */
    fun parse(rawInput: String): ParsedVoiceExpense {
        val trimmed = rawInput.trim()
        if (trimmed.isBlank()) {
            return ParsedVoiceExpense("", 0.0, originalText = rawInput)
        }

        val lower = trimmed.lowercase(Locale.getDefault())

        // 1. Check for explicit digits first (e.g., "elevator... 500 rs", "aata 50", "petrol 100 rs")
        val digitMatch = Regex("""\b\d+(\.\d{1,2})?\b""").find(lower)
        if (digitMatch != null) {
            val amount = digitMatch.value.toDoubleOrNull() ?: 0.0
            val cleanedWords = lower.replace(digitMatch.value, " ")
                .replace(Regex("""[.,\/#!$%\^&\*;:{}=\-_`~()?]"""), " ")
                .split(Regex("""\s+"""))
                .map { it.trim() }
                .filter { it.isNotBlank() && !noiseWords.contains(it) }

            val itemName = cleanedWords.joinToString(" ").replaceFirstChar { it.uppercase() }
            return ParsedVoiceExpense(
                itemName = if (itemName.isBlank()) "Expense" else itemName,
                amount = amount,
                originalText = rawInput
            )
        }

        // 2. Check for compound phrases like "dedh sau", "dhaai sau", "paanch sau", "do hazar"
        var recognizedAmount = 0.0
        var matchedPhrase = ""

        // Multiplier phrases first
        val compoundPatterns = listOf(
            Regex("""\b(dedh|derh|डेढ़)\s*(sau|सौ)\b""") to 150.0,
            Regex("""\b(dhaai|dhai|ढाई)\s*(sau|सौ)\b""") to 250.0,
            Regex("""\b(ek|do|teen|chaar|char|paanch|panch|chhah|chhe|saat|sat|aath|ath|nau|das|एक|दो|तीन|चार|पाँच|पांच|छह|सात|आठ|नौ|दस|\d+)\s*(sau|सौ|hundred)\b""") to 100.0,
            Regex("""\b(ek|do|teen|chaar|char|paanch|panch|chhah|chhe|saat|sat|aath|ath|nau|das|एक|दो|तीन|चार|पाँच|पांच|छह|सात|आठ|नौ|दस|\d+)\s*(hazar|hazaar|हजार|हज़ार|thousand)\b""") to 1000.0
        )

        for ((regex, multiplier) in compoundPatterns) {
            val match = regex.find(lower)
            if (match != null) {
                matchedPhrase = match.value
                val prefixWord = match.groupValues.getOrNull(1)?.trim() ?: ""
                val prefixVal = hindiNumberWords[prefixWord] ?: prefixWord.toDoubleOrNull() ?: 1.0
                recognizedAmount = prefixVal * multiplier
                break
            }
        }

        // 3. If not matched compound, check individual words
        if (recognizedAmount == 0.0) {
            val tokens = lower.split(Regex("""\s+"""))
            for (token in tokens) {
                if (hindiNumberWords.containsKey(token)) {
                    recognizedAmount = hindiNumberWords[token] ?: 0.0
                    matchedPhrase = token
                    break
                }
            }
        }

        // 4. Extract Item Name by removing matched number phrase and noise words
        val textWithoutAmount = if (matchedPhrase.isNotBlank()) {
            lower.replaceFirst(matchedPhrase, " ")
        } else {
            lower
        }

        val itemTokens = textWithoutAmount
            .replace(Regex("""[.,\/#!$%\^&\*;:{}=\-_`~()?]"""), " ")
            .split(Regex("""\s+"""))
            .map { it.trim() }
            .filter { it.isNotBlank() && !noiseWords.contains(it) }

        val finalItemName = itemTokens.joinToString(" ").replaceFirstChar { it.uppercase() }

        return ParsedVoiceExpense(
            itemName = if (finalItemName.isBlank()) "Cash Expense" else finalItemName,
            amount = recognizedAmount,
            originalText = rawInput
        )
    }
}
