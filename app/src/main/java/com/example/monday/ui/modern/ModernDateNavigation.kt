package com.example.monday.ui.modern

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale
import com.example.monday.ui.modern.ModernColors as C

/**
 * Infinitely scrollable date navigation bar.
 *
 * Architecture:
 * - LazyRow with TOTAL_ITEMS (Int.MAX_VALUE) virtual items.
 * - PIVOT index (TOTAL_ITEMS / 2) maps to [anchorDate] (today at first composition).
 * - Any index i → anchorDate.plusDays(i - PIVOT).
 * - [dateBarPosition] ("start", "mid", "end") controls where the selected date is pinned
 *   in the visible 7-slot window when navigating via < > or Today buttons.
 * - Snap-to-item via manual scroll offset correction after fling settles.
 * - Bidirectional sync: manual swipes update selectedDate to whatever date
 *   lands at the pinned slot.
 */
@Composable
fun ModernDateNavigation(
    selectedDate: LocalDate,
    onDateChange: (LocalDate) -> Unit,
    onOpenCalendar: () -> Unit,
    dailySpendMap: Map<LocalDate, Double> = emptyMap(),
    dateBarPosition: String = "mid" // "start" | "mid" | "end"
) {
    val today = LocalDate.now() // Recompute each recomposition — temporal-drift safe
    val scope = rememberCoroutineScope()

    // --- Virtual index math ---
    val anchorDate = remember { LocalDate.now() } // Stable reference — set once
    val totalItems = Int.MAX_VALUE
    val pivot = totalItems / 2

    fun dateToIndex(date: LocalDate): Int =
        (pivot + ChronoUnit.DAYS.between(anchorDate, date).toInt())
            .coerceIn(0, totalItems - 1) // Overflow guard

    fun indexToDate(index: Int): LocalDate =
        anchorDate.plusDays((index - pivot).toLong())

    // Target slot for the pinned position (0-indexed within 7 visible slots)
    val targetSlot = when (dateBarPosition) {
        "start" -> 0
        "end" -> 6
        else -> 3 // "mid" — center
    }

    // The LazyRow first-visible-item index that places selectedDate at targetSlot
    fun computeScrollIndex(date: LocalDate): Int =
        (dateToIndex(date) - targetSlot).coerceIn(0, totalItems - 1)

    // --- LazyRow state ---
    val lazyListState = rememberLazyListState(
        initialFirstVisibleItemIndex = computeScrollIndex(selectedDate)
    )

    // Max spend for scaling indicator bars (division-by-zero guarded)
    val maxSpend = remember(dailySpendMap) {
        dailySpendMap.values.maxOrNull()?.takeIf { it > 0.0 } ?: 1.0
    }

    // --- Item width: full screen / 7 ---
    val screenWidthDp = LocalConfiguration.current.screenWidthDp.dp
    val itemWidth = screenWidthDp / 7

    // --- Programmatic scroll when selectedDate changes (from buttons / Today / calendar) ---
    // Track whether the date change was triggered programmatically vs by user swipe
    var isProgrammaticDateChange by remember { mutableStateOf(false) }
    var lastProgrammaticDate by remember { mutableStateOf(selectedDate) }

    LaunchedEffect(selectedDate) {
        if (isProgrammaticDateChange || selectedDate != lastProgrammaticDate) {
            val targetIndex = computeScrollIndex(selectedDate)
            val currentIndex = lazyListState.firstVisibleItemIndex
            val jumpDistance = kotlin.math.abs(targetIndex - currentIndex)

            // Cancel any ongoing fling before starting new scroll
            lazyListState.scroll { }

            if (jumpDistance > 14) {
                // Large jump (calendar pick / Today from distant date) — instant teleport.
                // Prevents flood of intermediate recompositions from animating 60-180 cells.
                lazyListState.scrollToItem(targetIndex)
            } else {
                // Small jump (< / > buttons, nearby tap) — smooth animation for premium feel
                lazyListState.animateScrollToItem(targetIndex)
            }
            lastProgrammaticDate = selectedDate
            isProgrammaticDateChange = false
        }
    }

    // --- Bidirectional sync: manual swipe → update selectedDate ---
    // After scroll settles, detect which date is at the pinned slot and sync
    val isScrollInProgress by remember { derivedStateOf { lazyListState.isScrollInProgress } }
    LaunchedEffect(isScrollInProgress) {
        if (!isScrollInProgress && !isProgrammaticDateChange) {
            // Scroll has settled — compute which date is at the pinned slot
            val firstVisible = lazyListState.firstVisibleItemIndex
            val dateAtPinnedSlot = indexToDate(firstVisible + targetSlot)
            if (dateAtPinnedSlot != selectedDate) {
                onDateChange(dateAtPinnedSlot)
            }
        }
    }

    // --- Initial scroll on first composition ---
    LaunchedEffect(Unit) {
        val targetIndex = computeScrollIndex(selectedDate)
        lazyListState.scrollToItem(targetIndex) // Instant, no animation — prevents flash
    }

    Column {
        // Month header + nav buttons — with outer padding
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Clickable month/year label → opens full calendar
            Row(
                modifier = Modifier
                    .clickable { onOpenCalendar() }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = "${selectedDate.month.getDisplayName(JavaTextStyle.SHORT, Locale.ENGLISH)} ${selectedDate.year}",
                    color = C.EggnogDark, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.3).sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = selectedDate.dayOfWeek.getDisplayName(JavaTextStyle.SHORT, Locale.ENGLISH),
                    color = C.EggnogLight, fontSize = 12.sp, fontWeight = FontWeight.Medium
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // Prev day
                Box(
                    modifier = Modifier.size(34.dp)
                        .background(C.SoftCream, RoundedCornerShape(12.dp))
                        .border(1.dp, C.EggnogLight, RoundedCornerShape(12.dp))
                        .clickable {
                            isProgrammaticDateChange = true
                            onDateChange(selectedDate.minusDays(1))
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBackIos, "Prev", tint = C.EggnogDark, modifier = Modifier.size(14.dp))
                }
                // Today button
                Box(
                    modifier = Modifier.height(34.dp)
                        .background(C.TodayButton, RoundedCornerShape(12.dp))
                        .clickable {
                            isProgrammaticDateChange = true
                            onDateChange(today)
                        }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Today", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
                // Next day
                Box(
                    modifier = Modifier.size(34.dp)
                        .background(C.SoftCream, RoundedCornerShape(12.dp))
                        .border(1.dp, C.EggnogLight, RoundedCornerShape(12.dp))
                        .clickable {
                            isProgrammaticDateChange = true
                            onDateChange(selectedDate.plusDays(1))
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, "Next", tint = C.EggnogDark, modifier = Modifier.size(14.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // --- Infinite scrollable date strip (full width, no horizontal padding) ---
        // Snap-to-item: flings always land on exact item boundaries
        val snapFlingBehavior = rememberSnapFlingBehavior(lazyListState = lazyListState)
        LazyRow(
            state = lazyListState,
            modifier = Modifier.fillMaxWidth(),
            flingBehavior = snapFlingBehavior,
            userScrollEnabled = true,
            contentPadding = PaddingValues(horizontal = 3.dp) // Equal edge padding
        ) {
            items(
                count = totalItems,
                key = { it } // Stable key = virtual index
            ) { index ->
                val day = indexToDate(index)
                val isSel = day == selectedDate
                val isFuture = day.isAfter(today)
                val spend = dailySpendMap[day] ?: 0.0
                val relativeLabel = getRelativeLabel(day, today)
                val barFraction = if (spend > 0) (spend / maxSpend).toFloat().coerceIn(0.1f, 1f) else 0f

                DateSlotItem(
                    day = day,
                    isSelected = isSel,
                    isFuture = isFuture,
                    barFraction = barFraction,
                    relativeLabel = relativeLabel,
                    itemWidth = itemWidth,
                    onDateClick = {
                        isProgrammaticDateChange = true
                        onDateChange(day)
                    }
                )
            }
        }
    }
}

/**
 * Single date slot in the horizontal strip.
 * Extracted for LazyRow item reuse / composition optimization.
 */
@Composable
private fun DateSlotItem(
    day: LocalDate,
    isSelected: Boolean,
    isFuture: Boolean,
    barFraction: Float,
    relativeLabel: String,
    itemWidth: Dp,
    onDateClick: () -> Unit
) {
    val dayAbbr = day.dayOfWeek.getDisplayName(JavaTextStyle.SHORT, Locale.ENGLISH)
        .take(2).uppercase() // WE, TH, FR, SA, SU, MO, TU

    Column(
        modifier = Modifier
            .width(itemWidth)
            .graphicsLayer { alpha = if (isFuture) 0.4f else 1f },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Spend intensity indicator bar above the date
        Box(
            modifier = Modifier
                .width(20.dp)
                .height(12.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            if (barFraction > 0f) {
                Box(
                    modifier = Modifier
                        .width(20.dp)
                        .fillMaxHeight(barFraction)
                        .background(
                            if (isSelected) C.DateSelected else C.EggnogDark.copy(alpha = 0.4f),
                            RoundedCornerShape(2.dp)
                        )
                )
            } else {
                Box(
                    modifier = Modifier
                        .width(20.dp)
                        .height(2.dp)
                        .background(C.EggnogLight.copy(alpha = 0.3f), RoundedCornerShape(1.dp))
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Date cell — rounded rectangle with day abbreviation + number
        Box(
            modifier = Modifier
                .width(itemWidth - 7.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (isSelected) C.DateSelected else C.DateUnselected)
                .border(
                    width = if (isSelected) 0.dp else 1.dp,
                    color = if (isSelected) Color.Transparent else C.DateBorder.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(16.dp)
                )
                .clickable { onDateClick() }
                .padding(vertical = 7.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // 2-letter day abbreviation
                Text(
                    text = dayAbbr,
                    color = if (isSelected) Color.White.copy(alpha = 0.85f) else C.DateText.copy(alpha = 0.7f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                // Date number
                Text(
                    text = "${day.dayOfMonth}",
                    color = if (isSelected) Color.White else C.DateText,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Temporal-drift-safe relative day label.
 * Recomputed each recomposition from a fresh 'now'.
 */
private fun getRelativeLabel(date: LocalDate, now: LocalDate): String {
    return when {
        date == now -> "Today"
        date == now.minusDays(1) -> "Yday"
        date == now.plusDays(1) -> "Tmrw"
        else -> date.dayOfWeek.getDisplayName(JavaTextStyle.SHORT, Locale.ENGLISH).take(3)
    }
}
