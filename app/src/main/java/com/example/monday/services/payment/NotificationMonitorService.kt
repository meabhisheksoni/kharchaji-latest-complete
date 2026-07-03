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
    
    // Keywords indicating a debit/credit transaction
    private val transactionKeywords = listOf("debited", "credited", "spent", "paid", "sent", "deducted")

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val prefManager = com.example.monday.managers.PreferenceManager.from(this)
        val isMonitorEnabled = prefManager.getPaymentMonitorSetting("enable_monitor") ?: true
        if (!isMonitorEnabled) return

        val notification = sbn.notification
        val extras = notification.extras
        
        val title = extras.getString(android.app.Notification.EXTRA_TITLE) ?: ""
        val text = extras.getString(android.app.Notification.EXTRA_TEXT) ?: ""
        val bigText = extras.getString(android.app.Notification.EXTRA_BIG_TEXT) ?: ""

        val fullText = "$title $text $bigText"

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
                    triggerExpenseOverlay(amount)
                }
            }
        }
    }

    private fun isTransactionMessage(text: String): Boolean {
        val lowerText = text.lowercase()
        return transactionKeywords.any { lowerText.contains(it) } && amountRegex.containsMatchIn(text)
    }

    private fun triggerExpenseOverlay(amount: String) {
        val overlayIntent = Intent(this, OverlayService::class.java).apply {
            action = "ACTION_QUICK_ADD_PAYMENT"
            putExtra("EXTRA_AMOUNT", amount)
            // Use a generic ref for SMS/notifications since we might not have a clean UPI ref
            putExtra("EXTRA_REF", "NOTIF_${System.currentTimeMillis()}")
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(overlayIntent)
            } else {
                startService(overlayIntent)
            }
        } catch (e: Exception) {
            try {
                val activityIntent = Intent(this, WidgetInputActivity::class.java).apply {
                    putExtra("field", "item_name")
                    putExtra("prefill_amount", amount.replace(",", ""))
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                startActivity(activityIntent)
            } catch (e2: Exception) {
                Log.e("Kharchaji_Notif", "Cannot launch any UI", e2)
            }
        }
    }
}
