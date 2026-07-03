package com.example.monday.core.utils

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

private const val MAX_IMAGE_DIMENSION = 1200
private const val IMAGE_QUALITY = 85

fun createImageFile(context: Context): Uri {
    try {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val storageDir: File = context.getExternalFilesDir("images")!!
        if (!storageDir.exists()) storageDir.mkdirs()
        val file = File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir)
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    } catch (e: Exception) {
        Log.e("ImageUtils", "Error creating image file", e)
        throw e
    }
}

fun copyUriToInternalStorage(context: Context, uri: Uri): Uri? {
    try {
        if (uri.scheme == "file" && uri.path?.contains(context.filesDir.absolutePath) == true) return uri
        val imagesDir = File(context.filesDir, "images")
        if (!imagesDir.exists()) imagesDir.mkdirs()
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeStream(inputStream, null, options)
        inputStream.close()
        val sampleSize = calculateInSampleSize(options, MAX_IMAGE_DIMENSION, MAX_IMAGE_DIMENSION)
        val decodingOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val secondInputStream = context.contentResolver.openInputStream(uri)
        val bitmap = BitmapFactory.decodeStream(secondInputStream, null, decodingOptions)
        secondInputStream?.close()
        if (bitmap == null) return null
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(imagesDir, "IMG_${timeStamp}.jpg")
        val resizedBitmap = getResizedBitmap(bitmap, MAX_IMAGE_DIMENSION)
        FileOutputStream(file).use { outputStream ->
            resizedBitmap.compress(Bitmap.CompressFormat.JPEG, IMAGE_QUALITY, outputStream)
        }
        if (resizedBitmap != bitmap) {
            bitmap.recycle()
            resizedBitmap.recycle()
        } else {
            bitmap.recycle()
        }
        return Uri.fromFile(file)
    } catch (e: Exception) {
        Log.e("ImageUtils", "Error copying URI to internal storage", e)
        return null
    }
}

private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
    val height = options.outHeight
    val width = options.outWidth
    var inSampleSize = 1
    if (height > reqHeight || width > reqWidth) {
        val halfHeight: Int = height / 2
        val halfWidth: Int = width / 2
        while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
            inSampleSize *= 2
        }
    }
    return inSampleSize
}

private fun getResizedBitmap(bitmap: Bitmap, maxSize: Int): Bitmap {
    var width = bitmap.width
    var height = bitmap.height
    if (width <= maxSize && height <= maxSize) return bitmap
    val ratio = width.toFloat() / height.toFloat()
    if (ratio > 1) {
        width = maxSize
        height = (width / ratio).toInt()
    } else {
        height = maxSize
        width = (height * ratio).toInt()
    }
    return Bitmap.createScaledBitmap(bitmap, width, height, true)
}
