package com.example.monday.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * Reusable quantity picker — predefined chip row + custom value + unit chips.
 * Shared between EditItemDialog and AddNewExpenseScreenV2.
 *
 * @param predefinedQuantities List of predefined quantities (e.g., "250g", "500g", "1kg").
 * @param customUnits List of units for custom quantity (e.g., "kg", "g", "items").
 * @param selectedPredefined Currently selected predefined quantity.
 * @param customValue Current custom quantity value (digits only).
 * @param selectedUnit Currently selected unit.
 * @param onPredefinedChange Called when a predefined chip is toggled.
 * @param onCustomValueChange Called when custom quantity text changes.
 * @param onUnitChange Called when a unit chip is toggled.
 * @param onDone Optional keyboard "Done" action callback.
 * @param showLabels Whether to show section labels ("Quantity (Predefined)", "Or Custom Quantity").
 */
@Composable
fun QuantityPickerSection(
    predefinedQuantities: List<String>,
    customUnits: List<String>,
    selectedPredefined: String,
    customValue: String,
    selectedUnit: String,
    onPredefinedChange: (String) -> Unit,
    onCustomValueChange: (String) -> Unit,
    onUnitChange: (String) -> Unit,
    onDone: (() -> Unit)? = null,
    showLabels: Boolean = true
) {
    // Predefined quantities
    if (showLabels) {
        Text("Quantity (Predefined)", style = MaterialTheme.typography.bodyMedium)
    }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(predefinedQuantities) { quantity ->
            FilterChip(
                selected = quantity == selectedPredefined,
                onClick = {
                    val newValue = if (selectedPredefined == quantity) "" else quantity
                    onPredefinedChange(newValue)
                },
                label = { Text(quantity) }
            )
        }
    }

    // Custom quantity + unit
    if (showLabels) {
        Text("Or Custom Quantity", style = MaterialTheme.typography.bodyMedium)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = customValue,
            onValueChange = { onCustomValueChange(it) },
            label = { Text("Value") },
            modifier = Modifier.weight(1f),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = if (onDone != null) ImeAction.Done else ImeAction.Next
            ),
            keyboardActions = if (onDone != null) {
                KeyboardActions(onDone = { onDone() })
            } else {
                KeyboardActions.Default
            },
            singleLine = true
        )

        LazyRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(customUnits) { unit ->
                FilterChip(
                    selected = unit == selectedUnit,
                    onClick = {
                        val newUnit = if (selectedUnit == unit) "" else unit
                        onUnitChange(newUnit)
                    },
                    label = { Text(unit) }
                )
            }
        }
    }
}
