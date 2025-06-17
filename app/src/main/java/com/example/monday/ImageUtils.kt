package com.example.monday

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

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
        
        // Create a file with a unique name in internal storage
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(imagesDir, "IMG_${timeStamp}.jpg")
        Log.d("ImageUtils", "Creating file at: ${file.absolutePath}")
        
        val outputStream = FileOutputStream(file)
        
        // Use a buffer for more efficient copying
        val buffer = ByteArray(8192) // 8KB buffer
        var bytesRead: Int
        var totalBytesCopied: Long = 0
        
        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
            outputStream.write(buffer, 0, bytesRead)
            totalBytesCopied += bytesRead
        }
        
        Log.d("ImageUtils", "Copied $totalBytesCopied bytes to ${file.absolutePath}")
        
        inputStream.close()
        outputStream.flush()
        outputStream.close()
        
        val resultUri = Uri.fromFile(file)
        Log.d("ImageUtils", "Final URI: $resultUri")
        return resultUri
    } catch (e: Exception) {
        Log.e("ImageUtils", "Error copying URI to internal storage", e)
        return null
    }
} 