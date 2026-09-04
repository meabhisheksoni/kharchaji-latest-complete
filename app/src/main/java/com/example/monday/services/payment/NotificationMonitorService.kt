package com.example.monday.services.payment

import android.content.Intent
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.monday.ui.overlay.OverlayService
import com.example.monday.widget.WidgetInputActivity

class NotificationMonitorService : NotificationListenerService() {

    private val processedNotifications = mutableSetOf<String>()

    // Regex to capture amounts like Rs 500, Rs. 500.50, INR 500, ₹500
    private val amountRegex = Regex("(?:Rs\\.?|INR|₹)\\s*([0-9,]+(?:\\.[0-9]{1,2})?)", RegexOption.IGNORE_CASE)
    
    // Keywords indicating a debit transaction as requested
    private val transactionKeywords = listOf("debited")

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val prefManager = com.example.monday.managers.PreferenceManager.from(this)
        val isMonitorEnabled = prefManager.getPaymentMonitorSetting("enable_monitor") ?: true
        if (!isMonitorEnabled) return

        val notification = sbn.notification
        val extras = notification.extras
        
        val title = extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString() ?: ""
        val bigText = extras.getCharSequence(android.app.Notification.EXTRA_BIG_TEXT)?.toString() ?: ""
        
        // Handle InboxStyle / Grouped Notifications
        val textLines = extras.getCharSequenceArray(android.app.Notification.EXTRA_TEXT_LINES)
        val linesText = textLines?.joinToString(" ") { it.toString() } ?: ""

        val fullText = "$title $text $bigText $linesText"

        if (isTransactionMessage(fullText)) {
            val amountMatch = amountRegex.find(fullText)
            if (amountMatch != null) {
                val amount = amountMatch.groupValues[1]
                
                // Prevent duplicate triggers by using a hash of the text and timestamp (rough 1 min window)
                val timeWindow = System.currentTimeMillis() / 60000 
                val uniqueId = "${fullText.hashCode()}_$timeWindow"
                
                if (!processedNotifications.contains(uniqueId)) {
                    processedNotifications.add(uniqueId)
                    
                    if (processedNotifications.size > 50) {
                        val iterator = processedNotifications.iterator()
                        if (iterator.hasNext()) {
                            iterator.next()
                            iterator.remove()
                        }
                    }
                    
                    Log.d("Kharchaji_Notif", "Detected transaction of amount: $amount")
                    triggerForcedExpensePopup(amount)
                }
            }
        }
    }

    private fun isTransactionMessage(text: String): Boolean {
        val lowerText = text.lowercase()
        return transactionKeywords.any { lowerText.contains(it) } && amountRegex.containsMatchIn(text)
    }

    private fun triggerForcedExpensePopup(amount: String) {
        // 1. Vibrate device
        try {
            val vibrator = getSystemService(android.content.Context.VIBRATOR_SERVICE) as android.os.Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(android.os.VibrationEffect.createOneShot(500, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(500)
            }
        } catch (e: Exception) {
            Log.e("Kharchaji_Notif", "Vibration failed", e)
        }

        // 2. Launch WidgetInputActivity directly in forced mode
        try {
            val activityIntent = Intent(this, WidgetInputActivity::class.java).apply {
                putExtra("field", "item_name")
                putExtra("prefill_amount", amount.replace(",", ""))
                putExtra("isForced", true) // Our new flag
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or 
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or 
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(activityIntent)
        } catch (e: Exception) {
            Log.e("Kharchaji_Notif", "Cannot launch forced UI", e)
        }
    }
}
