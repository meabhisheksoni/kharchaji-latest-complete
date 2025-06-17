package com.example.monday

import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.foundation.background

// Project specific imports
import com.example.monday.TodoItem
import com.example.monday.parseItemText
import com.example.monday.parseCategoryInfo

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LocalTodoItemRow(
    item: TodoItem,
    onCheckedChange: (Boolean) -> Unit,
    onRemoveClick: () -> Unit,
    onEditClick: () -> Unit,
    viewModel: TodoViewModel
) {
    var showImageViewer by remember { mutableStateOf(false) }

    if (showImageViewer) {
        ImageViewerDialogFull(
            images = item.imageUris ?: emptyList(),
            onDismiss = { showImageViewer = false },
            onDeleteImage = { imageUrl ->
                viewModel.deleteImageFromItem(item, imageUrl)
            }
        )
    }

    val (name, quantity, price) = parseItemText(item.text)
    var offsetX by remember { mutableStateOf(0f) }
    val actionThreshold = 200.dp 
    val actionThresholdPx = with(LocalDensity.current) { actionThreshold.toPx() }
    var actionPerformed by remember { mutableStateOf(false) }
    
    // Print debug information for this item
    val logTag = "CategoryDebug"
    LaunchedEffect(item.id, item.text, item.categories, item.hasPrimaryCategory, item.hasSecondaryCategory, item.hasTertiaryCategory) {
        Log.d(logTag, "==== Item Display Debug ====")
        Log.d(logTag, "Item ID: ${item.id}, Text: ${item.text}")
        Log.d(logTag, "Categories list: ${item.categories}, Size: ${item.categories?.size ?: 0}")
        Log.d(logTag, "Category flags - Primary: ${item.hasPrimaryCategory}, Secondary: ${item.hasSecondaryCategory}, Tertiary: ${item.hasTertiaryCategory}")
    
        // Parse the categories from text for debugging
        val (_, textCategories) = parseCategoryInfo(item.text)
        Log.d(logTag, "Categories from text parsing: $textCategories")
    }
    
    // Determine if item has categories
    val hasCategoriesApplied = (item.hasPrimaryCategory || item.hasSecondaryCategory || item.hasTertiaryCategory) || 
                               (!item.categories.isNullOrEmpty())
    
    // Count how many category types are selected
    val categoryCount = listOf(
        item.hasPrimaryCategory,
        item.hasSecondaryCategory,
        item.hasTertiaryCategory
    ).count { it }
    
    // Determine background color based on categorization
    val cardBackgroundColor = if (hasCategoriesApplied) {
        Color(0xFFE3F2FD) // Light blue background for categorized items
    } else {
        MaterialTheme.colorScheme.surface
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize() 
                .clip(RoundedCornerShape(12.dp)), 
            horizontalArrangement = Arrangement.SpaceBetween 
        ) {
            Box(
                modifier = Modifier
                    .weight(1f) 
                    .fillMaxHeight()
                    .background(Color(0xFFD0E4FF)) 
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterStart 
            ) {
                 if (offsetX < -actionThresholdPx / 2) { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color.Blue)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Edit", color = Color.Blue, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                    }
                }
            }
            Box(
                modifier = Modifier
                    .weight(1f) 
                    .fillMaxHeight()
                    .background(Color(0xFFFFDDDD)) 
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                if (offsetX > actionThresholdPx / 2) { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Delete", color = Color.Red, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red)
                    }
                }
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.roundToInt(), 0) } 
                .pointerInput(item.id) { 
                    detectHorizontalDragGestures(
                        onDragStart = { actionPerformed = false },
                        onDragEnd = { if (!actionPerformed) offsetX = 0f },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            val newOffsetX = offsetX + dragAmount
                            if (abs(newOffsetX) > actionThresholdPx && !actionPerformed) {
                                if (newOffsetX < -actionThresholdPx) { 
                                    onEditClick()
                                    actionPerformed = true
                                    offsetX = 0f 
                                } else if (newOffsetX > actionThresholdPx) { 
                                    onRemoveClick()
                                    actionPerformed = true
                                }
                            } else if (!actionPerformed) {
                                offsetX = newOffsetX.coerceIn(-actionThresholdPx * 1.5f, actionThresholdPx * 1.5f)
                            }
                        }
                    )
                },
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = cardBackgroundColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Display category header if categories are applied
                if (hasCategoriesApplied) {
                    Surface(
                        color = Color(0xFFBBDEFB),  // Lighter blue surface for better visibility
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.End // Align to the right
                        ) {
                            // Display category names with smaller text and at the right
                            Text(
                                text = item.categories?.joinToString(", ") ?: "",
                                style = MaterialTheme.typography.bodySmall.copy(  // Smaller text
                                    fontSize = 10.sp
                                ),
                                color = Color(0xFF1565C0),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Checkbox(
                            checked = item.isDone,
                            onCheckedChange = onCheckedChange,
                            colors = CheckboxDefaults.colors(
                                checkedColor = MaterialTheme.colorScheme.primary,
                                uncheckedColor = MaterialTheme.colorScheme.outline
                            ),
                            modifier = Modifier.padding(0.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Column(modifier = Modifier.weight(1f, fill = true)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                
                                // Show image indicator if item has images
                                if (!item.imageUris.isNullOrEmpty()) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .padding(start = 4.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(MaterialTheme.colorScheme.primaryContainer)
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                            .pointerInput(item.id) {
                                                detectTapGestures(
                                                    onDoubleTap = { showImageViewer = true }
                                                )
                                            }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Image,
                                            contentDescription = "Has Images",
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        if (item.imageUris!!.size > 1) {
                                            Spacer(modifier = Modifier.width(2.dp))
                                            Text(
                                                text = "${item.imageUris!!.size}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }
                            
                            if (quantity != null) {
                                Text(
                                    text = quantity,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    
                    Text(
                        text = "₹$price", 
                        style = MaterialTheme.typography.titleMedium, 
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 2.dp, end = 0.dp)
                    )
                }
            }
        }
    }
} 