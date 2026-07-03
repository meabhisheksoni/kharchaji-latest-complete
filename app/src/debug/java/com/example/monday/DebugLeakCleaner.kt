package com.example.monday

import leakcanary.LeakCanary
import shark.ReferencePattern
import shark.IgnoredReferenceMatcher
import android.util.Log

class DebugLeakCleaner {
    fun applyFix() {
        try {
            val config = LeakCanary.config
            val matchers = config.referenceMatchers.toMutableList()
            
            // Add exclusion for the known Jetpack Compose PopupLayout detachment bug
            // where OnPositionedDispatcher caches detached LayoutNodes natively.
            matchers.add(
                IgnoredReferenceMatcher(
                    pattern = ReferencePattern.InstanceFieldPattern(
                        className = "androidx.compose.ui.node.OnPositionedDispatcher",
                        fieldName = "cachedNodes"
                    )
                )
            )
            
            LeakCanary.config = config.copy(referenceMatchers = matchers)
            Log.d("LeakCanary", "Applied Anti-Fragile exclusion for Compose OnPositionedDispatcher")
        } catch (e: Exception) {
            Log.e("LeakCanary", "Failed to apply exclusion matcher", e)
        }
    }
}
