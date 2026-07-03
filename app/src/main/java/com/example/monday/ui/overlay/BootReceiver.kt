package com.example.monday.ui.overlay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            
            if (OverlayHelper.hasOverlayPermission(context)) {
                OverlayHelper.startOverlayService(context)
            } else {
            }
        }
    }
}
