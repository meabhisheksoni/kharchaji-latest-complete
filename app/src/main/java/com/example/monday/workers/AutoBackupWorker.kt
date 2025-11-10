package com.example.monday.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.monday.AppDatabase
import com.example.monday.TodoRepository
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import com.example.monday.AppBackup

/**
 * Worker class for performing automatic backups at scheduled intervals
 */
class AutoBackupWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val WORK_NAME = "auto_backup_worker"
        private const val TAG = "AutoBackupWorker"
    }
    
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting scheduled auto-backup")
        
        try {
            // Get repository and database access
            val todoDao = AppDatabase.getDatabase(context).todoDao()
            val repository = TodoRepository(todoDao)
            
            // Get all data
            val allExpenses = repository.getAllItems()
            val allRecords = repository.getAllCalculationRecordsForExport()
            
            if (allExpenses.isEmpty() && allRecords.isEmpty()) {
                Log.d(TAG, "No data to backup, skipping")
                return@withContext Result.success()
            }
            
            // Create backup data object
            val backupData = AppBackup(
                todoItems = allExpenses,
                calculationRecords = allRecords,
                selectedDate = java.time.LocalDate.now().toString(),
                recordOrder = allRecords.mapIndexed { index, record -> record.id to index }.toMap(),
                appVersion = 5,
                backupDate = System.currentTimeMillis()
            )
            
            val jsonString = Gson().toJson(backupData)
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "kharchaji_auto_backup_$timeStamp.zip"
            val jsonFileName = "backup_data.json"
            
            // Create backup directory if it doesn't exist
            val backupDir = File(context.getExternalFilesDir(null), "auto_backups")
            if (!backupDir.exists()) {
                val created = backupDir.mkdirs()
                Log.d(TAG, "Created backup directory: $created")
            }
            
            // Create the ZIP file and add JSON data
            val zipFile = File(backupDir, fileName)
            Log.d(TAG, "Creating backup file: ${zipFile.absolutePath}")
            
            // Create a temporary JSON file
            val jsonFile = File(context.cacheDir, jsonFileName)
            jsonFile.writeText(jsonString)
            
            // Create the ZIP with the JSON file
            ZipOutputStream(FileOutputStream(zipFile)).use { zipOut ->
                // Add the JSON file to the ZIP
                val jsonEntry = ZipEntry(jsonFileName)
                zipOut.putNextEntry(jsonEntry)
                val jsonBytes = jsonFile.readBytes()
                zipOut.write(jsonBytes)
                zipOut.closeEntry()
                
                Log.d(TAG, "Added JSON data to ZIP (${jsonBytes.size} bytes)")
                
                // No images in auto-backup to save space
            }
            
            // Delete the temporary JSON file
            jsonFile.delete()
            
            // Keep only the last 5 backups to save space
            val allBackups = backupDir.listFiles()?.filter { it.extension == "zip" }?.sortedByDescending { it.lastModified() }
            if (allBackups != null && allBackups.size > 5) {
                allBackups.drop(5).forEach { oldBackup ->
                    val deleted = oldBackup.delete()
                    Log.d(TAG, "Deleted old backup ${oldBackup.name}: $deleted")
                }
            }
            
            Log.d(TAG, "Auto-backup completed successfully: ${zipFile.name}")
            Result.success()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during auto-backup", e)
            Result.failure()
        }
    }
} 