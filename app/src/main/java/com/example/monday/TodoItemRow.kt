package com.example.monday

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import android.net.Uri
import coil.compose.AsyncImage
import coil.request.ImageRequest

import com.example.monday.TodoItem // Correct import for TodoItem
import com.example.monday.ExpenseCategory // Correct import for ExpenseCategory
import com.example.monday.parseCategoryInfo // Import from Utils.kt now

// Assuming ExpenseCategory is in CategoryComponents.kt
// import com.example.monday.ExpenseCategory

// Assuming parseCategoryInfo is in a utility file or EntryFormScreen.kt initially
// For now, let's assume it will be moved to a Util.kt or kept in EntryFormScreen and imported
// import com.example.monday.parseCategoryInfo // Placeholder - adjust if needed


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TodoItemRow(
    item: TodoItem,
    onCheckedChange: (Boolean) -> Unit,
    onRemoveClick: () -> Unit,
    onEditClick: () -> Unit = {},
    viewModel: TodoViewModel
) {
    var showImageViewer by remember { mutableStateOf(false) }
    var selectedImageIndex by remember { mutableStateOf(0) }

    if (showImageViewer) {
        ImageViewerDialog(
            images = item.imageUris ?: emptyList(),
            onDismiss = { showImageViewer = false },
            onDeleteImage = { imageUrl ->
                viewModel.deleteImageFromItem(item, imageUrl)
            },
            initialPage = selectedImageIndex
        )
    }

    val (baseText, categoryNames) = parseCategoryInfo(item.text)
    val parts = baseText.split(" - ₹")
    val nameWithQuantity = parts[0].trim()
    val price = parts.getOrElse(1) { "0" }.trim()

    val quantityRegex = Regex("\\((.*?)\\)")
    val quantityMatch = quantityRegex.find(nameWithQuantity)
    val name = if (quantityMatch != null) {
        nameWithQuantity.replace(quantityRegex, "").trim()
    } else {
        nameWithQuantity
    }
    val quantity = quantityMatch?.groupValues?.get(1)

    val allCategories = listOf(
        ExpenseCategory("Groceries", Icons.Outlined.ShoppingCart),
        ExpenseCategory("Food", Icons.Outlined.Restaurant),
        ExpenseCategory("Transport", Icons.Outlined.DirectionsCar),
        ExpenseCategory("Bills", Icons.Outlined.Receipt),
        ExpenseCategory("Shopping", Icons.Outlined.LocalMall),
        ExpenseCategory("Health", Icons.Outlined.Medication),
        ExpenseCategory("Education", Icons.Outlined.School),
        ExpenseCategory("Entertainment", Icons.Outlined.Movie),
        ExpenseCategory("Other", Icons.Outlined.MoreHoriz)
    )

    val matchedCategories = allCategories.filter { category ->
        categoryNames.any { it.trim() == category.name }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .pointerInput(item.id) {
                detectTapGestures(
                    onTap = { 
                        onCheckedChange(!item.isDone)
                    },
                    onLongPress = {
                        onEditClick()
                    },
                    onDoubleTap = {
                        if (!item.imageUris.isNullOrEmpty()) {
                            selectedImageIndex = 0
                            showImageViewer = true
                        }
                    }
                )
            },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (categoryNames.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        matchedCategories.take(3).forEach { category ->
                            Icon(
                                imageVector = category.icon,
                                contentDescription = category.name,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(14.dp)
                            )
                        }

                        if (matchedCategories.isEmpty() && categoryNames.isNotEmpty()) {
                            categoryNames.take(3).joinToString(", ").let { names ->
                                Text(
                                    text = names,
                                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 9.sp),
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        if (categoryNames.size > 3) {
                            Text(
                                text = "+${categoryNames.size - (if (matchedCategories.isEmpty()) 3 else matchedCategories.take(3).size)}",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = item.isDone,
                    onCheckedChange = onCheckedChange,
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.primary,
                        uncheckedColor = MaterialTheme.colorScheme.outline
                    ),
                    modifier = Modifier.size(40.dp)
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!item.imageUris.isNullOrEmpty()) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = "Contains images",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "(${item.imageUris.size})",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    if (!quantity.isNullOrBlank()) {
                        Text(
                            text = "Qty: $quantity",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }

                Text(
                    text = "₹$price",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 8.dp, end = 4.dp)
                )

                IconButton(
                    onClick = onRemoveClick,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            if (!item.imageUris.isNullOrEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Image,
                            contentDescription = "Images",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${item.imageUris.size} image${if (item.imageUris.size > 1) "s" else ""} attached",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    // Horizontal scrollable row of image previews
                    androidx.compose.foundation.lazy.LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 4.dp, start = 12.dp, end = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(item.imageUris.size) { index ->
                            val imageUri = item.imageUris[index]
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(Uri.parse(imageUri))
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Image preview ${index + 1}",
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .pointerInput(index) {
                                        detectTapGestures {
                                            selectedImageIndex = index
                                            showImageViewer = true
                                        }
                                    },
                                contentScale = ContentScale.Crop,
                                onError = {
                                    Log.e("TodoItemRow", "Error loading image preview $index")
                                }
                            )
                        }
                    }
                }
            }
        }
    }
} 