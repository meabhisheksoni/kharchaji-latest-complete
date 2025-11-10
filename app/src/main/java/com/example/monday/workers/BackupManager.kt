package com.example.monday.workers

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

/**
 * Manages the scheduling of automatic backups
 */
class BackupManager {
    companion object {
        /**
         * Schedule automatic backups to run periodically
         * 
         * @param context Application context
         * @param intervalDays Number of days between backups (default: 7)
         * @param requiresCharging Whether device should be charging for backup to run
         * @param requiresNetwork Whether network connection is required for backup
         */
        fun scheduleAutoBackups(
            context: Context,
            intervalDays: Int = 7,
            requiresCharging: Boolean = false,
            requiresNetwork: Boolean = false
        ) {
            val constraints = Constraints.Builder().apply {
                if (requiresCharging) {
                    setRequiresCharging(true)
                }
                if (requiresNetwork) {
                    setRequiredNetworkType(NetworkType.CONNECTED)
                }
            }.build()
            
            val backupRequest = PeriodicWorkRequestBuilder<AutoBackupWorker>(
                intervalDays.toLong(), TimeUnit.DAYS
            )
                .setConstraints(constraints)
                .addTag("auto_backup")
                .build()
            
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                AutoBackupWorker.WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,  // Use existing schedule if it exists
                backupRequest
            )
        }
        
        /**
         * Run an immediate backup
         */
        fun runImmediateBackup(context: Context) {
            val backupRequest = OneTimeWorkRequestBuilder<AutoBackupWorker>()
                .addTag("manual_backup")
                .build()
            
            WorkManager.getInstance(context).enqueue(backupRequest)
        }
        
        /**
         * Cancel all scheduled backups
         */
        fun cancelScheduledBackups(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(AutoBackupWorker.WORK_NAME)
        }
    }
} 