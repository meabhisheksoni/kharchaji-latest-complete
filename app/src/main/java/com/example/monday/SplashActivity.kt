package com.example.monday

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.monday.ui.theme.KharchajiTheme
import kotlinx.coroutines.delay

/**
 * Splash screen activity using the official Android Splash Screen API
 */
@SuppressLint("CustomSplashScreen")
class SplashActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Install the splash screen before calling setContentView
        val splashScreen = installSplashScreen()
        
        // Keep the splash screen on-screen until we're done loading
        splashScreen.setKeepOnScreenCondition { true }
        
        super.onCreate(savedInstanceState)
        
        setContent {
            KharchajiTheme {
                SplashScreen(onTimeout = {
                    // Start the main activity
                    startActivity(Intent(this, MainActivity::class.java))
                    // Close this activity so it's not in the back stack
                    finish()
                })
            }
        }
    }
}

@Composable
fun SplashScreen(onTimeout: () -> Unit) {
    // Launch a coroutine to handle the delay and navigation
    LaunchedEffect(Unit) {
        try {
            // Simulate loading with a delay
            delay(1000)
            // Navigate to next screen
            onTimeout()
        } catch (e: Exception) {
            // Log any errors and still navigate to prevent app from hanging
            android.util.Log.e("KharchaJi", "Error in splash screen coroutine", e)
            onTimeout()
        }
    }
    
    // The splash screen UI
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Image(
            painter = painterResource(id = R.drawable.ic_launcher_foreground),
            contentDescription = "App Logo",
            modifier = Modifier.size(120.dp)
        )
    }
}