package com.example.monday.core.cache

import android.util.Log
import com.example.monday.data.models.CartItem

data class CachedCartItem(val item: CartItem, val timestamp: Long)

object ScanMemoryCache {
    private const val TAG = "Kharchaji_ScanCache"
    private val cache = mutableListOf<CachedCartItem>()
    // 5 Minutes Expiration Time for user scroll sessions
    // Short enough to allow repeat orders, long enough to cover slow scrolling.
    private const val EXPIRY_TIME_MS = 5 * 60 * 1000L

    @Synchronized
    fun addItems(items: List<CartItem>) {
        val now = System.currentTimeMillis()
        var addedCount = 0
        items.forEach { item ->
            // Remove any older duplicate signature to refresh its 30-min timer
            cache.removeAll { 
                it.item.rawName == item.rawName && 
                it.item.price == item.price && 
                it.item.quantity == item.quantity 
            }
            cache.add(CachedCartItem(item, now))
            addedCount++
        }
    }

    @Synchronized
    fun filterNewItems(scannedItems: List<CartItem>): List<CartItem> {
        val now = System.currentTimeMillis()
        
        // Anti-Fragile: GC expired cached items dynamically before checking
        val itemsRemoved = cache.removeAll { now - it.timestamp > EXPIRY_TIME_MS }
        if (itemsRemoved) Log.d(TAG, "Garbage Collected expired items from Memory Cache.")
        
        return scannedItems.filter { scanned ->
            // If the precise item (Name, Price, Quantity combo) exists in cache, it's redundant.
            val isDuplicate = cache.any { cached -> 
                cached.item.rawName == scanned.rawName && 
                cached.item.price == scanned.price && 
                cached.item.quantity == scanned.quantity
            }
            if (isDuplicate) {
            }
            !isDuplicate
        }
    }
    
    @Synchronized
    fun removeItems(itemsToRemove: List<CartItem>) {
        itemsToRemove.forEach { item ->
            cache.removeAll { 
                it.item.rawName == item.rawName && 
                it.item.price == item.price && 
                it.item.quantity == item.quantity 
            }
        }
    }

    @Synchronized
    fun clear() {
        cache.clear()
    }
}
