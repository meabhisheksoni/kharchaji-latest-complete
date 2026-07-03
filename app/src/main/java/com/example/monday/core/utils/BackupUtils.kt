package com.example.monday.core.utils

import com.example.monday.data.models.TodoItem
import com.example.monday.data.models.CalculationRecord
import com.example.monday.TodoViewModel
import com.example.monday.viewmodels.MainViewModel
import com.example.monday.ExpenseCategory
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

data class AppBackup(
    val todoItems: List<TodoItem>,
    val calculationRecords: List<CalculationRecord>,
    val selectedDate: String,
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
    val undoableDeletedItemsByDate: Map<String, List<TodoItem>>? = null,
    val lastCategoryAction: com.example.monday.managers.CategoryManager.CategoryAction? = null,
    val recordOrder: Map<Int, Int>? = null,
    val masterCheckboxStates: Map<String, Boolean>? = null,
    val categorySelectionStates: Map<String, Set<String>>? = null,
    val appVersion: Int = 4,
    val backupDate: Long = System.currentTimeMillis()
)

fun exportBackup(context: Context, todoViewModel: TodoViewModel, mainViewModel: MainViewModel, statsViewModel: com.example.monday.viewmodels.StatsViewModel) {
    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    val fileName = "kharchaji_backup_$timeStamp.zip"
    val jsonFileName = "backup_data.json"
    val gson = Gson()
    MainScope().launch {
        try {
            val allExpenses = withContext(Dispatchers.IO) { mainViewModel.getAllExpensesForExport() }
            val allRecords = withContext(Dispatchers.IO) { statsViewModel.getAllCalculationRecordsForExport() }
            val itemOrder = allExpenses.mapIndexed { index, item -> item.id to index }.toMap()
            val recordOrder = allRecords.mapIndexed { index, record -> record.id to index }.toMap()
            val undoableDeletedItems = mainViewModel.getUndoableDeletedItemsByDate()
            val undoableDeletedItemsAsStrings = undoableDeletedItems.mapKeys { it.key.toString() }
            val masterCheckboxStates = mutableMapOf<String, Boolean>()
            val selectedDate = mainViewModel.selectedDate.value.toString()
            val currentDateItems = allExpenses.filter {
                Instant.ofEpochMilli(it.timestamp).atZone(ZoneId.systemDefault()).toLocalDate().toString() == selectedDate
            }
            if (currentDateItems.isNotEmpty()) masterCheckboxStates[selectedDate] = currentDateItems.all { it.isDone }
            val categorySelectionStates = mutableMapOf<String, Set<String>>()
            todoViewModel.getRecentlySelectedCategory("primary")?.let { cat -> categorySelectionStates["primary"] = setOf(cat) }
            todoViewModel.getRecentlySelectedCategory("secondary")?.let { cat -> categorySelectionStates["secondary"] = setOf(cat) }
            todoViewModel.getRecentlySelectedCategory("tertiary")?.let { cat -> categorySelectionStates["tertiary"] = setOf(cat) }
            if (allExpenses.isEmpty() && allRecords.isEmpty()) {
                Toast.makeText(context, "No data to export", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val backupData = AppBackup(
                todoItems = allExpenses,
                calculationRecords = allRecords,
                selectedDate = selectedDate,
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
                categorySelectionStates = categorySelectionStates
            )
            val jsonString = gson.toJson(backupData)
            try {
                val downloadDir = File(context.getExternalFilesDir(null), "Downloads")
                if (!downloadDir.exists()) downloadDir.mkdirs()
                val zipFile = File(downloadDir, fileName)
                withContext(Dispatchers.IO) {
                    val jsonFile = File(context.cacheDir, jsonFileName)
                    jsonFile.writeText(jsonString)
                    val imageUris = mutableSetOf<String>()
                    allExpenses.forEach { it.imageUris?.forEach { uri -> imageUris.add(uri) } }
                    allRecords.forEach { it.items.forEach { item -> item.imageUris?.forEach { uri -> imageUris.add(uri) } } }
                    ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zipOut ->
                        val jsonEntry = ZipEntry(jsonFileName)
                        zipOut.putNextEntry(jsonEntry)
                        zipOut.write(jsonFile.readBytes())
                        zipOut.closeEntry()
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
                                }
                            } catch (e: Exception) {}
                        }
                    }
                    jsonFile.delete()
                }
                Toast.makeText(context, "Data exported to ${zipFile.name}", Toast.LENGTH_LONG).show()
                val fileUri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", zipFile)
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    putExtra(Intent.EXTRA_STREAM, fileUri)
                    type = "application/zip"
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Share backup file"))
            } catch (e: Exception) {
                Toast.makeText(context, "Error exporting data: ${e.message}", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Error exporting data: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}

fun importBackup(context: Context, uri: Uri, todoViewModel: TodoViewModel, mainViewModel: MainViewModel, statsViewModel: com.example.monday.viewmodels.StatsViewModel) {
    val gson = Gson()
    MainScope().launch {
        try {
            val isZipFile = uri.toString().endsWith(".zip", ignoreCase = true) || context.contentResolver.getType(uri)?.equals("application/zip") == true
            if (isZipFile) importZipBackup(context, uri, todoViewModel, mainViewModel, statsViewModel) else importLegacyJsonBackup(context, uri, todoViewModel, mainViewModel, statsViewModel)
        } catch (e: Exception) {
            Toast.makeText(context, "Error importing data: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}

private fun importZipBackup(context: Context, uri: Uri, todoViewModel: TodoViewModel, mainViewModel: MainViewModel, statsViewModel: com.example.monday.viewmodels.StatsViewModel) {
    val gson = Gson()
    MainScope().launch {
        try {
            val extractDir = File(context.cacheDir, "backup_extract_${System.currentTimeMillis()}")
            if (!extractDir.exists()) extractDir.mkdirs()
            var jsonData: String? = null
            val imageMap = mutableMapOf<String, Uri>()
            withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    ZipInputStream(BufferedInputStream(inputStream)).use { zipIn ->
                        var entry: ZipEntry? = zipIn.nextEntry
                        while (entry != null) {
                            val outputFile = File(extractDir, entry.name)
                            if (!entry.isDirectory) {
                                outputFile.parentFile?.mkdirs()
                                FileOutputStream(outputFile).use { output ->
                                    zipIn.copyTo(output)
                                }
                                if (entry.name == "backup_data.json") jsonData = outputFile.readText()
                                if (entry.name.startsWith("images/")) {
                                    val fileName = entry.name.substringAfterLast("/")
                                    val imageFile = saveImageToAppStorage(context, outputFile, fileName)
                                    imageMap[fileName] = Uri.fromFile(imageFile)
                                }
                            }
                            zipIn.closeEntry()
                            entry = zipIn.nextEntry
                        }
                    }
                }
            }
            if (jsonData == null) throw Exception("No backup data found in ZIP file")
            val backupData = gson.fromJson(jsonData, AppBackup::class.java)
            val iconMap = (DefaultCategories.primaryCategories + DefaultCategories.secondaryCategories + DefaultCategories.tertiaryCategories).associate { it.name to it.icon }
            fun restoreCategoryIcons(categories: List<ExpenseCategory>?): List<ExpenseCategory>? = categories?.map { it.copy(icon = iconMap[it.name] ?: Icons.Outlined.MoreHoriz) }
            val updatedTodoItems = backupData.todoItems.map { item ->
                item.copy(imageUris = item.imageUris?.mapNotNull { uri ->
                    val fileName = getFileNameFromUri(Uri.parse(uri))
                    if (fileName != null && imageMap.containsKey(fileName)) imageMap[fileName].toString() else uri
                })
            }
            val updatedRecords = backupData.calculationRecords.map { record ->
                record.copy(items = record.items.map { item ->
                    item.copy(imageUris = item.imageUris?.mapNotNull { uri ->
                        val fileName = getFileNameFromUri(Uri.parse(uri))
                        if (fileName != null && imageMap.containsKey(fileName)) imageMap[fileName].toString() else uri
                    })
                })
            }
            withContext(Dispatchers.IO) {
                mainViewModel.mergeAllData(updatedTodoItems, updatedRecords)
                try { mainViewModel.setDate(LocalDate.parse(backupData.selectedDate)) } catch (e: Exception) {}
                restoreCategoryIcons(backupData.primaryCategories)?.let { todoViewModel.saveCategories("primary", it) }
                restoreCategoryIcons(backupData.secondaryCategories)?.let { todoViewModel.saveCategories("secondary", it) }
                restoreCategoryIcons(backupData.tertiaryCategories)?.let { todoViewModel.saveCategories("tertiary", it) }
                backupData.showPrimaryCategories?.let { todoViewModel.saveCategoryVisibilitySetting("primary", it) }
                backupData.showSecondaryCategories?.let { todoViewModel.saveCategoryVisibilitySetting("secondary", it) }
                backupData.showTertiaryCategories?.let { todoViewModel.saveCategoryVisibilitySetting("tertiary", it) }
                backupData.recentPrimaryCategory?.let { todoViewModel.saveRecentlySelectedCategory("primary", it) }
                backupData.recentSecondaryCategory?.let { todoViewModel.saveRecentlySelectedCategory("secondary", it) }
                backupData.recentTertiaryCategory?.let { todoViewModel.saveRecentlySelectedCategory("tertiary", it) }
            }
            extractDir.deleteRecursively()
            Toast.makeText(context, "Successfully imported data with images.", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Error importing data: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}

private fun importLegacyJsonBackup(context: Context, uri: Uri, todoViewModel: TodoViewModel, mainViewModel: MainViewModel, statsViewModel: com.example.monday.viewmodels.StatsViewModel) {
    val gson = Gson()
    MainScope().launch {
        try {
            val jsonString = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }
            if (jsonString.isNullOrEmpty()) return@launch
            val backupData = gson.fromJson(jsonString, AppBackup::class.java)
            withContext(Dispatchers.IO) {
                mainViewModel.mergeAllData(backupData.todoItems, backupData.calculationRecords)
                try { mainViewModel.setDate(LocalDate.parse(backupData.selectedDate)) } catch (e: Exception) {}
            }
            Toast.makeText(context, "Successfully imported data (Legacy).", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Error importing data: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}

private fun getFileNameFromUri(uri: Uri): String? = uri.toString().substringAfterLast('/').substringBeforeLast('?')
private fun saveImageToAppStorage(context: Context, sourceFile: File, fileName: String): File {
    val imagesDir = File(context.getExternalFilesDir(null), "images")
    if (!imagesDir.exists()) imagesDir.mkdirs()
    val destFile = File(imagesDir, fileName)
    sourceFile.copyTo(destFile, overwrite = true)
    return destFile
}
