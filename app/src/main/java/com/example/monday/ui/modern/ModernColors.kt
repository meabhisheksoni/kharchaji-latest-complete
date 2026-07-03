package com.example.monday.ui.modern

import androidx.compose.ui.graphics.Color

// ─── Eggshell / Eggnog Color Palette (from globals.css) ─────
object ModernColors {
    val Eggshell = Color(0xFFF5F1E6)
    val SoftCream = Color(0xFFFFFBF0)
    val CardBg = Color(0xFFF9F5EA)
    val EggnogDark = Color(0xFFB89355)
    val EggnogLight = Color(0xFFDDC08E)

    // Accent colors
    val Groceries = Color(0xFF439D46)
    val Transport = Color(0xFF41C86A)
    val Utilities = Color(0xFF00BCD4)
    val Destructive = Color(0xFFD4183D)

    // AI Insight chip
    val ChipBg = Color(0xFFEFE5FF)
    val ChipBorder = Color(0xFFD5C1FF)
    val ChipText = Color(0xFF7B5CFF)

    // Date navigation
    val DateSelected = Color(0xFF66AFF0)
    val DateUnselected = Color(0xFFE0DFDC)
    val TodayButton = Color(0xFFC8A437)
    val DateBorder = Color(0xFFC4C1B5)
    val DateText = Color(0xFFA18A58)

    // Card & borders
    val CardBorder = Color(0xFFC2BEB7)

    // Drawer specific colors
    val DrawerHeaderBrown = Color(0xFF8B5E41)
    val DrawerSignOutPink = Color(0xFFFF5C77)
    val DrawerProGreen = Color(0xFF90A959)
    val DrawerTextMuted = Color(0xFF757575)

    // Category icon background tints
    val GroceriesBgTint = Color(0xFFE8F5E9)
    val TransportBgTint = Color(0xFFE0F7FA)
    val UtilitiesBgTint = Color(0xFFFFF3E0)
    val FoodBgTint = Color(0xFFFFEBEE)
    val HealthBgTint = Color(0xFFE8F5E9)
    val EntertainmentBgTint = Color(0xFFE3F2FD)
    val ShoppingBgTint = Color(0xFFFCE4EC)
    val BillsBgTint = Color(0xFFFFF9C4)
    val OtherBgTint = Color(0xFFF3E5F5)

    // Category emoji + color maps
    val categoryEmojis = mapOf(
        "Groceries" to "🛒", "Transport" to "🚗", "Utilities" to "⚡",
        "Food" to "🍕", "Health" to "💊", "Entertainment" to "🎬",
        "Shopping" to "🛍️", "Subscriptions" to "📱", "Home" to "🏠",
        "Bills" to "📄", "Education" to "📚", "Other" to "📦"
    )

    val categoryColors = mapOf(
        "Groceries" to Groceries, "Transport" to Transport,
        "Utilities" to Utilities, "Food" to Transport,
        "Health" to Groceries, "Entertainment" to Utilities,
        "Shopping" to Groceries, "Subscriptions" to Utilities,
        "Home" to Groceries, "Bills" to Utilities,
        "Education" to Utilities, "Other" to Transport
    )

    val categoryBgTints = mapOf(
        "Groceries" to GroceriesBgTint, "Transport" to TransportBgTint,
        "Utilities" to UtilitiesBgTint, "Food" to FoodBgTint,
        "Health" to HealthBgTint, "Entertainment" to EntertainmentBgTint,
        "Shopping" to ShoppingBgTint, "Subscriptions" to UtilitiesBgTint,
        "Home" to GroceriesBgTint, "Bills" to BillsBgTint,
        "Education" to UtilitiesBgTint, "Other" to OtherBgTint
    )
}
