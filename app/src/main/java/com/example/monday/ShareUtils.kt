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
import java.time.format.DateTimeFormatter
import java.time.Instant
import java.time.ZoneId
import java.time.YearMonth
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

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

/**
 * Utility functions for sharing expenses data
 */
object ShareUtils {

    /**
     * Share expenses as plain text
     */
    fun shareExpensesList(context: Context, expenses: List<TodoItem>, date: LocalDate) {
        val dateFormatter = DateTimeFormatter.ofPattern("EEEE, dd MMMM yyyy")
        val formattedDate = date.format(dateFormatter)
        
        val totalAmount = expenses.sumOf { parsePrice(it.text) }
        
        val sb = StringBuilder()
        sb.append("Expenses for $formattedDate\n\n")
        
        expenses.forEach { expense ->
            val (name, quantity, price) = parseItemText(expense.text)
            val quantityText = if (quantity != null) " ($quantity)" else ""
            sb.append("• $name$quantityText - ₹$price\n")
        }
        
        sb.append("\nTotal: ₹$totalAmount")
        
        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, sb.toString())
            type = "text/plain"
        }
        
        context.startActivity(Intent.createChooser(shareIntent, "Share expenses"))
    }
    
    /**
     * Generate HTML content for sharing expenses filtered by selected people (primary categories)
     * @param expenses List of expenses to share
     * @param date The date of expenses
     * @param selectedPeople List of selected primary categories (people) to filter by
     * @return HTML content as a string
     */
    fun generateHtmlContent(expenses: List<TodoItem>, date: LocalDate, selectedPeople: List<String>): String {
        val dateFormatter = DateTimeFormatter.ofPattern("EEEE, dd MMMM yyyy")
        val formattedDate = date.format(dateFormatter)
        
        // Filter expenses by selected people (primary categories)
        val filteredExpenses = if (selectedPeople.isEmpty()) {
            expenses
        } else {
            expenses.filter { expense ->
                expense.hasPrimaryCategory && 
                expense.categories?.any { category -> selectedPeople.contains(category) } == true
            }
        }
        
        val totalAmount = filteredExpenses.sumOf { parsePrice(it.text) }
        
        // Group expenses by primary category for better organization
        val expensesByCategory = filteredExpenses.groupBy { expense ->
            expense.categories?.firstOrNull { cat -> 
                selectedPeople.contains(cat)
            } ?: "Other"
        }
        
        // Generate HTML content
        val html = StringBuilder()
        html.append("""
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Expenses for $formattedDate</title>
                <style>
                    body {
                        font-family: 'Segoe UI', Roboto, Oxygen, Ubuntu, Cantarell, 'Open Sans', sans-serif;
                        line-height: 1.6;
                        color: #333;
                        max-width: 800px;
                        margin: 0 auto;
                        padding: 20px;
                        background-color: #f5f5f5;
                    }
                    .header {
                        background-color: #6200ee;
                        color: white;
                        padding: 20px;
                        border-radius: 8px 8px 0 0;
                        margin-bottom: 0;
                    }
                    .content {
                        background-color: white;
                        padding: 20px;
                        border-radius: 0 0 8px 8px;
                        box-shadow: 0 2px 10px rgba(0,0,0,0.1);
                        margin-top: 0;
                    }
                    .category {
                        margin-top: 20px;
                        border-bottom: 1px solid #eee;
                        padding-bottom: 10px;
                    }
                    .category-title {
                        font-size: 1.2rem;
                        font-weight: bold;
                        color: #6200ee;
                    }
                    .expense-item {
                        display: flex;
                        justify-content: space-between;
                        padding: 8px 0;
                        border-bottom: 1px dashed #eee;
                    }
                    .expense-name {
                        flex-grow: 1;
                    }
                    .expense-price {
                        font-weight: bold;
                        margin-left: 20px;
                    }
                    .total {
                        margin-top: 20px;
                        font-size: 1.2rem;
                        font-weight: bold;
                        text-align: right;
                        padding: 10px;
                        background-color: #f9f9f9;
                        border-radius: 4px;
                    }
                    .footer {
                        margin-top: 30px;
                        text-align: center;
                        font-size: 0.8rem;
                        color: #666;
                    }
                </style>
            </head>
            <body>
                <div class="header">
                    <h1>Expenses for $formattedDate</h1>
                </div>
                <div class="content">
        """.trimIndent())
        
        // Add expenses grouped by category
        for ((category, categoryExpenses) in expensesByCategory) {
            html.append("""
                <div class="category">
                    <div class="category-title">$category</div>
            """.trimIndent())
            
            for (expense in categoryExpenses) {
                val (name, quantity, price) = parseItemText(expense.text)
                val quantityText = if (quantity != null) " ($quantity)" else ""
                
                html.append("""
                    <div class="expense-item">
                        <span class="expense-name">$name$quantityText</span>
                        <span class="expense-price">₹$price</span>
                    </div>
                """.trimIndent())
            }
            
            // Add category subtotal
            val categoryTotal = categoryExpenses.sumOf { parsePrice(it.text) }
            html.append("""
                    <div class="expense-item" style="font-weight: bold;">
                        <span class="expense-name">Subtotal</span>
                        <span class="expense-price">₹$categoryTotal</span>
                    </div>
                </div>
            """.trimIndent())
        }
        
        // Add total amount
        html.append("""
                <div class="total">
                    Total: ₹$totalAmount
                </div>
                <div class="footer">
                    Generated by Kharchaji Expense Tracker
                </div>
            </div>
            </body>
            </html>
        """.trimIndent())
        
        return html.toString()
    }
    
    /**
     * Generate HTML content for the entire expense database with categories
     * @param allExpenses List of all expenses in the database
     * @param filterByCategories Optional list of categories to filter by
     * @return HTML content as a string
     */
    fun generateFullDatabaseHtml(allExpenses: List<TodoItem>, filterByCategories: List<String>? = null): String {
        // Filter expenses if categories are provided
        val filteredExpenses = if (filterByCategories.isNullOrEmpty()) {
            allExpenses
        } else {
            allExpenses.filter { expense ->
                expense.categories?.any { category -> filterByCategories.contains(category) } == true
            }
        }
        
        // Group expenses by date
        val expensesByDate = filteredExpenses.groupBy { expense ->
            val date = Instant.ofEpochMilli(expense.timestamp)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
            date
        }.toSortedMap(Comparator.reverseOrder()) // Sort by date descending
        
        val totalAmount = filteredExpenses.sumOf { parsePrice(it.text) }
        
        // Generate HTML content
        val html = StringBuilder()
        html.append("""
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Complete Expense Database</title>
                <style>
                    body {
                        font-family: 'Segoe UI', Roboto, Oxygen, Ubuntu, Cantarell, 'Open Sans', sans-serif;
                        line-height: 1.6;
                        color: #333;
                        max-width: 1000px;
                        margin: 0 auto;
                        padding: 20px;
                        background-color: #f5f5f5;
                    }
                    .header {
                        background-color: #6200ee;
                        color: white;
                        padding: 20px;
                        border-radius: 8px 8px 0 0;
                        margin-bottom: 0;
                        position: sticky;
                        top: 0;
                        z-index: 100;
                    }
                    .content {
                        background-color: white;
                        padding: 20px;
                        border-radius: 0 0 8px 8px;
                        box-shadow: 0 2px 10px rgba(0,0,0,0.1);
                        margin-top: 0;
                    }
                    .date-section {
                        margin: 30px 0;
                        border-bottom: 2px solid #6200ee;
                        padding-bottom: 10px;
                    }
                    .date-header {
                        background-color: #f0e6ff;
                        padding: 10px 15px;
                        border-radius: 4px;
                        font-size: 1.3rem;
                        font-weight: bold;
                        color: #6200ee;
                        margin-bottom: 15px;
                        position: sticky;
                        top: 80px;
                        z-index: 99;
                    }
                    .category {
                        margin: 15px 0;
                        border-bottom: 1px solid #eee;
                        padding-bottom: 10px;
                    }
                    .category-title {
                        font-size: 1.2rem;
                        font-weight: bold;
                        color: #6200ee;
                        background-color: #f9f9f9;
                        padding: 5px 10px;
                        border-radius: 4px;
                    }
                    .expense-item {
                        display: flex;
                        justify-content: space-between;
                        padding: 8px 0;
                        border-bottom: 1px dashed #eee;
                    }
                    .expense-name {
                        flex-grow: 1;
                    }
                    .expense-price {
                        font-weight: bold;
                        margin-left: 20px;
                    }
                    .expense-categories {
                        font-size: 0.85rem;
                        color: #666;
                        margin-top: 2px;
                        font-style: italic;
                    }
                    .date-total {
                        margin-top: 10px;
                        font-size: 1.1rem;
                        font-weight: bold;
                        text-align: right;
                        padding: 8px;
                        background-color: #f0f0f0;
                        border-radius: 4px;
                    }
                    .grand-total {
                        margin-top: 30px;
                        font-size: 1.4rem;
                        font-weight: bold;
                        text-align: right;
                        padding: 15px;
                        background-color: #e6e6ff;
                        border-radius: 4px;
                        border: 1px solid #6200ee;
                    }
                    .footer {
                        margin-top: 30px;
                        text-align: center;
                        font-size: 0.9rem;
                        color: #666;
                        padding: 20px;
                        border-top: 1px solid #ddd;
                    }
                    .summary-section {
                        margin: 20px 0;
                        padding: 15px;
                        background-color: #f9f9f9;
                        border-radius: 8px;
                    }
                    .summary-title {
                        font-size: 1.2rem;
                        font-weight: bold;
                        margin-bottom: 10px;
                    }
                    table {
                        width: 100%;
                        border-collapse: collapse;
                    }
                    th, td {
                        padding: 8px;
                        text-align: left;
                        border-bottom: 1px solid #ddd;
                    }
                    th {
                        background-color: #f0e6ff;
                        color: #6200ee;
                    }
                    tr:nth-child(even) {
                        background-color: #f9f9f9;
                    }
                    .category-pill {
                        display: inline-block;
                        padding: 2px 8px;
                        margin: 2px;
                        background-color: #e6e6ff;
                        border-radius: 12px;
                        font-size: 0.8rem;
                    }
                    .nav-menu {
                        position: fixed;
                        top: 100px;
                        right: 20px;
                        background-color: white;
                        padding: 10px;
                        border-radius: 8px;
                        box-shadow: 0 2px 10px rgba(0,0,0,0.1);
                        max-height: 300px;
                        overflow-y: auto;
                        z-index: 101;
                    }
                    .nav-menu a {
                        display: block;
                        padding: 5px;
                        text-decoration: none;
                        color: #6200ee;
                    }
                    .nav-menu a:hover {
                        background-color: #f0e6ff;
                    }
                </style>
            </head>
            <body>
                <div class="header">
                    <h1>Complete Expense Database</h1>
                    <p>${filteredExpenses.size} expenses from ${expensesByDate.size} dates</p>
                </div>
                
                <div class="content">
                    <div class="summary-section">
                        <div class="summary-title">Summary</div>
                        <table>
                            <tr>
                                <th>Total Expenses</th>
                                <td>${filteredExpenses.size}</td>
                            </tr>
                            <tr>
                                <th>Total Amount</th>
                                <td>₹$totalAmount</td>
                            </tr>
                            <tr>
                                <th>Date Range</th>
                                <td>${expensesByDate.keys.lastOrNull()?.format(DateTimeFormatter.ofPattern("dd MMM yyyy"))} - ${expensesByDate.keys.firstOrNull()?.format(DateTimeFormatter.ofPattern("dd MMM yyyy"))}</td>
                            </tr>
                        </table>
                    </div>
                    
                    <!-- Navigation menu -->
                    <div class="nav-menu">
                        <strong>Jump to Date:</strong>
                        ${expensesByDate.keys.joinToString("") { date ->
                            "<a href=\"#date-${date}\">$date</a>"
                        }}
                    </div>
        """.trimIndent())
        
        // Add expenses grouped by date
        for ((date, dateExpenses) in expensesByDate) {
            val dateFormatter = DateTimeFormatter.ofPattern("EEEE, dd MMMM yyyy")
            val formattedDate = date.format(dateFormatter)
            val dateTotalAmount = dateExpenses.sumOf { parsePrice(it.text) }
            
            html.append("""
                <div class="date-section" id="date-${date}">
                    <div class="date-header">$formattedDate</div>
            """.trimIndent())
            
            // Group by category within each date
            val expensesByCategory = dateExpenses.groupBy { expense ->
                expense.categories?.firstOrNull() ?: "Uncategorized"
            }.toSortedMap() // Sort categories alphabetically
            
            for ((category, categoryExpenses) in expensesByCategory) {
                html.append("""
                    <div class="category">
                        <div class="category-title">$category</div>
                """.trimIndent())
                
                for (expense in categoryExpenses) {
                    val (name, quantity, price) = parseItemText(expense.text)
                    val quantityText = if (quantity != null) " ($quantity)" else ""
                    
                    // Get all categories for this expense
                    val categoriesHtml = expense.categories?.joinToString("") { cat ->
                        "<span class=\"category-pill\">$cat</span>"
                    } ?: ""
                    
                    html.append("""
                        <div class="expense-item">
                            <div class="expense-name">
                                <div>$name$quantityText</div>
                                <div class="expense-categories">$categoriesHtml</div>
                            </div>
                            <span class="expense-price">₹$price</span>
                        </div>
                    """.trimIndent())
                }
                
                // Add category subtotal
                val categoryTotal = categoryExpenses.sumOf { parsePrice(it.text) }
                html.append("""
                        <div class="expense-item" style="font-weight: bold;">
                            <span class="expense-name">Subtotal</span>
                            <span class="expense-price">₹$categoryTotal</span>
                        </div>
                    </div>
                """.trimIndent())
            }
            
            // Add date total
            html.append("""
                <div class="date-total">
                    Date Total: ₹$dateTotalAmount
                </div>
            </div>
            """.trimIndent())
        }
        
        // Add grand total
        html.append("""
                <div class="grand-total">
                    Grand Total: ₹$totalAmount
                </div>
                
                <div class="footer">
                    Generated by Kharchaji Expense Tracker on ${LocalDate.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy"))}
                </div>
            </div>
            </body>
            </html>
        """.trimIndent())
        
        return html.toString()
    }
    
    /**
     * Share expenses as HTML via a temporary file
     */
    fun shareExpensesAsHtml(context: Context, expenses: List<TodoItem>, date: LocalDate, selectedPeople: List<String>) {
        try {
            val htmlContent = generateHtmlContent(expenses, date, selectedPeople)
            
            // Create temporary file
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val htmlFile = File(context.cacheDir, "expenses_$timeStamp.html")
            htmlFile.writeText(htmlContent)
            
            // Get content URI via FileProvider
            val fileUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                htmlFile
            )
            
            // Create and start share intent
            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_STREAM, fileUri)
                type = "text/html"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            context.startActivity(Intent.createChooser(shareIntent, "Share expenses as HTML"))
            
        } catch (e: Exception) {
            Log.e("ShareUtils", "Error sharing HTML content", e)
        }
    }
    
    /**
     * Share the entire expense database as HTML
     */
    suspend fun shareFullDatabaseAsHtml(context: Context, todoViewModel: TodoViewModel, filterByCategories: List<String>? = null) {
        withContext(Dispatchers.IO) {
            Log.d("ShareUtils", "Fetching all expenses for HTML export")
            val allExpenses = todoViewModel.getAllExpensesForExport()
            
            // Create a modified categoriesByType map that places selected categories only in primary
            val categoriesByType = if (!filterByCategories.isNullOrEmpty()) {
                val regularCategoriesByType = todoViewModel.getAllCategoriesByType()
                // Create a new map with the selected categories only in primary
                mapOf(
                    "primary" to filterByCategories,
                    "secondary" to (regularCategoriesByType["secondary"] ?: emptyList()),
                    "tertiary" to (regularCategoriesByType["tertiary"] ?: emptyList()),
                    "other" to (regularCategoriesByType["other"] ?: emptyList())
                )
            } else {
                todoViewModel.getAllCategoriesByType()
            }
            
            // Get expenses for export based on filter
            val filteredExpenses = if (!filterByCategories.isNullOrEmpty()) {
                allExpenses.filter { expense ->
                    expense.categories?.any { category -> filterByCategories.contains(category) } == true
                }
            } else {
                allExpenses
            }

            // Generate HTML with the modified category structure
            val htmlContent = generateFullDatabaseHtml(filteredExpenses, null)
            
            // The rest of the sharing code
            val filename = "expenses_export_${LocalDate.now().format(DateTimeFormatter.ISO_DATE)}.html"
            val htmlFile = File(context.cacheDir, filename)
            
                htmlFile.writeText(htmlContent)
            
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                htmlFile
            )
            
            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_STREAM, uri)
                type = "text/html"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            val shareIntentChooser = Intent.createChooser(shareIntent, "Share Expenses HTML")
            shareIntentChooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            
            try {
                context.startActivity(shareIntentChooser)
                Log.d("ShareUtils", "HTML sharing intent launched successfully")
        } catch (e: Exception) {
                Log.e("ShareUtils", "Error sharing HTML: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error sharing HTML: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    /**
     * Builds a JSON object of daily expense totals
     */
    private fun buildDailyTotalsJson(expenses: List<Expense>): String {
        // Group expenses by date
        val expensesByDate = expenses.groupBy { expense ->
            val date = Instant.ofEpochMilli(expense.timestamp)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
            date.toString() // YYYY-MM-DD format
        }
        
        // Calculate daily totals
        val dailyTotals = expensesByDate.mapValues { (_, dailyExpenses) ->
            dailyExpenses.sumOf { it.amount }
        }
        
        // Convert to JSON
        val jsonObject = JSONObject()
        dailyTotals.forEach { (date, total) ->
            jsonObject.put(date, total)
        }
        
        return jsonObject.toString()
    }
    
    /**
     * Builds a JSON object of monthly expense totals
     */
    private fun buildMonthlyTotalsJson(expenses: List<Expense>): String {
        // Group expenses by month
        val expensesByMonth = expenses.groupBy { expense ->
            val date = Instant.ofEpochMilli(expense.timestamp)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
            date.format(DateTimeFormatter.ofPattern("yyyy-MM")) // YYYY-MM format
        }
        
        // Calculate monthly totals
        val monthlyTotals = expensesByMonth.mapValues { (_, monthlyExpenses) ->
            monthlyExpenses.sumOf { it.amount }
        }
        
        // Convert to JSON
        val jsonObject = JSONObject()
        monthlyTotals.forEach { (month, total) ->
            jsonObject.put(month, total)
        }
        
        return jsonObject.toString()
    }
    
    /**
     * Builds a JSON object of expenses grouped by date
     */
    private fun buildExpensesByDateJson(expenses: List<Expense>): String {
        // Group expenses by date
        val expensesByDate = expenses.groupBy { expense ->
            val date = Instant.ofEpochMilli(expense.timestamp)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
            date.toString() // YYYY-MM-DD format
        }
        
        // Convert to JSON
        val jsonObject = JSONObject()
        
        expensesByDate.forEach { (date, dailyExpenses) ->
            val expensesArray = JSONArray()
            
            dailyExpenses.forEach { expense ->
                val expenseObject = JSONObject()
                expenseObject.put("description", expense.description)
                expenseObject.put("amount", expense.amount)
                expenseObject.put("quantity", expense.quantity ?: "")
                expenseObject.put("category", expense.category ?: "")
                expensesArray.put(expenseObject)
            }
            
            jsonObject.put(date, expensesArray)
        }
        
        return jsonObject.toString()
    }
    
    /**
     * Shares the expense calendar view as an HTML file
     */
    suspend fun shareCalendarViewHtml(
        context: Context,
        expenses: List<Expense>,
        filterDescription: String? = null,
        allCategories: List<String> = emptyList(),
        categoriesByType: Map<String, List<String>> = emptyMap()
    ) {
        withContext(Dispatchers.IO) {
            val htmlContent = generateCalendarViewHtml(expenses, filterDescription, allCategories, categoriesByType)
            
            // Create a temporary HTML file
            val htmlFile = File(context.cacheDir, "Expense_Calendar.html")
            htmlFile.writeText(htmlContent)
            
            // Get the FileProvider URI
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                htmlFile
            )
            
            // Create a share intent
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/html"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            val shareIntentChooser = Intent.createChooser(intent, "Share Expense Calendar")
            shareIntentChooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            
            try {
                context.startActivity(shareIntentChooser)
                Log.d("ShareUtils", "Calendar HTML sharing intent launched successfully")
        } catch (e: Exception) {
                Log.e("ShareUtils", "Error sharing calendar HTML: ${e.message}", e)
                withContext(Dispatchers.Main) {
            Toast.makeText(context, "Error sharing calendar: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    /**
     * Generates an HTML calendar view of expenses
     * @param expenses List of expenses to display in the calendar
     * @param filterDescription Optional description of the filter applied
     * @param allCategories List of all unique categories used in any expense
     * @param categoriesByType Map of categories organized by type (primary, secondary, tertiary, other)
     * @return HTML content as a string
     */
    fun generateCalendarViewHtml(
        expenses: List<Expense>,
        filterDescription: String? = null,
        allCategories: List<String> = emptyList(),
        categoriesByType: Map<String, List<String>> = emptyMap()
    ): String {
        // Build JSON data for the calendar view
        val dailyTotalsJson = buildDailyTotalsJson(expenses)
        val monthlyTotalsJson = buildMonthlyTotalsJson(expenses)
        val expensesByDateJson = buildExpensesByDateJson(expenses)
        
        // Convert allCategories to JSON array
        val categoriesJson = if (allCategories.isNotEmpty()) {
            allCategories.joinToString(prefix = "[", postfix = "]") { "\"${it.replace("\"", "\\\"")}\"" }
        } else {
            "[]"
        }
        
        // Convert categoriesByType to JSON object
        val categoriesByTypeJson = if (categoriesByType.isNotEmpty()) {
            val jsonBuilder = StringBuilder("{")
            val entries = categoriesByType.entries.joinToString(",") { (type, categories) ->
                "\"$type\": [${categories.joinToString(",") { "\"${it.replace("\"", "\\\"")}\"" }}]"
            }
            jsonBuilder.append(entries)
            jsonBuilder.append("}")
            jsonBuilder.toString()
        } else {
            "{}"
        }
        
        // Create filter status HTML if filter is applied
        val filterStatusHtml = if (filterDescription != null && filterDescription.isNotEmpty()) {
            """<div id="filterStatus" class="filter-status">
                Showing expenses for: ${filterDescription}
            </div>"""
        } else {
            """<div id="filterStatus" class="filter-status" style="display:none;"></div>"""
        }
        
        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Expense Calendar</title>
                <style>
                    :root {
                        --primary-color: #2874A6;
                        --primary-light: rgba(40, 116, 166, 0.1);
                        --secondary-color: #17A589;
                        --tertiary-color: #9A7D0A;
                        --green: #27AE60;
                        --orange: #F39C12;
                        --red: #ff3333;
                        --light-gray: #f9f9f9;
                        --border-color: #ddd;
                        --gray: #888;
                    }
                    body {
                        font-family: system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
                        margin: 0; padding: 20px; background-color: var(--light-gray); color: #333;
                    }
                    .container {
                        max-width: 1100px; margin: auto; background: white; padding: 25px 40px;
                        box-shadow: 0 4px 12px rgba(0,0,0,0.08); border-radius: 12px;
                    }
                    h1, h2, h3 { font-weight: 600; color: var(--dark-gray); }
                    h1 { text-align: center; border-bottom: 2px solid var(--border-color); padding-bottom: 15px; }
                    .view-switcher { text-align: center; margin-bottom: 25px; display: flex; flex-wrap: wrap; justify-content: center; gap: 10px; }
                    .view-switcher button {
                        padding: 10px 20px; font-size: 1rem; cursor: pointer; border: 1px solid var(--border-color);
                        background-color: white; border-radius: 20px; transition: all 0.3s ease; font-weight: 500;
                    }
                    .view-switcher button.active { background-color: var(--primary-color); color: white; border-color: var(--primary-color); }
                    
                    .view-content { display: none; }
                    .view-content.active { display: block; animation: viewFadeIn 0.5s; }
                    @keyframes viewFadeIn { from { opacity: 0; } to { opacity: 1; } }
                    
                    .filter-status {
                        text-align: center; background-color: var(--primary-light); color: var(--primary-color);
                        padding: 8px; border-radius: 8px; margin-bottom: 15px; font-weight: 500;
                    }
                    
                    /* Category Selection Modal Styles */
                    .category-modal {
                        display: none;
                        position: fixed;
                        z-index: 2000;
                        left: 0;
                        top: 0;
                        width: 100%;
                        height: 100%;
                        background-color: rgba(0,0,0,0.5);
                    }
                    .category-modal-content {
                        background-color: white;
                        margin: 5% auto;
                        padding: 20px;
                        border-radius: 8px;
                        width: 90%;
                        max-width: 800px;
                        max-height: 80vh;
                        overflow-y: auto;
                    }
                    .category-modal-header {
                        display: flex;
                        justify-content: space-between;
                        align-items: center;
                        margin-bottom: 15px;
                        border-bottom: 1px solid var(--border-color);
                        padding-bottom: 10px;
                    }
                    .category-modal-title {
                        font-size: 1.5rem;
                        font-weight: bold;
                        margin: 0;
                    }
                    .category-modal-close {
                        font-size: 1.5rem;
                        font-weight: bold;
                        cursor: pointer;
                        color: var(--gray);
                    }
                    .category-columns {
                        display: grid;
                        grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
                        gap: 20px;
                        margin-bottom: 15px;
                    }
                    .category-column {
                        background-color: var(--light-gray);
                        border-radius: 8px;
                        padding: 10px;
                    }
                    .category-column-title {
                        font-size: 1.2rem;
                        font-weight: bold;
                        margin-bottom: 10px;
                        padding-bottom: 5px;
                        border-bottom: 1px solid var(--border-color);
                        text-transform: capitalize;
                    }
                    .category-list {
                        max-height: 300px;
                        overflow-y: auto;
                    }
                    .category-item {
                        display: flex;
                        align-items: center;
                        padding: 8px 0;
                        border-bottom: 1px solid var(--border-color);
                    }
                    .category-checkbox {
                        margin-right: 10px;
                    }
                    .category-modal-actions {
                        display: flex;
                        justify-content: flex-end;
                        gap: 10px;
                        margin-top: 15px;
                    }
                    .category-modal-button {
                        padding: 8px 16px;
                        border-radius: 4px;
                        font-weight: 500;
                        cursor: pointer;
                        border: none;
                    }
                    .category-modal-cancel {
                        background-color: var(--light-gray);
                        color: var(--gray);
                    }
                    .category-modal-apply {
                        background-color: var(--primary-color);
                        color: white;
                    }
                    .select-all-container {
                        display: flex;
                        align-items: center;
                        margin-bottom: 10px;
                        padding-bottom: 10px;
                        border-bottom: 2px solid var(--border-color);
                    }
                    
                    /* Rest of the existing CSS */
                    .calendar-container {
                        margin-top: 20px;
                        position: relative;
                    }
                    .calendar-nav-container {
                        display: flex;
                        justify-content: space-between;
                        align-items: center;
                        margin-bottom: 15px;
                    }
                    .calendar-nav-button {
                        background-color: white;
                        border: 2px solid var(--primary-color);
                        font-size: 24px;
                        font-weight: bold;
                        cursor: pointer;
                        color: var(--primary-color);
                        width: 40px;
                        height: 40px;
                        display: flex;
                        justify-content: center;
                        align-items: center;
                        padding: 0;
                        transition: all 0.2s;
                        border-radius: 50%;
                        box-shadow: 0 2px 5px rgba(0,0,0,0.1);
                    }
                    .calendar-nav-button:hover {
                        background-color: var(--primary-light);
                    }
                    .calendar-title {
                        text-align: center;
                        flex-grow: 1;
                    }
                    .calendar-title h2 {
                        font-size: 28px;
                        font-weight: bold;
                        margin: 0;
                        color: var(--primary-color);
                    }
                    .total-amount {
                        font-size: 18px;
                        color: var(--gray);
                        margin-top: 5px;
                    }
                    .calendar-header-cell {
                        text-align: center;
                        font-weight: 600;
                        padding: 10px;
                        color: var(--gray);
                        font-size: 1em;
                    }
                    .calendar-grid {
                        display: grid;
                        grid-template-columns: repeat(7, 1fr);
                        gap: 8px;
                    }
                    .calendar-day {
                        aspect-ratio: 1.1;
                        border: 1px solid #e9ecef;
                        border-radius: 8px;
                        padding: 12px;
                        position: relative;
                        background: white;
                    }
                    .calendar-day.empty {
                        background: transparent;
                        border: none;
                    }
                    .calendar-day.today {
                        border: 2px solid var(--primary-color);
                        background-color: var(--primary-light);
                    }
                    .day-number {
                        font-weight: 600;
                        font-size: 16px;
                        position: absolute;
                        top: 8px;
                        left: 12px;
                    }
                    .expense-amount {
                        position: absolute;
                        bottom: 10px;
                        right: 10px;
                        padding: 4px 10px;
                        border-radius: 16px;
                        font-weight: 500;
                        text-align: center;
                        white-space: nowrap;
                    }
                    .calendar-day.has-expense {
                        cursor: pointer;
                        transition: transform 0.2s, box-shadow 0.2s;
                    }
                    .calendar-day.has-expense:hover {
                        transform: translateY(-3px);
                        box-shadow: 0 4px 8px rgba(0,0,0,0.1);
                    }
                    /* Expense amount styling */
                    .high-amount {
                        background-color: rgba(255, 51, 51, 0.5);
                        color: black;
                    }
                    .medium-amount {
                        background-color: rgba(243, 156, 18, 0.5);
                        color: black;
                    }
                    .low-amount {
                        background-color: rgba(39, 174, 96, 0.5);
                        color: black;
                    }
                    .modal {
                        display: none;
                        position: fixed;
                        z-index: 1000;
                        left: 0;
                        top: 0;
                        width: 100%;
                        height: 100%;
                        background-color: rgba(0,0,0,0.4);
                    }
                    .modal-content {
                        background-color: #fefefe;
                        margin: 5% auto;
                        padding: 25px;
                        border-radius: 8px;
                        width: 90%;
                        max-width: 550px;
                        max-height: 80vh;
                        overflow-y: auto;
                        position: relative;
                        animation: fadeIn 0.3s;
                    }
                    @keyframes fadeIn {
                        from {opacity: 0; transform: scale(0.95);}
                        to {opacity: 1; transform: scale(1);}
                    }
                    .modal-close {
                        color: #aaa;
                        position: absolute;
                        top: 10px;
                        right: 20px;
                        font-size: 28px;
                        font-weight: bold;
                        cursor: pointer;
                    }
                    .modal-nav {
                        display: flex;
                        justify-content: space-between;
                        align-items: center;
                        margin-bottom: 20px;
                    }
                    .modal-nav-button {
                        background: none;
                        border: none;
                        font-size: 24px;
                        color: var(--primary-color);
                        cursor: pointer;
                    }
                    .modal-title {
                        font-size: 22px;
                        font-weight: bold;
                        color: var(--primary-color);
                        text-align: center;
                        margin-bottom: 20px;
                    }
                    .daily-expense-table {
                        width: 100%;
                        border-collapse: separate;
                        border-spacing: 0;
                        margin-top: 10px;
                    }
                    .daily-expense-table td {
                        padding: 12px;
                        text-align: left;
                        border-bottom: 1px solid var(--border-color);
                    }
                    .daily-expense-table th {
                        background-color: transparent;
                        border-bottom: 2px solid var(--border-color);
                        font-weight: 600;
                        font-size: 1.1em;
                        text-align: left;
                        padding: 12px;
                    }
                    .daily-expense-table tbody tr:last-child td {
                        border-bottom: none;
                    }
                    .daily-expense-table tfoot td {
                        font-weight: bold;
                        border-top: 2px solid var(--border-color);
                        padding: 12px;
                    }
                    .daily-expense-table .price-col {
                        text-align: right;
                    }
                    .daily-expense-table .qty-col {
                        text-align: center;
                    }
                    /* Add vertical separators for the main calendar's daily view modal */
                    #day-details-modal .daily-expense-table {
                        border-collapse: collapse;
                    }
                     #day-details-modal .daily-expense-table th,
                     #day-details-modal .daily-expense-table td {
                        border-right: 1px solid var(--border-color);
                    }
                     #day-details-modal .daily-expense-table th:last-child,
                     #day-details-modal .daily-expense-table td:last-child {
                        border-right: none;
                    }
                    .modal-footer {
                        text-align: center;
                        font-size: 12px;
                        color: var(--gray);
                        margin-top: 15px;
                    }
                    .bar-chart .bar-item { margin-bottom: 8px; }
                    .bar-chart .bar-label { font-size: 0.9em; margin-bottom: 4px; color: var(--gray); }
                    .bar-wrapper { background-color: #e9ecef; border-radius: 4px; overflow: hidden; }
                    .bar { height: 28px; background: linear-gradient(90deg, var(--primary-color), #3498DB); border-radius: 4px; box-sizing: border-box; transition: width 0.5s ease-in-out; display: flex; align-items: center; justify-content: flex-end; padding-right: 10px; color: white; font-size: 0.9em; font-weight: 500; white-space: nowrap; }
                    /* Styles for the new monthly calendar popup */
                    .modal-content-large {
                        background-color: #fefefe;
                        margin: 3% auto;
                        padding: 25px;
                        border-radius: 8px;
                        width: 95%;
                        max-width: 1200px; /* Changed from 1400px for a more centered feel */
                        position: relative;
                        animation: fadeIn 0.3s;
                    }
                    .monthly-popup-container {
                        display: flex;
                        gap: 20px;
                    }
                    .popup-calendar-view {
                        flex: 3;
                    }
                    .popup-daily-details-view {
                        flex: 2;
                        background-color: #f9f9f9;
                        border-radius: 8px;
                        padding: 20px;
                        transform: translateX(105%);
                        transition: transform 0.4s ease-in-out;
                        overflow-y: auto;
                        max-height: 75vh;
                    }
                    .popup-daily-details-view.active {
                        transform: translateX(0);
                    }
                    #popup-calendar-view .calendar-grid {
                        gap: 5px;
                    }
                    #popup-calendar-view .calendar-day {
                        aspect-ratio: 1;
                        padding: 8px;
                    }
                    #popup-calendar-view .day-number {
                        font-size: 14px;
                        top: 5px;
                        left: 8px;
                    }
                    #popup-calendar-view .expense-amount {
                        font-size: 0.8em;
                        bottom: 5px;
                        right: 5px;
                    }
                    .calendar-day.popup-selected {
                        border-color: var(--secondary-color);
                        box-shadow: 0 0 10px rgba(23, 165, 137, 0.4);
                    }
                    .popup-daily-details-view h3 {
                        margin-top: 0;
                        color: var(--primary-color);
                        font-size: 1.3em;
                    }
                    .bar-end-label {
                        position: absolute;
                        top: -1.4em;
                        font-size: 0.8rem;
                        color: var(--gray);
                        white-space: nowrap;
                        transform: translateX(-100%);
                        padding-right: 5px;
                    }
                    .monthly-summary-grid {
                        display: grid;
                        grid-template-columns: 1fr 2fr;
                        gap: 40px;
                        align-items: start;
                        margin-top: 20px;
                    }
                    .summary-table {
                        width: 100%;
                        border-collapse: collapse;
                    }
                    .summary-table td {
                        padding: 10px 0;
                        font-size: 1rem;
                        border-bottom: 1px solid #f0f0f0;
                    }
                    .summary-table tr:last-child td {
                        border-bottom: none;
                    }
                    .summary-table td:last-child {
                        text-align: right;
                        font-weight: 500;
                    }
                    .bar-chart .bar-item { 
                        margin-bottom: 16px; 
                        cursor: pointer;
                        position: relative;
                    }
                    .bar-wrapper {
                        background-color: #e9ecef;
                        border-radius: 4px;
                        overflow: hidden;
                    }
                </style>
            </head>
            <body>
                <div class="container">
                    <h1>Expense Report</h1>
                    
                    <div class="view-switcher">
                        <button class="active" data-view="calendar">📅 Calendar</button>
                        <button data-view="monthly">📊 Monthly</button>
                        <button data-view="categories">📁 Categories</button>
                    </div>
                    
                    $filterStatusHtml
                    
                    <div id="calendar-view" class="view-content active">
                        <div id="calendar-container" class="calendar-container">
                            <!-- Calendar will be generated here -->
                        </div>
                    </div>
                    
                    <div id="monthly-view" class="view-content">
                        <!-- Monthly summary will be generated here -->
                    </div>
                    
                    <div id="categories-view" class="view-content">
                        <!-- Categories breakdown will be generated here -->
                    </div>
                    
                    <!-- Modal for day details -->
                    <div id="day-details-modal" class="modal">
                        <div id="day-details-content" class="modal-content">
                            <span class="modal-close">&times;</span>
                            <!-- Modal content will be generated dynamically -->
                        </div>
                    </div>

                    <!-- Modal for monthly calendar popup -->
                    <div id="monthly-calendar-modal" class="modal">
                        <div class="modal-content-large">
                             <span id="monthly-calendar-modal-close" class="modal-close">&times;</span>
                            <div class="monthly-popup-container">
                                <div id="popup-calendar-view" class="popup-calendar-view">
                                    <!-- Popup calendar will be generated here -->
                                </div>
                                <div id="popup-daily-details-view" class="popup-daily-details-view">
                                    <!-- Daily details will be shown here -->
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
                
                <script>
                    // Parse JSON data
                    const dailyTotals = ${dailyTotalsJson};
                    const monthlyTotals = ${monthlyTotalsJson};
                    const expensesByDate = ${expensesByDateJson};
                    const allCategories = ${categoriesJson};
                    const categoriesByType = ${categoriesByTypeJson};
                    
                    // Track current view and date
                    let currentView = 'calendar';
                    let currentYear = new Date().getFullYear();
                    let currentMonth = new Date().getMonth();
                    
                    // Selected categories for filtering
                    let selectedCategories = [];
                    
                    // Initialize the app
                    document.addEventListener('DOMContentLoaded', function() {
                        // Set up view switcher
                        document.querySelectorAll('.view-switcher button').forEach(button => {
                            button.addEventListener('click', function() {
                                const view = this.getAttribute('data-view');
                                document.querySelectorAll('.view-switcher button').forEach(btn => {
                                    btn.classList.remove('active');
                                });
                                this.classList.add('active');
                                switchView(view);
                            });
                        });
                        
                        // Set up modal close buttons
                        document.querySelectorAll('.modal-close').forEach(closeBtn => {
                            closeBtn.addEventListener('click', function() {
                                this.closest('.modal').style.display = 'none';
                            });
                        });
                        
                        // Initialize with calendar view
                        switchView('calendar');
                        
                        // Set up category selection modal
                        setupCategoryModal();
                    });
                    
                    // Set up the category selection modal
                    function setupCategoryModal() {
                        // Create the modal if it doesn't exist
                        if (!document.getElementById('category-modal')) {
                            const modal = document.createElement('div');
                            modal.id = 'category-modal';
                            modal.className = 'category-modal';
                            
                            let modalContent = '<div class="category-modal-content">';
                            modalContent += '<div class="category-modal-header">';
                            modalContent += '<h3 class="category-modal-title">Select Categories</h3>';
                            modalContent += '<span class="category-modal-close">&times;</span>';
                            modalContent += '</div>';
                            
                            // Select all option
                            modalContent += '<div class="select-all-container">';
                            modalContent += '<input type="checkbox" id="select-all-categories" class="category-checkbox">';
                            modalContent += '<label for="select-all-categories"><strong>Select All</strong></label>';
                            modalContent += '</div>';
                            
                            // Category columns
                            modalContent += '<div class="category-columns">';
                            
                            // Check if we have categoriesByType data
                            if (Object.keys(categoriesByType).length > 0) {
                                // Create a column for each category type
                                const categoryTypes = ['primary', 'secondary', 'tertiary', 'other'];
                                categoryTypes.forEach(type => {
                                    if (categoriesByType[type] && categoriesByType[type].length > 0) {
                                        const typeTitle = type === 'other' ? 'Uncategorized' : 
                                                         (type.charAt(0).toUpperCase() + type.slice(1));
                                        
                                        modalContent += '<div class="category-column">';
                                        modalContent += '<div class="category-column-title">' + typeTitle + '</div>';
                                        modalContent += '<div class="category-list">';
                                        
                                        categoriesByType[type].forEach((category, index) => {
                                            const categoryId = type + '-category-' + index;
                                            modalContent += '<div class="category-item">';
                                            modalContent += '<input type="checkbox" id="' + categoryId + '" class="category-checkbox" value="' + category + '">';
                                            modalContent += '<label for="' + categoryId + '">' + category + '</label>';
                                            modalContent += '</div>';
                                        });
                                        
                                        modalContent += '</div>'; // Close category-list
                                        modalContent += '</div>'; // Close category-column
                                    }
                                });
                            } else {
                                // Fallback to a single column if no categoriesByType data
                                modalContent += '<div class="category-column">';
                                modalContent += '<div class="category-column-title">All Categories</div>';
                                modalContent += '<div class="category-list">';
                                
                                if (allCategories && allCategories.length > 0) {
                                    allCategories.forEach((category, index) => {
                                        modalContent += '<div class="category-item">';
                                        modalContent += '<input type="checkbox" id="category-' + index + '" class="category-checkbox" value="' + category + '">';
                                        modalContent += '<label for="category-' + index + '">' + category + '</label>';
                                        modalContent += '</div>';
                                    });
                                } else {
                                    modalContent += '<p>No categories available.</p>';
                                }
                                
                                modalContent += '</div>'; // Close category-list
                                modalContent += '</div>'; // Close category-column
                            }
                            
                            modalContent += '</div>'; // Close category-columns
                            
                            // Action buttons
                            modalContent += '<div class="category-modal-actions">';
                            modalContent += '<button class="category-modal-button category-modal-cancel">Cancel</button>';
                            modalContent += '<button class="category-modal-button category-modal-apply">Apply</button>';
                            modalContent += '</div>';
                            
                            modalContent += '</div>'; // Close modal content
                            modal.innerHTML = modalContent;
                            document.body.appendChild(modal);
                            
                            // Set up event listeners
                            const closeBtn = modal.querySelector('.category-modal-close');
                            closeBtn.addEventListener('click', () => {
                                modal.style.display = 'none';
                            });
                            
                            const cancelBtn = modal.querySelector('.category-modal-cancel');
                            cancelBtn.addEventListener('click', () => {
                                modal.style.display = 'none';
                            });
                            
                            const applyBtn = modal.querySelector('.category-modal-apply');
                            applyBtn.addEventListener('click', () => {
                                // Get selected categories
                                const checkboxes = modal.querySelectorAll('.category-checkbox:checked');
                                selectedCategories = Array.from(checkboxes)
                                    .filter(cb => cb.id !== 'select-all-categories')
                                    .map(cb => cb.value);
                                
                                // Update filter status
                                updateFilterStatus();
                                
                                // Close modal
                                modal.style.display = 'none';
                            });
                            
                            // Select all functionality
                            const selectAllCheckbox = modal.querySelector('#select-all-categories');
                            selectAllCheckbox.addEventListener('change', () => {
                                const checkboxes = modal.querySelectorAll('.category-checkbox:not(#select-all-categories)');
                                checkboxes.forEach(cb => {
                                    cb.checked = selectAllCheckbox.checked;
                                });
                            });
                            
                            // When individual checkboxes change, update select all
                            const checkboxes = modal.querySelectorAll('.category-checkbox:not(#select-all-categories)');
                            checkboxes.forEach(cb => {
                                cb.addEventListener('change', () => {
                                    const allChecked = Array.from(checkboxes).every(cb => cb.checked);
                                    const anyChecked = Array.from(checkboxes).some(cb => cb.checked);
                                    selectAllCheckbox.checked = allChecked;
                                    selectAllCheckbox.indeterminate = anyChecked && !allChecked;
                                });
                            });
                            
                            // Close when clicking outside
                            window.addEventListener('click', (event) => {
                                if (event.target === modal) {
                                    modal.style.display = 'none';
                                }
                            });
                        }
                    }
                    
                    // Update filter status display
                    function updateFilterStatus() {
                        const filterStatus = document.getElementById('filterStatus');
                        if (selectedCategories.length > 0) {
                            filterStatus.innerHTML = 'Showing expenses for: ' + selectedCategories.join(', ');
                            filterStatus.style.display = 'block';
                        } else {
                            filterStatus.style.display = 'none';
                        }
                    }
                    
                    // View switcher functionality
                    function switchView(view) {
                        currentView = view;
                        
                        // Hide all views
                        document.querySelectorAll('.view-content').forEach(el => {
                            el.classList.remove('active');
                        });
                        
                        // Show selected view
                        document.getElementById(view + '-view').classList.add('active');
                        
                        // Render the appropriate view
                        if (view === 'calendar') {
                            renderCalendarView();
                        } else if (view === 'monthly') {
                            renderMonthlyView();
                        } else if (view === 'categories') {
                            renderCategoriesView();
                        }
                    }
                    
                    // Calendar view functions
                    function renderCalendarView() {
                        const calendarContainer = document.getElementById('calendar-container');
                        
                        // Create navigation and header
                        const navContainer = document.createElement('div');
                        navContainer.className = 'calendar-nav-container';
                        
                        const prevButton = document.createElement('button');
                        prevButton.className = 'calendar-nav-button';
                        prevButton.innerHTML = '&lt;';
                        prevButton.addEventListener('click', () => {
                            navigateMonth(-1);
                        });
                        
                        const nextButton = document.createElement('button');
                        nextButton.className = 'calendar-nav-button';
                        nextButton.innerHTML = '&gt;';
                        nextButton.addEventListener('click', () => {
                            navigateMonth(1);
                        });
                        
                        const titleContainer = document.createElement('div');
                        titleContainer.className = 'calendar-title';
                        
                        navContainer.appendChild(prevButton);
                        navContainer.appendChild(titleContainer);
                        navContainer.appendChild(nextButton);
                        
                        // Create calendar grid
                        const calendarGrid = document.createElement('div');
                        calendarGrid.className = 'calendar-grid';
                        
                        // Add day headers
                        const daysOfWeek = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];
                        daysOfWeek.forEach(day => {
                            const headerCell = document.createElement('div');
                            headerCell.className = 'calendar-header-cell';
                            headerCell.textContent = day;
                            calendarGrid.appendChild(headerCell);
                        });
                        
                        // Clear previous content and add new elements
                        calendarContainer.innerHTML = '';
                        calendarContainer.appendChild(navContainer);
                        calendarContainer.appendChild(calendarGrid);
                        
                        // Render the calendar days
                        renderCalendarDays(calendarGrid, titleContainer);
                    }
                    
                    function renderCalendarDays(calendarGrid, titleContainer) {
                        // Get the first day of the month and the number of days
                        const firstDay = new Date(currentYear, currentMonth, 1);
                        const lastDay = new Date(currentYear, currentMonth + 1, 0);
                        const daysInMonth = lastDay.getDate();
                        const startingDayOfWeek = firstDay.getDay();
                        
                        // Update the title
                        const monthNames = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'];
                        const monthYearText = document.createElement('h2');
                        monthYearText.textContent = monthNames[currentMonth] + ' ' + currentYear;
                        
                        // Calculate the total for the month
                        const monthKey = currentYear + '-' + String(currentMonth + 1).padStart(2, '0');
                        let monthTotal = 0;
                        
                        // Sum up daily totals for the current month
                        for (const dateKey in dailyTotals) {
                            if (dateKey.startsWith(monthKey)) {
                                monthTotal += dailyTotals[dateKey];
                            }
                        }
                        
                        const totalElement = document.createElement('div');
                        totalElement.className = 'total-amount';
                        totalElement.textContent = 'Total: ₹' + monthTotal.toLocaleString();
                        
                        titleContainer.innerHTML = '';
                        titleContainer.appendChild(monthYearText);
                        titleContainer.appendChild(totalElement);
                        
                        // Add empty cells for days before the first day of the month
                        for (let i = 0; i < startingDayOfWeek; i++) {
                            const emptyCell = document.createElement('div');
                            emptyCell.className = 'calendar-day empty';
                            calendarGrid.appendChild(emptyCell);
                        }
                        
                        // Add cells for each day of the month
                        for (let day = 1; day <= daysInMonth; day++) {
                            const dayCell = document.createElement('div');
                            dayCell.className = 'calendar-day';
                            
                            // Check if this is today
                            const today = new Date();
                            if (today.getDate() === day && today.getMonth() === currentMonth && today.getFullYear() === currentYear) {
                                dayCell.classList.add('today');
                            }
                            
                            // Add day number
                            const dayNumber = document.createElement('div');
                            dayNumber.className = 'day-number';
                            dayNumber.textContent = day;
                            dayCell.appendChild(dayNumber);
                            
                            // Format the date key (YYYY-MM-DD)
                            const dateKey = currentYear + '-' + String(currentMonth + 1).padStart(2, '0') + '-' + String(day).padStart(2, '0');
                            
                            // Check if there are expenses for this day
                            if (dateKey in dailyTotals) {
                                const amount = dailyTotals[dateKey];
                                dayCell.classList.add('has-expense');
                                
                                // Create expense indicator
                                const expenseIndicator = document.createElement('div');
                                expenseIndicator.className = 'expense-amount';
                                expenseIndicator.textContent = '₹' + amount;
                                
                                // Add color class based on amount
                                if (amount > 4000) {
                                    expenseIndicator.classList.add('high-amount');
                                } else if (amount >= 1000) {
                                    expenseIndicator.classList.add('medium-amount');
                                } else {
                                    expenseIndicator.classList.add('low-amount');
                                }
                                
                                dayCell.appendChild(expenseIndicator);
                                
                                // Add click event to show details
                                dayCell.addEventListener('click', () => {
                                    showDayDetails(dateKey);
                                });
                            }
                            
                            calendarGrid.appendChild(dayCell);
                        }
                        
                        // Add empty cells for days after the last day of the month if needed
                        const totalCells = startingDayOfWeek + daysInMonth;
                        const remainingCells = 7 - (totalCells % 7);
                        if (remainingCells < 7) {
                            for (let i = 0; i < remainingCells; i++) {
                                const emptyCell = document.createElement('div');
                                emptyCell.className = 'calendar-day empty';
                                calendarGrid.appendChild(emptyCell);
                            }
                        }
                    }
                    
                    function navigateMonth(change) {
                        currentMonth += change;
                        
                        if (currentMonth > 11) {
                            currentMonth = 0;
                            currentYear++;
                        } else if (currentMonth < 0) {
                            currentMonth = 11;
                            currentYear--;
                        }
                        
                        renderCalendarView();
                    }
                    
                    function showDayDetails(dateKey) {
                        const modal = document.getElementById('day-details-modal');
                        const modalContent = document.getElementById('day-details-content');

                        if (!expensesByDate[dateKey] || expensesByDate[dateKey].length === 0) {
                            return;
                        }

                        // Sort expenses by amount, descending
                        const dailyExpenses = [...expensesByDate[dateKey]];
                        dailyExpenses.sort((a, b) => b.amount - a.amount);

                        // --- Navigation ---
                        const allExpenseDates = Object.keys(expensesByDate).sort();
                        const currentIndex = allExpenseDates.indexOf(dateKey);
                        const prevDate = currentIndex > 0 ? allExpenseDates[currentIndex - 1] : null;
                        const nextDate = currentIndex < allExpenseDates.length - 1 ? allExpenseDates[currentIndex + 1] : null;
                        
                        // Format the date for display
                        const dateParts = dateKey.split('-');
                        const year = dateParts[0];
                        const month = parseInt(dateParts[1]);
                        const day = parseInt(dateParts[2]);
                        
                        const monthNames = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'];
                        const formattedDate = day + ' ' + monthNames[month-1] + ' ' + year;

                        // Create modal content
                        let modalHTML = '<div class="modal-nav">' +
                            (prevDate ? '<button id="modal-prev-btn" class="modal-nav-button">&lt;</button>' : '<div></div>') +
                            '<div class="modal-title">' + formattedDate + '</div>' +
                            (nextDate ? '<button id="modal-next-btn" class="modal-nav-button">&gt;</button>' : '<div></div>') +
                            '</div>' +
                            '<table class="daily-expense-table">' +
                            '<thead><tr><th>Description</th><th class="qty-col">Qty</th><th class="price-col">Price</th></tr></thead>' +
                            '<tbody>';

                        let total = 0;
                        dailyExpenses.forEach(expense => {
                            total += expense.amount;
                            modalHTML += '<tr>' +
                                '<td>' + expense.description + '</td>' +
                                '<td class="qty-col">' + (expense.quantity || '-') + '</td>' +
                                '<td class="price-col">₹' + expense.amount.toLocaleString('en-IN') + '</td>' +
                                '</tr>';
                        });
                        
                        modalHTML += '</tbody>' +
                            '<tfoot><tr>' +
                            '<td colspan="2">Total</td>' +
                            '<td class="price-col">₹' + total.toLocaleString('en-IN') + '</td>' +
                            '</tr></tfoot>' +
                            '</table>' +
                            '<div class="modal-footer">Press ESC to return to calendar view</div>';
                        
                        modalContent.innerHTML = modalHTML;
                        
                        // Show the modal
                        modal.style.display = 'block';
                        
                        // Add event listeners for navigation after content is rendered
                        if (prevDate) {
                            document.getElementById('modal-prev-btn').onclick = () => showDayDetails(prevDate);
                        }
                        if (nextDate) {
                            document.getElementById('modal-next-btn').onclick = () => showDayDetails(nextDate);
                        }
                        
                        // Close modal when clicking outside or pressing ESC
                        window.onclick = function(event) {
                            if (event.target === modal) {
                                modal.style.display = 'none';
                            }
                        };
                        
                        document.onkeydown = function(event) {
                            if (event.key === 'Escape') {
                                modal.style.display = 'none';
                            } else if (event.key === 'ArrowLeft' && prevDate) {
                                showDayDetails(prevDate);
                            } else if (event.key === 'ArrowRight' && nextDate) {
                                showDayDetails(nextDate);
                            }
                        };
                        
                        // Close button functionality
                        const closeButton = modalContent.querySelector('.modal-close');
                        if (closeButton) {
                            closeButton.onclick = function() {
                                modal.style.display = 'none';
                            };
                        }
                    }
                    
                    // Monthly view functions
                    function renderMonthlyView() {
                        const monthlyContainer = document.getElementById('monthly-view');

                        let headerHTML = '<div class="monthly-title-container">' +
                            '<h2>Monthly Summary</h2>' +
                            '<button class="choose-categories-btn">Choose Categories</button>' +
                            '</div>';
                        
                        // Sort months chronologically, newest first
                        const sortedMonths = Object.keys(monthlyTotals).sort((a, b) => b.localeCompare(a));
                        
                        if (sortedMonths.length === 0) {
                            monthlyContainer.innerHTML = headerHTML + '<p>No monthly data to display.</p>';
                            return;
                        }
                        
                        // Find the maximum monthly total for scaling the bars
                        const maxTotal = Math.max(0, ...Object.values(monthlyTotals));
                        
                        let gridHTML = '<div class="monthly-summary-grid">';
                        
                        // Left side: Table view
                        let tableHTML = '<div class="summary-table-container">';
                        tableHTML += '<table class="summary-table">';
                        tableHTML += '<tbody>';
                        const monthNamesFull = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'];
                        sortedMonths.forEach(monthKey => {
                            const total = monthlyTotals[monthKey];
                            const [year, month] = monthKey.split('-');
                            const monthName = monthNamesFull[parseInt(month) - 1];
                            
                            tableHTML += '<tr>' +
                                '<td>' + monthName + ' ' + year + '</td>' +
                                '<td style="text-align: right;">₹' + total.toLocaleString('en-IN') + '</td>' +
                                '</tr>';
                        });
                        tableHTML += '</tbody></table></div>';
                        
                        gridHTML += tableHTML;

                        // Right side: Bar chart view
                        let chartHTML = '<div class="bar-chart">';
                        const monthNamesShort = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
                        sortedMonths.forEach(monthKey => {
                            const total = monthlyTotals[monthKey];
                            const percentage = maxTotal > 0 ? (total / maxTotal) * 100 : 0;
                            
                            const [year, month] = monthKey.split('-');
                            const monthName = monthNamesShort[parseInt(month) - 1];
                            
                            chartHTML += '<div class="bar-item" data-monthkey="' + monthKey + '">' +
                                '<div class="bar-wrapper">' +
                                    '<div class="bar-end-label" style="left: ' + percentage + '%">' + monthName + ' ' + year + '</div>' +
                                    '<div class="bar" style="width: ' + percentage + '%">' +
                                        '<span>' + (total > 0 ? '₹' + total.toLocaleString('en-IN') : '') + '</span>' +
                                    '</div>' +
                                '</div>' +
                            '</div>';
                        });
                        chartHTML += '</div>';

                        gridHTML += chartHTML;
                        gridHTML += '</div>'; // close monthly-summary-grid
                        
                        monthlyContainer.innerHTML = headerHTML + gridHTML;
                        
                        // Add click listener for the new functionality
                        monthlyContainer.querySelector('.bar-chart').addEventListener('click', function(event) {
                            const barItem = event.target.closest('.bar-item');
                            if (barItem) {
                                const monthKey = barItem.dataset.monthkey;
                                openMonthlyCalendarPopup(monthKey);
                            }
                        });
                        
                        // Add click listener for the Choose Categories button
                        monthlyContainer.querySelector('.choose-categories-btn').addEventListener('click', function() {
                            document.getElementById('category-modal').style.display = 'block';
                        });
                    }
                    
                    // Categories view functions
                    function renderCategoriesView() {
                        // This would be implemented if category data is available
                        const categoriesContainer = document.getElementById('categories-view');
                        categoriesContainer.innerHTML = '<h2>Categories View</h2><p>Category breakdown is not available in this export.</p>';
                    }
                    
                    // --- New functions for monthly calendar popup ---
                    let popupCurrentYear, popupCurrentMonth;

                    function openMonthlyCalendarPopup(monthKey) {
                        const [year, month] = monthKey.split('-');
                        popupCurrentYear = parseInt(year);
                        popupCurrentMonth = parseInt(month) - 1;

                        const modal = document.getElementById('monthly-calendar-modal');
                        modal.style.display = 'block';

                        renderPopupCalendar();

                        // Hide details panel initially
                        document.getElementById('popup-daily-details-view').classList.remove('active');
                        document.getElementById('popup-daily-details-view').innerHTML = '';

                        // Close logic
                        const closeBtn = document.getElementById('monthly-calendar-modal-close');
                        closeBtn.onclick = () => modal.style.display = 'none';
                        window.onclick = (event) => {
                            if (event.target === modal) {
                                modal.style.display = 'none';
                            }
                        };
                    }

                    function renderPopupCalendar() {
                        const calendarContainer = document.getElementById('popup-calendar-view');
                        calendarContainer.innerHTML = ''; // Clear previous content

                        // Create navigation and header
                        const navContainer = document.createElement('div');
                        navContainer.className = 'calendar-nav-container';

                        const prevButton = document.createElement('button');
                        prevButton.className = 'calendar-nav-button';
                        prevButton.innerHTML = '&lt;';
                        prevButton.onclick = () => {
                            popupCurrentMonth--;
                            if (popupCurrentMonth < 0) { popupCurrentMonth = 11; popupCurrentYear--; }
                            renderPopupCalendar();
                            document.getElementById('popup-daily-details-view').classList.remove('active');
                        };

                        const nextButton = document.createElement('button');
                        nextButton.className = 'calendar-nav-button';
                        nextButton.innerHTML = '&gt;';
                        nextButton.onclick = () => {
                            popupCurrentMonth++;
                            if (popupCurrentMonth > 11) { popupCurrentMonth = 0; popupCurrentYear++; }
                            renderPopupCalendar();
                            document.getElementById('popup-daily-details-view').classList.remove('active');
                        };

                        const titleContainer = document.createElement('div');
                        titleContainer.className = 'calendar-title';

                        navContainer.appendChild(prevButton);
                        navContainer.appendChild(titleContainer);
                        navContainer.appendChild(nextButton);

                        // Create calendar grid
                        const calendarGrid = document.createElement('div');
                        calendarGrid.className = 'calendar-grid';

                        // Add day headers
                        const daysOfWeek = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];
                        daysOfWeek.forEach(day => {
                            const headerCell = document.createElement('div');
                            headerCell.className = 'calendar-header-cell';
                            headerCell.textContent = day;
                            calendarGrid.appendChild(headerCell);
                        });

                        calendarContainer.appendChild(navContainer);
                        calendarContainer.appendChild(calendarGrid);

                        // --- Render calendar days for the popup ---
                        const firstDay = new Date(popupCurrentYear, popupCurrentMonth, 1);
                        const lastDay = new Date(popupCurrentYear, popupCurrentMonth + 1, 0);
                        const daysInMonth = lastDay.getDate();
                        const startingDayOfWeek = firstDay.getDay();

                        const monthNames = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'];
                        const monthYearText = document.createElement('h2');
                        monthYearText.textContent = monthNames[popupCurrentMonth] + ' ' + popupCurrentYear;

                        const monthKey = popupCurrentYear + '-' + String(popupCurrentMonth + 1).padStart(2, '0');
                        let monthTotal = 0;
                        for (const dateKey in dailyTotals) {
                            if (dateKey.startsWith(monthKey)) {
                                monthTotal += dailyTotals[dateKey];
                            }
                        }

                        const totalElement = document.createElement('div');
                        totalElement.className = 'total-amount';
                        totalElement.textContent = 'Total: ₹' + monthTotal.toLocaleString();

                        titleContainer.innerHTML = '';
                        titleContainer.appendChild(monthYearText);
                        titleContainer.appendChild(totalElement);

                        for (let i = 0; i < startingDayOfWeek; i++) {
                            calendarGrid.appendChild(Object.assign(document.createElement('div'), { className: 'calendar-day empty' }));
                        }

                        for (let day = 1; day <= daysInMonth; day++) {
                            const dayCell = document.createElement('div');
                            dayCell.className = 'calendar-day';

                            const today = new Date();
                            if (today.getDate() === day && today.getMonth() === popupCurrentMonth && today.getFullYear() === popupCurrentYear) {
                                dayCell.classList.add('today');
                            }

                            dayCell.appendChild(Object.assign(document.createElement('div'), { className: 'day-number', textContent: day }));

                            const dateKey = popupCurrentYear + '-' + String(popupCurrentMonth + 1).padStart(2, '0') + '-' + String(day).padStart(2, '0');

                            if (dateKey in dailyTotals) {
                                const amount = dailyTotals[dateKey];
                                dayCell.classList.add('has-expense');
                                
                                const expenseIndicator = document.createElement('div');
                                expenseIndicator.className = 'expense-amount';
                                expenseIndicator.textContent = '₹' + amount;
                                if (amount > 4000) expenseIndicator.classList.add('high-amount');
                                else if (amount >= 1000) expenseIndicator.classList.add('medium-amount');
                                else expenseIndicator.classList.add('low-amount');
                                dayCell.appendChild(expenseIndicator);

                                dayCell.addEventListener('click', () => {
                                    document.querySelectorAll('#popup-calendar-view .calendar-day').forEach(d => d.classList.remove('popup-selected'));
                                    dayCell.classList.add('popup-selected');
                                    showDailyExpensesInSidePanel(dateKey);
                                });
                            }
                            calendarGrid.appendChild(dayCell);
                        }
                    }

                    function showDailyExpensesInSidePanel(dateKey) {
                        const detailsContainer = document.getElementById('popup-daily-details-view');
                        const dailyExpenses = [...(expensesByDate[dateKey] || [])];
                        dailyExpenses.sort((a, b) => b.amount - a.amount);

                        const date = new Date(dateKey + 'T00:00:00');
                        const formattedDate = date.toLocaleDateString('en-US', { weekday: 'long', year: 'numeric', month: 'long', day: 'numeric' });

                        let detailsHTML = '<h3>' + formattedDate + '</h3>';
                        if (dailyExpenses.length === 0) {
                            detailsHTML += '<p>No expenses for this day.</p>';
                        } else {
                            detailsHTML += '<table class="daily-expense-table">' +
                                '<thead><tr><th>Description</th><th class="qty-col">Qty</th><th class="price-col">Amount</th></tr></thead><tbody>';

                            dailyExpenses.forEach(expense => {
                                detailsHTML += '<tr>' +
                                    '<td>' + expense.description + '</td>' +
                                    '<td class="qty-col">' + (expense.quantity || '-') + '</td>' +
                                    '<td class="price-col">₹' + expense.amount.toLocaleString('en-IN') + '</td>' +
                                    '</tr>';
                            });

                            detailsHTML += '</tbody></table>';
                        }

                        detailsContainer.innerHTML = detailsHTML;
                        detailsContainer.classList.add('active');
                    }
                </script>
            </body>
            </html>
        """.trimIndent()
    }
} 