package com.example.monday.ui.modern

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.monday.core.utils.parseCategoryInfo
import com.example.monday.core.utils.parsePrice
import com.example.monday.data.models.TodoItem
import com.example.monday.core.utils.formatIndianCurrency
import com.example.monday.ui.modern.ModernColors as C
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * Premium expense item card matching the fintech reference design.
 *
 * Layout:
 *   [Icon]  Name                    ₹Price  ●
 *           time · person · cat
 *           [Tag] [Tag] [Tag]
 *
 * Features:
 *  - Clean bold name (no price in title)
 *  - Muted gray subtitle with person · category
 *  - Outline-style tag chips with colored text
 *  - White card background with subtle border
 *  - Small circle toggle instead of checkbox
 *  - Heavy-friction swipe-to-edit/delete
 */
@Composable
fun ModernExpenseItem(
    item: TodoItem,
    onCheckedChange: (Boolean) -> Unit,
    onRemoveClick: () -> Unit,
    onEditClick: () -> Unit
) {
    val (displayText, categories) = parseCategoryInfo(item.text)
    val price = parsePrice(item.text)
    val primaryCat = categories.firstOrNull() ?: item.categories?.firstOrNull() ?: ""

    // Extract clean item name — strip (quantity) parentheses and trailing price
    val itemName = displayText
        .replace(Regex("\\s*-\\s*₹[\\d,.]+"), "")           // Remove "- ₹123.00"
        .replace(Regex("\\s*₹[\\d,.]+"), "")                 // Remove standalone "₹123.00"
        .replace(Regex("\\s*\\([^)]*\\)"), "")               // Remove "(500g)" or "(1kg)" etc.
        .trim()
        .ifEmpty { displayText.trim() }

    // Extract quantity from parentheses — e.g. "Milk (500g) - ₹50" → "500g"
    val quantityText = Regex("""\(([^)]+)\)""").find(displayText)?.groupValues?.get(1)

    val emoji = C.categoryEmojis[primaryCat] ?: "📦"
    val catColor = C.categoryColors[primaryCat] ?: C.Transport
    val catBg = C.categoryBgTints[primaryCat] ?: C.OtherBgTint

    // Image info
    val hasImages = !item.imageUris.isNullOrEmpty()
    val imageCount = item.imageUris?.size ?: 0

    // Extract time (e.g., "01:22 PM")
    val timeText = remember(item.timestamp) {
        try {
            Instant.ofEpochMilli(item.timestamp)
                .atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("hh:mm a"))
        } catch (_: Exception) { "" }
    }

    // Build subtitle: time · quantity · 📷×N (no categories)
    val subtitleParts = buildList {
        if (timeText.isNotEmpty()) add(timeText)
        if (!quantityText.isNullOrBlank()) add(quantityText)
        if (hasImages) add("📷×$imageCount")
    }
    val subtitle = subtitleParts.joinToString(" · ")

    // --- Custom friction-based swipe state ---
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var cardWidthPx by remember { mutableFloatStateOf(1f) }
    val frictionDampen = 0.35f
    val triggerThreshold = 0.25f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .onSizeChanged { cardWidthPx = it.width.toFloat().coerceAtLeast(1f) }
    ) {
        // Background layer (edit / delete icons)
        val currentOffset = offsetX.value
        val bgColor = when {
            currentOffset > 0 -> C.EggnogLight
            currentOffset < 0 -> C.Destructive.copy(alpha = 0.8f)
            else -> Color.Transparent
        }
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(18.dp))
                .background(bgColor)
                .padding(horizontal = 24.dp),
            contentAlignment = if (currentOffset > 0) Alignment.CenterStart else Alignment.CenterEnd
        ) {
            if (currentOffset > 0) {
                Icon(Icons.Default.Edit, "Edit", tint = C.EggnogDark)
            } else if (currentOffset < 0) {
                Icon(Icons.Default.Delete, "Delete", tint = Color.White)
            }
        }

        // Foreground card
        Column(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .fillMaxWidth()
                .background(Color.White, RoundedCornerShape(18.dp))
                .border(
                    width = 0.5.dp,
                    color = Color(0xFFE8E4DF),
                    shape = RoundedCornerShape(18.dp)
                )
                .pointerInput(item.id) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                val threshold = cardWidthPx * triggerThreshold
                                when {
                                    offsetX.value > threshold -> {
                                        onEditClick()
                                        offsetX.animateTo(
                                            0f,
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                stiffness = Spring.StiffnessHigh
                                            )
                                        )
                                    }
                                    offsetX.value < -threshold -> {
                                        offsetX.animateTo(
                                            -cardWidthPx,
                                            animationSpec = spring(stiffness = Spring.StiffnessHigh)
                                        )
                                        onRemoveClick()
                                    }
                                    else -> {
                                        offsetX.animateTo(
                                            0f,
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                stiffness = Spring.StiffnessMedium
                                            )
                                        )
                                    }
                                }
                            }
                        },
                        onDragCancel = {
                            scope.launch {
                                offsetX.animateTo(0f, spring(stiffness = Spring.StiffnessMedium))
                            }
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            scope.launch {
                                val newVal = (offsetX.value + dragAmount * frictionDampen)
                                    .coerceIn(-cardWidthPx * 0.7f, cardWidthPx * 0.7f)
                                offsetX.snapTo(newVal)
                            }
                        }
                    )
                }
                .clickable { onEditClick() }
                .padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Category icon in colored circle
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(catBg),
                    contentAlignment = Alignment.Center
                ) {
                    Text(emoji, fontSize = 20.sp)
                }

                Spacer(modifier = Modifier.width(14.dp))

                // Name + subtitle column
                Column(modifier = Modifier.weight(1f)) {
                    // Bold clean name
                    Text(
                        text = itemName,
                        color = Color(0xFF1A1A1A),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (subtitle.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(3.dp))
                        // Muted subtitle: person · category · subcategory
                        Text(
                            text = subtitle,
                            color = Color(0xFF8E8E93),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Price — bold, slightly larger
                Text(
                    text = "₹${formatIndianCurrency(price.toInt())}",
                    color = Color(0xFF1A1A1A),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.3).sp
                )

                Spacer(modifier = Modifier.width(10.dp))

                // Small circle toggle (replaces checkbox)
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(
                            if (item.isDone) catColor else Color.Transparent
                        )
                        .border(
                            width = if (item.isDone) 0.dp else 1.5.dp,
                            color = if (item.isDone) Color.Transparent else Color(0xFFD1D1D6),
                            shape = CircleShape
                        )
                        .clickable { onCheckedChange(!item.isDone) },
                    contentAlignment = Alignment.Center
                ) {
                    if (item.isDone) {
                        Text("✓", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Tag pills — outline style with colored text
            if (categories.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    categories.take(3).forEach { catLabel ->
                        val tagColor = C.categoryColors[catLabel] ?: catColor
                        Box(
                            modifier = Modifier
                                .border(1.dp, tagColor.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = catLabel,
                                color = tagColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}
