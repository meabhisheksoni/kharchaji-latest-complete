package com.example.monday.ui.components

import android.Manifest
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.monday.core.utils.createImageFile
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager

/**
 * Self-contained image picker composable — handles camera, gallery,
 * permissions, and displays thumbnail previews with delete buttons.
 *
 * Shared between EditItemDialog and AddNewExpenseScreenV2 to eliminate
 * ~150 lines of duplicated code.
 *
 * @param imageUris Current list of image URI strings to display.
 * @param onImageAdded Called when a new image is captured/selected.
 * @param onImageDeleted Called when a user deletes a thumbnail. Null = no delete button shown.
 * @param onImageClicked Called when a thumbnail is tapped (for full-screen viewer). Null = no click handling.
 * @param title Header text shown above the image buttons.
 */

@Composable
fun ImagePickerSection(
    imageUris: List<String>,
    onImageAdded: (Uri) -> Unit,
    onImageDeleted: ((String) -> Unit)? = null,
    onImageClicked: ((Int) -> Unit)? = null,
    title: String = if (imageUris.isNotEmpty()) "Attached Images (${imageUris.size})" else "Add Images"
) {
    val context = LocalContext.current
    var tempCameraImageUri by remember { mutableStateOf<Uri?>(null) }

    val hasCameraPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    val hasStoragePermission = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    // Camera launcher
    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val capturedUri = tempCameraImageUri
        if (success && capturedUri != null) {
            onImageAdded(capturedUri)
        } else if (!success) {
            Log.e("ImagePicker", "Camera capture failed")
        }
    }

    // Gallery launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            onImageAdded(uri)
        }
    }

    // Camera permission launcher
    val requestCameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
        if (cameraGranted) {
            try {
                val uri = createImageFile(context)
                tempCameraImageUri = uri
                takePictureLauncher.launch(uri)
            } catch (e: Exception) {
                Log.e("ImagePicker", "Error creating camera image file", e)
                Toast.makeText(context, "Error creating image file: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Storage permission launcher
    val storagePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            imagePickerLauncher.launch("image/*")
        }
    }

    // ── Header with camera/gallery buttons ──────────────────────────
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(title, style = MaterialTheme.typography.bodyMedium)

        Row(
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Camera button
            IconButton(onClick = {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    try {
                        val uri = createImageFile(context)
                        tempCameraImageUri = uri
                        takePictureLauncher.launch(uri)
                    } catch (e: Exception) {
                        Log.e("ImagePicker", "Error creating camera image file", e)
                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    requestCameraPermissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
                }
            }) {
                Icon(Icons.Default.AddAPhoto, contentDescription = "Take Picture")
            }

            // Gallery button — Android 13+ doesn't need READ_EXTERNAL_STORAGE
            IconButton(onClick = {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    imagePickerLauncher.launch("image/*")
                } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                    imagePickerLauncher.launch("image/*")
                } else {
                    storagePermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
            }) {
                Icon(Icons.Default.AddPhotoAlternate, contentDescription = "Select from Gallery")
            }
        }
    }

    // ── Thumbnail row ───────────────────────────────────────────────
    if (imageUris.isNotEmpty()) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            itemsIndexed(imageUris) { index, uri ->
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(8.dp)
                        )
                ) {
                    AsyncImage(
                        model = Uri.parse(uri),
                        contentDescription = "Attached image",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .then(
                                if (onImageClicked != null) Modifier.clickable { onImageClicked(index) }
                                else Modifier.clickable { /* show image */ }
                            )
                    )

                    // Delete button — only shown if onImageDeleted is provided
                    if (onImageDeleted != null) {
                        IconButton(
                            onClick = { onImageDeleted(uri) },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(24.dp)
                                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.8f), CircleShape)
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete Image",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
