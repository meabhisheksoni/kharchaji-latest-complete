package com.example.monday.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.monday.managers.PreferenceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefManager: PreferenceManager
) : ViewModel() {

    private val _directMasterEditMode = MutableStateFlow(prefManager.getPaymentMonitorSetting("direct_master_edit_mode") ?: false)
    val directMasterEditMode: StateFlow<Boolean> = _directMasterEditMode

    fun saveCategoryVisibilitySetting(type: String, isVisible: Boolean) {
        prefManager.saveCategoryVisibilitySetting(type, isVisible)
    }

    fun getCategoryVisibilitySetting(type: String): Boolean? = prefManager.getCategoryVisibilitySetting(type)

    fun savePaymentMonitorSetting(key: String, isEnabled: Boolean) {
        prefManager.savePaymentMonitorSetting(key, isEnabled)
    }

    fun setDirectMasterEditMode(enabled: Boolean) {
        prefManager.savePaymentMonitorSetting("direct_master_edit_mode", enabled)
        _directMasterEditMode.value = enabled
    }

    fun getPaymentMonitorSetting(key: String): Boolean? = prefManager.getPaymentMonitorSetting(key)

    fun getDashboardViewMode(): String = prefManager.getDashboardViewMode()

    fun saveDashboardViewMode(mode: String) {
        prefManager.saveDashboardViewMode(mode)
    }

    fun getDateBarPosition(): String = prefManager.getDateBarPosition()

    fun saveDateBarPosition(position: String) {
        prefManager.saveDateBarPosition(position)
    }

    fun saveRecentlySelectedCategory(categoryType: String, categoryName: String) {
        prefManager.saveRecentlySelectedCategory(categoryType, categoryName)
    }

    fun getRecentlySelectedCategory(categoryType: String): String? =
        prefManager.getRecentlySelectedCategory(categoryType)
}
