package com.example.monday

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

// Data class for expense categories
data class ExpenseCategory(
    val name: String,
    @Transient val icon: ImageVector = Icons.Default.MoreHoriz
) {
    // Default placeholder for serialization
    override fun toString(): String {
        return name
    }
    
    // For better debugging
    fun getIconName(): String {
        return when (icon) {
            Icons.Default.MoreHoriz -> "MoreHoriz"
            else -> "Custom Icon"
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryItem(
    category: ExpenseCategory,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(56.dp)
                .background(
                    color = if (isSelected)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp)
                )
                .border(
                    width = if (isSelected) 1.5.dp else 0.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    shape = RoundedCornerShape(12.dp)
                )
        ) {
            Icon(
                imageVector = category.icon,
                contentDescription = category.name,
                tint = if (isSelected)
                    MaterialTheme.colorScheme.onPrimary
                else
                    MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(28.dp)
            )

            // Show checkmark for selected category
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(16.dp)
                        .background(color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(8.dp))
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                            shape = RoundedCornerShape(8.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = category.name,
            style = MaterialTheme.typography.bodySmall,
            color = if (isSelected)
                MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
} 