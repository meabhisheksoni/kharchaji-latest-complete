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
        
        // Use a mutable flag — setting keepOnScreenCondition to { true } permanently
        // blocks the splash, preventing the Compose content from ever rendering.
        var isLoading = true
        splashScreen.setKeepOnScreenCondition { isLoading }
        
        super.onCreate(savedInstanceState)
        
        setContent {
            KharchajiTheme {
                SplashScreen(onTimeout = {
                    // Start the main activity
                    startActivity(Intent(this, MainActivity::class.java))
                    // Close this activity so it's not in the back stack
                    finish()
                }, onLoadingComplete = {
                    // Dismiss the system splash screen
                    isLoading = false
                })
            }
        }
    }
}

@Composable
fun SplashScreen(onTimeout: () -> Unit, onLoadingComplete: () -> Unit = {}) {
    // Launch a coroutine to handle the delay and navigation
    LaunchedEffect(Unit) {
        try {
            // Signal that loading is done — this dismisses the system splash screen
            onLoadingComplete()
            // Brief pause so the user sees the custom splash content
            delay(1000)
            // Navigate to next screen
            onTimeout()
        } catch (e: Exception) {
            // Fail-safe: always navigate to prevent app from hanging indefinitely
            android.util.Log.e("KharchaJi", "Error in splash screen coroutine", e)
            onLoadingComplete()
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
