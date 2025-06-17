package com.example.monday

import android.Manifest
import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun AddNewExpenseScreen(onNextClick: () -> Unit, todoViewModel: TodoViewModel) {
    // Add debug logs
    Log.d("AddNewExpenseScreen", "AddNewExpenseScreen composable started")
    
    var itemName by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var selectedQuantity by remember { mutableStateOf("") }
    var customQuantity by remember { mutableStateOf("") }
    var selectedUnit by remember { mutableStateOf("") }
    
    // Image handling
    var pendingImageUris by remember { mutableStateOf<List<String>>(emptyList()) }
    var tempCameraImageUri by remember { mutableStateOf<Uri?>(null) }
    var showImageViewer by remember { mutableStateOf(false) }
    
    val context = LocalContext.current
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    val storagePermissionState = rememberPermissionState(Manifest.permission.READ_EXTERNAL_STORAGE)
    val scope = rememberCoroutineScope()

    Log.d("AddNewExpenseScreen", "Camera permission status: ${cameraPermissionState.status.isGranted}")
    Log.d("AddNewExpenseScreen", "Storage permission status: ${storagePermissionState.status.isGranted}")

    val predefinedQuantities = listOf("250g", "500g", "1kg", "1.5kg", "2kg")
    val units = listOf("kg", "g", "items")

    // Camera launcher
    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempCameraImageUri != null) {
            Log.d("ImageDebug", "Camera image captured successfully: $tempCameraImageUri")
            // Add to pending images for immediate display
            pendingImageUris = pendingImageUris + tempCameraImageUri.toString()
        } else {
            Log.e("ImageDebug", "Failed to capture image: success=$success, uri=$tempCameraImageUri")
        }
    }

    // Gallery launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            Log.d("ImageDebug", "Image selected from gallery: $uri")
            // Add to pending images for immediate display
            pendingImageUris = pendingImageUris + uri.toString()
        } else {
            Log.e("ImageDebug", "No image selected from gallery")
        }
    }
    
    // Permission launchers
    val requestCameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
        if (cameraGranted) {
            try {
                tempCameraImageUri = createImageFile(context)
                takePictureLauncher.launch(tempCameraImageUri)
            } catch (e: Exception) {
                Log.e("ImageDebug", "Error creating camera image file", e)
                Toast.makeText(context, "Error creating image file: ${e.message}", Toast.LENGTH_SHORT).show()
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

    val saveAndNavigateBack = {
        if (itemName.isNotBlank() && price.isNotBlank()) {
            val currentSelectedDate = todoViewModel.selectedDate.value
            val timestampForSelectedItem = currentSelectedDate.toEpochMilli()

            // Format price
            val formattedPrice = String.format("%.2f", price.toDoubleOrNull() ?: 0.0)
            
            // Determine final quantity string
            val finalQuantity = when {
                selectedQuantity.isNotBlank() -> selectedQuantity
                customQuantity.isNotBlank() && selectedUnit.isNotBlank() -> "$customQuantity$selectedUnit"
                else -> null
            }
            
            // Create the item text
            val itemText = if (finalQuantity != null) {
                "$itemName ($finalQuantity) - ₹$formattedPrice"
            } else {
                "$itemName - ₹$formattedPrice"
            }
            
            // Add the item
            val newItem = TodoItem(
                text = itemText,
                timestamp = timestampForSelectedItem
            )
            
            todoViewModel.addItem(newItem)
            
            // After the item is added, add any pending images
            if (pendingImageUris.isNotEmpty()) {
                // We need to get the item with its assigned ID
                // This is a workaround since we don't have the ID until after it's added
                scope.launch {
                    // Wait a moment for the database operation to complete
                    kotlinx.coroutines.delay(100)
                    
                    // Get all items for the current date
                    val items = todoViewModel.getExpensesForDate(currentSelectedDate).value
                    
                    // Find our newly added item by matching the text
                    val addedItem = items.find { it.text == itemText }
                    
                    if (addedItem != null) {
                        // Add each pending image to the item
                        pendingImageUris.forEach { uriString ->
                            todoViewModel.addImageToItem(addedItem, Uri.parse(uriString))
                        }
                    }
                }
            }
            
            Toast.makeText(context, "Expense saved!", Toast.LENGTH_SHORT).show()
            onNextClick()
        } else {
            Toast.makeText(context, "Please enter item name and price", Toast.LENGTH_SHORT).show()
        }
    }
    
    // Show image viewer if requested
    if (showImageViewer && pendingImageUris.isNotEmpty()) {
        ImageViewerDialog(
            images = pendingImageUris,
            onDismiss = { 
                showImageViewer = false 
            },
            onDeleteImage = { imageUrl ->
                pendingImageUris = pendingImageUris.filter { it != imageUrl }
                if (pendingImageUris.isEmpty()) {
                    showImageViewer = false
                }
            }
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Add New Expense (Updated)") },
                navigationIcon = {
                    IconButton(onClick = onNextClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Expense List"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Item Name Field
            OutlinedTextField(
                value = itemName,
                onValueChange = { itemName = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Item name") },
                leadingIcon = { 
                    Icon(
                        imageVector = Icons.Default.ShoppingCart,
                        contentDescription = "Item"
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Next
                ),
                shape = RoundedCornerShape(8.dp)
            )
            
            // Price Field with Save Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
            OutlinedTextField(
                    value = price,
                    onValueChange = { price = it },
                    modifier = Modifier.weight(1f),
                label = { Text("Price (₹)") },
                    leadingIcon = { 
                        Text(
                            "₹",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    shape = RoundedCornerShape(8.dp)
                )
                
                Button(
                    onClick = saveAndNavigateBack,
                modifier = Modifier
                        .size(56.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4CE0B3)
                    )
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Save",
                        tint = Color.Black
                    )
                }
            }
            
            // Quantity Selection
            Text(
                "Select Quantity",
                style = MaterialTheme.typography.titleMedium
            )
            
            // Predefined Quantities
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                predefinedQuantities.forEach { quantity ->
                        FilterChip(
                        selected = selectedQuantity == quantity,
                            onClick = { 
                                selectedQuantity = if (selectedQuantity == quantity) "" else quantity
                            // Clear custom quantity when predefined is selected
                            if (selectedQuantity.isNotBlank()) {
                                customQuantity = ""
                                selectedUnit = ""
                            }
                },
                        label = { Text(quantity) },
                        modifier = Modifier.height(36.dp)
                    )
                }
            }
            
            // Custom Quantity Input
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Custom Quantity Value
                OutlinedTextField(
                    value = customQuantity,
                    onValueChange = { 
                        customQuantity = it
                        // Clear predefined selection when custom is entered
                        if (customQuantity.isNotBlank()) {
                            selectedQuantity = ""
                        }
                    },
                    modifier = Modifier.weight(1f),
                    label = { Text("Quantity") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    shape = RoundedCornerShape(8.dp)
                )
                
                // Unit Selection
                units.forEach { unit ->
                    FilterChip(
                        selected = selectedUnit == unit,
                        onClick = { 
                            selectedUnit = if (selectedUnit == unit) "" else unit
                            // Clear predefined selection when unit is selected
                            if (selectedUnit.isNotBlank()) {
                                selectedQuantity = ""
                            }
                        },
                        label = { Text(unit) },
                        modifier = Modifier.height(36.dp)
                    )
                }
            }
            
            // Add Images Section
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (pendingImageUris.isNotEmpty()) "Attached Images (${pendingImageUris.size})" else "Add Images", 
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Row(
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        Log.d("AddNewExpenseScreen", "Camera button clicked")
                        if (cameraPermissionState.status.isGranted) {
                            try {
                                tempCameraImageUri = createImageFile(context)
                                Log.d("AddNewExpenseScreen", "Created camera image file: $tempCameraImageUri")
                                takePictureLauncher.launch(tempCameraImageUri)
                            } catch (e: Exception) {
                                Log.e("ImageDebug", "Error creating camera image file", e)
                                Toast.makeText(context, "Error creating image file: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Log.d("AddNewExpenseScreen", "Requesting camera permission")
                            requestCameraPermissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
                        }
                    }) {
                        Icon(Icons.Default.AddAPhoto, contentDescription = "Take Picture")
                    }
                    IconButton(onClick = {
                        Log.d("AddNewExpenseScreen", "Gallery button clicked")
                        // Android 13+ doesn't need READ_EXTERNAL_STORAGE for picking images
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                            Log.d("AddNewExpenseScreen", "Android 13+, launching gallery picker directly")
                            imagePickerLauncher.launch("image/*")
                        } else if (storagePermissionState.status.isGranted) {
                            Log.d("AddNewExpenseScreen", "Storage permission granted, launching gallery picker")
                            imagePickerLauncher.launch("image/*")
                        } else {
                            Log.d("AddNewExpenseScreen", "Requesting storage permission")
                            storagePermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                        }
                    }) {
                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = "Select from Gallery")
                    }
                }
            }
            
            // Show images if there are any
            if (pendingImageUris.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(pendingImageUris) { uri ->
                        Box(contentAlignment = Alignment.TopEnd) {
                            AsyncImage(
                                model = Uri.parse(uri),
                                contentDescription = "Attached image",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(
                                        width = 1.dp,
                                        color = MaterialTheme.colorScheme.outlineVariant,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable { showImageViewer = true }
                            )
                        }
                    }
                }
            }
        }
    }
}

private val LocalDate.toEpochMilli: Long
    get() = this.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
