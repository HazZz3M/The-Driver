package com.indrive.assistant.data.local

import android.content.Context
import android.content.SharedPreferences
import com.indrive.assistant.data.model.DriverSettings
import com.indrive.assistant.data.model.HudPrefs
import com.indrive.assistant.engine.FareCalculatorEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Native mirror of the driver's settings.
 *
 * The Flutter app owns the settings and pushes them here via [applyFromFlutter] (MethodChannel
 * "syncSettings") at startup and on every change. Zone edits made from the floating HUD are written
 * here and pulled back by Flutter on resume ("getNativeZones").
 */
class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("driver_prefs", Context.MODE_PRIVATE)

    @Volatile
    private var cachedSettings: DriverSettings = loadSettings()

    private val _settingsFlow = MutableStateFlow(cachedSettings)
    val settingsFlow: StateFlow<DriverSettings> = _settingsFlow.asStateFlow()

    @Volatile
    private var cachedHud: HudPrefs = loadHudPrefs()

    /** In-memory copy; safe to call from the accessibility worker thread on every scan. */
    fun getSettings(): DriverSettings = cachedSettings

    /** Floating HUD display preferences (in-memory copy). */
    fun getHudPrefs(): HudPrefs = cachedHud

    private fun loadHudPrefs(): HudPrefs {
        val raw = prefs.getString(KEY_HUD_PREFS, null)
        val legacy = HudPrefs(
            style = prefs.getString(KEY_OVERLAY_STYLE, null) ?: "COMPACT_CIRCLE",
            size = prefs.getString(KEY_OVERLAY_SIZE, null) ?: "MEDIUM"
        )
        if (raw.isNullOrBlank()) return legacy
        return try {
            HudPrefs.fromJson(JSONObject(raw), legacy)
        } catch (_: Exception) {
            legacy
        }
    }

    private fun saveHudPrefs(hud: HudPrefs) {
        prefs.edit().putString(KEY_HUD_PREFS, hud.toJson().toString()).apply()
        cachedHud = hud
    }

    private fun loadSettings(): DriverSettings {
        val d = DriverSettings()
        return DriverSettings(
            fuelConsumptionLPer100Km = prefs.getFloat(KEY_FUEL_CONSUMPTION, d.fuelConsumptionLPer100Km.toFloat()).toDouble(),
            fuelPricePerLiter = prefs.getFloat(KEY_FUEL_PRICE, d.fuelPricePerLiter.toFloat()).toDouble(),
            inDriveCommissionPercent = prefs.getFloat(KEY_COMMISSION, d.inDriveCommissionPercent.toFloat()).toDouble(),
            wearAndTearPerKm = prefs.getFloat(KEY_WEAR_PER_KM, d.wearAndTearPerKm.toFloat()).toDouble(),
            targetDailyEarnings = prefs.getFloat(KEY_DAILY_TARGET, d.targetDailyEarnings.toFloat()).toDouble(),
            minProfitPerKm = prefs.getFloat(KEY_MIN_PROFIT_KM, d.minProfitPerKm.toFloat()).toDouble(),
            assumedTripKm = prefs.getFloat(KEY_ASSUMED_TRIP_KM, d.assumedTripKm.toFloat()).toDouble(),
            currency = prefs.getString(KEY_CURRENCY, d.currency) ?: d.currency,
            favoriteZones = decodeList(prefs.getString(KEY_FAVORITE_ZONES, null)) ?: d.favoriteZones,
            blacklistedZones = decodeList(prefs.getString(KEY_BLACKLISTED_ZONES, null)) ?: d.blacklistedZones,
            autoAcceptEnabled = prefs.getBoolean(KEY_AUTO_ACCEPT_ENABLED, d.autoAcceptEnabled),
            autoAcceptMinProfitPerKm = prefs.getFloat(KEY_AUTO_ACCEPT_MIN_PROFIT_KM, d.autoAcceptMinProfitPerKm.toFloat()).toDouble(),
            autoAcceptMinNetProfit = prefs.getFloat(KEY_AUTO_ACCEPT_MIN_NET_PROFIT, d.autoAcceptMinNetProfit.toFloat()).toDouble(),
            autoAcceptMaxPickupKm = prefs.getFloat(KEY_AUTO_ACCEPT_MAX_PICKUP_KM, d.autoAcceptMaxPickupKm.toFloat()).toDouble(),
            autoAcceptOnlyFavoriteZones = prefs.getBoolean(KEY_AUTO_ACCEPT_ONLY_FAV, d.autoAcceptOnlyFavoriteZones),
            autoAcceptRejectBlacklisted = prefs.getBoolean(KEY_AUTO_ACCEPT_REJECT_BLACK, d.autoAcceptRejectBlacklisted),
            autoAcceptDelaySeconds = prefs.getFloat(KEY_AUTO_ACCEPT_DELAY, d.autoAcceptDelaySeconds.toFloat()).toDouble(),
            pickupPricingEnabled = prefs.getBoolean(KEY_PICKUP_PRICING_ENABLED, d.pickupPricingEnabled),
            pickupProfitPerKm = prefs.getFloat(KEY_PICKUP_PROFIT_KM, d.pickupProfitPerKm.toFloat()).toDouble(),
            pickupCommissionSharePercent = prefs.getFloat(KEY_PICKUP_COMMISSION_SHARE, d.pickupCommissionSharePercent.toFloat()).toDouble(),
            favoritePassengers = decodeList(prefs.getString(KEY_FAVORITE_PASSENGERS, null)) ?: d.favoritePassengers,
            blacklistedPassengers = decodeList(prefs.getString(KEY_BLACKLISTED_PASSENGERS, null)) ?: d.blacklistedPassengers
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
            putFloat(KEY_ASSUMED_TRIP_KM, settings.assumedTripKm.toFloat())
            putString(KEY_CURRENCY, settings.currency)
            putString(KEY_FAVORITE_ZONES, JSONArray(settings.favoriteZones).toString())
            putString(KEY_BLACKLISTED_ZONES, JSONArray(settings.blacklistedZones).toString())
            putBoolean(KEY_AUTO_ACCEPT_ENABLED, settings.autoAcceptEnabled)
            putFloat(KEY_AUTO_ACCEPT_MIN_PROFIT_KM, settings.autoAcceptMinProfitPerKm.toFloat())
            putFloat(KEY_AUTO_ACCEPT_MIN_NET_PROFIT, settings.autoAcceptMinNetProfit.toFloat())
            putFloat(KEY_AUTO_ACCEPT_MAX_PICKUP_KM, settings.autoAcceptMaxPickupKm.toFloat())
            putBoolean(KEY_AUTO_ACCEPT_ONLY_FAV, settings.autoAcceptOnlyFavoriteZones)
            putBoolean(KEY_AUTO_ACCEPT_REJECT_BLACK, settings.autoAcceptRejectBlacklisted)
            putFloat(KEY_AUTO_ACCEPT_DELAY, settings.autoAcceptDelaySeconds.toFloat())
            putBoolean(KEY_PICKUP_PRICING_ENABLED, settings.pickupPricingEnabled)
            putFloat(KEY_PICKUP_PROFIT_KM, settings.pickupProfitPerKm.toFloat())
            putFloat(KEY_PICKUP_COMMISSION_SHARE, settings.pickupCommissionSharePercent.toFloat())
            // JSON arrays: a name may contain a comma.
            putString(KEY_FAVORITE_PASSENGERS, JSONArray(settings.favoritePassengers).toString())
            putString(KEY_BLACKLISTED_PASSENGERS, JSONArray(settings.blacklistedPassengers).toString())
            apply()
        }
        cachedSettings = settings
        _settingsFlow.value = settings
    }

    // =========================================================================
    // FLUTTER SYNC
    // =========================================================================

    /** Applies the full settings snapshot sent by Flutter (see NativeBridgeService.syncSettings). */
    fun applyFromFlutter(json: JSONObject) {
        val d = cachedSettings
        fun dbl(key: String, fallback: Double) = if (json.has(key)) json.optDouble(key, fallback) else fallback
        fun bool(key: String, fallback: Boolean) = if (json.has(key)) json.optBoolean(key, fallback) else fallback
        fun list(key: String, fallback: List<String>): List<String> {
            val arr = json.optJSONArray(key) ?: return fallback
            return (0 until arr.length()).map { arr.optString(it).trim() }.filter { it.isNotEmpty() }
        }

        saveSettings(
            DriverSettings(
                fuelConsumptionLPer100Km = dbl("fuelConsumptionLPer100Km", d.fuelConsumptionLPer100Km),
                fuelPricePerLiter = dbl("fuelPricePerLiter", d.fuelPricePerLiter),
                inDriveCommissionPercent = dbl("inDriveCommissionPercent", d.inDriveCommissionPercent),
                wearAndTearPerKm = dbl("wearAndTearPerKm", d.wearAndTearPerKm),
                targetDailyEarnings = dbl("targetDailyEarnings", d.targetDailyEarnings),
                minProfitPerKm = dbl("minProfitPerKm", d.minProfitPerKm),
                assumedTripKm = dbl("assumedTripKm", d.assumedTripKm),
                currency = json.optString("currency", d.currency),
                favoriteZones = list("favoriteZones", d.favoriteZones),
                blacklistedZones = list("blacklistedZones", d.blacklistedZones),
                autoAcceptEnabled = bool("autoAcceptEnabled", d.autoAcceptEnabled),
                autoAcceptMinProfitPerKm = dbl("autoAcceptMinProfitPerKm", d.autoAcceptMinProfitPerKm),
                autoAcceptMinNetProfit = dbl("autoAcceptMinNetProfit", d.autoAcceptMinNetProfit),
                autoAcceptMaxPickupKm = dbl("autoAcceptMaxPickupKm", d.autoAcceptMaxPickupKm),
                autoAcceptOnlyFavoriteZones = bool("autoAcceptOnlyFavoriteZones", d.autoAcceptOnlyFavoriteZones),
                autoAcceptRejectBlacklisted = bool("autoAcceptRejectBlacklisted", d.autoAcceptRejectBlacklisted),
                autoAcceptDelaySeconds = dbl("autoAcceptDelaySeconds", d.autoAcceptDelaySeconds),
                pickupPricingEnabled = bool("pickupPricingEnabled", d.pickupPricingEnabled),
                pickupProfitPerKm = dbl("pickupProfitPerKm", d.pickupProfitPerKm),
                pickupCommissionSharePercent = dbl("pickupCommissionSharePercent", d.pickupCommissionSharePercent),
                favoritePassengers = list("favoritePassengers", d.favoritePassengers),
                blacklistedPassengers = list("blacklistedPassengers", d.blacklistedPassengers)
            )
        )

        // Flutter has now seen (and merged) any HUD zone edits.
        prefs.edit().putBoolean(KEY_ZONES_DIRTY, false).apply()

        json.optJSONObject("hud")?.let { saveHudPrefs(HudPrefs.fromJson(it, cachedHud)) }
    }

    /**
     * Zone lists as currently known natively, with how Flutter should treat them:
     *  - "replace": the driver edited zones from the HUD since the last sync; native is newest.
     *  - "union":   zones were saved by a pre-1.10 build (separate store); merge, never drop.
     *  - "none":    nothing new; Flutter's copy is authoritative.
     */
    fun zonesAsJson(): JSONObject {
        val s = cachedSettings
        val mode = when {
            prefs.getBoolean(KEY_ZONES_DIRTY, false) -> "replace"
            isLegacyList(prefs.getString(KEY_FAVORITE_ZONES, null)) ||
                isLegacyList(prefs.getString(KEY_BLACKLISTED_ZONES, null)) -> "union"
            else -> "none"
        }
        return JSONObject().apply {
            put("mode", mode)
            put("favoriteZones", JSONArray(s.favoriteZones))
            put("blacklistedZones", JSONArray(s.blacklistedZones))
            // Passenger lists edited from the HUD travel with the same "dirty" flag.
            put("favoritePassengers", JSONArray(s.favoritePassengers))
            put("blacklistedPassengers", JSONArray(s.blacklistedPassengers))
        }
    }

    // =========================================================================
    // QUICK PASSENGER OPERATIONS (1-Tap from the HUD). Adding to one list removes from the other.
    // =========================================================================
    fun setPassengerList(name: String, favorite: Boolean?) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        val current = cachedSettings
        fun without(list: List<String>) = list.filterNot { FareCalculatorEngine.matchPassenger(clean, listOf(it)) != null }
        val fav = without(current.favoritePassengers).let { if (favorite == true) it + clean else it }
        val black = without(current.blacklistedPassengers).let { if (favorite == false) it + clean else it }
        saveSettings(current.copy(favoritePassengers = fav, blacklistedPassengers = black))
        markZonesDirty()
    }

    fun isFavoritePassenger(name: String): Boolean = cachedSettings.isFavoritePassenger(name)

    fun isBlacklistedPassenger(name: String): Boolean = cachedSettings.isBlacklistedPassenger(name)

    private fun markZonesDirty() {
        prefs.edit().putBoolean(KEY_ZONES_DIRTY, true).apply()
    }


    // =========================================================================
    // QUICK ZONE OPERATIONS (1-Tap from the HUD)
    // =========================================================================
    fun addFavoriteZone(zone: String): Boolean {
        val clean = zone.trim()
        if (clean.isEmpty()) return false
        val current = cachedSettings
        if (current.favoriteZones.any { it.equals(clean, ignoreCase = true) }) return false
        saveSettings(current.copy(favoriteZones = current.favoriteZones + clean))
        markZonesDirty()
        return true
    }

    fun removeFavoriteZone(zone: String): Boolean {
        val clean = zone.trim()
        val current = cachedSettings
        saveSettings(current.copy(favoriteZones = current.favoriteZones.filterNot { it.equals(clean, ignoreCase = true) }))
        markZonesDirty()
        return true
    }

    fun addBlacklistZone(zone: String): Boolean {
        val clean = zone.trim()
        if (clean.isEmpty()) return false
        val current = cachedSettings
        if (current.blacklistedZones.any { it.equals(clean, ignoreCase = true) }) return false
        saveSettings(current.copy(blacklistedZones = current.blacklistedZones + clean))
        markZonesDirty()
        return true
    }

    fun removeBlacklistZone(zone: String): Boolean {
        val clean = zone.trim()
        val current = cachedSettings
        saveSettings(current.copy(blacklistedZones = current.blacklistedZones.filterNot { it.equals(clean, ignoreCase = true) }))
        markZonesDirty()
        return true
    }

    fun isFavoriteZone(zone: String): Boolean = cachedSettings.isFavorite(zone)

    fun isBlacklistedZone(zone: String): Boolean = cachedSettings.isBlacklisted(zone)

    companion object {
        private const val KEY_FUEL_CONSUMPTION = "fuel_consumption"
        private const val KEY_FUEL_PRICE = "fuel_price"
        private const val KEY_COMMISSION = "in_drive_commission"
        private const val KEY_WEAR_PER_KM = "wear_per_km"
        private const val KEY_DAILY_TARGET = "daily_target"
        private const val KEY_MIN_PROFIT_KM = "min_profit_km"
        private const val KEY_ASSUMED_TRIP_KM = "assumed_trip_km"
        private const val KEY_CURRENCY = "currency"
        private const val KEY_FAVORITE_ZONES = "favorite_zones"
        private const val KEY_BLACKLISTED_ZONES = "blacklisted_zones"

        private const val KEY_OVERLAY_STYLE = "overlay_style"
        private const val KEY_OVERLAY_SIZE = "overlay_size"
        private const val KEY_HUD_PREFS = "hud_prefs_json"

        private const val KEY_AUTO_ACCEPT_ENABLED = "auto_accept_enabled"
        private const val KEY_AUTO_ACCEPT_MIN_PROFIT_KM = "auto_accept_min_profit_km"
        private const val KEY_AUTO_ACCEPT_MIN_NET_PROFIT = "auto_accept_min_net_profit"
        private const val KEY_AUTO_ACCEPT_MAX_PICKUP_KM = "auto_accept_max_pickup_km"
        private const val KEY_AUTO_ACCEPT_ONLY_FAV = "auto_accept_only_fav"
        private const val KEY_AUTO_ACCEPT_REJECT_BLACK = "auto_accept_reject_black"
        private const val KEY_AUTO_ACCEPT_DELAY = "auto_accept_delay"
        private const val KEY_ZONES_DIRTY = "zones_dirty_since_flutter_sync"
        private const val KEY_PICKUP_PRICING_ENABLED = "pickup_pricing_enabled"
        private const val KEY_PICKUP_PROFIT_KM = "pickup_profit_km"
        private const val KEY_PICKUP_COMMISSION_SHARE = "pickup_commission_share"
        private const val KEY_FAVORITE_PASSENGERS = "favorite_passengers"
        private const val KEY_BLACKLISTED_PASSENGERS = "blacklisted_passengers"

        private fun isLegacyList(raw: String?): Boolean =
            raw != null && raw.isNotBlank() && !raw.trim().startsWith("[")

        /** Reads a JSON-array list, falling back to the legacy comma-joined format. */
        private fun decodeList(raw: String?): List<String>? {
            if (raw == null) return null
            val trimmed = raw.trim()
            if (trimmed.startsWith("[")) {
                return try {
                    val arr = JSONArray(trimmed)
                    (0 until arr.length()).map { arr.optString(it).trim() }.filter { it.isNotEmpty() }
                } catch (_: Exception) {
                    null
                }
            }
            return trimmed.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        }
    }
}
