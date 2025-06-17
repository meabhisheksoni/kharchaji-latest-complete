package com.example.monday

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.time.LocalDate

// Project-specific imports
import com.example.monday.TodoItem
import com.example.monday.parseItemText
import com.example.monday.formatForDisplay

fun shareExpensesList(
    context: Context,
    itemsToShare: List<TodoItem>,
    sumOfItemsToShare: Double,
    expensesDate: LocalDate,
    monthlySummaryText: String? = null
) {
    try {
        Log.d("ShareExpensesList", "Starting share process for ${itemsToShare.size} items, sum $sumOfItemsToShare, date $expensesDate, monthlySummary: $monthlySummaryText")
        
        if (itemsToShare.isEmpty()) {
            Log.e("ShareExpensesList", "No items selected/passed to share")
            Toast.makeText(
                context,
                "Please select items to share",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        Log.d("ShareExpensesList", "Sum to use for bitmap: $sumOfItemsToShare")
        
        Log.d("ShareExpensesList", "Items being sent to createExpensesBitmap (${itemsToShare.size} items):")
        itemsToShare.forEachIndexed { _, item ->
            Log.d("ShareExpensesList", "  Item ${item.text}, isDone: ${item.isDone}")
        }
        
        val mainHandler = Handler(Looper.getMainLooper())
        
        Thread {
            try {
                val bitmap = createExpensesBitmap(context, itemsToShare, sumOfItemsToShare, expensesDate, monthlySummaryText)
                Log.d("ShareExpensesList", "Created bitmap: ${bitmap.width}x${bitmap.height}")
                
                val file = saveBitmapToFile(context, bitmap)
                Log.d("ShareExpensesList", "Saved bitmap to file: ${file.absolutePath}")
                Log.d("ShareExpensesList", "File exists: ${file.exists()}")
                Log.d("ShareExpensesList", "File size: ${file.length()}")

                val authority = "${context.packageName}.fileprovider"
                Log.d("ShareExpensesList", "FileProvider authority: $authority")
                
                val uri = FileProvider.getUriForFile(context, authority, file)
                Log.d("ShareExpensesList", "Created URI: $uri")

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                Log.d("ShareExpensesList", "Created share intent")
                val chooser = Intent.createChooser(shareIntent, "Share Expenses")
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                
                mainHandler.post {
                    try {
                        Log.d("ShareExpensesList", "Starting chooser activity")
                        context.startActivity(chooser)
                        Log.d("ShareExpensesList", "Started share activity successfully")
                    } catch (e: Exception) {
                        Log.e("ShareExpensesList", "Error starting share activity", e)
                        Toast.makeText(
                            context,
                            "Error sharing: ${e.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("ShareExpensesList", "Error during bitmap/sharing process", e)
                e.printStackTrace()
                mainHandler.post {
                    try {
                        // progressDialog.dismiss() // Assuming no progress dialog here
                    } catch (e2: Exception) {
                        Log.e("ShareExpensesList", "Error dismissing dialog", e2)
                    }
                    Toast.makeText(
                        context,
                        "Error sharing expenses: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }.start()
    } catch (e: Exception) {
        Log.e("ShareExpensesList", "Error in shareExpensesList: ${e.message}")
        e.printStackTrace()
        Toast.makeText(
            context,
            "Error sharing expenses: ${e.message}",
            Toast.LENGTH_LONG
        ).show()
    }
}

private fun createExpensesBitmap(
    context: Context,
    itemsForBitmap: List<TodoItem>,
    totalSumForBitmap: Double,
    expensesDate: LocalDate,
    monthlySummaryText: String? = null
): Bitmap {
    try {
        Log.d("ShareExpensesList", "Creating bitmap for ${itemsForBitmap.size} items, sum: $totalSumForBitmap, date: $expensesDate, monthlySummary: $monthlySummaryText")
        
        val layout = android.widget.LinearLayout(context).apply layoutScope@{
            orientation = android.widget.LinearLayout.VERTICAL
            setBackgroundColor(android.graphics.Color.WHITE)
            setPadding(32, 24, 32, 24)
        }

        android.widget.TextView(context).apply dateTextViewScope@{
            text = expensesDate.formatForDisplay("dd MMM yyyy")
            setTextColor(android.graphics.Color.LTGRAY)
            textSize = 14f
            gravity = android.view.Gravity.START
            this@dateTextViewScope.layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 16)
            }
        }.also { layout.addView(it) }

        android.widget.TextView(context).apply titleDotsViewScope@{
            text = "...."
            setTextColor(android.graphics.Color.BLACK)
            textSize = 20f
            gravity = android.view.Gravity.CENTER
            this@titleDotsViewScope.layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 24)
            }
        }.also { layout.addView(it) }

        itemsForBitmap.forEachIndexed { index, item ->
            val (_, quantity, _) = parseItemText(item.text)
            
            android.widget.LinearLayout(context).apply itemRowContainerScope@{
                orientation = android.widget.LinearLayout.VERTICAL
                this@itemRowContainerScope.layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                )

                android.widget.LinearLayout(context).apply itemDetailsRowScope@{
                    orientation = android.widget.LinearLayout.HORIZONTAL
                    this@itemDetailsRowScope.layoutParams = android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        setMargins(0, 8, 0, 8)
                    }
                    
                    android.widget.TextView(context).apply nameTextViewScope@{
                        text = "${index + 1}. ${parseItemText(item.text).first}"
                        setTextColor(android.graphics.Color.BLACK)
                        textSize = 16f
                        this@nameTextViewScope.layoutParams = android.widget.LinearLayout.LayoutParams(
                            0,
                            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                            1.0f
                        )
                    }.also { addView(it) }

                    if (quantity != null) {
                        android.widget.TextView(context).apply quantityTextViewScope@{
                            text = quantity
                            setTextColor(android.graphics.Color.RED)
                            textSize = 16f
                            gravity = android.view.Gravity.CENTER
                            this@quantityTextViewScope.layoutParams = android.widget.LinearLayout.LayoutParams(
                                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                            ).apply {
                                marginStart = 16
                                marginEnd = 16
                                width = (80 * context.resources.displayMetrics.density).toInt()
                            }
                        }.also { addView(it) }
                    } else {
                        android.view.View(context).apply emptyViewScope@{
                            this@emptyViewScope.layoutParams = android.widget.LinearLayout.LayoutParams(
                                (80 * context.resources.displayMetrics.density).toInt(),
                                1
                            )
                        }.also { addView(it) }
                    }

                    android.widget.TextView(context).apply priceTextViewScope@{
                        text = "Rs ${parseItemText(item.text).third}"
                        setTextColor(android.graphics.Color.BLACK)
                        textSize = 16f
                        gravity = android.view.Gravity.END
                        this@priceTextViewScope.layoutParams = android.widget.LinearLayout.LayoutParams(
                            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            width = (80 * context.resources.displayMetrics.density).toInt()
                        }
                    }.also { addView(it) }
                }.also { addView(it) }

                if (index < itemsForBitmap.size - 1) {
                    android.widget.LinearLayout(context).apply separatorLayoutScope@{
                        orientation = android.widget.LinearLayout.HORIZONTAL
                        this@separatorLayoutScope.layoutParams = android.widget.LinearLayout.LayoutParams(
                            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            setMargins(0, 8, 0, 8)
                        }
                        
                        val dotsCount = 45
                        for (i in 0 until dotsCount) {
                            android.view.View(context).apply dotViewScope@{
                                setBackgroundColor(android.graphics.Color.GRAY)
                                this@dotViewScope.layoutParams = android.widget.LinearLayout.LayoutParams(
                                    2,
                                    1
                                ).apply {
                                    marginStart = 2
                                    marginEnd = 2
                                    weight = 1f
                                }
                            }.also { addView(it) }
                        }
                    }.also { addView(it) }
                }
            }.also { layout.addView(it) }
        }

        android.widget.TextView(context).apply totalTextViewScope@{
            text = "Total: Rs ${String.format("%.1f", totalSumForBitmap)}"
            setTextColor(android.graphics.Color.BLACK)
            textSize = 28f
            gravity = android.view.Gravity.END
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            this@totalTextViewScope.layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 32, 0, 0)
            }
        }.also { layout.addView(it) }

        // Add monthly summary text if provided
        if (!monthlySummaryText.isNullOrEmpty()) {
            android.widget.TextView(context).apply monthlySummaryTextViewScope@{
                text = monthlySummaryText
                setTextColor(android.graphics.Color.LTGRAY)
                textSize = 16f
                gravity = android.view.Gravity.START
                this@monthlySummaryTextViewScope.layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 16, 0, 8)
                }
            }.also { layout.addView(it) }
        }

        val width = (360 * context.resources.displayMetrics.density).toInt()
        val spec = android.view.View.MeasureSpec.makeMeasureSpec(width, android.view.View.MeasureSpec.EXACTLY)
        layout.measure(spec, android.view.View.MeasureSpec.UNSPECIFIED)
        
        val height = layout.measuredHeight
        layout.layout(0, 0, width, height)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.WHITE)
        layout.draw(canvas)

        Log.d("ShareExpensesList", "Bitmap created successfully: ${bitmap.width}x${bitmap.height}")
        return bitmap
    } catch (e: Exception) {
        Log.e("ShareExpensesList", "Error in createExpensesBitmap: ${e.message}")
        e.printStackTrace()
        // Consider re-throwing or returning a placeholder/error bitmap
        throw e // Re-throw to be caught by the calling Thread
    }
}

private fun saveBitmapToFile(context: Context, bitmap: Bitmap): File {
    val imagesDir = File(context.cacheDir, "images")
    if (!imagesDir.exists()) {
        imagesDir.mkdirs()
        Log.d("ShareExpensesList", "Created images directory: ${imagesDir.absolutePath}")
    } else {
        Log.d("ShareExpensesList", "Images directory already exists: ${imagesDir.absolutePath}")
    }
    
    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    val fileName = "expenses_$timeStamp.png"
    val imageFile = File(imagesDir, fileName)
    
    Log.d("ShareExpensesList", "Attempting to save bitmap to: ${imageFile.absolutePath}")

    try {
        FileOutputStream(imageFile).use { fos ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
            fos.flush()
        }
        Log.d("ShareExpensesList", "Bitmap saved successfully to ${imageFile.absolutePath}")
        return imageFile
    } catch (e: Exception) {
        Log.e("ShareExpensesList", "Error saving bitmap to file: ${imageFile.absolutePath}", e)
        e.printStackTrace()
        // Fallback or rethrow
        throw e // Re-throw to be caught by the calling Thread
    }
} 