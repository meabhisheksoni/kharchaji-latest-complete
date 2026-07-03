package com.example.monday

import android.app.Application
import android.util.Log
import androidx.work.Configuration
import com.example.monday.workers.BackupManager

import dagger.hilt.android.HiltAndroidApp

/**
 * Application class to initialize app-wide functionality
 */
@HiltAndroidApp
class KharchajiApplication : Application(), Configuration.Provider {

    companion object {
        private const val TAG = "KharchajiApp"
    }
    
    override fun onCreate() {
        super.onCreate()
        
        // LeakCanary 2.x auto-installs itself, no manual initialization needed
        
        // Anti-Fragile Hack: Ignore framework-level Compose AccessibilityManager false positives.
        // We use reflection so it only executes when DebugLeakCleaner (in src/debug) is actually present.
        try {
            val debugCleanerClass = Class.forName("com.example.monday.DebugLeakCleaner")
            val instance = debugCleanerClass.getDeclaredConstructor().newInstance()
            val method = debugCleanerClass.getMethod("applyFix")
            method.invoke(instance)
        } catch (e: Exception) {
            // Ignored in release build where this class doesn't exist
        }
        
        // Schedule automatic backups
        BackupManager.scheduleAutoBackups(
            context = this,
            intervalDays = 7, // Weekly backups
            requiresCharging = true,
            requiresNetwork = false
        )
    }
    
    /**
     * Configuration for WorkManager to enable logging in debug builds
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(Log.INFO)
            .build()
}
