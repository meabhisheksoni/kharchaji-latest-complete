package com.example.monday

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

/**
 * A full-featured image viewer dialog that supports both:
 * 1. Swiping between images
 * 2. Pinch-to-zoom and panning
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ImageViewerDialogFull(
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
            // Horizontal pager for swiping between images
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                if (page < images.size) {
                    // Each page has its own zoom state
                    var scale by remember { mutableStateOf(1f) }
                    var offsetX by remember { mutableStateOf(0f) }
                    var offsetY by remember { mutableStateOf(0f) }
                    
                    // Image with zoom and pan
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        // The image with zoom and pan applied
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
                        )
                        
                        // Transparent overlay for gestures
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    detectTransformGestures { _, pan, zoom, _ ->
                                        // Handle pinch-to-zoom
                                        scale = (scale * zoom).coerceIn(1f, 3f)
                                        
                                        // Handle panning when zoomed in
                                        if (scale > 1f) {
                                            val maxX = (size.width * (scale - 1)) / 2
                                            val maxY = (size.height * (scale - 1)) / 2
                                            
                                            offsetX = (offsetX + pan.x).coerceIn(-maxX, maxX)
                                            offsetY = (offsetY + pan.y).coerceIn(-maxY, maxY)
                                        } else {
                                            // Reset offsets when not zoomed in
                                            offsetX = 0f
                                            offsetY = 0f
                                        }
                                    }
                                }
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onDoubleTap = {
                                            // Double tap to toggle zoom
                                            if (scale > 1f) {
                                                scale = 1f
                                                offsetX = 0f
                                                offsetY = 0f
                                            } else {
                                                scale = 2f
                                            }
                                        },
                                        onTap = {
                                            // Single tap to dismiss only when not zoomed
                                            if (scale <= 1f) {
                                                onDismiss()
                                            }
                                        }
                                    )
                                }
                        )
                    }
                }
            }

            // Navigation buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Previous button - only show if not at first image
                if (pagerState.currentPage > 0) {
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage - 1)
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f))
                            .padding(8.dp)
                    ) {
                        Text(
                            text = "←",
                            color = Color.White,
                            style = MaterialTheme.typography.headlineMedium
                        )
                    }
                } else {
                    // Empty spacer for alignment
                    Spacer(modifier = Modifier.size(48.dp))
                }
                
                // Next button - only show if not at last image
                if (pagerState.currentPage < images.size - 1) {
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f))
                            .padding(8.dp)
                    ) {
                        Text(
                            text = "→",
                            color = Color.White,
                            style = MaterialTheme.typography.headlineMedium
                        )
                    }
                } else {
                    // Empty spacer for alignment
                    Spacer(modifier = Modifier.size(48.dp))
                }
            }

            // Top controls overlay
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
                text = "Use arrows to navigate • Pinch to zoom",
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