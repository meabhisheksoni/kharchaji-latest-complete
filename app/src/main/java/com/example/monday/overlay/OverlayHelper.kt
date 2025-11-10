package com.example.monday.overlay

import android.content.Context
import android.content.Intent
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
            val intent = Intent(context, OverlayService::class.java)
            context.startService(intent)
        }
    }
    
    fun stopOverlayService(context: Context) {
        val intent = Intent(context, OverlayService::class.java)
        context.stopService(intent)
    }
    
    fun openOverlaySettings(context: Context) {
        val intent = Intent(context, OverlayPermissionActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    }
}
