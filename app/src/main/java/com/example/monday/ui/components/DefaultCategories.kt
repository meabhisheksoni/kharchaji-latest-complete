package com.example.monday.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import com.example.monday.ExpenseCategory

// Default category data with icons
object DefaultCategories {
    // Primary categories (people)
    val primaryCategories = listOf(
        ExpenseCategory("Priya", Icons.Outlined.Person),
        ExpenseCategory("Papa", Icons.Outlined.Person),
        ExpenseCategory("Abhishek", Icons.Outlined.Person),
        ExpenseCategory("Family", Icons.Outlined.People)
    )
    
    // Secondary categories (purposes)
    val secondaryCategories = listOf(
        ExpenseCategory("Home", Icons.Outlined.Home),
        ExpenseCategory("Travel", Icons.Outlined.Flight),
        ExpenseCategory("Wedding", Icons.Outlined.Celebration),
        ExpenseCategory("Education", Icons.Outlined.School)
    )
    
    // Tertiary categories (specific expense types)
    val tertiaryCategories = listOf(
        ExpenseCategory("Food", Icons.Outlined.Restaurant),
        ExpenseCategory("Shopping", Icons.Outlined.LocalMall),
        ExpenseCategory("Eating Out", Icons.Outlined.DinnerDining),
        ExpenseCategory("Grocery", Icons.Outlined.ShoppingCart),
        ExpenseCategory("Give", Icons.Outlined.Redeem),
        ExpenseCategory("Maintenance", Icons.Outlined.Handyman),
        ExpenseCategory("Transport", Icons.Outlined.DirectionsCar),
        ExpenseCategory("Bills", Icons.Outlined.Receipt),
        ExpenseCategory("Entertainment", Icons.Outlined.Movie),
        ExpenseCategory("Miscellaneous", Icons.Outlined.MoreHoriz)
    )
} 