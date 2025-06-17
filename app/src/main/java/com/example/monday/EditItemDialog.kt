package com.example.monday

import android.Manifest
import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.example.monday.TodoItem
import com.example.monday.parseItemText
import com.example.monday.parseCategoryInfo
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.AddPhotoAlternate
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
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
    // Debug log to track when dialog is created and what item it receives
    val itemId = item.id
    val itemText = item.text
    
    Log.d("EditDialog", "Creating dialog for item ID: $itemId with text: $itemText")
    
    // Extract base text and categories directly (not using remember state)
    val (baseText, categories) = parseCategoryInfo(itemText)
    val (name, quantity, price) = parseItemText(baseText)
    
    val context = LocalContext.current
    var tempCameraImageUri by remember { mutableStateOf<Uri?>(null) }
    
    // Track if we need to refresh image data
    var refreshTrigger by remember { mutableStateOf(0) }
    
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    val storagePermissionState = rememberPermissionState(Manifest.permission.READ_EXTERNAL_STORAGE)

    // Get the original images from the item
    val originalImageUris = remember { item.imageUris ?: emptyList() }
    
    // Create a mutable state to track images that have been added but not yet saved to the database
    var pendingImageUris by remember { mutableStateOf<List<String>>(emptyList()) }
    
    // Create a mutable state to track images that have been deleted but not yet saved
    var deletedImageUris by remember { mutableStateOf<List<String>>(emptyList()) }
    
    // Combine the original images, pending images, and deleted images for display
    val displayImageUris = remember(originalImageUris, pendingImageUris, deletedImageUris, refreshTrigger) {
        val result = (originalImageUris + pendingImageUris).filter { uri -> !deletedImageUris.contains(uri) }.distinct()
        Log.d("EditDialog", "Recalculated displayImageUris: ${result.size} images remain after filtering")
        result
    }
    
    // Update the hasImages check to use the combined list
    val hasImages = displayImageUris.isNotEmpty()
    
    var showImageViewer by remember { mutableStateOf(false) }
    
    // Track which image was clicked
    var selectedImageIndex by remember { mutableStateOf(0) }
    
    // Log image information
    LaunchedEffect(item.id, refreshTrigger) {
        Log.d("EditDialog", "Item ${item.id} has ${originalImageUris.size} original images")
        Log.d("EditDialog", "Item ${item.id} has ${pendingImageUris.size} pending images")
        Log.d("EditDialog", "Item ${item.id} has ${deletedImageUris.size} deleted images")
        Log.d("EditDialog", "Item ${item.id} has ${displayImageUris.size} display images")
    }
    
    // Log initial state values
    Log.d("EditDialog", "Initial state - Name: $name, Price: $price, " +
        "PredefinedQty: $quantity, CustomQty: $quantity, Unit: $quantity")
        
    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempCameraImageUri != null) {
            Log.d("ImageDebug", "Camera image captured successfully: $tempCameraImageUri")
            // Add to pending images for immediate display
            pendingImageUris = pendingImageUris + tempCameraImageUri.toString()
            onAddImage(item, tempCameraImageUri!!)
            // Trigger refresh to update UI when database is updated
            refreshTrigger++
        } else {
            Log.e("ImageDebug", "Failed to capture image: success=$success, uri=$tempCameraImageUri")
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            Log.d("ImageDebug", "Image selected from gallery: $uri")
            // Add to pending images for immediate display
            pendingImageUris = pendingImageUris + uri.toString()
            onAddImage(item, uri)
            // Trigger refresh to update UI when database is updated
            refreshTrigger++
        } else {
            Log.e("ImageDebug", "No image selected from gallery")
        }
    }
    
    val requestCameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
        if (cameraGranted) {
            try {
                tempCameraImageUri = createImageFile(context)
                takePictureLauncher.launch(tempCameraImageUri)
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    val storagePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            imagePickerLauncher.launch("image/*")
        }
    }
    
    Log.d("EditDialog", "Parsed data - Name: $name, Quantity: $quantity, Price: $price, Categories: $categories")
    
    // Stateful values
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
    
    // Show image viewer if requested
    if (showImageViewer) {
        Log.d("EditDialog", "Opening image viewer with ${displayImageUris.size} images")
        ImageViewerDialogFull(
            images = displayImageUris,
            onDismiss = { 
                Log.d("EditDialog", "Image viewer dismissed")
                showImageViewer = false 
            },
            onDeleteImage = { imageUrl ->
                // Add to deleted images list instead of calling delete directly
                Log.d("EditDialog", "Marking image for deletion from viewer: $imageUrl")
                deletedImageUris = deletedImageUris + imageUrl
                refreshTrigger++
                
                // Close the image viewer if no images left after deletion
                val remainingImages = displayImageUris.filter { uri -> uri != imageUrl }
                if (remainingImages.isEmpty()) {
                    Log.d("EditDialog", "No more images after deletion, closing viewer")
                    showImageViewer = false
                }
            },
            initialPage = selectedImageIndex
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Expense") },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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
                
                Text("Quantity (Predefined)", style = MaterialTheme.typography.bodyMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(predefinedQuantities) { quantity ->
                        FilterChip(
                            selected = quantity == selectedPredefinedQuantity,
                            onClick = { 
                                selectedPredefinedQuantity = if (selectedPredefinedQuantity == quantity) "" else quantity
                                customQuantityValue = ""
                                selectedCustomUnit = ""
                            },
                            label = { Text(quantity) }
                        )
                    }
                }
                
                Text("Or Custom Quantity", style = MaterialTheme.typography.bodyMedium)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = customQuantityValue, 
                        onValueChange = { 
                            customQuantityValue = it
                            selectedPredefinedQuantity = "" 
                        }, 
                        label = { Text("Value") }, 
                        modifier = Modifier.weight(1f), 
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number, 
                            imeAction = ImeAction.Next
                        ), 
                        singleLine = true
                    )
                    
                    LazyRow(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(customUnits) { unit ->
                            FilterChip(
                                selected = unit == selectedCustomUnit,
                                onClick = { 
                                    selectedCustomUnit = if (selectedCustomUnit == unit) "" else unit
                                    selectedPredefinedQuantity = "" 
                                },
                                label = { Text(unit) }
                            )
                        }
                    }
                }
                
                // Always show the "Add Images" section
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (hasImages) "Attached Images (${displayImageUris.size})" else "Add Images", 
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    Row(
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            if (cameraPermissionState.status.isGranted) {
                                try {
                                    tempCameraImageUri = createImageFile(context)
                                    takePictureLauncher.launch(tempCameraImageUri)
                                } catch (e: Exception) {
                                    Log.e("ImageDebug", "Error creating camera image file", e)
                                    Toast.makeText(context, "Error creating image file: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                requestCameraPermissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
                            }
                        }) {
                            Icon(Icons.Default.AddAPhoto, contentDescription = "Take Picture")
                        }
                        IconButton(onClick = {
                            // Android 13+ doesn't need READ_EXTERNAL_STORAGE for picking images
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                Log.d("ImageDebug", "Android 13+, launching gallery picker directly")
                                imagePickerLauncher.launch("image/*")
                            } else if (storagePermissionState.status.isGranted) {
                                Log.d("ImageDebug", "Storage permission granted, launching gallery picker")
                                imagePickerLauncher.launch("image/*")
                            } else {
                                Log.d("ImageDebug", "Requesting storage permission")
                                storagePermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                            }
                        }) {
                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = "Select from Gallery")
                        }
                    }
                }
                
                // Show images if there are any
                if (hasImages) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        itemsIndexed(displayImageUris) { index, uri ->
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(
                                        width = 1.dp,
                                        color = MaterialTheme.colorScheme.outlineVariant,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                            ) {
                                // Image
                                AsyncImage(
                                    model = Uri.parse(uri),
                                    contentDescription = "Attached image",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clickable { 
                                            selectedImageIndex = index
                                            showImageViewer = true 
                                        }
                                )
                                
                                // Delete button in the corner
                                IconButton(
                                    onClick = {
                                        // Add to deleted images list
                                        Log.d("ImageDeletion", "Marking image for deletion: $uri")
                                        deletedImageUris = deletedImageUris + uri
                                        refreshTrigger++
                                        Toast.makeText(context, "Image marked for deletion", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd) // Align button to top end
                                        .size(24.dp)
                                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.8f), CircleShape)
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Delete Image",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                } else if (deletedImageUris.isNotEmpty()) {
                    // Show a message when all images are deleted
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
                    
                    // Create the new base text
                    val newBaseText = if (finalQuantityString != null) {
                        "${itemName.trim()} ($finalQuantityString) - ₹${itemPrice.trim()}"
                    } else {
                        "${itemName.trim()} - ₹${itemPrice.trim()}"
                    }
                    
                    // Add back the category metadata if it exists
                    val newText = if (categories.isNotEmpty()) {
                        "$newBaseText|CATS:${categories.joinToString(",")}"
                    } else {
                        newBaseText
                    }
                    
                    // Process any deleted images - do this first
                    if (deletedImageUris.isNotEmpty()) {
                        Log.d("ImageDeletion", "Processing ${deletedImageUris.size} deleted images")
                        deletedImageUris.forEach { imageUrl ->
                            if (imageUrl.isNotEmpty()) {
                                Log.d("ImageDeletion", "Deleting image: $imageUrl")
                                onDeleteImage(item, imageUrl)
                            }
                        }
                        
                        // Show confirmation toast
                        val message = if (deletedImageUris.size == 1) "1 image deleted" else "${deletedImageUris.size} images deleted"
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    }
                    
                    Log.d("EditDialog", "Saving - New text: $newText")
                    onConfirm(newText)
                    onDismiss()
                },
                enabled = itemName.isNotBlank() && itemPrice.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = { 
            TextButton(
                onClick = {
                    // Clear deleted images list when canceling
                    if (deletedImageUris.isNotEmpty()) {
                        Toast.makeText(context, "Image deletion canceled", Toast.LENGTH_SHORT).show()
                    }
                    onDismiss()
                }
            ) { Text("Cancel") }
        }
    )
} 