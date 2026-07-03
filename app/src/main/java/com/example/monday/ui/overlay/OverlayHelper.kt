package com.example.monday.ui.overlay

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import android.net.Uri
import android.os.Build
import android.provider.Settings

object OverlayHelper {
    
    fun hasOverlayPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }
    
    fun startOverlayService(context: Context) {
        if (hasOverlayPermission(context)) {
            // Use applicationContext to prevent Activity context retention (LeakCanary fix)
            val appContext = context.applicationContext
            val intent = Intent(appContext, OverlayService::class.java)
            ContextCompat.startForegroundService(appContext, intent)
        }
    }
    
    fun stopOverlayService(context: Context) {
        val appContext = context.applicationContext
        val intent = Intent(appContext, OverlayService::class.java)
        appContext.stopService(intent)
    }
    
    fun openOverlaySettings(context: Context) {
        val intent = Intent(context, OverlayPermissionActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    }
}
