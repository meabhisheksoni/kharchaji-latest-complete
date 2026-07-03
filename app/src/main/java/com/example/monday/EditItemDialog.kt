package com.example.monday
import com.example.monday.TodoViewModel
import com.example.monday.core.utils.*
import com.example.monday.data.models.TodoItem
import com.example.monday.ui.components.ImagePickerSection
import com.example.monday.ui.components.QuantityPickerSection

import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditItemDialog(
    item: TodoItem,
    onDismiss: () -> Unit,
    onConfirm: (updatedText: String) -> Unit,
    predefinedQuantities: List<String>,
    customUnits: List<String>,
    onDeleteImage: (TodoItem, String) -> Unit = { _, _ -> },
    onAddImage: (TodoItem, Uri) -> Unit
) {
    // Extract base text and categories
    val (baseText, categories) = parseCategoryInfo(item.text)
    val (name, quantity, price) = parseItemText(baseText)

    val context = LocalContext.current

    // Image state — pending adds and pending deletes tracked separately
    val originalImageUris = remember { item.imageUris ?: emptyList() }
    var pendingImageUris by remember { mutableStateOf<List<String>>(emptyList()) }
    var deletedImageUris by remember { mutableStateOf<List<String>>(emptyList()) }
    var refreshTrigger by remember { mutableStateOf(0) }

    val displayImageUris = remember(originalImageUris, pendingImageUris, deletedImageUris, refreshTrigger) {
        (originalImageUris + pendingImageUris).filter { uri -> !deletedImageUris.contains(uri) }.distinct()
    }

    var showImageViewer by remember { mutableStateOf(false) }
    var selectedImageIndex by remember { mutableStateOf(0) }

    // Stateful form values — keyed by item.id to prevent stale state on recomposition
    var itemName by remember(item.id) { mutableStateOf(name) }
    var itemPrice by remember(item.id) { mutableStateOf(price) }
    var selectedPredefinedQuantity by remember(item.id) {
        mutableStateOf(if (quantity != null && predefinedQuantities.contains(quantity)) quantity else "")
    }
    var customQuantityValue by remember(item.id) {
        mutableStateOf(if (quantity != null && !predefinedQuantities.contains(quantity))
            quantity.filter { it.isDigit() || it == '.' } else "")
    }
    var selectedCustomUnit by remember(item.id) {
        var unit = ""
        if (quantity != null && !predefinedQuantities.contains(quantity)) {
            customUnits.sortedByDescending { it.length }.forEach { u ->
                if (quantity.endsWith(u, ignoreCase = true)) {
                    unit = u
                    return@forEach
                }
            }
        }
        mutableStateOf(if (unit.isNotEmpty()) unit else
            (if (customQuantityValue.isNotEmpty() && quantity?.filterNot { it.isDigit() || it == '.' }?.isEmpty() == true)
                "items" else ""))
    }

    // Full-screen image viewer
    if (showImageViewer) {
        ImageViewerDialogFull(
            images = displayImageUris,
            onDismiss = { showImageViewer = false },
            onDeleteImage = { imageUrl ->
                deletedImageUris = deletedImageUris + imageUrl
                refreshTrigger++
                // Auto-close viewer if no images remain
                if (displayImageUris.filter { it != imageUrl }.isEmpty()) {
                    showImageViewer = false
                }
            },
            initialPage = selectedImageIndex
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = { Text("Edit Expense") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Name field
                OutlinedTextField(
                    value = itemName,
                    onValueChange = { itemName = it },
                    label = { Text("Item Name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Next
                    )
                )

                // Price field
                OutlinedTextField(
                    value = itemPrice,
                    onValueChange = { itemPrice = it },
                    label = { Text("Price (₹)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next
                    )
                )

                // Quantity picker — shared component
                QuantityPickerSection(
                    predefinedQuantities = predefinedQuantities,
                    customUnits = customUnits,
                    selectedPredefined = selectedPredefinedQuantity,
                    customValue = customQuantityValue,
                    selectedUnit = selectedCustomUnit,
                    onPredefinedChange = { newVal ->
                        selectedPredefinedQuantity = newVal
                        if (newVal.isNotBlank()) {
                            customQuantityValue = ""
                            selectedCustomUnit = ""
                        }
                    },
                    onCustomValueChange = { newVal ->
                        customQuantityValue = newVal
                        selectedPredefinedQuantity = ""
                    },
                    onUnitChange = { newUnit ->
                        selectedCustomUnit = newUnit
                        selectedPredefinedQuantity = ""
                    }
                )

                // Image section divider
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))

                // Image picker — shared component
                ImagePickerSection(
                    imageUris = displayImageUris,
                    onImageAdded = { uri ->
                        pendingImageUris = pendingImageUris + uri.toString()
                        onAddImage(item, uri)
                        refreshTrigger++
                    },
                    onImageDeleted = { uri ->
                        deletedImageUris = deletedImageUris + uri
                        refreshTrigger++
                        Toast.makeText(context, "Image marked for deletion", Toast.LENGTH_SHORT).show()
                    },
                    onImageClicked = { index ->
                        selectedImageIndex = index
                        showImageViewer = true
                    }
                )

                // Show message when all images are deleted
                if (displayImageUris.isEmpty() && deletedImageUris.isNotEmpty()) {
                    Text(
                        text = "All images removed. Click Save to confirm deletion.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    // Build final quantity string
                    val finalQuantityString = when {
                        selectedPredefinedQuantity.isNotBlank() -> selectedPredefinedQuantity
                        customQuantityValue.isNotBlank() -> {
                            when {
                                selectedCustomUnit.isNotBlank() && selectedCustomUnit.lowercase() != "items" ->
                                    customQuantityValue + selectedCustomUnit.lowercase()
                                else -> customQuantityValue
                            }
                        }
                        else -> null
                    }

                    // Build final text
                    val newBaseText = if (finalQuantityString != null) {
                        "${itemName.trim()} ($finalQuantityString) - ₹${itemPrice.trim()}"
                    } else {
                        "${itemName.trim()} - ₹${itemPrice.trim()}"
                    }
                    val newText = if (categories.isNotEmpty()) {
                        "$newBaseText|CATS:${categories.joinToString(",")}"
                    } else {
                        newBaseText
                    }

                    // Process deleted images before saving
                    if (deletedImageUris.isNotEmpty()) {
                        deletedImageUris.forEach { imageUrl ->
                            if (imageUrl.isNotEmpty()) {
                                onDeleteImage(item, imageUrl)
                            }
                        }
                        val message = if (deletedImageUris.size == 1) "1 image deleted" else "${deletedImageUris.size} images deleted"
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    }

                    onConfirm(newText)
                    onDismiss()
                },
                enabled = itemName.isNotBlank() && itemPrice.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    if (deletedImageUris.isNotEmpty()) {
                        Toast.makeText(context, "Image deletion canceled", Toast.LENGTH_SHORT).show()
                    }
                    onDismiss()
                }
            ) { Text("Cancel") }
        }
    )
}
