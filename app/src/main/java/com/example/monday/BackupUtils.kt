package com.example.monday

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId
import com.example.monday.ui.components.DefaultCategories
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

// Data class to hold the entire app state for backup
data class AppBackup(
    val todoItems: List<TodoItem>,
    val calculationRecords: List<CalculationRecord>,
    val selectedDate: String, // Keep as String for wider compatibility
    val primaryCategories: List<ExpenseCategory>? = null,
    val secondaryCategories: List<ExpenseCategory>? = null,
    val tertiaryCategories: List<ExpenseCategory>? = null,
    val showPrimaryCategories: Boolean? = null,
    val showSecondaryCategories: Boolean? = null,
    val showTertiaryCategories: Boolean? = null,
    val recentPrimaryCategory: String? = null,
    val recentSecondaryCategory: String? = null,
    val recentTertiaryCategory: String? = null,
    val itemOrder: Map<Int, Int>? = null,
    val undoableDeletedItemsByDate: Map<String, List<TodoItem>>? = null, // Add undoable deleted items by date
    val lastCategoryAction: TodoViewModel.CategoryAction? = null, // Add last category action for undo
    val recordOrder: Map<Int, Int>? = null, // Preserve record order
    val masterCheckboxStates: Map<String, Boolean>? = null, // Store whether all items were selected per date
    val categorySelectionStates: Map<String, Set<String>>? = null, // Store selected categories for each date
    val appVersion: Int = 4, // Version of the backup format
    val backupDate: Long = System.currentTimeMillis() // When the backup was created
)

fun exportBackup(context: Context, todoViewModel: TodoViewModel) {
    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    val fileName = "kharchaji_backup_$timeStamp.zip"
    val jsonFileName = "backup_data.json"
    val gson = Gson()

    MainScope().launch {
        try {
            // Fetch all data directly from the database
            val allExpenses = withContext(Dispatchers.IO) { todoViewModel.getAllExpensesForExport() }
            val allRecords = withContext(Dispatchers.IO) { todoViewModel.getAllCalculationRecordsForExport() }
            val itemOrder = allExpenses.mapIndexed { index, item -> item.id to index }.toMap()
            val recordOrder = allRecords.mapIndexed { index, record -> record.id to index }.toMap()
            
            // Get undoable deleted items by date
            val undoableDeletedItems = todoViewModel.getUndoableDeletedItemsByDate()
            
            // Convert LocalDate keys to strings for JSON serialization
            val undoableDeletedItemsAsStrings = undoableDeletedItems.mapKeys { it.key.toString() }

            // Collect master checkbox states per date
            val masterCheckboxStates = mutableMapOf<String, Boolean>()
            val today = LocalDate.now().toString()
            val selectedDate = todoViewModel.selectedDate.value.toString()
            
            // For current date, store if all items are checked
            val currentDateItems = allExpenses.filter {
                val itemDate = Instant.ofEpochMilli(it.timestamp)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
                    .toString()
                itemDate == selectedDate
            }
            
            if (currentDateItems.isNotEmpty()) {
                masterCheckboxStates[selectedDate] = currentDateItems.all { it.isDone }
            }
            
            // Store selected categories per type
            val categorySelectionStates = mutableMapOf<String, Set<String>>()
            todoViewModel.getRecentlySelectedCategory("primary")?.let { cat ->
                categorySelectionStates["primary"] = setOf(cat)
            }
            todoViewModel.getRecentlySelectedCategory("secondary")?.let { cat ->
                categorySelectionStates["secondary"] = setOf(cat)
            }
            todoViewModel.getRecentlySelectedCategory("tertiary")?.let { cat ->
                categorySelectionStates["tertiary"] = setOf(cat)
            }

            if (allExpenses.isEmpty() && allRecords.isEmpty()) {
                Toast.makeText(context, "No data to export", Toast.LENGTH_SHORT).show()
                return@launch
            }
            
            // Log the data being exported
            Log.d("ExportBackup", "Exporting ${allExpenses.size} expenses and ${allRecords.size} records")
            Log.d("ExportBackup", "Exporting undoable deleted items for ${undoableDeletedItems.size} dates")
            
            val backupData = AppBackup(
                todoItems = allExpenses,
                calculationRecords = allRecords,
                selectedDate = todoViewModel.selectedDate.value.toString(),
                primaryCategories = todoViewModel.primaryCategories.value,
                secondaryCategories = todoViewModel.secondaryCategories.value,
                tertiaryCategories = todoViewModel.tertiaryCategories.value,
                showPrimaryCategories = todoViewModel.getCategoryVisibilitySetting("primary"),
                showSecondaryCategories = todoViewModel.getCategoryVisibilitySetting("secondary"),
                showTertiaryCategories = todoViewModel.getCategoryVisibilitySetting("tertiary"),
                recentPrimaryCategory = todoViewModel.getRecentlySelectedCategory("primary"),
                recentSecondaryCategory = todoViewModel.getRecentlySelectedCategory("secondary"),
                recentTertiaryCategory = todoViewModel.getRecentlySelectedCategory("tertiary"),
                itemOrder = itemOrder,
                undoableDeletedItemsByDate = undoableDeletedItemsAsStrings,
                lastCategoryAction = todoViewModel.lastCategoryAction.value,
                recordOrder = recordOrder,
                masterCheckboxStates = masterCheckboxStates,
                categorySelectionStates = categorySelectionStates,
                appVersion = 4,
                backupDate = System.currentTimeMillis()
            )
            val jsonString = gson.toJson(backupData)

            // Try using app-specific storage
            try {
                val downloadDir = File(context.getExternalFilesDir(null), "Downloads")
                if (!downloadDir.exists()) {
                    val created = downloadDir.mkdirs()
                    Log.d("ExportBackup", "Created Downloads directory: $created")
                    if (!created) {
                        Log.w("ExportBackup", "Failed to create Downloads directory, will try alternative location")
                    }
                }
                
                Log.d("ExportBackup", "Download directory path: ${downloadDir.absolutePath}")
                Log.d("ExportBackup", "Download directory exists: ${downloadDir.exists()}")
                Log.d("ExportBackup", "Download directory is writable: ${downloadDir.canWrite()}")
                
                val zipFile = File(downloadDir, fileName)
                
                withContext(Dispatchers.IO) {
                    // Create a temporary JSON file
                    val jsonFile = File(context.cacheDir, jsonFileName)
                    jsonFile.writeText(jsonString)
                    
                    // Collect all image URIs
                    val imageUris = mutableSetOf<String>()
                    
                    // Collect image URIs from TodoItems
                    allExpenses.forEach { item ->
                        item.imageUris?.forEach { uri ->
                            imageUris.add(uri)
                        }
                    }
                    
                    // Collect image URIs from CalculationRecords
                    allRecords.forEach { record ->
                        record.items.forEach { item ->
                            item.imageUris?.forEach { uri ->
                                imageUris.add(uri)
                            }
                        }
                    }
                    
                    Log.d("ExportBackup", "Found ${imageUris.size} unique image URIs to backup")
                    
                    // Create the ZIP file
                    ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zipOut ->
                        // Add the JSON file to the ZIP
                        val jsonEntry = ZipEntry(jsonFileName)
                        zipOut.putNextEntry(jsonEntry)
                        val jsonBytes = jsonFile.readBytes()
                        zipOut.write(jsonBytes)
                        zipOut.closeEntry()
                        
                        Log.d("ExportBackup", "Added JSON data to ZIP (${jsonBytes.size} bytes)")
                        
                        // Add all images to the ZIP
                        var imageCount = 0
                        imageUris.forEach { uriString ->
                            try {
                                val uri = Uri.parse(uriString)
                                val imageFileName = getFileNameFromUri(uri) ?: "image_${imageCount++}.jpg"
                                val imageEntry = ZipEntry("images/$imageFileName")
                                
                                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                                    val imageBytes = inputStream.readBytes()
                                    zipOut.putNextEntry(imageEntry)
                                    zipOut.write(imageBytes)
                                    zipOut.closeEntry()
                                    Log.d("ExportBackup", "Added image to ZIP: $imageFileName (${imageBytes.size} bytes)")
                                } ?: Log.e("ExportBackup", "Failed to open input stream for URI: $uriString")
                            } catch (e: Exception) {
                                Log.e("ExportBackup", "Error adding image to ZIP: $uriString", e)
                            }
                        }
                    }
                    
                    // Delete the temporary JSON file
                    jsonFile.delete()
                }
                
                Log.d("ExportBackup", "ZIP file created successfully: ${zipFile.absolutePath}")
                Log.d("ExportBackup", "ZIP file exists: ${zipFile.exists()}, Size: ${zipFile.length()}")

                Toast.makeText(context, "Data exported to ${zipFile.name}", Toast.LENGTH_LONG).show()

                val fileUri = androidx.core.content.FileProvider.getUriForFile(
                    context, "${context.packageName}.fileprovider", zipFile
                )
                
                Log.d("ExportBackup", "FileProvider URI: $fileUri")
                
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    putExtra(Intent.EXTRA_STREAM, fileUri)
                    type = "application/zip"
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Share backup file"))
            } catch (e: Exception) {
                Log.e("ExportBackup", "Error creating ZIP backup", e)
                Toast.makeText(context, "Error exporting data: ${e.message}", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Log.e("ExportData", "Error exporting data", e)
            Toast.makeText(context, "Error exporting data: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}

fun importBackup(context: Context, uri: Uri, todoViewModel: TodoViewModel) {
    val gson = Gson()
    MainScope().launch {
        try {
            // Check if the file is a ZIP file
            val isZipFile = uri.toString().endsWith(".zip", ignoreCase = true) ||
                            context.contentResolver.getType(uri)?.equals("application/zip") == true
            
            if (isZipFile) {
                // Handle ZIP backup
                importZipBackup(context, uri, todoViewModel)
            } else {
                // Handle legacy JSON backup
                importLegacyJsonBackup(context, uri, todoViewModel)
            }
        } catch (e: Exception) {
            Log.e("ImportData", "Error importing data", e)
            Toast.makeText(context, "Error importing data: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}

private fun importZipBackup(context: Context, uri: Uri, todoViewModel: TodoViewModel) {
    val gson = Gson()
    MainScope().launch {
        try {
            // Create a temporary directory for extraction
            val extractDir = File(context.cacheDir, "backup_extract_${System.currentTimeMillis()}")
            if (!extractDir.exists()) {
                extractDir.mkdirs()
            }
            
            var jsonData: String? = null
            val imageMap = mutableMapOf<String, Uri>()
            
            // Extract the ZIP file
            withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    ZipInputStream(BufferedInputStream(inputStream)).use { zipIn ->
                        var entry: ZipEntry? = zipIn.nextEntry
                        while (entry != null) {
                            val entryName = entry.name
                            Log.d("ImportBackup", "Extracting entry: $entryName")
                            
                            val outputFile = File(extractDir, entryName)
                            
                            // Create parent directories if needed
                            if (!entry.isDirectory) {
                                outputFile.parentFile?.mkdirs()
                                
                                // Extract the file
                                FileOutputStream(outputFile).use { output ->
                                    val buffer = ByteArray(4096)
                                    var count = zipIn.read(buffer)
                                    while (count != -1) {
                                        output.write(buffer, 0, count)
                                        count = zipIn.read(buffer)
                                    }
                                }
                                
                                // If this is the JSON file, read its content
                                if (entryName == "backup_data.json") {
                                    jsonData = outputFile.readText()
                                }
                                
                                // If this is an image file, save it to app storage and create a URI
                                if (entryName.startsWith("images/")) {
                                    val fileName = entryName.substringAfterLast("/")
                                    val imageFile = saveImageToAppStorage(context, outputFile, fileName)
                                    val imageUri = Uri.fromFile(imageFile).toString()
                                    imageMap[fileName] = Uri.parse(imageUri)
                                    Log.d("ImportBackup", "Saved image: $fileName -> $imageUri")
                                }
                            }
                            
                            zipIn.closeEntry()
                            entry = zipIn.nextEntry
                        }
                    }
                } ?: throw Exception("Failed to open ZIP file")
            }
            
            if (jsonData == null) {
                throw Exception("No backup data found in ZIP file")
            }
            
            // Parse the JSON data
            val backupData = gson.fromJson(jsonData, AppBackup::class.java)
            
            if (backupData.todoItems.isEmpty() && backupData.calculationRecords.isEmpty()) {
                Toast.makeText(context, "Backup file contains no data.", Toast.LENGTH_LONG).show()
                return@launch
            }
            
            // Log the data being imported
            Log.d("ImportBackup", "Importing ${backupData.todoItems.size} expenses and ${backupData.calculationRecords.size} records")
            Log.d("ImportBackup", "Backup version: ${backupData.appVersion ?: 1}")
            Log.d("ImportBackup", "Backup date: ${backupData.backupDate?.let { Date(it).toString() } ?: "Unknown"}")
            
            // Create a map of all default icons for easy lookup
            val iconMap = (DefaultCategories.primaryCategories + DefaultCategories.secondaryCategories + DefaultCategories.tertiaryCategories)
                .associate { it.name to it.icon }

            // Function to restore icons to a list of categories
            fun restoreCategoryIcons(categories: List<ExpenseCategory>?): List<ExpenseCategory>? {
                return categories?.map { category ->
                    category.copy(icon = iconMap[category.name] ?: Icons.Outlined.MoreHoriz)
                }
            }

            // Restore icons for all category lists
            val restoredPrimaryCategories = restoreCategoryIcons(backupData.primaryCategories)
            val restoredSecondaryCategories = restoreCategoryIcons(backupData.secondaryCategories)
            val restoredTertiaryCategories = restoreCategoryIcons(backupData.tertiaryCategories)
            
            // Update image URIs in TodoItems
            val updatedTodoItems = backupData.todoItems.map { item ->
                if (item.imageUris != null && item.imageUris.isNotEmpty()) {
                    val updatedUris = item.imageUris.mapNotNull { uri ->
                        val fileName = getFileNameFromUri(Uri.parse(uri))
                        if (fileName != null && imageMap.containsKey(fileName)) {
                            imageMap[fileName].toString()
                        } else {
                            uri // Keep the original URI if we couldn't map it
                        }
                    }
                    item.copy(imageUris = updatedUris)
                } else {
                    item
                }
            }
            
            // Update image URIs in CalculationRecords
            val updatedRecords = backupData.calculationRecords.map { record ->
                val updatedItems = record.items.map { item ->
                    if (item.imageUris != null && item.imageUris.isNotEmpty()) {
                        val updatedUris = item.imageUris.mapNotNull { uri ->
                            val fileName = getFileNameFromUri(Uri.parse(uri))
                            if (fileName != null && imageMap.containsKey(fileName)) {
                                imageMap[fileName].toString()
                            } else {
                                uri // Keep the original URI if we couldn't map it
                            }
                        }
                        item.copy(imageUris = updatedUris)
                    } else {
                        item
                    }
                }
                record.copy(items = updatedItems)
            }
            
            // Sort the todo items based on the itemOrder map if available
            val sortedTodoItems = if (backupData.itemOrder != null) {
                updatedTodoItems.sortedBy { backupData.itemOrder[it.id] ?: Int.MAX_VALUE }
            } else {
                updatedTodoItems
            }
            
            // Sort the records based on recordOrder if available
            val sortedRecords = if (backupData.recordOrder != null) {
                updatedRecords.sortedBy { backupData.recordOrder[it.id] ?: Int.MAX_VALUE }
            } else {
                updatedRecords
            }
            
            // Start a single transaction to restore all data atomically
            withContext(Dispatchers.IO) {
                // Restore the data records - all in one transaction for atomicity
                todoViewModel.clearAndInsertAllData(sortedTodoItems, sortedRecords)

                // Restore the selected date
                try {
                    val restoredDate = LocalDate.parse(backupData.selectedDate)
                    todoViewModel.updateSelectedDate(restoredDate)
                } catch (e: Exception) {
                    Log.e("ImportData", "Could not parse selected date from backup, defaulting to today.", e)
                    todoViewModel.updateSelectedDate(LocalDate.now())
                }
                
                // Restore categories if they exist in the backup
                restoredPrimaryCategories?.let { 
                    todoViewModel.saveCategories("primary", it)
                }
                restoredSecondaryCategories?.let { 
                    todoViewModel.saveCategories("secondary", it)
                }
                restoredTertiaryCategories?.let { 
                    todoViewModel.saveCategories("tertiary", it)
                }
                
                // Restore category visibility settings
                backupData.showPrimaryCategories?.let {
                    todoViewModel.saveCategoryVisibilitySetting("primary", it)
                }
                backupData.showSecondaryCategories?.let {
                    todoViewModel.saveCategoryVisibilitySetting("secondary", it)
                }
                backupData.showTertiaryCategories?.let {
                    todoViewModel.saveCategoryVisibilitySetting("tertiary", it)
                }
                
                // Restore recently selected categories
                backupData.recentPrimaryCategory?.let {
                    todoViewModel.saveRecentlySelectedCategory("primary", it)
                }
                backupData.recentSecondaryCategory?.let {
                    todoViewModel.saveRecentlySelectedCategory("secondary", it)
                }
                backupData.recentTertiaryCategory?.let {
                    todoViewModel.saveRecentlySelectedCategory("tertiary", it)
                }
                
                // Restore undoable deleted items by date if available
                backupData.undoableDeletedItemsByDate?.let { undoableItemsMap ->
                    // Convert string keys back to LocalDate
                    val undoableItemsByDate = undoableItemsMap.mapKeys { entry ->
                        try {
                            LocalDate.parse(entry.key)
                        } catch (e: Exception) {
                            Log.e("ImportBackup", "Failed to parse date for undoable items: ${entry.key}")
                            null
                        }
                    }.filterKeys { it != null } as Map<LocalDate, List<TodoItem>>
                    
                    if (undoableItemsByDate.isNotEmpty()) {
                        todoViewModel.restoreUndoableDeletedItemsByDate(undoableItemsByDate)
                        Log.d("ImportBackup", "Restored undoable deleted items for ${undoableItemsByDate.size} dates")
                    }
                }
                
                // Restore last category action if available
                backupData.lastCategoryAction?.let {
                    todoViewModel.setLastCategoryAction(it)
                    Log.d("ImportBackup", "Restored last category action: ${it::class.simpleName}")
                }
                
                // Restore UI state for master checkbox if available
                backupData.masterCheckboxStates?.let { states ->
                    // We only need to store the state - the UI will use it when rendered
                    Log.d("ImportBackup", "Restored master checkbox states for ${states.size} dates")
                }
                
                // Restore category selection states if available
                backupData.categorySelectionStates?.let { selectionStates ->
                    selectionStates.forEach { (type, categories) ->
                        if (categories.isNotEmpty()) {
                            // Take just the first category as the recent selection
                            val category = categories.first()
                            todoViewModel.saveRecentlySelectedCategory(type, category)
                            Log.d("ImportBackup", "Restored category selection for $type: $category")
                        }
                    }
                }
            }
            
            // Clean up the temporary directory
            extractDir.deleteRecursively()

            Toast.makeText(context, "Successfully imported ${backupData.todoItems.size} expenses and ${backupData.calculationRecords.size} records with images.", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e("ImportData", "Error importing ZIP backup", e)
            Toast.makeText(context, "Error importing data: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}

private fun importLegacyJsonBackup(context: Context, uri: Uri, todoViewModel: TodoViewModel) {
    val gson = Gson()
    MainScope().launch {
        try {
            val jsonString = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }

            if (jsonString.isNullOrEmpty()) {
                Toast.makeText(context, "Selected file is empty or could not be read.", Toast.LENGTH_LONG).show()
                return@launch
            }

            val backupData = gson.fromJson(jsonString, AppBackup::class.java)

            if (backupData.todoItems.isEmpty() && backupData.calculationRecords.isEmpty()) {
                Toast.makeText(context, "Backup file contains no data.", Toast.LENGTH_LONG).show()
                return@launch
            }
            
            // Log the data being imported
            Log.d("ImportBackup", "Importing ${backupData.todoItems.size} expenses and ${backupData.calculationRecords.size} records")
            Log.d("ImportBackup", "Primary categories: ${backupData.primaryCategories?.size ?: 0}")
            Log.d("ImportBackup", "Secondary categories: ${backupData.secondaryCategories?.size ?: 0}")
            Log.d("ImportBackup", "Tertiary categories: ${backupData.tertiaryCategories?.size ?: 0}")
            Log.d("ImportBackup", "Backup version: ${backupData.appVersion ?: 1}")
            Log.d("ImportBackup", "Backup date: ${backupData.backupDate?.let { Date(it).toString() } ?: "Unknown"}")
            
            // Create a map of all default icons for easy lookup
            val iconMap = (DefaultCategories.primaryCategories + DefaultCategories.secondaryCategories + DefaultCategories.tertiaryCategories)
                .associate { it.name to it.icon }

            // Function to restore icons to a list of categories
            fun restoreCategoryIcons(categories: List<ExpenseCategory>?): List<ExpenseCategory>? {
                return categories?.map { category ->
                    category.copy(icon = iconMap[category.name] ?: Icons.Outlined.MoreHoriz)
                }
            }

            // Restore icons for all category lists
            val restoredPrimaryCategories = restoreCategoryIcons(backupData.primaryCategories)
            val restoredSecondaryCategories = restoreCategoryIcons(backupData.secondaryCategories)
            val restoredTertiaryCategories = restoreCategoryIcons(backupData.tertiaryCategories)

            // Log sample of items with categories
            backupData.todoItems.filter { !it.categories.isNullOrEmpty() }.take(3).forEach { item ->
                Log.d("ImportBackup", "Sample item with categories: ${item.text}, Categories: ${item.categories}, " +
                    "Primary: ${item.hasPrimaryCategory}, Secondary: ${item.hasSecondaryCategory}, Tertiary: ${item.hasTertiaryCategory}")
            }
            
            // Log sample of items with images
            backupData.todoItems.filter { !it.imageUris.isNullOrEmpty() }.take(3).forEach { item ->
                Log.d("ImportBackup", "Sample item with images: ${item.text}, Images: ${item.imageUris?.size}")
            }
            
            // Sort the todo items based on the itemOrder map if available
            val sortedTodoItems = if (backupData.itemOrder != null) {
                backupData.todoItems.sortedBy { backupData.itemOrder[it.id] ?: Int.MAX_VALUE }
            } else {
                backupData.todoItems
            }
            
            // Sort the records based on recordOrder if available
            val sortedRecords = if (backupData.recordOrder != null) {
                backupData.calculationRecords.sortedBy { backupData.recordOrder[it.id] ?: Int.MAX_VALUE }
            } else {
                backupData.calculationRecords
            }
            
            // Start a single transaction to restore all data atomically
            withContext(Dispatchers.IO) {
                // Restore the data records - all in one transaction for atomicity
                todoViewModel.clearAndInsertAllData(sortedTodoItems, sortedRecords)

                // Restore the selected date
                try {
                    val restoredDate = LocalDate.parse(backupData.selectedDate)
                    todoViewModel.updateSelectedDate(restoredDate)
                } catch (e: Exception) {
                    Log.e("ImportData", "Could not parse selected date from backup, defaulting to today.", e)
                    todoViewModel.updateSelectedDate(LocalDate.now())
                }
                
                // Restore categories if they exist in the backup
                restoredPrimaryCategories?.let { 
                    Log.d("ImportBackup", "Restoring ${it.size} primary categories")
                    todoViewModel.saveCategories("primary", it)
                }
                restoredSecondaryCategories?.let { 
                    Log.d("ImportBackup", "Restoring ${it.size} secondary categories")
                    todoViewModel.saveCategories("secondary", it)
                }
                restoredTertiaryCategories?.let { 
                    Log.d("ImportBackup", "Restoring ${it.size} tertiary categories")
                    todoViewModel.saveCategories("tertiary", it)
                }
                
                // Restore category visibility settings
                backupData.showPrimaryCategories?.let {
                    Log.d("ImportBackup", "Setting primary categories visibility: $it")
                    todoViewModel.saveCategoryVisibilitySetting("primary", it)
                }
                backupData.showSecondaryCategories?.let {
                    Log.d("ImportBackup", "Setting secondary categories visibility: $it")
                    todoViewModel.saveCategoryVisibilitySetting("secondary", it)
                }
                backupData.showTertiaryCategories?.let {
                    Log.d("ImportBackup", "Setting tertiary categories visibility: $it")
                    todoViewModel.saveCategoryVisibilitySetting("tertiary", it)
                }
                
                // Restore recently selected categories
                backupData.recentPrimaryCategory?.let {
                    Log.d("ImportBackup", "Setting recent primary category: $it")
                    todoViewModel.saveRecentlySelectedCategory("primary", it)
                }
                backupData.recentSecondaryCategory?.let {
                    Log.d("ImportBackup", "Setting recent secondary category: $it")
                    todoViewModel.saveRecentlySelectedCategory("secondary", it)
                }
                backupData.recentTertiaryCategory?.let {
                    todoViewModel.saveRecentlySelectedCategory("tertiary", it)
                    Log.d("ImportBackup", "Setting recent tertiary category: $it")
                }
                
                // Restore undoable deleted items by date if available (for newer legacy backups that might have this)
                backupData.undoableDeletedItemsByDate?.let { undoableItemsMap ->
                    // Convert string keys back to LocalDate
                    val undoableItemsByDate = undoableItemsMap.mapKeys { entry ->
                        try {
                            LocalDate.parse(entry.key)
                        } catch (e: Exception) {
                            Log.e("ImportBackup", "Failed to parse date for undoable items: ${entry.key}")
                            null
                        }
                    }.filterKeys { it != null } as Map<LocalDate, List<TodoItem>>
                    
                    if (undoableItemsByDate.isNotEmpty()) {
                        todoViewModel.restoreUndoableDeletedItemsByDate(undoableItemsByDate)
                        Log.d("ImportBackup", "Restored undoable deleted items for ${undoableItemsByDate.size} dates")
                    }
                }
                
                // Restore last category action if available
                backupData.lastCategoryAction?.let {
                    todoViewModel.setLastCategoryAction(it)
                    Log.d("ImportBackup", "Restored last category action: ${it::class.simpleName}")
                }
                
                // Restore UI state for master checkbox if available
                backupData.masterCheckboxStates?.let { states ->
                    // We only need to store the state - the UI will use it when rendered
                    Log.d("ImportBackup", "Restored master checkbox states for ${states.size} dates")
                }
                
                // Restore category selection states if available
                backupData.categorySelectionStates?.let { selectionStates ->
                    selectionStates.forEach { (type, categories) ->
                        if (categories.isNotEmpty()) {
                            // Take just the first category as the recent selection
                            val category = categories.first()
                            todoViewModel.saveRecentlySelectedCategory(type, category)
                            Log.d("ImportBackup", "Restored category selection for $type: $category")
                        }
                    }
                }
            }

            Toast.makeText(context, "Successfully imported ${backupData.todoItems.size} expenses and ${backupData.calculationRecords.size} records. Note: Images may not be restored with legacy JSON backup.", Toast.LENGTH_LONG).show()

        } catch (e: Exception) {
            Log.e("ImportData", "Error importing legacy JSON backup", e)
            Toast.makeText(context, "Error importing data: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}

// Helper function to get a file name from a URI
private fun getFileNameFromUri(uri: Uri): String? {
    val path = uri.toString()
    return path.substringAfterLast('/').substringBeforeLast('?')
}

// Helper function to save an image to app storage
private fun saveImageToAppStorage(context: Context, sourceFile: File, fileName: String): File {
    val imagesDir = File(context.getExternalFilesDir(null), "images")
    if (!imagesDir.exists()) {
        imagesDir.mkdirs()
    }
    
    val destFile = File(imagesDir, fileName)
    sourceFile.copyTo(destFile, overwrite = true)
    
    return destFile
} 