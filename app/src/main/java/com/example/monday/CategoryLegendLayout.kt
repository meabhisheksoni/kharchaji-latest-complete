package com.example.monday

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex

data class CategoryLegendItem(
    val name: String,
    val color: Color
)

@Composable
fun CategoryLegendLayout(
    primaryCategories: List<CategoryLegendItem>,
    secondaryCategories: List<CategoryLegendItem>,
    tertiaryCategories: List<CategoryLegendItem>,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Primary Categories Column
        CategoryColumn(
            categories = primaryCategories,
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.Start
        )

        // Secondary Categories Column
        CategoryColumn(
            categories = secondaryCategories,
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally
        )

        // Tertiary Categories Column
        CategoryColumn(
            categories = tertiaryCategories,
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.End
        )
    }
}

@Composable
private fun CategoryColumn(
    categories: List<CategoryLegendItem>,
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal
) {
    var expanded by remember { mutableStateOf(false) }
    val visibleCategories = categories.take(3)

    Box(modifier = modifier) {
        Column(
            horizontalAlignment = horizontalAlignment
        ) {
            // Show first 3 categories
            visibleCategories.forEach { category ->
                CategoryLegendItem(category, Modifier.padding(vertical = 0.5.dp))
            }

            // Show dropdown arrow if there are more categories
            if (categories.size > 3) {
                IconButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.size(16.dp)
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (expanded) "Show less" else "Show more",
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }

        // Popup for additional categories - positioned with zIndex to appear on top
        if (expanded && categories.size > 3) {
            Popup(
                alignment = when (horizontalAlignment) {
                    Alignment.Start -> Alignment.TopStart
                    Alignment.CenterHorizontally -> Alignment.TopCenter
                    else -> Alignment.TopEnd
                },
                onDismissRequest = { expanded = false },
                properties = PopupProperties(
                    focusable = true,
                    dismissOnBackPress = true,
                    dismissOnClickOutside = true
                )
            ) {
                Surface(
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .zIndex(10f),
                    shape = RoundedCornerShape(4.dp),
                    shadowElevation = 4.dp,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 2.dp, horizontal = 8.dp)
                    ) {
                        categories.drop(3).forEach { category ->
                            CategoryLegendItem(
                                category,
                                Modifier.padding(vertical = 0.5.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryLegendItem(
    category: CategoryLegendItem,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(category.color)
        )
        Spacer(modifier = Modifier.width(1.dp))
        Text(
            text = category.name,
            fontSize = 9.sp,
            color = Color.Black
        )
    }
} 