package com.example.monday

import android.app.Application
import android.util.Log
import androidx.work.Configuration
import com.example.monday.workers.BackupManager

/**
 * Application class to initialize app-wide functionality
 */
class KharchajiApplication : Application(), Configuration.Provider {

    companion object {
        private const val TAG = "KharchajiApp"
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Application initialized")
        
        // LeakCanary 2.x auto-installs itself, no manual initialization needed
        Log.d(TAG, "LeakCanary auto-initialized for memory leak detection")
        
        // Schedule automatic backups
        BackupManager.scheduleAutoBackups(
            context = this,
            intervalDays = 7, // Weekly backups
            requiresCharging = true,
            requiresNetwork = false
        )
        
        Log.d(TAG, "Automatic backups scheduled")
    }
    
    /**
     * Configuration for WorkManager to enable logging in debug builds
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(Log.INFO)
            .build()
}