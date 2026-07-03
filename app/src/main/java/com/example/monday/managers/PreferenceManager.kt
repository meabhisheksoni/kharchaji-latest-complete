package com.example.monday.managers

import android.content.Context
import android.content.SharedPreferences
import com.example.monday.ExpenseCategory
import com.example.monday.ui.components.DefaultCategories
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.example.monday.data.models.ExportHistoryItem
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz

/**
 * Centralizes all SharedPreferences access.
 * Eliminates scattered pref reads/writes across the ViewModel,
 * reducing the risk of key typos and stale-read bugs.
 *
 * For ViewModel: constructed directly with cached SharedPreferences.
 * For Services/Workers/Widgets: use `PreferenceManager.from(context)`.
 */
class PreferenceManager(
    private val categoryPrefs: SharedPreferences,
    private val monitorPrefs: SharedPreferences
) {
    private val gson = Gson()

    companion object {
        /** Factory for Service/Worker/Widget contexts that don't have ViewModel access. */
        fun from(context: Context): PreferenceManager {
            val categoryPrefs = context.getSharedPreferences("categories_prefs", Context.MODE_PRIVATE)
            val monitorPrefs = context.getSharedPreferences("monitor_prefs", Context.MODE_PRIVATE)
            return PreferenceManager(categoryPrefs, monitorPrefs)
        }
    }

    // ── Category Persistence ──────────────────────────────────────────

    fun saveCategories(type: String, categories: List<ExpenseCategory>) {
        val json = gson.toJson(categories)
        categoryPrefs.edit().putString("${type}_categories", json).apply()
    }

    fun getSavedCategories(type: String): List<ExpenseCategory>? {
        val json = categoryPrefs.getString("${type}_categories", null) ?: return null
        val typeToken = TypeToken.getParameterized(List::class.java, ExpenseCategory::class.java).type
        val categories = gson.fromJson<List<ExpenseCategory>>(json, typeToken)
        val defaultCategories = when (type) {
            "primary" -> DefaultCategories.primaryCategories
            "secondary" -> DefaultCategories.secondaryCategories
            "tertiary" -> DefaultCategories.tertiaryCategories
            else -> emptyList()
        }
        // Restore icons from defaults — Gson can't serialize ImageVector
        val iconMap = defaultCategories.associate { it.name to it.icon }
        return categories.map { category ->
            val defaultIcon = iconMap[category.name] ?: Icons.Default.MoreHoriz
            category.copy(icon = defaultIcon)
        }
    }

    // ── Category Visibility ──────────────────────────────────────────

    fun saveCategoryVisibilitySetting(type: String, isVisible: Boolean) {
        categoryPrefs.edit().putBoolean("${type}_visibility", isVisible).apply()
    }

    fun getCategoryVisibilitySetting(type: String): Boolean? {
        if (!categoryPrefs.contains("${type}_visibility")) return null
        return categoryPrefs.getBoolean("${type}_visibility", true)
    }

    // ── Payment Monitor ──────────────────────────────────────────────

    fun savePaymentMonitorSetting(key: String, isEnabled: Boolean) {
        monitorPrefs.edit().putBoolean(key, isEnabled).apply()
    }

    fun getPaymentMonitorSetting(key: String): Boolean? {
        if (!monitorPrefs.contains(key)) return null
        return monitorPrefs.getBoolean(key, true)
    }

    // ── Dashboard View Mode ──────────────────────────────────────────

    fun getDashboardViewMode(): String {
        return monitorPrefs.getString("dashboard_view_mode", "both") ?: "both"
    }

    fun saveDashboardViewMode(mode: String) {
        // Guard against invalid values — fail safe to "both"
        val safeMode = if (mode in listOf("both", "cards", "graph", "monthly")) mode else "both"
        monitorPrefs.edit().putString("dashboard_view_mode", safeMode).apply()
    }

    // ── Date Bar Position ────────────────────────────────────────────

    fun getDateBarPosition(): String {
        return monitorPrefs.getString("date_bar_position", "mid") ?: "mid"
    }

    fun saveDateBarPosition(position: String) {
        // Guard against invalid values — fail safe to "mid"
        val safePosition = if (position in listOf("start", "mid", "end")) position else "mid"
        monitorPrefs.edit().putString("date_bar_position", safePosition).apply()
    }

    // ── Recently Selected Category ───────────────────────────────────

    fun saveRecentlySelectedCategory(categoryType: String, categoryName: String) {
        categoryPrefs.edit().putString("recent_${categoryType}", categoryName).apply()
    }

    fun getRecentlySelectedCategory(categoryType: String): String? {
        return categoryPrefs.getString("recent_${categoryType}", null)
    }

    // ── Export History ───────────────────────────────────────────────

    fun saveExportHistory(history: List<ExportHistoryItem>) {
        val json = gson.toJson(history)
        monitorPrefs.edit().putString("export_history", json).apply()
    }

    fun getExportHistory(): List<ExportHistoryItem> {
        val json = monitorPrefs.getString("export_history", null) ?: return emptyList()
        val typeToken = TypeToken.getParameterized(List::class.java, ExportHistoryItem::class.java).type
        return gson.fromJson(json, typeToken)
    }
}
