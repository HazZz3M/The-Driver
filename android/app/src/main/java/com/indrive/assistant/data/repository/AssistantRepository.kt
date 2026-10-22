package com.indrive.assistant.data.repository

import com.indrive.assistant.data.local.PreferencesManager
import com.indrive.assistant.data.model.DriverSettings
import com.indrive.assistant.data.model.HudPrefs
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject

class AssistantRepository(
    private val preferencesManager: PreferencesManager
) {
    val driverSettingsFlow: StateFlow<DriverSettings> = preferencesManager.settingsFlow

    fun getDriverSettings(): DriverSettings = preferencesManager.getSettings()

    fun getHudPrefs(): HudPrefs = preferencesManager.getHudPrefs()

    fun applySettingsFromFlutter(json: JSONObject) = preferencesManager.applyFromFlutter(json)

    fun zonesAsJson(): JSONObject = preferencesManager.zonesAsJson()

    fun addFavoriteZone(zone: String): Boolean = preferencesManager.addFavoriteZone(zone)
    fun removeFavoriteZone(zone: String): Boolean = preferencesManager.removeFavoriteZone(zone)
    fun addBlacklistZone(zone: String): Boolean = preferencesManager.addBlacklistZone(zone)
    fun removeBlacklistZone(zone: String): Boolean = preferencesManager.removeBlacklistZone(zone)
    fun isFavoriteZone(zone: String): Boolean = preferencesManager.isFavoriteZone(zone)
    fun isBlacklistedZone(zone: String): Boolean = preferencesManager.isBlacklistedZone(zone)

    /** favorite = true → favourites, false → blocked, null → neither. */
    fun setPassengerList(name: String, favorite: Boolean?) = preferencesManager.setPassengerList(name, favorite)
    fun isFavoritePassenger(name: String): Boolean = preferencesManager.isFavoritePassenger(name)
    fun isBlacklistedPassenger(name: String): Boolean = preferencesManager.isBlacklistedPassenger(name)
}
