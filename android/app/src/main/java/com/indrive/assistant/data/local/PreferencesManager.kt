package com.indrive.assistant.data.local

import android.content.Context
import android.content.SharedPreferences
import com.indrive.assistant.data.model.DriverSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("driver_prefs", Context.MODE_PRIVATE)

    private val _settingsFlow = MutableStateFlow(loadSettings())
    val settingsFlow: StateFlow<DriverSettings> = _settingsFlow.asStateFlow()

    fun getSettings(): DriverSettings = loadSettings()

    private fun loadSettings(): DriverSettings {
        return DriverSettings(
            fuelConsumptionLPer100Km = prefs.getFloat(KEY_FUEL_CONSUMPTION, 8.5f).toDouble(),
            fuelPricePerLiter = prefs.getFloat(KEY_FUEL_PRICE, 15.0f).toDouble(),
            inDriveCommissionPercent = prefs.getFloat(KEY_COMMISSION, 10.0f).toDouble(),
            wearAndTearPerKm = prefs.getFloat(KEY_WEAR_PER_KM, 1.2f).toDouble(),
            targetDailyEarnings = prefs.getFloat(KEY_DAILY_TARGET, 1500.0f).toDouble(),
            minProfitPerKm = prefs.getFloat(KEY_MIN_PROFIT_KM, 8.0f).toDouble(),
            currency = prefs.getString(KEY_CURRENCY, "EGP") ?: "EGP"
        )
    }

    fun saveSettings(settings: DriverSettings) {
        prefs.edit().apply {
            putFloat(KEY_FUEL_CONSUMPTION, settings.fuelConsumptionLPer100Km.toFloat())
            putFloat(KEY_FUEL_PRICE, settings.fuelPricePerLiter.toFloat())
            putFloat(KEY_COMMISSION, settings.inDriveCommissionPercent.toFloat())
            putFloat(KEY_WEAR_PER_KM, settings.wearAndTearPerKm.toFloat())
            putFloat(KEY_DAILY_TARGET, settings.targetDailyEarnings.toFloat())
            putFloat(KEY_MIN_PROFIT_KM, settings.minProfitPerKm.toFloat())
            putString(KEY_CURRENCY, settings.currency)
            apply()
        }
        _settingsFlow.value = settings
    }

    companion object {
        private const val KEY_FUEL_CONSUMPTION = "fuel_consumption"
        private const val KEY_FUEL_PRICE = "fuel_price"
        private const val KEY_COMMISSION = "in_drive_commission"
        private const val KEY_WEAR_PER_KM = "wear_per_km"
        private const val KEY_DAILY_TARGET = "daily_target"
        private const val KEY_MIN_PROFIT_KM = "min_profit_km"
        private const val KEY_CURRENCY = "currency"
    }
}
