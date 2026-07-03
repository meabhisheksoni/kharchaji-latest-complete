package com.example.monday.core.utils

import com.example.monday.data.models.TodoItem
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
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

// ═══════════════════════════════════════════════════════════════════
// PDF Export — Crisp, paginated, vector text. No compression artifacts.
// Uses Android's native PdfDocument (no external deps, available since API 19).
// ═══════════════════════════════════════════════════════════════════

fun shareExpensesAsPdf(
    context: Context,
    itemsToShare: Map<LocalDate, List<TodoItem>>,
    sumOfItemsToShare: Double,
    monthlySummaryText: String? = null,
    onFileReady: ((File) -> Unit)? = null
) {
    try {
        if (itemsToShare.isEmpty() || itemsToShare.values.all { it.isEmpty() }) {
            Toast.makeText(context, "Please select items to share", Toast.LENGTH_SHORT).show()
            return
        }

        val mainHandler = Handler(Looper.getMainLooper())
        Thread {
            var pdfDocument: PdfDocument? = null
            try {
                pdfDocument = buildExpensesPdf(context, itemsToShare, sumOfItemsToShare, monthlySummaryText)
                val file = savePdfToFile(context, pdfDocument)
                // Close doc *before* sharing — file is already written
                pdfDocument.close()
                pdfDocument = null

                val authority = "${context.packageName}.fileprovider"
                val uri = FileProvider.getUriForFile(context, authority, file)
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                val chooser = Intent.createChooser(shareIntent, "Share Expenses PDF")
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                mainHandler.post {
                    onFileReady?.invoke(file)
                    try {
                        context.startActivity(chooser)
                    } catch (e: Exception) {
                        Log.e("ShareExpensesPdf", "Error starting share activity", e)
                        Toast.makeText(context, "Error sharing: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("ShareExpensesPdf", "Error during PDF generation/sharing", e)
                mainHandler.post {
                    Toast.makeText(context, "Error sharing PDF: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                // Safety net: close doc if sharing path threw before close
                pdfDocument?.close()
            }
        }.start()
    } catch (e: Exception) {
        Log.e("ShareExpensesPdf", "Error in shareExpensesAsPdf: ${e.message}")
        Toast.makeText(context, "Error sharing expenses: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

/**
 * Builds a multi-page PDF from expense data.
 * A4 portrait = 595 × 842 points (72 dpi). Auto-paginates when content overflows.
 */
private fun buildExpensesPdf(
    context: Context,
    itemsMap: Map<LocalDate, List<TodoItem>>,
    totalSum: Double,
    monthlySummaryText: String? = null
): PdfDocument {
    // ── Page dimensions (A4 portrait in PostScript points) ──
    val pageWidth = 595
    val pageHeight = 842
    val marginLeft = 100f   // Huge margin as requested
    val marginRight = 100f
    val marginTop = 50f
    val marginBottom = 60f
    val usableBottom = pageHeight - marginBottom

    // ── Paint presets ──────────────────────────────────────
    val paintTitle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textSize = 18f
        color = android.graphics.Color.parseColor("#222222")
        textAlign = Paint.Align.CENTER
    }
    val paintSubtitle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 10f
        color = android.graphics.Color.parseColor("#888888")
        textAlign = Paint.Align.CENTER
    }
    val paintDateCol = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 9f
        color = android.graphics.Color.parseColor("#333333")
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val paintTimeCol = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 8f
        color = android.graphics.Color.parseColor("#999999") // Light gray for time
    }
    val paintItemName = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 10f
        color = android.graphics.Color.parseColor("#333333")
    }
    val paintItemQty = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 9f
        color = android.graphics.Color.parseColor("#CC3333")
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
        textAlign = Paint.Align.RIGHT // Right align for predictable gutter
    }
    val paintItemPrice = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 10f
        color = android.graphics.Color.parseColor("#333333")
        textAlign = Paint.Align.RIGHT
    }
    val paintSubtotal = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 10f
        color = android.graphics.Color.parseColor("#777777")
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC)
        textAlign = Paint.Align.RIGHT
    }
    val paintGrandTotalLabel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textSize = 14f
        color = android.graphics.Color.BLACK
    }
    val paintGrandTotalValue = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textSize = 14f
        color = android.graphics.Color.BLACK
        textAlign = Paint.Align.RIGHT
    }
    // Dashed border (small pen ink size)
    val paintDashedLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.BLACK
        strokeWidth = 0.5f
        style = Paint.Style.STROKE
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(4f, 4f), 0f)
    }
    val paintLineThick = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.BLACK
        strokeWidth = 1.5f
    }
    val paintMonthlySummary = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 10f
        color = android.graphics.Color.parseColor("#666666")
        textAlign = Paint.Align.CENTER
    }
    val paintPageNum = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 9f
        color = android.graphics.Color.parseColor("#BBBBBB")
        textAlign = Paint.Align.CENTER
    }

    val pdf = PdfDocument()
    var pageNumber = 1
    var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
    var page = pdf.startPage(pageInfo)
    var canvas = page.canvas
    var yPos = marginTop

    val showSubtotals = itemsMap.size > 1 && !itemsMap.containsKey(LocalDate.MIN)

    // Helper: finish current page, start new one, return fresh yPos
    fun newPage(): Float {
        // Page number footer on current page
        canvas.drawText("Page $pageNumber", pageWidth / 2f, pageHeight - 20f, paintPageNum)
        pdf.finishPage(page)
        pageNumber++
        pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        page = pdf.startPage(pageInfo)
        canvas = page.canvas
        return marginTop
    }

    // Helper: ensure enough vertical space; if not, paginate
    fun ensureSpace(needed: Float) {
        if (yPos + needed > usableBottom) {
            yPos = newPage()
        }
    }

    // ── Header (Centered) ────────────────────────────────────
    val centerX = pageWidth / 2f
    canvas.drawText("Kharchaji — Expense Report", centerX, yPos, paintTitle)
    yPos += 14f
    val generatedDate = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date())
    canvas.drawText("Generated: $generatedDate", centerX, yPos, paintSubtitle)
    yPos += 20f

    // ── Column header labels & structure ──────────────────────
    val colDateX = marginLeft
    val colItemX = colDateX + 65f            // Item Name Starts
    val colPriceX = pageWidth - marginRight  // Right-aligned Price
    val colQtyX = colPriceX - 100f           // Right-aligned Qty boundary (Moved much further left)
    val colNameEnd = colQtyX - 20f           // Truncate name boundary

    fun drawColumnHeaders() {
        val headerPaint = Paint(paintSubtitle).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 8f
            color = android.graphics.Color.parseColor("#888888")
            textAlign = Paint.Align.LEFT
        }
        val headerCenter = Paint(headerPaint).apply { textAlign = Paint.Align.CENTER }
        val headerRight = Paint(headerPaint).apply { textAlign = Paint.Align.RIGHT }
        
        // Top border of table
        canvas.drawLine(marginLeft, yPos - 12f, pageWidth - marginRight, yPos - 12f, paintDashedLine)
        
        canvas.drawText("DATE", colDateX, yPos, headerPaint)
        canvas.drawText("ITEM", colItemX, yPos, headerPaint)
        canvas.drawText("QTY", colQtyX, yPos, headerRight)
        canvas.drawText("AMOUNT", colPriceX, yPos, headerRight)
        
        yPos += 6f
        // Separator between header and first row
        canvas.drawLine(marginLeft, yPos, pageWidth - marginRight, yPos, paintDashedLine)
        yPos += 16f
    }

    drawColumnHeaders()

    // ── Render Items in Tabular Format ───────────────────────
    itemsMap.forEach { (dateKey, items) ->
        items.forEachIndexed { index, item ->
            ensureSpace(28f) // Space for multi-line row

            // Timestamp parsing
            val dateTime = Instant.ofEpochMilli(item.timestamp).atZone(ZoneId.systemDefault())
            val dateText = dateTime.format(DateTimeFormatter.ofPattern("dd MMM", Locale.getDefault()))
            val timeText = dateTime.format(DateTimeFormatter.ofPattern("hh:mm a", Locale.getDefault()))

            val (nameStr, quantity, priceStr) = parseItemText(item.text)
            
            // Overrides for Single date edge case
            val displayName = nameStr 

            // Left Col: Date (Only for first item) & Time (Always)
            if (index == 0) {
                canvas.drawText(dateText, colDateX, yPos, paintDateCol)
            }
            canvas.drawText(timeText, colDateX, yPos + 10f, paintTimeCol)

            // Item Name (Truncated)
            val maxNameWidth = colNameEnd - colItemX
            val truncatedName = truncateText(displayName, paintItemName, maxNameWidth)
            canvas.drawText(truncatedName, colItemX, yPos, paintItemName)

            // Qty
            if (quantity != null) {
                canvas.drawText(quantity, colQtyX, yPos, paintItemQty)
            }

            // Price (Right Aligned)
            canvas.drawText("₹$priceStr", colPriceX, yPos, paintItemPrice)
            
            yPos += 18f
            // Dashed line below each row
            canvas.drawLine(marginLeft, yPos, pageWidth - marginRight, yPos, paintDashedLine)
            yPos += 14f
        }

        // Subtotal row per date group
        if (showSubtotals) {
            ensureSpace(20f)
            val subtotal = items.sumOf { parsePrice(it.text) }
            canvas.drawText(
                "Subtotal: ₹${String.format(Locale.getDefault(), "%.0f", subtotal)}",
                colPriceX, yPos, paintSubtotal
            )
            yPos += 10f
            canvas.drawLine(marginLeft, yPos, pageWidth - marginRight, yPos, paintDashedLine)
            yPos += 14f
        }
    }

    // ── Grand Total ──────────────────────────────────────────
    ensureSpace(50f)
    yPos += 8f
    canvas.drawLine(marginLeft, yPos, pageWidth - marginRight, yPos, paintLineThick)
    yPos += 22f
    canvas.drawText("Total", colDateX, yPos, paintGrandTotalLabel)
    canvas.drawText(
        "₹${String.format(Locale.getDefault(), "%.0f", totalSum)}",
        colPriceX, yPos, paintGrandTotalValue
    )
    yPos += 20f

    // ── Monthly summary (optional) ───────────────────────────
    if (!monthlySummaryText.isNullOrEmpty()) {
        ensureSpace(20f)
        canvas.drawText(monthlySummaryText, centerX, yPos, paintMonthlySummary)
        yPos += 16f
    }

    // ── Footer on last page ──────────────────────────────────
    canvas.drawText("Page $pageNumber", pageWidth / 2f, pageHeight - 20f, paintPageNum)
    pdf.finishPage(page)

    return pdf
}

/**
 * Truncate text to fit within maxWidth, appending "…" if truncated.
 * Prevents text from bleeding into the quantity/price columns.
 */
private fun truncateText(text: String, paint: Paint, maxWidth: Float): String {
    if (paint.measureText(text) <= maxWidth) return text
    val ellipsis = "…"
    val ellipsisWidth = paint.measureText(ellipsis)
    var end = text.length
    while (end > 0 && paint.measureText(text, 0, end) + ellipsisWidth > maxWidth) {
        end--
    }
    return if (end > 0) text.substring(0, end) + ellipsis else ellipsis
}

private fun savePdfToFile(context: Context, pdfDocument: PdfDocument): File {
    val pdfDir = File(context.cacheDir, "pdfs")
    if (!pdfDir.exists()) pdfDir.mkdirs()
    // Clean old PDFs to prevent unbounded cache growth
    pdfDir.listFiles()?.forEach { old ->
        if (System.currentTimeMillis() - old.lastModified() > 24 * 60 * 60 * 1000) {
            old.delete()
        }
    }
    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    val pdfFile = File(pdfDir, "expenses_$timeStamp.pdf")
    FileOutputStream(pdfFile).use { fos ->
        pdfDocument.writeTo(fos)
        fos.flush()
    }
    return pdfFile
}

fun shareExpensesList(
    context: Context,
    itemsToShare: Map<LocalDate, List<TodoItem>>,
    sumOfItemsToShare: Double,
    monthlySummaryText: String? = null,
    onFileReady: ((File) -> Unit)? = null
) {
    try {
        if (itemsToShare.isEmpty() || itemsToShare.values.all { it.isEmpty() }) {
            Toast.makeText(context, "Please select items to share", Toast.LENGTH_SHORT).show()
            return
        }

        val mainHandler = Handler(Looper.getMainLooper())
        Thread {
            try {
                val bitmap = createExpensesBitmap(context, itemsToShare, sumOfItemsToShare, monthlySummaryText)
                val file = saveBitmapToFile(context, bitmap)
                val authority = "${context.packageName}.fileprovider"
                val uri = FileProvider.getUriForFile(context, authority, file)
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                val chooser = Intent.createChooser(shareIntent, "Share Expenses")
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                mainHandler.post {
                    onFileReady?.invoke(file)
                    try {
                        context.startActivity(chooser)
                    } catch (e: Exception) {
                        Log.e("ShareExpensesList", "Error starting share activity", e)
                        Toast.makeText(context, "Error sharing: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("ShareExpensesList", "Error during bitmap/sharing process", e)
                mainHandler.post {
                    Toast.makeText(context, "Error sharing expenses: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    } catch (e: Exception) {
        Log.e("ShareExpensesList", "Error in shareExpensesList: ${e.message}")
        Toast.makeText(context, "Error sharing expenses: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

private fun createExpensesBitmap(
    context: Context,
    itemsMap: Map<LocalDate, List<TodoItem>>,
    totalSumForBitmap: Double,
    monthlySummaryText: String? = null
): Bitmap {
    try {
        val layout = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setBackgroundColor(android.graphics.Color.WHITE)
            setPadding(32, 24, 32, 24)
        }
        
        // Add header dots
        android.widget.TextView(context).apply {
            text = "...."
            setTextColor(android.graphics.Color.BLACK)
            textSize = 20f
            gravity = android.view.Gravity.CENTER
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 16) }
        }.also { layout.addView(it) }

        val showSubtotals = itemsMap.size > 1 && !itemsMap.containsKey(LocalDate.MIN)
        
        itemsMap.forEach { (date, items) -> 
            if (date != LocalDate.MIN) {
                val dateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy, EEE", Locale.getDefault())
                android.widget.TextView(context).apply {
                    text = date.format(dateFormatter)
                    setTextColor(android.graphics.Color.parseColor("#4EB05B")) // Match C.Groceries approx
                    textSize = 14f
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    layoutParams = android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { setMargins(0, 8, 0, 8) }
                }.also { layout.addView(it) }
            }

            items.forEachIndexed { index, item ->
                val (nameStr, quantity, priceStr) = parseItemText(item.text)
                
                android.widget.LinearLayout(context).apply {
                    orientation = android.widget.LinearLayout.HORIZONTAL
                    layoutParams = android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { setMargins(0, 6, 0, 6) }
                    
                    android.widget.TextView(context).apply {
                        text = if (date == LocalDate.MIN) "${index + 1}. $nameStr" else nameStr
                        setTextColor(android.graphics.Color.BLACK)
                        textSize = 15f
                        layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
                    }.also { addView(it) }

                    if (quantity != null) {
                        android.widget.TextView(context).apply {
                            text = quantity
                            setTextColor(android.graphics.Color.RED)
                            textSize = 13f
                            setTypeface(null, android.graphics.Typeface.ITALIC)
                            gravity = android.view.Gravity.CENTER
                            layoutParams = android.widget.LinearLayout.LayoutParams(
                                (80 * context.resources.displayMetrics.density).toInt(),
                                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                            ).apply { marginStart = 8; marginEnd = 8 }
                        }.also { addView(it) }
                    }

                    android.widget.TextView(context).apply {
                        text = "₹$priceStr"
                        setTextColor(android.graphics.Color.BLACK)
                        textSize = 15f
                        gravity = android.view.Gravity.END
                        layoutParams = android.widget.LinearLayout.LayoutParams(
                            (80 * context.resources.displayMetrics.density).toInt(),
                            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                    }.also { addView(it) }
                }.also { layout.addView(it) }
            }
            
            if (showSubtotals) {
                val subtotal = items.sumOf { parsePrice(it.text) }
                
                // Add tiny divider
                android.view.View(context).apply {
                    setBackgroundColor(android.graphics.Color.LTGRAY)
                    layoutParams = android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                        (1 * context.resources.displayMetrics.density).toInt()
                    ).apply { setMargins(0, 12, 0, 8) }
                }.also { layout.addView(it) }
                
                // Subtotal text
                android.widget.TextView(context).apply {
                    text = "Subtotal: ₹${String.format(Locale.getDefault(), "%.0f", subtotal)}"
                    setTextColor(android.graphics.Color.LTGRAY)
                    textSize = 12f
                    gravity = android.view.Gravity.START
                    layoutParams = android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { setMargins(0, 0, 0, 16) }
                }.also { layout.addView(it) }
            }
        }

        // Grand Total Divider
        android.view.View(context).apply {
            setBackgroundColor(android.graphics.Color.BLACK)
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                (2 * context.resources.displayMetrics.density).toInt()
            ).apply { setMargins(0, 16, 0, 16) }
        }.also { layout.addView(it) }

        android.widget.TextView(context).apply {
            text = "Total: ₹${String.format(Locale.getDefault(), "%.0f", totalSumForBitmap)}"
            setTextColor(android.graphics.Color.BLACK)
            textSize = 24f
            gravity = android.view.Gravity.START
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 8, 0, 0) }
        }.also { layout.addView(it) }

        if (!monthlySummaryText.isNullOrEmpty()) {
            android.widget.TextView(context).apply {
                text = monthlySummaryText
                setTextColor(android.graphics.Color.LTGRAY)
                textSize = 16f
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 16, 0, 8) }
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
        return bitmap
    } catch (e: Exception) {
        Log.e("ShareExpensesList", "Error in createExpensesBitmap: ${e.message}")
        throw e
    }
}

private fun saveBitmapToFile(context: Context, bitmap: Bitmap): File {
    val imagesDir = File(context.cacheDir, "images")
    if (!imagesDir.exists()) imagesDir.mkdirs()
    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    val fileName = "expenses_$timeStamp.png"
    val imageFile = File(imagesDir, fileName)
    FileOutputStream(imageFile).use { fos ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
        fos.flush()
    }
    return imageFile
}

object ShareUtils {
    fun shareCalendarViewHtml(
        context: Context,
        expenses: List<com.example.monday.Expense>,
        filterDescription: String?,
        allCategories: List<String>,
        categoriesByType: Map<String, List<String>>
    ) {
        val today = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, dd MMMM yyyy"))
        val totalAmount = expenses.sumOf { it.amount }
        
        val html = StringBuilder()
        html.append("<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\"><title>Expenses Calendar View</title>")
        html.append("<style>body{font-family:sans-serif;padding:20px;}table{width:100%;border-collapse:collapse;}th,td{border:1px solid #ddd;padding:8px;text-align:left;}th{background-color:#f2f2f2;}</style></head><body>")
        html.append("<h1>Expenses Calendar View</h1>")
        html.append("<p>Generated on: $today</p>")
        if (filterDescription != null) html.append("<p>Filters: $filterDescription</p>")
        
        html.append("<table><tr><th>Date</th><th>Description</th><th>Qty</th><th>Category</th><th>Amount</th></tr>")
        expenses.sortedByDescending { it.timestamp }.forEach { expense ->
            val date = Instant.ofEpochMilli(expense.timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
            html.append("<tr><td>${date.format(DateTimeFormatter.ofPattern("dd/MM/yy"))}</td>")
            html.append("<td>${expense.description}</td>")
            html.append("<td>${expense.quantity ?: ""}</td>")
            html.append("<td>${expense.category ?: ""}</td>")
            html.append("<td>₹${expense.amount}</td></tr>")
        }
        html.append("</table><h2>Total: ₹$totalAmount</h2></body></html>")
        
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/html"
            putExtra(Intent.EXTRA_SUBJECT, "Expenses Calendar View - $today")
            putExtra(Intent.EXTRA_TEXT, html.toString())
        }
        context.startActivity(Intent.createChooser(intent, "Share Calendar View"))
    }
}

/**
 * Re-shares an existing file from history.
 */
fun reShareFile(context: Context, filePath: String, type: String) {
    try {
        val file = File(filePath)
        if (!file.exists()) {
            Toast.makeText(context, "Original file no longer exists", Toast.LENGTH_SHORT).show()
            return
        }

        val authority = "${context.packageName}.fileprovider"
        val uri = FileProvider.getUriForFile(context, authority, file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            this.type = if (type == "pdf") "application/pdf" else "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(shareIntent, "Share Recent Export")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    } catch (e: Exception) {
        Log.e("ReShare", "Error re-sharing file", e)
        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
