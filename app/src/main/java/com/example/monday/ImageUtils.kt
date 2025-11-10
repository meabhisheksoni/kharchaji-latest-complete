package com.example.monday

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

// Maximum image dimension for efficient storage and display
private const val MAX_IMAGE_DIMENSION = 1200
private const val IMAGE_QUALITY = 85

fun createImageFile(context: Context): Uri {
    try {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val storageDir: File = context.getExternalFilesDir("images")!!
        
        if (!storageDir.exists()) {
            val created = storageDir.mkdirs()
            Log.d("ImageUtils", "Created directory: $created for path: ${storageDir.absolutePath}")
        }
        
        val file = File.createTempFile(
            "JPEG_${timeStamp}_",
            ".jpg",
            storageDir
        )
        
        Log.d("ImageUtils", "Created temp file: ${file.absolutePath}, exists: ${file.exists()}")
        
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        
        Log.d("ImageUtils", "Created URI: $uri")
        return uri
    } catch (e: Exception) {
        Log.e("ImageUtils", "Error creating image file", e)
        throw e
    }
}

fun copyUriToInternalStorage(context: Context, uri: Uri): Uri? {
    Log.d("ImageUtils", "Copying URI to internal storage: $uri")
    try {
        // If it's already a file URI in our app's directory, just return it
        if (uri.scheme == "file" && uri.path?.contains(context.filesDir.absolutePath) == true) {
            Log.d("ImageUtils", "URI is already in internal storage, returning as is: $uri")
            return uri
        }
        
        // Create a directory if it doesn't exist
        val imagesDir = File(context.filesDir, "images")
        if (!imagesDir.exists()) {
            val created = imagesDir.mkdirs()
            Log.d("ImageUtils", "Created internal directory: $created for path: ${imagesDir.absolutePath}")
        }
        
        val inputStream = context.contentResolver.openInputStream(uri)
        if (inputStream == null) {
            Log.e("ImageUtils", "Failed to open input stream for URI: $uri")
            return null
        }
        
        // Read the bitmap with options to get dimensions first
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeStream(inputStream, null, options)
        inputStream.close()
        
        // Calculate sample size for more efficient loading
        val sampleSize = calculateInSampleSize(options, MAX_IMAGE_DIMENSION, MAX_IMAGE_DIMENSION)
        
        // Now actually decode the bitmap with sampling
        val decodingOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
        }
        
        val secondInputStream = context.contentResolver.openInputStream(uri)
        val bitmap = BitmapFactory.decodeStream(secondInputStream, null, decodingOptions)
        secondInputStream?.close()
        
        if (bitmap == null) {
            Log.e("ImageUtils", "Failed to decode bitmap from URI: $uri")
            return null
        }
        
        // Create a file with a unique name in internal storage
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(imagesDir, "IMG_${timeStamp}.jpg")
        Log.d("ImageUtils", "Creating file at: ${file.absolutePath}")
        
        // Resize if necessary and compress before saving
        val resizedBitmap = getResizedBitmap(bitmap, MAX_IMAGE_DIMENSION)
        FileOutputStream(file).use { outputStream ->
            resizedBitmap.compress(Bitmap.CompressFormat.JPEG, IMAGE_QUALITY, outputStream)
        }
        
        // If the bitmap was resized, recycle it to free memory
        if (resizedBitmap != bitmap) {
            bitmap.recycle()
            resizedBitmap.recycle()
        } else {
            bitmap.recycle()
        }
        
        val resultUri = Uri.fromFile(file)
        Log.d("ImageUtils", "Final URI after compression: $resultUri")
        return resultUri
    } catch (e: Exception) {
        Log.e("ImageUtils", "Error copying URI to internal storage", e)
        return null
    }
}

/**
 * Calculate optimal inSampleSize for loading large images efficiently
 */
private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
    val height = options.outHeight
    val width = options.outWidth
    var inSampleSize = 1
    
    if (height > reqHeight || width > reqWidth) {
        val halfHeight: Int = height / 2
        val halfWidth: Int = width / 2
        
        // Calculate the largest inSampleSize value that is a power of 2 and keeps both
        // height and width larger than the requested height and width.
        while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
            inSampleSize *= 2
        }
    }
    
    return inSampleSize
}

/**
 * Resize bitmap while maintaining aspect ratio
 */
private fun getResizedBitmap(bitmap: Bitmap, maxSize: Int): Bitmap {
    var width = bitmap.width
    var height = bitmap.height
    
    if (width <= maxSize && height <= maxSize) {
        return bitmap // No need to resize
    }
    
    val ratio = width.toFloat() / height.toFloat()
    
    if (ratio > 1) {
        // Width is greater, so scale based on width
        width = maxSize
        height = (width / ratio).toInt()
    } else {
        // Height is greater or equal, so scale based on height
        height = maxSize
        width = (height * ratio).toInt()
    }
    
    return Bitmap.createScaledBitmap(bitmap, width, height, true)
} 