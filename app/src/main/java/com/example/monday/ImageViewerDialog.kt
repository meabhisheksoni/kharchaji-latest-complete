package com.example.monday

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ImageViewerDialog(
    images: List<String>,
    onDismiss: () -> Unit,
    onDeleteImage: (String) -> Unit,
    initialPage: Int = 0
) {
    if (images.isEmpty()) {
        onDismiss()
        return
    }

    val context = LocalContext.current
    val pagerState = rememberPagerState(initialPage = initialPage.coerceIn(0, images.size - 1)) { images.size }
    val coroutineScope = rememberCoroutineScope()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.9f))
        ) {
            // Simple HorizontalPager without any gesture conflicts
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                if (page < images.size) {
                    // Each page has its own zoom state
                    var scale by remember { mutableStateOf(1f) }
                    var offsetX by remember { mutableStateOf(0f) }
                    var offsetY by remember { mutableStateOf(0f) }
                    
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(
                                enabled = scale <= 1f,
                                onClick = { onDismiss() }
                            )
                    ) {
                        // Image with zoom gesture
                        AsyncImage(
                            model = Uri.parse(images[page]),
                            contentDescription = "Image ${page + 1}",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(
                                    scaleX = scale,
                                    scaleY = scale,
                                    translationX = offsetX,
                                    translationY = offsetY
                                )
                                .pointerInput(Unit) {
                                    detectTransformGestures { _, pan, zoom, _ ->
                                        // Handle zoom
                                        scale = (scale * zoom).coerceIn(1f, 3f)
                                        
                                        // Handle pan only when zoomed in
                                        if (scale > 1f) {
                                            val maxX = (size.width * (scale - 1)) / 2
                                            val maxY = (size.height * (scale - 1)) / 2
                                            
                                            offsetX = (offsetX + pan.x).coerceIn(-maxX, maxX)
                                            offsetY = (offsetY + pan.y).coerceIn(-maxY, maxY)
                                        } else {
                                            offsetX = 0f
                                            offsetY = 0f
                                        }
                                    }
                                }
                                .clickable(
                                    enabled = true,
                                    onClick = {
                                        // Double tap effect: toggle zoom
                                        if (scale > 1f) {
                                            scale = 1f
                                            offsetX = 0f
                                            offsetY = 0f
                                        } else {
                                            scale = 2f
                                        }
                                    }
                                )
                        )
                    }
                }
            }

            // Controls overlay - completely separate from the pager
            Box(modifier = Modifier.fillMaxSize()) {
                // Top controls
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .align(Alignment.TopCenter),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Close button
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f))
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White
                        )
                    }
                    
                    // Image counter
                    Text(
                        text = "${pagerState.currentPage + 1}/${images.size}",
                        color = Color.White,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    
                    // Delete button
                    IconButton(
                        onClick = {
                            val currentPage = pagerState.currentPage
                            if (currentPage < images.size) {
                                val imageToDelete = images[currentPage]
                                Toast.makeText(context, "Image deleted", Toast.LENGTH_SHORT).show()
                                onDeleteImage(imageToDelete)
                                
                                if (currentPage == images.size - 1 && currentPage > 0) {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(currentPage - 1)
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color.Red.copy(alpha = 0.7f))
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete Image",
                            tint = Color.White
                        )
                    }
                }
                
                // Bottom instruction
                Text(
                    text = "Swipe to view more • Tap to zoom",
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 24.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
} 