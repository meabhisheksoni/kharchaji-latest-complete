package com.example.monday.ui.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.monday.MasterSaveHelper
import com.example.monday.R
import com.example.monday.core.utils.ParsedVoiceExpense
import com.example.monday.core.utils.VoiceExpenseParser
import com.example.monday.data.local.AppDatabase
import com.example.monday.data.models.TodoItem
import com.example.monday.ui.theme.KharchajiTheme
import com.example.monday.widget.WidgetInputActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

@AndroidEntryPoint
class VoiceExpenseActivity : ComponentActivity() {

    companion object {
        private const val TAG = "VoiceExpenseActivity"
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening by mutableStateOf(false)
    private var spokenText by mutableStateOf("")
    private var statusMessage by mutableStateOf("Initializing voice...")
    private var parsedExpense by mutableStateOf<ParsedVoiceExpense?>(null)
    private var rmsLevel by mutableStateOf(0f)
    private var hasAudioPermission by mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasAudioPermission = isGranted
        if (isGranted) {
            startListening()
        } else {
            statusMessage = "Microphone permission required for voice entry."
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Make window translucent dialog style
        window.setSoftInputMode(
            android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        )

        hasAudioPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        setContent {
            KharchajiTheme {
                VoiceExpenseDialog(
                    isListening = isListening,
                    statusMessage = statusMessage,
                    spokenText = spokenText,
                    parsedExpense = parsedExpense,
                    rmsLevel = rmsLevel,
                    hasPermission = hasAudioPermission,
                    onRequestPermission = {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    onRetryListening = {
                        startListening()
                    },
                    onSaveExpense = { item ->
                        saveExpense(item)
                    },
                    onEditExpense = { item ->
                        openManualEdit(item)
                    },
                    onDismiss = {
                        finish()
                    }
                )
            }
        }

        if (hasAudioPermission) {
            startListening()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(applicationContext)) {
            statusMessage = "Speech recognition is not available on this device."
            return
        }

        stopListening()

        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(applicationContext).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        isListening = true
                        statusMessage = "Listening... (e.g., 'Aata 50 rupay', 'Chai 20')"
                        parsedExpense = null
                    }

                    override fun onBeginningOfSpeech() {
                        statusMessage = "Hearing you..."
                    }

                    override fun onRmsChanged(rmsdB: Float) {
                        rmsLevel = rmsdB.coerceIn(0f, 10f)
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        isListening = false
                        statusMessage = "Processing your expense..."
                    }

                    override fun onError(error: Int) {
                        isListening = false
                        val errorText = when (error) {
                            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                            SpeechRecognizer.ERROR_CLIENT -> "Client error"
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permission error"
                            SpeechRecognizer.ERROR_NETWORK -> "Network connection error"
                            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                            SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized. Tap mic to retry."
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech service busy"
                            SpeechRecognizer.ERROR_SERVER -> "Server error"
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech heard. Tap mic to try again."
                            else -> "Voice recognition error"
                        }
                        statusMessage = errorText
                    }

                    override fun onResults(results: Bundle?) {
                        isListening = false
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            val recognized = matches[0]
                            spokenText = recognized
                            val parsed = VoiceExpenseParser.parse(recognized)
                            parsedExpense = parsed
                            statusMessage = if (parsed.amount > 0) "Parsed successfully!" else "Could not identify amount. Tap edit or retry."
                            triggerHaptic()
                        } else {
                            statusMessage = "No speech detected. Tap mic to retry."
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            spokenText = matches[0]
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "hi-IN")
                putExtra(RecognizerIntent.EXTRA_SUPPORTED_LANGUAGES, arrayListOf("hi-IN", "en-IN", "en-US"))
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }

            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting speech recognizer", e)
            statusMessage = "Error starting speech recognition: ${e.message}"
        }
    }

    private fun stopListening() {
        try {
            speechRecognizer?.apply {
                setRecognitionListener(null)
                cancel()
                destroy()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping speech recognizer", e)
        } finally {
            speechRecognizer = null
        }
    }

    private fun triggerHaptic() {
        try {
            val vibrator = getSystemService(Vibrator::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(80)
            }
        } catch (_: Exception) {}
    }

    private fun saveExpense(expense: ParsedVoiceExpense) {
        val amount = expense.amount
        if (amount <= 0.0) {
            Toast.makeText(this, "Please provide a valid amount", Toast.LENGTH_SHORT).show()
            return
        }

        val name = if (expense.itemName.isBlank()) "Cash Expense" else expense.itemName
        val formattedPrice = String.format(Locale.US, "%.2f", amount)
        val itemText = "$name - ₹$formattedPrice"
        val timestamp = System.currentTimeMillis()

        val appContext = applicationContext
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val db = AppDatabase.getDatabase(appContext)
                val todoItem = TodoItem(
                    text = itemText,
                    timestamp = timestamp,
                    isDone = false
                )
                val insertedId = db.todoDao().insertAndGetId(todoItem)

                val prefManager = com.example.monday.managers.PreferenceManager.from(appContext)
                val autoMasterSave = prefManager.getPaymentMonitorSetting("auto_master_save") ?: false
                if (autoMasterSave) {
                    val itemWithId = todoItem.copy(id = insertedId.toInt())
                    MasterSaveHelper.appendToMasterAsync(appContext, itemWithId)
                }

                launch(Dispatchers.Main) {
                    com.example.monday.core.utils.CompactToast.show(appContext, "Saved: $itemText")
                    triggerHaptic()
                    finish()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error saving voice expense", e)
                launch(Dispatchers.Main) {
                    com.example.monday.core.utils.CompactToast.show(appContext, "Error saving: ${e.message}")
                }
            }
        }
    }

    private fun openManualEdit(expense: ParsedVoiceExpense?) {
        val inputIntent = Intent(this, WidgetInputActivity::class.java).apply {
            putExtra("field", "item_name")
            if (expense != null) {
                if (expense.itemName.isNotBlank() && expense.itemName != "Cash Expense" && expense.itemName != "Expense") {
                    putExtra("prefill_name", expense.itemName)
                }
                if (expense.amount > 0) {
                    val amountStr = if (expense.amount % 1.0 == 0.0) {
                        expense.amount.toLong().toString()
                    } else {
                        String.format(Locale.US, "%.2f", expense.amount)
                    }
                    putExtra("prefill_amount", amountStr)
                }
            }
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(inputIntent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopListening()
    }
}

@Composable
fun VoiceExpenseDialog(
    isListening: Boolean,
    statusMessage: String,
    spokenText: String,
    parsedExpense: ParsedVoiceExpense?,
    rmsLevel: Float,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onRetryListening: () -> Unit,
    onSaveExpense: (ParsedVoiceExpense) -> Unit,
    onEditExpense: (ParsedVoiceExpense?) -> Unit,
    onDismiss: () -> Unit
) {
    // Pulse animation based on speech RMS
    val infiniteTransition = rememberInfiniteTransition(label = "mic_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f + (rmsLevel * 0.03f),
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    // Auto-save countdown if parsed successfully with amount > 0
    var countdownProgress by remember(parsedExpense) { mutableStateOf(1f) }
    var autoSaveCancelled by remember(parsedExpense) { mutableStateOf(false) }

    LaunchedEffect(parsedExpense) {
        if (parsedExpense != null && parsedExpense.amount > 0 && !autoSaveCancelled) {
            countdownProgress = 1f
            val totalSteps = 25
            for (i in 1..totalSteps) {
                delay(100)
                if (autoSaveCancelled) break
                countdownProgress = 1f - (i.toFloat() / totalSteps)
            }
            if (!autoSaveCancelled && countdownProgress <= 0.05f) {
                onSaveExpense(parsedExpense)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                ),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🎙️ Quick Voice Entry",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.Gray
                        )
                    }
                }

                // Microphone Visualizer Bubble
                Box(
                    modifier = Modifier
                        .size(90.dp)
                        .scale(if (isListening) pulseScale else 1f)
                        .background(
                            brush = Brush.radialGradient(
                                colors = if (isListening) {
                                    listOf(Color(0xFF4CAF50), Color(0xFF2E7D32))
                                } else {
                                    listOf(Color(0xFF757575), Color(0xFF424242))
                                }
                            ),
                            shape = CircleShape
                        )
                        .clickable {
                            if (!hasPermission) {
                                onRequestPermission()
                            } else {
                                onRetryListening()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isListening) Icons.Default.Mic else Icons.Default.Refresh,
                        contentDescription = "Mic",
                        tint = Color.White,
                        modifier = Modifier.size(42.dp)
                    )
                }

                // Status Message Subtitle
                Text(
                    text = statusMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center
                )

                // Spoken Subtitle (What the user actually said)
                if (spokenText.isNotBlank()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Text(
                            text = "\"$spokenText\"",
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Parsed Expense Preview Box
                if (parsedExpense != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                autoSaveCancelled = true
                                onEditExpense(parsedExpense)
                            },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (parsedExpense.amount > 0) Color(0xFF1B5E20).copy(alpha = 0.15f) else Color(0xFFE65100).copy(alpha = 0.15f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = parsedExpense.itemName,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (parsedExpense.amount > 0) "₹${String.format(Locale.US, "%.2f", parsedExpense.amount)}" else "Amount Not Detected",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (parsedExpense.amount > 0) Color(0xFF2E7D32) else Color(0xFFC62828)
                            )

                            // Auto-save progress bar indicator
                            if (parsedExpense.amount > 0 && !autoSaveCancelled) {
                                LinearProgressIndicator(
                                    progress = { countdownProgress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp),
                                    color = Color(0xFF4CAF50),
                                    trackColor = Color.LightGray.copy(alpha = 0.3f)
                                )
                                Text(
                                    text = "Auto-saving in 2s... Tap cancel or edit to stop",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    // Action Buttons Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Edit button
                        OutlinedButton(
                            onClick = {
                                autoSaveCancelled = true
                                onEditExpense(parsedExpense)
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Edit")
                        }

                        // Save button
                        Button(
                            onClick = {
                                autoSaveCancelled = true
                                onSaveExpense(parsedExpense)
                            },
                            enabled = parsedExpense.amount > 0,
                            modifier = Modifier.weight(1.3f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Save",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save Now", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}
