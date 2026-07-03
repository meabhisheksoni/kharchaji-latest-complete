package com.example.monday.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.StarOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.monday.R
import com.example.monday.ui.modern.ModernColors as C

@Composable
fun AppDrawerContent(
    onExport: () -> Unit = {},
    onImport: () -> Unit = {},
    onBatchSave: () -> Unit = {},
    onSettings: () -> Unit = {},
    onExportCalendarView: () -> Unit = {},
    onAllExpenses: () -> Unit = {},
    onSaveAll: () -> Unit = {}
) {
    // ANTI-FRAGILE: Explicit font fallback
    val displayFontFamily = remember {
        try {
            FontFamily(Font(R.font.satoshi_bold, FontWeight.Bold))
        } catch (e: Exception) {
            FontFamily.SansSerif
        }
    }
    val regularFontFamily = remember {
        try {
            FontFamily(Font(R.font.lexend_bold, FontWeight.Normal))
        } catch (e: Exception) {
            FontFamily.SansSerif
        }
    }

    // Single source of truth for safe scrolling
    val scrollState = rememberScrollState()

    ModalDrawerSheet(
        modifier = Modifier.width(320.dp),
        drawerContainerColor = Color.White,
        drawerShape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            // --- TOP CURVED HEADER ---
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
            ) {
                // ANTI-FRAGILE: Remember path so it doesn't recalculate on every frame of the drawer animation
                val curvePath = remember {
                    Path()
                }
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val width = size.width
                    val height = size.height
                    
                    if (curvePath.isEmpty) {
                        curvePath.moveTo(0f, 0f)
                        curvePath.lineTo(width, 0f)
                        curvePath.lineTo(width, height - 40f) // Start curve a bit higher on the right
                        
                        // Bezier curve to make the U-shape bottom
                        curvePath.quadraticBezierTo(
                            width / 2f, height + 40f, // Control point in the middle, pushing down
                            0f, height - 40f          // End point on the left
                        )
                        curvePath.close()
                    }
                    
                    drawPath(
                        path = curvePath,
                        color = C.DrawerHeaderBrown
                    )
                }

                // Header Content
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Fake Logo / App Name
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.StarOutline, contentDescription = "Logo", tint = Color.White, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Kharchaji", color = Color.White, fontWeight = FontWeight.Bold, fontFamily = displayFontFamily, fontSize = 18.sp)
                        }
                        
                        // Close Icon
                        IconButton(onClick = { /* Parent modal handles standard back presses, this is decorative for hostility testing */ }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White.copy(alpha = 0.7f))
                        }
                    }
                    
                    Spacer(modifier = Modifier.weight(1f))
                    
                    // Profile Info
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Mock Profile Image
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(Color.LightGray)
                                .border(2.dp, Color.White, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Description, contentDescription = "User", tint = Color.White)
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Shinomiya Kaguya",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = displayFontFamily
                        )
                        Text(
                            text = "You are being mindful",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 12.sp,
                            fontFamily = regularFontFamily
                        )
                        Spacer(modifier = Modifier.height(40.dp)) // Space for the curve
                    }
                }
            } // End Header Box

            // --- MENU SECTIONS ---
            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                
                // General Section
                Text("General", color = C.DrawerTextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
                DrawerMenuItem("Mental Health Metrics", R.drawable.ic_launcher_foreground, onAllExpenses) // Mapping to All Expenses
                DrawerMenuItem("Journaling", R.drawable.ic_launcher_foreground, onExportCalendarView) // Mapping to Export Cal
                
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = Color.LightGray.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(16.dp))
                
                // Data Section
                Text("Data", color = C.DrawerTextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
                DrawerMenuItem("View All Records", R.drawable.ic_launcher_foreground, onSaveAll)
                DrawerMenuItem("Batch Save Data", R.drawable.ic_launcher_foreground, onBatchSave)
                
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = Color.LightGray.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(16.dp))
                
                // Profile Section
                Text("Profile", color = C.DrawerTextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
                DrawerMenuItem("Settings", R.drawable.ic_launcher_foreground, onSettings)
                DrawerMenuItem("Subscription", R.drawable.ic_launcher_foreground, {}) // Dummy endpoint for mockup parity
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // Sign Out
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { /* Handle Sign Out */ }.padding(vertical = 12.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Sign Out", tint = C.DrawerSignOutPink, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("Sign Out", color = C.DrawerSignOutPink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.weight(1f))
            
            // --- BOTTOM ACTIONS ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Go Pro Button
                Button(
                    onClick = { },
                    colors = ButtonDefaults.buttonColors(containerColor = C.DrawerProGreen),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.weight(1f).height(44.dp)
                ) {
                    Text("Go Pro", color = Color.White, fontWeight = FontWeight.Bold)
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                // Rate App Button
                OutlinedButton(
                    onClick = { },
                    shape = RoundedCornerShape(50),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.LightGray),
                    modifier = Modifier.weight(1f).height(44.dp)
                ) {
                    Text("Rate Our App", color = Color.DarkGray, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// Extracted for reusability and to prevent bloated composition
@Composable
private fun DrawerMenuItem(label: String, iconRes: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Using a generic placeholder icon as we map from the mockup
        Icon(Icons.Default.StarOutline, contentDescription = label, tint = Color.Gray, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = label,
            color = Color.DarkGray,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
