package com.indrive.assistant.data.local

import android.content.Context
import android.content.SharedPreferences
import com.indrive.assistant.data.model.DriverSettings
import com.indrive.assistant.data.model.VehicleProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("driver_prefs", Context.MODE_PRIVATE)

    private val _settingsFlow = MutableStateFlow(loadSettings())
    val settingsFlow: StateFlow<DriverSettings> = _settingsFlow.asStateFlow()

    private val _themeModeFlow = MutableStateFlow(prefs.getString(KEY_THEME_MODE, "DARK") ?: "DARK")
    val themeModeFlow: StateFlow<String> = _themeModeFlow.asStateFlow()

    private val _overlayStyleFlow = MutableStateFlow(prefs.getString(KEY_OVERLAY_STYLE, "COMPACT_CIRCLE") ?: "COMPACT_CIRCLE")
    val overlayStyleFlow: StateFlow<String> = _overlayStyleFlow.asStateFlow()

    private val _overlaySizeFlow = MutableStateFlow(prefs.getString(KEY_OVERLAY_SIZE, "MEDIUM") ?: "MEDIUM")
    val overlaySizeFlow: StateFlow<String> = _overlaySizeFlow.asStateFlow()

    private val _vehicleProfilesFlow = MutableStateFlow(loadVehicleProfiles())
    val vehicleProfilesFlow: StateFlow<List<VehicleProfile>> = _vehicleProfilesFlow.asStateFlow()

    private val _activeVehicleIdFlow = MutableStateFlow(prefs.getString(KEY_ACTIVE_VEHICLE_ID, "") ?: "")
    val activeVehicleIdFlow: StateFlow<String> = _activeVehicleIdFlow.asStateFlow()

    fun getSettings(): DriverSettings = loadSettings()

    private fun loadSettings(): DriverSettings {
        return DriverSettings(
            fuelConsumptionLPer100Km = prefs.getFloat(KEY_FUEL_CONSUMPTION, 8.5f).toDouble(),
            fuelPricePerLiter = prefs.getFloat(KEY_FUEL_PRICE, 15.0f).toDouble(),
            inDriveCommissionPercent = prefs.getFloat(KEY_COMMISSION, 10.0f).toDouble(),
            wearAndTearPerKm = prefs.getFloat(KEY_WEAR_PER_KM, 1.2f).toDouble(),
            targetDailyEarnings = prefs.getFloat(KEY_DAILY_TARGET, 1500.0f).toDouble(),
            minProfitPerKm = prefs.getFloat(KEY_MIN_PROFIT_KM, 8.0f).toDouble(),
            currency = prefs.getString(KEY_CURRENCY, "EGP") ?: "EGP",
            favoriteZones = prefs.getString(KEY_FAVORITE_ZONES, null)?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
                ?: listOf("المعادي", "التجمع", "مصر الجديدة", "الدقي", "الشيخ زايد"),
            blacklistedZones = prefs.getString(KEY_BLACKLISTED_ZONES, null)?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
                ?: listOf("شبرا الخيمة", "حلوان", "المرج")
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
            putString(KEY_FAVORITE_ZONES, settings.favoriteZones.joinToString(","))
            putString(KEY_BLACKLISTED_ZONES, settings.blacklistedZones.joinToString(","))
            apply()
        }
        _settingsFlow.value = settings
    }

    // =========================================================================
    // THEME & DISPLAY PREFERENCES
    // =========================================================================
    fun setThemeMode(mode: String) {
        prefs.edit().putString(KEY_THEME_MODE, mode).apply()
        _themeModeFlow.value = mode
    }

    fun setOverlayStyle(style: String) {
        prefs.edit().putString(KEY_OVERLAY_STYLE, style).apply()
        _overlayStyleFlow.value = style
    }

    fun setOverlaySize(size: String) {
        prefs.edit().putString(KEY_OVERLAY_SIZE, size).apply()
        _overlaySizeFlow.value = size
    }

    // =========================================================================
    // QUICK ZONE OPERATIONS (1-Tap from any order)
    // =========================================================================
    fun addFavoriteZone(zone: String): Boolean {
        val clean = zone.trim()
        if (clean.isEmpty()) return false
        val current = loadSettings()
        if (current.favoriteZones.any { it.equals(clean, ignoreCase = true) }) return false
        val updated = current.favoriteZones.toMutableList().apply { add(clean) }
        saveSettings(current.copy(favoriteZones = updated))
        return true
    }

    fun addBlacklistZone(zone: String): Boolean {
        val clean = zone.trim()
        if (clean.isEmpty()) return false
        val current = loadSettings()
        if (current.blacklistedZones.any { it.equals(clean, ignoreCase = true) }) return false
        val updated = current.blacklistedZones.toMutableList().apply { add(clean) }
        saveSettings(current.copy(blacklistedZones = updated))
        return true
    }

    // =========================================================================
    // MULTI-VEHICLE PROFILES
    // =========================================================================
    private fun loadVehicleProfiles(): List<VehicleProfile> {
        val raw = prefs.getString(KEY_VEHICLE_PROFILES, null)
        if (raw.isNullOrEmpty()) {
            val defaultProfiles = listOf(
                VehicleProfile(
                    name = "سيارة اقتصادية (بنزين 92)",
                    modelYear = "2021",
                    fuelType = "بنزين 92",
                    fuelConsumptionLPer100Km = 8.5,
                    fuelPricePerLiter = 15.25,
                    wearAndTearPerKm = 1.2
                ),
                VehicleProfile(
                    name = "سيارة موفرة (غاز طبيعي / سولار)",
                    modelYear = "2019",
                    fuelType = "غاز طبيعي / CNG",
                    fuelConsumptionLPer100Km = 7.0,
                    fuelPricePerLiter = 11.50,
                    wearAndTearPerKm = 1.1
                )
            )
            saveVehicleProfiles(defaultProfiles)
            if (prefs.getString(KEY_ACTIVE_VEHICLE_ID, "").isNullOrEmpty()) {
                prefs.edit().putString(KEY_ACTIVE_VEHICLE_ID, defaultProfiles.first().id).apply()
                _activeVehicleIdFlow.value = defaultProfiles.first().id
            }
            return defaultProfiles
        }

        val list = mutableListOf<VehicleProfile>()
        try {
            val jsonArray = JSONArray(raw)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    VehicleProfile(
                        id = obj.optString("id"),
                        name = obj.optString("name", "مركبة"),
                        modelYear = obj.optString("modelYear", ""),
                        fuelType = obj.optString("fuelType", "بنزين 92"),
                        fuelConsumptionLPer100Km = obj.optDouble("fuelConsumptionLPer100Km", 8.5),
                        fuelPricePerLiter = obj.optDouble("fuelPricePerLiter", 15.25),
                        wearAndTearPerKm = obj.optDouble("wearAndTearPerKm", 1.2),
                        inDriveCommissionPercent = obj.optDouble("inDriveCommissionPercent", 10.0),
                        minProfitPerKm = obj.optDouble("minProfitPerKm", 8.0),
                        notes = obj.optString("notes", "")
                    )
                )
            }
        } catch (_: Exception) {}

        if (list.isEmpty()) {
            return listOf(VehicleProfile(name = "مركبة افتراضية"))
        }
        return list
    }

    fun saveVehicleProfiles(profiles: List<VehicleProfile>) {
        val jsonArray = JSONArray()
        for (p in profiles) {
            val obj = JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
                put("modelYear", p.modelYear)
                put("fuelType", p.fuelType)
                put("fuelConsumptionLPer100Km", p.fuelConsumptionLPer100Km)
                put("fuelPricePerLiter", p.fuelPricePerLiter)
                put("wearAndTearPerKm", p.wearAndTearPerKm)
                put("inDriveCommissionPercent", p.inDriveCommissionPercent)
                put("minProfitPerKm", p.minProfitPerKm)
                put("notes", p.notes)
            }
            jsonArray.put(obj)
        }
        prefs.edit().putString(KEY_VEHICLE_PROFILES, jsonArray.toString()).apply()
        _vehicleProfilesFlow.value = profiles
    }

    fun switchActiveVehicle(profile: VehicleProfile) {
        prefs.edit().putString(KEY_ACTIVE_VEHICLE_ID, profile.id).apply()
        _activeVehicleIdFlow.value = profile.id

        val current = loadSettings()
        val updatedSettings = current.copy(
            fuelConsumptionLPer100Km = profile.fuelConsumptionLPer100Km,
            fuelPricePerLiter = profile.fuelPricePerLiter,
            inDriveCommissionPercent = profile.inDriveCommissionPercent,
            wearAndTearPerKm = profile.wearAndTearPerKm,
            minProfitPerKm = profile.minProfitPerKm
        )
        saveSettings(updatedSettings)
    }

    fun updateWearAndTearRate(newRate: Double) {
        val current = loadSettings()
        saveSettings(current.copy(wearAndTearPerKm = newRate))
    }

    companion object {
        private const val KEY_FUEL_CONSUMPTION = "fuel_consumption"
        private const val KEY_FUEL_PRICE = "fuel_price"
        private const val KEY_COMMISSION = "in_drive_commission"
        private const val KEY_WEAR_PER_KM = "wear_per_km"
        private const val KEY_DAILY_TARGET = "daily_target"
        private const val KEY_MIN_PROFIT_KM = "min_profit_km"
        private const val KEY_CURRENCY = "currency"
        private const val KEY_FAVORITE_ZONES = "favorite_zones"
        private const val KEY_BLACKLISTED_ZONES = "blacklisted_zones"

        private const val KEY_THEME_MODE = "app_theme_mode"
        private const val KEY_OVERLAY_STYLE = "overlay_style"
        private const val KEY_OVERLAY_SIZE = "overlay_size"
        private const val KEY_VEHICLE_PROFILES = "vehicle_profiles"
        private const val KEY_ACTIVE_VEHICLE_ID = "active_vehicle_id"
    }
}
