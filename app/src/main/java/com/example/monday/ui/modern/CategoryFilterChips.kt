package com.example.monday.ui.modern

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.monday.ui.modern.ModernColors as C

@Composable
fun CategoryFilterChips(categories: List<String>) {
    LazyRow(
        modifier = Modifier.padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 20.dp)
    ) {
        // Rainbow "All" chip
        item {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFFFF5A5F), Color(0xFFFFB400), Color(0xFF00CC88), Color(0xFF0084FF), Color(0xFFA855F7))
                        )
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("All", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        items(categories) { cat ->
            val color = C.categoryColors[cat] ?: C.Transport
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(color)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(C.categoryEmojis[cat] ?: "📦", fontSize = 12.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(cat, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
