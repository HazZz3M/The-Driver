package com.indrive.assistant.data.repository

import com.indrive.assistant.data.local.PreferencesManager
import com.indrive.assistant.data.model.DriverSettings
import com.indrive.assistant.data.model.VehicleProfile
import kotlinx.coroutines.flow.StateFlow

class AssistantRepository(
    private val preferencesManager: PreferencesManager
) {
    val driverSettingsFlow: StateFlow<DriverSettings> = preferencesManager.settingsFlow
    val vehicleProfilesFlow = preferencesManager.vehicleProfilesFlow
    val activeVehicleIdFlow = preferencesManager.activeVehicleIdFlow
    val themeModeFlow = preferencesManager.themeModeFlow
    val overlayStyleFlow = preferencesManager.overlayStyleFlow
    val overlaySizeFlow = preferencesManager.overlaySizeFlow

    fun getDriverSettings(): DriverSettings = preferencesManager.getSettings()

    fun updateDriverSettings(settings: DriverSettings) {
        preferencesManager.saveSettings(settings)
    }

    fun switchActiveVehicle(profile: VehicleProfile) {
        preferencesManager.switchActiveVehicle(profile)
    }

    fun saveVehicleProfiles(profiles: List<VehicleProfile>) {
        preferencesManager.saveVehicleProfiles(profiles)
    }

    fun setThemeMode(mode: String) {
        preferencesManager.setThemeMode(mode)
    }

    fun setOverlayStyle(style: String) {
        preferencesManager.setOverlayStyle(style)
    }

    fun setOverlaySize(size: String) {
        preferencesManager.setOverlaySize(size)
    }

    fun updateWearAndTearRate(rate: Double) {
        preferencesManager.updateWearAndTearRate(rate)
    }

    fun addFavoriteZone(zone: String): Boolean = preferencesManager.addFavoriteZone(zone)
    fun removeFavoriteZone(zone: String): Boolean = preferencesManager.removeFavoriteZone(zone)
    fun addBlacklistZone(zone: String): Boolean = preferencesManager.addBlacklistZone(zone)
    fun removeBlacklistZone(zone: String): Boolean = preferencesManager.removeBlacklistZone(zone)
    fun isFavoriteZone(zone: String): Boolean = preferencesManager.isFavoriteZone(zone)
    fun isBlacklistedZone(zone: String): Boolean = preferencesManager.isBlacklistedZone(zone)
    fun getPreferencesManager(): PreferencesManager = preferencesManager
}
