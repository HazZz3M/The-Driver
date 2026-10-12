package com.indrive.assistant.data.repository

import com.indrive.assistant.data.local.PreferencesManager
import com.indrive.assistant.data.model.DriverSettings
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject

class AssistantRepository(
    private val preferencesManager: PreferencesManager
) {
    val driverSettingsFlow: StateFlow<DriverSettings> = preferencesManager.settingsFlow
    val overlayStyleFlow = preferencesManager.overlayStyleFlow
    val overlaySizeFlow = preferencesManager.overlaySizeFlow

    fun getDriverSettings(): DriverSettings = preferencesManager.getSettings()

    fun applySettingsFromFlutter(json: JSONObject) = preferencesManager.applyFromFlutter(json)

    fun zonesAsJson(): JSONObject = preferencesManager.zonesAsJson()

    fun addFavoriteZone(zone: String): Boolean = preferencesManager.addFavoriteZone(zone)
    fun removeFavoriteZone(zone: String): Boolean = preferencesManager.removeFavoriteZone(zone)
    fun addBlacklistZone(zone: String): Boolean = preferencesManager.addBlacklistZone(zone)
    fun removeBlacklistZone(zone: String): Boolean = preferencesManager.removeBlacklistZone(zone)
    fun isFavoriteZone(zone: String): Boolean = preferencesManager.isFavoriteZone(zone)
    fun isBlacklistedZone(zone: String): Boolean = preferencesManager.isBlacklistedZone(zone)
    fun getPreferencesManager(): PreferencesManager = preferencesManager
}
