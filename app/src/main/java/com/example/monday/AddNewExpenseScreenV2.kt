package com.example.monday

import android.Manifest
import android.app.DatePickerDialog
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.monday.core.utils.createImageFile
import com.example.monday.data.models.TodoItem
import com.example.monday.ui.modern.ModernColors
import com.example.monday.viewmodels.MainViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun AddNewExpenseScreenV2(
    onNextClick: () -> Unit,
    todoViewModel: TodoViewModel,
    mainViewModel: MainViewModel = androidx.hilt.navigation.compose.hiltViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    // ── Form State ──────────────────────────────────────────────────────────
    var itemName by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var selectedQuantity by remember { mutableStateOf("") }
    var customQuantity by remember { mutableStateOf("") }
    var selectedUnit by remember { mutableStateOf("") }
    var selectedCategories by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isSaving by remember { mutableStateOf(false) }

    // ── Date Management ─────────────────────────────────────────────────────
    val selectedDate by mainViewModel.selectedDate.collectAsState()
    val isToday = selectedDate == LocalDate.now()
    val dateDisplayStr = remember(selectedDate) {
        if (isToday) "Today, ${selectedDate.format(DateTimeFormatter.ofPattern("d MMM"))}"
        else selectedDate.format(DateTimeFormatter.ofPattern("EEE, d MMM"))
    }

    // ── Focus Requester ─────────────────────────────────────────────────────
    val itemNameFocusRequester = remember { FocusRequester() }
    val priceFocusRequester = remember { FocusRequester() }

    // ── Category State from ViewModel ───────────────────────────────────────
    val primaryCategories by todoViewModel.primaryCategories.collectAsState()
    val secondaryCategories by todoViewModel.secondaryCategories.collectAsState()

    // Distinct list of selectable categories with fallback
    val allAvailableCategories = remember(primaryCategories, secondaryCategories) {
        val combined = (primaryCategories + secondaryCategories).map { it.name }.distinct()
        if (combined.isNotEmpty()) combined else ModernColors.categoryColors.keys.toList()
    }

    // ── Image State ─────────────────────────────────────────────────────────
    var pendingImageUris by remember { mutableStateOf<List<String>>(emptyList()) }
    var tempCameraImageUri by remember { mutableStateOf<Uri?>(null) }
    var showImageViewer by remember { mutableStateOf(false) }
    var selectedImageIndex by remember { mutableStateOf(0) }

    // ── Permissions ─────────────────────────────────────────────────────────
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    val storagePermissionState = rememberPermissionState(Manifest.permission.READ_EXTERNAL_STORAGE)

    // Camera launcher
    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempCameraImageUri != null) {
            pendingImageUris = pendingImageUris + tempCameraImageUri.toString()
            Log.d("AddNewExpenseV2", "Image captured: $tempCameraImageUri")
        } else {
            Log.e("AddNewExpenseV2", "Camera capture cancelled or failed")
        }
    }

    // Gallery launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            pendingImageUris = pendingImageUris + uri.toString()
            Log.d("AddNewExpenseV2", "Gallery image picked: $uri")
        }
    }

    // Camera permission requester
    val requestCameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            try {
                val uri = createImageFile(context)
                tempCameraImageUri = uri
                takePictureLauncher.launch(uri)
            } catch (e: Exception) {
                Log.e("AddNewExpenseV2", "Error creating image file", e)
                Toast.makeText(context, "Failed to create image: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Camera permission needed to take photos", Toast.LENGTH_SHORT).show()
        }
    }

    // Storage permission requester for older Android versions
    val storagePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            imagePickerLauncher.launch("image/*")
        } else {
            Toast.makeText(context, "Storage permission needed to select photos", Toast.LENGTH_SHORT).show()
        }
    }

    // Predefined options
    val predefinedQuantities = listOf("250g", "500g", "1kg", "1.5kg", "2kg")
    val units = listOf("kg", "g", "items", "L", "pkt")

    // ── Save Function ───────────────────────────────────────────────────────
    val saveExpense: () -> Unit = {
        if (!isSaving && itemName.isNotBlank() && price.isNotBlank()) {
            isSaving = true
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)

            val parsedPrice = price.toDoubleOrNull() ?: 0.0
            val formattedPrice = String.format(Locale.US, "%.2f", parsedPrice)

            // Determine quantity
            val finalQuantity = when {
                selectedQuantity.isNotBlank() -> selectedQuantity
                customQuantity.isNotBlank() && selectedUnit.isNotBlank() -> "$customQuantity$selectedUnit"
                customQuantity.isNotBlank() -> customQuantity
                else -> null
            }

            // Build base text
            val baseText = if (finalQuantity != null) {
                "${itemName.trim()} ($finalQuantity) - ₹$formattedPrice"
            } else {
                "${itemName.trim()} - ₹$formattedPrice"
            }

            // Categories list & determine types
            val categoriesList = selectedCategories.toList()
            val (hasPrimary, hasSecondary, hasTertiary) = todoViewModel.categoryManager.determineCategoryTypes(
                categoriesList.ifEmpty { null }
            )

            // Append CATS tag for universal backwards compatibility
            val finalText = if (categoriesList.isNotEmpty()) {
                "$baseText|CATS:${categoriesList.joinToString(",")}"
            } else {
                baseText
            }

            // Timestamp locked to target date
            val timestampForSelectedItem = selectedDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

            val newItem = TodoItem(
                text = finalText,
                timestamp = timestampForSelectedItem,
                categories = categoriesList.ifEmpty { null },
                hasPrimaryCategory = hasPrimary,
                hasSecondaryCategory = hasSecondary,
                hasTertiaryCategory = hasTertiary,
                imageUris = pendingImageUris.ifEmpty { null }
            )

            // Insert into Room DB via ViewModel
            mainViewModel.addItem(newItem)

            scope.launch {
                // Reset form for next rapid entry
                itemName = ""
                price = ""
                selectedQuantity = ""
                customQuantity = ""
                selectedUnit = ""
                selectedCategories = emptySet()
                pendingImageUris = emptyList()

                // Brief toast
                val toast = Toast.makeText(context, "Saved ₹$formattedPrice!", Toast.LENGTH_SHORT)
                toast.show()
                Handler(Looper.getMainLooper()).postDelayed({ toast.cancel() }, 600)

                // Re-focus on item name for rapid consecutive logging
                itemNameFocusRequester.requestFocus()
                delay(400)
                isSaving = false
            }
        } else if (itemName.isBlank()) {
            Toast.makeText(context, "Please enter item name", Toast.LENGTH_SHORT).show()
            itemNameFocusRequester.requestFocus()
        } else if (price.isBlank()) {
            Toast.makeText(context, "Please enter amount", Toast.LENGTH_SHORT).show()
            priceFocusRequester.requestFocus()
        }
    }

    // ── Fullscreen Image Viewer ─────────────────────────────────────────────
    if (showImageViewer && pendingImageUris.isNotEmpty()) {
        ImageViewerDialog(
            images = pendingImageUris,
            onDismiss = { showImageViewer = false },
            onDeleteImage = { uriToDelete ->
                pendingImageUris = pendingImageUris.filter { it != uriToDelete }
                if (pendingImageUris.isEmpty()) {
                    showImageViewer = false
                }
            },
            initialPage = selectedImageIndex
        )
    }

    // Initial focus on mount
    LaunchedEffect(Unit) {
        itemNameFocusRequester.requestFocus()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = ModernColors.Eggshell,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Add Expense",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            ),
                            color = Color(0xFF212121)
                        )
                        Text(
                            text = "Fast manual entry",
                            style = MaterialTheme.typography.labelSmall,
                            color = ModernColors.TextMuted
                        )
                    }
                },
                navigationIcon = {
                    Box(
                        modifier = Modifier.size(44.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        IconButton(onClick = onNextClick) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color(0xFF212121)
                            )
                        }
                    }
                },
                actions = {
                    // Tactile Target Date Pill
                    Surface(
                        onClick = {
                            val cal = Calendar.getInstance()
                            cal.set(selectedDate.year, selectedDate.monthValue - 1, selectedDate.dayOfMonth)
                            DatePickerDialog(
                                context,
                                { _, year, month, dayOfMonth ->
                                    mainViewModel.setDate(LocalDate.of(year, month + 1, dayOfMonth))
                                },
                                cal.get(Calendar.YEAR),
                                cal.get(Calendar.MONTH),
                                cal.get(Calendar.DAY_OF_MONTH)
                            ).show()
                        },
                        shape = RoundedCornerShape(20.dp),
                        color = if (isToday) ModernColors.CardBg else Color(0xFFE1F5FE),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isToday) ModernColors.EggnogDark.copy(alpha = 0.5f) else ModernColors.DateSelected.copy(alpha = 0.6f)
                        ),
                        modifier = Modifier
                            .padding(end = 16.dp)
                            .height(34.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(horizontal = 10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarToday,
                                contentDescription = "Pick Date",
                                modifier = Modifier.size(13.dp),
                                tint = if (isToday) ModernColors.EggnogDark else ModernColors.DateSelected
                            )
                            Text(
                                text = dateDisplayStr,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = if (isToday) ModernColors.EggnogDark else Color(0xFF0288D1)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ModernColors.Eggshell
                )
            )
        },
        bottomBar = {
            // ── Primary Action Bar (Fixed Bottom Rail) ──────────────────────
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars),
                color = ModernColors.Eggshell,
                shadowElevation = 8.dp,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    ModernColors.CardBorder.copy(alpha = 0.2f)
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    val canSave = itemName.isNotBlank() && price.isNotBlank() && !isSaving
                    val buttonBg = if (canSave) ModernColors.EggnogDark else Color(0xFFD1C8B4)

                    Button(
                        onClick = saveExpense,
                        enabled = canSave,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = buttonBg,
                            disabledContainerColor = Color(0xFFE0DDD5),
                            contentColor = Color.White,
                            disabledContentColor = Color(0xFF9E9E9E)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .shadow(
                                elevation = if (canSave) 4.dp else 0.dp,
                                shape = RoundedCornerShape(16.dp)
                            )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            val priceSuffix = if (price.isNotBlank()) " (₹$price)" else ""
                            Text(
                                text = "Save Expense$priceSuffix",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── 1. Hero Amount Input Card ───────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = ModernColors.CardBg),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    ModernColors.CardBorder.copy(alpha = 0.4f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "AMOUNT SPENT",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = ModernColors.EggnogDark
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "₹",
                            style = TextStyle(
                                fontSize = 34.sp,
                                fontWeight = FontWeight.Bold,
                                color = ModernColors.EggnogDark
                            ),
                            modifier = Modifier.padding(end = 6.dp)
                        )

                        OutlinedTextField(
                            value = price,
                            onValueChange = { input ->
                                // Strict decimal sanitizer: allow digits and at most one decimal with 2 places
                                if (input.isEmpty() || input.matches(Regex("""^\d*(\.\d{0,2})?$"""))) {
                                    price = input
                                }
                            },
                            placeholder = {
                                Text(
                                    "0.00",
                                    style = TextStyle(
                                        fontSize = 32.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFBDBDBD)
                                    )
                                )
                            },
                            textStyle = TextStyle(
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF212121)
                            ),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Decimal,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    keyboardController?.hide()
                                    if (itemName.isNotBlank() && price.isNotBlank()) {
                                        saveExpense()
                                    }
                                }
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                cursorColor = ModernColors.EggnogDark
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(priceFocusRequester)
                        )

                        if (price.isNotEmpty()) {
                            IconButton(
                                onClick = { price = "" },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear price",
                                    modifier = Modifier.size(16.dp),
                                    tint = ModernColors.TextMuted
                                )
                            }
                        }
                    }

                    // Quick increment chips for lightning logging
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf(50, 100, 200, 500).forEach { inc ->
                            Surface(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    val currentVal = price.toDoubleOrNull() ?: 0.0
                                    price = String.format(Locale.US, "%.0f", currentVal + inc)
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = ModernColors.SoftCream,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    ModernColors.CardBorder.copy(alpha = 0.35f)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(30.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "+₹$inc",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        color = ModernColors.EggnogDark
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── 2. Item Description Input ───────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = ModernColors.CardBg),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    ModernColors.CardBorder.copy(alpha = 0.4f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "ITEM / DESCRIPTION",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = ModernColors.EggnogDark
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedTextField(
                        value = itemName,
                        onValueChange = { itemName = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(itemNameFocusRequester),
                        placeholder = { Text("What did you buy? (e.g. Milk, Petrol, Coffee)") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.ShoppingCart,
                                contentDescription = null,
                                tint = ModernColors.EggnogDark
                            )
                        },
                        trailingIcon = {
                            if (itemName.isNotEmpty()) {
                                IconButton(onClick = { itemName = "" }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear text",
                                        modifier = Modifier.size(16.dp),
                                        tint = ModernColors.TextMuted
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = {
                                priceFocusRequester.requestFocus()
                            }
                        ),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = ModernColors.SoftCream,
                            unfocusedContainerColor = ModernColors.SoftCream,
                            focusedBorderColor = ModernColors.EggnogDark,
                            unfocusedBorderColor = ModernColors.CardBorder.copy(alpha = 0.5f)
                        )
                    )
                }
            }

            // ── 3. Tactile Category Selector ────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = ModernColors.CardBg),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    ModernColors.CardBorder.copy(alpha = 0.4f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocalOffer,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = ModernColors.EggnogDark
                            )
                            Text(
                                text = "CATEGORY",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                ),
                                color = ModernColors.EggnogDark
                            )
                        }

                        if (selectedCategories.isNotEmpty()) {
                            Text(
                                text = "${selectedCategories.size} selected",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = ModernColors.DateSelected
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Horizontal Scrolling Category Pills
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(allAvailableCategories) { categoryName ->
                            val isSelected = selectedCategories.contains(categoryName)
                            val catColor = ModernColors.categoryColors[categoryName] ?: ModernColors.EggnogDark
                            val catBgTint = ModernColors.categoryBgTints[categoryName] ?: Color(0xFFF3E5F5)
                            val emoji = ModernColors.categoryEmojis[categoryName] ?: "🏷️"

                            Surface(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    selectedCategories = if (isSelected) {
                                        selectedCategories - categoryName
                                    } else {
                                        selectedCategories + categoryName
                                    }
                                },
                                shape = RoundedCornerShape(20.dp),
                                color = if (isSelected) catBgTint else ModernColors.SoftCream,
                                border = androidx.compose.foundation.BorderStroke(
                                    if (isSelected) 1.5.dp else 1.dp,
                                    if (isSelected) catColor else ModernColors.CardBorder.copy(alpha = 0.4f)
                                ),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.padding(horizontal = 12.dp)
                                ) {
                                    Text(text = emoji, fontSize = 13.sp)
                                    Text(
                                        text = categoryName,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 13.sp
                                        ),
                                        color = if (isSelected) catColor else Color(0xFF424242)
                                    )
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(12.dp),
                                            tint = catColor
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── 4. Tactile Quantity Selector ────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = ModernColors.CardBg),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    ModernColors.CardBorder.copy(alpha = 0.4f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "QUANTITY (OPTIONAL)",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = ModernColors.EggnogDark
                    )

                    // Predefined chips
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        predefinedQuantities.forEach { quantity ->
                            val isSelected = selectedQuantity == quantity
                            Surface(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    selectedQuantity = if (isSelected) "" else quantity
                                    if (selectedQuantity.isNotBlank()) {
                                        customQuantity = ""
                                        selectedUnit = ""
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) Color(0xFFE8F5E9) else ModernColors.SoftCream,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isSelected) ModernColors.Groceries else ModernColors.CardBorder.copy(alpha = 0.35f)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(34.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = quantity,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 12.sp
                                        ),
                                        color = if (isSelected) ModernColors.Groceries else Color(0xFF424242)
                                    )
                                }
                            }
                        }
                    }

                    // Custom quantity input row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = customQuantity,
                            onValueChange = {
                                customQuantity = it
                                if (it.isNotBlank()) selectedQuantity = ""
                            },
                            placeholder = { Text("Qty", fontSize = 12.sp) },
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = ModernColors.SoftCream,
                                unfocusedContainerColor = ModernColors.SoftCream,
                                focusedBorderColor = ModernColors.EggnogDark,
                                unfocusedBorderColor = ModernColors.CardBorder.copy(alpha = 0.4f)
                            )
                        )

                        units.forEach { unit ->
                            val isSelected = selectedUnit == unit
                            Surface(
                                onClick = {
                                    selectedUnit = if (isSelected) "" else unit
                                    if (selectedUnit.isNotBlank()) selectedQuantity = ""
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) Color(0xFFE8F5E9) else ModernColors.SoftCream,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isSelected) ModernColors.Groceries else ModernColors.CardBorder.copy(alpha = 0.35f)
                                ),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(horizontal = 10.dp)
                                ) {
                                    Text(
                                        text = unit,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 12.sp
                                        ),
                                        color = if (isSelected) ModernColors.Groceries else Color(0xFF616161)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── 5. Receipt / Attachment Strip ───────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = ModernColors.CardBg),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    ModernColors.CardBorder.copy(alpha = 0.4f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = ModernColors.EggnogDark
                            )
                            Text(
                                text = "RECEIPT / ATTACHMENT",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                ),
                                color = ModernColors.EggnogDark
                            )
                        }

                        if (pendingImageUris.isNotEmpty()) {
                            Text(
                                text = "${pendingImageUris.size} attached",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = ModernColors.Groceries
                            )
                        }
                    }

                    // Action buttons (Camera + Gallery)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Take Photo Pill
                        Surface(
                            onClick = {
                                if (cameraPermissionState.status.isGranted) {
                                    try {
                                        val uri = createImageFile(context)
                                        tempCameraImageUri = uri
                                        takePictureLauncher.launch(uri)
                                    } catch (e: Exception) {
                                        Log.e("AddNewExpenseV2", "Error creating image file", e)
                                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = ModernColors.SoftCream,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                ModernColors.CardBorder.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PhotoCamera,
                                    contentDescription = "Camera",
                                    modifier = Modifier.size(16.dp),
                                    tint = ModernColors.EggnogDark
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Take Photo",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = Color(0xFF424242)
                                )
                            }
                        }

                        // Upload Receipt Pill
                        Surface(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ||
                                    storagePermissionState.status.isGranted
                                ) {
                                    imagePickerLauncher.launch("image/*")
                                } else {
                                    storagePermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = ModernColors.SoftCream,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                ModernColors.CardBorder.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PhotoLibrary,
                                    contentDescription = "Gallery",
                                    modifier = Modifier.size(16.dp),
                                    tint = ModernColors.DateSelected
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Upload Receipt",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = Color(0xFF424242)
                                )
                            }
                        }
                    }

                    // Attached thumbnails strip
                    if (pendingImageUris.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(pendingImageUris) { uriStr ->
                                Box(
                                    contentAlignment = Alignment.TopEnd,
                                    modifier = Modifier.size(72.dp)
                                ) {
                                    AsyncImage(
                                        model = Uri.parse(uriStr),
                                        contentDescription = "Receipt Thumbnail",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(12.dp))
                                            .border(
                                                1.dp,
                                                ModernColors.CardBorder.copy(alpha = 0.5f),
                                                RoundedCornerShape(12.dp)
                                            )
                                            .clickable {
                                                selectedImageIndex = pendingImageUris.indexOf(uriStr)
                                                showImageViewer = true
                                            }
                                    )

                                    // Delete badge
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .padding(4.dp)
                                            .size(22.dp)
                                            .background(Color(0xCC000000), CircleShape)
                                            .clickable {
                                                pendingImageUris = pendingImageUris.filter { it != uriStr }
                                            }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Remove image",
                                            tint = Color.White,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}