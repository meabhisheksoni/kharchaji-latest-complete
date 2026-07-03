package com.example.monday.ui.cart

import com.example.monday.data.models.TodoItem
import com.example.monday.data.local.TodoDao
import com.example.monday.data.local.AppDatabase

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import java.util.Date
import java.util.Locale
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.example.monday.R
import com.example.monday.data.models.CartItem
import com.example.monday.core.cache.ScanMemoryCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat

class CartBatchPopupActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cart_batch_popup)
        
        // Anti-Fragile Window Tweaks for instant, bottom sheet behavior
        window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
        window.setGravity(Gravity.BOTTOM)
        val lp = window.attributes
        lp.dimAmount = 0.5f // Dim background slightly
        window.attributes = lp
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)

        val rawNames = intent.getStringArrayExtra("cart_names") ?: emptyArray()
        val prices = intent.getDoubleArrayExtra("cart_prices") ?: DoubleArray(0)
        val quantities = intent.getIntArrayExtra("cart_qtys") ?: IntArray(0)
        
        if (rawNames.isEmpty() || prices.isEmpty() || quantities.isEmpty()) {
            Toast.makeText(this, "No items found in cart.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        val titleText = findViewById<TextView>(R.id.cart_title_text)
        val totalText = findViewById<TextView>(R.id.cart_total_text)
        val itemsContainer = findViewById<LinearLayout>(R.id.cart_items_container)
        val btnSave = findViewById<Button>(R.id.btn_cart_save)
        val btnCancel = findViewById<Button>(R.id.btn_cart_cancel)
        
        titleText.text = "Save ${rawNames.size} Items?"
        
        var totalPrice = 0.0
        
        // Dynamically build the list UI
        for (i in rawNames.indices) {
            totalPrice += prices[i] * quantities[i]
            
            val itemRow = TextView(this)
            // Truncate name if it's crazy long
            val shortName = if (rawNames[i].length > 30) rawNames[i].take(27) + "..." else rawNames[i]
            itemRow.text = "${quantities[i]}x $shortName (₹${prices[i]})"
            itemRow.textSize = 15f
            itemRow.setTextColor(android.graphics.Color.DKGRAY)
            itemRow.setPadding(0, 16, 0, 16)
            itemsContainer.addView(itemRow)
            
            // tiny divider
            val divider = View(this)
            divider.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
            divider.setBackgroundColor(android.graphics.Color.LTGRAY)
            itemsContainer.addView(divider)
        }
        
        totalText.text = "Estimated Total: ₹$totalPrice"
        
        btnCancel.setOnClickListener { finish() }
        
        btnSave.setOnClickListener {
            // Anti-Fragile: 0-Latency UI.
            // Fire tasks into IO dispatcher independent of this Activity's lifecycle,
            // and close the UI instantly. User feels no lag.
            btnSave.isEnabled = false
            btnSave.text = "Saving..."
            
            // Register these items into the 30-min Memory Cache to prevent duplicates on next scan
            val itemsToCache = mutableListOf<CartItem>()
            for (i in rawNames.indices) {
                itemsToCache.add(CartItem(rawNames[i], prices[i], quantities[i]))
            }
            ScanMemoryCache.addItems(itemsToCache)
            
            val appContext = applicationContext // Grab app context to survive activity death
            
            CoroutineScope(Dispatchers.IO).launch {
                val db = AppDatabase.getDatabase(appContext)
                val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
                val today = dateFormat.format(Date())
                
                try {
                    val dao = db.todoDao() // Call correct Dao
                    for (i in rawNames.indices) {
                         // The app expects the formatted string style "ItemName  - Quantity ₹Price"
                         // We will construct this exact pattern so the main app parses it natively
                         val formattedText = "${rawNames[i]} (${quantities[i]}) - ₹${prices[i]}"
                         
                         val item = TodoItem(
                             text = formattedText,
                             timestamp = System.currentTimeMillis()
                         )
                         dao.insert(item)
                    }
                    withContext(Dispatchers.Main) {
                         Toast.makeText(appContext, "Saved ${rawNames.size} parsed items to Today.", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.e("Kharchaji_CartSave", "Failed to batch save cart items", e)
                }
            }
            
            // Instantly dismiss
            finish()
        }
    }
}





