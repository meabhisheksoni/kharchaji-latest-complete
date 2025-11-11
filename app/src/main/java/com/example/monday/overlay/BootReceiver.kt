package com.example.monday.overlay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("BootReceiver", "Boot completed, checking overlay permission")
            
            // Check if overlay permission is granted
            if (OverlayHelper.hasOverlayPermission(context)) {
                Log.d("BootReceiver", "Permission granted, starting overlay service")
                OverlayHelper.startOverlayService(context)
            } else {
                Log.d("BootReceiver", "Overlay permission not granted")
            }
        }
    }
}
