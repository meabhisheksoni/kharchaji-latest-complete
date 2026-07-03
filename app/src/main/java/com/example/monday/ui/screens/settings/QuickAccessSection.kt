package com.example.monday.ui.screens.settings

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.monday.viewmodels.SettingsViewModel
import com.example.monday.TodoViewModel
import com.example.monday.ui.overlay.OverlayHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.example.monday.ui.modern.ModernColors

/**
 * Quick Access (Floating Button) + Payment Monitor sections.
 */
fun LazyListScope.quickAccessSection(
    context: Context,
    scope: CoroutineScope,
    snackbarHostState: SnackbarHostState
) {
    item {
        Text(
            text = "Quick Access",
            style = MaterialTheme.typography.titleLarge,
            color = ModernColors.DateText,
            modifier = Modifier.padding(vertical = 16.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = ModernColors.CardBg)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Floating Button",
                    style = MaterialTheme.typography.titleMedium,
                    color = ModernColors.DateText,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Text(
                    text = "Add expenses from anywhere on your phone with a floating button that appears over all apps",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ModernColors.DateBorder,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Enable Floating Button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (OverlayHelper.hasOverlayPermission(context)) {
                                OverlayHelper.startOverlayService(context)
                                scope.launch {
                                    snackbarHostState.showSnackbar("Floating button enabled!")
                                }
                            } else {
                                OverlayHelper.openOverlaySettings(context)
                            }
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.TouchApp, contentDescription = "Enable Floating Button", tint = ModernColors.EggnogDark)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Enable Floating Button", style = MaterialTheme.typography.bodyLarge, color = ModernColors.DateText)
                        Text(
                            text = if (OverlayHelper.hasOverlayPermission(context)) "Tap to activate" else "Grant permission first",
                            style = MaterialTheme.typography.bodyMedium,
                            color = ModernColors.DateBorder
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // Disable Floating Button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            OverlayHelper.stopOverlayService(context)
                            scope.launch { snackbarHostState.showSnackbar("Floating button disabled") }
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Disable Floating Button", tint = ModernColors.Destructive)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(text = "Disable Floating Button", style = MaterialTheme.typography.bodyLarge, color = ModernColors.DateText)
                        Text(
                            text = "Remove the floating button",
                            style = MaterialTheme.typography.bodyMedium,
                            color = ModernColors.DateBorder
                        )
                    }
                }
            }
        }
    }
}

fun LazyListScope.paymentMonitorSection(
    context: Context,
    enablePaymentMonitor: Boolean,
    enablePaymentVibration: Boolean,
    scope: CoroutineScope,
    snackbarHostState: SnackbarHostState,
    settingsViewModel: SettingsViewModel,
    onMonitorChange: (Boolean) -> Unit,
    onVibrationChange: (Boolean) -> Unit
) {
    item {
        Text(
            text = "Payment Monitor",
            style = MaterialTheme.typography.titleLarge,
            color = ModernColors.DateText,
            modifier = Modifier.padding(vertical = 16.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = ModernColors.CardBg)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Automated Expense Popup",
                    style = MaterialTheme.typography.titleMedium,
                    color = ModernColors.DateText,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Text(
                    text = "Automatically detect when you make a payment on GPay, PhonePe, or Paytm and show a quick-add popup",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ModernColors.DateBorder,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Enable Payment Monitor", style = MaterialTheme.typography.bodyLarge, color = ModernColors.DateText)
                    }
                    Switch(
                        checked = enablePaymentMonitor,
                        onCheckedChange = {
                            onMonitorChange(it)
                            settingsViewModel.savePaymentMonitorSetting("enable_monitor", it) 
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ModernColors.Eggshell,
                            checkedTrackColor = ModernColors.EggnogDark
                        )
                    )
                }

                if (enablePaymentMonitor) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Vibrate on Success", style = MaterialTheme.typography.bodyLarge, color = ModernColors.DateText)
                            Text(
                                text = "Play a short vibration when a payment is detected",
                                style = MaterialTheme.typography.bodyMedium,
                                color = ModernColors.DateBorder
                            )
                        }
                        Switch(
                            checked = enablePaymentVibration,
                            onCheckedChange = {
                                onVibrationChange(it)
                                settingsViewModel.savePaymentMonitorSetting("enable_vibration", it) 
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = ModernColors.Eggshell,
                                checkedTrackColor = ModernColors.EggnogDark
                            )
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                try {
                                    val intent = android.content.Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                                    context.startActivity(intent)
                                    scope.launch { snackbarHostState.showSnackbar("Please allow Notification Access for Kharchaji") }
                                } catch (e: Exception) {
                                    scope.launch { snackbarHostState.showSnackbar("Cannot open settings") }
                                }
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.NotificationsActive, contentDescription = "Enable Notification Access", tint = ModernColors.EggnogDark)
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Grant Notification Access", style = MaterialTheme.typography.bodyLarge, color = ModernColors.DateText)
                            Text(
                                text = "Required to read SMS and Bank push notifications for debits/credits",
                                style = MaterialTheme.typography.bodyMedium,
                                color = ModernColors.DateBorder
                            )
                        }
                    }
                }
            }
        }
    }
}
